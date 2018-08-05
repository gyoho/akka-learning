package aia.faulttolerance

import java.io.File
import java.util.UUID
import akka.actor._
import akka.actor.SupervisorStrategy.{Stop, Resume, Restart, Escalate}
import akka.actor.OneForOneStrategy
import scala.concurrent.duration._
import language.postfixOps

//Note: Version 1 --> LogProcessingSupervisor supervises all [FileWatcher, LogProcessor, DbWriter] (pg.74 figure 4.7)
//Note: Anti-Pattern (https://manuel.bernhardt.io/2016/08/09/akka-anti-patterns-flat-actor-hierarchies-or-mixing-business-logic-and-failure-handling/)
package LogProcessingFlat {

  object LogProcessingApp extends App {
    val sources = Vector("file:///source1/", "file:///source2/")
    val system = ActorSystem("log-processing")

    val databaseUrl = "http://mydatabase1"

    //Key: put props(= apply method) and name in its companion object
    system.actorOf(
      LogProcessingSupervisor.props(sources, databaseUrl),
      LogProcessingSupervisor.name
    )
  }

  object LogProcessingSupervisor {
    def props(sources: Vector[String], databaseUrl: String) =
      Props(new LogProcessingSupervisor(sources, databaseUrl))
    def name = "file-watcher-supervisor"
  }

  class LogProcessingSupervisor(
      sources: Vector[String],
      databaseUrl: String
  ) extends Actor
      with ActorLogging {

    //Key: override the default decider
    override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _: CorruptedFileException      => Resume
      case _: DbBrokenConnectionException => Restart
      case _: DiskError                   => Stop
    }

    var fileWatchers: Vector[ActorRef] = sources.map { source =>
      val dbWriter = context.actorOf(
        DbWriter.props(databaseUrl),
        DbWriter.name(databaseUrl)
      )

      val logProcessor = context.actorOf(
        LogProcessor.props(dbWriter),
        LogProcessor.name
      )

      val fileWatcher = context.actorOf(
        FileWatcher.props(source, logProcessor),
        FileWatcher.name
      )
      context.watch(fileWatcher)
      fileWatcher
    }

    def receive: Receive = {
      case Terminated(actorRef) =>
        if (fileWatchers.contains(actorRef)) {
          fileWatchers = fileWatchers.filterNot(_ == actorRef)
          if (fileWatchers.isEmpty) {
            log.info("Shutting down, all file watchers have failed.")
            context.system.terminate()
          }
        }
    }
  }

  object FileWatcher {
    def props(source: String, logProcessor: ActorRef) = Props(new FileWatcher(source, logProcessor))
    def name = s"file-watcher-${UUID.randomUUID.toString}"

    //Key: messages are kept in the companion object of the respective actor
    case class NewFile(file: File, timeAdded: Long)
    case class SourceAbandoned(uri: String)
  }

  class FileWatcher(source: String, logProcessor: ActorRef)
      extends Actor
      with FileWatchingAbilities {
    register(source)

    import FileWatcher._

    def receive: Receive = {
      case NewFile(file, _) =>
        logProcessor ! LogProcessor.LogFile(file)
      case SourceAbandoned(uri) if uri == source =>
        self ! PoisonPill
    }
  }

  object LogProcessor {
    def props(dbWriter: ActorRef) = Props(new LogProcessor(dbWriter))
    def name = s"log_processor_${UUID.randomUUID.toString}"
    // represents a new log file
    case class LogFile(file: File)
  }

  class LogProcessor(dbWriter: ActorRef)
      extends Actor
      with ActorLogging
      with LogParsing {

    import LogProcessor._

    def receive: Receive = {
      case LogFile(file) =>
        val lines: Vector[DbWriter.Line] = parse(file)
        lines.foreach(dbWriter ! _)
    }
  }

  object DbWriter {
    def props(databaseUrl: String) = Props(new DbWriter(databaseUrl))
    def name(databaseUrl: String) = s"""db-writer-${databaseUrl.split("/").last}"""

    // A line in the log file parsed by the LogProcessor Actor
    case class Line(time: Long, message: String, messageType: String)
  }

  class DbWriter(databaseUrl: String) extends Actor {
    val connection = new DbCon(databaseUrl)

    import DbWriter._
    def receive: Receive = {
      case Line(time, message, messageType) =>
        connection.write(
          Map('time -> time, 'message -> message, 'messageType -> messageType)
        )
    }

    override def postStop(): Unit = connection.close()
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

  case class DiskError(msg: String) extends Error(msg) with Serializable

  case class CorruptedFileException(msg: String, file: File) extends Exception(msg)

  case class DbBrokenConnectionException(msg: String) extends Exception(msg)

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
