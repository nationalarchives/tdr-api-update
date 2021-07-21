import sbt._

object Dependencies {
  private val circeVersion = "0.13.0"
  private val elasticMqVersion = "1.1.1"

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.1.1"
  lazy val circeCore = "io.circe" %% "circe-core" % circeVersion
  lazy val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  lazy val circeParser = "io.circe" %% "circe-parser" % circeVersion
  lazy val generatedGraphql = "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.62"
  lazy val lambdaCore = "com.amazonaws" % "aws-lambda-java-core" % "1.2.1"
  lazy val lambdaEvents = "com.amazonaws" % "aws-lambda-java-events" % "2.2.9"
  lazy val graphqlClient = "uk.gov.nationalarchives" %% "tdr-graphql-client" % "0.0.15"
  lazy val authUtils = "uk.gov.nationalarchives" %% "tdr-auth-utils" % "0.0.19"
  lazy val awsUtils = "uk.gov.nationalarchives.aws.utils" %% "tdr-aws-utils" % "0.1.16"
  lazy val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
  lazy val logstashLogbackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "6.6"
  lazy val wiremock = "com.github.tomakehurst" % "wiremock-jre8" % "2.26.0"
  lazy val keycloakMock = "com.tngtech.keycloakmock" % "mock" % "0.3.0"
  lazy val mockito = "org.mockito" %% "mockito-scala" % "1.14.1"
  lazy val sqs = "software.amazon.awssdk" % "sqs" % "2.13.5"
  lazy val typesafe = "com.typesafe" % "config" % "1.4.0"
  lazy val elasticMq = "org.elasticmq" %% "elasticmq-server" % elasticMqVersion
  lazy val elasticMqSqs = "org.elasticmq" %% "elasticmq-rest-sqs" % elasticMqVersion
}
