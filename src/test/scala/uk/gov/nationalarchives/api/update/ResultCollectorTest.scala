package uk.gov.nationalarchives.api.update

import org.mockito.MockitoSugar
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers._
import io.circe
import uk.gov.nationalarchives.api.update.utils.ExternalServicesTest

import scala.concurrent.Future

class ResultCollectorTest extends ExternalServicesTest with MockitoSugar with EitherValues with ScalaFutures {
  "The collect method" should "error if the api response has failed" in {
    val responseProcessor = ResultCollector()
    val caught =
      intercept[RuntimeException] {
        responseProcessor.collect(List(Right(Future.successful(Left("error"))))).futureValue
      }
    caught.getMessage should endWith("error.")
  }

  "The collect method" should "error if the decoding has failed" in {
    val responseProcessor = ResultCollector()
    val error = mock[circe.Error]
    when(error.getMessage).thenReturn("decoderError")
    val decoderError: Either[circe.Error, Future[Either[String, String]]] = Left(error)
    val caught =
      intercept[RuntimeException] {
        responseProcessor.collect(List(decoderError)).futureValue
      }
    caught.getMessage should endWith("decoderError.")
  }

  "The collect method" should "return successfully if all responses have succeeded" in {
    val responseProcessor = ResultCollector()
    val id = "successful-id"
    val processed = responseProcessor.collect(List(Right(Future.successful(Right(id)))))
    processed.futureValue.head should equal(id)
  }

}
