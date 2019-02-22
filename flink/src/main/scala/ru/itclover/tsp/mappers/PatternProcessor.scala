package ru.itclover.tsp.mappers

import cats.Id
import org.apache.flink.util.Collector
import ru.itclover.tsp.io.TimeExtractor
import ru.itclover.tsp.v2.Pattern.QI
import ru.itclover.tsp.v2.{PState, Pattern, StateMachine, Succ}

import scala.collection.mutable.ListBuffer

case class PatternProcessor[E, State <: PState[Inner, State], Inner, Out](
  pattern: Pattern[E, State, Inner], //todo Why List?
  mapResults: (E, Seq[Inner]) => Seq[Out],
  eventsMaxGapMs: Long,
  emptyEvent: E
)(
  implicit timeExtractor: TimeExtractor[E]
) {
  var lastState = pattern.initialState()

  def process(
    key: String,
    elements: Iterable[E],
    out: Collector[Out]
  ): Unit = {
    // Split the different time sequences if they occurred in the same time window
    val sequences = PatternProcessor.splitByCondition(
      elements.toList,
      (next: E, prev: E) => timeExtractor(next).toMillis - timeExtractor(prev).toMillis > eventsMaxGapMs
    )
    val states = StateMachine[Id].run(pattern, sequences.head, lastState) :: sequences.tail.map(
      StateMachine[Id].run(pattern, _, pattern.initialState())
    )
    lastState = states.last
    val results = states.map(_.queue).foldLeft(List.empty[Inner]) { (acc: List[Inner], q: QI[Inner]) =>
      acc ++ q.map(_.value).collect { case Succ(v) => v }
    }
    if (elements.nonEmpty)
      mapResults(elements.head, results).foreach(out.collect)
  }
}

object PatternProcessor {

  /**
    * Splits a list into a list of fragments, the boundaries are determined by the given predicate
    * E.g. `splitByCondition(List(1,2,3,5,8,9,12), (x, y) => (x - y) > 2) == List(List(1,2,3,5),List(8,9),List(12)`
    * @param elements initial sequence
    * @param pred condition between the next and previous elements (in this order)
    * @tparam T Element type
    * @return List of chunks
    */
  def splitByCondition[T](elements: List[T], pred: (T, T) => Boolean): List[Seq[T]] = {
    val results = ListBuffer(ListBuffer(elements.head))
    if (elements.length < 2) {
      List(elements)
    } else {
      elements.sliding(2).foreach { e =>
        val prev = e(0)
        val cur = e(1)
        if (pred(cur, prev)) {
          results += ListBuffer(cur)
        } else {
          results.last += cur
        }
      }
      results.map(_.toSeq).toList
    }
  }
  val currentEventTsMetric = "currentEventTs"
}