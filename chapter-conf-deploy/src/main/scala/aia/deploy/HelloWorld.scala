package aia.deploy

import akka.actor.{ActorRef, ActorLogging, Actor}
import scala.concurrent.duration._

class HelloWorld extends Actor with ActorLogging {

  def receive: Receive = {
    case msg: String =>
      val hello = "Hello %s".format(msg)
      sender() ! hello
      log.info("Sent response {}", hello)
  }
}

class HelloWorldCaller(timer: FiniteDuration, actor: ActorRef)
    extends Actor
    with ActorLogging {

  case class TimerTick(msg: String)

  override def preStart(): Unit = {
    super.preStart()
    implicit val ec = context.dispatcher
    context.system.scheduler.schedule(timer,
                                      timer,
                                      self,
                                      TimerTick("everybody"))
  }

  def receive: Receive = {
    case msg: String     => log.info("received {}", msg)
    case tick: TimerTick => actor ! tick.msg
  }
}
