package aia.structure

import akka.actor._
import akka.testkit._
import org.scalatest._

import scala.concurrent.duration._

class PipeAndFilterTest
    extends TestKit(ActorSystem("PipeAndFilterTest"))
    with WordSpecLike
    with BeforeAndAfterAll {

  val timeout = 2.seconds

  override def afterAll(): Unit = {
    system.terminate()
  }

  //Note: replace endProbe with testActor works
  "The pipe and filter" must {

    "filter messages in configuration 1" in {
      val endProbe = TestProbe()
      val speedFilterRef = system.actorOf(Props(new SpeedFilter(50, endProbe.ref)))
      val licenseFilterRef = system.actorOf(Props(new LicenseFilter(speedFilterRef)))
      val msg = Photo("123xyz", 60)
      licenseFilterRef ! msg
      endProbe.expectMsg(msg)

      licenseFilterRef ! Photo("", 60)
      endProbe.expectNoMsg(timeout)

      licenseFilterRef ! Photo("123xyz", 49)
      endProbe.expectNoMsg(timeout)
    }

    "filter messages in configuration 2" in {
      val endProbe = TestProbe()
      val licenseFilterRef = system.actorOf(Props(new LicenseFilter(endProbe.ref)))
      val speedFilterRef = system.actorOf(Props(new SpeedFilter(50, licenseFilterRef)))
      val msg = Photo("123xyz", 60)
      speedFilterRef ! msg
      endProbe.expectMsg(msg)

      speedFilterRef ! Photo("", 60)
      endProbe.expectNoMsg(timeout)

      speedFilterRef ! Photo("123xyz", 49)
      endProbe.expectNoMsg(timeout)
    }
  }
}
