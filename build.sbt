import Dependencies._

lazy val commonSettings = Seq(
  organization := "com.github.wlingxiao",
  version := "0.0.1-SNAPSHOT",
  scalaVersion := "2.12.7",
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-language:implicitConversions",
    "-unchecked"
  )
)

lazy val core = Project(id = "flame-core", base = file("core"))
  .settings(commonSettings)
  .settings(libraryDependencies ++= Seq(
    slf4jApi,

    logbackClassic % Test,
    scalatest % Test,
  ))

lazy val http = Project(id = "flame-http", base = file("http"))
  .settings(commonSettings)
  .settings(libraryDependencies ++= Seq(
    slf4jApi,

    logbackClassic % Test,
    scalatest % Test,
  )).dependsOn(core)