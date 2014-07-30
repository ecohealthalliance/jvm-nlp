package io.ecohealth.nlp

import java.util.List
import java.util.Properties
import java.util.Date
import java.text.SimpleDateFormat

import collection.JavaConversions._

import edu.stanford.nlp.ling.CoreAnnotations
import edu.stanford.nlp.ling.CoreLabel
import edu.stanford.nlp.pipeline._
import edu.stanford.nlp.time._
import edu.stanford.nlp.util.CoreMap

import models._

class NLPAnnotator {

    val isoDatePatt = """^\d\d\d\d\-\d\d\-\d\d""".r

    val props = new Properties()

    val pipeline = new AnnotationPipeline()
    pipeline.addAnnotator(new PTBTokenizerAnnotator(false))
    pipeline.addAnnotator(new WordsToSentencesAnnotator(false))
    pipeline.addAnnotator(new POSTaggerAnnotator(false))
    pipeline.addAnnotator(new TimeAnnotator("sutime", props))

    def annotate(text: String, referenceDateOpt: Option[String] = None): AnnoDoc = {

        val referenceDate = referenceDateOpt getOrElse {
            new SimpleDateFormat("yyyy-MM-dd").format(new Date())
        }
        var docDate = referenceDate

        println("referenceDate:", referenceDate)


        val annotation = new Annotation(text)
        annotation.set(classOf[CoreAnnotations.DocDateAnnotation], referenceDate)
        pipeline.annotate(annotation)
        var timexAnnotations = annotation.get(classOf[TimeAnnotations.TimexAnnotations])

        // If we didn't get a reference date, let's try to guess one from the
        // text itself, and re-annotate with respect to that guessed date.
        if (! referenceDateOpt.isDefined) {
            println("referenceDateOpt was not defined")
            val guessedReferenceDate = guessReferenceDate(timexAnnotations)
            if (guessedReferenceDate.isDefined) {
                println("guessedReferenceDate was defined", guessedReferenceDate)
                // annotation = new Annotation(text)
                annotation.set(classOf[CoreAnnotations.DocDateAnnotation], guessedReferenceDate.get)
                pipeline.annotate(annotation)
                docDate = guessedReferenceDate.get
                timexAnnotations = annotation.get(classOf[TimeAnnotations.TimexAnnotations])
                println("docDate", docDate)
            }
        }

        val spans = timexAnnotations.iterator map { annotation =>
            val tokens = annotation.get(classOf[CoreAnnotations.TokensAnnotation])
            val start = tokens.get(0).get(classOf[CoreAnnotations.CharacterOffsetBeginAnnotation])
            val stop = tokens.get(tokens.size() - 1).get(classOf[CoreAnnotations.CharacterOffsetEndAnnotation])
            val temporal = annotation.get(classOf[TimeExpression.Annotation]).getTemporal()
            val label = temporal.toISOString
            val tempType = temporal.getTimexType.toString
            AnnoSpanTemporal(start, stop, label, tempType)
        } toList

        AnnoDoc(text, Map(("times", AnnoTier(spans))), docDate)

    }

    def guessReferenceDate(timexAnnotations: java.util.List[edu.stanford.nlp.util.CoreMap]): Option[String] = {
        val annotation = timexAnnotations.iterator.next
        val tokens = annotation.get(classOf[CoreAnnotations.TokensAnnotation])
        val start = tokens.get(0).get(classOf[CoreAnnotations.CharacterOffsetBeginAnnotation])
        val temporal = annotation.get(classOf[TimeExpression.Annotation]).getTemporal()
        val label = temporal.toISOString
        val tempType = temporal.getTimexType.toString

        // Let's look for a date that starts within the first 10 characters of the document, and is a full DATE
        if (start <= 10 && tempType == "DATE" && isoDatePatt.findFirstMatchIn(label).isDefined) Some(label.substring(0, 10))
        else None

    }
}