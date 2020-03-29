package com.barisbolkan.mystroid.api.serialization

import java.time.{Instant, ZoneId, ZonedDateTime}

import com.barisbolkan.mystroid.api.persitence.MystroidRepository.{ApproachData, AstroidInfo, Diameter}
import io.circe.Decoder.Result
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, HCursor, Json}

trait JsonSupport {

  implicit lazy val diameterDecoder = new Decoder[Diameter] {
    final def apply(cursor: HCursor): Result[Diameter] = {
      val kilometers = cursor.downField("kilometers")
      for {
        min <- kilometers.downField("estimated_diameter_min").as[Double]
        max <- kilometers.downField("estimated_diameter_max").as[Double]
      } yield Diameter(min, max)
    }
  }

  implicit lazy val zonedDateTimeDecoder = new Decoder[ZonedDateTime] {
    final def apply(cursor: HCursor): Result[ZonedDateTime] = {
      for {
        epoch <- cursor.as[Long]
      } yield ZonedDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.of("UTC"))
    }
  }

  implicit lazy val approachDataDecoder = new Decoder[ApproachData] {
    final def apply(cursor: HCursor): Result[ApproachData] = {
      for {
        date <- cursor.downField("epoch_date_close_approach").as[ZonedDateTime]
        velocity <- cursor.downField("relative_velocity").downField("kilometers_per_hour").as[Double]
        missDist <- cursor.downField("miss_distance").downField("kilometers").as[Double]
      } yield ApproachData(date, velocity, missDist)
    }
  }

  implicit lazy val astroidInfoDecoder = new Decoder[AstroidInfo] {
    final def apply(cursor: HCursor): Result[AstroidInfo] = {
      for {
        id <- cursor.downField("id").as[String]
        neoId <- cursor.downField("neo_reference_id").as[String]
        name <- cursor.downField("name").as[String]
        mag <- cursor.downField("absolute_magnitude_h").as[Double]
        dia <- cursor.downField("estimated_diameter").as[Diameter]
        hazard <- cursor.downField("is_potentially_hazardous_asteroid").as[Boolean]
        approach <- cursor.downField("close_approach_data").as[List[ApproachData]]
      } yield AstroidInfo(id, neoId, name, mag, dia, hazard, approach)
    }
  }

  implicit lazy val zonedDateTimeEncoder = new Encoder[ZonedDateTime] {
    final def apply(a: ZonedDateTime): Json = Json.fromLong(a.toInstant.toEpochMilli)
  }
  implicit lazy val diameterEncoder = deriveEncoder[Diameter]
  implicit lazy val approachDataEncoder = deriveEncoder[ApproachData]
  implicit lazy val astroidInfoEncoder = deriveEncoder[AstroidInfo]
}