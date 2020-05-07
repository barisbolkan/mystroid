package com.barisbolkan.mystroid.api.routing

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.barisbolkan.mystroid.api.BuildInfo
import com.barisbolkan.mystroid.api.persitence._
import com.mongodb.reactivestreams.client.MongoDatabase
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe._
import io.circe.parser._
import sangria.ast
import sangria.ast.Document
import sangria.execution.{ErrorWithResolver, Executor, QueryAnalysisError}
import sangria.macros.derive.{ObjectTypeName, deriveObjectType}
import sangria.marshalling.circe._
import sangria.parser.{QueryParser, SyntaxError}
import sangria.schema.{Argument, Field, IntType, ListType, ObjectType, OptionInputType, ScalarType, Schema, fields}
import sangria.validation.ValueCoercionViolation

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class MystroidRoutes()(implicit system: ActorSystem, materializer: Materializer, ec: ExecutionContext, db: MongoDatabase) {

  lazy val repository: MystroidRepository = MystroidRepository

  implicit val DateTimeType = ScalarType[OffsetDateTime]("DateTime",
    description = Some("DateTime is a scalar value that represents an ISO8601 formatted date and time."),
    coerceOutput = (date, _) => DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(date),
    coerceUserInput = {
      case s: String => parseDateTime(s) match {
        case Success(date) => Right(date)
        case Failure(_) => Left(DateCoercionViolation)
      }
      case _ ⇒ Left(DateCoercionViolation)
    },
    coerceInput = {
      case ast.StringValue(s, _, _, _, _) => parseDateTime(s) match {
        case Success(date) => Right(date)
        case Failure(_) => Left(DateCoercionViolation)
      }
      case _ => Left(DateCoercionViolation)
    })

  def parseDateTime(s: String) = Try(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(s).asInstanceOf[OffsetDateTime])

  case object DateCoercionViolation extends ValueCoercionViolation("Date value expected")
  case object MapCoercionViolation extends ValueCoercionViolation("Map value expected")

  implicit val EstimatedDiamaeterType = ScalarType[Map[String, Diameter]]("EstimatedDiameter",
    description = Some("EstimatedDiameter is a scalar value that represents map"),
    coerceOutput = (ed, _) =>
      ast.ObjectValue(ed.map(k => ast.ObjectField(k._1, ast.ObjectValue(("min", ast.FloatValue(k._2.min)), ("max", ast.FloatValue(k._2.max))))).toVector),
    coerceUserInput = {
      case s: Map[String, Diameter] => Right(s)
      case _ => Left(MapCoercionViolation)
    },
    coerceInput = {
      case ast.ObjectValue(fields, _, _) => {
        Right(Map.empty)
      }
      case _ => Left(MapCoercionViolation)
    }
  )

  private[this] implicit val MissDistanceType: ObjectType[Unit, MissDistance] = deriveObjectType[Unit, MissDistance](ObjectTypeName("MissDistance"))
  private[this] implicit val RelativeVelocityType: ObjectType[Unit, RelativeVelocity] = deriveObjectType[Unit, RelativeVelocity](ObjectTypeName("RelativeVelocity"))
  private[this] implicit val ApproachDataType: ObjectType[Unit, ApproachData] = deriveObjectType[Unit, ApproachData](ObjectTypeName("ApproachData"))
  private[this] implicit val DiameterType: ObjectType[Unit, Diameter] = deriveObjectType[Unit, Diameter](ObjectTypeName("Diameter"))
  private[this] implicit val AstroidInfoType: ObjectType[Unit, AstroidInfo] = deriveObjectType[Unit, AstroidInfo](ObjectTypeName("AstroidInfo"))

  private[this] val limitArg = Argument("limit", OptionInputType(IntType), defaultValue = 20)
  private[this] val offsetArg = Argument("offset", OptionInputType(IntType), defaultValue = 0)

  private[this] val queries: List[Field[Unit, Unit]] = List(
    Field(
      name = "astroids",
      fieldType = ListType(AstroidInfoType),
      arguments = (limitArg :: offsetArg :: Nil),
      resolve = ctx => repository.readAll[AstroidInfo](ctx arg limitArg)
    )
  )

  val schema = Schema(query = ObjectType("Query", fields(queries: _*)))

  lazy val routes: Route =
    pathEndOrSingleSlash {
      get {
        complete(s"Mystroid API is alive${BuildInfo.toJson}")
      }
    } ~
      path("schema.json") {
        post {
          complete(Executor.execute(schema, sangria.introspection.introspectionQuery))
        }
      } ~
      (post & path("graphql")){
        entity(as[Json]) { json =>

          val query = json.hcursor.get[String]("query").toOption.getOrElse("")
          val variables = json.hcursor.get[String]("variables").toOption
          val operationName = json.hcursor.get[String]("operationName").toOption

          QueryParser.parse(query) match {
            case Success(j) =>
              variables.map(parse) match {
                case Some(Left(error)) => complete(BadRequest, formatError(error))
                case Some(Right(json)) => executeGraphQL(j, operationName, json)
                case None => executeGraphQL(j, operationName, Json.obj())
              }
            case Failure(error) ⇒ complete(BadRequest, formatError(error))
          }
        }
      }

  def formatError(error: Throwable): Json = error match {
    case syntaxError: SyntaxError =>
      Json.obj("errors" -> Json.arr(
        Json.obj(
          "message" -> Json.fromString(syntaxError.getMessage),
          "locations" -> Json.arr(Json.obj(
            "line" -> Json.fromBigInt(syntaxError.originalError.position.line),
            "column" -> Json.fromBigInt(syntaxError.originalError.position.column))))))
    case NonFatal(e) =>
      formatError(e.getMessage)
    case e =>
      throw e
  }

  def formatError(message: String): Json =
    Json.obj("errors" -> Json.arr(Json.obj("message" -> Json.fromString(message))))

  def executeGraphQL(query: Document, operationName: Option[String], variables: Json) =
    complete(Executor.execute(schema, query,
      variables = if (variables.isNull) Json.obj() else variables,
      operationName = operationName,
      middleware = Nil)
      .map(OK → _)
      .recover {
        case error: QueryAnalysisError ⇒ BadRequest → error.resolveError
        case error: ErrorWithResolver ⇒ InternalServerError → error.resolveError
      })
}
