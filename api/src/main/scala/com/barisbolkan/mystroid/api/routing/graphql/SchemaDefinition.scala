package com.barisbolkan.mystroid.api.routing.graphql

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import akka.stream.Materializer
import com.barisbolkan.mystroid.api.persitence._
import com.mongodb.reactivestreams.client.MongoDatabase
import sangria.ast
import sangria.macros.derive.{ObjectTypeName, deriveObjectType}
import sangria.schema.{Argument, Field, IntType, ListType, ObjectType, OptionInputType, ScalarType, Schema, fields}
import sangria.validation.ValueCoercionViolation

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

trait SchemaDefinition {

  lazy val repository: MystroidRepository = MystroidRepository

  case object DateCoercionViolation extends ValueCoercionViolation("Date value expected")
  case object MapCoercionViolation extends ValueCoercionViolation("Map value expected")

  implicit val DateTimeType =  {
    def parseDateTime(s: String) = Try(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(s).asInstanceOf[OffsetDateTime])

    ScalarType[OffsetDateTime]("DateTime",
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
  }
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
  implicit val MissDistanceType: ObjectType[Unit, MissDistance] = deriveObjectType[Unit, MissDistance](ObjectTypeName("MissDistance"))
  implicit val RelativeVelocityType: ObjectType[Unit, RelativeVelocity] = deriveObjectType[Unit, RelativeVelocity](ObjectTypeName("RelativeVelocity"))
  implicit val ApproachDataType: ObjectType[Unit, ApproachData] = deriveObjectType[Unit, ApproachData](ObjectTypeName("ApproachData"))
  implicit val DiameterType: ObjectType[Unit, Diameter] = deriveObjectType[Unit, Diameter](ObjectTypeName("Diameter"))
  implicit val AstroidInfoType: ObjectType[Unit, AstroidInfo] = deriveObjectType[Unit, AstroidInfo](ObjectTypeName("AstroidInfo"))

  val limitArg = Argument("limit", OptionInputType(IntType), defaultValue = 20)
  val offsetArg = Argument("offset", OptionInputType(IntType), defaultValue = 0)

  def schema()(implicit db: MongoDatabase, ec: ExecutionContext, materializer: Materializer) =
    Schema(query = ObjectType("Query",
      fields(List[Field[Unit, Unit]](
        Field(name = "astroids", fieldType = ListType(AstroidInfoType), arguments = (limitArg :: offsetArg :: Nil),
          resolve = ctx => repository.readAll[AstroidInfo](ctx arg limitArg)
        )
      ): _*))
    )
}
