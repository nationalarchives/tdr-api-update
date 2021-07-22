package uk.gov.nationalarchives.api.update.utils

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import org.mockito.MockitoSugar
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.{ReceiveMessageRequest, SendMessageRequest}

import scala.io.Source.fromResource
import scala.jdk.CollectionConverters._

object AWSInputs extends MockitoSugar {

  val context: Context = mock[Context]
  def createSqsEvent(queueUrl: String, client: SqsClient, jsonLocations: String*): SQSEvent = {
    val event = new SQSEvent()
    val records: Seq[SQSMessage] = jsonLocations.indices.map(i => {
      val jsonLocation = jsonLocations(i)
      val record = new SQSMessage()
      val body = fromResource(s"json/$jsonLocation.json").mkString
      record.setBody(body)
      val sendResponse = client.sendMessage(SendMessageRequest
        .builder.messageBody(body).queueUrl(queueUrl).build())
      record.setMessageId(sendResponse.messageId())
      record
    })

    val queueMessages = client.receiveMessage(ReceiveMessageRequest
      .builder
      .maxNumberOfMessages(10)
      .queueUrl(queueUrl)
      .build).messages.asScala.toList

    records.foreach(record => {
      val receiptHandle = queueMessages.filter(_.messageId() == record.getMessageId).head.receiptHandle()
      record.setReceiptHandle(receiptHandle)
    })

    event.setRecords(records.asJava)
    event
  }

}
