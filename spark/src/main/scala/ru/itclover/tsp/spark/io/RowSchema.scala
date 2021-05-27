package ru.itclover.tsp.spark.io

import org.apache.spark.sql.execution.streaming.FileStreamSource.Timestamp
import org.apache.spark.sql.types.{DataType, DataTypes}

import scala.collection.mutable

case class RowSchema(
  unitIdField: Symbol,
  fromTsField: Symbol,
  toTsField: Symbol,
  appIdFieldVal: (Symbol, Int),
  patternIdField: Symbol,
  subunitIdField: Symbol,
  valueField: Symbol,
) extends Serializable {
  val fieldsCount: Int = 7

  val fieldsNames: List[Symbol] =
    List(unitIdField, fromTsField, toTsField, appIdFieldVal._1, patternIdField, subunitIdField, valueField)

  val fieldsIndexesMap: mutable.LinkedHashMap[Symbol, Int] = mutable.LinkedHashMap(fieldsNames.zipWithIndex: _*)

  val fieldClasses: List[Class[_]] =
    List(classOf[Int], classOf[Timestamp], classOf[Timestamp], classOf[Int], classOf[Int], classOf[Int], classOf[Double])

  val fieldDatatypes: List[DataType] =
    List(
      DataTypes.IntegerType,
      DataTypes.TimestampType,
      DataTypes.TimestampType,
      DataTypes.IntegerType,
      DataTypes.IntegerType,
      DataTypes.IntegerType,
      DataTypes.DoubleType
    )

  val unitIdInd = fieldsIndexesMap(unitIdField)

  val beginInd = fieldsIndexesMap(fromTsField)
  val endInd = fieldsIndexesMap(toTsField)

  val patternIdInd = fieldsIndexesMap(patternIdField)

  val appIdInd = fieldsIndexesMap(appIdFieldVal._1)

  val subunitIdInd = fieldsIndexesMap(subunitIdField)

  val valueInd = fieldsIndexesMap(valueField)
}
