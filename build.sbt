import Dependencies._
import sbt.Keys.fork

ThisBuild / scalaVersion := "2.13.2"
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
  sqs,
  typesafe,
  mockito % Test,
  wiremock % Test,
  scalaTest % Test,
  keycloakMock % Test,
  sqsMock % Test
)

fork in Test := true
javaOptions in Test += s"-Dconfig.file=${sourceDirectory.value}/test/resources/application.conf"

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}
resolvers += "TDR Releases" at "s3://tdr-releases-mgmt"
assemblyJarName in assembly := "api-update.jar"