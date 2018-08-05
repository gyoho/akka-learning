package aia.faulttolerance

import java.io.File
import java.util.UUID
import akka.actor._
import akka.actor.SupervisorStrategy.{Stop, Resume, Restart, Escalate}
import akka.actor.OneForOneStrategy
import scala.concurrent.duration._
import language.postfixOps

//Note: Version 2 --> Every actor creates and supervises child actors (pg.84 Figure 4.13)
package LogProcessingOrdered {
  object LogProcessingApp extends App {
    val sources = Vector("file:///source1/", "file:///source2/")
    val system = ActorSystem("log-processing")

    val databaseUrls = Vector(
      "http://mydatabase1",
      "http://mydatabase2",
      "http://mydatabase3"
    )

    system.actorOf(
      LogProcessingSupervisor.props(sources, databaseUrls),
      LogProcessingSupervisor.name
    )
  }

  object LogProcessingSupervisor {
    def props(sources: Vector[String], databaseUrls: Vector[String]) =
      Props(new LogProcessingSupervisor(sources, databaseUrls))
    def name = "file-watcher-supervisor"
  }

  class LogProcessingSupervisor(
      sources: Vector[String],
      databaseUrls: Vector[String]
  ) extends Actor
    with ActorLogging {

    // AllForOneStrategy: if any of the file watchers crashes with a DiskError, all file watchers (siblings) are stopped
    override def supervisorStrategy: AllForOneStrategy = AllForOneStrategy() {
      case _: DiskError => Stop
    }

    var fileWatchers: Vector[ActorRef] = sources.map { source =>
      val fileWatcher = context.actorOf(
        FileWatcher.props(source, databaseUrls),
        FileWatcher.name
      )

      //Key: monitor the actor's lifecycle, send the `Terminated` message when the actor is terminated
      context.watch(fileWatcher)
      fileWatcher
    }

    def receive: Receive = {
      case Terminated(fileWatcher) =>
        fileWatchers = fileWatchers.filterNot(_ == fileWatcher)
        if (fileWatchers.isEmpty) {
          log.info("Shutting down, all file watchers have failed.")
          context.system.terminate()
        }
    }
  }

  object FileWatcher {
    def props(source: String, databaseUrls: Vector[String]) = Props(new FileWatcher(source, databaseUrls))
    def name = s"file-watcher-${UUID.randomUUID.toString}"

    case class NewFile(file: File, timeAdded: Long)
    case class SourceAbandoned(uri: String)
  }

  class FileWatcher(source: String, databaseUrls: Vector[String])
      extends Actor
      with ActorLogging
      with FileWatchingAbilities {
    register(source)

    override def supervisorStrategy: OneForOneStrategy = OneForOneStrategy() {
      case _: CorruptedFileException => Resume
    }

    val logProcessor: ActorRef = context.actorOf(
      LogProcessor.props(databaseUrls),
      LogProcessor.name
    )
    context.watch(logProcessor)

    import FileWatcher._

    def receive: Receive = {
      case NewFile(file, _) =>
        logProcessor ! LogProcessor.LogFile(file)
      case SourceAbandoned(uri) if uri == source =>
        log.info(s"$uri abandoned, stopping file watcher.")
        self ! PoisonPill
      case Terminated(`logProcessor`) => //Discovery
        log.info(s"Log processor terminated, stopping file watcher.")
        self ! PoisonPill
    }
  }

  object LogProcessor {
    def props(databaseUrls: Vector[String]) = Props(new LogProcessor(databaseUrls))
    def name = s"log_processor_${UUID.randomUUID.toString}"
    // represents a new log file
    case class LogFile(file: File)
  }

  class LogProcessor(databaseUrls: Vector[String])
      extends Actor
      with ActorLogging
      with LogParsing {
    require(databaseUrls.nonEmpty)

    val initialDatabaseUrl: String = databaseUrls.head
    var alternateDatabases: Vector[String] = databaseUrls.tail

    override def supervisorStrategy: OneForOneStrategy = OneForOneStrategy() {
      case _: DbBrokenConnectionException => Restart
      case _: DbNodeDownException         => Stop
    }

    var dbWriter: ActorRef = context.actorOf(
      DbWriter.props(initialDatabaseUrl),
      DbWriter.name(initialDatabaseUrl)
    )
    context.watch(dbWriter)

    import LogProcessor._

    def receive: Receive = {
      case LogFile(file) =>
        val lines: Vector[DbWriter.Line] = parse(file)
        lines.foreach(dbWriter ! _)
      case Terminated(_) =>
        if (alternateDatabases.nonEmpty) {
          val newDatabaseUrl = alternateDatabases.head
          alternateDatabases = alternateDatabases.tail
          dbWriter = context.actorOf(
            DbWriter.props(newDatabaseUrl),
            DbWriter.name(newDatabaseUrl)
          )
          context.watch(dbWriter)
        } else {
          log.error("All Db nodes broken, stopping.")
          self ! PoisonPill
        }
    }
  }

  object DbWriter {
    def props(databaseUrl: String) = Props(new DbWriter(databaseUrl))
    def name(databaseUrl: String) = s"""db-writer-${databaseUrl.split("/").last}"""

    // A line in the log file parsed by the LogProcessor Actor
    case class Line(time: Long, message: String, messageType: String)
  }

  class DbWriter(databaseUrl: String) extends Actor {
    val connection: DbCon = new DbCon(databaseUrl)

    import DbWriter._

    def receive: Receive = {
      case Line(time, message, messageType) =>
        connection.write(
          Map('time -> time, 'message -> message, 'messageType -> messageType)
        )
    }

    //Key: clean up resources in `postStop`
    override def postStop(): Unit = {
      connection.close()
    }
  }

  // --------------------
  //      Utilities
  // --------------------

  class DbCon(url: String) {

    /**
      * Writes a map to a database.
      * @param map the map to write to the database.
      * @throws DbBrokenConnectionException when the connection is broken. It might be back later
      * @throws DbNodeDownException when the database Node has been removed from the database cluster. It will never work again.
      */
    def write(map: Map[Symbol, Any]): Unit = {
      //
    }

    def close(): Unit = {
      //
    }
  }

  // Unrecoverable Error occurs when disk for the source has crashed
  case class DiskError(msg: String) extends Error(msg)

  // Exception occurs when log file is corrupt and can’t be processed
  case class CorruptedFileException(msg: String, file: File) extends Exception(msg)

  // Connection can be come back after certain time
  case class DbBrokenConnectionException(msg: String) extends Exception(msg)

  // Unrecoverable database node has fatally crashed
  case class DbNodeDownException(msg: String) extends Exception(msg)

  trait LogParsing {
    import DbWriter._
    // Parses log files. creates line objects from the lines in the log file.
    // If the file is corrupt a CorruptedFileException is thrown
    def parse(file: File): Vector[Line] = {
      // implement parser here, now just return dummy value
      Vector.empty[Line]
    }
  }

  trait FileWatchingAbilities {
    def register(uri: String): Unit = {}
  }
}
