package aia.testdriven

import akka.actor._
import akka.testkit.TestKit
import org.scalatest.{MustMatchers, WordSpecLike}

//This test is ignored in the BookBuild, it's added to the defaultExcludedNames

class SilentActor01Test extends TestKit(ActorSystem("testsystem"))
  with WordSpecLike
  with MustMatchers
  with StopSystemAfterAll {

  //KEY: red-green refactor
  "A Silent Actor" must {
    "change state when it receives a message, single threaded" in {
      //Write the test, first fail
      fail("not implemented yet")
    }
    "change state when it receives a message, multi-threaded" in {
      //Write the test, first fail
      fail("not implemented yet")
    }
  }

}



class SilentActor extends Actor {
  override def receive: Receive = {
    case msg => // swallows any messages
  }
}

