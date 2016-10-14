package au.csiro.data61.magda.registry

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.Materializer
import akka.http.scaladsl.server.Directives._
import scalikejdbc._
import spray.json._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.ExceptionHandler

class RecordsService(system: ActorSystem, materializer: Materializer) extends RecordProtocols with BadRequestProtocols {
  val route =
    get { pathEnd { getAll } } ~
    get { pathEnd { parameters('section.*) { getAllWithSections } } }

  private val getAll = complete {
      DB readOnly { session =>
        RecordPersistence.getAll(session)
      }
  }
  
  private val getAllWithSections = (sections: Iterable[String]) => {
    complete {
      DB readOnly { session =>
        RecordPersistence.getAllWithSections(session, sections)
      }
    }
  }
}
