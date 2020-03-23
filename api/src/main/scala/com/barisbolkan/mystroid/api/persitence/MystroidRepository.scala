package com.barisbolkan.mystroid.api.persitence

import java.time.ZonedDateTime

import akka.NotUsed
import akka.stream.alpakka.mongodb.DocumentUpdate
import akka.stream.alpakka.mongodb.scaladsl.{MongoFlow, MongoSource}
import akka.stream.scaladsl.{Flow, Source}
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.result.UpdateResult
import com.mongodb.reactivestreams.client.{MongoCollection, MongoDatabase}
import io.circe.Decoder
import org.bson.Document
import io.circe.parser.decode

case class Diameter(min: Double, max: Double)
case class ApproachData(date: ZonedDateTime, velocity: Double, missDistance: Double)
case class AstroidInfo(id: String, neoRefId: String, name: String, absoluteMagnitude: Double,
                       estimatedDiameter: Diameter, isHazardous: Boolean, closeApproachData: List[ApproachData])

trait MongoRepository {
  protected def collectionName: String

  protected def col(db: MongoDatabase): MongoCollection[Document] = db.getCollection(collectionName, classOf[Document])

  def read[T : Decoder]()(implicit db: MongoDatabase): Source[Option[T], NotUsed] =
    MongoSource(col(db).find()).map(i => decode(i.toJson).toOption)

  def update()(implicit db: MongoDatabase): Flow[DocumentUpdate, (UpdateResult, DocumentUpdate), NotUsed] =
    MongoFlow.updateOne(col(db), new UpdateOptions().upsert(true))
//  def hearthBeat = Source.fromPublisher(connection.listDatabaseNames()).runWith(Sink.head)
}

trait MystroidRepository extends MongoRepository {
  protected val collectionName: String = "astroid-info"
}
object MystroidRepository extends MystroidRepository