package uk.gov.nationalarchives.api.update

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import graphql.codegen.types.{AddAntivirusMetadataInputValues, AddFileMetadataWithFileIdInput, FFIDMetadataInputValues}
import io.circe.parser.decode
import net.logstash.logback.argument.StructuredArguments.value
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest
import uk.gov.nationalarchives.api.update.Decoders._
import uk.gov.nationalarchives.aws.utils.kms.KMSClients.kms
import uk.gov.nationalarchives.aws.utils.sqs.SQSClients.sqs
import uk.gov.nationalarchives.aws.utils.kms.KMSUtils
import uk.gov.nationalarchives.aws.utils.sqs.SQSUtils

import java.net.URI
import java.time.Instant
import scala.annotation.unused
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

class Lambda {
  val configFactory: Config = ConfigFactory.load
  val kmsUtils: KMSUtils = KMSUtils(kms(configFactory.getString("kms.endpoint")), Map("LambdaFunctionName" -> configFactory.getString("function.name")))

  val config: Map[String, String] = List("url.api", "url.auth", "sqs.url", "client.id")
    .map(configName => configName -> kmsUtils.decryptValue(configFactory.getString(configName))).toMap +
    ("client.secret" -> getClientSecret(configFactory.getString("client.secret_path"), configFactory.getString("ssm.endpoint")))
  val logger: Logger = Logger[Lambda]
  val sqsUtils: SQSUtils = SQSUtils(sqs(configFactory.getString("sqs.endpoint")))

  def getClientSecret(secretPath: String, endpoint: String): String = {
    val httpClient = ApacheHttpClient.builder.build
    val ssmClient: SsmClient = SsmClient.builder()
      .endpointOverride(URI.create(endpoint))
      .httpClient(httpClient)
      .region(Region.EU_WEST_2)
      .build()
    val getParameterRequest = GetParameterRequest.builder.name(secretPath).withDecryption(true).build
    ssmClient.getParameter(getParameterRequest).parameter().value()
  }

  def update(event: SQSEvent, @unused context: Context): Unit = {
    implicit val startTime: Instant = Instant.now
    case class BodyWithReceiptHandle(body: String, receiptHandle: String)

    logger.info("Running API update with {} messages", value("messageCount", event.getRecords.size()))

    val results: List[Either[FailedApiUpdateException, Future[String]]] = event.getRecords.asScala
      .map(r => BodyWithReceiptHandle(r.getBody, r.getReceiptHandle))
      .map(bodyWithReceiptHandle => {
        decode[Serializable](bodyWithReceiptHandle.body).map {
          case avInput: AddAntivirusMetadataInputValues =>
            val processor = new AntivirusProcessor(config)
            processor.process(avInput, bodyWithReceiptHandle.receiptHandle)
          case fileMetadataInput: AddFileMetadataWithFileIdInput =>
            val processor = new FileMetadataProcessor(config)
            processor.process(fileMetadataInput, bodyWithReceiptHandle.receiptHandle)
          case ffidMetadataInput: FFIDMetadataInputValues =>
            val processor = new FileFormatProcessor(config)
            processor.process(ffidMetadataInput, bodyWithReceiptHandle.receiptHandle)
        }.left.map(circeError =>
          FailedApiUpdateException(bodyWithReceiptHandle.receiptHandle, circeError)
        )
      }).toList

    Await.result(ResultCollector(config, sqsUtils).collect(results), 10 seconds)
  }
}
