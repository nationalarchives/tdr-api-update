package uk.gov.nationalarchives.api.update

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

object LambdaRunner extends App {
  val body =
"""{
  |  "key": "a4c0e084-7562-46dd-9724-92ac9b64d149/7f068a2a-e2c8-472f-bb05-daed89f8de3c/results.json",
  |  "bucket": "tdr-backend-checks-intg"
  |}
      |""".stripMargin

  val baos = new ByteArrayInputStream(body.getBytes())
  val output = new ByteArrayOutputStream()
  new Lambda().update(baos, output)
  val res = output.toByteArray.map(_.toChar).mkString
  println(res)
}
