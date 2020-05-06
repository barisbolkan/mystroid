package com.barisbolkan.mystroid.api.serialization

import java.time.{Instant, OffsetDateTime, ZoneId}

import com.barisbolkan.mystroid.api.persitence._
import io.circe.Decoder.Result
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, HCursor, Json}

trait JsonSupport {
  // OffsetDateTime info
  implicit lazy val offsetDateTimeDecoder = new Decoder[OffsetDateTime] {
    final def apply(cursor: HCursor): Result[OffsetDateTime] = {
      for {
        epoch <- cursor.as[Long]
      } yield OffsetDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.of("UTC"))
    }
  }
  implicit lazy val offsetDateTimeEncoder = new Encoder[OffsetDateTime] {
    final def apply(a: OffsetDateTime): Json = Json.fromString(a.toString)
  }

  // Miss Distance info
  implicit lazy val decodeMissDistance: Decoder[MissDistance] = deriveDecoder[MissDistance]
  implicit lazy val encodeMissDistance: Encoder[MissDistance] = deriveEncoder[MissDistance]

  // RelativeVelocity info
  implicit lazy val decodeRelativeVelocity: Decoder[RelativeVelocity] =
    Decoder.forProduct3("kilometers_per_second", "kilometers_per_hour", "miles_per_hour")(RelativeVelocity.apply)
  implicit lazy val encodeRelativeVelocity: Encoder[RelativeVelocity] = deriveEncoder[RelativeVelocity]

  // ApproachData info
  implicit lazy val approachDataDecoder =
    Decoder.forProduct4[ApproachData, OffsetDateTime, RelativeVelocity, MissDistance, String]("epoch_date_close_approach",
      "relative_velocity", "miss_distance", "orbiting_body")(ApproachData.apply)
  implicit lazy val encodeApproachData: Encoder[ApproachData] = deriveEncoder[ApproachData]

  // Diameter info
  implicit lazy val decodeDiameter: Decoder[Diameter] =
    Decoder.forProduct2("estimated_diameter_min", "estimated_diameter_max")(Diameter.apply)
  implicit lazy val encodeDiameter: Encoder[Diameter] = deriveEncoder[Diameter]

  // AstroidInfo
  implicit lazy val decodeAstroidInfo: Decoder[AstroidInfo] =
    Decoder.forProduct8("id", "neo_reference_id", "name", "nasa_jpl_url",
      "absolute_magnitude_h", "estimated_diameter", "is_potentially_hazardous_asteroid",
      "close_approach_data")(AstroidInfo.apply)
  implicit lazy val encodeAstroidInfo: Encoder[AstroidInfo] = deriveEncoder[AstroidInfo]
}