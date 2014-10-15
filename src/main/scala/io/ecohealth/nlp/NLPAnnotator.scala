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

    val timeProps = new Properties()
    timeProps.setProperty("sutime.includeRanges", "true")
    timeProps.setProperty("sutime.markTimeRanges", "true")
    timeProps.setProperty("teRelHeurLevel", "more")

    val pipeline = new AnnotationPipeline()
    pipeline.addAnnotator(new PTBTokenizerAnnotator(false))
    pipeline.addAnnotator(new WordsToSentencesAnnotator(false))
    pipeline.addAnnotator(new POSTaggerAnnotator(false))
    pipeline.addAnnotator(new TimeAnnotator("sutime", timeProps))

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
            val labelOpt =
                if (temporal.toISOString != null) Some(temporal.toISOString)
                else if (temporal.toString != null) Some(temporal.toString)
                else None

            val temporalType = temporal.getTimexType.toString
            val timeRange =
                if (temporalType == "DATE" || temporalType == "DURATION") getTimeRangeFromTemporal(temporal)
                else None
            val timePoint =
                if (timeRange.isDefined) None
                else if (temporalType == "DATE" || temporalType == "TIME") getTimePointFromTemporal(temporal)
                else None
            val timeDuration =
                if (temporalType == "DURATION") getTimeDurationFromTemporal(temporal)
                else None
            val timeSet =
                if (temporalType == "SET") getTimeSetFromTemporal(temporal)
                else None
            AnnoSpan(
                start,
                stop,
                label=labelOpt,
                `type`=Some(temporalType),
                timePoint=timePoint,
                timeRange=timeRange,
                timeDuration=timeDuration,
                timeSet=timeSet)
        } toList

        val tiers = Map(("tokens", getTokenTier(annotation)),
                        ("sentences", getSentenceTier(annotation)),
                        ("pos", getPOSTier(annotation)),
                        ("times", AnnoTier(spans)))

        doc.copy(tiers=doc.tiers ++ tiers, date=Some(docDate))

    }

    def getTimePointFromTemporal(temporal: SUTime.Temporal): Option[TimePoint] = {
        val mod = if (temporal.getMod == null) None else Some(temporal.getMod)
        getTimePointFromTime(temporal.getTime, mod)
    }

    def getTimeDurationFromTemporal(temporal: SUTime.Temporal): Option[TimeDuration] = {
        val mod = if (temporal.getMod == null) None else Some(temporal.getMod)
        val isoOpt = if (temporal.toISOString == null) None else Some(temporal.toISOString)
        isoOpt map { iso => TimeDuration(iso, mod) }
    }

    def getTimeSetFromTemporal(temporal: SUTime.Temporal): Option[TimeSet] = {
        val mod = if (temporal.getMod == null) None else Some(temporal.getMod)
        val isoOpt = if (temporal.toString == null) None else Some(temporal.toString)
        isoOpt map { iso => TimeSet(iso, mod) }
    }

    def getTimeRangeFromTemporal(temporal: SUTime.Temporal): Option[TimeRange] = {
        val range = temporal.getRange
        val mod = if (temporal.getMod == null) None else Some(temporal.getMod)
        if (range == null) {
            return None
        }
        val begin = range.begin
        val end = range.end
        if (begin == null || end == null) {
            return None
        }

        val beginMod = if (begin.getMod == null) None else Some(begin.getMod)
        val beginIso = range.begin.toISOString
        val beginTimePoint = if (beginIso == null) {
            None
        } else {
            getTimePointFromIso(beginIso, new TimePoint(mod=beginMod))
        }
        val endIso = end.toISOString
        val endMod = if (end.getMod == null) None else Some(end.getMod)
        val endTimePoint = if (endIso == null) {
            None
        } else {
            getTimePointFromIso(endIso, new TimePoint(mod=endMod))
        }
        if (beginTimePoint.isDefined && endTimePoint.isDefined) {
            Some(TimeRange(beginTimePoint.get, endTimePoint.get, mod))
        } else {
            None
        }

    }

    def getTimePointFromTime(time: SUTime.Time, mod: Option[String] = None): Option[TimePoint] = {
        val iso = time.toISOString
        if (iso == null) {
            return None
        } else {
            getTimePointFromIso(iso, new TimePoint(mod=mod))
        }
    }

    def getTimePointFromIso(iso: String, timePoint: TimePoint=new TimePoint): Option[TimePoint] = {
        if (Set("PAST_REF", "FUTURE_REF").contains(iso)) {
            return None
        } else if (iso.length == 4) {
            if (iso == "XXXX") Some(timePoint)
            else Some(timePoint.copy(year=Some(iso.toInt)))
        } else if (iso.length == 7) {
            getTimePointFromIso(iso.substring(0, 4), timePoint.copy(month=Some(iso.substring(5, 7).toInt)))
        } else if (iso.length == 10) {
            getTimePointFromIso(iso.substring(0, 7), timePoint.copy(date=Some(iso.substring(8, 10).toInt)))
        } else if (iso.length == 13) {
            getTimePointFromIso(iso.substring(0, 10), timePoint.copy(hour=Some(iso.substring(11, 13).toInt)))
        } else if (iso.length == 16) {
            getTimePointFromIso(iso.substring(0, 13), timePoint.copy(minute=Some(iso.substring(14, 16).toInt)))
        } else if (iso.length == 19) {
            getTimePointFromIso(iso.substring(0, 16), timePoint.copy(second=Some(iso.substring(17, 19).toInt)))
        } else {
            None
        }
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