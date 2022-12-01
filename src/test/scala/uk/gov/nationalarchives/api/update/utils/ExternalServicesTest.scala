package uk.gov.nationalarchives.api.update.utils

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import io.circe.{Encoder, Json}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.nationalarchives.api.update.Lambda._
import io.circe.syntax._
import io.circe.generic.auto._
import scala.io.Source.fromResource

class ExternalServicesTest extends AnyFlatSpec with BeforeAndAfterEach with BeforeAndAfterAll with ScalaFutures {
  implicit val fileEncoder: Encoder[File] = (file: File) => {
    val resultsJson = file.results.map {
      case AV(antivirus) => Json.obj(("antivirus", antivirus.asJson))
      case Checksum(checksum) => Json.obj(("checksum", checksum.asJson))
      case FileFormat(fileFormat) => Json.obj(("fileFormat", fileFormat.asJson))
    }
    Json.obj(
      ("userId", Json.fromString(file.userId.toString)),
      ("consignmentId", Json.fromString(file.consignmentId.toString)),
      ("fileId", Json.fromString(file.fileId.toString)),
      ("results", Json.fromValues(resultsJson))
    )
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))

  val wiremockGraphqlServer = new WireMockServer(9001)
  val wiremockAuthServer = new WireMockServer(9002)
  val wiremockSsmServer = new WireMockServer(9004)

  def setupSsmServer(): Unit = {
    wiremockSsmServer
      .stubFor(post(urlEqualTo("/"))
        .willReturn(okJson("{\"Parameter\":{\"Name\":\"string\",\"Value\":\"string\"}}"))
      )
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
  }

  def authOkJson(): StubMapping = wiremockAuthServer.stubFor(post(urlEqualTo(authPath))
    .willReturn(okJson("""{"access_token": "abcde"}""")))

  def authUnavailable: StubMapping = wiremockAuthServer.stubFor(post(urlEqualTo(authPath)).willReturn(serverError()))

  def graphqlUnavailable: StubMapping = wiremockGraphqlServer.stubFor(post(urlEqualTo(graphQlPath)).willReturn(serverError()))

  override def beforeEach(): Unit = {
    setupSsmServer()
  }

  override def beforeAll(): Unit = {
    wiremockGraphqlServer.start()
    wiremockAuthServer.start()
    wiremockSsmServer.start()
  }

  override def afterAll(): Unit = {
    wiremockGraphqlServer.stop()
    wiremockAuthServer.stop()
    wiremockSsmServer.stop()
  }

  override def afterEach(): Unit = {
    wiremockAuthServer.resetAll()
    wiremockGraphqlServer.resetAll()
    wiremockSsmServer.resetAll()
  }
}
