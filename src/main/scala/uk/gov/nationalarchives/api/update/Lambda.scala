package uk.gov.nationalarchives.api.update

import com.typesafe.config.ConfigFactory
import graphql.codegen.AddBulkAntivirusMetadata.{addBulkAntivirusMetadata => avbm}
import graphql.codegen.AddBulkFFIDMetadata.{addBulkFFIDMetadata => abfim}
import graphql.codegen.AddMultipleFileMetadata.{addMultipleFileMetadata => amfm}
import graphql.codegen.types._
import io.circe.Printer
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
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
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

  def getInputs(inputStream: InputStream): Future[Input] = {
    val body = Source.fromInputStream(inputStream).getLines().mkString
    decode[Input](body) match {
      case Left(error) => Future.failed(error)
      case Right(input) => Future.successful(input)
    }
  }

  def getFileMetadata(input: Input): List[AddFileMetadataWithFileIdInputValues] = {
    input.results.flatMap(_.fileCheckResults.checksum.map(c => AddFileMetadataWithFileIdInputValues("SHA256ServerSideChecksum", c.fileId, c.sha256Checksum))) ++
    input.redactedResults.redactedFiles.map(r => AddFileMetadataWithFileIdInputValues("OriginalFilepath", r.redactedFileId, r.originalFilePath))
  }

  def update(inputStream: InputStream, output: OutputStream): Unit = {
    val result = for {
      input <- getInputs(inputStream)
      token <- keycloakUtils.serviceAccountToken(config("client.id"), clientSecret)
      av: avbm.Data <- RequestSender[avbm.Data, avbm.Variables]().sendRequest(token, avbm.document, avbm.Variables(AddAntivirusMetadataInput(input.results.flatMap(_.fileCheckResults.antivirus))))
      file: amfm.Data <- RequestSender[amfm.Data, amfm.Variables].sendRequest(token, amfm.document, amfm.Variables(AddFileMetadataWithFileIdInput(getFileMetadata(input))))
      ffid: abfim.Data <- RequestSender[abfim.Data, abfim.Variables].sendRequest(token, abfim.document, abfim.Variables(FFIDMetadataInput(input.results.flatMap(_.fileCheckResults.fileFormat))))
    } yield {
      output.write(Result(av, file, ffid).asJson.printWith(Printer.noSpaces).getBytes())
    }
    Await.result(result, 60.seconds)
  }
}

object Lambda {
  case class Result(antivirus: avbm.Data, file: amfm.Data, ffid: abfim.Data)

  case class ChecksumResult(sha256Checksum: String, fileId: UUID)

  case class FileCheckResults(antivirus: List[AddAntivirusMetadataInputValues], checksum: List[ChecksumResult], fileFormat: List[FFIDMetadataInputValues])

  case class File(fileId: UUID,
                  userId: UUID,
                  consignmentId: UUID,
                  fileSize: String,
                  clientChecksum: String,
                  originalPath: String,
                  fileCheckResults: FileCheckResults
                 )

  case class RedactedResult(redactedFiles: List[RedactedFilePairs], errors: List[RedactedErrors])

  case class RedactedErrors(fileId: UUID, cause: String)

  case class RedactedFilePairs(originalFileId: UUID, originalFilePath: String, redactedFileId: UUID, redactedFilePath: String)

  case class Input(results: List[File], redactedResults: RedactedResult)
}
