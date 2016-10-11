package au.csiro.data61.magda.registry

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.Materializer
import akka.http.scaladsl.server.Directives._
import scalikejdbc._
import spray.json._
import akka.http.scaladsl.model.StatusCodes

class SectionsService(system: ActorSystem, materializer: Materializer) extends SectionProtocols with BadRequestProtocols {
  val route = get {
    pathEnd {
      complete {
        DB readOnly { implicit session =>
          sql"select sectionID, name, jsonSchema from Section".map(rs => rowToSection(rs)).list.apply()
        }
      }
    } ~
    path(Segment) { id =>
      complete {
        DB readOnly { implicit session =>
          sql"select sectionID, name, jsonSchema from Section where sectionID=${id}".map(rs => rowToSection(rs)).single.apply().get
        }
      }
    }
  } ~
  put {
    path(Segment) { id =>
      entity(as[Section]) { section =>
        if (id != section.id) {
          complete(StatusCodes.BadRequest, BadRequest("The section's id property does not match the URL."))
        } else {
          complete {
            DB localTx { implicit session =>
              sql"insert into Section (sectionID, name, jsonSchema) values (${section.id}, ${section.name}, ${section.jsonSchema.compactPrint}::json)".update.apply()
              section
            }
          }
        }
      }
    }
  } ~
  post {
    pathEnd {
      entity(as[Section]) { section =>
        complete {
          DB localTx { implicit session =>
            sql"insert into Section (sectionID, name, jsonSchema) values (${section.id}, ${section.name}, ${section.jsonSchema.compactPrint}::json)".update.apply()
            section
          }
        }
      }
    }
  }

  private def rowToSection(rs: WrappedResultSet): Section = new Section(rs.string("sectionID"), rs.string("name"), JsonParser(rs.string("jsonSchema")).asJsObject)
}
