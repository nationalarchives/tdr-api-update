import sbt._

object Dependencies {
  private val circeVersion = "0.14.14"
  private val elasticMqVersion = "1.3.14"
  private val awsUtilsVersion = "0.1.65" 

  lazy val awsSsm = "software.amazon.awssdk" % "ssm" % "2.32.21"
  lazy val backendCheckUtils = "uk.gov.nationalarchives" %% "tdr-backend-checks-utils" % "0.1.130"
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.19"
  lazy val circeCore = "io.circe" %% "circe-core" % circeVersion
  lazy val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  lazy val circeParser = "io.circe" %% "circe-parser" % circeVersion
  lazy val generatedGraphql = "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.426"
  lazy val graphqlClient = "uk.gov.nationalarchives" %% "tdr-graphql-client" % "0.0.245"
  lazy val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.5.18"
  lazy val logstashLogbackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "8.1"
  lazy val wiremockStandalone = "com.github.tomakehurst" % "wiremock-standalone" % "3.0.1"
  lazy val keycloakMock = "com.tngtech.keycloakmock" % "mock" % "0.18.2"
  lazy val mockito = "org.mockito" %% "mockito-scala" % "2.0.0"
  lazy val typesafe = "com.typesafe" % "config" % "1.4.4"
  lazy val authUtils = "uk.gov.nationalarchives" %% "tdr-auth-utils" % "0.0.253"
}
