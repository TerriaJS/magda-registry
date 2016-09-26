package au.csiro.data61.magda.registry

import spray.json.DefaultJsonProtocol

trait SectionProtocols extends DefaultJsonProtocol {
  implicit val sectionFormat = jsonFormat2(Section.apply)
}