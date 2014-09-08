package io.ecohealth.nlp.models

case class TimePoint(
    year: Option[String]=None,
    month: Option[String]=None,
    date: Option[String]=None,
    hour: Option[String]=None,
    minute: Option[String]=None,
    second: Option[String]=None,
    mod: Option[String]=None
    )

case class TimeRange(
    begin: TimePoint,
    end: TimePoint,
    mod: Option[String]
)

case class TimeDuration(
    label: String,
    mod: Option[String]
)

case class TimeSet(
    label: String,
    mod: Option[String]
)

/** mod should be one of:

    Points

    BEFORE              more than a decade ago
    AFTER               less than a year ago
    ON OR BEFORE        no less than a year ago
    ON OR AFTER         no more than a year ago

    Durations

    LESS THAN           less than 2 hours long
    MORE THAN           more than 5 minutes
    EQUAL OR LESS       no more than 10 days
    EQUAL OR MORE       at least 10 days

    Points and Durations

    START               the early 1960s, the dawn of 2000
    MID                 the middle of the month, mid-February
    END                 the end of the year
    APPROX

see: http://www.timeml.org/tempeval2/tempeval2-trial/guidelines/timex3guidelines-072009.pdf
*/