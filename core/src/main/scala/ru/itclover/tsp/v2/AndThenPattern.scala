package ru.itclover.tsp.v2
import cats.Monad
import ru.itclover.tsp.v2.Extract.{Idx, QI, Result}
import ru.itclover.tsp.v2.Extract._
import cats.syntax.flatMap._
import cats.syntax.functor._

import scala.collection.{mutable => m}
import scala.annotation.tailrec
import scala.language.higherKinds

/** AndThen  */
//We lose T1 and T2 in output for performance reason only. If needed outputs of first and second stages can be returned as well
case class AndThenPattern[Event, T1, T2, S1 <: PState[T1, S1], S2 <: PState[T2, S2], F[_]: Monad, Cont[_]](
  first: Pattern[Event, T1, S1, F, Cont],
  second: Pattern[Event, T2, S2, F, Cont]
) extends Pattern[Event, (Idx, Idx), AndThenPState[T1, T2, S1, S2], F, Cont] {

  def apply(
    oldState: AndThenPState[T1, T2, S1, S2],
    event: Cont[Event]
  ): F[AndThenPState[T1, T2, S1, S2]] = {

    val firstF = first.apply(oldState.first, event)
    val secondF = second.apply(oldState.second, event)

    for (newFirstState  <- firstF;
         newSecondState <- secondF)
      yield {
        // process queues
        val (updatedFirstQueue, updatedSecondQueue, finalQueue) =
          process(newFirstState.queue, newSecondState.queue, oldState.queue)

        AndThenPState(
          newFirstState.copyWithQueue(updatedFirstQueue),
          newSecondState.copyWithQueue(updatedSecondQueue),
          finalQueue
        )
      }
  }

  override def initialState(): AndThenPState[T1, T2, S1, S2] =
    AndThenPState(first.initialState(), second.initialState(), m.Queue.empty)

  private def process(firstQ: QI[T1], secondQ: QI[T2], totalQ: QI[(Idx, Idx)]): (QI[T1], QI[T2], QI[(Idx, Idx)]) = {

    @tailrec
    def inner(first: QI[T1], second: QI[T2], total: QI[(Idx, Idx)]): (QI[T1], QI[T2], QI[(Idx, Idx)]) = {

      def default: (QI[T1], QI[T2], QI[(Idx, Idx)]) = (first, second, total)

      first.headOption match {
        // if any of parts is empty -> do nothing
        case None => default
        // if first part is Failure (== None) then return None as a result
        case Some(x @ IdxValue(_, Fail)) =>
          inner({first.dequeue; first}, second, {total.enqueue(IdxValue(x.index,Result.fail)); total})
        case Some(IdxValue(index1, _)) =>
          second.headOption match {
            // if any of parts is empty -> do nothing
            case None => default
            // if that's an late event from second queue, just skip it
            case Some(IdxValue(index2, _)) if index2 < index1 => //todo < or <= ?
              inner(first, {second.dequeue; second}, total)
            // if second part is Failure return None as a result
            case Some(IdxValue(_, Fail)) =>
              inner({first.dequeue; first}, second, {total.enqueue(IdxValue(index1, Fail)); total})
            // if both first and second stages a Success then return Success
            case Some(IdxValue(index2, Succ(_))) if index2 >= index1 =>
              inner({first.dequeue; first}, {second.dequeue; second}, {total.enqueue(IdxValue(index1, Succ(index1 -> index2))); total})
          }
      }
    }

    inner(firstQ, secondQ, totalQ)
  }
}

case class AndThenPState[T1, T2, State1 <: PState[T1, State1], State2 <: PState[T2, State2]](
  first: State1,
  second: State2,
  override val queue: QI[(Idx, Idx)]
) extends PState[(Idx, Idx), AndThenPState[T1, T2, State1, State2]] {

  override def copyWithQueue(queue: QI[(Idx, Idx)]): AndThenPState[T1, T2, State1, State2] = this.copy(queue = queue)
}

object AndThenPState {}