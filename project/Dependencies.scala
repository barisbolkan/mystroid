import sbt._

object Dependencies {

  val akkaVersion = "2.6.3"
  val akkaHttpVersion = "10.1.11"
  val circeVersion = "0.12.3"
  val logVersion = "1.2.3"

  // Akka
  val akkaTyped = "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion
  val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
  val akkaDiscovery = "com.typesafe.akka" %% "akka-discovery" % akkaVersion
  val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
  val akkaHttp2 = "com.typesafe.akka" %% "akka-http2-support" % akkaHttpVersion
  val pubsub = "com.lightbend.akka" %% "akka-stream-alpakka-google-cloud-pub-sub-grpc" % "2.0.0-RC2"
  val alpakkaMongo = "com.lightbend.akka" %% "akka-stream-alpakka-mongodb" % "2.0.0-RC1"
  val mongo = "org.mongodb.scala" %% "mongo-scala-driver" % "2.8.0"

  // Logging
  val logBackClassic = "ch.qos.logback" % "logback-classic" % logVersion
  val logBackCore = "ch.qos.logback" % "logback-core" % logVersion
  val logBackAccess = "ch.qos.logback" % "logback-access" % logVersion
  val logBackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "4.11"

  // Misc
  val circeCore = "io.circe" %% "circe-core" % circeVersion
  val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  val circeParser = "io.circe" %% "circe-parser" % circeVersion
  val akkaHttpCirce = "de.heikoseeberger" %% "akka-http-circe" % "1.27.0"
  val sangria = "org.sangria-graphql" %% "sangria" % "1.4.2"
  val sangriaCirce = "org.sangria-graphql" %% "sangria-circe" % "1.2.1"

  // Tests
  val testKit = "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion
  val streamTestKit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion
  val scalaTest = "org.scalatest" %% "scalatest" % "3.1.0"
  val scalaMock = "org.scalamock" %% "scalamock" % "4.4.0"
}
