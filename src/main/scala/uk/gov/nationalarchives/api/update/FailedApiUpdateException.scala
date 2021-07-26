package uk.gov.nationalarchives.api.update

case class FailedApiUpdateException(receiptHandle: String, cause: Throwable)
  extends RuntimeException(cause)
