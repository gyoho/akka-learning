package aia.config

import akka.actor.ActorSystem
import org.scalatest.WordSpecLike
import com.typesafe.config.ConfigFactory
import org.scalatest.MustMatchers

class ConfigTest extends WordSpecLike with MustMatchers {

  "Configuration" should {
    "has configuration" in {
      //Key: ConfigFactory.load() is used internally to create a default config
      val mySystem = ActorSystem("myTest")
      val config = mySystem.settings.config
      config.getInt("myTest.intParam") must be(20)
      config.getString("myTest.applicationDesc") must be("My Config Test")
    }
    "has defaults" in {
      val mySystem = ActorSystem("myDefaultsTest")
      val config = mySystem.settings.config
      config.getInt("myTestDefaults.intParam") must be(10)
      config.getString("myTestDefaults.applicationDesc") must be(
        "My Current Test")
    }
    "can include file" in {
      val mySystem = ActorSystem("myIncludeTest")
      val config = mySystem.settings.config
      config.getInt("myTestIncluded.intParam") must be(30)
      config.getString("myTestIncluded.applicationDesc") must be(
        "My Include Test")
    }
    "can be loaded by ourself" in {
      //Key: use custom config file name (default: application.conf)
      val configuration = ConfigFactory.load("load")
      //Key: use specific config for the actor system (default: ConfigFactory.load() is used internally)
      val mySystem = ActorSystem("myLoadTest", configuration)
      //Key: once ActorSystem is constructed, we can get config just by referencing it using this path
      val config = mySystem.settings.config
      //Key: then we just get a property as we would ordinarily
      config.getInt("myTestLoad.intParam") must be(100)
      config.getString("myTestLoad.applicationDesc") must be("My Load Test")
    }
    //Key: provide default config with `withFallback` method
    "can be lifted" in {
      val configuration = ConfigFactory.load("lift")
      val mySystem = ActorSystem("myFirstLiftTest", configuration.getConfig("myTestLift").withFallback(configuration))
      val config = mySystem.settings.config
      config.getInt("myTest.intParam") must be(20)
      config.getString("myTest.applicationDesc") must be("My Lift Test")
      config.getString("rootParam") must be("root")
      config.getString("myTestLift.rootParam") must be("root")
    }
  }
}
