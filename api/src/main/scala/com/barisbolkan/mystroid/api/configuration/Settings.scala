package com.barisbolkan.mystroid.api.configuration

import com.typesafe.config.{Config, ConfigFactory}

object Settings {
  implicit val config = AppSettings(ConfigFactory.load())
}

case class AppSettings(config: Config) {

  object http {
    val host: String = config.getString("mystroid.http.host")
    val port: Int = config.getInt("mystroid.http.port")
  }

}
