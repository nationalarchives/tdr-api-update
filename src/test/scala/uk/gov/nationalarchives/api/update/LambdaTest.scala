package uk.gov.nationalarchives.api.update

import com.github.tomakehurst.wiremock.client.WireMock.{equalToJson, postRequestedFor, urlEqualTo}
import uk.gov.nationalarchives.api.update.utils.ExternalServicesTest

class LambdaTest extends ExternalServicesTest {
  "Creating the lambda class" should "call systems manager" in {
    new Lambda()
    wiremockSsmServer
      .verify(postRequestedFor(urlEqualTo("/"))
        .withRequestBody(equalToJson("""{"Name" : "/a/secret/path","WithDecryption" : true}""")))
  }
}
