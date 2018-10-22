package ru.itclover.tsp.transformers

import org.apache.flink.api.common.functions.RichFlatMapFunction
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala.DataStream
import org.apache.flink.types.Row
import org.apache.flink.util.Collector
import ru.itclover.tsp.phases.NumericPhases.SymbolNumberExtractor
import ru.itclover.tsp.EvalUtils
import ru.itclover.tsp.core.{Pattern, Time}
import ru.itclover.tsp.core.Time.TimeExtractor
import ru.itclover.tsp.io.{EventCreator, EventCreatorInstances}
import ru.itclover.tsp.io.input.{InputConf, JDBCInputConf, NarrowDataUnfolding}

import scala.collection.mutable

trait SparseDataAccumulator

/**
  * Accumulates sparse key-value format into dense Row using timeouts.
  * @param fieldsKeysTimeoutsMs - indexes to collect and timeouts (milliseconds) per each (collect by-hand for now)
  * @param extraFieldNames - will be added to every emitting event
  */
case class SparseRowsDataAccumulator[InEvent, Value, OutEvent](
  fieldsKeysTimeoutsMs: Map[Symbol, Long],
  extraFieldNames: Seq[Symbol]
)(
  implicit extractTime: TimeExtractor[InEvent],
  extractKeyAndVal: InEvent => (Symbol, Value),
  extractAny: (InEvent, Symbol) => Any,
  eventCreator: EventCreator[OutEvent]
) extends RichFlatMapFunction[InEvent, OutEvent]
    with Serializable {
  // potential event values with receive time
  val event: mutable.Map[Symbol, (Value, Time)] = mutable.Map.empty
  val targetKeySet: Set[Symbol] = fieldsKeysTimeoutsMs.keySet
  val keysIndexesMap: Map[Symbol, Int] = targetKeySet.zip(0 until targetKeySet.size).toMap

  val extraFieldsIndexesMap: Map[Symbol, Int] = extraFieldNames
    .zip(
      targetKeySet.size until
      targetKeySet.size + extraFieldNames.size
    )
    .toMap
  val fieldsIndexesMap: Map[Symbol, Int] = keysIndexesMap ++ extraFieldsIndexesMap
  val arity: Int = fieldsKeysTimeoutsMs.size + extraFieldNames.size

  override def flatMap(item: InEvent, out: Collector[OutEvent]): Unit = {
    val (key, value) = extractKeyAndVal(item)
    val time = extractTime(item)
    event(key) = (value, time)
    dropExpiredKeys(event, time)
    if (targetKeySet subsetOf event.keySet) {
      val list = mutable.ListBuffer.fill[(Symbol, AnyRef)](arity)(null)
      event.foreach { case (k, (v, _)) => list(keysIndexesMap(k)) = (k, v.asInstanceOf[AnyRef]) }
      extraFieldNames.foreach { name =>
        list(extraFieldsIndexesMap(name)) = (name, extractAny(item, name).asInstanceOf[AnyRef])
      }
      val outEvent = eventCreator.create(list)
      out.collect(outEvent)
    }
  }

  private def dropExpiredKeys(event: mutable.Map[Symbol, (Value, Time)], currentRowTime: Time): Unit = {
    event.retain((k, v) => currentRowTime.toMillis - v._2.toMillis < fieldsKeysTimeoutsMs(k))
  }
}

object SparseRowsDataAccumulator {

  def apply[InEvent, Value, OutEvent](inputConf: InputConf[InEvent])(
    implicit timeExtractor: TimeExtractor[InEvent],
    extractKeyVal: InEvent => (Symbol, Value),
    extractAny: (InEvent, Symbol) => Any,
    rowTypeInfo: TypeInformation[OutEvent],
    eventCreator: EventCreator[OutEvent]
  ): SparseRowsDataAccumulator[InEvent, Value, OutEvent] = {
    val sparseRowsConf = inputConf.dataTransformation
      .map({
        case ndu: NarrowDataUnfolding => ndu
      })
      .getOrElse(sys.error("Invalid config type"))
    val fim = inputConf.errOrFieldsIdxMap match {
      case Right(m) => m
      case Left(e)  => sys.error(e.toString)
    }
    val extraFields = fim
      .filterNot(
        nameAndInd => nameAndInd._1 == sparseRowsConf.key || nameAndInd._1 == sparseRowsConf.value
      )
      .keys
      .toSeq
    SparseRowsDataAccumulator(sparseRowsConf.fieldsTimeouts, extraFields)(
      timeExtractor,
      extractKeyVal,
      extractAny,
      eventCreator
    )
  }
}
