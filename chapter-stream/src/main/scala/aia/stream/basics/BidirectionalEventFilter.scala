package aia.stream.basics

import java.nio.file.StandardOpenOption._

import aia.stream.serialization.EventMarshalling
import aia.stream.utils.FileArg
import aia.stream.{Event, LogStreamProcessor, State}
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{JsonFraming, _}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import spray.json._

import scala.concurrent.Future

object BidirectionalEventFilter extends App with EventMarshalling {
  val config = ConfigFactory.load()
  val maxLine = config.getInt("log-stream-processor.max-line")
  val maxJsonObject = config.getInt("log-stream-processor.max-json-object")

  if (args.length != 5) {
    System.err.println(
      "Provide args: input-format output-format input-file output-file state")
    System.exit(1)
  }

  val inputFile = FileArg.shellExpanded(args(2))
  val outputFile = FileArg.shellExpanded(args(3))
  val filterState = args(4) match {
    case State(state) => state
    case unknown =>
      System.err.println(s"Unknown state $unknown, exiting.")
      System.exit(1)
  }

  val inFlow: Flow[ByteString, Event, NotUsed] =
    if (args(0).toLowerCase == "json") {
      JsonFraming
        .objectScanner(maxJsonObject)
        .map(_.decodeString("UTF8").parseJson.convertTo[Event])
    } else {
      Framing
        .delimiter(ByteString("\n"), maxLine)
        .map(_.decodeString("UTF8"))
        .map(LogStreamProcessor.parseLineEx)
        .collect { case Some(event) => event }
    }

  val outFlow: Flow[Event, ByteString, NotUsed] =
    if (args(1).toLowerCase == "json") {
      Flow[Event].map(event => ByteString(event.toJson.compactPrint))
    } else {
      Flow[Event].map { event =>
        ByteString(LogStreamProcessor.logLine(event))
      }
    }

  val bidiFlow = BidiFlow.fromFlows(inFlow, outFlow)

  val source: Source[ByteString, Future[IOResult]] = FileIO.fromPath(inputFile)

  val sink: Sink[ByteString, Future[IOResult]] =
    FileIO.toPath(outputFile, Set(CREATE, WRITE, APPEND))

  val filter: Flow[Event, Event, NotUsed] =
    Flow[Event].filter(_.state == filterState)

  // separates the serialization protocol from the logic for filtering events
  val flow = bidiFlow.join(filter)

  val runnableGraph: RunnableGraph[Future[IOResult]] =
    source.via(flow).toMat(sink)(Keep.right)

  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()

  runnableGraph.run().foreach { result =>
    println(s"Wrote ${result.count} bytes to '$outputFile'.")
    system.terminate()
  }
}
