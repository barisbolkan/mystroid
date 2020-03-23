package com.barisbolkan.mystroid.api.processing

import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.alpakka.mongodb.DocumentUpdate
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink}
import com.barisbolkan.mystroid.api.configuration.AppSettings
import com.barisbolkan.mystroid.api.persitence.{AstroidInfo, MongoRepository, MystroidRepository}
import com.barisbolkan.mystroid.api.serialization.JsonSupport
import com.google.pubsub.v1.pubsub.{AcknowledgeRequest, ReceivedMessage}
import com.mongodb.client.model.{Filters, Updates}
import com.mongodb.reactivestreams.client.MongoDatabase
import io.circe.parser._

trait DataService extends JsonSupport {

  lazy val repository: MongoRepository = MystroidRepository

  /**
    * Converts the [[com.google.pubsub.v1.pubsub.ReceivedMessage]] into
    * [[Option[com.barisbolkan.mystroid.api.persitence.AstroidInfo]]] for easy usage
    */
  protected[processing] lazy val receivedMessage2AstroidFlow: Flow[ReceivedMessage, Option[AstroidInfo], NotUsed] =
    Flow[ReceivedMessage]
    .map(sm => decode[AstroidInfo](sm.getMessage.data.toStringUtf8).toOption)

  /**
    * Converts the [[com.barisbolkan.mystroid.api.persitence.AstroidInfo]] into
    * [[akka.stream.alpakka.mongodb.DocumentUpdate]] to feed the downstream with updates
    */
  protected[processing] lazy val astroid2UpdateFlow: Flow[Option[AstroidInfo], DocumentUpdate, NotUsed] =
    Flow[Option[AstroidInfo]]
    .collect {
      case Some(ai) => DocumentUpdate(
        filter = Filters.eq("_id", ai.id),
        update = Updates.combine(
          Updates.set("neoRefId", ai.neoRefId), Updates.set("name", ai.name), Updates.set("absoluteMagnitude", ai.absoluteMagnitude),
          Updates.set("estimatedDiameter", ai.estimatedDiameter), Updates.set("isHazardous", ai.isHazardous),
          Updates.set("closeApproachData", ai.closeApproachData)
        )
      )
    }

  def createPersistenceFlow()(implicit settings: AppSettings, db: MongoDatabase): Flow[ReceivedMessage, AcknowledgeRequest, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val bcast = builder.add(Broadcast[ReceivedMessage](2))
      val ending = builder.add(Flow[AcknowledgeRequest])

      /**
        * This setup converts the [[com.google.pubsub.v1.pubsub.ReceivedMessage]] instance retrieved from pubsub into
        * [[akka.stream.alpakka.mongodb.DocumentUpdate]] instance and directs the output to Mongo
        */
      bcast ~> receivedMessage2AstroidFlow ~> astroid2UpdateFlow ~> repository.update ~> Sink.ignore

      /**
        * This setup is just sending acknowledgement to pubsub
        */
      bcast ~> Flow[ReceivedMessage]
        .map(rm => AcknowledgeRequest(settings.pubsub.subscription, Seq(rm.ackId))) ~> ending

      FlowShape(bcast.in, ending.out)
    })
}