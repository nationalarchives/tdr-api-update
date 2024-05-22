package uk.gov.nationalarchives.api.update

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import com.typesafe.config.ConfigFactory
import graphql.codegen.AddBulkAntivirusMetadata.{addBulkAntivirusMetadata => avbm}
import graphql.codegen.AddBulkFFIDMetadata.{addBulkFFIDMetadata => abfim}
import graphql.codegen.AddConsignmentStatus.{addConsignmentStatus => acs}
import graphql.codegen.AddMultipleFileMetadata.{addMultipleFileMetadata => amfm}
import graphql.codegen.AddMultipleFileStatuses.{addMultipleFileStatuses => amfs}
import graphql.codegen.UpdateConsignmentStatus.{updateConsignmentStatus => ucs}
import graphql.codegen.types.{FFIDMetadataInputMatches, _}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}
import uk.gov.nationalarchives.BackendCheckUtils
import uk.gov.nationalarchives.BackendCheckUtils._
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment}

import java.io.{InputStream, OutputStream}
import java.net.URI
import java.util.Calendar
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

  private val endpoint = sys.env("S3_ENDPOINT")

  val backendCheckUtils: BackendCheckUtils = BackendCheckUtils(endpoint)

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

  def getInput(inputStream: InputStream): Future[(Input, S3Input)] =
    (for {
      s3Input <- decode[S3Input](Source.fromInputStream(inputStream).getLines().mkString)
      input <- backendCheckUtils.getResultJson(s3Input.key, s3Input.bucket)
    } yield (input, s3Input)) match {
      case Left(error) => Future.failed(error)
      case Right(inputs) => Future.successful(inputs)
    }

  def getFileMetadata(input: Input): List[AddFileMetadataWithFileIdInputValues] = {
    input.results.flatMap(_.fileCheckResults.checksum.map(c => AddFileMetadataWithFileIdInputValues("SHA256ServerSideChecksum", c.fileId, c.sha256Checksum))) ++
    input.redactedResults.redactedFiles.map(r => AddFileMetadataWithFileIdInputValues("OriginalFilepath", r.redactedFileId, r.originalFilePath))
  }

  def sendStatuses(input: Input, token: BearerAccessToken): Future[StatusResult] = {
    val (consignmentStatuses, fileStatuses) = input.statuses.statuses.partition(_.statusType == "Consignment")

    for {
      _ <- Future.sequence {
        consignmentStatuses.map(consignmentStatus => {
          val statusInput = ConsignmentStatusInput(consignmentStatus.id, consignmentStatus.statusName, Option(consignmentStatus.statusValue))
          if(consignmentStatus.overwrite) {
            val updateConsignmentStatusVariables = ucs.Variables(statusInput)
            RequestSender[ucs.Data, ucs.Variables].sendRequest(token, ucs.document, updateConsignmentStatusVariables)
          } else {
            val consignmentStatusVariables = acs.Variables(statusInput)
            RequestSender[acs.Data, acs.Variables].sendRequest(token, acs.document, consignmentStatusVariables)
          }
        })
      }
      _ <- if (fileStatuses.nonEmpty) {
        val fileStatusVariables = amfs.Variables(AddMultipleFileStatusesInput(fileStatuses.map(fs => AddFileStatusInput(fs.id, fs.statusName, fs.statusValue))))
        RequestSender[amfs.Data, amfs.Variables].sendRequest(token, amfs.document, fileStatusVariables)
      } else {
        Future()
      }
    } yield input.statuses
  }

  def writeResults(resultJson: String, s3Input: S3Input): Future[S3Input] =
    backendCheckUtils.writeResultJson(s3Input.key, s3Input.bucket, resultJson) match {
      case Left(err) => Future.failed(err)
      case Right(value) => Future.successful(value)
    }

  def avVariables(input: Input): avbm.Variables = {
    val avResults: List[AddAntivirusMetadataInputValues] = input.results
      .flatMap(_.fileCheckResults.antivirus
        .map(av => AddAntivirusMetadataInputValues(av.fileId, av.software, av.softwareVersion, av.databaseVersion, av.result, av.datetime)))
    avbm.Variables(AddAntivirusMetadataInput(avResults))
  }

  def ffidVariables(input: Input): abfim.Variables = {
    abfim.Variables(FFIDMetadataInput(input.results
      .flatMap(_.fileCheckResults.fileFormat
        .map(ff => {
          val matches = ff.matches.map(m => FFIDMetadataInputMatches(m.extension, m.identificationBasis, m.puid, m.fileExtensionMismatch, m.formatName))
          FFIDMetadataInputValues(ff.fileId, ff.software, ff.softwareVersion ,ff.binarySignatureFileVersion, ff.containerSignatureFileVersion, ff.method, matches)
        }))))
  }

  def update(inputStream: InputStream, output: OutputStream): Unit = {
    println("=A=>" + Calendar.getInstance())
    val result = for {
      (input, s3Input) <- getInput(inputStream)
      token <- keycloakUtils.serviceAccountToken(config("client.id"), clientSecret)
      _: avbm.Data <- RequestSender[avbm.Data, avbm.Variables].sendRequest(token, avbm.document, avVariables(input))
      _: amfm.Data <- RequestSender[amfm.Data, amfm.Variables].sendRequest(token, amfm.document, amfm.Variables(AddFileMetadataWithFileIdInput(getFileMetadata(input))))
      _: abfim.Data <- RequestSender[abfim.Data, abfim.Variables].sendRequest(token, abfim.document, ffidVariables(input))
      _ <- sendStatuses(input, token)
      _ <- writeResults(input.results.asJson.printWith(Printer.noSpaces), s3Input)
    } yield {
      output.write(inputStream.readAllBytes())
    }
    println("=B=>" + Calendar.getInstance())
    Await.result(result, 1200.seconds)
    println("=C=>" + Calendar.getInstance())
  }
}
