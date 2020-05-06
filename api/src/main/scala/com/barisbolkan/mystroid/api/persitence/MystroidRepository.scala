package com.barisbolkan.mystroid.api.persitence

import java.time.OffsetDateTime

import akka.Done
import akka.stream.Materializer
import akka.stream.alpakka.mongodb.DocumentReplace
import akka.stream.alpakka.mongodb.scaladsl.{MongoSink, MongoSource}
import akka.stream.scaladsl.Sink
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.reactivestreams.client.{MongoCollection, MongoDatabase}
import org.bson.codecs.configuration.CodecRegistries.{fromCodecs, fromProviders, fromRegistries}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonReader, BsonWriter}
import org.mongodb.scala.bson.annotations.BsonProperty
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros._

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

trait MongoRepository {
  protected def collectionName: String

  protected def col[T : ClassTag](db: MongoDatabase): MongoCollection[T] =
    codec(db).getCollection(collectionName, implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]])
  protected def codec(db: MongoDatabase): MongoDatabase

  def update[T : ClassTag]()(implicit db: MongoDatabase): Sink[DocumentReplace[T], Future[Done]] = {
    MongoSink.replaceOne(col[T](db), new ReplaceOptions().upsert(true))
  }

  def readAll[T: ClassTag](take: Int)(implicit db: MongoDatabase, ec: ExecutionContext, materializer: Materializer): Future[Seq[T]] = {
    MongoSource(col[T](db).find().limit(take)).runWith(Sink.seq[T])
  }
}

case class MissDistance(astronomical: Double, lunar: Double, kilometers: Double, miles: Double)
case class RelativeVelocity(kilometersPerSecond: Double, kilometersPerHour: Double, milesPerHour: Double)
case class ApproachData(date: OffsetDateTime, relativeVelocity: RelativeVelocity, missDistance: MissDistance, orbitingBody: String)
case class Diameter(min: Double, max: Double)
case class AstroidInfo(@BsonProperty("_id") id: Int, neoRefId: Int, name: String, jplUrl: Option[String], absoluteMagnitude: Double,
                       estimatedDiameter: Map[String, Diameter], isHazardous: Boolean, closeApproachData: List[ApproachData])

trait MystroidRepository extends MongoRepository {
  protected val collectionName: String = "astroid-info"

  // Codec for decoding and encoding [[OffsetDateTime]]
  class OffsetDateTimeCodec extends Codec[OffsetDateTime] {
    override def decode(reader: BsonReader, decoderContext: DecoderContext): OffsetDateTime =
      OffsetDateTime.parse(reader.readString)

    override def encode(writer: BsonWriter, value: OffsetDateTime, encoderContext: EncoderContext): Unit =
      writer.writeString(value.toString)

    override def getEncoderClass: Class[OffsetDateTime] = classOf[OffsetDateTime]
  }

  override protected def codec(db: MongoDatabase): MongoDatabase = db.withCodecRegistry(
    fromRegistries(
      fromProviders(classOf[MissDistance], classOf[RelativeVelocity], classOf[Diameter],
        classOf[ApproachData], classOf[AstroidInfo]), fromCodecs(new OffsetDateTimeCodec()),
      DEFAULT_CODEC_REGISTRY
    )
  )
}
object MystroidRepository extends MystroidRepository