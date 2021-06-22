package uk.gov.nationalarchives.api.update

import sangria.ast.Document
import sttp.client.{Identity, NothingT, SttpBackend}
import uk.gov.nationalarchives.tdr.keycloak.KeycloakUtils
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class ApiUpdate(config: Map[String, String])(implicit val executionContext: ExecutionContext, backend: SttpBackend[Identity, Nothing, NothingT]) {

  def send[D, V](keycloakUtils: KeycloakUtils, client: GraphQLClient[D, V], document: Document, variables: V): Future[D] = {
    val queryResult: Future[GraphQlResponse[D]] = for {
      token <- keycloakUtils.serviceAccountToken(config("client.id"), config("client.secret"))
      result <- client.getResult(token, document, Option(variables))
    } yield result

    queryResult.map(result => {
      result.errors match {
        case Nil => result.data.get
        case errors => throw new RuntimeException(
          s"GraphQL response contained errors: ${errors.map(e => e.message).mkString}")
      }
    })
  }
}

object ApiUpdate {
  def apply(config: Map[String, String])(implicit executionContext: ExecutionContext, backend: SttpBackend[Identity, Nothing, NothingT]): ApiUpdate = new ApiUpdate(config)(executionContext, backend)
}
