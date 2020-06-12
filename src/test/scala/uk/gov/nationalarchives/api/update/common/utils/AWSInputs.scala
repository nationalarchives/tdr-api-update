package uk.gov.nationalarchives.api.update.common.utils

import java.util.Base64

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import org.mockito.MockitoSugar

import scala.io.Source.fromResource
import scala.jdk.CollectionConverters._

object AWSInputs extends MockitoSugar {

  val context = mock[Context]
  def sqsEvent(jsonLocations: String*): SQSEvent = {
    val event = new SQSEvent()
    val records: Seq[SQSMessage] = jsonLocations.indices.map(i => {
      val jsonLocation = jsonLocations(i)
      val record = new SQSMessage()
      val body = fromResource(s"json/$jsonLocation.json").mkString
      record.setBody(body)
      val handle = Base64.getEncoder.encodeToString(body.getBytes("UTF-8"))
      record.setReceiptHandle(handle)
      record
    })
    event.setRecords(records.asJava)
    event
  }

}
