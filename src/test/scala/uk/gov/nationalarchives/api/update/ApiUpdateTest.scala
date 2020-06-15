package uk.gov.nationalarchives.api.update

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import com.typesafe.config.ConfigFactory
import org.mockito.ArgumentMatchers._
import org.mockito.MockitoSugar
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers._
import sangria.ast.Document
import sttp.client.{HttpError, Response}
import sttp.model.StatusCode
import uk.gov.nationalarchives.api.update.utils.ExternalServicesTest
import uk.gov.nationalarchives.api.update.utils.TestGraphQLObjects.{Data, TestResponse, Variables}
import uk.gov.nationalarchives.tdr.GraphQLClient.Extensions
import uk.gov.nationalarchives.tdr.error.{GraphQlError, HttpException}
import uk.gov.nationalarchives.tdr.keycloak.KeycloakUtils
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import scala.concurrent.Future

class ApiUpdateTest extends ExternalServicesTest with MockitoSugar with EitherValues {

  "The send method" should "request a service account token" in {
    val apiUpdate = ApiUpdate()

    val client = mock[GraphQLClient[Data, Variables]]
    val document = mock[Document]
    val keycloakUtils = mock[KeycloakUtils]

    when(keycloakUtils.serviceAccountToken(any[String], any[String]))
      .thenReturn(Future.successful(new BearerAccessToken("token")))
    when(client.getResult(any[BearerAccessToken], any[Document], any[Option[Variables]]))
      .thenReturn(Future.successful(GraphQlResponse(Some(Data(TestResponse())), List())))
    apiUpdate.send(keycloakUtils, client, document, Variables()).futureValue

    val configFactory = ConfigFactory.load
    val expectedId = configFactory.getString("client.id")
    val expectedSecret = configFactory.getString("client.secret")

    verify(keycloakUtils).serviceAccountToken(expectedId, expectedSecret)
  }

  "The send method" should "call the graphql api with the correct data" in {
    val apiUpdate = ApiUpdate()

    val client = mock[GraphQLClient[Data, Variables]]
    val document = mock[Document]
    val keycloakUtils = mock[KeycloakUtils]

    val variables = Variables()

    when(keycloakUtils.serviceAccountToken(any[String], any[String]))
      .thenReturn(Future.successful(new BearerAccessToken("token")))
    when(client.getResult(any[BearerAccessToken], any[Document], any[Option[Variables]]))
      .thenReturn(Future.successful(GraphQlResponse(Some(Data(TestResponse())), List())))
    apiUpdate.send(keycloakUtils, client, document, variables).futureValue

    verify(client).getResult(new BearerAccessToken("token"), document, Some(variables))
  }

  "The send method" should "error if the auth server is unavailable" in {
    val client = mock[GraphQLClient[Data, Variables]]
    val document = mock[Document]
    val keycloakUtils = mock[KeycloakUtils]

    val variables = Variables()

    when(keycloakUtils.serviceAccountToken(any[String], any[String]))
      .thenThrow(HttpError("An error occurred contacting the auth server"))

    val exception = intercept[HttpError] {
      ApiUpdate().send(keycloakUtils, client, document, variables).futureValue
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

    when(keycloakUtils.serviceAccountToken(any[String], any[String]))
      .thenReturn(Future.successful(new BearerAccessToken("token")))
    when(client.getResult(any[BearerAccessToken], any[Document], any[Option[Variables]])).thenThrow(new HttpException(response))

    val res: Either[String, Data] = ApiUpdate().send(keycloakUtils, client, document, variables).futureValue
    res.left.value shouldEqual "Unexpected response from GraphQL API: Response(Left(Graphql error),503,,List(),List())"
  }

  "The send method" should "error if the graphql query returns not authorised errors" in {
    val client = mock[GraphQLClient[Data, Variables]]
    val document = mock[Document]
    val keycloakUtils = mock[KeycloakUtils]

    val variables = Variables()

    when(keycloakUtils.serviceAccountToken(any[String], any[String]))
      .thenReturn(Future.successful(new BearerAccessToken("token")))
    val graphqlResponse: GraphQlResponse[Data] =
      GraphQlResponse(Option.empty, List(GraphQlError(GraphQLClient.Error("Not authorised message",
        List(), List(), Some(Extensions(Some("NOT_AUTHORISED")))))))
    when(client.getResult(any[BearerAccessToken], any[Document], any[Option[Variables]]))
      .thenReturn(Future.successful(graphqlResponse))

    val res = ApiUpdate().send(keycloakUtils, client, document, variables).futureValue

    res.left.value shouldEqual "Not authorised message"
  }

  "The send method" should "error if the graphql query returns a general error" in {
    val client = mock[GraphQLClient[Data, Variables]]
    val document = mock[Document]
    val keycloakUtils = mock[KeycloakUtils]

    val variables = Variables()

    when(keycloakUtils.serviceAccountToken(any[String], any[String]))
      .thenReturn(Future.successful(new BearerAccessToken("token")))
    val graphqlResponse: GraphQlResponse[Data] =
      GraphQlResponse(Option.empty, List(GraphQlError(GraphQLClient.Error("General error",
        List(), List(), Option.empty))))
    when(client.getResult(any[BearerAccessToken], any[Document], any[Option[Variables]]))
      .thenReturn(Future.successful(graphqlResponse))

    val res = ApiUpdate().send(keycloakUtils, client, document, variables).futureValue
    res.left.value shouldEqual "GraphQL response contained errors: General error"
  }
}
