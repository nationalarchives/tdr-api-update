package uk.gov.nationalarchives.api.update

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import graphql.codegen.types.{AddAntivirusMetadataInput, AddFileMetadataInput, FFIDMetadataInput}
import io.circe
import io.circe.parser.decode
import net.logstash.logback.argument.StructuredArguments.value
import uk.gov.nationalarchives.api.update.Decoders._
import uk.gov.nationalarchives.aws.utils.Clients.kms
import uk.gov.nationalarchives.aws.utils.KMSUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

class Lambda {
  val configFactory: Config = ConfigFactory.load
  val kmsUtils: KMSUtils = KMSUtils(kms(configFactory.getString("kms.endpoint")), Map("LambdaFunctionName" -> configFactory.getString("function.name")))
  val config: Map[String, String] = kmsUtils.decryptValuesFromConfig(
    List("url.api", "url.auth", "sqs.url", "client.id", "client.secret")
  )
  val logger: Logger = Logger[Lambda]

  def update(event: SQSEvent, context: Context): Unit = {
    case class BodyWithReceiptHandle(body: String, recieptHandle: String)

    logger.info("Running API update with {} messages", value("messageCount", event.getRecords.size()))

    val results: List[Either[circe.Error, Future[String]]] = event.getRecords.asScala
      .map(r => BodyWithReceiptHandle(r.getBody, r.getReceiptHandle))
      .map(bodyWithReceiptHandle => {
        decode[Serializable](bodyWithReceiptHandle.body).map {
          case avInput: AddAntivirusMetadataInput =>
            val processor = new AntivirusProcessor(config)
            processor.process(avInput, bodyWithReceiptHandle.recieptHandle)
          case fileMetadataInput: AddFileMetadataInput =>
            val processor = new FileMetadataProcessor(config)
            processor.process(fileMetadataInput, bodyWithReceiptHandle.recieptHandle)
          case ffidMetadataInput: FFIDMetadataInput =>
            val processor = new FileFormatProcessor(config)
            processor.process(ffidMetadataInput, bodyWithReceiptHandle.recieptHandle)
        }
      }).toList

    Await.result(ResultCollector().collect(results), 10 seconds)
  }
}
