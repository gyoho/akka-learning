package aia.stream.basics

import aia.stream.basics.StreamingCopy.{sink, source}
import akka.Done
import akka.stream.IOResult
import akka.stream.scaladsl.{Keep, RunnableGraph}

import scala.concurrent.Future

object Materialization {

  val graphLeft: RunnableGraph[Future[IOResult]] = source.toMat(sink)(Keep.left)
  val graphRight: RunnableGraph[Future[IOResult]] = source.toMat(sink)(Keep.right)
  val graphBoth: RunnableGraph[(Future[IOResult], Future[IOResult])] = source.toMat(sink)(Keep.both)
  val graphCustom: RunnableGraph[Future[Done]] = source.toMat(sink) { (l, r) =>
    Future.sequence(List(l, r)).map(_ => Done)
  }
}
