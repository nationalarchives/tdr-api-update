package uk.gov.nationalarchives.api.update.common

import org.mockito.ArgumentMatchers._
import org.mockito.{ArgumentCaptor, Mockito, MockitoSugar}
import org.scalatest.matchers.should.Matchers._
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.{DeleteMessageRequest, DeleteMessageResponse}
import uk.gov.nationalarchives.api.update.common.utils.ExternalServicesTest

class SQSUpdateTest extends ExternalServicesTest with MockitoSugar  {
  "The deleteMessage method" should "delete a message" in {
    val sqsClient = Mockito.mock(classOf[SqsClient])
    val sqsUpdate = SQSUpdate(sqsClient)
    val queueUrl = "testqueue"
    val receiptHandle = "testreceipthandle"
    val captor: ArgumentCaptor[DeleteMessageRequest] = ArgumentCaptor.forClass(classOf[DeleteMessageRequest])
    when(sqsClient.deleteMessage(captor.capture())).thenReturn(DeleteMessageResponse.builder().build())

    sqsUpdate.deleteSqsMessage(queueUrl, receiptHandle)
    verify(sqsClient).deleteMessage(any[DeleteMessageRequest])
    captor.getValue.receiptHandle should equal(receiptHandle)
    captor.getValue.queueUrl() should equal(queueUrl)
  }
}
