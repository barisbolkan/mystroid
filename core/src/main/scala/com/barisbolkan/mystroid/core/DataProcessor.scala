package com.barisbolkan.mystroid.core

import java.util.concurrent.Executors

import akka.NotUsed
import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.stream.{KillSwitches, Materializer, SharedKillSwitch}
import com.barisbolkan.mystroid.core.processing.StreamingService

import scala.concurrent.ExecutionContext

/**
  * Guardian of the whole application
  */
object DataProcessor extends StreamingService {

  /**
    * A [[SharedKillSwitch]] instance to cancel the graph
    */
  private lazy val switch: SharedKillSwitch = KillSwitches.shared("streamer-switch")

  def apply(): Behavior[NotUsed] = Behaviors.setup { context =>
    context.log.info(s"${BuildInfo.name}[${BuildInfo.version}] is starting with sbt[${BuildInfo.sbtVersion}] " +
      s"and scala[${BuildInfo.scalaVersion}].")

    implicit val system = context.system
    implicit val untypedSystem: akka.actor.ActorSystem = context.system.toClassic
    implicit val materializer: Materializer = Materializer(context.system.toClassic)

    Behaviors.receiveSignal {
      case (_, Terminated(ref)) =>
        context.log.error(s"Actor[${ref.path.name}] is terminated. Setting service state to faulty!")
        Behaviors.stopped
    }
  }

  def main(args: Array[String]): Unit = {
    val system = ActorSystem(DataProcessor(), "data-processor")
    implicit val classicalSystem = system.toClassic
    implicit val materializer = Materializer(classicalSystem)

    val ecService = Executors.newFixedThreadPool(2)
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(ecService)
    import com.barisbolkan.mystroid.core.configuration.Settings._

    // Start processing
    consume(switch).run()

    // System termination
    // TODO: Get the ExecutionContext into the ActorSystem
    system.whenTerminated.map(_ => ecService.shutdown())(scala.concurrent.ExecutionContext.Implicits.global)
  }
}