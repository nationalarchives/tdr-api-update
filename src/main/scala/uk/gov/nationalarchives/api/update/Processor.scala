package uk.gov.nationalarchives.api.update

import java.net.URI
import java.util.UUID

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import graphql.codegen.AddAntivirusMetadata.{addAntivirusMetadata => avm}
import graphql.codegen.AddFFIDMetadata.{addFFIDMetadata => afim}
import graphql.codegen.AddFileMetadata.{addFileMetadata => afm}
import graphql.codegen.types.{AddAntivirusMetadataInput, AddFileMetadataWithFileIdInput, FFIDMetadataInput}
import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, Encoder}
import net.logstash.logback.argument.StructuredArguments.value
import sangria.ast.Document
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

trait Processor[Input, Data, Variables] {
  val logger: Logger = Logger[Processor[Input, Data, Variables]]
  val configFactory: Config = ConfigFactory.load

  implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  implicit val tdrKeycloakDeployment: TdrKeycloakDeployment = TdrKeycloakDeployment(config("url.auth"), "tdr", 3600)

  val client = new GraphQLClient[Data, Variables](config("url.api"))
  val apiUpdate: ApiUpdate = ApiUpdate(config)
  val keycloakUtils: KeycloakUtils = KeycloakUtils()
  val sqsClient: SqsClient = SqsClient.builder()
    .region(Region.EU_WEST_2)
    .endpointOverride(new URI(configFactory.getString("sqs.endpoint")))
    .httpClient(ApacheHttpClient.builder.build)
    .build()

  def graphQlQuery: Document
  def config: Map[String, String]
  def variables(input: Input): Variables
  def fileCheckName(input: Input): String
  def fileId(input: Input): UUID

  implicit def executionContext: ExecutionContext
  implicit def dataDecoder: Decoder[Data]
  implicit def variablesEncoder: Encoder[Variables]

  def process(input: Input, receiptHandle: String): Future[String] = {
    logStart(input)

    apiUpdate.send[Data, Variables](keycloakUtils, client, graphQlQuery, variables(input))
      .map(_ => {
        logSuccess(input)
        SQSUpdate(sqsClient).deleteSqsMessage(config("sqs.url"), receiptHandle)
        logSqsUpdate(input)
        fileId(input).toString
      })
      .recover(e => {
        logError(input, e)
        throw FailedApiUpdateException(receiptHandle, e)
      })
  }

  private def logStart(input: Input): Unit =
    logStatus("Started saving metadata", fileCheckName(input), fileId(input), "started")
  private def logSuccess(input: Input): Unit =
    logStatus("Successfully saved metadata", fileCheckName(input), fileId(input), "success")
  private def logSqsUpdate(input: Input): Unit =
    logStatus("Successfully removed message", fileCheckName(input), fileId(input), "messageDeleted")
  private def logStatus(baseMessage: String, metadataType: String, fileId: UUID, status: String): Unit = {
    logger.info(
      s"$baseMessage. Metadata type: '{}', file ID: '{}', status: '{}'",
      value("metadataType", metadataType),
      value("fileId", fileId),
      value("apiUpdateStatus", status)
    )
  }

  private def logError(input: Input, e: Throwable): Unit = {
    // ScalaLogging does not have a method for logging structured data and a full error in the same message, so log them separately
    logger.error(
      "Error saving {} metadata for file ID '{}'. Status: {}",
      value("metadataType", fileCheckName(input)),
      value("fileId", fileId(input)),
      value("apiUpdateStatus", "error")
    )
    logger.error(s"Error saving ${fileCheckName(input)} metadata for file ID '${fileId(input)}'", e)
  }
}

class AntivirusProcessor(val config: Map[String, String])(implicit val executionContext: ExecutionContext)
  extends Processor[AddAntivirusMetadataInput, avm.Data, avm.Variables] {
  override val graphQlQuery: Document = avm.document
  override def variables(input: AddAntivirusMetadataInput): avm.Variables = avm.Variables(input)
  override def dataDecoder: Decoder[avm.Data] = deriveDecoder[avm.Data]
  override def variablesEncoder: Encoder[avm.Variables] = avm.Variables.jsonEncoder

  override def fileCheckName(input: AddAntivirusMetadataInput): String = "antivirus"
  override def fileId(input: AddAntivirusMetadataInput): UUID = input.fileId
}

class FileMetadataProcessor(val config: Map[String, String])(implicit val executionContext: ExecutionContext)
  extends Processor[AddFileMetadataWithFileIdInput, afm.Data, afm.Variables] {
  override val graphQlQuery: Document = afm.document
  override def variables(input: AddFileMetadataWithFileIdInput): afm.Variables = afm.Variables(input)
  override def dataDecoder: Decoder[afm.Data] = deriveDecoder[afm.Data]
  override def variablesEncoder: Encoder[afm.Variables] = afm.Variables.jsonEncoder

  override def fileCheckName(input: AddFileMetadataWithFileIdInput): String = input.filePropertyName
  override def fileId(input: AddFileMetadataWithFileIdInput): UUID = input.fileId
}

class FileFormatProcessor(val config: Map[String, String])(implicit val executionContext: ExecutionContext)
  extends Processor[FFIDMetadataInput, afim.Data, afim.Variables] {
  override val graphQlQuery: Document = afim.document
  override def variables(input: FFIDMetadataInput): afim.Variables = afim.Variables(input)
  override def dataDecoder: Decoder[afim.Data] = deriveDecoder[afim.Data]
  override def variablesEncoder: Encoder[afim.Variables] = afim.Variables.jsonEncoder

  override def fileCheckName(input: FFIDMetadataInput): String = "FFID"
  override def fileId(input: FFIDMetadataInput): UUID = input.fileId
}
