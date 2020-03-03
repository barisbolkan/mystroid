package com.barisbolkan.mystroid.api

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.Materializer
import com.barisbolkan.mystroid.api.configuration.AppSettings
import com.barisbolkan.mystroid.api.routing.MystroidRoutes

import scala.concurrent.Future
import scala.util.{Failure, Success}

object WebServer {

  sealed trait Message

  private final case class StartFailed(cause: Throwable) extends Message
  private final case class Started(binding: ServerBinding) extends Message
  case object Stop extends Message

  def apply(host: String, port: Int)(implicit configuration: AppSettings): Behavior[Message] = Behaviors.setup { context =>
    // Do initialization
    implicit val system = context.system
    implicit val untypedSystem: akka.actor.ActorSystem = context.system.toClassic
    implicit val materializer: Materializer = Materializer(context.system.toClassic)
    context.log.info("Mystroid web-server is starting.")

    val serverBinding: Future[ServerBinding] = Http.apply().bindAndHandle(new MystroidRoutes().routes, host, port)
    context.pipeToSelf(serverBinding) {
      case Success(binding) => Started(binding)
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
        case Started(binding) =>
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
    import com.barisbolkan.mystroid.api.configuration.Settings._
    ActorSystem(WebServer(config.http.host, config.http.port), "web-server")
  }
}