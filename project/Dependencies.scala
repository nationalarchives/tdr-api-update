import sbt._

object Dependencies {
  private val circeVersion = "0.14.5"
  private val elasticMqVersion = "1.3.14"
  private val awsUtilsVersion = "0.1.65"

  lazy val awsSsm = "software.amazon.awssdk" % "ssm" % "2.20.1"
  lazy val backendCheckUtils = "uk.gov.nationalarchives" %% "tdr-backend-checks-utils" % "0.1.22"
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.16"
  lazy val circeCore = "io.circe" %% "circe-core" % circeVersion
  lazy val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  lazy val circeParser = "io.circe" %% "circe-parser" % circeVersion
  lazy val generatedGraphql = "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.341"
  lazy val graphqlClient = "uk.gov.nationalarchives" %% "tdr-graphql-client" % "0.0.120"
  lazy val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.4.11"
  lazy val logstashLogbackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "7.4"
  lazy val wiremock = "com.github.tomakehurst" % "wiremock-jre8" % "3.0.0"
  lazy val keycloakMock = "com.tngtech.keycloakmock" % "mock" % "0.15.1"
  lazy val mockito = "org.mockito" %% "mockito-scala" % "1.17.14"
  lazy val typesafe = "com.typesafe" % "config" % "1.4.2"
  lazy val authUtils = "uk.gov.nationalarchives" %% "tdr-auth-utils" % "0.0.156"
}
