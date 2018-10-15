package ru.itclover.tsp.http.protocols
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import ru.itclover.tsp.dsl.PatternsValidatorConf
import ru.itclover.tsp.dsl.schema.RawPattern
import spray.json.DefaultJsonProtocol

case class ValidationResult(pattern: RawPattern, success: Boolean, context: String)

trait PatternsValidatorProtocols extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val rawPattern = jsonFormat4(RawPattern.apply)
  implicit val patterns = jsonFormat1(PatternsValidatorConf.apply)
  implicit val patternResult = jsonFormat3(ValidationResult.apply)
}
