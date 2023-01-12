package uk.gov.nationalarchives.api.update

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import com.typesafe.config.ConfigFactory
import io.circe.{Decoder, Encoder}
import sangria.ast.Document
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.nationalarchives.tdr.GraphQLClient

import scala.concurrent.Future

class RequestSender[D, V](client: GraphQLClient[D, V])(implicit val decoder: Decoder[D], val encoder: Encoder[V]) {
  implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

  def sendRequest(token: BearerAccessToken, document: Document, variables: V): Future[D] = {
    client.getResult(token, document, Option(variables)).flatMap(res => {
      res.data.map(data => Future.successful(data))
        .getOrElse(Future.failed(new RuntimeException(res.errors.map(_.message).mkString("\n"))))
    })
  }
}
object RequestSender {
  private val apiUrl: String = ConfigFactory.load.getString("url.api")
  def apply[D, V]()(implicit decoder: Decoder[D], encoder: Encoder[V]): RequestSender[D, V] = {
    val client = new GraphQLClient[D, V](apiUrl)
    new RequestSender[D, V](client)
  }
}
