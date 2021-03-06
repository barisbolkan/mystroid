package com.barisbolkan.mystroid.api

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream._
import com.barisbolkan.mystroid.api.configuration.SettingsSupport
import com.barisbolkan.mystroid.api.processing.DataService
import com.barisbolkan.mystroid.api.routing.MystroidRoutes
import com.mongodb.reactivestreams.client.{MongoClient, MongoClients}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object WebServer extends SettingsSupport
  with DataService {

  sealed trait Message

  private final case class StartFailed(cause: Throwable) extends Message
  private final case class Started(binding: ServerBinding, switch: UniqueKillSwitch) extends Message
  case object Stop extends Message

  /**
    * Mongo connection
    */
  lazy val connection: MongoClient = MongoClients.create(config.mongo.connectionString)

  /**
    * A [[SharedKillSwitch]] instance to cancel the graph
    */
  private lazy val switch: SharedKillSwitch = KillSwitches.shared("subscriber-switch")

  def apply(host: String, port: Int): Behavior[Message] = Behaviors.setup { context =>
    // Do initialization
    implicit val system = context.system
    implicit val untypedSystem: akka.actor.ActorSystem = context.system.toClassic
    implicit val materializer: Materializer = Materializer(context.system.toClassic)
    implicit val ec: ExecutionContext = system.executionContext

    context.log.info(s"Mystroid web-server is starting.Variables: [${config.mongo.connectionString.toString}]")

    val mongoClient = MongoClients.create(config.mongo.connectionString)
    implicit val db = mongoClient.getDatabase(config.mongo.dbName)

    val decider: Supervision.Decider = {
      case ex: Exception =>
        system.log.error(s"Webserver consuming stream has failed. The reason: ${ex.toString}")
        Supervision.Restart
    }

    // Bind server
    val server: Future[(ServerBinding, UniqueKillSwitch)] = for {
      binding <- Http.apply().bindAndHandle(new MystroidRoutes().routes, host, port)
      switch = persistenceGraph.withAttributes(ActorAttributes.supervisionStrategy(decider)).run()
    } yield (binding, switch)
    context.pipeToSelf(server) {
      case Success((binding, switch)) => Started(binding, switch)
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