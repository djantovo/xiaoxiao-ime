package com.xx.ime.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.xx.ime.R

/**
 * 候选栏：单行横滑 + 「更多」展开成 4 列网格
 * （面板内展开，不用 Dialog，避免 IME 弹窗 token 问题）
 */
class CandidateBar @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : LinearLayout(ctx, attrs) {

    interface Listener {
        fun onCandidate(text: String)
    }

    var listener: Listener? = null

    private val scroll: HorizontalScrollView
    private val list: LinearLayout
    private val more: TextView
    private val expandedBox: ScrollView
    private val expandedGrid: LinearLayout

    private var items: List<String> = emptyList()
    private var expanded = false

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(ContextCompat.getColor(ctx, R.color.ime_bg))

        LayoutInflater.from(ctx).inflate(R.layout.view_candidate_bar, this, true)
        scroll = findViewById(R.id.cand_scroll)
        list = findViewById(R.id.cand_list)
        more = findViewById(R.id.cand_more)

        expandedGrid = LinearLayout(ctx).apply { orientation = VERTICAL }
        expandedBox = ScrollView(ctx).apply {
            addView(expandedGrid)
            visibility = GONE
        }
        addView(expandedBox, LayoutParams(MATCH_PARENT, dp(150)))

        more.setOnClickListener { toggleExpand() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    fun showMessage(msg: String) {
        items = emptyList()
        list.removeAllViews()
        collapse()
        addChip(msg, false)
    }

    fun setCandidates(newItems: List<String>) {
        items = newItems
        collapse()
        list.removeAllViews()
        if (newItems.isEmpty()) {
            more.visibility = GONE
            return
        }
        more.visibility = if (newItems.size > 12) VISIBLE else GONE
        for (s in newItems.take(60)) addChip(s, true)
        scroll.scrollTo(0, 0)
    }

    fun currentCandidates(): List<String> = items

    private fun addChip(s: String, clickable: Boolean) {
        val tv = TextView(context)
        tv.text = s
        tv.textSize = 19f
        tv.maxLines = 1
        tv.setTextColor(ContextCompat.getColor(context, R.color.key_text))
        tv.setPadding(dp(13), dp(8), dp(13), dp(8))
        if (clickable) {
            tv.isClickable = true
            tv.setOnClickListener { listener?.onCandidate(s) }
        }
        list.addView(tv)
    }

    private fun toggleExpand() {
        if (expanded) collapse() else expand()
    }

    private fun expand() {
        if (items.isEmpty()) return
        expanded = true
        expandedGrid.removeAllViews()
        var row: LinearLayout? = null
        for ((i, s) in items.take(200).withIndex()) {
            if (i % 4 == 0) {
                row = LinearLayout(context).apply { orientation = HORIZONTAL }
                expandedGrid.addView(row, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            }
            val tv = TextView(context)
            tv.text = s
            tv.textSize = 17f
            tv.maxLines = 1
            tv.gravity = Gravity.CENTER
            tv.setPadding(dp(4), dp(10), dp(4), dp(10))
            tv.setTextColor(ContextCompat.getColor(context, R.color.key_text))
            tv.isClickable = true
            tv.setOnClickListener { listener?.onCandidate(s) }
            row?.addView(tv, LayoutParams(0, WRAP_CONTENT, 1f))
        }
        scroll.visibility = GONE
        more.visibility = VISIBLE
        more.text = "收起"
        expandedBox.visibility = VISIBLE
    }

    private fun collapse() {
        expanded = false
        expandedBox.visibility = GONE
        scroll.visibility = VISIBLE
        more.text = context.getString(R.string.more)
        more.visibility = if (items.size > 12) VISIBLE else GONE
    }
}
