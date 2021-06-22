package uk.gov.nationalarchives.api.update

import graphql.codegen.types.{AddAntivirusMetadataInput, AddFileMetadataInput, FFIDMetadataInput}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

object Decoders {
  val antivirusDecoder: Decoder[Serializable] = deriveDecoder[AddAntivirusMetadataInput].map[Serializable](identity)
  val metadataDecoder: Decoder[Serializable] = deriveDecoder[AddFileMetadataInput].map[Serializable](identity)
  val ffidMetadataDecoder: Decoder[Serializable] = deriveDecoder[FFIDMetadataInput].map[Serializable](identity)

  implicit val allDecoder: Decoder[Serializable] = antivirusDecoder.or(metadataDecoder).or(ffidMetadataDecoder)
}
