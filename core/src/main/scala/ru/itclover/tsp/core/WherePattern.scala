package ru.itclover.tsp.core

import cats.{Foldable, Functor, Monad}
import ru.itclover.tsp.core.Pattern.IdxExtractor

case class WherePattern[Event: IdxExtractor, State1, State2, T1, T2](
  value: Pattern[Event, State1, T1],
  cond: Pattern[Event, State2, T2]
) extends Pattern[Event, WherePState[State1, State2, T1, T2], T1] {
  override def initialState(): WherePState[State1, State2, T1, T2] = ???

  override def apply[F[_]: Monad, Cont[_]: Foldable: Functor](
    oldState: WherePState[State1, State2, T1, T2],
    queue: PQueue[T1],
    events: Cont[Event]
  ): F[(WherePState[State1, State2, T1, T2], PQueue[T1])] = ???
}

case class WherePState[State1, State2, T1, T2](
  valueState: State1,
  valueQueue: PQueue[T1],
  condState: State2,
  condQueue: PQueue[T2]
)
