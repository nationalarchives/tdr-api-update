package uk.gov.nationalarchives.api.update

import com.github.tomakehurst.wiremock.client.WireMock.{equalToJson, postRequestedFor, urlEqualTo}
import graphql.codegen.types.{AddAntivirusMetadataInputValues, FFIDMetadataInputValues}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.matchers.should.Matchers._
import sttp.client3.HttpError
import uk.gov.nationalarchives.api.update.Lambda._
import uk.gov.nationalarchives.api.update.utils.ExternalServicesTest
import uk.gov.nationalarchives.tdr.error.HttpException

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.UUID
import scala.jdk.CollectionConverters.ListHasAsScala

class LambdaTest extends ExternalServicesTest {
  "Creating the lambda class" should "call systems manager" in {
    new Lambda()
    wiremockSsmServer
      .verify(postRequestedFor(urlEqualTo("/"))
        .withRequestBody(equalToJson("""{"Name" : "/a/secret/path","WithDecryption" : true}""")))
  }

  "The update method" should "send metadata to the API" in {
    graphqlOkJson()
    authOkJson()
    val lambda = new Lambda()

    val inputStream = new ByteArrayInputStream(getInputJson().getBytes())

    lambda.update(inputStream, new ByteArrayOutputStream())

    val serveEvents = wiremockGraphqlServer.getAllServeEvents.asScala
    serveEvents.size should equal(4)
    serveEvents.count(_.getRequest.getBodyAsString.contains("addBulkAntivirusMetadata")) should equal(1)
    serveEvents.count(_.getRequest.getBodyAsString.contains("addBulkFFIDMetadata")) should equal(1)
    serveEvents.count(_.getRequest.getBodyAsString.contains("addMultipleFileMetadata")) should equal(1)
    serveEvents.count(_.getRequest.getBodyAsString.contains("addConsignmentStatus")) should equal(1)
  }

  "The update method" should "return an error if there is an error from keycloak" in {
    authUnavailable

    val inputStream = new ByteArrayInputStream(getInputJson().getBytes())
    val ex = intercept[HttpError[String]] {
      new Lambda().update(inputStream, new ByteArrayOutputStream())
    }
    ex.getMessage should equal("statusCode: 500, response: ")
  }

  "The update method" should "return an error if there is an error from the API" in {
    authOkJson()
    graphqlUnavailable

    val inputStream = new ByteArrayInputStream(getInputJson().getBytes())
    val ex = intercept[HttpException] {
      new Lambda().update(inputStream, new ByteArrayOutputStream())
    }
    ex.getMessage.contains("Unexpected response from GraphQL API: Response(Left(),500,Server Error") should equal(true)
  }

  "The update method" should "return the correct json" in {
    authOkJson()
    graphqlOkJson()
    val fileId = UUID.fromString("2ecc4d46-9c8b-46cd-b2a4-8ac2a52001b3")
    val inputStream = new ByteArrayInputStream(getInputJson(fileId).getBytes())
    val outputStream = new ByteArrayOutputStream()
    new Lambda().update(inputStream, outputStream)
    val result = decode[Result](outputStream.toByteArray.map(_.toChar).mkString).toOption.get

    val av = result.antivirus.addBulkAntivirusMetadata
    val file = result.file.addMultipleFileMetadata
    val ffid = result.ffid.addBulkFFIDMetadata

    av.size should equal(1)
    file.size should equal(1)
    ffid.size should equal(1)
    av.head.fileId should equal(fileId)
    file.head.fileId should equal(fileId)
    file.last.fileId should equal(fileId)
    ffid.head.fileId should equal(fileId)
  }

  "The getInputs method" should "return the correct results for valid json" in {
    val fileId = UUID.randomUUID()
    val json = getInputJson(fileId)
    val inputStream = new ByteArrayInputStream(json.getBytes())
    val inputs = new Lambda().getInputs(inputStream).futureValue

    val avInput = inputs.results.flatMap(_.fileCheckResults.antivirus)
    val fileMetadataInput = inputs.results.flatMap(_.fileCheckResults.checksum)
    val ffidInput = inputs.results.flatMap(_.fileCheckResults.fileFormat)

    avInput.size should equal(1)
    fileMetadataInput.size should equal(1)
    ffidInput.size should equal(1)

    avInput.head.fileId should equal(fileId)
    fileMetadataInput.head.fileId should equal(fileId)
    fileMetadataInput.last.fileId should equal(fileId)
    ffidInput.head.fileId should equal(fileId)
  }

  "The getInputs method" should "return an error for invalid json" in {
    val json = "{}"
    val inputStream = new ByteArrayInputStream(json.getBytes())
    val ex = new Lambda().getInputs(inputStream).failed.futureValue
    ex.getMessage should equal("Missing required field: DownField(results)")
  }

  private def getInputJson(fileId: UUID = UUID.randomUUID()): String = {
    val ffid = FFIDMetadataInputValues(fileId, "software", "softwareVersion", "binarySignatureFileVersion", "containerSignatureFileVersion", "method", Nil) :: Nil
    val checksum = ChecksumResult("checksum", fileId) :: Nil
    val av = AddAntivirusMetadataInputValues(fileId, "software", "softwareVersion", "databaseVersion", "result", 1L) :: Nil
    Input(
      List(File(fileId, UUID.randomUUID(), UUID.randomUUID(), "0", "originalFilePath", "checksum", FileCheckResults(av, checksum, ffid))),
      RedactedResult(RedactedFilePairs(UUID.randomUUID(), "original", fileId, "redacted") :: Nil, Nil),
      Statuses(Status(UUID.randomUUID(), "Consignment", "Status", "StatusValue", overwrite = false) :: Nil),
    ).asJson.printWith(Printer.noSpaces)
  }
}
