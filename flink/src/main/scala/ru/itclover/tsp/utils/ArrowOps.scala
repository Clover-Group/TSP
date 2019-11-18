package ru.itclover.tsp.utils

import java.io.{File, FileInputStream, FileOutputStream}

import org.apache.arrow.memory.RootAllocator
import org.apache.arrow.vector.dictionary.DictionaryProvider
import org.apache.arrow.vector.ipc.{ArrowFileReader, ArrowFileWriter, ArrowReader, SeekableReadChannel}
import org.apache.arrow.vector.{BaseValueVector, BigIntVector, BitVector, FieldVector, Float4Vector, Float8Vector, IntVector, SmallIntVector, VarCharVector, VectorDefinitionSetter, VectorSchemaRoot}
import org.apache.arrow.vector.types.Types
import org.apache.arrow.vector.types.pojo.Schema
import org.apache.arrow.vector.util.Text
import org.apache.flink.types.Row

import scala.collection.JavaConverters._
import scala.collection.mutable

object ArrowOps {

  /**
    * Types mapping from Apache Arrow to Scala types
    * @return
    */
  def typesMap = Map(
    Types.MinorType.BIGINT   -> classOf[Long],
    Types.MinorType.SMALLINT -> classOf[Short],
    Types.MinorType.BIT      -> classOf[Boolean],
    Types.MinorType.INT      -> classOf[Int],
    Types.MinorType.VARCHAR  -> classOf[String],
    Types.MinorType.FLOAT4   -> classOf[Float],
    Types.MinorType.FLOAT8   -> classOf[Double]
  )

  /**
    * Method for retrieving typed value from vector
    * @param valueVector vector with raw value
    * @return typed value
    */
  def retrieveFieldValue(valueVector: FieldVector): BaseValueVector with FieldVector with VectorDefinitionSetter = {

    val valueInfo = typesMap(valueVector.getMinorType)

    valueInfo.getName match {
      case "int"              => valueVector.asInstanceOf[IntVector]
      case "boolean"          => valueVector.asInstanceOf[BitVector]
      case "java.lang.String" => valueVector.asInstanceOf[VarCharVector]
      case "float"            => valueVector.asInstanceOf[Float4Vector]
      case "double"           => valueVector.asInstanceOf[Float8Vector]
      case "long"             => valueVector.asInstanceOf[BigIntVector]
      case "short"            => valueVector.asInstanceOf[SmallIntVector]
      case _                  => throw new IllegalArgumentException(s"No mapper for type ${valueInfo.getName}")
    }

  }

  /**
    * Method for retrieving schema and reader from input file
    * @param input file with input data
    * @param allocatorValue value for root allocator
    * @return tuple with schema and reader
    */
  def retrieveSchemaAndReader(input: File, allocatorValue: Int): (Schema, ArrowReader, RootAllocator) = {

    val allocator = new RootAllocator(allocatorValue)
    val fileStream = new FileInputStream(input)

    val readChannel = new SeekableReadChannel(fileStream.getChannel)

    val reader = new ArrowFileReader(readChannel, allocator)

    (reader.getVectorSchemaRoot.getSchema, reader, allocator)

  }

  /**
    * Method for schema fields
    * @param schema schema from Apache Arrow
    * @return list of fields(string)
    */
  def getSchemaFields(schema: Schema): List[String] = {

    schema.getFields.asScala
      .map(_.getName)
      .toList

  }

  /**
    * Retrieve data in Apache Flink rows
    * @param input arrow schema and reader
    * @return flink rows
    */
  def retrieveData(input: (Schema, ArrowReader, RootAllocator)): mutable.ListBuffer[Row] = {

    val (schema, reader, allocator) = input
    val schemaFields = getSchemaFields(schema)

    val schemaRoot = reader.getVectorSchemaRoot
    var rowCount = 0

    var readCondition = reader.loadNextBatch()
    val result: mutable.ListBuffer[Row] = mutable.ListBuffer.empty[Row]
    val objectsList: mutable.ListBuffer[Any] = mutable.ListBuffer.empty[Any]

    while (readCondition) {

      rowCount = schemaRoot.getRowCount

      for (i <- 0 until rowCount) {

        for (field <- schemaFields) {

          val valueVector = schemaRoot.getVector(field)
          objectsList += retrieveFieldValue(valueVector).getObject(i)

        }

        val row = new Row(objectsList.size)
        for (i <- objectsList.indices) {
          row.setField(i, objectsList(i))
        }

        result += row
        objectsList.clear()

      }

      readCondition = reader.loadNextBatch()

    }

    reader.close()
    allocator.close()

    result

  }

  /**
  * Method for writing data to Apache Arrow file
    * @param input file for data, schema for data, data, allocator
    */
  def writeData(input: (File, Schema, mutable.ListBuffer[mutable.Map[String, Object]],  RootAllocator)): Unit = {

    val (inputFile, schema, data, allocator) = input

    val outStream = new FileOutputStream(inputFile)
    val rootSchema = VectorSchemaRoot.create(schema, allocator)
    val provider = new DictionaryProvider.MapDictionaryProvider()

    val dataWriter = new ArrowFileWriter(
      rootSchema,
      provider,
      outStream.getChannel
    )

    var counter = 0

    dataWriter.start()

    for(item <- data){

       val fields = rootSchema.getSchema.getFields.asScala

       for(field <- fields){

         if(item.contains(field.getName)){

           val data = item(field.getName)
           val vector = rootSchema.getVector(field.getName)

           val valueInfo = typesMap(vector.getMinorType)

           //TODO: REFACTOR!!!
           valueInfo.getName match {
             case "int"              => vector.asInstanceOf[IntVector].setSafe(counter, data.asInstanceOf[Int])
             case "boolean"          =>

               var value: Int = 0
               if(!data.asInstanceOf[Boolean]){
                 value = 1
               }
               vector.asInstanceOf[BitVector].setSafe(counter, value)

             case "java.lang.String" => vector.asInstanceOf[VarCharVector].setSafe(
               counter, new Text(data.asInstanceOf[String]
               )
             )
             case "float"            => vector.asInstanceOf[Float4Vector].setSafe(counter, data.asInstanceOf[Float])
             case "double"           => vector.asInstanceOf[Float8Vector].setSafe(counter, data.asInstanceOf[Double])
             case "long"             => vector.asInstanceOf[BigIntVector].setSafe(counter, data.asInstanceOf[Long])
             case "short"            => vector.asInstanceOf[SmallIntVector].setSafe(counter, data.asInstanceOf[Short])
             case _                  => throw new IllegalArgumentException(s"No mapper for type ${valueInfo.getName}")
           }

           counter += 1


         }

       }

    }

    dataWriter.writeBatch()
    dataWriter.end()
    dataWriter.close()

    outStream.flush()
    outStream.close()

  }

}
