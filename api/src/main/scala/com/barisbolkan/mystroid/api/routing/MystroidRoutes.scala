package com.barisbolkan.mystroid.api.routing

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.barisbolkan.mystroid.api.BuildInfo
import com.barisbolkan.mystroid.api.routing.graphql.SchemaDefinition
import com.mongodb.reactivestreams.client.MongoDatabase
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe._
import io.circe.parser._
import sangria.ast.Document
import sangria.execution.{ErrorWithResolver, Executor, QueryAnalysisError}
import sangria.marshalling.circe._
import sangria.parser.{QueryParser, SyntaxError}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class MystroidRoutes()(implicit val system: ActorSystem, val materializer: Materializer, val ec: ExecutionContext, val db: MongoDatabase)
  extends SchemaDefinition {

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
