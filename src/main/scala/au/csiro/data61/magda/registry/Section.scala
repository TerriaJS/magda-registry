package au.csiro.data61.magda.registry

import spray.json.JsObject

case class Section(id: String, name: String, jsonSchema: Option[JsObject]) {
}
