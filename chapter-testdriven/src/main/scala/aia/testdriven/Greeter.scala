package aia.testdriven

import akka.actor.{ActorLogging, Actor}

case class Greeting(message: String)

// Akka Actor Hello World
class Greeter extends Actor with ActorLogging {
  def receive: Receive = {
    case Greeting(message) => log.info("Hello {}!", message)
  }
}
