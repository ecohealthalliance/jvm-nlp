import java.util.List
import java.util.Properties
import scala.collection.JavaConversions._

import org.scalatra.LifeCycle
import javax.servlet.ServletContext

import edu.stanford.nlp.ling.CoreAnnotations
import edu.stanford.nlp.ling.CoreLabel
import edu.stanford.nlp.pipeline._
import edu.stanford.nlp.time._
import edu.stanford.nlp.util.CoreMap

import io.ecohealth.nlp.{NLPSwagger, NLPController, ResourcesApp}

class ScalatraBootstrap extends LifeCycle {

  implicit val swagger = new NLPSwagger

  override def init(context: ServletContext) {
    context.mount(new NLPController, "/annotate", "annotate")
    context.mount (new ResourcesApp, "/api-docs")
  }
}
