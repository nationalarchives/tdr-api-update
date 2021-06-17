package uk.gov.nationalarchives.api.update

import java.net.URI

import com.typesafe.config.{Config, ConfigFactory}
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

class Processor[Input, Data, Variables](document: Document, variablesFn: Input => Variables, config: Map[String, String])(implicit val excecutionContext: ExecutionContext, val decoder: Decoder[Input], val dataDecoder: Decoder[Data], val variablesEncoder: Encoder[Variables]) {
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

  def process(input: Input, receiptHandle: String): Future[String] = {
    val response: Future[Data] =
      apiUpdate.send[Data, Variables](keycloakUtils, client, document, variablesFn(input))
    response.map(_ => {
      SQSUpdate(sqsClient).deleteSqsMessage(config("sqs.url"), receiptHandle)
      s"$input was successful"
    })
  }
}
