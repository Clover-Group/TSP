package ru.itclover.tsp.spark.io

import org.apache.spark.sql.Row

trait OutputConf[Event] {
  def forwardedFieldsIds: Seq[Symbol]

  def parallelism: Option[Int]

  def rowSchema: RowSchema
}

/**
  * Sink for anything that support JDBC connection
  * @param rowSchema schema of writing rows, __will be replaced soon__
  * @param jdbcUrl example - "jdbc:clickhouse://localhost:8123/default?"
  * @param driverName example - "ru.yandex.clickhouse.ClickHouseDriver"
  * @param userName for JDBC auth
  * @param password for JDBC auth
  * @param batchInterval batch size for writing found incidents
  * @param parallelism num of parallel task to write data
  */
case class JDBCOutputConf(
                           tableName: String,
                           rowSchema: RowSchema,
                           jdbcUrl: String,
                           driverName: String,
                           password: Option[String] = None,
                           batchInterval: Option[Int] = None,
                           userName: Option[String] = None,
                           parallelism: Option[Int] = Some(1)
) extends OutputConf[Row] {
  override def forwardedFieldsIds = Seq.empty
}

case class KafkaOutputConf(
                            broker: String,
                            topic: String,
                            //serializer: Option[String] = Some("json"),
                            rowSchema: RowSchema,
                            parallelism: Option[Int] = Some(1)
) extends OutputConf[Row] {
  override def forwardedFieldsIds = Seq.empty

}
