package uk.gov.nationalarchives.api.update

import com.typesafe.scalalogging.Logger
import net.logstash.logback.argument.StructuredArguments.value
import uk.gov.nationalarchives.aws.utils.sqs.SQSUtils

import java.time.Instant
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class ResultCollector(val config: Map[String, String], sqsUtils: SQSUtils)
                     (implicit val executionContext: ExecutionContext, startTime: Instant) {

  val logger: Logger = Logger[ResultCollector]

  def collect(results: List[Either[FailedApiUpdateException, Future[String]]]): Future[Unit] = {
    val (decodingFailures: Seq[FailedApiUpdateException], apiResult: Seq[Future[String]]) = results.partitionMap(identity)

    // Map all failed futures to a Try so that we can collect all the failures
    val handledFutures: Seq[Future[Try[String]]] = apiResult.map(futureResult => {
      futureResult.map(result => Success(result))
        .recover(e => Failure(e))
    })

    Future.sequence(handledFutures).map(results => {
      val (apiFailures: Seq[Throwable], successes: Seq[String]) = results.partitionMap(_.toEither)
      val allFailures = apiFailures ++ decodingFailures

      if (allFailures.nonEmpty) {
        logger.error(
          "There were {} failures out of {} messages",
          value("messageCount", allFailures.size),
          value("messageCount", results.size)
        )

        allFailures.foreach {
          case FailedApiUpdateException(receiptHandle, _) => sqsUtils.makeMessageVisible(config("sqs.url"), receiptHandle)
          case _ => // Allow message to expire and be retried at its original expiry time
        }

        throw new RuntimeException(s"${allFailures.length} messages out of ${results.length} failed: " +
          allFailures.map(_.getMessage).mkString(", "))
      } else {
        val timeTaken = java.time.Duration.between(startTime, Instant.now).toMillis.toDouble / 1000
        successes.map(success => {
          logger.info(
            "Successfully processed {} messages in {} seconds for fileId {}",
            value("messageCount", results.size),
            value("timeTaken", timeTaken),
            value("fileId", success),
            value("consignmentId", success)
          )
        })
      }
    })
  }
}

object ResultCollector {
  def apply(config: Map[String, String], sqsUtils: SQSUtils)
           (implicit executionContext: ExecutionContext, startTime: Instant): ResultCollector = new ResultCollector(config, sqsUtils)
}
