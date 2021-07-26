package uk.gov.nationalarchives.api.update

import io.circe
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers._
import sttp.client.Response
import sttp.model.StatusCode
import uk.gov.nationalarchives.api.update.utils.ExternalServicesTest
import uk.gov.nationalarchives.aws.utils.SQSUtils
import uk.gov.nationalarchives.tdr.error.HttpException

import scala.concurrent.Future

class ResultCollectorTest extends ExternalServicesTest with MockitoSugar with EitherValues with ScalaFutures {

  val config: Map[String, String] = Map("sqs.url" -> queueUrl)

  "The collect method" should "error if the api response has failed and reset message visibility to allow retry" in {
    val sqsUtilsMock = mock[SQSUtils]
    val error = new RuntimeException("some API error")
    val updateException = FailedApiUpdateException("receiptHandle", error)

    val responseProcessor = ResultCollector(config, sqsUtilsMock)
    val caught = responseProcessor.collect(List(Right(Future.failed(updateException))))
      .failed.futureValue

    verify(sqsUtilsMock, times(1)).makeMessageVisible(queueUrl, "receiptHandle")
    caught.getMessage should include("some API error")
  }

  "The collect method" should "error if the decoding has failed and reset message visibility to allow retry" in {
    val sqsUtilsMock = mock[SQSUtils]
    val circeError = new Exception("decoderError")
    val responseProcessor = ResultCollector(config, sqsUtilsMock)
    val updateException = FailedApiUpdateException("receiptHandle", circeError)

    val decoderError: Either[FailedApiUpdateException, Future[String]] = Left(updateException)
    val caught = responseProcessor.collect(List(decoderError)).failed.futureValue
    verify(sqsUtilsMock, times(1)).makeMessageVisible(queueUrl, "receiptHandle")
    caught.getMessage should include("decoderError")
  }

  "The collect method" should "group errors from a mixture of results and reset messages visibility to allow retry" in {
    val sqsUtilsMock = mock[SQSUtils]

    val circeError1 = new Exception("first decoder error")
    val circeError2 = new Exception("second decoder error")
    val apiError1 = new RuntimeException("first API error")
    val apiError2 = new HttpException(Response(Left("second API error"), StatusCode.InternalServerError))

    val updateExCirceError1 = FailedApiUpdateException("receiptHandle1", circeError1)
    val updateExCirceError2 = FailedApiUpdateException("receiptHandle2", circeError2)
    val updateExApiError1 = FailedApiUpdateException("receiptHandle3", apiError1)
    val updateExApiError2 = FailedApiUpdateException("receiptHandle4", apiError2)

    val errors = List(
      Left(updateExCirceError1),
      Right(Future.failed(updateExApiError1)),
      Right(Future.successful("Successful result")),
      Left(updateExCirceError2),
      Right(Future.failed(updateExApiError2))
    )

    val responseProcessor = ResultCollector(config, sqsUtilsMock)
    val caught = responseProcessor.collect(errors).failed.futureValue

    verify(sqsUtilsMock, times(1)).makeMessageVisible(queueUrl, "receiptHandle1")
    verify(sqsUtilsMock, times(1)).makeMessageVisible(queueUrl, "receiptHandle2")
    verify(sqsUtilsMock, times(1)).makeMessageVisible(queueUrl, "receiptHandle3")
    verify(sqsUtilsMock, times(1)).makeMessageVisible(queueUrl, "receiptHandle4")

    caught.getMessage should include("first decoder error")
    caught.getMessage should include("second decoder error")
    caught.getMessage should include("first API error")
    caught.getMessage should include("second API error")
  }

  "The collect method" should "return successfully if all responses have succeeded" in {
    val sqsUtilsMock = mock[SQSUtils]
    val responseProcessor = ResultCollector(config, sqsUtilsMock)
    val id = "successful-id"
    val processed = responseProcessor.collect(List(Right(Future.successful(id))))

    verify(sqsUtilsMock, never).makeMessageVisible(any[String], any[String])

    processed.futureValue should equal (())
  }
}
