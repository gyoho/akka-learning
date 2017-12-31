package aia.channels

import akka.actor.Actor

class EchoActor extends Actor {
  def receive: Receive = {
    case msg: AnyRef =>
      sender() ! msg

  }
}
