package com.barisbolkan.mystroid.core

import akka.NotUsed
import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.stream.Materializer


object DataProcessor {

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
    ActorSystem(DataProcessor(), "data-processor")
  }
}