package au.csiro.data61.magda.registry

import spray.json.{JsObject, JsValue}

case class Record(id: String, name: String, sections: List[RecordSection]) {
}
