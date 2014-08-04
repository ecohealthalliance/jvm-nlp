package io.ecohealth.nlp

import org.scalatra._
import org.scalatra.swagger._
import org.scalatra.json._

import org.json4s.{DefaultFormats, Formats}

import models._

class NLPController(implicit val swagger: Swagger) extends ScalatraServlet with NativeJsonSupport with SwaggerSupport  {

  protected implicit val jsonFormats: Formats = DefaultFormats

  protected val applicationDescription = "API to provide Stanford NLP results"

  val annotator = new NLPAnnotator()

  before() {
    contentType = formats("json")
  }

  val getNLPAnnotations =
    (apiOperation[AnnoDoc]("getNLPAnnotations")
      summary "Get all the NLP annotations for a document"
      notes "Process a document through the Stanford NLP pipeline and get all annotations"
      parameter bodyParam[AnnoDoc]("doc").description("The AnnoDoc to be annotated"))

  post("/getNLPAnnotations", operation(getNLPAnnotations)){
    val doc = parsedBody.extract[AnnoDoc]
    annotator.annotate(doc)
  }

}


