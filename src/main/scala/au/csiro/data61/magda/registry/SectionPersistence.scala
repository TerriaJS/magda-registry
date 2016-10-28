package au.csiro.data61.magda.registry

import scalikejdbc._
import spray.json.JsonParser
import scala.util.Try
import scala.util.{Success, Failure}
import java.sql.SQLException
import spray.json._
import gnieh.diffson.sprayJson._

object SectionPersistence extends Protocols {
  def getAll(implicit session: DBSession): List[Section] = {
    sql"select sectionID, name, jsonSchema from Section".map(rowToSection).list.apply()
  }
  
  def getById(implicit session: DBSession, id: String): Option[Section] = {
    sql"""select sectionID, name, jsonSchema from Section where sectionID=$id""".map(rowToSection).single.apply()
  }
  
  def putById(implicit session: DBSession, id: String, section: Section): Try[Section] = {
    if (id != section.id) {
      // TODO: we can do better than RuntimeException here.
      return Failure(new RuntimeException("The provided ID does not match the section's id."))
    }
    
//    // Make sure we have a valid JSON Schema
//    if (section.jsonSchema.isDefined) {
//      val schemaValidationResult = validateJsonSchema(section.jsonSchema.get)
//      if (schemaValidationResult.nonEmpty) {
//        val lines = "The provided JSON Schema is not valid:" ::
//                    schemaValidationResult.map(_.getMessage())
//        val message = lines.mkString("\n")
//        return Failure(new RuntimeException(message))
//      }
//    }
    
    // Make sure existing data for this section matches the new JSON Schema
    // TODO

    val jsonString = section.jsonSchema match {
      case Some(jsonSchema) => jsonSchema.compactPrint
      case None => null
    }
    sql"""insert into Section (sectionID, name, jsonSchema) values (${section.id}, ${section.name}, $jsonString::json)
          on conflict (sectionID) do update
          set name = ${section.name}, jsonSchema = $jsonString::json
          """.update.apply()
    Success(section)
  }

  def patchById(implicit session: DBSession, id: String, sectionPatchJson: String): Try[Section] = {
    val sectionPatch = JsonPatch.parse(sectionPatchJson)

    // Create a 'Patch Section' event
    sql"insert into Events (eventTypeID, userID, data) values (4, 0, $sectionPatchJson::json)".update.apply();

    // Read the existing section
    val section = this.getById(session, id)
    if (section.isEmpty) {
      Failure(new RuntimeException("No section exists with that ID."))
    } else {
      val sectionJson = section.get.toJson
      val patchedJson = sectionPatch(sectionJson)
      val patchedSection = patchedJson.convertTo[Section]

      if (id != patchedSection.id) {
        // TODO: we can do better than RuntimeException here.
        Failure(new RuntimeException("The patch must not change the section's ID."))
      } else {
        val jsonString = patchedSection.jsonSchema match {
          case Some(jsonSchema) => jsonSchema.compactPrint
          case None => null
        }
        sql"""insert into Section (sectionID, name, jsonSchema) values (${patchedSection.id}, ${patchedSection.name}, $jsonString::json)
          on conflict (sectionID) do update
          set name = ${patchedSection.name}, jsonSchema = $jsonString::json
          """.update.apply()
        Success(patchedSection)
      }
    }
  }

  def create(implicit session: DBSession, section: Section): Try[Section] = {
    // Create a 'Create Section' event
    val sectionJson = section.toJson.compactPrint
    sql"insert into Events (eventTypeID, userID, data) values (1, 0, $sectionJson::json)".update.apply();

    // Create the actual Section
    try {
      val jsonString = section.jsonSchema match {
        case Some(jsonSchema) => jsonSchema.compactPrint
        case None => null
      }
      sql"insert into Section (sectionID, name, jsonSchema) values (${section.id}, ${section.name}, $jsonString::json)".update.apply()
      Success(section)
    } catch {
      case e: SQLException => Failure(new RuntimeException("A section with the specified ID already exists."))
    }
  }
  
  private def rowToSection(rs: WrappedResultSet): Section = {
    val jsonSchema: Option[JsObject] = if (rs.string("jsonSchema") == null) None else Some(JsonParser(rs.string("jsonSchema")).asJsObject)
    Section(
      rs.string("sectionID"),
      rs.string("name"),
      jsonSchema)
  }
  
//  private def validateJsonSchema(jsonSchema: JsObject): List[ValidationMessage] = {
//    // TODO: it's super inefficient format the JSON as a string only to parse it back using a different library.
//    //       it'd be nice if we had a spray-json based JSON schema validator.
//    val jsonString = jsonSchema.compactPrint
//    val jsonNode = new ObjectMapper().readValue(jsonString, classOf[JsonNode])
//    jsonSchemaSchema.validate(jsonNode).asScala.toList
//  }
  
//  private val jsonSchemaSchema = new JsonSchemaFactory().getSchema(getClass.getResourceAsStream("/json-schema.json"))
}