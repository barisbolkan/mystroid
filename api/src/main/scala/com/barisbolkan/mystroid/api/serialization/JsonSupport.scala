package com.barisbolkan.mystroid.api.serialization

import com.barisbolkan.mystroid.api.persitence.{ApproachData, AstroidInfo, Diameter}
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}

trait JsonSupport {

  // Diameter encoder and decoder
  implicit val encoderDiameter: Encoder[Diameter] = deriveEncoder
  implicit val decoderDiameter: Decoder[Diameter] = deriveDecoder

  // ApproachDate encoder and decoder
  implicit val encoderApproachDate: Encoder[ApproachData] = deriveEncoder
  implicit val decoderapproachDate: Decoder[ApproachData] = deriveDecoder

  // Astroid encoder and decoder
  implicit val encoderAstroid: Encoder[AstroidInfo] = deriveEncoder
  implicit val decoderAstroid: Decoder[AstroidInfo] = deriveDecoder
}