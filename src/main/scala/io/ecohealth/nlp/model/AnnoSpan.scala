package io.ecohealth.nlp.models

case class AnnoSpan(
    start: Int,
    stop: Int,
    label: Option[String] = None,
    `type`: Option[String] = None)
