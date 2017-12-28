package aia.testdriven

import akka.testkit.TestKit
import akka.actor.{ Actor, Props, ActorRef, ActorSystem }
import org.scalatest.{MustMatchers, WordSpecLike }

class FilteringActorTest extends TestKit(ActorSystem("testsystem"))
  with WordSpecLike
  with MustMatchers
  with StopSystemAfterAll {
  "A Filtering Actor" must {

    "filter out particular messages" in {
      import FilteringActor._

      val props = FilteringActor.props(testActor, 5)
      val filter = system.actorOf(props, "filter-1")

      filter ! Event(1)
      filter ! Event(2)
      filter ! Event(1)
      filter ! Event(3)
      filter ! Event(1)
      filter ! Event(4)
      filter ! Event(5)
      filter ! Event(5)
      filter ! Event(6)

      //KEY: testActor receives this
      // only non-duplicated message are sent to the test actor
      // note: receiveWhile() => receive messages while the partial function evaluates true
      // note: cont. when no match happens, popping out of this while loop and returns the list of received message
      val eventIds = receiveWhile() {
        case Event(id) if id <= 5 => id
      }

      eventIds must be(List(1, 2, 3, 4, 5))
      expectMsg(Event(6))
    }


    "filter out particular messages using expectNoMsg" in {
      import FilteringActor._

      val props = FilteringActor.props(testActor, 5)
      val filter = system.actorOf(props, "filter-2")

      filter ! Event(1)
      filter ! Event(2)
      expectMsg(Event(1))
      expectMsg(Event(2))
      filter ! Event(1)
      expectNoMsg
      filter ! Event(3)
      expectMsg(Event(3))
      filter ! Event(1)
      expectNoMsg
      filter ! Event(4)
      filter ! Event(5)
      filter ! Event(5)
      expectMsg(Event(4))
      expectMsg(Event(5))
      expectNoMsg()
    }

  }
}

object FilteringActor {

  case class Event(id: Long)

  def props(nextActor: ActorRef, bufferSize: Int) = Props(new FilteringActor(nextActor, bufferSize))

}

class FilteringActor(nextActor: ActorRef, bufferSize: Int) extends Actor {
  import FilteringActor._

  var lastMessages: Vector[Event] = Vector[Event]()

  def receive: Receive = {
    case msg: Event =>
      if (!lastMessages.contains(msg)) {
        lastMessages = lastMessages :+ msg

        nextActor ! msg

        if (lastMessages.size > bufferSize) {
          // discard the oldest
          lastMessages = lastMessages.tail
        }
      }
  }
}


