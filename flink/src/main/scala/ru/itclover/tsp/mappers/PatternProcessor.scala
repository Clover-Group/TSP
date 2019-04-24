package ru.itclover.tsp.mappers

import cats.Id
import com.typesafe.scalalogging.Logger
import org.apache.flink.util.Collector
import ru.itclover.tsp.core.Time
import ru.itclover.tsp.io.TimeExtractor
import ru.itclover.tsp.v2._

import scala.collection.mutable.ListBuffer
import scala.language.reflectiveCalls


case class PatternProcessor[E, State <: PState[Inner, State], Inner, Out](
  pattern: Pattern[E, State, Inner],
  patternMaxWindow: Long,
  mapResults: E => Inner => Out,
  eventsMaxGapMs: Long,
  emptyEvent: E
)(
  implicit timeExtractor: TimeExtractor[E]
) {

  val log = Logger("PatternProcessor")
  var lastState: MapPState[State, Inner, Out] = _
  var lastTime: Time = Time(0)
  log.info(s"pattern: $pattern, inner: $pattern.inner")

  def process(
    key: String,
    elements: Iterable[E],
    out: Collector[Out]
  ): Unit = {
    if (elements.isEmpty) {
      log.info("No elements to proccess")
      return
    }

    val firstElement = elements.head
    val mapFunction = mapResults(firstElement) // do not inline!
    val mappedPattern: MapPattern[E, Inner, Out, State] = new MapPattern(pattern)(in => Result.succ(mapFunction(in)))

    // if the last event occurred so long ago, clear the state
    if (lastState == null || timeExtractor(firstElement).toMillis - lastTime.toMillis > eventsMaxGapMs) {
      lastState = mappedPattern.initialState()
    }

    // Split the different time sequences if they occurred in the same time window
    val sequences = PatternProcessor.splitByCondition(elements.toList)(
      (next, prev) => timeExtractor(next).toMillis - timeExtractor(prev).toMillis > eventsMaxGapMs
    )

    val machine = StateMachine[Id]

    val consume: IdxValue[Out] => Unit = x => x.value.foreach(out.collect)

    val seedStates = lastState +: Stream.continually(mappedPattern.initialState())

    //log.info ("Started lastState")
    // this step has side-effect = it calls `consume` for each output event. We need to process
    // events sequentually, that's why I use foldLeft here 
    val outStates  = sequences zip seedStates
    
    lastState = outStates.foldLeft (mappedPattern.initialState()) {
      case (_, (events, seedState)) => machine.run(mappedPattern, events, seedState, consume)
    }

    //log.info ("Finished lastState")

    lastTime = elements.lastOption.map(timeExtractor(_)).getOrElse(Time(0))
  }
}

object PatternProcessor {

  val currentEventTsMetric = "currentEventTs"

  /**
    * Splits a list into a list of fragments, the boundaries are determined by the given predicate
    * E.g. `splitByCondition(List(1,2,3,5,8,9,12), (x, y) => (x - y) > 2) == List(List(1,2,3,5),List(8,9),List(12)`
    *
    * @param elements initial sequence
    * @param pred condition between the next and previous elements (in this order)
    * @tparam T Element type
    * @return List of chunks
    */
  def splitByCondition[T](elements: List[T])(pred: (T, T) => Boolean): List[Seq[T]] = {
    if (elements.length < 2) {
      List(elements)
    } else {
      val results = ListBuffer(ListBuffer(elements.head))
      elements.sliding(2).foreach { e =>
        val prev = e.head
        val cur = e(1)
        if (pred(cur, prev)) {
          results += ListBuffer(cur)
        } else {
          results.lastOption.getOrElse(sys.error("Empty result sequence - something went wrong")) += cur
        }
      }
      results.map(_.toSeq).toList
    }
  }
}
