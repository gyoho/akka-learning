package aia.structure

import java.util.Date
import scala.concurrent.duration._
import scala.collection.mutable.ListBuffer

import akka.actor._
import java.text.SimpleDateFormat

case class PhotoMessage(id: String,
                        photo: String,
                        creationTime: Option[Date] = None,
                        speed: Option[Int] = None)

object ImageProcessing {

  val dateFormat = new SimpleDateFormat("ddMMyyyy HH:mm:ss.SSS")

  def getSpeed(image: String): Option[Int] = {
    val attributes = image.split('|')
    if (attributes.length == 3) Some(attributes(1).toInt) else None
  }

  def getTime(image: String): Option[Date] = {
    val attributes = image.split('|')
    if (attributes.length == 3) Some(dateFormat.parse(attributes(0))) else None
  }

  def getLicense(image: String): Option[String] = {
    val attributes = image.split('|')
    if (attributes.length == 3) Some(attributes(2)) else None
  }

  def createPhotoString(date: Date, speed: Int): String = {
    createPhotoString(date, speed, " ")
  }

  def createPhotoString(date: Date, speed: Int, license: String): String = {
    "%s|%s|%s".format(dateFormat.format(date), speed, license)
  }
}

class GetSpeed(pipe: ActorRef) extends Actor {
  def receive: Receive = {
    case msg: PhotoMessage => {
      pipe ! msg.copy(speed = ImageProcessing.getSpeed(msg.photo))
    }
  }
}

class GetTime(pipe: ActorRef) extends Actor {
  def receive: Receive = {
    case msg: PhotoMessage => {
      pipe ! msg.copy(creationTime = ImageProcessing.getTime(msg.photo))
    }
  }
}
