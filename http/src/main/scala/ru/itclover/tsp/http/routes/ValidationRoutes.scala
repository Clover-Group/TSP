package ru.itclover.tsp.http.routes
// import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import cats.data.Reader
import ru.itclover.tsp.core.io.{Decoder, TimeExtractor}
import ru.itclover.tsp.core.{RawPattern, Time}
import ru.itclover.tsp.dsl.{PatternsValidator, PatternsValidatorConf}
import ru.itclover.tsp.http.protocols.{PatternsValidatorProtocols, RoutesProtocols, ValidationResult}

import scala.concurrent.ExecutionContextExecutor

object ValidationRoutes {

  def fromExecutionContext(
    monitoringUri: Uri
  )(implicit am: ActorMaterializer): Reader[ExecutionContextExecutor, Route] =
    Reader(_ => new ValidationRoutes {}.route)
}

trait ValidationRoutes extends RoutesProtocols with PatternsValidatorProtocols {

  val route: Route = path("patterns" / "validate"./) {
    entity(as[PatternsValidatorConf]) { request =>
      val patterns: Seq[RawPattern] = request.patterns
      val fields: Map[String, String] = request.fields
      val res = PatternsValidator.validate[Nothing](patterns, fields)(
        new TimeExtractor[Nothing] { override def apply(v1: Nothing): Time = Time(0) },
        new Decoder[Any, Double] {
          override def apply(v1: Any): Double = 0.0
        }
      )
      val result = res.map { x =>
        x._2 match {
          case Right(success) =>
            ValidationResult(pattern = x._1, success = true, context = success.toString)
          case Left(error) =>
            ValidationResult(pattern = x._1, success = false, context = error.toString)
        }
      }
      complete(result)
    }
  }
}
