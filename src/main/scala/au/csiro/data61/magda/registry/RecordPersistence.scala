package au.csiro.data61.magda.registry

import scalikejdbc._
import spray.json._

import scala.util.Try
import scala.util.{Failure, Success}
import java.sql.SQLException

import gnieh.diffson._
import gnieh.diffson.sprayJson._

object RecordPersistence extends Protocols with DiffsonProtocol {
  def getAll(implicit session: DBSession): Iterable[RecordSummary] = {
    tuplesToSummaryRecords(sql"""select recordID, Record.name as recordName, sectionID
                                 from Record
                                 left outer join RecordSection using (recordID)"""
      .map(recordSummaryRowToTuple)
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
  
  def getRecordSectionById(implicit session: DBSession, recordID: String, sectionID: String): Option[JsObject] = {
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
      return Failure(new RuntimeException("The provided ID does not match the record's id."))
    }

    // Update the record
    sql"""insert into Record (recordID, name) values ($id, ${record.name})
          on conflict (recordID) do update
          set name=${record.name}""".update.apply()

    // Update the sections
    record.sections.foreach(section => putRecordSectionById(session, id, section._1, section._2))

    Success(record)
  }

  def patchRecordById(implicit session: DBSession, id: String, recordPatch: JsonPatch): Try[Record] = {
    for {
      record <- this.getById(session, id) match {
        case Some(section) => Success(section)
        case None => Failure(new RuntimeException("No record exists with that ID."))
      }
      eventID <- Try {
        val event = PatchRecordEvent(id, recordPatch).toJson.compactPrint
        sql"insert into Events (eventTypeID, userID, data) values (${PatchRecordEvent.ID}, 0, $event::json)".updateAndReturnGeneratedKey().apply()
      }
      patchedRecord <- Try {
        // Don't include section changes when patching the record itself
        val recorddOnlyPatch = recordPatch.filter(op => op.path match {
          case "sections" / rest => false
          case anythingElse => true
        })

        val recordJson = record.toJson
        val patchedJson = recorddOnlyPatch(recordJson)
        patchedJson.convertTo[Record]
      }
      _ <- if (id == patchedRecord.id) Success(patchedRecord) else Failure(new RuntimeException("The patch must not change the record's ID."))
      _ <- Try {
        sql"""update Record set name = ${patchedRecord.name}, lastUpdate = $eventID where recordID = $id""".update.apply()
      }
      sectionResults <- Try {
        recordPatch.ops.groupBy(op => op.path match {
          case "sections" / (name / rest) => Some(name)
          case anythingElse => None
        }).filterKeys(!_.isEmpty).map({
          // Patch each section
          case (Some(sectionID), operations) => (sectionID, patchRecordSectionById(session, id, sectionID, JsonPatch(operations.map({
            // Make paths in operations relative to the section instead of the record
            case Add("sections" / (name / rest), value) => Add(rest, value)
            case Remove("sections" / (name / rest), old) => Remove(rest, old)
            case Replace("sections" / (name / rest), value, old) => Replace(rest, value, old)
            case Move("sections" / (sourceName / sourceRest), "sections" / (destName / destRest)) => Move(sourceRest, destRest)
            case Copy("sections" / (sourceName / sourceRest), "sections" / (destName / destRest)) => Copy(sourceRest, destRest)
            case Test("sections" / (name / rest), value) => Test(rest, value)
            case anythingElse => throw new RuntimeException("The patch contains an unsupported operation for section " + sectionID)
          }))))
          case anythingElse => throw new RuntimeException("Section ID is missing (this shouldn't be possible).")
        })
      }
      // Report the first failed section, if any
      _ <- sectionResults.find(_._2.isFailure) match {
        case Some((sectionID, failure)) => failure
        case anythingElse => Success(record)
      }
      // No failed sections, so unwrap the sections from the Success Trys.
      sections <- Success(sectionResults.map(section => (section._1, section._2.get)))
    } yield Record(patchedRecord.id, patchedRecord.name, sections)
  }

  def patchRecordSectionById(implicit session: DBSession, recordID: String, sectionID: String, sectionPatch: JsonPatch): Try[JsObject] = {
    for {
      section <- this.getRecordSectionById(session, recordID, sectionID) match {
        case Some(section) => Success(section)
        case None => Failure(new RuntimeException("No section exists on that record with that ID."))
      }
      eventID <- Try {
        val event = PatchRecordSectionEvent(recordID, sectionID, sectionPatch).toJson.compactPrint
        sql"insert into Events (eventTypeID, userID, data) values (${PatchRecordSectionEvent.ID}, 0, $event::json)".updateAndReturnGeneratedKey().apply()
      }
      patchedSection <- Try {
        sectionPatch(section).asJsObject
      }
      _ <- Try {
        val jsonString = patchedSection.compactPrint
        sql"""insert into RecordSection (recordID, sectionID, lastUpdate, data) values (${recordID}, ${sectionID}, $eventID, $jsonString::json)
             on conflict (recordID, sectionID) do update
             set lastUpdate = $eventID, data = $jsonString::json
             """.update.apply()
      }
    } yield patchedSection
  }

  def putRecordSectionById(implicit session: DBSession, recordID: String, sectionID: String, section: JsObject): Try[JsObject] = {
    val jsonData = section.compactPrint
    sql"""insert into RecordSection (recordID, sectionID, data)
          values ($recordID, ${sectionID}, $jsonData::json)
          on conflict (recordID, sectionID) do update
          set data=$jsonData::json""".update.apply()

    Success(section)
  }

  def createRecord(implicit session: DBSession, record: Record): Try[Record] = {
    for {
      eventID <- Try {
          val eventJson = CreateRecordEvent(record.id, record.name).toJson.compactPrint
          sql"insert into Events (eventTypeID, userID, data) values (${CreateRecordEvent.ID}, 0, $eventJson::json)".updateAndReturnGeneratedKey.apply()
      }
      insertResult <- Try {
        sql"""insert into Record (recordID, name, lastUpdate) values (${record.id}, ${record.name}, $eventID)""".update.apply()
      } match {
        case Failure(e: SQLException) if e.getSQLState().substring(0, 2) == "23" =>
          Failure(new RuntimeException(s"Cannot create record '${record.id}' because a record with that ID already exists."))
        case anythingElse => anythingElse
      }
      hasSectionFailure <- record.sections.map(section => createRecordSection(session, record.id, section._1, section._2)).find(_.isFailure) match {
        case Some(Failure(e)) => Failure(e)
        case anythingElse => Success(record)
      }
    } yield hasSectionFailure
  }

  def createRecordSection(implicit session: DBSession, recordID: String, sectionID: String, section: JsObject): Try[JsObject] = {
    for {
      eventID <- Try {
        val eventJson = CreateRecordSectionEvent(recordID, sectionID, section).toJson.compactPrint
        sql"insert into Events (eventTypeID, userID, data) values (${CreateRecordSectionEvent.ID}, 0, $eventJson::json)".updateAndReturnGeneratedKey.apply()
      }
      insertResult <- Try {
        val jsonData = section.compactPrint
        sql"""insert into RecordSection (recordID, sectionID, lastUpdate, data) values ($recordID, ${sectionID}, $eventID, $jsonData::json)""".update.apply()
        section
      } match {
        case Failure(e: SQLException) if e.getSQLState().substring(0, 2) == "23" =>
          Failure(new RuntimeException(s"Cannot create section '${sectionID}' for record '${recordID}' because the record or section does not exist, or because data already exists for that combination of record and section."))
        case anythingElse => anythingElse
      }
    } yield insertResult
  }

  private def recordSummaryRowToTuple(rs: WrappedResultSet) = (rs.string("recordID"), rs.string("recordName"), rs.string("sectionID"))
  private def recordRowWithDataToTuple(rs: WrappedResultSet) = (rs.string("recordID"), rs.string("recordName"), rs.string("sectionID"), rs.string("sectionName"), rs.stringOpt("data"))

  private def tuplesToSummaryRecords(tuples: List[(String, String, String)]): Iterable[RecordSummary] = {
    tuples.groupBy({ case (recordID, recordName, _) => (recordID, recordName) })
      .map {
        case ((recordID, recordName), value) =>
          RecordSummary(
            id = recordID,
            name = recordName,
            sections = value.filter({ case (_, _, sectionID) => sectionID != null })
              .map({ case (_, _, sectionID) => sectionID }))
      }
  }

  private def tuplesToRecords(tuples: List[(String, String, String, String, Option[String])]): Iterable[Record] = {
    tuples.groupBy({ case (recordID, recordName, _, _, _) => (recordID, recordName) })
          .map {
            case ((recordID, recordName), value) =>
              Record(
                id = recordID,
                name = recordName,
                sections = value.filter({ case (_, _, sectionID, _, data) => sectionID != null && data.isDefined })
                                .map({ case (_, _, sectionID, sectionName, data) =>
                                  (sectionID, JsonParser(data.get).asJsObject)
                                }).toMap)
          }
  }
  
  private def rowToSection(rs: WrappedResultSet): JsObject = {
    JsonParser(rs.string("data")).asJsObject
  }
}