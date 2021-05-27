package ru.itclover.tsp.core.aggregators

import ru.itclover.tsp.core.Pattern.{Idx, IdxExtractor, QI}
import ru.itclover.tsp.core.Time.MaxWindow
import ru.itclover.tsp.core.io.TimeExtractor
import ru.itclover.tsp.core._
import ru.itclover.tsp.core.aggregators.TimestampAdderAccumState.toDoubleValue

import scala.collection.{mutable => m}
import scala.reflect.ClassTag

class TimestampsAdderPattern[Event: IdxExtractor: TimeExtractor, S, T](
  override val inner: Pattern[Event, S, T]
) extends AccumPattern[Event, S, T, Segment, TimestampAdderAccumState[T]] {
  override def initialState(): AggregatorPState[S, T, TimestampAdderAccumState[T]] = AggregatorPState(
    inner.initialState(),
    innerQueue = PQueue.empty,
    astate = TimestampAdderAccumState[T](),
    indexTimeMap = m.Queue.empty
  )

  override val window: Window = MaxWindow

  override def toString: String = s"TimestampsAdderPattern($inner)"
}

protected case class TimestampAdderAccumState[T]() extends AccumState[T, Segment, TimestampAdderAccumState[T]] {

  @inline
  override def updated(
    window: Window,
    times: m.Queue[(Idx, Time)],
    idxValue: IdxValue[T]
  ): (TimestampAdderAccumState[T], QI[Segment]) = {
    if (times.isEmpty || idxValue.value.isFail) (this, PQueue.empty) else {
      val result = idxValue.map(iv => Succ(Segment(times.head._2, times.last._2, toDoubleValue(iv))))
      (TimestampAdderAccumState(), PQueue.apply(result))
    }
  }
}

object TimestampAdderAccumState {
  def toDoubleValue[T](value: T): Double = {
      value.getClass match {
        case x if x.isAssignableFrom(classOf[Double]) => value.asInstanceOf[Double]
        case x if x.isAssignableFrom(classOf[java.lang.Double]) => value.asInstanceOf[Double]
        case x if x.isAssignableFrom(classOf[Int]) => value.asInstanceOf[Int].toDouble
        case x if x.isAssignableFrom(classOf[java.lang.Integer]) => value.asInstanceOf[Int].toDouble
        case x if x.isAssignableFrom(classOf[Long]) => value.asInstanceOf[Long].toDouble
        case x if x.isAssignableFrom(classOf[java.lang.Long]) => value.asInstanceOf[Long].toDouble
        case x if x.isAssignableFrom(classOf[Boolean]) => if(value.asInstanceOf[Boolean]) 1.0 else 0.0
        case x if x.isAssignableFrom(classOf[java.lang.Boolean]) => if(value.asInstanceOf[Boolean]) 1.0 else 0.0
      }
  }
}