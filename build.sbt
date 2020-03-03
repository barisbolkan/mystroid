import Dependencies._
import sbt.Keys.version

ThisBuild / organization := "com.barisbolkan"
ThisBuild / version := "0.0.1"
ThisBuild / scalaVersion := "2.12.6"

lazy val commonDependecies = Seq(
  akkaTyped, akkaHttp, akkaStream, logBack,
  testKit % Test, scalaTest % Test
)

lazy val api = (project in file("api"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "mystroid-api",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.barisbolkan.mystroid.api",
    buildInfoOptions += BuildInfoOption.ToJson,
    mainClass in Compile := Some("com.barisbolkan.mystroid.api.WebServer"),
    libraryDependencies ++= commonDependecies
  )

lazy val core = (project in file("core"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "mystroid-core",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.barisbolkan.mystroid.core",
    buildInfoOptions += BuildInfoOption.ToJson,
    mainClass in Compile := Some("com.barisbolkan.mystroid.core.DataProcessor"),
    libraryDependencies ++= commonDependecies
  )