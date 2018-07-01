package aia.structure

import akka.actor.{Actor, ActorRef}

class RecipientList(recipientList: Seq[ActorRef]) extends Actor {
  def receive: Receive = {
    case msg: AnyRef => recipientList.foreach(_ ! msg)
  }
}
