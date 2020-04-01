package com.barisbolkan.mystroid.core.processing

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.alpakka.googlecloud.pubsub.grpc.scaladsl.GooglePubSub
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}
import akka.stream.{ClosedShape, SharedKillSwitch}
import com.barisbolkan.mystroid.core.configuration.AppSettings
import com.google.protobuf.ByteString
import com.google.pubsub.v1.pubsub.{PublishRequest, PublishResponse, PubsubMessage}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, Json, Printer}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Enables streaming from the nasa endpoint and publishes it to pubsub
  */
trait StreamingService extends FailFastCirceSupport {

  /**
    * Represents an object for paging for the Nasa Endpoint
    * @param next Next page url for the data, if there is none available it returns [[None]]
    * @param previous Previous page for the url
    * @param self Current requested page
    */
  final case class Links(next: Option[String], previous: Option[String], self: String)
  final case class HttpResponseException(code: StatusCode, msg: Option[String]) extends Exception

  /**
    * Deserializer for [[Links]] instance
    */
  private implicit val nextDecoder: Decoder[Links] = deriveDecoder[Links]

  /**
    * Converts the given [[Json]] instance into [[ByteString]]
    * @param json [[Json]] instance to convert to
    * @return [[ByteString]] that represents the value of the given [[Json]]
    */
  implicit def jsonToByteString(json: Json): ByteString = ByteString.copyFromUtf8(json.printWith(Printer.noSpaces))

  /**
    * Publishes the data to [[GooglePubSub]]
    * @param paralellism Paralellism factor
    * @return [[Flow]] for pubsub
    */
  def publisher()(implicit paralellism: Int): Flow[PublishRequest, PublishResponse, NotUsed] = GooglePubSub.publish(paralellism)

  def httpSource(initialPage: Option[String])(implicit system: ActorSystem, ec: ExecutionContext): Source[Json, NotUsed] =
    Source.unfoldAsync[Option[String], Json](initialPage) {
      case Some(page) =>
        Http()
          .singleRequest(request = HttpRequest(uri = Uri(page), method = HttpMethods.GET))
          .flatMap {
            case resp if resp.status != StatusCodes.OK =>
              throw HttpResponseException(resp.status, Some(s"The response retrieved from Url[$page] has errors."))
            case resp =>
              Unmarshal(resp.entity)
                .to[Json]
                .map(_.hcursor)
                .map { cursor =>
                  cursor.get("links")
                    .toOption
                    .map(_.next)
                    .map((_, cursor.downField("near_earth_objects").as[Json].getOrElse(Json.Null)))
                }
          }
          .recover {
            case ex => throw ex
          }
      case None => Future.successful(None)
    }

  def httpFlow()(implicit system: ActorSystem, ec: ExecutionContext): Flow[Option[String], Json, NotUsed] =
    Flow[Option[String]]
      .map(httpSource)
    .flatMapConcat(identity)

  /**
    * A [[Flow]] to convert the given topic into a pubsub [[PublishRequest]]
    * @param topic Name of the topic to publish the message to
    * @return [[Flow]]
    */
  def convert(topic: String): Flow[Json, PublishRequest, NotUsed] =
    Flow[Json].map { j =>
      PublishRequest()
        .withTopic(topic)
        .addMessages(PubsubMessage().withData(j))
    }

  /**
    * Consumes the Endpoint in a reactive manner and directs the messages to [[GooglePubSub]]
    * @param switch [[SharedKillSwitch]] instance to cancel the streams in safe manner
    * @param system An [[ActorSystem]] to maintain actors
    * @param ec An [[ExecutionContext]] to execute the [[Future]]s in parallel
    * @param settings An [[AppSettings]] instance for configuration
    * @return A  reusable [[RunnableGraph]] instance to stream
    */
  def consume(switch: SharedKillSwitch)(implicit system: ActorSystem, ec: ExecutionContext, settings: AppSettings) =
    RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // Create the ticks in a repeating manner to start streaming repeatedly
      val tick = Source.tick(0.second, settings.nasa.schedulePeriod, Some(settings.nasa.url))

      // Shape of the flows
      val toHttp = builder.add(httpFlow())
      val toConvert = builder.add(convert(settings.pubsub.topic))
      val toPublish = builder.add(publisher()(1))

      // Processing
      tick ~> toHttp ~> toConvert ~> toPublish ~> Sink.ignore

      ClosedShape
    })
}

object StreamingService extends StreamingService