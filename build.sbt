import Dependencies._
import com.typesafe.sbt.packager.docker.Cmd
import sbt.Keys.version

ThisBuild / organization := "com.barisbolkan"
ThisBuild / version := "0.1.1"
ThisBuild / scalaVersion := "2.12.10"

lazy val commonDependecies = Seq(
  akkaTyped, akkaHttp, akkaStream,
  logBackClassic, logBackCore, logBackAccess, logBackEncoder,
  testKit % Test, scalaTest % Test, scalaMock % Test, circeCore, circeGeneric, circeParser,
  akkaDiscovery % Provided,
  akkaHttp2 % Provided,
  pubsub,
)

lazy val api = (project in file("api"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaServerAppPackaging)
  .settings(
    name := "mystroid-api",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.barisbolkan.mystroid.api",
    buildInfoOptions += BuildInfoOption.ToJson,
    mainClass in Compile := Some("com.barisbolkan.mystroid.api.WebServer"),
    libraryDependencies ++= commonDependecies ++ Seq(
      mongo, alpakkaMongo, sangria, akkaHttpCirce, sangriaCirce,
      streamTestKit % Test
    ),
    packageName in Docker := "mystorid/" + name.value,
    version in Docker := version.value,
    dockerLabels := Map("maintainer" -> organization.value, "version" -> version.value),
    dockerBaseImage := "openjdk:14-jdk-alpine",
    defaultLinuxInstallLocation in Docker := s"/opt/${name.value}",
    dockerExposedPorts ++= Seq(8080),
    dockerCommands := dockerCommands.value.flatMap {
      case cmd@Cmd("FROM", _) => List(cmd, Cmd("RUN", "apk update && apk add bash"))
      case other => List(other)
    }
  )

lazy val core = (project in file("core"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaServerAppPackaging)
  .settings(
    name := "mystroid-core",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.barisbolkan.mystroid.core",
    buildInfoOptions += BuildInfoOption.ToJson,
    mainClass in Compile := Some("com.barisbolkan.mystroid.core.DataProcessor"),
    libraryDependencies ++= commonDependecies ++ Seq(
      akkaHttpCirce
    ),
    packageName in Docker := "mystorid/" + name.value,
    version in Docker := version.value,
    dockerLabels := Map("maintainer" -> organization.value, "version" -> version.value),
    dockerBaseImage := "openjdk:14-jdk-alpine",
    defaultLinuxInstallLocation in Docker := s"/opt/${name.value}",
    dockerCommands := dockerCommands.value.flatMap {
      case cmd@Cmd("FROM", _) => List(cmd, Cmd("RUN", "apk update && apk add bash"))
      case other => List(other)
    }
  )