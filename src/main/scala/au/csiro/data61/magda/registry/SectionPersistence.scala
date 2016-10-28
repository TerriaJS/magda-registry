package au.csiro.data61.magda.registry

import scalikejdbc._
import spray.json.JsonParser

import scala.util.Try
import scala.util.{Failure, Success}
import java.sql.SQLException

import spray.json._
import gnieh.diffson.sprayJson._

object SectionPersistence extends Protocols with DiffsonProtocol {
  def getAll(implicit session: DBSession): List[Section] = {
    sql"select sectionID, name, jsonSchema from Section".map(rowToSection).list.apply()
  }
  
  def getById(implicit session: DBSession, id: String): Option[Section] = {
    sql"""select sectionID, name, jsonSchema from Section where sectionID=$id""".map(rowToSection).single.apply()
  }
  
  def putById(implicit session: DBSession, id: String, newSection: Section): Try[Section] = {
    if (id != newSection.id) {
      // TODO: we can do better than RuntimeException here.
      return Failure(new RuntimeException("The provided ID does not match the section's id."))
    }

    // Read the existing section
    val oldSection = this.getById(session, id)
    if (oldSection.isEmpty) {
      return Failure(new RuntimeException("No section exists with that ID."))
    }

    // Diff the old section and the new one
    val oldSectionJson = oldSection.toJson
    val newSectionJson = newSection.toJson

    val diff = JsonDiff.diff(oldSectionJson, newSectionJson, false)
    val event = PatchSectionDefinitionEvent(id, diff)
    val eventString = event.toJson.compactPrint

    // Create a 'Patch Section' event with the diff
    sql"insert into Events (eventTypeID, userID, data) values (${PatchSectionDefinitionEvent.ID}, 0, $eventString::json)".update.apply()

    // Patch the old section
    val patchedSectionJson = diff(oldSectionJson)
    val patchedSection = patchedSectionJson.convertTo[Section]

    if (id != patchedSection.id) {
      return Failure(new RuntimeException("The patch must not change the section's ID."))
    }

    // Write back the modified Section
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

  def patchById(implicit session: DBSession, id: String, sectionPatch: JsonPatch): Try[Section] = {
    for {
      section <- this.getById(session, id) match {
        case Some(section) => Success(section)
        case None => Failure(new RuntimeException("No section exists with that ID."))
      }
      patchedSection <- Try {
        val event = PatchSectionDefinitionEvent(id, sectionPatch).toJson.compactPrint
        sql"insert into Events (eventTypeID, userID, data) values (${PatchSectionDefinitionEvent.ID}, 0, $event::json)".update.apply()

        val sectionJson = section.toJson
        val patchedJson = sectionPatch(sectionJson)
        patchedJson.convertTo[Section]
      }
      if (id == patchedSection.id)
      _ <- Try {
        val jsonString = patchedSection.jsonSchema match {
          case Some(jsonSchema) => jsonSchema.compactPrint
          case None => null
        }
        sql"""insert into Section (sectionID, name, jsonSchema) values (${patchedSection.id}, ${patchedSection.name}, $jsonString::json)
             on conflict (sectionID) do update
             set name = ${patchedSection.name}, jsonSchema = $jsonString::json
             """.update.apply()
      }
    } yield patchedSection
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
}