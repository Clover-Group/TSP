package ru.itclover.streammachine.http.routes

import java.util.concurrent.TimeUnit
import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.Logger
import ru.itclover.streammachine.http.domain.output.{FailureResponse, SuccessfulResponse}
import ru.itclover.streammachine.http.protocols.RoutesProtocols
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import cats.data.Reader
import ru.itclover.streammachine.BuildInfo
import ru.itclover.streammachine.http.services.flink.{MonitoringService, MonitoringServiceProtocols}
import ru.itclover.streammachine.utils.Exceptions
import scala.util.{Failure, Success}

object MonitoringRoutes {
  def fromExecutionContext(monitoringUri: Uri)(implicit as: ActorSystem, am: ActorMaterializer): Reader[ExecutionContextExecutor, Route] =
    Reader { execContext =>
      new MonitoringRoutes {
        override implicit val executionContext = execContext
        override implicit val actors = as
        override implicit val materializer = am
        override val uri = monitoringUri
      }.route
    }
}


trait MonitoringRoutes extends RoutesProtocols with MonitoringServiceProtocols {
  implicit val executionContext: ExecutionContextExecutor
  implicit val actors: ActorSystem
  implicit val materializer: ActorMaterializer

  val uri: Uri
  lazy val monitoring = MonitoringService(uri)

  val noSuchJobWarn = "No such job or no connection to the FlinkMonitoring"

  private val log = Logger[MonitoringRoutes]

  val route: Route = path("job" / Segment / "status"./) { uuid =>
    onComplete(monitoring.queryJobInfo(uuid)) {
      case Success(Some(details)) => complete(details)
      case Success(None) => complete(SuccessfulResponse(0, Seq(noSuchJobWarn)))
      case Failure(err) => complete(InternalServerError, FailureResponse(5005, err))
    }
  } ~
  path("job" / Segment / "stop"./) { uuid =>
    val query = monitoring.sendStopQuery(uuid).map {
      case Some(_) => SuccessfulResponse(1)
      case None => SuccessfulResponse(0, Seq(noSuchJobWarn))
    }
    onComplete(query) {
      case Success(resp) => complete(resp)
      case Failure(err) => complete(InternalServerError, FailureResponse(5005, err))
    }
  } ~
  path("jobs" / "overview"./) {
    onComplete(monitoring.queryJobsOverview) {
      case Success(Some(resp)) => complete(resp)
      case Success(None) => complete(SuccessfulResponse(0, Seq("No jobs or no connection to the FlinkMonitoring")))
      case Failure(err) => complete(InternalServerError, FailureResponse(5005, err))
    }
  } ~
  path("metainfo" /  "getVersion"./) {
    complete(SuccessfulResponse(BuildInfo.version))
  }
}