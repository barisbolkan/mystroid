package com.barisbolkan.mystroid.api.persitence

import java.time.{Instant, ZonedDateTime}

import akka.Done
import akka.stream.alpakka.mongodb.DocumentReplace
import akka.stream.alpakka.mongodb.scaladsl.MongoSink
import akka.stream.scaladsl.Sink
import com.barisbolkan.mystroid.api.persitence.MystroidRepository.{ApproachData, AstroidInfo, Diameter, ZonedDateTimeCodec}
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.reactivestreams.client.{MongoCollection, MongoDatabase}
import org.bson.codecs.configuration.CodecRegistries.{fromCodecs, fromProviders, fromRegistries}
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonReader, BsonWriter}
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros._

import scala.concurrent.Future
import scala.reflect.ClassTag

trait MongoRepository {
  protected def collectionName: String

  protected def col[T : ClassTag](db: MongoDatabase): MongoCollection[T] =
    codec(db).getCollection(collectionName, implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]])
  protected def codec(db: MongoDatabase): MongoDatabase

//  def read[T : Decoder]()(implicit db: MongoDatabase): Source[Option[T], NotUsed] =
//    MongoSource(col(db).find()).map(i => decode(i.toJson).toOption)

  def update[T : ClassTag]()(implicit db: MongoDatabase): Sink[DocumentReplace[T], Future[Done]] = {
    MongoSink.replaceOne(col[T](db), new ReplaceOptions().upsert(true))
  }
}

trait MystroidRepository extends MongoRepository {
  protected val collectionName: String = "astroid-info"

  override protected def codec(db: MongoDatabase): MongoDatabase = db.withCodecRegistry(
    fromRegistries(
      fromProviders(classOf[Diameter], classOf[ApproachData], classOf[AstroidInfo]),
      fromCodecs(new ZonedDateTimeCodec()),
      DEFAULT_CODEC_REGISTRY
    )
  )
}
object MystroidRepository extends MystroidRepository {

  case class Diameter(min: Double, max: Double)
  case class ApproachData(date: ZonedDateTime, velocity: Double, missDistance: Double)
  case class AstroidInfo(id: String, neoRefId: String, name: String, absoluteMagnitude: Double,
                         estimatedDiameter: Diameter, isHazardous: Boolean, closeApproachData: List[ApproachData])

  class AstroidInfoCodec(registry: CodecRegistry) extends Codec[AstroidInfo] {

    private[this] val diameterCodec = registry.get(classOf[Diameter])
    private[this] val approachDataCodec = registry.get(classOf[ApproachData])

    override def decode(reader: BsonReader, decoderContext: DecoderContext): AstroidInfo = {
      null
    }

    override def encode(writer: BsonWriter, value: AstroidInfo, encoderContext: EncoderContext): Unit = {
      writer.writeStartDocument()

      writer.writeString("_id", value.id)
      writer.writeString("neoRefId", value.neoRefId)
      writer.writeString("name", value.name)
      writer.writeDouble("absoluteMagnitude", value.absoluteMagnitude)

      writer.writeName("estimatedDiameter")
      diameterCodec.encode(writer, value.estimatedDiameter, encoderContext)

      writer.writeBoolean("isHazardous", value.isHazardous)

      writer.writeStartArray("closeApproachData")
      value.closeApproachData.foreach(cad => approachDataCodec.encode(writer, cad, encoderContext))
      writer.writeEndArray()

      writer.writeDateTime("modifiedAt", Instant.now().toEpochMilli)

      writer.writeEndDocument()
    }

    override def getEncoderClass: Class[AstroidInfo] = classOf[AstroidInfo]
  }

  // Codec for decoding and encoding [[ZonedDateTime]]
  class ZonedDateTimeCodec extends Codec[ZonedDateTime] {
    override def decode(reader: BsonReader, decoderContext: DecoderContext): ZonedDateTime =
      ZonedDateTime.parse(reader.readString)

    override def encode(writer: BsonWriter, value: ZonedDateTime, encoderContext: EncoderContext): Unit =
      writer.writeString(value.toString)

    override def getEncoderClass: Class[ZonedDateTime] = classOf[ZonedDateTime]
  }
}