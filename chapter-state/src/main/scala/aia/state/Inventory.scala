package aia.state

import akka.actor.{ActorRef, Actor, FSM}
import math.min
import scala.concurrent.duration._

// events
case class BookRequest(context: AnyRef, target: ActorRef)
case class BookSupply(nrBooks: Int)
case object BookSupplySoldOut
case object Done
case object PendingRequests

//responses
case object PublisherRequest
case class BookReply(context: AnyRef, reserveId: Either[String, Int])

//states
sealed trait State
case object WaitForRequests extends State
case object ProcessRequest extends State
case object WaitForPublisher extends State
case object SoldOut extends State
case object ProcessSoldOut extends State

// state data needs to be tracked by the FSM
case class StateData(nrBooksInStore: Int, pendingRequests: Seq[BookRequest])

class Inventory(publisher: ActorRef) extends Actor with FSM[State, StateData] {

  var reserveId = 0
  startWith(WaitForRequests, StateData(0, Seq()))

  /**
    * `when` clause takes
    *   - state name
    *   - partial function to handle all the possible events
    */

  /**
    * final case class Event(event: Any, stateData: D)
    *   - event: possible events i.e) BookRequest, BookSupply, etc
    *   - stateData: StateData defined above
    */
  // Declares transitions for state WaitForRequests
  when(WaitForRequests) {

    // Declares possible Event when a BookRequest message occurs
    case Event(request: BookRequest, data: StateData) =>
      val newStateData = data.copy(pendingRequests = data.pendingRequests :+ request)

      if (newStateData.nrBooksInStore > 0) {
        goto(ProcessRequest) using newStateData
      } else {
        goto(WaitForPublisher) using newStateData
      }

    // Declares possible Event when a PendingRequests message occurs
    case Event(PendingRequests, data: StateData) =>
      if (data.pendingRequests.isEmpty) {
        stay
      } else if (data.nrBooksInStore > 0) {
        goto(ProcessRequest)
      } else {
        goto(WaitForPublisher)
      }
  }

  // Transition declaration of the state WaitForPublisher
  when(WaitForPublisher) {
    case Event(supply: BookSupply, data: StateData) =>
      goto(ProcessRequest) using data.copy(nrBooksInStore = supply.nrBooks)
    case Event(BookSupplySoldOut, _) =>
      goto(ProcessSoldOut)
  }

  // Transition declaration of the state ProcessRequest
  when(ProcessRequest) {
    case Event(Done, data: StateData) =>
      goto(WaitForRequests) using data.copy(
        nrBooksInStore = data.nrBooksInStore - 1,
        pendingRequests = data.pendingRequests.tail
      )
  }

  // Transition declaration of the state SoldOut
  when(SoldOut) {
    case Event(request: BookRequest, _: StateData) =>
      goto(ProcessSoldOut) using StateData(0, Seq(request))
    // TODO: handle pendingRequests?
  }

  // Transition declaration of the state ProcessSoldOut
  when(ProcessSoldOut) {
    case Event(Done, _: StateData) =>
      goto(SoldOut) using StateData(0, Seq())
  }

  // default handler like catch call for all states
  // Note: we can still receive BookRequest at any state at anytime
  whenUnhandled {
    case Event(request: BookRequest, data: StateData) =>
      stay using data.copy(pendingRequests = data.pendingRequests :+ request)

    case Event(e, s) =>
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      stay
  }

  onTransition {
    case _ -> WaitForRequests =>
      if (nextStateData.pendingRequests.nonEmpty) {
        // go to next state
        self ! PendingRequests
      }
    case _ -> WaitForPublisher =>
      // send request to publisher
      publisher ! PublisherRequest
    case _ -> ProcessRequest =>
      val request = nextStateData.pendingRequests.head
      reserveId += 1
      // send a replay to sender
      request.target ! BookReply(request.context, Right(reserveId))
      self ! Done
    case _ -> ProcessSoldOut =>
      // send an error replay to all PendingRequests
      nextStateData.pendingRequests.foreach(request =>
        request.target ! BookReply(request.context, Left("SoldOut"))
      )
      self ! Done
  }

  // initialize and startup the FSM (required)
  initialize
}

class Publisher(totalNrBooks: Int, nrBooksPerRequest: Int) extends Actor {

  var nrLeft: Int = totalNrBooks

  def receive: Receive = {
    case PublisherRequest =>
      if (nrLeft == 0)
        sender() ! BookSupplySoldOut
      else {
        val supply = min(nrBooksPerRequest, nrLeft)
        nrLeft -= supply
        sender() ! BookSupply(supply)
      }
  }
}

class InventoryWithTimer(publisher: ActorRef)
    extends Actor
    with FSM[State, StateData] {

  var reserveId = 0
  startWith(WaitForRequests, StateData(0, Seq()))

  when(WaitForRequests) {
    case Event(request: BookRequest, data: StateData) =>
      val newStateData =
        data.copy(pendingRequests = data.pendingRequests :+ request)
      if (newStateData.nrBooksInStore > 0) {
        goto(ProcessRequest) using newStateData
      } else {
        goto(WaitForPublisher) using newStateData
      }
    case Event(PendingRequests, data: StateData) =>
      if (data.pendingRequests.isEmpty) {
        stay
      } else if (data.nrBooksInStore > 0) {
        goto(ProcessRequest)
      } else {
        goto(WaitForPublisher)
      }
  }
  when(WaitForPublisher, stateTimeout = 5.seconds) {
    case Event(supply: BookSupply, data: StateData) =>
      goto(ProcessRequest) using data.copy(nrBooksInStore = supply.nrBooks)
    case Event(BookSupplySoldOut, _) =>
      goto(ProcessSoldOut)
    case Event(StateTimeout, _) => goto(WaitForRequests)
  }
  when(ProcessRequest) {
    case Event(Done, data: StateData) =>
      goto(WaitForRequests) using data.copy(
        nrBooksInStore = data.nrBooksInStore - 1,
        pendingRequests = data.pendingRequests.tail)
  }
  when(SoldOut) {
    case Event(request: BookRequest, _: StateData) =>
      goto(ProcessSoldOut) using StateData(0, Seq(request))
  }
  when(ProcessSoldOut) {
    case Event(Done, _: StateData) =>
      goto(SoldOut) using StateData(0, Seq())
  }
  whenUnhandled {
    // common code for all states
    case Event(request: BookRequest, data: StateData) =>
      stay using data.copy(pendingRequests = data.pendingRequests :+ request)
    case Event(e, s) =>
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      stay
  }
  initialize

  onTransition {
    case _ -> WaitForRequests =>
      if (nextStateData.pendingRequests.nonEmpty) {
        // go to next state
        self ! PendingRequests
      }
    case _ -> WaitForPublisher =>
      //send request to publisher
      publisher ! PublisherRequest
    case _ -> ProcessRequest =>
      val request = nextStateData.pendingRequests.head
      reserveId += 1
      request.target ! BookReply(request.context, Right(reserveId))
      self ! Done
    case _ -> ProcessSoldOut =>
      nextStateData.pendingRequests.foreach(request =>
        request.target ! BookReply(request.context, Left("SoldOut")))
      self ! Done
  }
}
