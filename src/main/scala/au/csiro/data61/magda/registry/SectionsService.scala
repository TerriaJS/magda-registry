package au.csiro.data61.magda.registry

import javax.ws.rs.Path

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.Materializer
import akka.http.scaladsl.server.Directives._
import scalikejdbc._
import akka.http.scaladsl.model.StatusCodes
import io.swagger.annotations._

import scala.util.{Success, Failure}

@Path("/sections")
@io.swagger.annotations.Api(value = "/sections", produces = "application/json")
class SectionsService(system: ActorSystem, materializer: Materializer) extends Protocols {
  val route =
    get { pathEnd { getAll } } ~
    get { path(Segment) { getById } } ~
    put { path(Segment) { putById } } ~
    post { pathEnd { createNew } }

  private def getAll = complete {
      DB readOnly { session =>
        SectionPersistence.getAll(session)
      }
  }

  private def getById(id: String) = {
    DB readOnly { session =>
      SectionPersistence.getById(session, id) match {
        case Some(section) => complete(section)
        case None => complete(StatusCodes.NotFound, BadRequest("No section exists with that ID."))
      }
    }
  }
  
  private def putById(id: String) = {
    entity(as[Section]) { section =>
      DB localTx { session => 
        SectionPersistence.putById(session, id, section) match {
          case Success(result) => complete(result)
          case Failure(exception) => complete(StatusCodes.BadRequest, BadRequest(exception.getMessage))
        }
      }
    }
  }
  
  private def createNew = entity(as[Section]) { section =>
    DB localTx { session =>
      SectionPersistence.create(session, section) match {
        case Success(result) => complete(result)
        case Failure(exception) => complete(StatusCodes.BadRequest, BadRequest(exception.getMessage))
      }
    }
  }
}
