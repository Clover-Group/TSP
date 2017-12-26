package ru.itclover.streammachine.phases

import org.joda.time.{DateTime, Instant}
import org.joda.time.format.DateTimeFormatter
import org.scalatest.{FunSuite, Matchers, WordSpec}
import ru.itclover.streammachine.Event
import ru.itclover.streammachine.core.AggregatingPhaseParser.derivation
import ru.itclover.streammachine.core.Aggregators.{ToSegments, Segment}
import ru.itclover.streammachine.core.{NumericPhaseParser, PhaseResult}
import ru.itclover.streammachine.core.NumericPhaseParser.{SymbolNumberExtractor, field}
import ru.itclover.streammachine.core.PhaseResult.{Failure, Stay, Success}
import ru.itclover.streammachine.core.Time.TimeExtractor
import ru.itclover.streammachine.phases.Phases.TestPhase

class PhasesTest extends WordSpec with Matchers {
  import Predef.{any2stringadd => _, assert => _, _}

  case class Event(speed: Int, time: DateTime)

  implicit val symbolNumberExtractorEvent = new SymbolNumberExtractor[Event] {
    override def extract(event: Event, symbol: Symbol) = {
      symbol match {
        case 'speed => event.speed
        case _ => sys.error(s"No field $symbol in $event")
      }
    }
  }

  implicit val timeExtractor: TimeExtractor[Event] = new TimeExtractor[Event] {
    override def apply(v1: Event) = v1.time
  }

  "Derivation phase" should {
    "work" in {
      val speed = field('speed)
      val initialState = (speed.initialState, None)
      val (result1, state1) = derivation(speed).apply(Event(100, DateTime.now()), initialState)
      val (result2, state2) = derivation(speed).apply(Event(200, DateTime.now()), state1)
      result2.isTerminal shouldEqual true
      for {
        derivative <- result2
      } yield {
        derivative should be > 0.0
        derivative shouldEqual (100.0 +- 0.000001)
      }
    }
  }

  "IncludeStays phase" should {
    val t = DateTime.now()
    val t1 = t.minusMillis(3000)
    val t2 = t.minusMillis(2000)
    val t3 = t.minusMillis(1000)

    "work on stay-success" in {
      val wwsStream = Stay #:: Stay #:: Success(()) #:: Stream.empty[PhaseResult[Unit]]
      val stay_success = ToSegments(TestPhase[Event, Unit](wwsStream))

      val (result1, state1) = stay_success.apply(Event(100, t1), stay_success.initialState)
      val (result2, state2) = stay_success.apply(Event(200, t2), state1)
      val (result3, state3) = stay_success.apply(Event(300, t3), state2)

      result1 should not be an [Success[_]]
      result2 should not be an [Success[_]]
      result3 shouldBe a [Success[_]]

      val segmentLengthOpt = result3 match {
        case Success(Segment(from, to)) => Some(to.toMillis - from.toMillis)
        case _ => None
      }

      segmentLengthOpt should not be empty
      segmentLengthOpt.get should equal(2000L)
    }

    "not work on stay-failure" in {
      val wwfStream = Stay #:: Stay #:: Failure("Test failure") #:: Stream.empty[PhaseResult[Unit]]
      val stay_failure = ToSegments(TestPhase[Event, Unit](wwfStream))

      val (result1, state1) = stay_failure.apply(Event(100, t1), stay_failure.initialState)
      val (result2, state2) = stay_failure.apply(Event(200, t2), state1)
      val (result3, state3) = stay_failure.apply(Event(300, t3), state2)

      result1 should not be an [Failure]
      result2 should not be an [Failure]
      result3 shouldBe a [Failure]
    }
  }
}
