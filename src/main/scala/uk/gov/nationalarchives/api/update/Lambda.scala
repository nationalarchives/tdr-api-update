package uk.gov.nationalarchives.api.update

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import graphql.codegen.AddAntivirusMetadata.{AddAntivirusMetadata => av}
import graphql.codegen.AddFileMetadata.{addFileMetadata => afm}
import graphql.codegen.types.{AddAntivirusMetadataInput, AddFileMetadataInput}
import Decoders._
import io.circe
import io.circe.parser.decode

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.jdk.CollectionConverters._
import scala.concurrent.duration._

class Lambda {
  def update(event: SQSEvent, context: Context): Seq[String] = {
    case class BodyWithReceiptHandle(body: String, recieptHandle: String)

    val results: List[Either[circe.Error, Future[Either[String, String]]]] = event.getRecords.asScala
      .map(r => BodyWithReceiptHandle(r.getBody, r.getReceiptHandle))
      .map(bodyWithReceiptHandle => {
        decode[Serializable](bodyWithReceiptHandle.body).map {
          case avInput: AddAntivirusMetadataInput =>
            val processor = new Processor[AddAntivirusMetadataInput, av.Data, av.Variables](av.document, i => av.Variables(i))
            processor.process(avInput, bodyWithReceiptHandle.recieptHandle)
          case fileMetadataInput: AddFileMetadataInput =>
            val processor = new Processor[AddFileMetadataInput, afm.Data, afm.Variables](afm.document, i => afm.Variables(i))
            processor.process(fileMetadataInput, bodyWithReceiptHandle.recieptHandle)
        }
      }).toList

    Await.result(ResultCollector().collect(results), 10 seconds)


  }
}
