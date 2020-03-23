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

  object pubsub {
    val projectId: String = config.getString("mystroid.pubsub.project-id")
    val topic: String = config.getString("mystroid.pubsub.topic")
    val subscription: String = config.getString("mystroid.pubsub.subscription")
  }

  object mongo {
    val connectionString = config.getString("mystroid.mongo.connStr")
    val collName = config.getString("mystroid.mongo.collection")
    val dbName = config.getString("mystroid.mongo.database")
  }
}

trait SettingsSupport {
  implicit val config = Settings.config
}