package io.ecohealth.nlp.models

class AnnoSpan

case class AnnoSpanTemporal(start: Int, stop: Int, label: String, `type`: String) extends AnnoSpan
