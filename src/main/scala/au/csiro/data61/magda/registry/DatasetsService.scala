package au.csiro.data61.magda.registry

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Directives.Remaining
import akka.http.scaladsl.server.Directives.complete

class DatasetsService(system: ActorSystem, materializer: Materializer) {
  val route = get {
    path(Remaining) { url =>
      complete(url)
    }
  }
}
