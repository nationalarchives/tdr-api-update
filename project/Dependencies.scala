import sbt._

object Dependencies {
  private val circeVersion = "0.14.3"
  private val elasticMqVersion = "1.3.14"

  lazy val awsUtils = "uk.gov.nationalarchives" %% "tdr-aws-utils" % "0.1.51"
  lazy val awsSsm = "software.amazon.awssdk" % "ssm" % "2.18.11"
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.14"
  lazy val circeCore = "io.circe" %% "circe-core" % circeVersion
  lazy val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  lazy val circeParser = "io.circe" %% "circe-parser" % circeVersion
  lazy val generatedGraphql = "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.281"
  lazy val lambdaCore = "com.amazonaws" % "aws-lambda-java-core" % "1.2.2"
  lazy val lambdaEvents = "com.amazonaws" % "aws-lambda-java-events" % "3.11.0"
  lazy val graphqlClient = "uk.gov.nationalarchives" %% "tdr-graphql-client" % "0.0.72"
  lazy val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.4.4"
  lazy val logstashLogbackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "7.2"
  lazy val wiremock = "com.github.tomakehurst" % "wiremock-jre8" % "2.35.0"
  lazy val keycloakMock = "com.tngtech.keycloakmock" % "mock" % "0.12.0"
  lazy val mockito = "org.mockito" %% "mockito-scala" % "1.17.12"
  lazy val sqs = "software.amazon.awssdk" % "sqs" % "2.18.11"
  lazy val typesafe = "com.typesafe" % "config" % "1.4.2"
  lazy val elasticMq = "org.elasticmq" %% "elasticmq-server" % elasticMqVersion
  lazy val elasticMqSqs = "org.elasticmq" %% "elasticmq-rest-sqs" % elasticMqVersion
  lazy val authUtils = "uk.gov.nationalarchives" %% "tdr-auth-utils" % "0.0.98"
}
