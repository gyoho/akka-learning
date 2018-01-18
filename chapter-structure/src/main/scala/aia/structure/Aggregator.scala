package aia.structure

import akka.actor.{Actor, ActorRef}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration

class Aggregator(timeout: FiniteDuration, pipe: ActorRef) extends Actor {

  //Key: buffer to store messages that can’t be processed yet
  val messages = new ListBuffer[PhotoMessage]

  implicit val ec = context.system.dispatcher

  //Key: Sends all received messages to our own mailbox to restore the previous state before start
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    messages.foreach(self ! _)
    messages.clear()
  }

  def receive: Receive = {
    case rcvMsg: PhotoMessage =>
      messages.find(_.id == rcvMsg.id) match {
        case Some(alreadyRcvMsg) => // second message received ready to combine
          val newCombinedMsg = PhotoMessage(
            rcvMsg.id,
            rcvMsg.photo,
            rcvMsg.creationTime.orElse(alreadyRcvMsg.creationTime),
            rcvMsg.speed.orElse(alreadyRcvMsg.speed)
          )
          pipe ! newCombinedMsg
          messages -= alreadyRcvMsg  // cleanup message

        case None =>  // first message, store it for processing later
          messages += rcvMsg
          //Key: schedules timeout by sending the `TimeoutMessages` to self after `timeout` interval
          context.system.scheduler.scheduleOnce(timeout, self, TimeoutMessage(rcvMsg))
      }

    case TimeoutMessage(rcvMsg) =>
      messages.find(_.id == rcvMsg.id) match {
        case Some(alreadyRcvMsg) =>
          pipe ! alreadyRcvMsg  // send incomplete message anyway
          messages -= alreadyRcvMsg  // cleanup message

        case None => //message is already processed
      }

    case ex: Exception => throw ex  //Key: trigger a restart by throwing exception
  }
}

case class TimeoutMessage(msg: PhotoMessage)
