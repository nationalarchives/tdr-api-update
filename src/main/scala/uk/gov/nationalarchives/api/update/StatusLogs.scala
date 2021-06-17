package uk.gov.nationalarchives.api.update

import java.util.UUID

import com.typesafe.scalalogging.Logger
import net.logstash.logback.argument.StructuredArguments.value

class StatusLogs(logger: Logger) {
  def log(fileId: UUID, fileCheckType: String, status: String): Unit = {
    logger.info(
      "Saving {} metadata for file ID '{}'. Status: {}",
      value("metadataType", fileCheckType),
      value("fileId", fileId),
      value("apiUpdateStatus", status)
    )
  }
}
