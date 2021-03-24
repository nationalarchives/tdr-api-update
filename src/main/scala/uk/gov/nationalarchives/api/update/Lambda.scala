package uk.gov.nationalarchives.api.update

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import graphql.codegen.AddAntivirusMetadata.{AddAntivirusMetadata => avm}
import graphql.codegen.AddFileMetadata.{addFileMetadata => afm}
import graphql.codegen.AddFFIDMetadata.{addFFIDMetadata => afim}
import graphql.codegen.types.{AddAntivirusMetadataInput, AddFileMetadataInput, FFIDMetadataInput}
import Decoders._
import com.typesafe.config.ConfigFactory
import io.circe
import io.circe.parser.decode
import uk.gov.nationalarchives.aws.utils.Clients.kms
import uk.gov.nationalarchives.aws.utils.KMSUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.jdk.CollectionConverters._
import scala.concurrent.duration._

class Lambda {
  val kmsUtils: KMSUtils = KMSUtils(kms, Map("LambdaFunctionName" -> ConfigFactory.load.getString("function.name")))
  val config: Map[String, String] = kmsUtils.decryptValuesFromConfig(
    List("url.api", "url.auth", "sqs.url", "client.id", "client.secret")
  )
  def update(event: SQSEvent, context: Context): Seq[String] = {
    case class BodyWithReceiptHandle(body: String, recieptHandle: String)

    val results: List[Either[circe.Error, Future[Either[String, String]]]] = event.getRecords.asScala
      .map(r => BodyWithReceiptHandle(r.getBody, r.getReceiptHandle))
      .map(bodyWithReceiptHandle => {
        decode[Serializable](bodyWithReceiptHandle.body).map {
          case avInput: AddAntivirusMetadataInput =>
            val processor = new Processor[AddAntivirusMetadataInput, avm.Data, avm.Variables](avm.document, i => avm.Variables(i), config)
            processor.process(avInput, bodyWithReceiptHandle.recieptHandle)
          case fileMetadataInput: AddFileMetadataInput =>
            println(s"file property name ${fileMetadataInput.filePropertyName}")
            val processor = new Processor[AddFileMetadataInput, afm.Data, afm.Variables](afm.document, i => afm.Variables(i), config)
            processor.process(fileMetadataInput, bodyWithReceiptHandle.recieptHandle)
          case ffidMetadataInput: FFIDMetadataInput =>
            val processor = new Processor[FFIDMetadataInput, afim.Data, afim.Variables](afim.document, i => afim.Variables(i), config)
            processor.process(ffidMetadataInput, bodyWithReceiptHandle.recieptHandle)
        }
      }).toList

    Await.result(ResultCollector().collect(results), 10 seconds)
  }
}
