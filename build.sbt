import Dependencies._
import sbt.Keys.fork

ThisBuild / scalaVersion := "2.13.8"
ThisBuild / version := "0.1.0"
ThisBuild / organization := "uk.gov.nationalarchives"
ThisBuild / organizationName := "api-update"

libraryDependencies ++= Seq(
  circeCore,
  circeGeneric,
  circeParser,
  generatedGraphql,
  graphqlClient,
  lambdaCore,
  lambdaEvents,
  authUtils,
  awsUtils,
  sqs,
  typesafe,
  scalaLogging,
  logback,
  logstashLogbackEncoder,
  mockito % Test,
  wiremock % Test,
  scalaTest % Test,
  keycloakMock % Test,
  elasticMq % Test,
  elasticMqSqs % Test
)

(Test / fork) := true
(Test / javaOptions) += s"-Dconfig.file=${sourceDirectory.value}/test/resources/application.conf"

(assembly / assemblyMergeStrategy) := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}
resolvers += "TDR Releases" at "s3://tdr-releases-mgmt"
(assembly / assemblyJarName) := "api-update.jar"
