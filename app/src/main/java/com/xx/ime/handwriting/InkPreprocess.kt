package com.xx.ime.handwriting

import kotlin.math.sqrt

/**
 * 笔迹预处理，遵循 Google digital-ink 规范：
 *   1. 把所有笔画放到同一个 bbox 里等比缩放到 256x256
 *   2. 按 8px 间距重采样
 *   3. 转成增量特征 [dx/256, dy/256, 抬笔标记(, 落笔标记)]
 *   4. 截断/补零到模型要求的 seqLen
 */
object InkPreprocess {

    private const val BOX = 256f
    private const val SPACING = 8f

    fun features(strokes: List<Stroke>, seqLen: Int, dim: Int): Array<FloatArray> {
        val pts = ArrayList<FloatArray>(512)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (s in strokes) {
            for (p in s.pts) {
                if (p.x < minX) minX = p.x
                if (p.y < minY) minY = p.y
                if (p.x > maxX) maxX = p.x
                if (p.y > maxY) maxY = p.y
            }
        }
        if (minX > maxX) return Array(seqLen) { FloatArray(dim) }

        val w = maxX - minX
        val h = maxY - minY
        val scale = if (w <= 0.01f && h <= 0.01f) 1f else (BOX - SPACING * 2f) / maxOf(w, h)

        for (s in strokes) {
            val resampled = resample(s, minX, minY, scale)
            for ((i, p) in resampled.withIndex()) {
                val end = if (i == resampled.size - 1) 1f else 0f
                pts.add(floatArrayOf(p[0], p[1], end))
            }
        }
        if (pts.isEmpty()) return Array(seqLen) { FloatArray(dim) }

        val out = Array(seqLen) { FloatArray(dim) }
        var px = 0f
        var py = 0f
        var prevEnd = 1f
        val n = minOf(seqLen, pts.size)
        for (i in 0 until n) {
            val p = pts[i]
            val dx = (p[0] - px) / BOX
            val dy = (p[1] - py) / BOX
            px = p[0]
            py = p[1]
            val isEnd = p[2]
            when (dim) {
                2 -> {
                    out[i][0] = dx
                    out[i][1] = dy
                }
                3 -> {
                    out[i][0] = dx
                    out[i][1] = dy
                    out[i][2] = isEnd
                }
                else -> {
                    out[i][0] = dx
                    out[i][1] = dy
                    out[i][2] = isEnd
                    out[i][3] = if (i == 0) 1f else 0f
                }
            }
            prevEnd = isEnd
        }
        return out
    }

    /** 按固定间距重采样一条笔画 */
    private fun resample(s: Stroke, minX: Float, minY: Float, scale: Float): List<FloatArray> {
        val out = ArrayList<FloatArray>(32)
        val pts = s.pts
        if (pts.isEmpty()) return out
        if (pts.size == 1) {
            val p = floatArrayOf(
                (pts[0].x - minX) * scale + SPACING,
                (pts[0].y - minY) * scale + SPACING
            )
            out.add(p)
            out.add(p.copyOf())
            return out
        }
        var lastX = (pts[0].x - minX) * scale + SPACING
        var lastY = (pts[0].y - minY) * scale + SPACING
        out.add(floatArrayOf(lastX, lastY))

        for (i in 1 until pts.size) {
            val x = (pts[i].x - minX) * scale + SPACING
            val y = (pts[i].y - minY) * scale + SPACING
            var dx = x - lastX
            var dy = y - lastY
            var dist = sqrt(dx * dx + dy * dy)
            if (dist <= 0f) continue
            var cx = lastX
            var cy = lastY
            while (dist >= SPACING) {
                val r = SPACING / dist
                cx += dx * r
                cy += dy * r
                out.add(floatArrayOf(cx, cy))
                dx = x - cx
                dy = y - cy
                dist = sqrt(dx * dx + dy * dy)
            }
            lastX = x
            lastY = y
        }
        val last = pts[pts.size - 1]
        out.add(
            floatArrayOf(
                (last.x - minX) * scale + SPACING,
                (last.y - minY) * scale + SPACING
            )
        )
        return out
    }
}
