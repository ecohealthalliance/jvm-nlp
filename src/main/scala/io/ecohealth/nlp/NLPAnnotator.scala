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

    def annotate(doc: AnnoDoc): AnnoDoc = {

        val referenceDate = doc.date getOrElse new Date()
        var docDate = referenceDate

        val annotation = new Annotation(doc.text)
        annotation.set(classOf[CoreAnnotations.DocDateAnnotation],
            new SimpleDateFormat("yyyy-MM-dd").format(referenceDate))
        pipeline.annotate(annotation)
        var timexAnnotations = annotation.get(classOf[TimeAnnotations.TimexAnnotations])

        // If we didn't get a reference date from the doc, let's try to guess
        // one from the text itself, and re-annotate with respect to that guessed date.
        if (! doc.date.isDefined) {
            val guessedReferenceDate = guessReferenceDate(timexAnnotations)
            if (guessedReferenceDate.isDefined) {
                // annotation = new Annotation(text)
                annotation.set(classOf[CoreAnnotations.DocDateAnnotation], guessedReferenceDate.get)
                pipeline.annotate(annotation)
                docDate = new SimpleDateFormat("yyyy-MM-dd").parse(guessedReferenceDate.get)
                timexAnnotations = annotation.get(classOf[TimeAnnotations.TimexAnnotations])
            }
        }

        val spans = timexAnnotations.iterator map { timexAnnotation =>
            val tokens = timexAnnotation.get(classOf[CoreAnnotations.TokensAnnotation])
            val start = tokens.get(0).get(classOf[CoreAnnotations.CharacterOffsetBeginAnnotation])
            val stop = tokens.get(tokens.size() - 1).get(classOf[CoreAnnotations.CharacterOffsetEndAnnotation])
            val temporal = timexAnnotation.get(classOf[TimeExpression.Annotation]).getTemporal()
            val label = temporal.toISOString
            val tempType = temporal.getTimexType.toString
            AnnoSpan(start, stop, label=Some(label), `type`=Some(tempType))
        } toList

        val tiers = Map(("tokens", getTokenTier(annotation)),
                        ("sentences", getSentenceTier(annotation)),
                        ("pos", getPOSTier(annotation)),
                        ("times", AnnoTier(spans)))

        doc.copy(tiers=doc.tiers ++ tiers, date=Some(docDate))

    }

    def guessReferenceDate(timexAnnotations: java.util.List[edu.stanford.nlp.util.CoreMap]): Option[String] = {
        if (timexAnnotations.length > 0) {
            val annotation = timexAnnotations.iterator.next
            val tokens = annotation.get(classOf[CoreAnnotations.TokensAnnotation])
            val start = tokens.get(0).get(classOf[CoreAnnotations.CharacterOffsetBeginAnnotation])
            val temporal = annotation.get(classOf[TimeExpression.Annotation]).getTemporal()
            val label = temporal.toISOString
            val tempType = temporal.getTimexType.toString

            // Let's look for a date that starts within the first 10 characters of the document, and is a full DATE
            if (start <= 10 && tempType == "DATE" && isoDatePatt.findFirstMatchIn(label).isDefined) Some(label.substring(0, 10))
            else None
        } else {
            None
        }
    }

    def getSentenceTier(annotation: Annotation): AnnoTier = {

        var sentenceAnnotations = annotation.get(classOf[CoreAnnotations.SentencesAnnotation])

        val spans = sentenceAnnotations.iterator map { sentenceAnnotation =>
            val tokens = sentenceAnnotation.get(classOf[CoreAnnotations.TokensAnnotation])
            val start = tokens.get(0).get(classOf[CoreAnnotations.CharacterOffsetBeginAnnotation])
            val stop = tokens.get(tokens.size() - 1).get(classOf[CoreAnnotations.CharacterOffsetEndAnnotation])

            AnnoSpan(start, stop)
            
        } toList

        AnnoTier(spans)

    }

    def getTokenTier(annotation: Annotation): AnnoTier = {

        var tokenAnnotations = annotation.get(classOf[CoreAnnotations.TokensAnnotation])

        val spans = tokenAnnotations.iterator map { tokenAnnotation =>
            AnnoSpan(tokenAnnotation.beginPosition,
                     tokenAnnotation.endPosition)
        } toList

        AnnoTier(spans)

    }

    def getPOSTier(annotation: Annotation): AnnoTier = {

        var tokenAnnotations = annotation.get(classOf[CoreAnnotations.TokensAnnotation])

        val spans = tokenAnnotations.iterator map { tokenAnnotation =>
            AnnoSpan(tokenAnnotation.beginPosition,
                     tokenAnnotation.endPosition,
                     label=Some(tokenAnnotation.get(classOf[CoreAnnotations.PartOfSpeechAnnotation])))
        } toList

        AnnoTier(spans)

    }

}