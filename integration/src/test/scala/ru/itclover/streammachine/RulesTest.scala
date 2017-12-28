package ru.itclover.streammachine

import java.time.Instant

import org.scalatest.{Matchers, WordSpec}
import ru.itclover.streammachine.RulesDemo.Row2
import ru.itclover.streammachine.core.NumericPhaseParser.field
import ru.itclover.streammachine.core.{AliasedParser, PhaseParser, Window}
import ru.itclover.streammachine.core.PhaseResult.{Failure, Success}
import ru.itclover.streammachine.core.Time.{TimeExtractor, more}
import ru.itclover.streammachine.phases.Phases.{Assert, Decreasing, Wait}
import ru.itclover.streammachine.http.utils.{Timer => TimerGenerator, _}

import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scala.util.Random

class RulesTest extends WordSpec with Matchers {

  import Rules._
  import core.Aggregators._
  import core.AggregatingPhaseParser._
  import core.NumericPhaseParser._
  import ru.itclover.streammachine.core.Time._
  import Predef.{any2stringadd => _, assert => _, _}


  implicit val random: Random = new java.util.Random(345l)

  implicit val symbolNumberExtractorEvent = new SymbolNumberExtractor[Row] {
    override def extract(event: Row, symbol: Symbol) = {
      symbol match {
        case 'speed => event.speed
        case 'pump => event.pump
        case _ => sys.error(s"No field $symbol in $event")
      }
    }
  }
  implicit val timeExtractor: TimeExtractor[Row] = new TimeExtractor[Row] {
    override def apply(v1: Row) = v1.time
  }

  def fakeMapper[Event, PhaseOut](p: PhaseParser[Event, _, PhaseOut]) = FakeMapper[Event, PhaseOut]()


  def segmentMapper[Event, PhaseOut](p: PhaseParser[Event, _, PhaseOut])(implicit te: TimeExtractor[Event]) =
    SegmentResultsMapper[Event, PhaseOut]()(te)


  def run[Event, Out](rule: PhaseParser[Event, _, Out], events: Seq[Event]) = {
    val mapResults = fakeMapper(rule)
    events
      .foldLeft(StateMachineMapper(rule, mapResults)) { case (machine, event) => machine(event) }
      .result
  }

  def runWithSegmentation[Event, Out](rule: PhaseParser[Event, _, Out], events: Seq[Event])
                                     (implicit te: TimeExtractor[Event]) = {
    val mapResults = segmentMapper(rule)(te)
    events
      .foldLeft(StateMachineMapper(rule, mapResults)) { case (machine, event) => machine(event) }
      .result
  }



  type Phase[Row] = PhaseParser[Row, _, _]

  "Combine And & Assert parsers" should {
    "work correctly" in {
      val phase: Phase[Row] = 'speed > 10 and 'speed < 20

      val rows = (
        for (time <- TimerGenerator(from = Instant.now());
             speed <- Constant(30.0).timed(1.seconds)
               .after(Change(from = 30.0, to = 0.0, howLong = 10.seconds))
               .after(Constant(0.0))
        ) yield Row(time, speed.toInt, 0)
        ).run(seconds = 10)

      val results = run(phase, rows)

      assert(results.nonEmpty)

      val (success, failures) = results partition {
        case Success(_) => true
        case Failure(_) => false
      }

      success.length should be > 1
      failures.length should be > 1
    }
  }


  "stopWithoutOilPumping" should {

    //        +1 Остановка без прокачки масла
    //        1. начало куска ContuctorOilPump не равен 0 И SpeedEngine уменьшается с 260 до 0
    //        2. конец когда SpeedEngine попрежнему 0 и ContactorOilPump = 0 и между двумя этими условиями прошло меньше 60 сек
    //          """ЕСЛИ SpeedEngine перешел из "не 0" в "0" И в течение 90 секунд суммарное время когда ContactorBlockOilPumpKMN = "не 0" менее 60 секунд"""

    implicit val random: Random = new java.util.Random(345l)

    "match for valid-1" in {
      val rows = (
        for (time <- TimerGenerator(from = Instant.now());
             pump <- RandomInRange(1, 100).map(_.toDouble).timed(40.second)
               .after(Constant(0));
             speed <- Constant(261.0).timed(1.seconds)
               .after(Change(from = 260.0, to = 0.0, howLong = 10.seconds))
               .after(Constant(0.0))
        ) yield Row2(time, speed.toInt, pump.toInt, 1)
        ).run(seconds = 100)

      //      val results: Seq[(Int, String)] = run(Rules.stopWithoutOilPumping, rows).collect { case Success(x) => x }
      //
      //      assert(results.nonEmpty)
    }

    "match for valid-2" in {
      val rows = (
        for (time <- TimerGenerator(from = Instant.now());
             pump <- RandomInRange(1, 100).map(_.toDouble).timed(40.second)
               .after(Constant(0));
             speed <- Change(from = 1.0, to = 261, 15.seconds).timed(1.seconds)
               .after(Change(from = 260.0, to = 0.0, howLong = 10.seconds))
               .after(Constant(0.0))
        ) yield Row2(time, speed.toInt, pump.toInt, 1)
        ).run(seconds = 100)

      //      val results: Seq[(Int, String)] = run(Rules.stopWithoutOilPumping, rows).collect { case Success(x) => x }
      //
      //      assert(results.nonEmpty)
    }

    //    "not to match" in {
    //      val rows = (
    //        for (time <- TimerGenerator(from = Instant.now());
    //             pump <- RandomInRange(1, 100).map(_.toDouble).timed(40.second)
    //               .after(Constant(0));
    //             speed <- Constant(250d).timed(1.seconds)
    //               .after(Change(from = 250.0, to = 0.0, howLong = 10.seconds))
    //               .after(Constant(0.0))
    //        ) yield Row2(time, speed.toInt, pump.toInt, 1)
    //        ).run(seconds = 100)
    //
    //      val results: Seq[(Int, String)] = run(Rules.stopWithoutOilPumping, rows).collect { case Success(x) => x }
    //
    //      assert(results.isEmpty)
    //    }

  }

  "dsl" should {

    "works" in {
      import core.Aggregators._
      import core.AggregatingPhaseParser._
      import core.NumericPhaseParser._
      import ru.itclover.streammachine.core.Time._

      import Predef.{any2stringadd => _, assert => _, _}

      implicit val random: Random = new java.util.Random(345l)

      val window: Window = 5.seconds

      type Phase[Row] = PhaseParser[Row, _, _]

      val phase: Phase[Row] = avg((e: Row) => e.speed, 2.seconds) > 100

      val rows = (
        for (time <- TimerGenerator(from = Instant.now());
             pump <- RandomInRange(1, 100).map(_.toDouble).timed(40.second)
               .after(Constant(0));
             speed <- Constant(261.0).timed(1.seconds)
               .after(Change(from = 260.0, to = 0.0, howLong = 10.seconds))
               .after(Constant(0.0))
        ) yield Row(time, speed.toInt, pump.toInt)
        ).run(seconds = 10)

      val results = run(phase, rows)

      assert(results.nonEmpty)
      //
      //    val phase2: Phase[Row] = avg('speed, window) > avg('pump, window)
      //
      //    val phase3 = (avg('speed, 5.seconds) >= 5.0) andThen avg('pump, 3.seconds) > 0
      //
      //    val phase4: Phase[Row] = avg((e: Row) => e.speed, 5.seconds) >= value(5.0)
      //
      //    val phase5: Phase[Row] = ('speed > 4 & 'pump > 100).timed(more(10.seconds))
    }

  }


  "Result segmentation" should {

    implicit val random: Random = new java.util.Random(345l)

    implicit val symbolNumberExtractorEvent = new SymbolNumberExtractor[Row] {
      override def extract(event: Row, symbol: Symbol) = {
        symbol match {
          case 'speed => event.speed
          case 'pump => event.pump
          case _ => sys.error(s"No field $symbol in $event")
        }
      }
    }

    implicit val timeExtractor = new TimeExtractor[Row] {
      override def apply(v1: Row) = v1.time
    }

    "work on not segmented output" in {
      val phase: Phase[Row] = 'speed > 35
      val rows = (
        for (time <- TimerGenerator(from = Instant.now());
             speed <- Constant(50.0).timed(1.seconds)
               .after(Change(from = 50.0, to = 30.0, howLong = 20.seconds))
               .after(Constant(0.0))
        ) yield Row(time, speed.toInt, 0)
        ).run(seconds = 20)
      val (successes, failures) = runWithSegmentation(phase, rows).partition(_.isInstanceOf[Success[Segment]])

      failures should not be empty
      successes should not be empty
      successes.length should equal(1)

      val segmentLengthOpt = successes.head match {
        case Success(Segment(from, to)) => Some(to.toMillis - from.toMillis)
        case _ => None
      }
      segmentLengthOpt should not be empty
      segmentLengthOpt.get should be > 12000L
      segmentLengthOpt.get should be < 20000L
    }

    "work on segmented output" in {
      val phase: Phase[Row] = ToSegments(Decreasing(_.speed, 50.0, 35.0))
      // val phase: Phase[Row] = ToSegments(('speed >= 50.0) andThen (derivation('speed) <= 0.0) until ('speed <= 35.0))
      val rows = (
        for (time <- TimerGenerator(from = Instant.now());
             speed <- Constant(51.0).timed(1.seconds)
               .after(Constant(50.0).timed(1.seconds))
               .after(Change(from = 50.0, to = 35.0, howLong = 15.seconds))
               .after(Constant(35.0).timed(1.seconds))
               .after(Change(from = 35.0, to = 30.0, howLong = 5.seconds))
               .after(Constant(0.0))
        ) yield Row(time, speed.toInt, 0)
        ).run(seconds = 20)
      println(rows.map(_.speed))
      val (successes, failures) = runWithSegmentation(phase, rows).partition(_.isInstanceOf[Success[_]])

      failures should not be empty
      successes should not be empty
      successes.length should equal(1)

      val segmentLengthOpt = successes.head match {
        case Success(Segment(from, to)) => Some(to.toMillis - from.toMillis)
        case _ => None
      }
      segmentLengthOpt should not be empty
      segmentLengthOpt.get should be > 12000L
      segmentLengthOpt.get should be < 15000L
    }
  }

  case class Row(time: Instant, speed: Double, pump: Double)

}


object RulesTest extends App {


  //  (1 to 100).map(_.seconds).map(generator).foreach(println)

}