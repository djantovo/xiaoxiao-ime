package com.xx.ime.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import androidx.core.content.ContextCompat
import com.xx.ime.R

/**
 * 自绘键盘：所有布局都由 Layouts 里的数据驱动，一个 View 搞定全部键盘。
 * 支持按键高亮、长按（功能键）、删除键长按连删、震动/按键音。
 */
class KeyboardView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    interface Listener {
        fun onKey(key: Key)

        /** 返回 true 表示已处理（避免松手时再触发普通点击） */
        fun onKeyLongPress(key: Key): Boolean = false
    }

    var listener: Listener? = null
    var haptic = true
    var sound = false

    var shiftOn = false
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }

    var keyHeightDp: Int = 52
        set(v) {
            field = v
            keyHeightPx = v * density
            requestLayout()
            invalidate()
        }

    var layout: KeyboardLayout? = null
        set(v) {
            field = v
            rebuild()
            requestLayout()
            invalidate()
        }

    private class KeyRect(val key: Key, val rect: RectF)

    private val density = resources.displayMetrics.density
    private var keyHeightPx = 52f * density
    private val gap = 3f * density
    private val radius = 9f * density
    private val slop = 26f * density

    private val keys = ArrayList<KeyRect>()
    private var pressed: KeyRect? = null
    private var longFired = false
    private var repeatRunning = false

    private val handler = Handler(Looper.getMainLooper())

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }

    private val colorKey = ContextCompat.getColor(ctx, R.color.key_bg)
    private val colorFunc = ContextCompat.getColor(ctx, R.color.key_bg_func)
    private val colorPressed = ContextCompat.getColor(ctx, R.color.key_bg_pressed)
    private val colorAccent = ContextCompat.getColor(ctx, R.color.accent)
    private val colorText = ContextCompat.getColor(ctx, R.color.key_text)
    private val colorDim = ContextCompat.getColor(ctx, R.color.key_text_dim)

    private fun sp(v: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics
    )

    init {
        isSoundEffectsEnabled = false
        subPaint.color = colorDim
        subPaint.textSize = sp(10f)
    }

    // ---------------- 布局计算 ----------------

    private fun rebuild() {
        keys.clear()
        val lay = layout ?: return
        val w = width - paddingLeft - paddingRight
        if (w <= 0) return
        var y = paddingTop.toFloat()
        for (row in lay.rows) {
            val h = keyHeightPx * row.height
            var total = 0f
            for (kk in row.keys) total += kk.weight
            if (total <= 0f) total = 1f
            var x = paddingLeft.toFloat()
            for (kk in row.keys) {
                val kw = w * (kk.weight / total)
                keys.add(
                    KeyRect(
                        kk,
                        RectF(
                            x + gap / 2f, y + gap / 2f,
                            x + kw - gap / 2f, y + h - gap / 2f
                        )
                    )
                )
                x += kw
            }
            y += h
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        var h = 0f
        layout?.rows?.forEach { h += keyHeightPx * it.height }
        h += paddingTop + paddingBottom
        setMeasuredDimension(w, h.toInt())
    }

    // ---------------- 绘制 ----------------

    override fun onDraw(canvas: Canvas) {
        if (keys.isEmpty()) rebuild()
        for (kr in keys) {
            val kk = kr.key
            bgPaint.color = when {
                kr === pressed -> colorPressed
                kk.style == KeyStyle.ACCENT -> colorAccent
                kk.style == KeyStyle.FUNC -> colorFunc
                else -> colorKey
            }
            canvas.drawRoundRect(kr.rect, radius, radius, bgPaint)

            val label = displayLabel(kk)
            textPaint.color = if (kk.style == KeyStyle.ACCENT) 0xFFFFFFFF.toInt() else colorText
            textPaint.textSize = when {
                label.length >= 4 -> sp(13f)
                label.length == 3 -> sp(15f)
                kk.style == KeyStyle.FUNC -> sp(15f)
                else -> sp(20f)
            }
            val fm = textPaint.fontMetrics
            val cy = kr.rect.centerY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText(label, kr.rect.centerX(), cy, textPaint)

            kk.sub?.let {
                subPaint.color = colorDim
                canvas.drawText(
                    it,
                    kr.rect.right - 7f * density,
                    kr.rect.top + subPaint.textSize + 3f * density,
                    subPaint
                )
            }
        }
    }

    private fun displayLabel(kk: Key): String {
        if (!shiftOn) return kk.label
        if (kk.label.length == 1 && kk.label[0] in 'a'..'z') return kk.label.uppercase()
        return kk.label
    }

    // ---------------- 触摸 ----------------

    private val longPressRunnable = Runnable {
        val kr = pressed ?: return@Runnable
        longFired = true
        listener?.onKeyLongPress(kr.key)
        if (kr.key.code == K.DEL) {
            repeatRunning = true
            handler.post(repeatRunnable)
        }
    }

    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (!repeatRunning) return
            val kr = pressed ?: return
            listener?.onKey(kr.key)
            handler.postDelayed(this, 55)
        }
    }

    private fun stopRepeat() {
        repeatRunning = false
        handler.removeCallbacks(repeatRunnable)
    }

    private fun hit(x: Float, y: Float): KeyRect? {
        for (i in keys.indices.reversed()) {
            val kr = keys[i]
            if (x >= kr.rect.left && x <= kr.rect.right &&
                y >= kr.rect.top && y <= kr.rect.bottom
            ) return kr
        }
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val kr = hit(event.x, event.y) ?: return false
                pressed = kr
                longFired = false
                if (haptic) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                handler.postDelayed(longPressRunnable, 450)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val kr = pressed ?: return true
                val outside = event.x < kr.rect.left - slop || event.x > kr.rect.right + slop ||
                        event.y < kr.rect.top - slop || event.y > kr.rect.bottom + slop
                if (outside) {
                    handler.removeCallbacks(longPressRunnable)
                    stopRepeat()
                    pressed = null
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val kr = pressed
                handler.removeCallbacks(longPressRunnable)
                stopRepeat()
                pressed = null
                invalidate()
                if (kr != null && !longFired && kr.rect.contains(event.x, event.y)) {
                    listener?.onKey(kr.key)
                    if (sound) playSoundEffect(SoundEffectConstants.CLICK)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                stopRepeat()
                pressed = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
