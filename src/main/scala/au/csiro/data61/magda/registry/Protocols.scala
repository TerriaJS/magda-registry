package au.csiro.data61.magda.registry

import spray.json.DefaultJsonProtocol

trait Protocols extends DefaultJsonProtocol {
  implicit val badRequestFormat = jsonFormat1(BadRequest.apply)
  implicit val sectionFormat = jsonFormat3(Section.apply)
  implicit val recordSectionFormat = jsonFormat3(RecordSection.apply)
  implicit val recordFormat = jsonFormat3(Record.apply)
  implicit val recordSummaryFormat = jsonFormat3(RecordSummary.apply)
}