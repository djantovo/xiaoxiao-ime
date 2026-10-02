package com.xx.ime.handwriting

interface InkRecognizer {
    val available: Boolean
    val name: String
    fun recognize(strokes: List<Stroke>, maxResults: Int = 10): List<String>
    fun close() {}
}
