package com.goticks

import scala.concurrent.Future
import com.github.nscala_time.time.Imports._
import scala.util.control.NonFatal
// what about timeout? or at least termination condition?
// future -> actors scheduling time
trait TicketInfoService extends WebServiceCalls {
  //Note: need to be executed on separate threads (decouple caller and callee)
  import scala.concurrent.ExecutionContext.Implicits.global

  type Recovery[T] = PartialFunction[Throwable,T]

  //Note: if call to external service fails, recover with None
  def withNone[T]: Recovery[Option[T]] = {
    case NonFatal(_) => None
  }

  // recover with empty sequence
  def withEmptySeq[T]: Recovery[Seq[T]] = {
    case NonFatal(_) => Seq()
  }

  // recover with the ticketInfo that was built in the previous step
  def withPrevious(previous: TicketInfo): Recovery[TicketInfo] = {
    case NonFatal(_) => previous
  }

  def getWeather(ticketInfo: TicketInfo): Future[TicketInfo] = {

    val futureWeatherX = callWeatherXService(ticketInfo).recover(withNone)

    val futureWeatherY = callWeatherYService(ticketInfo).recover(withNone)

    val futures: List[Future[Option[Weather]]] = List(futureWeatherX, futureWeatherY)

    val fastestResponseFC: Future[Option[Weather]] = Future.firstCompletedOf(futures)
    val fastestResponse: Future[Option[Weather]] = Future.find(futures)(maybeWeather => maybeWeather.isDefined).map(_.flatten)

    fastestResponse.map { weatherResponse =>
      ticketInfo.copy(weather = weatherResponse)
    }
  }

  def getTravelAdvice(info: TicketInfo, event: Event): Future[TicketInfo] = {

    val futureRouteByCar = callTrafficService(info.userLocation, event.location, event.time).recover(withNone)

    val futurePublicTransport = callPublicTransportService(info.userLocation, event.location, event.time).recover(withNone)

    //Note: `zip` creates tuple of future  i.e) Future[(T, U)]
    futureRouteByCar.zip(futurePublicTransport).map { case (routeByCar, publicTransportAdvice) =>
      val travelAdvice = TravelAdvice(routeByCar, publicTransportAdvice)
      info.copy(travelAdvice = Some(travelAdvice))
    }

    // more readable
    for {
      (routeByCar, publicTransportAdvice) <- futureRouteByCar.zip(futurePublicTransport)
      travelAdvice = TravelAdvice(routeByCar, publicTransportAdvice)
    } yield info.copy(travelAdvice = Some(travelAdvice))
  }

  //Note: `Future.sequence`
  def getPlannedEvents(event: Event, artists: Seq[Artist]): Future[Seq[Event]] = {
    val events = artists.map(artist => callArtistCalendarService(artist, event.location))
    Future.sequence(events)
  }

  //Note: `Future.traverse` apply fn: A => Future[B] to all items of a list in parallel
  def getPlannedEventsWithTraverse(event: Event, artists: Seq[Artist]): Future[Seq[Event]] = {
    Future.traverse(artists) { artist =>
      callArtistCalendarService(artist, event.location)
    }
  }

  def getSuggestions(event: Event): Future[Seq[Event]] = {

    val futureArtists = callSimilarArtistsService(event).recover(withEmptySeq)

    for {
      artists <- futureArtists
      events <- getPlannedEvents(event, artists)
    } yield events
  }

  def getTicketInfo(ticketNr: String): Future[TicketInfo] = {
    val emptyTicketInfo = TicketInfo(ticketNr)
    val eventInfo = getEvent(ticketNr).recover(withPrevious(emptyTicketInfo))

    eventInfo.flatMap { info =>
      val infoWithWeather = getWeather(info)
      val infoWithTravelAdvice = info.event.map { event =>
        getTravelAdvice(info, event)
      }.getOrElse(eventInfo)


      val suggestedEvents = info.event.map { event =>
        getSuggestions(event)
      }.getOrElse(Future.successful(Seq()))

      val ticketInfos = Seq(infoWithTravelAdvice, infoWithWeather)

      val infoWithTravelAndWeather: Future[TicketInfo] = Future.fold(ticketInfos)(info) { (acc, elem) =>
        val (travelAdvice, weather) = (elem.travelAdvice, elem.weather)

        //Note: None orElse None --> None,  Some(A) orElse Some(B) --> Some(A)
        acc.copy(travelAdvice = travelAdvice.orElse(acc.travelAdvice), weather = weather.orElse(acc.weather))
      }

      for {
        info <- infoWithTravelAndWeather
        suggestions <- suggestedEvents
      } yield info.copy(suggestions = suggestions)
    }
  }
}

trait WebServiceCalls {
  def getEvent(ticketNr: String): Future[TicketInfo]

  def callWeatherXService(ticketInfo: TicketInfo): Future[Option[Weather]]

  def callWeatherYService(ticketInfo: TicketInfo): Future[Option[Weather]]

  def callTrafficService(origin: Option[Location], destination: Location, time: DateTime): Future[Option[RouteByCar]]

  def callPublicTransportService(origin: Option[Location], destination: Location, time: DateTime): Future[Option[PublicTransportAdvice]]

  def callSimilarArtistsService(event: Event): Future[Seq[Artist]]

  def callArtistCalendarService(artist: Artist, nearLocation: Location): Future[Event]
}
