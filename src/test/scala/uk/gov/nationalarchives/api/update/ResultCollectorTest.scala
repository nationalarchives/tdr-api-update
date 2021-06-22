package uk.gov.nationalarchives.api.update

import io.circe
import org.mockito.MockitoSugar
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers._
import sttp.client.Response
import sttp.model.StatusCode
import uk.gov.nationalarchives.api.update.utils.ExternalServicesTest
import uk.gov.nationalarchives.tdr.error.HttpException

import scala.concurrent.Future

class ResultCollectorTest extends ExternalServicesTest with MockitoSugar with EitherValues with ScalaFutures {
  "The collect method" should "error if the api response has failed" in {
    val responseProcessor = ResultCollector()
    val caught = responseProcessor.collect(List(Right(Future.failed(new RuntimeException("some API error")))))
      .failed.futureValue
    caught.getMessage should include("some API error")
  }

  "The collect method" should "error if the decoding has failed" in {
    val responseProcessor = ResultCollector()
    val error = mock[circe.Error]
    when(error.getMessage).thenReturn("decoderError")
    val decoderError: Either[circe.Error, Future[String]] = Left(error)
    val caught = responseProcessor.collect(List(decoderError)).failed.futureValue
    caught.getMessage should include("decoderError")
  }



  "The collect method" should "group errors from a mixture of results" in {
    val circeError1 = mock[circe.Error]
    when(circeError1.getMessage).thenReturn("first decoder error")
    val circeError2 = mock[circe.Error]
    when(circeError2.getMessage).thenReturn("second decoder error")
    val apiError1 = new RuntimeException("first API error")
    val apiError2 = new HttpException(Response(Left("second API error"), StatusCode.InternalServerError))
    val errors = List(
      Left(circeError1),
      Right(Future.failed(apiError1)),
      Right(Future.successful("Successful result")),
      Left(circeError2),
      Right(Future.failed(apiError2))
    )

    val responseProcessor = ResultCollector()
    val caught = responseProcessor.collect(errors).failed.futureValue

    caught.getMessage should include("first decoder error")
    caught.getMessage should include("second decoder error")
    caught.getMessage should include("first API error")
    caught.getMessage should include("second API error")
  }

  "The collect method" should "return successfully if all responses have succeeded" in {
    val responseProcessor = ResultCollector()
    val id = "successful-id"
    val processed = responseProcessor.collect(List(Right(Future.successful(id))))
    processed.futureValue should equal (())
  }
}
