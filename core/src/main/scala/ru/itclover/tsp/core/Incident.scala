package ru.itclover.tsp.core

import cats.Semigroup

/**
  * Represents found pattern
  *
  * @param id from input conf
  * @param maxWindowMs maximum time-window (accum, aggregation) inside
  * @param segment bounds of incident
  * @param forwardedFields which fields need to push to sink
  */
case class Incident(
  id: String,
  patternId: Int,
  maxWindowMs: Long,
  segment: Segment,
  patternUnit: Int,
  patternSubunit: Int,
  patternValue: Double
) extends Product
    with Serializable

object IncidentInstances {

  implicit def semigroup(aggregator: Option[String]): Semigroup[Incident] = new Semigroup[Incident] {
    override def combine(a: Incident, b: Incident): Incident = {
      val from =
        if (a.segment.from.toMillis > b.segment.from.toMillis) b.segment.from
        else a.segment.from
      val to =
        if (a.segment.to.toMillis > b.segment.to.toMillis) a.segment.to
        else b.segment.to
      Incident(
        b.id,
        b.patternId,
        b.maxWindowMs,
        Segment(from, to, aggregate(a, b, aggregator)),
        b.patternUnit,
        b.patternSubunit,
        aggregate(a, b, aggregator)
      )
    }
  }

  def aggregate(incident1: Incident, incident2: Incident, aggregator: Option[String]): Double = aggregator match {
    case Some("sum") => incident1.patternValue + incident2.patternValue
    case Some("max") => incident1.patternValue.max(incident2.patternValue)
    case Some("min") => incident1.patternValue.min(incident2.patternValue)
    case Some("avg") =>
      val duration1 = incident1.segment.to.toMillis - incident1.segment.from.toMillis
      val duration2 = incident2.segment.to.toMillis - incident1.segment.to.toMillis
      (duration1 * incident1.patternValue + duration2 * incident2.patternValue) / (duration1 + duration2)
    case None => incident2.patternValue
    case _ => incident2.patternValue
  }
}
