import sbt._

object Dependencies {

  lazy val slf4jApi = "org.slf4j" % "slf4j-api" % "1.7.25"
  lazy val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"

  lazy val scalatest = "org.scalatest" %% "scalatest" % scalatestVersion

  private val scalatestVersion = "3.0.4"
}
 