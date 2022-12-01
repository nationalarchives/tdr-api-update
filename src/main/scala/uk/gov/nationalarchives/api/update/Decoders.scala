package uk.gov.nationalarchives.api.update

import graphql.codegen.types.{AddAntivirusMetadataInputValues, AddFileMetadataWithFileIdInput, FFIDMetadataInputValues}
import io.circe.Decoder

import io.circe.generic.semiauto.deriveDecoder

object Decoders {
  val antivirusDecoder: Decoder[Serializable] = deriveDecoder[AddAntivirusMetadataInputValues].map[Serializable](identity)
  val metadataDecoder: Decoder[Serializable] = deriveDecoder[AddFileMetadataWithFileIdInput].map[Serializable](identity)
  val ffidMetadataDecoder: Decoder[Serializable] = deriveDecoder[FFIDMetadataInputValues].map[Serializable](identity)

  implicit val allDecoder: Decoder[Serializable] = antivirusDecoder.or(metadataDecoder).or(ffidMetadataDecoder)
}
