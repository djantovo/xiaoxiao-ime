package com.xx.ime.handwriting

data class Pt(val x: Float, val y: Float, val t: Long)

class Stroke {
    val pts = ArrayList<Pt>(64)
}
