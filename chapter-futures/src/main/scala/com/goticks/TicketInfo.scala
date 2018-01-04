package com.goticks

import org.joda.time.{Duration, DateTime}

//Key: optional values should come after mandatory values
case class TicketInfo(ticketNr: String,
                      event: Option[Event] = None,
                      userLocation: Option[Location] = None,
                      travelAdvice: Option[TravelAdvice] = None,
                      weather: Option[Weather] = None,
                      suggestions: Seq[Event] = Seq())

case class Location(lat: Double, lon: Double)

case class Event(name: String, location: Location, time: DateTime)

case class Artist(name: String, calendarUri: String)

case class TravelAdvice(routeByCar: Option[RouteByCar] = None,
                        publicTransportAdvice: Option[PublicTransportAdvice] = None)

case class RouteByCar(route: String,
                      timeToLeave: DateTime,
                      origin: Location,
                      destination: Location,
                      estimatedDuration: Duration,
                      trafficJamTime: Duration)

case class PublicTransportAdvice(advice: String,
                                 timeToLeave: DateTime,
                                 origin: Location,
                                 destination: Location,
                                 estimatedDuration: Duration)

case class Weather(temperature: Int, precipitation: Boolean)
