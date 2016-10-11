package au.csiro.data61.magda.registry

import spray.json.DefaultJsonProtocol

trait BadRequestProtocols extends DefaultJsonProtocol {
  implicit val badRequestFormat = jsonFormat1(BadRequest.apply)
}