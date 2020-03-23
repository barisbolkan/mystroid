package com.barisbolkan.mystroid.api

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.mongodb.DocumentUpdate
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import com.barisbolkan.mystroid.api.configuration.SettingsSupport
import com.barisbolkan.mystroid.api.persitence._
import com.barisbolkan.mystroid.api.processing.DataService
import com.barisbolkan.mystroid.api.serialization.JsonSupport
import com.google.protobuf.ByteString
import com.google.pubsub.v1.pubsub.{PubsubMessage, ReceivedMessage}
import com.mongodb.client.model.{Filters, Updates}
import com.mongodb.reactivestreams.client.MongoDatabase
import io.circe.syntax._
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
    val astroid = AstroidInfo("fake_id", "fake_neoref", "some_name", 1.2222, Diameter(12.02, 91.111), false, List(
      ApproachData(ZonedDateTime.now(), 123.654, 12)
    ))

    val (_, sink) = receivedMessage2AstroidFlow
      .runWith(Source.single(ReceivedMessage("fake_ack_id", message = Some(PubsubMessage(
        data = ByteString.copyFromUtf8(astroid.asJson.toString())
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
