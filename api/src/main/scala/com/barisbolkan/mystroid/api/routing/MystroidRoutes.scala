package com.barisbolkan.mystroid.api.routing

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.barisbolkan.mystroid.api.BuildInfo
import com.barisbolkan.mystroid.api.persitence.MystroidRepository
import com.barisbolkan.mystroid.api.persitence.MystroidRepository.{AstroidInfo, Diameter}
import com.mongodb.reactivestreams.client.MongoDatabase
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe._
import io.circe.parser._
import sangria.ast.Document
import sangria.execution.{ErrorWithResolver, Executor, QueryAnalysisError}
import sangria.macros.derive.{ObjectTypeName, deriveObjectType}
import sangria.marshalling.circe._
import sangria.parser.{QueryParser, SyntaxError}
import sangria.schema.{Argument, Field, IntType, ListType, ObjectType, OptionInputType, Schema, fields}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class MystroidRoutes()(implicit system: ActorSystem, materializer: Materializer, ec: ExecutionContext, db: MongoDatabase) {

  lazy val repository: MystroidRepository = MystroidRepository

  private[this] implicit val DiameterType: ObjectType[Unit, Diameter] = deriveObjectType[Unit, Diameter](ObjectTypeName("Diameter"))

  private[this] val limitArg = Argument("limit", OptionInputType(IntType), defaultValue = 20)
  private[this] val offsetArg = Argument("offset", OptionInputType(IntType), defaultValue = 0)

  private[this] val queries: List[Field[Unit, Unit]] = List(
    Field(
      name = "astroids",
      fieldType = ListType(DiameterType),
      arguments = (limitArg :: offsetArg :: Nil),
      resolve = ctx => repository.readAll[AstroidInfo](ctx arg limitArg).map(_.map(_.estimatedDiameter))
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
    get {
      complete(Executor.execute(schema, sangria.introspection.introspectionQuery))
    }
  } ~
      path("graphql") {
        get {
          parameters('query, 'operationName.?, 'variables.?) { (query, operationName, variables) =>
            QueryParser.parse(query) match {
              case Success(ast) =>
                variables.map(parse) match {
                  case Some(Left(error)) => complete(BadRequest, formatError(error))
                  case Some(Right(json)) => executeGraphQL(ast, operationName, json)
                  case None => executeGraphQL(ast, operationName, Json.obj())
                }
              case Failure(error) ⇒ complete(BadRequest, formatError(error))
            }
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