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
  @ApiOperation(value = "Get a list of all sections", nickname = "getAll", httpMethod = "GET", response = classOf[Section], responseContainer = "List")
  def getAll = get { pathEnd {
    complete {
      DB readOnly { session =>
        SectionPersistence.getAll(session)
      }
    }
  } }

  @ApiOperation(value = "Create a new section", nickname = "create", httpMethod = "POST", response = classOf[Section])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "section", required = true, dataType = "au.csiro.data61.magda.registry.Section", paramType = "body", value = "The definition of the new section.")
  ))
  def create = post { pathEnd { entity(as[Section]) { section =>
    DB localTx { session =>
      SectionPersistence.create(session, section) match {
        case Success(result) => complete(result)
        case Failure(exception) => complete(StatusCodes.BadRequest, BadRequest(exception.getMessage))
      }
    }
  } } }

  @Path("/{id}")
  @ApiOperation(value = "Get a section by ID", nickname = "getById", httpMethod = "GET", response = classOf[Section])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", required = true, dataType = "string", paramType = "path", value = "ID of the section to be fetched.")
  ))
  def getById = get { path(Segment) { (id: String) => {
    DB readOnly { session =>
      SectionPersistence.getById(session, id) match {
        case Some(section) => complete(section)
        case None => complete(StatusCodes.NotFound, BadRequest("No section exists with that ID."))
      }
    }
  } } }

  @Path("/{id}")
  @ApiOperation(value = "Modify a section by ID", nickname = "putById", httpMethod = "PUT", response = classOf[Section],
    notes = "Modifies the section with a given ID.  If a section with the ID does not yet exist, it is created.")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", required = true, dataType = "string", paramType = "path", value = "ID of the section to be saved."),
    new ApiImplicitParam(name = "section", required = true, dataType = "au.csiro.data61.magda.registry.Section", paramType = "body", value = "The section to save.")
  ))
  def putById = put { path(Segment) { (id: String) => {
    entity(as[Section]) { section =>
      DB localTx { session =>
        SectionPersistence.putById(session, id, section) match {
          case Success(result) => complete(result)
          case Failure(exception) => complete(StatusCodes.BadRequest, BadRequest(exception.getMessage))
        }
      }
    }
  } } }

  def route =
    getAll ~
    create ~
    getById ~
    putById
}
