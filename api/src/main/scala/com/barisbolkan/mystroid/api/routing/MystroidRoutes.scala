package com.barisbolkan.mystroid.api.routing

import akka.http.scaladsl.server.Directives.{complete, get, pathEndOrSingleSlash}
import akka.http.scaladsl.server.Route
import com.barisbolkan.mystroid.api.BuildInfo

class MystroidRoutes {

  lazy val routes: Route =
    pathEndOrSingleSlash {
      get {
        complete(s"Mystroid API is alive${BuildInfo.toJson}")
      }
    }

}
