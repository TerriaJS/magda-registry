package au.csiro.data61.magda.registry

import spray.json.JsObject

case class RecordSection(id: String, name: String, data: Option[JsObject]) {
}
