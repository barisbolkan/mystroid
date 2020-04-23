package com.barisbolkan.mystroid.api.processing

import akka.NotUsed
import akka.actor.Cancellable
import akka.stream.alpakka.googlecloud.pubsub.grpc.scaladsl.GooglePubSub
import akka.stream.alpakka.mongodb.DocumentReplace
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Source}
import akka.stream.{ClosedShape, KillSwitches, UniqueKillSwitch}
import com.barisbolkan.mystroid.api.WebServer.config
import com.barisbolkan.mystroid.api.configuration.AppSettings
import com.barisbolkan.mystroid.api.persitence.MystroidRepository._
import com.barisbolkan.mystroid.api.persitence.{MongoRepository, MystroidRepository}
import com.barisbolkan.mystroid.api.serialization.JsonSupport
import com.google.pubsub.v1.pubsub.{AcknowledgeRequest, PullRequest, ReceivedMessage}
import com.mongodb.client.model.Filters
import com.mongodb.reactivestreams.client.MongoDatabase
import io.circe.parser._

import scala.concurrent.Future
import scala.concurrent.duration._

trait DataService extends JsonSupport {

  lazy val repository: MongoRepository = MystroidRepository

  /**
    * Google PubSub source to stream the messages
    */
  lazy val pubsubSource: Source[ReceivedMessage, Future[Cancellable]] = GooglePubSub.subscribePolling(
    PullRequest()
      .withSubscription(config.pubsub.subscription)
      .withMaxMessages(5)
    , 1.second)

  /**
    * Google PubSub sink to acknowledge the messages
    */
  lazy val ackSink = GooglePubSub.acknowledge(parallelism = 1)

  /**
    * Converts the [[com.google.pubsub.v1.pubsub.ReceivedMessage]] into
    * [[Option[com.barisbolkan.mystroid.api.persitence.AstroidInfo]]] for easy usage
    */
  protected[processing] lazy val receivedMessage2AstroidFlow: Flow[ReceivedMessage, AstroidInfo, NotUsed] =
    Flow[ReceivedMessage]
    .map(sm => decode[List[AstroidInfo]](sm.getMessage.data.toStringUtf8).toOption)
    .mapConcat(f => f.getOrElse(List.empty))


  /**
    * Converts the [[com.barisbolkan.mystroid.api.persitence.MystroidRepository.AstroidInfo]] into
    * [[akka.stream.alpakka.mongodb.DocumentUpdate]] to feed the downstream with updates
    */
  protected[processing] lazy val astroid2UpdateFlow: Flow[AstroidInfo, DocumentReplace[AstroidInfo], NotUsed] =
    Flow[AstroidInfo]
      .map { ai => DocumentReplace(Filters.eq("_id", ai.id), ai) }

  def persistenceGraph()(implicit settings: AppSettings, db: MongoDatabase): RunnableGraph[UniqueKillSwitch] =
    RunnableGraph.fromGraph(GraphDSL.create(KillSwitches.single[ReceivedMessage]) { implicit builder: GraphDSL.Builder[UniqueKillSwitch] => sw =>
      import GraphDSL.Implicits._

      val bcast = builder.add(Broadcast[ReceivedMessage](2))

      // Split the stream
      pubsubSource ~> sw ~> bcast

      /**
        * This setup converts the [[com.google.pubsub.v1.pubsub.ReceivedMessage]] instance retrieved from pubsub into
        * [[akka.stream.alpakka.mongodb.DocumentUpdate]] instance and directs the output to Mongo
        */
      bcast ~> receivedMessage2AstroidFlow ~> astroid2UpdateFlow ~> repository.update[AstroidInfo]()

      /**
        * This setup is just sending acknowledgement to pubsub
        */
      bcast ~> Flow[ReceivedMessage]
        .map(rm => AcknowledgeRequest(settings.pubsub.subscription, Seq(rm.ackId))) ~> ackSink

      ClosedShape
    })
}