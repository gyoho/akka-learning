package aia.testdriven

import org.scalatest.WordSpecLike
import org.scalatest.MustMatchers
import akka.testkit.{ TestActorRef, TestKit }
import akka.actor._

package silentactor02 {

  //KEY: use test actor's system
  class SilentActorTest extends TestKit(ActorSystem("testsystem"))
    with WordSpecLike
    with MustMatchers
    with StopSystemAfterAll {

    "A Silent Actor" must {

      "change internal state when it receives a message, in single-threaded env" in {
        import SilentActor._

        //KEY: use CallingThreadDispatcher (i.e. invokes actor on the calling thread instead of separate thread)
        val silentActor = TestActorRef[SilentActor]
        silentActor ! SilentMessage("whisper")
        //KEY: access to the underlying actor instance
        silentActor.underlyingActor.state must contain("whisper")
      }

    }
  }


  object SilentActor {
    case class SilentMessage(data: String)
    case class GetState(receiver: ActorRef)
  }

  class SilentActor extends Actor {
    import SilentActor._
    var internalState: Vector[String] = Vector[String]()

    def receive: Receive = {
      case SilentMessage(data) =>
        internalState = internalState :+ data
    }

    def state: Vector[String] = internalState
  }
}

package silentactor03 {

  class SilentActorTest extends TestKit(ActorSystem("testsystem"))
    with WordSpecLike
    with MustMatchers
    with StopSystemAfterAll {

    "A Silent Actor" must {

      "change internal state when it receives a message, in multi-threaded env" in {
        import SilentActor._

        //KEY: Props[classTag] => use the actor's default constructor
        val silentActor = system.actorOf(Props[SilentActor], "s3")
        silentActor ! SilentMessage("whisper1")
        silentActor ! SilentMessage("whisper2")
        silentActor ! GetState(testActor)
        //KEY: expectMsg expects ONE message to be sent to the testActor and asserts the message
        expectMsg(Vector("whisper1", "whisper2"))
      }

    }

  }



  object SilentActor {
    case class SilentMessage(data: String)
    //note: added for testing purpose - forward message to the receiver
    case class GetState(receiver: ActorRef)
  }

  class SilentActor extends Actor {
    import SilentActor._

    var internalState: Vector[String] = Vector[String]()

    def receive: Receive = {
      case SilentMessage(data) => internalState = internalState :+ data
      case GetState(receiver) => receiver ! internalState
    }
  }

}
