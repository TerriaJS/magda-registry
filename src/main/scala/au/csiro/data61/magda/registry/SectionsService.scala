package au.csiro.data61.magda.registry

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.stream.Materializer
import akka.http.scaladsl.server.Directives._
import scalikejdbc._
import spray.json.DefaultJsonProtocol

class SectionsService(system: ActorSystem, materializer: Materializer) extends DefaultJsonProtocol with SprayJsonSupport with SectionProtocols {
  val route = get {
    pathEnd {
      complete {
        DB readOnly { implicit session =>
          sql"select sectionid, name from section".map(rs => rowToSection(rs)).list.apply()
        }
      }
    } ~
    path(Segment) { id =>
      complete {
        DB readOnly { implicit session =>
          sql"select sectionid, name from section where sectionid=${id}".map(rs => rowToSection(rs)).single.apply()
        }
      }
    }
  } ~
  put {
    path(Segment) { id =>
      entity(as[Section]) { section =>
        complete {
          DB localTx { implicit session =>
            sql"insert into section (sectionid, name) values (${section.id}, ${section.name})".update.apply()
            section
          }
        }
      }
    }
  }

  private def rowToSection(rs: WrappedResultSet): Section = new Section(rs.string("sectionid"), rs.string("name"))
}
