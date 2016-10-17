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

object RecordPersistence {
  def getAll(implicit session: DBSession): Iterable[Record] = {
    tuplesToRecords(sql"""select recordID, Record.name as recordName, sectionID, Section.name as sectionName
                          from Record
                          left outer join RecordSection using (recordID)
                          left outer join Section using (sectionID)"""
      .map(recordRowToTuple)
      .list.apply())
      
  }

  def getAllWithSections(implicit session: DBSession, sectionIDs: Iterable[String]): Iterable[Record] = {
    tuplesToRecords(sql"""select recordID, Record.name as recordName, sectionID, Section.name as sectionName, data
                          from Record
                          left outer join RecordSection using (recordID)
                          left outer join Section using (sectionID)
                          where sectionID in (${sectionIDs})"""
      .map(recordRowWithDataToTuple)
      .list.apply())
  }
  
  def getById(implicit session: DBSession, id: String): Option[Record] = {
    tuplesToRecords(sql"""select recordID, Record.name as recordName, sectionID, Section.name as sectionName, data
                          from Record
                          left outer join RecordSection using (recordID)
                          left outer join Section using (sectionID)
                          where recordID=${id}"""
      .map(recordRowWithDataToTuple)
      .list.apply()).headOption
  }
  
  def getRecordSectionById(implicit session: DBSession, recordID: String, sectionID: String): Option[RecordSection] = {
    sql"""select RecordSection.sectionID as sectionID, name as sectionName, data from RecordSection
          inner join Section using (sectionID)
          where RecordSection.recordID=${recordID}
          and RecordSection.sectionID=${sectionID}"""
      .map(rowToSection)
      .single.apply()
  }
  
  def createRecord(implicit session: DBSession, record: Record): Try[Record] = {
    // First create the Record
    try {
      sql"""insert into Record (recordID, name) values (${record.id}, ${record.name})""".update.apply()
    } catch {
      case e: SQLException => return Failure(new RuntimeException("A record with the specified ID already exists."))
    }
    
    // Then create the sections
    record.sections.foreach { section =>
      val jsonData = section.data match {
        case Some(json) => json.compactPrint
        case None => null
      }
      sql"""insert into RecordSection (recordID, sectionID, data) values (${record.id}, ${section.id}, ${jsonData}::json)""".update.apply()
    }
    
    Success(record)
  }

  private def recordRowToTuple(rs: WrappedResultSet) = (rs.string("recordID"), rs.string("recordName"), rs.string("sectionID"), rs.string("sectionName"), None)
  private def recordRowWithDataToTuple(rs: WrappedResultSet) = (rs.string("recordID"), rs.string("recordName"), rs.string("sectionID"), rs.string("sectionName"), rs.stringOpt("data"))
  private def sectionRowToTuple(rs: WrappedResultSet) = (rs.string("sectionID"), rs.string("sectionName"), rs.stringOpt("data"))
  
  private def tuplesToRecords(tuples: List[(String, String, String, String, Option[String])]): Iterable[Record] = {
    tuples.groupBy({ case (recordID, recordName, _, _, _) => (recordID, recordName) })
          .map {
            case ((recordID, recordName), value) =>
              Record(
                id = recordID,
                name = recordName,
                sections = value.filter({ case (_, _, sectionID, _, _) => sectionID != null })
                                .map({ case (_, _, sectionID, sectionName, data) =>
                                  RecordSection(
                                    id = sectionID,
                                    name = sectionName,
                                    data = data.map(JsonParser(_).asJsObject))
                                }))
          }
  }
  
  private def rowToSection(rs: WrappedResultSet): RecordSection = {
    RecordSection(
        id = rs.string("sectionID"),
        name = rs.string("sectionName"),
        data = rs.stringOpt("data").map(JsonParser(_).asJsObject))
  }

//  def getById(implicit session: DBSession, id: String): Option[Record] = {
//    sql"select sectionID, name, jsonSchema from Section where sectionID=${id}".map(rs => rowToSection(rs)).single.apply()
//  }
//  
//  def putById(implicit session: DBSession, id: String, section: Section): Try[Section] = {
//    if (id != section.id) {
//      // TODO: we can do better than RuntimeException here.
//      return Failure(new RuntimeException("The provided ID does not match the section's id."))
//    }
//    
//    // Make sure we have a valid JSON Schema
//    val schemaValidationResult = validateJsonSchema(section.jsonSchema);
//    if (schemaValidationResult.size > 0) {
//      var lines = "The provided JSON Schema is not valid:" ::  
//                  schemaValidationResult.map(_.getMessage())
//      var message = lines.mkString("\n")
//      
//      // TODO: include details of the validation failure.
//      return Failure(new RuntimeException(message))
//    }
//    
//    // Make sure existing data for this section matches the new JSON Schema
//    // TODO
//
//    val jsonString = section.jsonSchema.compactPrint
//    sql"""insert into Section (sectionID, name, jsonSchema) values (${section.id}, ${section.name}, ${jsonString}::json)
//          on conflict (sectionID) do update
//          set name = ${section.name}, jsonSchema = ${jsonString}::json
//          """.update.apply()
//    Success(section)
//  }
//  
//  def create(implicit session: DBSession, section: Section): Try[Section] = {
//    try {
//      sql"insert into Section (sectionID, name, jsonSchema) values (${section.id}, ${section.name}, ${section.jsonSchema.compactPrint}::json)".update.apply()
//      Success(section)
//    } catch {
//      case e: SQLException => Failure(new RuntimeException("A section with the specified ID already exists."))
//    }
//  }
  
  private def rowToRecordSummary(rs: WrappedResultSet): Section = {
    ???    
//    new Record(
//      rs.string("recordID"), rs.string("recordName"), JsonParser(rs.string("jsonSchema")).asJsObject)
  }
  
//  private def validateJsonSchema(jsonSchema: JsObject): List[ValidationMessage] = {
//    // TODO: it's super inefficient format the JSON as a string only to parse it back using a different library.
//    //       it'd be nice if we had a spray-json based JSON schema validator.
//    val jsonString = jsonSchema.compactPrint
//    val jsonNode = new ObjectMapper().readValue(jsonString, classOf[JsonNode])
//    jsonSchemaSchema.validate(jsonNode).asScala.toList
//  }
//  
//  private val jsonSchemaSchema = new JsonSchemaFactory().getSchema(getClass.getResourceAsStream("/json-schema.json"))
}