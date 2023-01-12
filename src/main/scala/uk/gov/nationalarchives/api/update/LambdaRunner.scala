package uk.gov.nationalarchives.api.update

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

object LambdaRunner extends App {
  val body =
    """
      |{
      |  "results": [
      |    {
      |      "fileId": "20d80488-d247-47cf-8687-be26de2558b5",
      |      "originalPath": "smallfile/subfolder/subfolder-nested/subfolder-nested-1.txt",
      |      "fileSize": "2",
      |      "clientChecksum": "87428fc522803d31065e7bce3cf03fe475096631e5e07bbd7a0fde60c4cf25c7",
      |      "consignmentType": "standard",
      |      "consignmentId": "cedba409-c806-439f-8982-943afb03c85a",
      |      "userId": "030cf12c-8d5d-46b9-b86a-38e0920d0e1a",
      |      "fileCheckResults": {
      |        "antivirus": [
      |          {
      |            "software": "yara",
      |            "softwareVersion": "4.2.0",
      |            "databaseVersion": "$LATEST",
      |            "result": "",
      |            "datetime": 1670411830303,
      |            "fileId": "20d80488-d247-47cf-8687-be26de2558b5"
      |          }
      |        ],
      |        "checksum": [
      |          {
      |            "fileId": "20d80488-d247-47cf-8687-be26de2558b5",
      |            "sha256Checksum": "87428fc522803d31065e7bce3cf03fe475096631e5e07bbd7a0fde60c4cf25c7"
      |          }
      |        ],
      |        "fileFormat": [
      |          {
      |            "fileId": "20d80488-d247-47cf-8687-be26de2558b5",
      |            "software": "Droid",
      |            "softwareVersion": "6.6.0-rc2",
      |            "binarySignatureFileVersion": "109",
      |            "containerSignatureFileVersion": "20221102",
      |            "method": "pronom",
      |            "matches": [
      |              {
      |                "extension": "txt",
      |                "identificationBasis": "Extension",
      |                "puid": "x-fmt/111"
      |              }
      |            ]
      |          }
      |        ]
      |      }
      |    }
      |  ],
      |  "redactedResults": {
      |    "redactedFiles": [],
      |    "errors": []
      |  },
      |  "statuses": {
      |    "statuses": [
      |      {
      |        "id": "12edbedc-48e1-4342-846a-fb772289790c",
      |        "statusType": "File",
      |        "statusName": "FFID",
      |        "statusValue": "Success",
      |        "overwrite": false
      |      }
      |    ]
      |  }
      |}
      |""".stripMargin

  val baos = new ByteArrayInputStream(body.getBytes())
  val output = new ByteArrayOutputStream()
  new Lambda().update(baos, output)
  val res = output.toByteArray.map(_.toChar).mkString
  println(res)
}
