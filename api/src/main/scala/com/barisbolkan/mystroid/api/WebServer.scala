package com.barisbolkan.mystroid.api

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream._
import akka.stream.alpakka.googlecloud.pubsub.grpc.scaladsl.GooglePubSub
import akka.stream.scaladsl.Source
import com.barisbolkan.mystroid.api.configuration.SettingsSupport
import com.barisbolkan.mystroid.api.processing.DataService
import com.barisbolkan.mystroid.api.routing.MystroidRoutes
import com.google.pubsub.v1.pubsub.{ReceivedMessage, StreamingPullRequest}
import com.mongodb.reactivestreams.client.{MongoClient, MongoClients}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object WebServer extends SettingsSupport
  with DataService {

  sealed trait Message

  private final case class StartFailed(cause: Throwable) extends Message
  private final case class Started(binding: ServerBinding, cancellable: Cancellable) extends Message
  case object Stop extends Message

  /**
    * Google PubSub source to stream the messages
    */
  lazy val pubsubSource: Source[ReceivedMessage, Future[Cancellable]] = GooglePubSub.subscribe(
    StreamingPullRequest()
      .withSubscription(config.pubsub.subscription)
      .withStreamAckDeadlineSeconds(10)
    , 1.second)

  /**
    * Google PubSub sink to acknowledge the messages
    */
  lazy val ackSink = GooglePubSub.acknowledge(parallelism = 1)

  /**
    * Mongo connection
    */
  lazy val connection: MongoClient = MongoClients.create(config.mongo.connectionString)

  def apply(host: String, port: Int): Behavior[Message] = Behaviors.setup { context =>
    // Do initialization
    implicit val system = context.system
    implicit val untypedSystem: akka.actor.ActorSystem = context.system.toClassic
    implicit val materializer: Materializer = Materializer(context.system.toClassic)
    implicit val ec: ExecutionContext = system.executionContext

    context.log.info("Mystroid web-server is starting.")

    val mongoClient = MongoClients.create(config.mongo.connectionString)
    implicit val db = mongoClient.getDatabase(config.mongo.dbName)

    // Bind server
    val server: Future[(ServerBinding, Cancellable)] = for {
      binding <- Http.apply().bindAndHandle(new MystroidRoutes().routes, host, port)
      cancellable <- createPersistenceFlow().runWith(pubsubSource, ackSink)._1
    } yield (binding, cancellable)
    context.pipeToSelf(server) {
      case Success((binding, cancellable)) => Started(binding, cancellable)
      case Failure(ex)      => StartFailed(ex)
    }

    def running(binding: ServerBinding): Behavior[Message] =
      Behaviors.receiveMessagePartial[Message] {
        case Stop => context.log.info(s"Stopping Mystroid web-server at " +
          s"http://${binding.localAddress.getHostString}:${binding.localAddress.getPort}/")
          Behaviors.stopped
      }.receiveSignal {
        case (_, PostStop) =>
          binding.unbind()
          Behaviors.same
      }

    def starting(wasStopped: Boolean): Behaviors.Receive[Message] =
      Behaviors.receiveMessage[Message] {
        case StartFailed(cause) =>
          throw new RuntimeException("Server failed to start!", cause)
        case Started(binding, cancellable) =>
          context.log.info(s"Mystroid web-server started at " +
            s"http://${binding.localAddress.getHostString}:${binding.localAddress.getPort}/")
          if (wasStopped) context.self ! Stop
          running(binding)
        case Stop =>
          starting(wasStopped = true)
      }

    starting(false)
  }

  def main(args: Array[String]): Unit = {
    ActorSystem(WebServer(config.http.host, config.http.port), "web-server")
  }
}