package au.csiro.data61.magda.registry

import spray.json.{JsObject, JsValue}

case class Section(id: String, name: String, jsonSchema: JsObject) {
}
