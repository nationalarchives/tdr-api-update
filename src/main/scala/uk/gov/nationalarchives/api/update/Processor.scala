package uk.gov.nationalarchives.api.update

import java.net.URI

import com.typesafe.config.{Config, ConfigFactory}
import graphql.codegen.AddAntivirusMetadata.{AddAntivirusMetadata => avm}
import graphql.codegen.AddFFIDMetadata.{addFFIDMetadata => afim}
import graphql.codegen.AddFileMetadata.{addFileMetadata => afm}
import graphql.codegen.types.{AddAntivirusMetadataInput, AddFileMetadataInput, FFIDMetadataInput}
import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, Encoder}
import sangria.ast.Document
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, SttpBackend}
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.keycloak.KeycloakUtils

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

trait Processor[Input, Data, Variables] {
  implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()
  val configFactory: Config = ConfigFactory.load
  val client = new GraphQLClient[Data, Variables](config("url.api"))
  val apiUpdate: ApiUpdate = ApiUpdate(config)
  val keycloakUtils: KeycloakUtils = KeycloakUtils(config("url.auth"))
  val sqsClient: SqsClient = SqsClient.builder()
    .region(Region.EU_WEST_2)
    .endpointOverride(new URI(configFactory.getString("sqs.endpoint")))
    .httpClient(ApacheHttpClient.builder.build)
    .build()

  def graphQlQuery: Document
  def config: Map[String, String]
  def variables(input: Input): Variables

  implicit def executionContext: ExecutionContext
  implicit def dataDecoder: Decoder[Data]
  implicit def variablesEncoder: Encoder[Variables]

  def process(input: Input, receiptHandle: String): Future[String] = {
    val response: Future[Data] =
      apiUpdate.send[Data, Variables](keycloakUtils, client, graphQlQuery, variables(input))
    response.map(_ => {
      SQSUpdate(sqsClient).deleteSqsMessage(config("sqs.url"), receiptHandle)
      s"$input was successful"
    })
  }
}

class AntivirusProcessor(val config: Map[String, String])(implicit val executionContext: ExecutionContext)
  extends Processor[AddAntivirusMetadataInput, avm.Data, avm.Variables] {
  override val graphQlQuery: Document = avm.document
  override def variables(input: AddAntivirusMetadataInput): avm.Variables = avm.Variables(input)
  override def dataDecoder: Decoder[avm.Data] = deriveDecoder[avm.Data]
  override def variablesEncoder: Encoder[avm.Variables] = avm.Variables.jsonEncoder
}

class FileMetadataProcessor(val config: Map[String, String])(implicit val executionContext: ExecutionContext)
  extends Processor[AddFileMetadataInput, afm.Data, afm.Variables] {
  override val graphQlQuery: Document = afm.document
  override def variables(input: AddFileMetadataInput): afm.Variables = afm.Variables(input)
  override def dataDecoder: Decoder[afm.Data] = deriveDecoder[afm.Data]
  override def variablesEncoder: Encoder[afm.Variables] = afm.Variables.jsonEncoder
}

class FileFormatProcessor(val config: Map[String, String])(implicit val executionContext: ExecutionContext)
  extends Processor[FFIDMetadataInput, afim.Data, afim.Variables] {
  override val graphQlQuery: Document = afim.document
  override def variables(input: FFIDMetadataInput): afim.Variables = afim.Variables(input)
  override def dataDecoder: Decoder[afim.Data] = deriveDecoder[afim.Data]
  override def variablesEncoder: Encoder[afim.Variables] = afim.Variables.jsonEncoder
}
