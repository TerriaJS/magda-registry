package au.csiro.data61.magda.registry

import scalikejdbc._
import spray.json.JsonParser
import scala.util.Try
import scala.util.{Success, Failure}
import java.sql.SQLException
import spray.json.JsObject
import com.networknt.schema.ValidationMessage
import collection.JavaConverters._
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode

object SectionPersistence {
  def getAll(implicit session: DBSession): List[Section] = {
    sql"select sectionID, name, jsonSchema from Section".map(rs => rowToSection(rs)).list.apply()
  }
  
  def getById(implicit session: DBSession, id: String): Option[Section] = {
    sql"select sectionID, name, jsonSchema from Section where sectionID=${id}".map(rs => rowToSection(rs)).single.apply()
  }
  
  def putById(implicit session: DBSession, id: String, section: Section): Try[Section] = {
    if (id != section.id) {
      // TODO: we can do better than RuntimeException here.
      return Failure(new RuntimeException("The provided ID does not match the section's id."))
    }
    
    // Make sure we have a valid JSON Schema
    if (!section.jsonSchema.isEmpty) {
      val schemaValidationResult = validateJsonSchema(section.jsonSchema.get);
      if (schemaValidationResult.size > 0) {
        var lines = "The provided JSON Schema is not valid:" ::  
                    schemaValidationResult.map(_.getMessage())
        var message = lines.mkString("\n")
        return Failure(new RuntimeException(message))
      }
    }
    
    // Make sure existing data for this section matches the new JSON Schema
    // TODO

    val jsonString = section.jsonSchema match {
      case Some(jsonSchema) => jsonSchema.compactPrint
      case None => null
    }
    sql"""insert into Section (sectionID, name, jsonSchema) values (${section.id}, ${section.name}, ${jsonString}::json)
          on conflict (sectionID) do update
          set name = ${section.name}, jsonSchema = ${jsonString}::json
          """.update.apply()
    Success(section)
  }
  
  def create(implicit session: DBSession, section: Section): Try[Section] = {
    try {
      val jsonString = section.jsonSchema match {
        case Some(jsonSchema) => jsonSchema.compactPrint
        case None => null
      }
      sql"insert into Section (sectionID, name, jsonSchema) values (${section.id}, ${section.name}, ${jsonString}::json)".update.apply()
      Success(section)
    } catch {
      case e: SQLException => Failure(new RuntimeException("A section with the specified ID already exists."))
    }
  }
  
  private def rowToSection(rs: WrappedResultSet): Section = {
    var jsonSchema: Option[JsObject] = if (rs.string("jsonSchema") == null) None else Some(JsonParser(rs.string("jsonSchema")).asJsObject)
    new Section(
        rs.string("sectionID"),
        rs.string("name"),
        jsonSchema)
  }
  
  private def validateJsonSchema(jsonSchema: JsObject): List[ValidationMessage] = {
    // TODO: it's super inefficient format the JSON as a string only to parse it back using a different library.
    //       it'd be nice if we had a spray-json based JSON schema validator.
    val jsonString = jsonSchema.compactPrint
    val jsonNode = new ObjectMapper().readValue(jsonString, classOf[JsonNode])
    jsonSchemaSchema.validate(jsonNode).asScala.toList
  }
  
  private val jsonSchemaSchema = new JsonSchemaFactory().getSchema(getClass.getResourceAsStream("/json-schema.json"))
}