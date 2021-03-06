package ecommerce.sales

import java.util.Date

import ecommerce.sales.Reservation.State
import ecommerce.sales.ReservationStatus._
import pl.newicom.dddd.actor.PassivationConfig
import pl.newicom.dddd.aggregate.{AggregateRoot, AggregateState, EntityId}
import pl.newicom.dddd.eventhandling.EventPublisher

object Reservation {

  def persistenceId(aggregateId: EntityId) = "Reservation-" + aggregateId

  case class State(
      clientId: EntityId,
      status: ReservationStatus,
      items: List[ReservationItem],
      createDate: Date)
    extends AggregateState {

    override def apply = {
      case ProductReserved(_, product, quantity) =>
        val newItems = items.find(item => item.productId == product.productId) match {
          case Some(orderLine) =>
            val index = items.indexOf(orderLine)
            items.updated(index, orderLine.increaseQuantity(quantity))
          case None =>
            ReservationItem(product, quantity) :: items
        }
        copy(items = newItems)

      case ReservationConfirmed(_, _) => copy(status = Confirmed)
      case ReservationClosed(_) => copy(status = Closed)
    }

  }

}

abstract class Reservation(override val pc: PassivationConfig) extends AggregateRoot[State] {
  this: EventPublisher =>

  override def persistenceId = Reservation.persistenceId(id)

  override val factory: AggregateRootFactory = {
    case ReservationCreated(_, clientId) =>
      State(clientId, Opened, items = List.empty, createDate = new Date)
  }

  override def handleCommand: Receive = {
    case CreateReservation(reservationId, clientId) =>
      if (initialized) {
        throw new RuntimeException(s"Reservation $reservationId already exists")
      } else {
        raise(ReservationCreated(reservationId, clientId))
      }

    case ReserveProduct(reservationId, product, quantity) =>
      if (state.status eq Closed) {
        throw new RuntimeException(s"Reservation $reservationId is closed")
      } else {
        raise(ProductReserved(reservationId, product, quantity))
      }

    case ConfirmReservation(reservationId) =>
      if (state.status eq Closed) {
        throw new RuntimeException(s"Reservation $reservationId is closed")
      } else {
        raise(ReservationConfirmed(reservationId, state.clientId))
      }

    case CloseReservation(reservationId) =>
      raise(ReservationClosed(reservationId))
  }
}
