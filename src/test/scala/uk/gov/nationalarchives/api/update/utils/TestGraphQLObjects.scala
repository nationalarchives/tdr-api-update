package uk.gov.nationalarchives.api.update.utils

object TestGraphQLObjects {

  case class Variables()
  case class Data(response : TestResponse)
  case class TestResponse()
}
