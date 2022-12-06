package uk.gov.nationalarchives.api.update

import com.typesafe.config.ConfigFactory
import graphql.codegen.AddBulkAntivirusMetadata.{addBulkAntivirusMetadata => avbm}
import graphql.codegen.AddBulkFFIDMetadata.{addBulkFFIDMetadata => abfim}
import graphql.codegen.AddMultipleFileMetadata.{addMultipleFileMetadata => amfm}
import graphql.codegen.types._
import io.circe.{Decoder, HCursor, Json, Printer}
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}
import uk.gov.nationalarchives.api.update.Lambda._
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment}

import java.io.{InputStream, OutputStream}
import java.net.URI
import java.util.UUID
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.io.Source

class Lambda {
  val config: String => String = ConfigFactory.load.getString(_)
  private val clientSecret: String = getClientSecret(config("client.secret_path"), config("ssm.endpoint"))
  implicit val tdrKeycloakDeployment: TdrKeycloakDeployment = TdrKeycloakDeployment(config("url.auth"), "tdr", 3600)
  private val keycloakUtils: KeycloakUtils = KeycloakUtils()
  implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

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

  def getInputs(inputStream: InputStream): Future[List[AllInputs]] = {
    val body = Source.fromInputStream(inputStream).getLines().mkString
    val updateInput = decode[Input](body) match {
      case Left(error) => Future.failed(error)
      case Right(value) => Future.successful(value)
    }
    updateInput.map(input => {
      input.results.map(file => {
        file.results.foldLeft(AllInputs(Nil, Nil, Nil))((allInputs, result) => {
          result match {
            case AV(antivirus) => allInputs.copy(avInput = antivirus :: allInputs.avInput)
            case Checksum(checksum) =>
              val newValue = AddFileMetadataWithFileIdInputValues("SHA256ServerSideChecksum", checksum.fileId, checksum.sha256Checksum)
              allInputs.copy(fileMetadataInput = newValue :: allInputs.fileMetadataInput)
            case FileFormat(fileFormat) => allInputs.copy(ffidInput = fileFormat :: allInputs.ffidInput)
            case _ => throw new RuntimeException("Unexpected input")
          }
        })

      }).map(eachInput => {
        val redactedMetadata = input.redactedResults.flatMap(_.redactedFiles.map(res => AddFileMetadataWithFileIdInputValues("OriginalFilepath", res.redactedFileId, res.originalFilePath)))
        eachInput.copy(fileMetadataInput = eachInput.fileMetadataInput ++ redactedMetadata)
      })
    })
  }

  def update(input: InputStream, output: OutputStream): Unit = {
    val result = for {
      allInputs <- getInputs(input)
      token <- keycloakUtils.serviceAccountToken(config("client.id"), clientSecret)
      _ <- RequestSender[avbm.Data, avbm.Variables]().sendRequest(token, avbm.document, avbm.Variables(AddAntivirusMetadataInput(allInputs.flatMap(_.avInput))))
      _ <- RequestSender[amfm.Data, amfm.Variables].sendRequest(token, amfm.document, amfm.Variables(AddFileMetadataWithFileIdInput(allInputs.flatMap(_.fileMetadataInput))))
      _ <- RequestSender[abfim.Data, abfim.Variables].sendRequest(token, abfim.document, abfim.Variables(FFIDMetadataInput(allInputs.flatMap(_.ffidInput))))
    } yield {
      output.write(allInputs.asJson.printWith(Printer.noSpaces).getBytes())
    }
    Await.result(result, 60.seconds)
  }
}
object Lambda {
  implicit val fileDecoder: Decoder[File] = (c: HCursor) => for {
    userId <- c.downField("userId").as[UUID]
    fileId <- c.downField("fileId").as[UUID]
    consignmentId <- c.downField("consignmentId").as[UUID]
    resultsJson <- c.downField("results").as[Json]
    av <- resultsJson.findAllByKey("antivirus").head.as[AddAntivirusMetadataInputValues]
    fileFormat <- resultsJson.findAllByKey("fileFormat").head.as[FFIDMetadataInputValues]
    checksum <- resultsJson.findAllByKey("checksum").head.as[ChecksumResult]
  } yield File(fileId, userId, consignmentId, List(AV(av), FileFormat(fileFormat), Checksum(checksum)))

  trait Result {}

  case class AllInputs(avInput: List[AddAntivirusMetadataInputValues], fileMetadataInput: List[AddFileMetadataWithFileIdInputValues], ffidInput: List[FFIDMetadataInputValues])

  case class ChecksumResult(sha256Checksum: String, fileId: UUID)

  case class AV(antivirus: AddAntivirusMetadataInputValues) extends Result

  case class Checksum(checksum: ChecksumResult) extends Result

  case class FileFormat(fileFormat: FFIDMetadataInputValues) extends Result

  case class File(fileId: UUID, userId: UUID, consignmentId: UUID, results: List[Result])

  case class RedactedResult(redactedFiles: List[RedactedFilePairs], errors: List[RedactedErrors])

  case class RedactedErrors(fileId: UUID, cause: String)

  case class RedactedFilePairs(originalFileId: UUID, originalFilePath: String, redactedFileId: UUID, redactedFilePath: String)

  case class Input(results: List[File], redactedResults: List[RedactedResult])
}
