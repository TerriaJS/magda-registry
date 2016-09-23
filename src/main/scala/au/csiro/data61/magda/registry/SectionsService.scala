package au.csiro.data61.magda.registry

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.http.scaladsl.server.Directives._

class SectionsService(system: ActorSystem, materializer: Materializer) {
  val route = get {
    pathEnd {
      complete("OK")
    } ~
    path(Segment) { id =>
      complete(id)
    }
  }
}
