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
    serveEvents.size should equal(3)
    serveEvents.count(_.getRequest.getBodyAsString.contains("addBulkAntivirusMetadata")) should equal(1)
    serveEvents.count(_.getRequest.getBodyAsString.contains("addBulkFFIDMetadata")) should equal(1)
    serveEvents.count(_.getRequest.getBodyAsString.contains("addMultipleFileMetadata")) should equal(1)
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
    val fileId = UUID.randomUUID()
    val inputStream = new ByteArrayInputStream(getInputJson(fileId).getBytes())
    val outputStream = new ByteArrayOutputStream()
    new Lambda().update(inputStream, outputStream)
    val result = decode[List[AllInputs]](outputStream.toByteArray.map(_.toChar).mkString).toOption.get

    result.size should equal(1)
    val first = result.head
    first.avInput.size should equal(1)
    first.fileMetadataInput.size should equal(2)
    first.ffidInput.size should equal(1)
    first.avInput.head.fileId should equal(fileId)
    first.fileMetadataInput.head.fileId should equal(fileId)
    first.fileMetadataInput.last.fileId should equal(fileId)
    first.ffidInput.head.fileId should equal(fileId)
  }

  "The getInputs method" should "return the correct results for valid json" in {
    val fileId = UUID.randomUUID()
    val json = getInputJson(fileId)
    val inputStream = new ByteArrayInputStream(json.getBytes())
    val inputs = new Lambda().getInputs(inputStream).futureValue

    inputs.size should equal(1)
    val avInput = inputs.flatMap(_.avInput)
    val fileMetadataInput = inputs.flatMap(_.fileMetadataInput)
    val ffidInput = inputs.flatMap(_.ffidInput)

    avInput.size should equal(1)
    fileMetadataInput.size should equal(2)
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
    val ffid = FileFormat(FFIDMetadataInputValues(fileId, "software", "softwareVersion", "binarySignatureFileVersion", "containerSignatureFileVersion", "method", Nil))
    val checksum = Checksum(ChecksumResult("checksum", fileId))
    val av = AV(AddAntivirusMetadataInputValues(fileId, "software", "softwareVersion", "databaseVersion", "result", 1L))
    Input(List(
      File(fileId, UUID.randomUUID(), UUID.randomUUID(), List(ffid, checksum, av))),
      List(RedactedResult(RedactedFilePairs(UUID.randomUUID(), "original", fileId, "redacted") :: Nil, Nil))
    ).asJson.printWith(Printer.noSpaces)
  }
}
