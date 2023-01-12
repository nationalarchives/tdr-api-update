import Dependencies._
import sbt.Keys.fork

ThisBuild / scalaVersion := "2.13.10"
ThisBuild / version := "0.1.0"
ThisBuild / organization := "uk.gov.nationalarchives"
ThisBuild / organizationName := "api-update"

libraryDependencies ++= Seq(
  awsSsm,
  circeCore,
  circeGeneric,
  circeParser,
  generatedGraphql,
  graphqlClient,
  authUtils,
  typesafe,
  scalaLogging,
  logback,
  logstashLogbackEncoder,
  mockito % Test,
  wiremock % Test,
  scalaTest % Test,
  keycloakMock % Test
)

(Test / fork) := true
(Test / javaOptions) += s"-Dconfig.file=${sourceDirectory.value}/test/resources/application.conf"
(Test / envVars) := Map("AWS_ACCESS_KEY_ID" -> "test", "AWS_SECRET_ACCESS_KEY" -> "test")

(assembly / assemblyMergeStrategy) := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}
(assembly / assemblyJarName) := "api-update.jar"
