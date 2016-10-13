package au.csiro.data61.magda.registry

import scalikejdbc._
import spray.json.JsonParser
import scala.util.Try
import scala.util.{Success, Failure}
import java.sql.SQLException
import spray.json.JsObject
import com.networknt._

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
    
    val schemaValidationResult = validateJsonSchema(section.jsonSchema);
    if (schemaValidationResult.size() > 0) {
      // TODO: include details of the validation failure.
      return Failure(new RuntimeException("The provided JSON Schema is not valid."))
    }

    val jsonString = section.jsonSchema.compactPrint
    sql"""insert into Section (sectionID, name, jsonSchema) values (${section.id}, ${section.name}, ${jsonString}::json)
          on conflict (sectionID) do update
          set name = ${section.name}, jsonSchema = ${jsonString}::json
          """.update.apply()
    Success(section)
  }
  
  def create(implicit session: DBSession, section: Section): Try[Section] = {
    try {
      sql"insert into Section (sectionID, name, jsonSchema) values (${section.id}, ${section.name}, ${section.jsonSchema.compactPrint}::json)".update.apply()
      Success(section)
    } catch {
      case e: SQLException => Failure(new RuntimeException("A section with the specified ID already exists."))
    }
  }
  
  private def rowToSection(rs: WrappedResultSet): Section = new Section(rs.string("sectionID"), rs.string("name"), JsonParser(rs.string("jsonSchema")).asJsObject)
  
  private def validateJsonSchema(jsonSchema: JsObject): Set[ValidationMessage] = {
  }
}