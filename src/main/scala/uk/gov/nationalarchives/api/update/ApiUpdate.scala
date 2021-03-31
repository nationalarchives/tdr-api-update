package uk.gov.nationalarchives.api.update

import sangria.ast.Document
import sttp.client.{Identity, NothingT, SttpBackend}
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError
import uk.gov.nationalarchives.tdr.keycloak.KeycloakUtils
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class ApiUpdate(config: Map[String, String])(implicit val executionContext: ExecutionContext, backend: SttpBackend[Identity, Nothing, NothingT]) {

  def send[D, V](keycloakUtils: KeycloakUtils, client: GraphQLClient[D, V], document: Document, variables: V): Future[Either[String, D]] = {
    val queryResult: Future[Either[String, GraphQlResponse[D]]] = (for {
      token <- keycloakUtils.serviceAccountToken(config("client.id"), config("client.secret"))
      result <- client.getResult(token, document, Option(variables))
    } yield Right(result)) recover(e => {
      Left(e.getMessage)
    })

    queryResult.map {
      case Right(response) => response.errors match {
        case Nil => Right(response.data.get)
        case List(authError: NotAuthorisedError) => Left(authError.message)
        case errors => Left(s"GraphQL response contained errors: ${errors.map(e => e.message).mkString}")
      }
      case Left(e) => Left(e)
    }
  }
}

object ApiUpdate {
  def apply(config: Map[String, String])(implicit executionContext: ExecutionContext, backend: SttpBackend[Identity, Nothing, NothingT]): ApiUpdate = new ApiUpdate(config)(executionContext, backend)
}
