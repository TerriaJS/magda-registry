package au.csiro.data61.magda.registry

import io.swagger.annotations.{ApiModel, ApiModelProperty}
import spray.json.JsObject

import scala.annotation.meta.field

@ApiModel(description = "A section of a record in the registry.")
case class RecordSection(
  @(ApiModelProperty @field)(value = "The unique identifier of the section", required = true)
  id: String,

  @(ApiModelProperty @field)(value = "The name of the section", required = true)
  name: String,

  @(ApiModelProperty @field)(value = "The JSON data of this section", required = false, dataType = "object")
  data: Option[JsObject]
)
