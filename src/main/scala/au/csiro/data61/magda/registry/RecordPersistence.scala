package au.csiro.data61.magda.registry

import scalikejdbc._
import spray.json.JsonParser
import scala.util.Try
import scala.util.{Success, Failure}
import java.sql.SQLException

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
                          where sectionID in ($sectionIDs)"""
      .map(recordRowWithDataToTuple)
      .list.apply())
  }
  
  def getById(implicit session: DBSession, id: String): Option[Record] = {
    tuplesToRecords(sql"""select recordID, Record.name as recordName, sectionID, Section.name as sectionName, data
                          from Record
                          left outer join RecordSection using (recordID)
                          left outer join Section using (sectionID)
                          where recordID=$id"""
      .map(recordRowWithDataToTuple)
      .list.apply()).headOption
  }
  
  def getRecordSectionById(implicit session: DBSession, recordID: String, sectionID: String): Option[RecordSection] = {
    sql"""select RecordSection.sectionID as sectionID, name as sectionName, data from RecordSection
          inner join Section using (sectionID)
          where RecordSection.recordID=$recordID
          and RecordSection.sectionID=$sectionID"""
      .map(rowToSection)
      .single.apply()
  }
  
  def putRecordById(implicit session: DBSession, id: String, record: Record): Try[Record] = {
    if (id != record.id) {
      // TODO: we can do better than RuntimeException here.
      // TODO: Really, this is a silly API and this method shouldn't take an id at all.
      return Failure(new RuntimeException("The provided ID does not match the record's id."))
    }

    // Update the record
    sql"""insert into Record (recordID, name) values (${record.id}, ${record.name})
          on conflict (recordID) do update
          set name=${record.name}""".update.apply()

    // Update the sections
    record.sections.foreach { section =>
      val jsonData = section.data match {
        case Some(json) => json.compactPrint
        case None => null
      }
      sql"""insert into RecordSection (recordID, sectionID, data)
            values (${record.id}, ${section.id}, $jsonData::json)
            on conflict (recordID, sectionID) do update
            set data=$jsonData::json""".update.apply()
    }

    Success(record)
  }
  
  def createRecord(implicit session: DBSession, record: Record): Try[Record] = {
    // First create the Record
    try {
      sql"""insert into Record (recordID, name) values (${record.id}, ${record.name})""".update.apply()
    } catch {
      case e: SQLException => return Failure(new RuntimeException("A record with the specified ID already exists."))
    }
    
    // Then create the sections
    record.sections.foreach(createRecordSection(session, record.id, _))
    
    Success(record)
  }

  def createRecordSection(implicit session: DBSession, recordID: String, section: RecordSection): Try[RecordSection] = {
    val jsonData = section.data match {
      case Some(json) => json.compactPrint
      case None => null
    }
    sql"""insert into RecordSection (recordID, sectionID, data) values ($recordID, ${section.id}, $jsonData::json)""".update.apply()

    Success(section)
  }

  def putRecordSectionById(implicit session: DBSession, recordID: String, sectionID: String, section: RecordSection): Try[RecordSection] = {
    if (sectionID != section.id) {
      // TODO: we can do better than RuntimeException here.
      // TODO: Really, this is a silly API and this method shouldn't take an id at all.
      return Failure(new RuntimeException("The provided ID does not match the section's id."))
    }

    val jsonData = section.data match {
      case Some(json) => json.compactPrint
      case None => null
    }
    sql"""insert into RecordSection (recordID, sectionID, data)
          values ($recordID, ${section.id}, $jsonData::json)
          on conflict (recordID, sectionID) do update
          set data=$jsonData::json""".update.apply()

    Success(section)
  }

  private def recordRowToTuple(rs: WrappedResultSet) = (rs.string("recordID"), rs.string("recordName"), rs.string("sectionID"), rs.string("sectionName"), None)
  private def recordRowWithDataToTuple(rs: WrappedResultSet) = (rs.string("recordID"), rs.string("recordName"), rs.string("sectionID"), rs.string("sectionName"), rs.stringOpt("data"))

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
}