package ru.itclover.tsp.io.input

sealed trait SourceDataTransformationConf

abstract class SourceDataTransformation[Event, EKey, EValue](val `type`: String) extends Serializable {
  val config: SourceDataTransformationConf
}

@SuppressWarnings(Array(
  "org.wartremover.warts.DefaultArguments",
))
case class NarrowDataUnfolding[Event, EKey, EValue](
  keyColumn: EKey,
  valueColumn: EKey,
  fieldsTimeoutsMs: Map[EKey, Long],
  defaultTimeout: Option[Long] = None
) extends SourceDataTransformation[Event, EKey, EValue]("NarrowDataUnfolding")
    with SourceDataTransformationConf {
  override val config: SourceDataTransformationConf = this
}

@SuppressWarnings(Array(
  "org.wartremover.warts.DefaultArguments",
))
case class WideDataFilling[Event, EKey, EValue](fieldsTimeoutsMs: Map[EKey, Long], defaultTimeout: Option[Long] = None)
    extends SourceDataTransformation[Event, EKey, EValue]("WideDataFilling")
    with SourceDataTransformationConf {
  override val config: SourceDataTransformationConf = this
}
