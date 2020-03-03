ThisBuild / organization := "com.barisbolkan"
ThisBuild / version := "0.0.1"
ThisBuild / scalaVersion := "2.12.6"

lazy val commonDependecies = Seq()

lazy val api = (project in file("api"))
    .settings(
      name := "mystroid-api",
      mainClass in Compile := Some("com.barisbolkan.mystroid.api.WebServer"),
      libraryDependencies ++= commonDependecies
    )

lazy val core = (project in file("core"))
  .settings(
    name := "mystroid-core",
    mainClass in Compile := Some("com.barisbolkan.mystroid.core.DataProcessor"),
    libraryDependencies ++= commonDependecies
  )