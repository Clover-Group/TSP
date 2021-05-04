package ru.itclover.tsp.http.services.streaming

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.apache.spark.sql.SparkSession
import ru.itclover.tsp.http.services.streaming.MonitoringServiceModel._

import java.sql.DriverManager
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class SparkMonitoringService(spark: SparkSession)(
  implicit as: ActorSystem,
  am: ActorMaterializer,
  ec: ExecutionContext
) extends MonitoringServiceProtocols {

  val databaseConnection = DriverManager.getConnection("jdbc:h2:mem:tsp_data")

  def queryJobInfo(name: String): Future[Option[JobDetails]] = Future {
    Try {
      val basicStatement = databaseConnection.prepareStatement("SELECT stream, status FROM jobs WHERE name = ?")
      basicStatement.setString(1, name)
      val basicResultSet = basicStatement.executeQuery()
      basicResultSet.next()
      val stream = basicResultSet.getBoolean("stream")
      val status = basicResultSet.getString("status")
      val streamingQueryStatement =
        databaseConnection.prepareStatement("SELECT id, status, read_rows FROM streaming_queries WHERE job_name = ?")
      streamingQueryStatement.setString(1, name)
      val streamingQueryResultSet = streamingQueryStatement.executeQuery()
      val streamingQueries = Iterator.continually {
        val id = streamingQueryResultSet.getInt("id")
        val status = streamingQueryResultSet.getString("status")
        val readRows = streamingQueryResultSet.getInt("readRows")
        SparkJob(id.toString, status, readRows)
      }.takeWhile(_ => streamingQueryResultSet.next()).toVector
      JobDetails(name, stream, status, 0, 0, streamingQueries) // TODO: Jobs
    }.toOption
  }

  def queryJobExceptions(name: String): Future[Option[JobExceptions]] = Future { None }

  def sendStopQuery(jobName: String): Future[Option[Unit]] = Future {
    Try(spark.sparkContext.cancelJobGroup(jobName)).toOption
  }

  def queryJobsOverview: Future[JobsOverview] = Future {
    val statement = databaseConnection.createStatement()
    val resultSet = statement.executeQuery("SELECT * FROM jobs")
    JobsOverview(
      Iterator.continually {
        val name = resultSet.getString("name")
        val stream = resultSet.getBoolean("stream")
        val status = resultSet.getString("status")
        JobBrief(name, status, stream)
      }.takeWhile(_ => resultSet.next()).toList
    )
  }

}
