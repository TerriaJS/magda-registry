package au.csiro.data61.magda.registry

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.Materializer
import akka.http.scaladsl.server.Directives._
import scalikejdbc._
import spray.json._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.ExceptionHandler
import scala.util.Failure
import scala.util.Success

class RecordsService(system: ActorSystem, materializer: Materializer) extends RecordProtocols with BadRequestProtocols {
  val route =
    get { pathEnd { parameters('section.*) { getAll } } } ~
    get { path(Segment) {getById } } ~
    put { path(Segment) { putRecordById } } ~
    post { pathEnd { createRecord } } ~
    get { path(Segment / "sections" / Segment) { getRecordSectionById } } ~
    put { path(Segment / "sections" / Segment) { putRecordSectionById } } ~
    post { path(Segment / "sections") { createRecordSection } }

  private val getAll = (sections: Iterable[String]) => {
    complete {
      DB readOnly { session =>
        if (sections.isEmpty)
          RecordPersistence.getAll(session)
        else
          RecordPersistence.getAllWithSections(session, sections)
      }
    }
  }
  
  private val getById = (id: String) => {
    DB readOnly { session =>
      RecordPersistence.getById(session, id) match {
        case Some(section) => complete(section)
        case None => complete(StatusCodes.NotFound, BadRequest("No record exists with that ID."))
      }
    }
  }

  private val getRecordSectionById = (recordID: String, sectionID: String) => {
    DB readOnly { session =>
      RecordPersistence.getRecordSectionById(session, recordID, sectionID) match {
        case Some(recordSection) => complete(recordSection)
        case None => complete(StatusCodes.NotFound, BadRequest("No record section exists with that ID."))
      }
    }
  }

  private val putRecordById = (id: String) => {
    entity(as[Record]) { record =>
      DB localTx { session => 
        RecordPersistence.putRecordById(session, id, record) match {
          case Success(section) => complete(record)
          case Failure(exception) => complete(StatusCodes.BadRequest, BadRequest(exception.getMessage()))
        }
      }
    }
  }

  private val createRecord = entity(as[Record]) { record =>
    DB localTx { session =>
      RecordPersistence.createRecord(session, record) match {
        case Success(record) => complete(record)
        case Failure(exception) => complete(StatusCodes.BadRequest, BadRequest(exception.getMessage()))
      }
    }
  }

  private val createRecordSection = (recordID: String) => {
    entity(as[RecordSection]) { section =>
      DB localTx { session =>
        RecordPersistence.createRecordSection(session, recordID, section) match {
          case Success(section) => complete(section)
          case Failure(exception) => complete(StatusCodes.BadRequest, BadRequest(exception.getMessage()))
        }
      }
    }
  }

  private val putRecordSectionById = (recordID: String, sectionID: String) => {
    entity(as[RecordSection]) { section =>
      DB localTx { session =>
        RecordPersistence.putRecordSectionById(session, recordID, sectionID, section) match {
          case Success(section) => complete(section)
          case Failure(exception) => complete(StatusCodes.BadRequest, BadRequest(exception.getMessage()))
        }
      }
    }
  }
}
