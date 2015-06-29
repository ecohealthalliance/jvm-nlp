package io.ecohealth.nlp

import scala.collection.immutable.List

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
    val intersectPatt = """.*INTERSECT (\d{4}\-\d{2}\-\d{2})$""".r

    val props = new Properties()
    props.put("annotators", "tokenize, ssplit, pos, lemma, ner")
    val pipeline = new StanfordCoreNLP(props)

    val timeProps = new Properties()
    timeProps.setProperty("sutime.includeRanges", "true")
    timeProps.setProperty("sutime.markTimeRanges", "true")
    timeProps.setProperty("teRelHeurLevel", "more")
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
            val labelOpt = {
                val labelStringOpt =
                    if (temporal.toISOString != null) Some(temporal.toISOString)
                    else if (temporal.toString != null) Some(temporal.toString)
                    else None
                labelStringOpt map { string =>
                    val intersectMatchOpt = intersectPatt.findFirstMatchIn(string)
                    intersectMatchOpt map { _.group(1).toString } getOrElse string
                }
            }

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

        def getNEAnnoSpans( tokenList : scala.collection.immutable.List[edu.stanford.nlp.ling.CoreLabel] ) : scala.collection.immutable.List[AnnoSpan] = {
            if(tokenList.length == 0){
                return scala.collection.immutable.List()
            }
            val token = tokenList.head
            val neTag : String = token.get(classOf[CoreAnnotations.NamedEntityTagAnnotation])
            if(neTag.length > 0) {
                val neEndIdx = {
                    val idx = tokenList.indexWhere { t =>
                        neTag != t.get(classOf[CoreAnnotations.NamedEntityTagAnnotation])
                    }
                    if(idx > 0) idx
                    else tokenList.length
                }

                val start = token.get(classOf[CoreAnnotations.CharacterOffsetBeginAnnotation])
                val stop = tokenList(neEndIdx - 1).get(classOf[CoreAnnotations.CharacterOffsetEndAnnotation])
                val label = tokenList.take(neEndIdx) map { t =>
                    t.get(classOf[CoreAnnotations.TextAnnotation])
                } mkString(" ")
                return AnnoSpan(
                    start,
                    stop,
                    label=Some(label),
                    `type`=Some(neTag)
                ) :: getNEAnnoSpans(tokenList.drop(neEndIdx))
            } else {
                return getNEAnnoSpans(tokenList.drop(1))
            }
        }

        val tokens = annotation.get(classOf[CoreAnnotations.TokensAnnotation])
        val neSpans = getNEAnnoSpans(tokens.iterator.toList)

        val tiers = Map(("tokens", getTokenTier(annotation)),
                        ("sentences", getSentenceTier(annotation)),
                        ("pos", getPOSTier(annotation)),
                        ("nes", AnnoTier(neSpans)),
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

        // We can end up with some unanticipated kinds of ISO strings from the time
        // annoator here. For example, "the morning of Tuesday, 3 [April/2012]"
        // will yield an ISO string like "2015-01-06TMO", which we wouldn't be able
        // to parse and it's unclear how to represent for the client. Therefore
        // return None for any ISO strings we can't parse ints out of as expected.

        try {
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
        } catch {
            case e: java.lang.NumberFormatException => None
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
            val label = Some(sentenceAnnotation.get(classOf[CoreAnnotations.TextAnnotation]))

            AnnoSpan(start, stop, label=label)

        } toList

        AnnoTier(spans)

    }

    def getTokenTier(annotation: Annotation): AnnoTier = {

        var tokenAnnotations = annotation.get(classOf[CoreAnnotations.TokensAnnotation])

        val spans = tokenAnnotations.iterator map { tokenAnnotation =>
            AnnoSpan(tokenAnnotation.beginPosition,
                     tokenAnnotation.endPosition,
                     label=Some(tokenAnnotation.get(classOf[CoreAnnotations.TextAnnotation])))
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
