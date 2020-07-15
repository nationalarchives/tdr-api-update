import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.1.1"
  lazy val circeCore = "io.circe" %% "circe-core" % "0.13.0"
  lazy val circeGeneric = "io.circe" %% "circe-generic" % "0.13.0"
  lazy val circeParser = "io.circe" %% "circe-parser" % "0.13.0"
  lazy val generatedGraphql = "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.54"
  lazy val lambdaCore = "com.amazonaws" % "aws-lambda-java-core" % "1.2.1"
  lazy val lambdaEvents = "com.amazonaws" % "aws-lambda-java-events" % "2.2.9"
  lazy val graphqlClient = "uk.gov.nationalarchives" %% "tdr-graphql-client" % "0.0.12"
  lazy val authUtils = "uk.gov.nationalarchives" %% "tdr-auth-utils" % "0.0.14"
  lazy val wiremock = "com.github.tomakehurst" % "wiremock-jre8" % "2.26.0"
  lazy val keycloakMock = "com.tngtech.keycloakmock" % "mock" % "0.3.0"
  lazy val mockito = "org.mockito" %% "mockito-scala" % "1.14.1"
  lazy val sqs = "software.amazon.awssdk" % "sqs" % "2.13.5"
  lazy val typesafe = "com.typesafe" % "config" % "1.4.0"
  lazy val sqsMock = "io.findify" %% "sqsmock" % "0.3.4"
}
