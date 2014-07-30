package io.ecohealth.nlp

import org.scalatra._
import org.scalatra.swagger._
import org.scalatra.json._

import org.json4s.{DefaultFormats, Formats}

import models.AnnoDoc

class NLPController(implicit val swagger: Swagger) extends ScalatraServlet with NativeJsonSupport with SwaggerSupport  {

  protected implicit val jsonFormats: Formats = DefaultFormats

  protected val applicationDescription = "API to provide Stanford NLP results"

  val annotator = new NLPAnnotator()

  before() {
    contentType = formats("json")
  }

  val getNLPAnnotations =
    (apiOperation[List[AnnoDoc]]("getNLPAnnotations")
      summary "Get all the NLP annotations for a document"
      notes "Process a document through the Stanford NLP pipeline and get all annotations"
      parameter queryParam[String]("text").description("The text content of the document to be annotated")
      parameter queryParam[Option[String]]("referenceDate").description("The date with respect to which the document will be time-annotated"))

  post("/getNLPAnnotations", operation(getNLPAnnotations)){
    params.get("text") match {
      case Some(text) => annotator.annotate(text, params.get("referenceDate"))
      case None => halt(400, "did not find text POST parameter")
    }
  }

}


