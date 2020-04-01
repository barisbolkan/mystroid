//package com.barisbolkan.mystroid.api
//
//import com.barisbolkan.mystroid.api.configuration.AppSettings
//import com.typesafe.config.{Config, ConfigException, ConfigFactory}
//import org.scalatest.funsuite.AnyFunSuite
//
//class AppSettingsSuite extends AnyFunSuite {
//
//  test("AppSettings should load the configuration") {
//
//    val fakeConfig: Config = ConfigFactory.parseString(
//      """
//        |mystroid {
//        |
//        |  http {
//        |    host = "0.0.0.0"
//        |    port = 8080
//        |  }
//        |
//        |  pubsub {
//        |    project-id = "mystroid"
//        |    topic = "astroid-data"
//        |
//        |    subscription = "projects/"${mystroid.pubsub.project-id}"/topics/"${mystroid.pubsub.topic}
//        |  }
//        |}
//      """.stripMargin).resolve()
//
//    val settings: AppSettings = AppSettings(fakeConfig)
//
//    assert(settings.http.host == "0.0.0.0")
//    assert(settings.http.port == 8080)
//    assert(settings.pubsub.subscription == "projects/mystroid/topics/astroid-data")
//  }
//
//  test("AppSettings should throw exception when no key exists") {
//    assertThrows[ConfigException] {
//      val fakeConfig: Config = ConfigFactory.parseString(
//        """
//          |mystroid-wrong {
//          |
//          |  http {
//          |    host = "0.0.0.0"
//          |    port = 8080
//          |  }
//          |
//          |  pubsub {
//          |    project-id = "mystroid"
//          |    topic = "astroid-data"
//          |
//          |    subscription = "projects/"${mystroid-wrong.pubsub.project-id}"/topics/"${mystroid-wrong.pubsub.topic}
//          |  }
//          |}
//        """.stripMargin).resolve()
//
//      val host = AppSettings(fakeConfig).http.host
//    }
//  }
//
//}
