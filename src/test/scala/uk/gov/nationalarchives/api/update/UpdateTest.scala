package uk.gov.nationalarchives.api.update

import com.github.tomakehurst.wiremock.client.WireMock.{equalTo, equalToJson, postRequestedFor, urlEqualTo}
import org.scalatest.matchers.should.Matchers._
import org.scalatest.prop.TableDrivenPropertyChecks
import software.amazon.awssdk.services.sqs.model.{ReceiveMessageRequest, SendMessageRequest}
import uk.gov.nationalarchives.api.update.utils.AWSInputs._
import uk.gov.nationalarchives.api.update.utils.ExternalServicesTest

import scala.io.Source.fromResource
import scala.util.Try

class UpdateTest extends ExternalServicesTest with TableDrivenPropertyChecks {

  def verifyWiremockResponse(fileName: String): Unit = {
    wiremockGraphqlServer.verify(postRequestedFor(urlEqualTo(graphQlPath))
      .withRequestBody(equalToJson(fromResource(s"json/$fileName.json").mkString))
      .withHeader("Authorization", equalTo("Bearer token")))
  }

  val updateTypes =
    Table(
      "updateType",
      "av",
      "checksum"
    )

  forAll(updateTypes) { updateType => {
    "The update method" should s"call the graphql api with a single record with a single $updateType update" in {
      authOkJson("access_token")
      graphqlOkJson(s"graphql_valid_${updateType}_response")
      val main = new Lambda()
      main.update(sqsEvent(s"function_valid_${updateType}_input"), context)
      verifyWiremockResponse(s"graphql_valid_${updateType}_expected")
    }

    "The update method" should s"call the graphql api with multiple records with a single $updateType update" in {
      authOkJson("access_token")
      graphqlOkJson(s"graphql_valid_${updateType}_response")
      new Lambda().update(sqsEvent(s"function_valid_${updateType}_input", s"function_valid_${updateType}_input"), context)
      verifyWiremockResponse(s"graphql_valid_${updateType}_multiple_records_expected_1")
      verifyWiremockResponse(s"graphql_valid_${updateType}_multiple_records_expected_2")
    }

    "The update method" should s"delete a successful $updateType message from the queue" in {
      val event = sqsEvent(s"function_valid_${updateType}_input")
      client.sendMessage(request(event.getRecords.get(0).getBody))
      authOkJson("access_token")
      graphqlOkJson(s"graphql_valid_${updateType}_response")
      val main = new Lambda()
      main.update(sqsEvent(s"function_valid_${updateType}_input"), context)
      val messages = client.receiveMessage(ReceiveMessageRequest.builder.queueUrl(queueUrl).build)
      messages.hasMessages should be(false)
    }

    "The update method" should s"leave a failed $updateType message in the queue" in {
      val event = sqsEvent(s"function_invalid_${updateType}_input")
      client.sendMessage(request(event.getRecords.get(0).getBody))
      val main = new Lambda()
      Try(main.update(event, context))
      val messages = client.receiveMessage(ReceiveMessageRequest.builder.queueUrl(queueUrl).build)
      messages.hasMessages should be(true)
    }

    "The update method" should s"delete a successful $updateType message and leave a failed message in the queue" in {
      val event = sqsEvent(s"function_valid_${updateType}_input", s"function_invalid_${updateType}_input")
      client.sendMessage(request(event.getRecords.get(0).getBody))
      client.sendMessage(request(event.getRecords.get(1).getBody))

      authOkJson("access_token")
      graphqlOkJson(s"graphql_valid_${updateType}_response")
      val main = new Lambda()
      Try(main.update(event, context))
      val messages = client.receiveMessage(ReceiveMessageRequest.builder.queueUrl(queueUrl).build)
      messages.hasMessages should be(true)
      messages.messages.size should be(1)
    }
  }
  }

  private def request(body: String) = SendMessageRequest.builder()
    .messageBody(body)
    .queueUrl(queueUrl)
    .build()
}
