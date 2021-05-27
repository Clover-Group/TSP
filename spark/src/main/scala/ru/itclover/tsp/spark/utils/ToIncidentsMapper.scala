package ru.itclover.tsp.spark.utils

import ru.itclover.tsp.core.io.{Decoder, Extractor}
import ru.itclover.tsp.core.{Incident, Segment}

import scala.util.Try

final case class ToIncidentsMapper[E, EKey, EItem](
  patternId: Int,
  unitIdField: EKey,
  subunit: Int,
  sessionWindowMs: Long,
  partitionFields: Seq[EKey]
)(implicit extractor: Extractor[E, EKey, EItem], decoder: Decoder[EItem, Any]) {

  def apply(event: E): Segment => Incident = {
    val incidentId = s"P#$patternId;" + partitionFields.map(f => f -> extractor[Any](event, f)).mkString
    val unit = Try(extractor[Any](event, unitIdField).toString.toInt).getOrElse(Int.MinValue)
    segment => Incident(incidentId, patternId, sessionWindowMs, segment, unit, subunit, segment.value)
  }
}
