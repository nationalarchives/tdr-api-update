package uk.gov.nationalarchives.api.update.common

import io.circe

import scala.concurrent.{ExecutionContext, Future}

class ResultCollector()(implicit val executionContext: ExecutionContext) {

  def collect(results: List[Either[circe.Error, Future[Either[String, String]]]]): Future[Seq[String]] = {
    val (decodingFailures: Seq[circe.Error], apiResult: Seq[Future[Either[String, String]]]) = results.partitionMap(identity)
    Future.sequence(apiResult).map(r => {
      val failures: Seq[String] = r.collect { case Left(msg) => msg } ++ decodingFailures.map(_.getMessage)
      val successes: Seq[String] = r.collect { case Right(msg) => msg }
      if (failures.nonEmpty) {
        throw new RuntimeException(failures.mkString(", "))
      } else {
        successes
      }
    })
  }
}

object ResultCollector {
  def apply()(implicit executionContext: ExecutionContext): ResultCollector = new ResultCollector()
}