package io.ecohealth.nlp.models

case class AnnoSpan(
    start: Int,
    stop: Int,
    label: Option[String] = None,
    `type`: Option[String] = None,
    timePoint: Option[TimePoint] = None,
    timeRange: Option[TimeRange] = None,
    timeDuration: Option[TimeDuration] = None,
    timeSet: Option[TimeSet] = None)
