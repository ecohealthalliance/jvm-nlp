package io.ecohealth.nlp

import org.scalatra.ScalatraServlet
import org.scalatra.swagger.{ApiInfo, NativeSwaggerBase, Swagger}


class ResourcesApp(implicit val swagger: Swagger) extends ScalatraServlet with NativeSwaggerBase

object NLPSwagger{
  val Info = ApiInfo(
    "EcoHealth NLP annotation",
    "Docs for the NLP API",
    "http://ecohealth.io",
    "russ@ecohealth.io",
    "Apache 2",
    "http://www.apache.org/licenses/LICENSE-2.0.html")
}
class NLPSwagger extends Swagger(Swagger.SpecVersion, "1", NLPSwagger.Info)
