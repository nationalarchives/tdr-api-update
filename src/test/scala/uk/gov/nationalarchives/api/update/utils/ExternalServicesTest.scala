package uk.gov.nationalarchives.api.update.utils

import java.net.URI

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import io.findify.sqsmock.SQSService
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.{CreateQueueRequest, DeleteMessageRequest, ReceiveMessageRequest}

import scala.concurrent.ExecutionContext
import scala.io.Source.fromResource
import scala.jdk.CollectionConverters._

class ExternalServicesTest extends AnyFlatSpec with BeforeAndAfterEach with BeforeAndAfterAll with ScalaFutures {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))

  val wiremockGraphqlServer = new WireMockServer(9001)
  val wiremockAuthServer = new WireMockServer(9002)
  val wiremockKmsEndpoint = new WireMockServer(9003)

  def stubKmsResponse(cipherText: String): StubMapping =
    wiremockKmsEndpoint.stubFor(post(urlEqualTo("/"))
      .withRequestBody(equalToJson(s"""{"CiphertextBlob":"$cipherText","EncryptionContext":{"LambdaFunctionName":"test-lambda-function"}}"""))
      .willReturn(okJson(s"""{"Plaintext": "$cipherText"}""")))

  val port = 8001
  val account = 1
  val queueName = "testqueue"
  val api = new SQSService(port, account)
  val queueUrl = s"http://localhost:$port/$account/$queueName"

  implicit val ec: ExecutionContext = ExecutionContext.global

  val graphQlPath = "/graphql"
  val authPath = "/auth/realms/tdr/protocol/openid-connect/token"

  def graphQlUrl: String = wiremockGraphqlServer.url(graphQlPath)

  def graphqlOkJson(jsonLocation: String): StubMapping = wiremockGraphqlServer.stubFor(post(urlEqualTo(graphQlPath))
    .willReturn(okJson(fromResource(s"json/$jsonLocation.json").mkString)))

  def authOkJson(jsonLocation: String): StubMapping = wiremockAuthServer.stubFor(post(urlEqualTo(authPath))
    .willReturn(okJson(fromResource(s"json/$jsonLocation.json").mkString)))

  def authUnavailable: StubMapping = wiremockAuthServer.stubFor(post(urlEqualTo(authPath)).willReturn(serverError()))

  def graphqlUnavailable: StubMapping = wiremockGraphqlServer.stubFor(post(urlEqualTo(graphQlPath)).willReturn(serverError()))

  def client: SqsClient = SqsClient.builder()
    .region(Region.EU_WEST_2)
    .endpointOverride(new URI(s"http://localhost:$port"))
    .build()

  override def beforeEach(): Unit = {
    stubKmsResponse("aHR0cDovL2xvY2FsaG9zdDo5MDAxL2dyYXBocWw=")
    stubKmsResponse("aHR0cDovL2xvY2FsaG9zdDo5MDAyL2F1dGg=")
    stubKmsResponse("aWQ=")
    stubKmsResponse("c2VjcmV0")
    stubKmsResponse("aHR0cDovL2xvY2FsaG9zdDo4MDAxLzEvdGVzdHF1ZXVl")
  }

  override def beforeAll(): Unit = {
    wiremockGraphqlServer.start()
    wiremockAuthServer.start()
    wiremockKmsEndpoint.start()
    api.start()

    val request = CreateQueueRequest.builder().queueName(queueName).build()
    client.createQueue(request)
  }

  override def afterAll(): Unit = {
    wiremockGraphqlServer.stop()
    wiremockAuthServer.stop()
    wiremockKmsEndpoint.stop()
    api.shutdown()
  }

  override def afterEach(): Unit = {
    wiremockAuthServer.resetAll()
    wiremockGraphqlServer.resetAll()
    wiremockKmsEndpoint.resetAll()
    client.receiveMessage(
      ReceiveMessageRequest
        .builder
        .queueUrl(queueUrl)
        .maxNumberOfMessages(10)
        .build
    ).messages.asScala.foreach(msg => {
      client.deleteMessage(DeleteMessageRequest.builder.queueUrl(queueUrl).receiptHandle(msg.receiptHandle).build())
    })
  }
}

