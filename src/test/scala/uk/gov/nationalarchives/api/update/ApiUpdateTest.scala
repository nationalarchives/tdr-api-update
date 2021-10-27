package uk.gov.nationalarchives.api.update

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import org.mockito.ArgumentMatchers._
import org.mockito.MockitoSugar
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers._
import sangria.ast.Document
import sttp.client.{HttpError, HttpURLConnectionBackend, Identity, NothingT, Response, SttpBackend}
import sttp.model.StatusCode
import uk.gov.nationalarchives.api.update.utils.ExternalServicesTest
import uk.gov.nationalarchives.api.update.utils.TestGraphQLObjects.{Data, TestResponse, Variables}
import uk.gov.nationalarchives.tdr.GraphQLClient.Extensions
import uk.gov.nationalarchives.tdr.error.{GraphQlError, HttpException}
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment}
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import scala.concurrent.Future
import scala.reflect.ClassTag

class ApiUpdateTest extends ExternalServicesTest with MockitoSugar with EitherValues {
  val config = Map("client.id" -> "clientId", "client.secret" -> "secret", "url.auth" -> "something")

  implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()
  implicit val tdrKeycloakDeployment: TdrKeycloakDeployment = TdrKeycloakDeployment(config("url.auth"), "tdr", 3600)

  "The send method" should "request a service account token" in {
    val apiUpdate = ApiUpdate(config)

    val client = mock[GraphQLClient[Data, Variables]]
    val document = mock[Document]
    val keycloakUtils = mock[KeycloakUtils]

    when(keycloakUtils.serviceAccountToken[Identity](any[String], any[String])(
      any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]], any[TdrKeycloakDeployment]))
      .thenReturn(Future.successful(new BearerAccessToken("token")))
    when(client.getResult[Identity](any[BearerAccessToken], any[Document], any[Option[Variables]])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]]))
      .thenReturn(Future.successful(GraphQlResponse(Some(Data(TestResponse())), List())))
    apiUpdate.send(keycloakUtils, client, document, Variables()).futureValue

    val expectedId = "clientId"
    val expectedSecret = "secret"

    verify(keycloakUtils).serviceAccountToken(expectedId, expectedSecret)
  }

  "The send method" should "call the graphql api with the correct data" in {
    val apiUpdate = ApiUpdate(config)

    val client = mock[GraphQLClient[Data, Variables]]
    val document = mock[Document]
    val keycloakUtils = mock[KeycloakUtils]

    val variables = Variables()

    when(keycloakUtils.serviceAccountToken[Identity](any[String], any[String])(
      any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]], any[TdrKeycloakDeployment]))
      .thenReturn(Future.successful(new BearerAccessToken("token")))
    when(client.getResult[Identity](any[BearerAccessToken], any[Document], any[Option[Variables]])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]]))
      .thenReturn(Future.successful(GraphQlResponse(Some(Data(TestResponse())), List())))
    apiUpdate.send(keycloakUtils, client, document, variables).futureValue

    verify(client).getResult(new BearerAccessToken("token"), document, Some(variables))
  }

  "The send method" should "error if the auth server is unavailable" in {
    val client = mock[GraphQLClient[Data, Variables]]
    val document = mock[Document]
    val keycloakUtils = mock[KeycloakUtils]

    val variables = Variables()

    when(keycloakUtils.serviceAccountToken[Identity](any[String], any[String])(
      any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]], any[TdrKeycloakDeployment]))
      .thenThrow(HttpError("An error occurred contacting the auth server", StatusCode.InternalServerError))

    val exception = intercept[HttpError] {
      ApiUpdate(config).send(keycloakUtils, client, document, variables).futureValue
    }
    exception.body should equal("An error occurred contacting the auth server")
  }

  "The send method" should "error if the graphql server is unavailable" in {

    val client = mock[GraphQLClient[Data, Variables]]
    val document = mock[Document]
    val keycloakUtils = mock[KeycloakUtils]

    val variables = Variables()
    val body: Either[String, String] = Left("Graphql error")

    val response = Response(body, StatusCode.ServiceUnavailable)

    when(keycloakUtils.serviceAccountToken[Identity](any[String], any[String])(
      any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]], any[TdrKeycloakDeployment]))
      .thenReturn(Future.successful(new BearerAccessToken("token")))
    when(client.getResult[Identity](any[BearerAccessToken], any[Document], any[Option[Variables]])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]])).thenThrow(new HttpException(response))

    val res = ApiUpdate(config).send(keycloakUtils, client, document, variables).failed.futureValue
    res.getMessage shouldEqual "Unexpected response from GraphQL API: Response(Left(Graphql error),503,,List(),List())"
  }

  "The send method" should "error if the graphql query returns not authorised errors" in {
    val client = mock[GraphQLClient[Data, Variables]]
    val document = mock[Document]
    val keycloakUtils = mock[KeycloakUtils]

    val variables = Variables()

    when(keycloakUtils.serviceAccountToken[Identity](any[String], any[String])(
      any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]], any[TdrKeycloakDeployment]))
      .thenReturn(Future.successful(new BearerAccessToken("token")))
    val graphqlResponse: GraphQlResponse[Data] =
      GraphQlResponse(Option.empty, List(GraphQlError(GraphQLClient.Error("Not authorised message",
        List(), List(), Some(Extensions(Some("NOT_AUTHORISED")))))))
    when(client.getResult[Identity](any[BearerAccessToken], any[Document], any[Option[Variables]])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]]))
      .thenReturn(Future.successful(graphqlResponse))

    val res = ApiUpdate(config).send(keycloakUtils, client, document, variables).failed.futureValue

    res.getMessage should include("Not authorised message")
  }

  "The send method" should "error if the graphql query returns a general error" in {
    val client = mock[GraphQLClient[Data, Variables]]
    val document = mock[Document]
    val keycloakUtils = mock[KeycloakUtils]

    val variables = Variables()

    when(keycloakUtils.serviceAccountToken[Identity](any[String], any[String])(
      any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]], any[TdrKeycloakDeployment]))
      .thenReturn(Future.successful(new BearerAccessToken("token")))
    val graphqlResponse: GraphQlResponse[Data] =
      GraphQlResponse(Option.empty, List(GraphQlError(GraphQLClient.Error("General error",
        List(), List(), Option.empty))))
    when(client.getResult[Identity](any[BearerAccessToken], any[Document], any[Option[Variables]])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]]))
      .thenReturn(Future.successful(graphqlResponse))

    val res = ApiUpdate(config).send(keycloakUtils, client, document, variables).failed.futureValue
    res.getMessage shouldEqual "GraphQL response contained errors: General error"
  }
}
