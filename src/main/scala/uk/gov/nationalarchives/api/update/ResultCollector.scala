package uk.gov.nationalarchives.api.update

import com.typesafe.scalalogging.Logger
import io.circe

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class ResultCollector()(implicit val executionContext: ExecutionContext) {

  val logger: Logger = Logger[ResultCollector]

  // TODO: Return unit
  def collect(results: List[Either[circe.Error, Future[String]]]): Future[Seq[String]] = {
    val (decodingFailures: Seq[circe.Error], apiResult: Seq[Future[String]]) = results.partitionMap(identity)

    // Map all failed futures to a Try so that we can collect all the failures
    val handledFutures: Seq[Future[Try[String]]] = apiResult.map(futureResult => {
      futureResult.map(result => Success(result))
        .recover(e => Failure(e))
    })

    Future.sequence(handledFutures).map(results => {
      val (apiFailures: Seq[Throwable], successes: Seq[String]) = results.partitionMap(_.toEither)
      val allFailures = apiFailures ++ decodingFailures

      if (allFailures.nonEmpty) {
        throw new RuntimeException(s"${allFailures.length} messages out of ${results.length} failed: " +
          allFailures.map(_.getMessage).mkString(", "))
      } else {
        successes
      }
    })
  }
}

object ResultCollector {
  def apply()(implicit executionContext: ExecutionContext): ResultCollector = new ResultCollector()
}
