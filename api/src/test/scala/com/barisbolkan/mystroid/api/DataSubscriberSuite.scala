package com.barisbolkan.mystroid.api

import java.time.{Instant, ZoneId, ZonedDateTime}

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.mongodb.DocumentUpdate
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import com.barisbolkan.mystroid.api.configuration.SettingsSupport
import com.barisbolkan.mystroid.api.persitence.MystroidRepository.{ApproachData, AstroidInfo, Diameter}
import com.barisbolkan.mystroid.api.processing.DataService
import com.barisbolkan.mystroid.api.serialization.JsonSupport
import com.google.protobuf.ByteString
import com.google.pubsub.v1.pubsub.{PubsubMessage, ReceivedMessage}
import com.mongodb.client.model.{Filters, Updates}
import com.mongodb.reactivestreams.client.MongoDatabase
import org.scalamock.scalatest.MockFactory
import org.scalatest.funsuite.AnyFunSuite

class DataSubscriberSuite extends AnyFunSuite
  with DataService
  with JsonSupport
  with SettingsSupport
  with MockFactory {

  implicit val system = ActorSystem()
  implicit val materializer: Materializer = Materializer(system)

  implicit val mockDb = mock[MongoDatabase]

  private val validJson =
    """{
       |  "links":{"self":"http://www.neowsapp.com/rest/v1/neo/2021277?api_key=FB7MwHCyyGwKMfizb7PShbwfq6G0aqHqvnscMbtR"},
       |  "id":"2021277",
       |  "neo_reference_id":"2021277",
       |  "name":"21277 (1996 TO5)",
       |  "designation":"21277",
       |  "nasa_jpl_url":"http://ssd.jpl.nasa.gov/sbdb.cgi?sstr=2021277",
       |  "absolute_magnitude_h":16.1,
       |  "estimated_diameter":{
       |      "kilometers":{"estimated_diameter_min":1.6016033798,"estimated_diameter_max":3.5812940302},
       |      "meters":{"estimated_diameter_min":1601.6033797856,"estimated_diameter_max":3581.2940301941},
       |      "miles":{"estimated_diameter_min":0.9951898937,"estimated_diameter_max":2.2253122528},
       |      "feet":{"estimated_diameter_min":5254.6044325359,"estimated_diameter_max":11749.652706022}
       |  },
       |  "is_potentially_hazardous_asteroid":false,
       |  "close_approach_data":[
       |      {
       |        "close_approach_date":"1945-06-07",
       |        "close_approach_date_full":"1945-Jun-07 22:35",
       |        "epoch_date_close_approach":-775272300000,
       |        "relative_velocity":{"kilometers_per_second":"15.509473678","kilometers_per_hour":"55834.1052409282","miles_per_hour":"34693.1416703978"},
       |        "miss_distance":{"astronomical":"0.0334232759","lunar":"13.0016543251","kilometers":"5000050.883062333","miles":"3106887.5504448754"},
       |        "orbiting_body":"Mars"
       |      }
       |  ],
       |  "orbital_data":{
       |      "orbit_id":"161"
       |  },
       |  "is_sentry_object":false
       |}
    """.stripMargin

  test("receivedMessage2AstroidFlow should convert ReceivedMessage to None if it's empty") {
    val ts = TestSink.probe[Option[AstroidInfo]]
    val (_, sink) = receivedMessage2AstroidFlow
        .runWith(Source.single(ReceivedMessage("fake_ack_id", message = Some(PubsubMessage()))), ts)

    sink.request(1)
    sink.expectNext(None)
    sink.expectComplete()
  }

  test("receivedMessage2AstroidFlow should convert ReceivedMessage to Some(AstroidInfo) if it's a valid data") {
    val ts = TestSink.probe[Option[AstroidInfo]]
    val astroid = AstroidInfo("2021277", "2021277", "21277 (1996 TO5)", 16.1, Diameter(1.6016033798, 3.5812940302), false, List(
      ApproachData(ZonedDateTime.ofInstant(Instant.ofEpochMilli(-775272300000L), ZoneId.of("UTC")), 55834.1052409282, 5000050.883062333)
    ))

    val (_, sink) = receivedMessage2AstroidFlow
      .runWith(Source.single(ReceivedMessage("fake_ack_id", message = Some(PubsubMessage(
        data = ByteString.copyFromUtf8(validJson)
      )))), ts)

    sink.request(1)
    sink.expectNext(Some(astroid))
    sink.expectComplete()
  }

  test("receivedMessage2AstroidFlow should convert ReceivedMessage to None if it's malformed") {
    val ts = TestSink.probe[Option[AstroidInfo]]
    val (_, sink) = receivedMessage2AstroidFlow
      .runWith(Source.single(ReceivedMessage("fake_ack_id", message = Some(PubsubMessage(
        data = ByteString.copyFromUtf8("{")
      )))), ts)

    sink.request(1)
    sink.expectNext(None)
    sink.expectComplete()
  }

  test("astroid2UpdateFlow should complete the flow with 1 None element") {
    val ts = TestSink.probe[DocumentUpdate]
    val (_, sink) = astroid2UpdateFlow
      .runWith(Source.single(None), ts)

    sink.request(1)
    sink.expectComplete()
  }

  test("astroid2UpdateFlow should complete the flow with multiple elements with a couple None value") {
    val ts = TestSink.probe[DocumentUpdate]
    val astroid1 = AstroidInfo("fake_id", "fake_neoref", "some_name", 1.2222, Diameter(12.02, 91.111), false, List(
      ApproachData(ZonedDateTime.now(), 123.654, 12)
    ))
    val astroid2 = AstroidInfo("fake_id_2", "fake_neoref_2", "some_name_2", 1.2222, Diameter(12.02, 91.111), false, List(
      ApproachData(ZonedDateTime.now(), 123.654, 12)
    ))
    val (_, sink) = astroid2UpdateFlow
      .runWith(Source.fromIterator(() => List(None, Some(astroid1), None, None, Some(astroid2)).iterator), ts)

    sink.request(5)
    val d1 = sink.expectNext()
    val d1Check = DocumentUpdate(
      filter = Filters.eq("_id", astroid1.id),
      update = Updates.combine(
        Updates.set("neoRefId", astroid1.neoRefId), Updates.set("name", astroid1.name), Updates.set("absoluteMagnitude", astroid1.absoluteMagnitude),
        Updates.set("estimatedDiameter", astroid1.estimatedDiameter), Updates.set("isHazardous", astroid1.isHazardous),
        Updates.set("closeApproachData", astroid1.closeApproachData)
      )
    )
    val d2 = sink.expectNext()
    val d2Check = DocumentUpdate(
      filter = Filters.eq("_id", astroid2.id),
      update = Updates.combine(
        Updates.set("neoRefId", astroid2.neoRefId), Updates.set("name", astroid2.name), Updates.set("absoluteMagnitude", astroid2.absoluteMagnitude),
        Updates.set("estimatedDiameter", astroid2.estimatedDiameter), Updates.set("isHazardous", astroid2.isHazardous),
        Updates.set("closeApproachData", astroid2.closeApproachData)
      )
    )
    assert(d1.toString() == d1Check.toString())
    assert(d2.toString() == d2Check.toString())
    sink.expectComplete()
  }
}
