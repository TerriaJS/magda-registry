package au.csiro.data61.magda.registry

import javax.ws.rs.Path

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.Materializer
import akka.http.scaladsl.server.Directives._
import scalikejdbc._
import akka.http.scaladsl.model.StatusCodes
import io.swagger.annotations._

import scala.util.Failure
import scala.util.Success

@Path("/records/{recordID}/sections")
@io.swagger.annotations.Api(value = "record sections", produces = "application/json")
class RecordSectionsService(system: ActorSystem, materializer: Materializer) extends Protocols {
  @Path("/{sectionID}")
  @ApiOperation(value = "Get a record section by ID", nickname = "getById", httpMethod = "GET", response = classOf[RecordSection])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "recordID", required = true, dataType = "string", paramType = "path", value = "ID of the record for which to fetch a section."),
    new ApiImplicitParam(name = "sectionID", required = true, dataType = "string", paramType = "path", value = "ID of the section to fetch.")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 404, message = "No record or section exists with the given IDs.", response = classOf[BadRequest])
  ))
  def getById = get { path(Segment / "sections" / Segment) { (recordID: String, sectionID: String) => {
    DB readOnly { session =>
      RecordPersistence.getRecordSectionById(session, recordID, sectionID) match {
        case Some(recordSection) => complete(recordSection)
        case None => complete(StatusCodes.NotFound, BadRequest("No record section exists with that ID."))
      }
    }
  } } }

  @Path("/{sectionID}")
  @ApiOperation(value = "Modify a record section by ID", nickname = "putById", httpMethod = "PUT", response = classOf[RecordSection],
    notes = "Modifies a record section.  If the section does not yet exist on this record, it is created.")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "recordID", required = true, dataType = "string", paramType = "path", value = "ID of the record for which to fetch a section."),
    new ApiImplicitParam(name = "sectionID", required = true, dataType = "string", paramType = "path", value = "ID of the section to fetch."),
    new ApiImplicitParam(name = "section", required = true, dataType = "au.csiro.data61.magda.registry.RecordSection", paramType = "body", value = "The record section to save.")
  ))
  def putById = put { path(Segment / "sections" / Segment) { (recordID: String, sectionID: String) => {
    entity(as[RecordSection]) { section =>
      DB localTx { session =>
        RecordPersistence.putRecordSectionById(session, recordID, sectionID, section) match {
          case Success(result) => complete(result)
          case Failure(exception) => complete(StatusCodes.BadRequest, BadRequest(exception.getMessage))
        }
      }
    }
  } } }

  @ApiOperation(value = "Create a new record section", nickname = "create", httpMethod = "POST", response = classOf[RecordSection])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "recordID", required = true, dataType = "string", paramType = "path", value = "ID of the record for which to create a section."),
    new ApiImplicitParam(name = "section", required = true, dataType = "au.csiro.data61.magda.registry.RecordSection", paramType = "body", value = "The definition of the new section.")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "The record does not exist, a section already exists with the supplied ID, or the specified section ID is not valid.", response = classOf[BadRequest])
  ))
  def create = post { path(Segment / "sections") { (recordID: String) => {
    entity(as[RecordSection]) { section =>
      DB localTx { session =>
        RecordPersistence.createRecordSection(session, recordID, section) match {
          case Success(result) => complete(result)
          case Failure(exception) => complete(StatusCodes.BadRequest, BadRequest(exception.getMessage))
        }
      }
    }
  } } }

  val route =
      getById ~
      putById ~
      create
}
