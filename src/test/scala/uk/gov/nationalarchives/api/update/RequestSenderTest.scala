package uk.gov.nationalarchives.api.update

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import io.circe.generic.auto._
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import sangria.ast.Document
import sttp.client3.SttpBackend
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.matchers.should.Matchers._
import uk.gov.nationalarchives.tdr.GraphQLClient.Error
import uk.gov.nationalarchives.tdr.error.GraphQlError

import scala.reflect.ClassTag

class RequestSenderTest extends AnyFlatSpec with MockitoSugar {
  "sendRequest" should "return the correct data if present" in {
    case class Data(test: Option[String])
    case class Variables()
    val client: GraphQLClient[Data, Variables] = mock[GraphQLClient[Data, Variables]]
    when(client.getResult[Future](any[BearerAccessToken], any[Document], any[Option[Variables]])(any[SttpBackend[Future, Any]], any[ClassTag[Future[_]]]))
      .thenReturn(Future(GraphQlResponse(Option(Data(Option("test"))), List())))
    val requestSender = new RequestSender[Data, Variables](client)
    val result: Data = requestSender.sendRequest(new BearerAccessToken("token"), new Document(Vector()), Variables()).futureValue
    result.test.get should equal("test")
  }

  "sendRequest" should "return an error if the data is missing" in {
    case class Data(test: Option[String])
    case class Variables()
    val client: GraphQLClient[Data, Variables] = mock[GraphQLClient[Data, Variables]]
    when(client.getResult[Future](any[BearerAccessToken], any[Document], any[Option[Variables]])(any[SttpBackend[Future, Any]], any[ClassTag[Future[_]]]))
      .thenReturn(Future(GraphQlResponse(None, List(GraphQlError(Error("An error", Nil, Nil, None))))))
    val requestSender = new RequestSender[Data, Variables](client)
    val result: Throwable = requestSender.sendRequest(new BearerAccessToken("token"), new Document(Vector()), Variables()).failed.futureValue
    result.getMessage should equal("An error")
  }
}
