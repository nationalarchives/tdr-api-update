package uk.gov.nationalarchives.api.update.utils

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.nationalarchives.BackendCheckUtils.S3Input

import scala.io.Source.fromResource

class ExternalServicesTest extends AnyFlatSpec with BeforeAndAfterEach with BeforeAndAfterAll with ScalaFutures {
  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))

  val wiremockGraphqlServer = new WireMockServer(9001)
  val wiremockAuthServer = new WireMockServer(9002)
  val wiremockSsmServer = new WireMockServer(9004)
  val wiremockS3Server = new WireMockServer(9005)

  def setupSsmServer(): Unit = {
    wiremockSsmServer
      .stubFor(post(urlEqualTo("/"))
        .willReturn(okJson("{\"Parameter\":{\"Name\":\"string\",\"Value\":\"string\"}}"))
      )
  }

  def setupS3ForWrite(): Unit = {
    wiremockS3Server.stubFor(put(anyUrl()).willReturn(ok()))
  }

  def putJsonFile(s3Input: S3Input, inputJson: String): S3Input = {
    wiremockS3Server
      .stubFor(get(urlEqualTo(s"/${s3Input.bucket}/${s3Input.key}")).willReturn(ok(inputJson)))
    s3Input
  }

  val graphQlPath = "/graphql"
  val authPath = "/auth/realms/tdr/protocol/openid-connect/token"

  def graphQlUrl: String = wiremockGraphqlServer.url(graphQlPath)

  def graphqlOkJson(): StubMapping = {
    wiremockGraphqlServer.stubFor(post(urlEqualTo(graphQlPath))
      .withRequestBody(containing("addBulkAntivirusMetadata"))
      .willReturn(okJson(fromResource(s"json/av_response.json").mkString)))

    wiremockGraphqlServer.stubFor(post(urlEqualTo(graphQlPath))
      .withRequestBody(containing("addBulkFFIDMetadata"))
      .willReturn(okJson(fromResource(s"json/ffid_response.json").mkString)))

    wiremockGraphqlServer.stubFor(post(urlEqualTo(graphQlPath))
      .withRequestBody(containing("addMultipleFileMetadata"))
      .willReturn(okJson(fromResource(s"json/checksum_response.json").mkString)))

    wiremockGraphqlServer.stubFor(post(urlEqualTo(graphQlPath))
      .withRequestBody(containing("ConsignmentStatus"))
      .willReturn(okJson(fromResource(s"json/consignment_status_response.json").mkString)))
  }

  def authOkJson(): StubMapping = wiremockAuthServer.stubFor(post(urlEqualTo(authPath))
    .willReturn(okJson("""{"access_token": "abcde"}""")))

  def authUnavailable: StubMapping = wiremockAuthServer.stubFor(post(urlEqualTo(authPath)).willReturn(serverError()))

  def graphqlUnavailable: StubMapping = wiremockGraphqlServer.stubFor(post(urlEqualTo(graphQlPath)).willReturn(serverError()))

  override def beforeEach(): Unit = {
    setupSsmServer()
  }

  override def beforeAll(): Unit = {
    setupS3ForWrite()
    wiremockGraphqlServer.start()
    wiremockAuthServer.start()
    wiremockSsmServer.start()
    wiremockS3Server.start()
  }

  override def afterAll(): Unit = {
    wiremockGraphqlServer.stop()
    wiremockAuthServer.stop()
    wiremockSsmServer.stop()
    wiremockS3Server.stop()
  }

  override def afterEach(): Unit = {
    wiremockAuthServer.resetAll()
    wiremockGraphqlServer.resetAll()
    wiremockSsmServer.resetAll()
  }
}
