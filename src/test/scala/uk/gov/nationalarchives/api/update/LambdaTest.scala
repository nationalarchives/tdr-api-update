package uk.gov.nationalarchives.api.update

import com.github.tomakehurst.wiremock.client.WireMock.{equalToJson, postRequestedFor, urlEqualTo}
import com.github.tomakehurst.wiremock.http.RequestMethod
import io.circe.Printer.noSpaces
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser.decode
import org.scalatest.matchers.should.Matchers._
import sttp.client3.HttpError
import uk.gov.nationalarchives.BackendCheckUtils._
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
    val s3Input = setupS3()
    val lambda = new Lambda()

    val inputStream = new ByteArrayInputStream(s3Input.getBytes())

    lambda.update(inputStream, new ByteArrayOutputStream())

    val serveEvents = wiremockGraphqlServer.getAllServeEvents.asScala
    serveEvents.size should equal(5)
    serveEvents.count(_.getRequest.getBodyAsString.contains("addBulkAntivirusMetadata")) should equal(1)
    serveEvents.count(_.getRequest.getBodyAsString.contains("addBulkFFIDMetadata")) should equal(1)
    serveEvents.count(_.getRequest.getBodyAsString.contains("addMultipleFileMetadata")) should equal(1)
    serveEvents.count(_.getRequest.getBodyAsString.contains("addConsignmentStatus")) should equal(1)
    serveEvents.count(_.getRequest.getBodyAsString.contains("updateConsignmentStatus")) should equal(1)
  }

  "The update method" should "return an error if there is an error from keycloak" in {
    authUnavailable
    val s3Input = setupS3()

    val inputStream = new ByteArrayInputStream(s3Input.getBytes())
    val ex = intercept[HttpError[String]] {
      new Lambda().update(inputStream, new ByteArrayOutputStream())
    }
    ex.getMessage should equal("statusCode: 500, response: ")
  }

  "The update method" should "return an error if there is an error from the API" in {
    authOkJson()
    graphqlUnavailable
    val s3Input = setupS3()
    val inputStream = new ByteArrayInputStream(s3Input.getBytes())
    val ex = intercept[HttpException] {
      new Lambda().update(inputStream, new ByteArrayOutputStream())
    }
    ex.getMessage.contains("Unexpected response from GraphQL API: Response(Left(),500,Server Error") should equal(true)
  }

  "The update method" should "return the correct json" in {
    val fileId = UUID.fromString("2ecc4d46-9c8b-46cd-b2a4-8ac2a52001b3")
    val s3Input = setupS3(fileId, Some("source-bucket"), Some("source/object/key"))
    authOkJson()
    graphqlOkJson()
    val inputStream = new ByteArrayInputStream(s3Input.getBytes())
    val outputStream = new ByteArrayOutputStream()
    new Lambda().update(inputStream, outputStream)

    results.head.s3SourceBucket.get should equal("source-bucket")
    results.head.s3SourceBucketKey.get should equal("source/object/key")

    val result = results.head.fileCheckResults

    val av = result.antivirus
    val file = result.checksum
    val ffid = result.fileFormat

    av.size should equal(1)
    file.size should equal(1)
    ffid.size should equal(1)
    av.head.fileId should equal(fileId)
    file.head.fileId should equal(fileId)
    file.last.fileId should equal(fileId)
    ffid.head.fileId should equal(fileId)
    ffid.head.matches.size should equal(1)
  }

  "The update method" should "not error when optional s3 source state is not present" in {
    val fileId = UUID.fromString("2ecc4d46-9c8b-46cd-b2a4-8ac2a52001b3")
    val s3Input = setupS3(fileId)
    authOkJson()
    graphqlOkJson()
    val inputStream = new ByteArrayInputStream(s3Input.getBytes())
    val outputStream = new ByteArrayOutputStream()
    new Lambda().update(inputStream, outputStream)

    results.head.s3SourceBucket should equal(None)
    results.head.s3SourceBucketKey should equal(None)
  }

  "The getInputs method" should "return the correct results for valid json" in {
    val fileId = UUID.randomUUID()
    val s3Input = setupS3(fileId, Some("source-bucket"), Some("source/object/key"))
    val inputStream = new ByteArrayInputStream(s3Input.getBytes())
    val (input, _) = new Lambda().getInput(inputStream).futureValue
    val s3SourceBucket = input.results.flatMap(_.s3SourceBucket.get)
    val s3SourceBucketKey = input.results.flatMap(_.s3SourceBucketKey.get)
    val avInput = input.results.flatMap(_.fileCheckResults.antivirus)
    val fileMetadataInput = input.results.flatMap(_.fileCheckResults.checksum)
    val ffidInput = input.results.flatMap(_.fileCheckResults.fileFormat)

    s3SourceBucket.mkString should equal("source-bucket")
    s3SourceBucketKey.mkString should equal("source/object/key")

    avInput.size should equal(1)
    fileMetadataInput.size should equal(1)
    ffidInput.size should equal(1)

    avInput.head.fileId should equal(fileId)
    fileMetadataInput.head.fileId should equal(fileId)
    fileMetadataInput.last.fileId should equal(fileId)
    ffidInput.head.fileId should equal(fileId)

    val ffidMatches = ffidInput.head.matches
    ffidMatches.size should equal(1)
    ffidMatches.head.formatName should equal(Some("format-name"))
    ffidMatches.head.fileExtensionMismatch should equal(Some(false))
    ffidMatches.head.puid should equal(Some("x-fmt/111"))
    ffidMatches.head.extension should equal(Some("txt"))
    ffidMatches.head.identificationBasis should equal("Some basis")
  }

  "The getInputs method" should "not error when optional s3 source state is not present" in {
    val fileId = UUID.randomUUID()
    val s3Input = setupS3(fileId)
    val inputStream = new ByteArrayInputStream(s3Input.getBytes())
    val (input, _) = new Lambda().getInput(inputStream).futureValue
    val s3SourceBucket = input.results.flatMap(_.s3SourceBucket)
    val s3SourceBucketKey = input.results.flatMap(_.s3SourceBucketKey)
    s3SourceBucket.mkString should equal("")
    s3SourceBucketKey.mkString should equal("")
  }

  "The getInputs method" should "return an error for invalid json" in {
    val json = "{}"
    val inputStream = new ByteArrayInputStream(json.getBytes())
    val ex = new Lambda().getInput(inputStream).failed.futureValue
    ex.getMessage should equal("DecodingFailure at .key: Missing required field")
  }

  def setupS3(fileId: UUID = UUID.randomUUID(), s3SourceBucket: Option[String] = None, s3SourceBucketKey: Option[String] = None): String = {
    val inputJson = getInputJson(fileId, s3SourceBucket, s3SourceBucketKey)
    val s3Input = S3Input("testKey", "testBucket")
    putJsonFile(s3Input, inputJson).asJson.printWith(noSpaces)
  }

  def results: List[File] = wiremockS3Server.getAllServeEvents.asScala.find(_.getRequest.getMethod == RequestMethod.PUT)
    .flatMap(ev => {
      val bodyString = ev.getRequest.getBodyAsString.split("\r\n")(1)
      decode[List[File]](bodyString).toOption
    }).getOrElse(Nil)

  private def getInputJson(fileId: UUID = UUID.randomUUID(), s3SourceBucket: Option[String], s3SourceBucketKey: Option[String]): String = {
    val ffidMatch = FFIDMetadataInputMatches(Some("txt"), "Some basis", Some("x-fmt/111"), Some(false), Some("format-name"))
    val ffid = FFID(fileId, "software", "softwareVersion", "binarySignatureFileVersion", "containerSignatureFileVersion", "method", ffidMatch :: Nil) :: Nil
    val checksum = ChecksumResult("checksum", fileId) :: Nil
    val av = Antivirus(fileId, "software", "softwareVersion", "databaseVersion", "result", 1L) :: Nil
    Input(
      List(File(UUID.randomUUID(), fileId, UUID.randomUUID(), "standard", "0", "originalFilePath", "checksum", s3SourceBucket, s3SourceBucketKey, FileCheckResults(av, checksum, ffid))),
      RedactedResults(RedactedFilePairs(UUID.randomUUID(), "original", fileId, "redacted") :: Nil, Nil),
      StatusResult(
        List(
          Status(UUID.randomUUID(), "Consignment", "Status", "StatusValue", overwrite = false),
          Status(UUID.randomUUID(), "Consignment", "OverwriteStatus", "OverwriteStatusValue", overwrite = true)
        )
      )
    ).asJson.deepDropNullValues.printWith(noSpaces)
  }
}
