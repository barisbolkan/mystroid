import sbt._

object Dependencies {

  val akkaVersion = "2.6.3"
  val akkaHttpVersion = "10.1.11"
  val alpakkaVersion = "1.1.2"
  val circeVersion = "0.12.3"
  val logVersion = "1.2.3"

  // Akka
  val akkaTyped = "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion
  val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
  val akkaDiscovery = "com.typesafe.akka" %% "akka-discovery" % akkaVersion
  val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
  val akkaHttp2 = "com.typesafe.akka" %% "akka-http2-support" % akkaHttpVersion
  val pubsub = "com.lightbend.akka" %% "akka-stream-alpakka-google-cloud-pub-sub-grpc" % alpakkaVersion
  val mongo = "com.lightbend.akka" %% "akka-stream-alpakka-mongodb" % alpakkaVersion
  val reactiveMongo = "org.mongodb" % "mongodb-driver-reactivestreams" % "4.0.1"

  // Logging
  val logBackClassic = "ch.qos.logback" % "logback-classic" % logVersion
  val logBackCore = "ch.qos.logback" % "logback-core" % logVersion
  val logBackAccess = "ch.qos.logback" % "logback-access" % logVersion
  val logBackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "4.11"

  // Misc
  val circeCore = "io.circe" %% "circe-core" % circeVersion
  val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  val circeParser = "io.circe" %% "circe-parser" % circeVersion
  val akkaHttpCirce = "de.heikoseeberger" %% "akka-http-circe" % "1.31.0"
  val netty = "io.netty" % "netty-all" % "4.1.17.Final"

  // Tests
  val testKit = "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion
  val streamTestKit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion
  val scalaTest = "org.scalatest" %% "scalatest" % "3.1.0"
  val scalaMock = "org.scalamock" %% "scalamock" % "4.4.0"
}
