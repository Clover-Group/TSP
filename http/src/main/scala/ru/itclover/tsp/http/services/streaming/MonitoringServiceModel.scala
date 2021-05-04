package ru.itclover.tsp.http.services.streaming

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.unmarshalling.Unmarshaller
import spray.json._

object MonitoringServiceModel {

  /** @param metrics - set of metrics names and values */
  case class JobDetailsWithMetrics(details: JobDetails, metrics: Map[String, String])

  case class JobDetails(
                         jobId: String,
                         isStream: Boolean,
                         state: String,
                         startTsMs: Long,
                         durationMs: Long,
                         sparkJobs: Vector[SparkJob]
  ) {
    // note vice-versa
    // def readRecords = sparkJobs.head.metrics.writeRecords
    // def writeRecords: Long = sparkJobs.last.metrics.readRecords
  }

  case class Metric(id: String, value: String)

  case class MetricName(id: String)

  case class MetricInfo(vertexIndex: Int, id: String, name: String)

  object MetricInfo {
    def onLastVertex(id: String, name: String) = MetricInfo(Int.MaxValue, id, name)
  }

  case class SparkJob(id: String, name: String, readRecords: Int)

  case class JobsOverview(jobs: List[JobBrief])

  case class JobBrief(jid: String, status: String, isStream: Boolean)

  case class JobExceptions(timestamp: Long, rootException: String, truncated: Boolean)

  case class EmptyResponse()

  case class MonitoringException(err: String) extends RuntimeException(err)

  case class MonitoringError(errors: Seq[String]) {
    def toThrowable = MonitoringException(errors.mkString("; "))
  }

}

trait MonitoringServiceProtocols extends SprayJsonSupport with DefaultJsonProtocol {
  import MonitoringServiceModel._

  implicit val jobExceptionsFormat = jsonFormat(JobExceptions.apply, "timestamp", "root-exception", "truncated")
  implicit val emptyFormat = jsonFormat0(EmptyResponse.apply _)
  implicit val metricFormat = jsonFormat2(Metric.apply)
  implicit val metricNameFormat = jsonFormat1(MetricName.apply)
  implicit val monitoringErrorFormat = jsonFormat1(MonitoringError.apply)

  implicit val vertexFormat = jsonFormat3(SparkJob.apply)
  implicit object jobFormat extends RootJsonFormat[JobDetails] {
    override def write(obj: JobDetails): JsValue = JsObject(
      ("jobId", JsString(obj.jobId)),
      ("isStream", JsBoolean(obj.isStream)),
      ("state", JsString(obj.state)),
      ("start-time", JsNumber(obj.startTsMs)),
      ("duration", JsNumber(obj.durationMs)),
      ("sparkJobs", JsArray(obj.sparkJobs.map(vertexFormat.write)))
    )

    override def read(json: JsValue): JobDetails = json match {
      case JsObject(fields) =>
        JobDetails(
          fields("jobId").convertTo[String],
          fields("isStream").convertTo[Boolean],
          fields("state").convertTo[String],
          fields("start-time").convertTo[Long],
          fields("duration").convertTo[Long],
          fields("vertices").convertTo[Vector[SparkJob]]
        )
      case _ => throw new DeserializationException(s"Cannot deserialize $json as JobDetails")
    }
  }
  implicit val jobBriefFormat = jsonFormat3(JobBrief.apply)
  implicit val jobInfoFormat = jsonFormat3(MetricInfo.apply)
  implicit val jobOverviewFormat = jsonFormat1(JobsOverview.apply)
  implicit val jobDetailsAndMetricsFormat = jsonFormat2(JobDetailsWithMetrics.apply)

  implicit val errorOrDetailsUnmarshaller = Unmarshaller.eitherUnmarshaller[MonitoringError, JobDetails]
  implicit val errorOrInfoUnmarshaller = Unmarshaller.eitherUnmarshaller[MonitoringError, JobsOverview]
  implicit val errorOrUnitUnmarshaller = Unmarshaller.eitherUnmarshaller[MonitoringError, EmptyResponse]
  implicit val errorOrMetricUnmarshaller = Unmarshaller.eitherUnmarshaller[MonitoringError, Metric]
  implicit val emptyOrDetailsUnmarshaller = Unmarshaller.eitherUnmarshaller[EmptyResponse, JobDetails]
}
