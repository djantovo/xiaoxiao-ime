package com.xx.ime.handwriting

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.xx.ime.R
import kotlin.math.abs

/** 笔迹采集 + 绘制；停笔 350ms 后回调识别 */
class HandwritingView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    interface Listener {
        fun onInk(strokes: List<Stroke>)
    }

    var listener: Listener? = null

    private val strokes = ArrayList<Stroke>()
    private var cur: Stroke? = null
    private val path = Path()
    private val density = resources.displayMetrics.density

    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5.5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#1A1A1A")
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 15f * density
        color = ContextCompat.getColor(ctx, R.color.key_text_dim)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var hint: String? = null

    private val recognizeTask = Runnable {
        if (strokes.isNotEmpty()) listener?.onInk(ArrayList(strokes))
    }

    init {
        setBackgroundColor(Color.WHITE)
    }

    fun showHint(text: String?) {
        hint = text
        invalidate()
    }

    fun clear() {
        handler.removeCallbacks(recognizeTask)
        strokes.clear()
        cur = null
        invalidate()
    }

    fun undo() {
        handler.removeCallbacks(recognizeTask)
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.size - 1)
        invalidate()
        handler.postDelayed(recognizeTask, 120)
    }

    fun hasInk(): Boolean = strokes.isNotEmpty()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handler.removeCallbacks(recognizeTask)
                cur = Stroke().also { strokes.add(it) }
                cur?.pts?.add(Pt(event.x, event.y, event.eventTime))
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                val c = cur
                if (c != null) {
                    val last = if (c.pts.isEmpty()) null else c.pts[c.pts.size - 1]
                    if (last == null || abs(last.x - event.x) > 0.8f || abs(last.y - event.y) > 0.8f) {
                        c.pts.add(Pt(event.x, event.y, event.eventTime))
                        invalidate()
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cur = null
                invalidate()
                handler.removeCallbacks(recognizeTask)
                handler.postDelayed(recognizeTask, 350)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (s in strokes) {
            if (s.pts.isEmpty()) continue
            if (s.pts.size == 1) {
                canvas.drawPoint(s.pts[0].x, s.pts[0].y, inkPaint)
                continue
            }
            path.reset()
            path.moveTo(s.pts[0].x, s.pts[0].y)
            for (i in 1 until s.pts.size) path.lineTo(s.pts[i].x, s.pts[i].y)
            canvas.drawPath(path, inkPaint)
        }
        if (strokes.isEmpty() && hint != null) {
            val fm = hintPaint.fontMetrics
            canvas.drawText(
                hint!!,
                width / 2f,
                height / 2f - (fm.ascent + fm.descent) / 2f,
                hintPaint
            )
        }
    }
}
