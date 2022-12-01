package uk.gov.nationalarchives.api.update

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

object LambdaRunner extends App {
  val body =
    """[
      |  {
      |    "userId": "640fc1d5-9586-413f-b527-4bc4972d4dd1",
      |    "consignmentId": "115c14ae-0e80-4c8d-87bb-d5c218205424",
      |    "fileId": "c71901e0-d386-43da-bb7c-84a74097107c",
      |    "results": [
      |      {
      |        "fileFormat": {
      |          "fileId": "c71901e0-d386-43da-bb7c-84a74097107c",
      |          "software": "software",
      |          "softwareVersion": "softwareVersion",
      |          "binarySignatureFileVersion": "binarySignatureFileVersion",
      |          "containerSignatureFileVersion": "containerSignatureFileVersion",
      |          "method": "method",
      |          "matches": [
      |            {
      |              "extension": "txt",
      |              "identificationBasis": "container",
      |              "puid": "fmt/000"
      |            }
      |          ]
      |        }
      |      },
      |      {
      |        "checksum": {
      |          "sha256Checksum": "checksum",
      |          "fileId": "c71901e0-d386-43da-bb7c-84a74097107c"
      |        }
      |      },
      |      {
      |        "antivirus": {
      |          "fileId": "c71901e0-d386-43da-bb7c-84a74097107c",
      |          "software": "software",
      |          "softwareVersion": "softwareVersion",
      |          "databaseVersion": "databaseVersion",
      |          "result": "result",
      |          "datetime": 1
      |        }
      |      }
      |    ]
      |  },
      |  {
      |    "userId": "640fc1d5-9586-413f-b527-4bc4972d4dd1",
      |    "consignmentId": "115c14ae-0e80-4c8d-87bb-d5c218205424",
      |    "fileId": "c537fac7-58ee-4e9c-beaa-a0dc3068d5c2",
      |    "results": [
      |      {
      |        "fileFormat": {
      |          "fileId": "c537fac7-58ee-4e9c-beaa-a0dc3068d5c2",
      |          "software": "software",
      |          "softwareVersion": "softwareVersion",
      |          "binarySignatureFileVersion": "binarySignatureFileVersion",
      |          "containerSignatureFileVersion": "containerSignatureFileVersion",
      |          "method": "method",
      |          "matches": [
      |            {
      |              "extension": "txt",
      |              "identificationBasis": "container",
      |              "puid": "fmt/000"
      |            }
      |          ]
      |        }
      |      },
      |      {
      |        "checksum": {
      |          "sha256Checksum": "checksum",
      |          "fileId": "c537fac7-58ee-4e9c-beaa-a0dc3068d5c2"
      |        }
      |      },
      |      {
      |        "antivirus": {
      |          "fileId": "c537fac7-58ee-4e9c-beaa-a0dc3068d5c2",
      |          "software": "software",
      |          "softwareVersion": "softwareVersion",
      |          "databaseVersion": "databaseVersion",
      |          "result": "result",
      |          "datetime": 1
      |        }
      |      }
      |    ]
      |  }
      |]
      |""".stripMargin

  val baos = new ByteArrayInputStream(body.getBytes())
  val output = new ByteArrayOutputStream()
  new Lambda().update(baos, output)

}
