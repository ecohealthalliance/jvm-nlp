package io.ecohealth.nlp.models

case class AnnoDoc(
    text: String, tiers: Map[String, AnnoTier], date: String)
