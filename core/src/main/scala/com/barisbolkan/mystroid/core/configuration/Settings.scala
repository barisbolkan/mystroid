package com.barisbolkan.mystroid.core.configuration

import java.time.Duration

import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration.{FiniteDuration, NANOSECONDS}

object Settings {
  implicit val config = AppSettings(ConfigFactory.load())
}

case class AppSettings(config: Config) {

  private implicit def duration2FiniteDuration(d: Duration) = FiniteDuration(d.toNanos, NANOSECONDS)

  object nasa {
    val url: String = config.getString("mystroid.nasa.url")
    val schedulePeriod: FiniteDuration = config.getDuration("mystroid.nasa.schedule-period")
  }

  object health {
    val host: String = config.getString("mystroid.health.host")
    val port: Int = config.getInt("mystroid.health.port")
  }

  object pubsub {
    val projectId: String = config.getString("mystroid.pubsub.project-id")
    val topic: String = config.getString("mystroid.pubsub.topic")
    val subscription: String = config.getString("mystroid.pubsub.subscription")
  }
}