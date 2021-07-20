package uk.gov.nationalarchives.api.update.utils

import java.net.URI
import java.util

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.common.FileSource
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.{Parameters, ResponseDefinitionTransformer}
import com.github.tomakehurst.wiremock.http.{Request, ResponseDefinition}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.elasticmq.rest.sqs.TheSQSRestServerBuilder
import software.amazon.awssdk.services.sqs.model.QueueAttributeName

import java.nio.ByteBuffer
import java.nio.charset.Charset

import io.circe.generic.auto._
import io.circe.parser.decode
import org.elasticmq.rest.sqs.SQSRestServerBuilder
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
  val wiremockKmsEndpoint = new WireMockServer(new WireMockConfiguration().port(9003).extensions(new ResponseDefinitionTransformer {
    override def transform(request: Request, responseDefinition: ResponseDefinition, files: FileSource, parameters: Parameters): ResponseDefinition = {
      case class KMSRequest(CiphertextBlob: String)
      decode[KMSRequest](request.getBodyAsString) match {
        case Left(err) => throw err
        case Right(req) =>
          val charset = Charset.defaultCharset()
          val plainText = charset.newDecoder.decode(ByteBuffer.wrap(req.CiphertextBlob.getBytes(charset))).toString
          ResponseDefinitionBuilder
            .like(responseDefinition)
            .withBody(s"""{"Plaintext": "$plainText"}""")
            .build()
      }
    }
    override def getName: String = ""
  }))

  def stubKmsResponse: StubMapping =
    wiremockKmsEndpoint.stubFor(post(urlEqualTo("/")))

  val port = 8001
  val account = 1
  val queueName = "testqueue"
  val api: TheSQSRestServerBuilder = SQSRestServerBuilder.withPort(port).withAWSRegion(Region.EU_WEST_2.toString)
  val queueUrl = s"http://localhost:$port/queue/$queueName"

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
    stubKmsResponse
  }

  override def beforeAll(): Unit = {
    wiremockGraphqlServer.start()
    wiremockAuthServer.start()
    wiremockKmsEndpoint.start()
    api.start()

    val visibilityTimeoutAttributes = new util.HashMap[QueueAttributeName, String]()
    visibilityTimeoutAttributes.put(QueueAttributeName.VISIBILITY_TIMEOUT, (12 * 60 * 60).toString)

    val request = CreateQueueRequest.builder.queueName(queueUrl.split("/")(4)).attributes(visibilityTimeoutAttributes).build()
    client.createQueue(request)
  }

  override def afterAll(): Unit = {
    wiremockGraphqlServer.stop()
    wiremockAuthServer.stop()
    wiremockKmsEndpoint.stop()
  }

  override def afterEach(): Unit = {
    wiremockAuthServer.resetAll()
    wiremockGraphqlServer.resetAll()
    wiremockKmsEndpoint.resetAll()
    client.receiveMessage(
      ReceiveMessageRequest
        .builder
        .queueUrl(queueUrl)
        .visibilityTimeout(0) //Remove this once the change visibility code is added to the lambda.
        .maxNumberOfMessages(10)
        .build
    ).messages.asScala
      .foreach(msg => {
      client.deleteMessage(DeleteMessageRequest.builder.queueUrl(queueUrl).receiptHandle(msg.receiptHandle).build())
    })
  }
}
