import sbt._

object Dependencies {

  val akkaVersion = "2.6.3"
  val akkaHttpVersion = "10.1.11"

  // Akka
  val akkaTyped = "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion
  val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
  val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion

  // Logging
  val logBack = "ch.qos.logback" % "logback-classic" % "1.2.3"

  // Tests
  val testKit = "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion
  val scalaTest = "org.scalatest" %% "scalatest" % "3.1.0"
}
