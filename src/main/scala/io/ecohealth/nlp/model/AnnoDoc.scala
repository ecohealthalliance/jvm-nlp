package io.ecohealth.nlp.models

import java.util.Date

case class AnnoDoc(
    text: String,
    tiers: Map[String, AnnoTier],
    date: Option[Date])
