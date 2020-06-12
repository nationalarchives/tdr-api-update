package uk.gov.nationalarchives.api.update.common

import sangria.ast.Document
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError
import uk.gov.nationalarchives.tdr.keycloak.KeycloakUtils
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import com.typesafe.config.ConfigFactory


class ApiUpdate()(implicit val executionContext: ExecutionContext) {

  def send[D, V](keycloakUtils: KeycloakUtils, client: GraphQLClient[D, V], document: Document, variables: V): Future[Either[String, D]] = {
    val configFactory = ConfigFactory.load
    val queryResult: Future[Either[String, GraphQlResponse[D]]] = (for {
      token <- keycloakUtils.serviceAccountToken(configFactory.getString("client.id"), configFactory.getString("client.secret"))
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
  def apply()(implicit executionContext: ExecutionContext): ApiUpdate = new ApiUpdate()(executionContext)
}
