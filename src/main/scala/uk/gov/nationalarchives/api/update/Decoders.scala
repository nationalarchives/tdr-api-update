package uk.gov.nationalarchives.api.update

import graphql.codegen.types.{AddAntivirusMetadataInput, AddFileMetadataInput, FFIDMetadataInput}
import graphql.codegen.AddAntivirusMetadata.AddAntivirusMetadata
import graphql.codegen.AddFileMetadata.addFileMetadata
import graphql.codegen.AddFFIDMetadata.addFFIDMetadata
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

object Decoders {
  val antivirusDecoder: Decoder[Serializable] = deriveDecoder[AddAntivirusMetadataInput].map[Serializable](identity)
  val metadataDecoder: Decoder[Serializable] = deriveDecoder[AddFileMetadataInput].map[Serializable](identity)
  val ffidMetadataDecoder: Decoder[Serializable] = deriveDecoder[FFIDMetadataInput].map[Serializable](identity)
  implicit val antivirusDataDecoder: Decoder[AddAntivirusMetadata.Data] = deriveDecoder[AddAntivirusMetadata.Data]
  implicit val antivirusVariablesDecoder: Decoder[AddAntivirusMetadata.Variables] = deriveDecoder[AddAntivirusMetadata.Variables]
  implicit val fileMetadataDataDecoder: Decoder[addFileMetadata.Data] = deriveDecoder[addFileMetadata.Data]
  implicit val fileMetadataVariablesDecoder: Decoder[addFileMetadata.Variables] = deriveDecoder[addFileMetadata.Variables]
  implicit val ffidMetadataDataDecoder: Decoder[addFFIDMetadata.Data] = deriveDecoder[addFFIDMetadata.Data]
  implicit val ffidMetadataVariablesDecoder: Decoder[addFFIDMetadata.Variables] = deriveDecoder[addFFIDMetadata.Variables]

  implicit val allDecoder: Decoder[Serializable] = antivirusDecoder.or(metadataDecoder).or(ffidMetadataDecoder)
}
