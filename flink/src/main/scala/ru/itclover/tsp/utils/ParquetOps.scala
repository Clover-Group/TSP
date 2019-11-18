package ru.itclover.tsp.utils

import java.io.File

import org.apache.flink.types.Row
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.parquet.example.data.simple.SimpleGroup
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter
import org.apache.parquet.hadoop.ParquetFileReader
import org.apache.parquet.hadoop.util.HadoopInputFile
import org.apache.parquet.io.ColumnIOFactory
import org.apache.parquet.schema.{MessageType, OriginalType, PrimitiveType, Type}

import scala.collection.JavaConverters._
import scala.collection.mutable

object ParquetOps {

  /**
    *  Types mapping from Apache Parquet to Scala types
    */
  private def typesMap = Map(
    PrimitiveType.PrimitiveTypeName.INT64   -> classOf[Long],
    PrimitiveType.PrimitiveTypeName.INT32   -> classOf[Int],
    PrimitiveType.PrimitiveTypeName.INT96   -> classOf[Array[Byte]],
    PrimitiveType.PrimitiveTypeName.BOOLEAN -> classOf[Boolean],
    PrimitiveType.PrimitiveTypeName.FLOAT   -> classOf[Float],
    PrimitiveType.PrimitiveTypeName.DOUBLE  -> classOf[Double],
    PrimitiveType.PrimitiveTypeName.BINARY  -> classOf[String],
    OriginalType.UTF8                       -> classOf[String]
  )

  /**
    * Retrieving field types from Apache Parquet fields
    * @param input types for particular field
    * @param indices indices for field and his value
    * @param group parquet block with data
    * @return value with Scala / Java type
    */
  def retrieveFieldValue(input: (PrimitiveType, OriginalType), indices: (Int, Int), group: SimpleGroup) = {

    if (typesMap.contains(input._2)) {
      group.getString(indices._1, indices._2)
    }

    val valueInfo = typesMap(input._1.getPrimitiveTypeName)

    valueInfo.getName match {
      case "long" => group.getLong(indices._1, indices._2)
      case "int"  => group.getInteger(indices._1, indices._2)
      //array of bytes
      case "[B" => group.getBinary(indices._1, indices._2).getBytes
      case "boolean"          => group.getBoolean(indices._1, indices._2)
      case "float"            => group.getFloat(indices._1, indices._2)
      case "double"           => group.getDouble(indices._1, indices._2)
      case "java.lang.String" => group.getString(indices._1, indices._2)
      case _ => throw new IllegalArgumentException(s"No mapper for type ${valueInfo.getName}")
    }
  }

  /**
    * Get schema and reader from input file
    * @param input file to get schema and reader
    * @return tuple with parquet schema and reader
    */
  def retrieveSchemaAndReader(input: File): (MessageType, ParquetFileReader) = {

    val reader = ParquetFileReader.open(
      HadoopInputFile.fromPath(new Path(input.toURI), new Configuration())
    )

    val schema = reader.getFooter.getFileMetaData.getSchema

    (schema, reader)
  }

  /**
    * Get schema and reader from bytes array
    * @param input byte array to get schema and reader
    * @return tuple with parquet schema and reader
    */
  def retrieveSchemaAndReader(input: Array[Byte]): (MessageType, ParquetFileReader) = {

    val reader = ParquetFileReader.open(new ParquetStream(input))
    val schema = reader.getFooter.getFileMetaData.getSchema

    (schema, reader)
  }

  /**
    * Retrieve fields types from schema
    * @param schema parquet schema
    * @return map with field name as a key and types tuple as a value
    */
  def getSchemaTypes(schema: MessageType): mutable.Map[String, (PrimitiveType, OriginalType)] = {

    val schemaFields: List[Type] = schema.getFields.asScala.toList

    val fieldMap: mutable.Map[String, (PrimitiveType, OriginalType)] = mutable.Map.empty

    for (field <- schemaFields) {
      fieldMap += (field.getName -> Tuple2(field.asPrimitiveType, field.getOriginalType))
    }

    fieldMap

  }

  /**
    * Retrieve Apache Parquet Groups from schema and reader
    * @param schema apache parquet schema
    * @param reader apache parquet reader
    * @return list of parquet groups
    */
  def getParquetGroups(schema: MessageType, reader: ParquetFileReader): Seq[SimpleGroup] = {

    val groups: mutable.ListBuffer[SimpleGroup] = mutable.ListBuffer.empty
    var pages = reader.readNextRowGroup()

    var rows = 0L

    while (pages != null) {

      rows = pages.getRowCount

      val columnIO = new ColumnIOFactory().getColumnIO(schema)
      val recordReader = columnIO.getRecordReader(pages, new GroupRecordConverter(schema))

      (0 until rows.toInt).foreach(_ => groups += recordReader.read().asInstanceOf[SimpleGroup])

      pages = reader.readNextRowGroup()

    }

    groups

  }

  /**
    * Retrieve data in Apache Flink rows
    * @param input parquet schema and reader
    * @return flink rows
    */
  def retrieveData(input: (MessageType, ParquetFileReader)): Seq[Row] = {

    val (schema, reader) = input
    val groups: Seq[SimpleGroup] = getParquetGroups(input._1, input._2)
    val result: mutable.ListBuffer[Row] = mutable.ListBuffer.empty[Row]

    val schemaTypes = getSchemaTypes(schema)
    var pages = reader.readNextRowGroup()

    var rows = 0L

    while (pages != null) {

      rows = pages.getRowCount

      val columnIO = new ColumnIOFactory().getColumnIO(schema)
      val recordReader = columnIO.getRecordReader(pages, new GroupRecordConverter(schema))

      (0 until rows.toInt).foreach(_ => groups += recordReader.read().asInstanceOf[SimpleGroup])

      pages = reader.readNextRowGroup()

    }

    reader.close()

    var fieldCount = 0
    val objectsList: mutable.ListBuffer[Any] = mutable.ListBuffer.empty[Any]

    groups
      .foreach(group => {

        fieldCount = group.getType.getFieldCount

        (0 until fieldCount).foreach(i => {

          val valueCount = group.getFieldRepetitionCount(i)

          val fieldType = group.getType.getType(i)
          val fieldName = fieldType.getName
          val fieldMapping = schemaTypes(fieldName)

          (0 until valueCount).foreach(j => objectsList += retrieveFieldValue(fieldMapping, (i, j), group))

        })

        val row = new Row(objectsList.size)

        for (i <- objectsList.indices) {
          row.setField(i, objectsList(i))
        }

        result += row
        objectsList.clear()

      })

    result

  }

}
