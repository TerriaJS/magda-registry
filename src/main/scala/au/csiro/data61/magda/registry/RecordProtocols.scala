package au.csiro.data61.magda.registry

import spray.json.DefaultJsonProtocol

trait RecordProtocols extends DefaultJsonProtocol {
  implicit val recordSectionFormat = jsonFormat3(RecordSection.apply)
  implicit val recordFormat = jsonFormat3(Record.apply)
  implicit val recordSummaryFormat = jsonFormat3(RecordSummary.apply)
}