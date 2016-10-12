package au.csiro.data61.magda.registry

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.Materializer
import akka.http.scaladsl.server.Directives._
import scalikejdbc._
import spray.json._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.ExceptionHandler

import scala.util.{Success, Failure}

class SectionsService(system: ActorSystem, materializer: Materializer) extends SectionProtocols with BadRequestProtocols {
  val getAll = complete {
      DB readOnly { session =>
        SectionPersistence.getAll(session)
      }
  }
  
  val getById = (id: String) => {
    DB readOnly { session =>
      SectionPersistence.getById(session, id) match {
        case Some(section) => complete(section)
        case None => complete(StatusCodes.NotFound, BadRequest("No section exists with that ID."))
      }
    }
  }
  
  val putById = (id: String) => {
    entity(as[Section]) { section =>
      DB localTx { implicit session => 
        SectionPersistence.putById(session, id, section) match {
          case Success(section) => complete(section)
          case Failure(exception) => complete(StatusCodes.BadRequest, BadRequest(exception.getMessage()))
        }
      }
    }
  }
  
  val createNew = entity(as[Section]) { section =>
    DB localTx { implicit session =>
      SectionPersistence.create(session, section) match {
        case Success(section) => complete(section)
        case Failure(exception) => complete(StatusCodes.BadRequest, BadRequest(exception.getMessage()))
      }
    }
  }
  
  val route =
    get { pathEnd { getAll } } ~
    get { path(Segment) { getById } } ~
    put { path(Segment) { putById } } ~
    post { pathEnd { createNew } }

  private def rowToSection(rs: WrappedResultSet): Section = new Section(rs.string("sectionID"), rs.string("name"), JsonParser(rs.string("jsonSchema")).asJsObject)
}
