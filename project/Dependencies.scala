import sbt._

object Dependencies {
  private val circeVersion = "0.14.15"
  private val elasticMqVersion = "1.3.14"
  private val awsUtilsVersion = "0.1.65" 

  lazy val awsSsm = "software.amazon.awssdk" % "ssm" % "2.41.9"
  lazy val backendCheckUtils = "uk.gov.nationalarchives" %% "tdr-backend-checks-utils" % "0.1.169"
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.19"
  lazy val circeCore = "io.circe" %% "circe-core" % circeVersion
  lazy val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  lazy val circeParser = "io.circe" %% "circe-parser" % circeVersion
  lazy val generatedGraphql = "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.446"
  lazy val graphqlClient = "uk.gov.nationalarchives" %% "tdr-graphql-client" % "0.0.263"
  lazy val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6"
  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.5.24"
  lazy val logstashLogbackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "9.0"
  lazy val wiremockStandalone = "com.github.tomakehurst" % "wiremock-standalone" % "3.0.1"
  lazy val keycloakMock = "com.tngtech.keycloakmock" % "mock" % "0.19.0"
  lazy val mockito = "org.mockito" %% "mockito-scala" % "2.0.0"
  lazy val typesafe = "com.typesafe" % "config" % "1.4.5"
  lazy val authUtils = "uk.gov.nationalarchives" %% "tdr-auth-utils" % "0.0.266"
}
