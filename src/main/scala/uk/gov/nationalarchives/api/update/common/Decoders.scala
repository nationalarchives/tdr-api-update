package uk.gov.nationalarchives.api.update.common

import graphql.codegen.types.{AddAntivirusMetadataInput, AddFileMetadataInput}
import graphql.codegen.AddAntivirusMetadata.AddAntivirusMetadata
import graphql.codegen.AddFileMetadata.addFileMetadata
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

object Decoders {
  val antivirusDecoder: Decoder[Serializable] = deriveDecoder[AddAntivirusMetadataInput].map[Serializable](identity)
  val metadataDecoder: Decoder[Serializable] = deriveDecoder[AddFileMetadataInput].map[Serializable](identity)
  implicit val antivirusDataDecoder: Decoder[AddAntivirusMetadata.Data] = deriveDecoder[AddAntivirusMetadata.Data]
  implicit val antivirusVariablesDecoder: Decoder[AddAntivirusMetadata.Variables] = deriveDecoder[AddAntivirusMetadata.Variables]
  implicit val fileMetadataDataDecoder: Decoder[addFileMetadata.Data] = deriveDecoder[addFileMetadata.Data]
  implicit val fileMetadataVariablesDecoder: Decoder[addFileMetadata.Variables] = deriveDecoder[addFileMetadata.Variables]

  implicit val allDecoder: Decoder[Serializable] = antivirusDecoder.or(metadataDecoder)
}
