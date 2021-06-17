package uk.gov.nationalarchives.api.update

import java.util.UUID

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import graphql.codegen.AddAntivirusMetadata.{AddAntivirusMetadata => avm}
import graphql.codegen.AddFFIDMetadata.{addFFIDMetadata => afim}
import graphql.codegen.AddFileMetadata.{addFileMetadata => afm}
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
  val statusLogs = new StatusLogs(logger)

  def update(event: SQSEvent, context: Context): Seq[String] = {
    case class BodyWithReceiptHandle(body: String, recieptHandle: String)

    logger.info("Running API update with {} messages", value("messageCount", event.getRecords.size()))

    val results: List[Either[circe.Error, Future[String]]] = event.getRecords.asScala
      .map(r => BodyWithReceiptHandle(r.getBody, r.getReceiptHandle))
      .map(bodyWithReceiptHandle => {
        decode[Serializable](bodyWithReceiptHandle.body).map {
          case avInput: AddAntivirusMetadataInput =>
            logUpdateStart(avInput.fileId, "antivirus")
            val processor = new Processor[AddAntivirusMetadataInput, avm.Data, avm.Variables](avm.document, i => avm.Variables(i), config)
            processor.process(avInput, bodyWithReceiptHandle.recieptHandle)
          case fileMetadataInput: AddFileMetadataInput =>
            logUpdateStart(fileMetadataInput.fileId, fileMetadataInput.filePropertyName)
            val processor = new Processor[AddFileMetadataInput, afm.Data, afm.Variables](afm.document, i => afm.Variables(i), config)
            processor.process(fileMetadataInput, bodyWithReceiptHandle.recieptHandle)
          case ffidMetadataInput: FFIDMetadataInput =>
            logUpdateStart(ffidMetadataInput.fileId, "FFID")
            val processor = new Processor[FFIDMetadataInput, afim.Data, afim.Variables](afim.document, i => afim.Variables(i), config)
            processor.process(ffidMetadataInput, bodyWithReceiptHandle.recieptHandle)
        }
      }).toList

    Await.result(ResultCollector().collect(results), 10 seconds)
  }

  def logUpdateStart(fileId: UUID, fileCheckType: String): Unit = {
    statusLogs.log(fileId, fileCheckType, "started")
  }
}
