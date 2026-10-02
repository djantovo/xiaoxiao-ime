package com.xx.ime.ui

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.xx.ime.R
import com.xx.ime.core.ClipItem
import com.xx.ime.core.ClipboardStore
import com.xx.ime.core.Segmenter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 剪切板面板：300 条上限、无字数限制（列表只读预览，点击才取全文）、每条可分词
 */
class ClipboardPanel(ctx: Context, private val store: ClipboardStore) : LinearLayout(ctx) {

    interface Listener {
        fun onInsert(text: String)
        fun onClosePanel()
    }

    var listener: Listener? = null

    private val header = TextView(ctx)
    private val listBox = LinearLayout(ctx)
    private val scroll = ScrollView(ctx)
    private val df = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
    private var items: List<ClipItem> = emptyList()
    private var showLimit = 50

    init {
        orientation = VERTICAL
        setBackgroundColor(ContextCompat.getColor(ctx, R.color.ime_bg))

        val head = LinearLayout(ctx).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(6), dp(4))
        }
        header.textSize = 13f
        header.setTextColor(ContextCompat.getColor(ctx, R.color.key_text_dim))
        head.addView(header, LayoutParams(0, WRAP_CONTENT, 1f))
        head.addView(button("清空") {
            store.clear()
            showLimit = 50
            refresh()
        })
        head.addView(button("收起") { listener?.onClosePanel() })
        addView(head, LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        listBox.orientation = VERTICAL
        listBox.setPadding(dp(6), 0, dp(6), dp(6))
        scroll.addView(listBox)
        addView(scroll, LayoutParams(MATCH_PARENT, dp(196)))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun button(text: String, onClick: () -> Unit): TextView {
        val tv = TextView(context)
        tv.text = text
        tv.textSize = 13f
        tv.setPadding(dp(10), dp(8), dp(10), dp(8))
        tv.setTextColor(ContextCompat.getColor(context, R.color.accent))
        tv.isClickable = true
        tv.setOnClickListener { onClick() }
        return tv
    }

    fun refresh() {
        items = store.list()
        header.text = context.getString(R.string.clip_count, items.size)
        listBox.removeAllViews()
        if (items.isEmpty()) {
            val tv = TextView(context)
            tv.text = context.getString(R.string.clip_empty)
            tv.textSize = 14f
            tv.setPadding(dp(12), dp(24), dp(12), dp(24))
            tv.setTextColor(ContextCompat.getColor(context, R.color.key_text_dim))
            listBox.addView(tv)
            return
        }
        for (it in items.take(showLimit)) listBox.addView(buildItem(it))
        if (items.size > showLimit) {
            listBox.addView(button("加载更多（还有 ${items.size - showLimit} 条）") {
                showLimit += 50
                refresh()
            })
        }
    }

    private fun buildItem(item: ClipItem): View {
        val box = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        box.setBackgroundColor(Color.WHITE)

        val tv = TextView(context)
        tv.text = item.preview.replace('\n', ' ')
        tv.textSize = 15f
        tv.maxLines = 3
        tv.ellipsize = TextUtils.TruncateAt.END
        tv.setTextColor(ContextCompat.getColor(context, R.color.key_text))
        tv.isClickable = true
        tv.setOnClickListener { insert(item) }
        box.addView(tv, LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val meta = TextView(context)
        val pin = if (item.pinned) "📌 " else ""
        meta.text = "$pin${sizeText(item.length)} · ${timeText(item.time)} · ${item.length} 字"
        meta.textSize = 11f
        meta.setTextColor(ContextCompat.getColor(context, R.color.key_text_dim))
        meta.setPadding(0, dp(2), 0, dp(4))
        box.addView(meta, LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val tokensBox = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = GONE
            setPadding(0, dp(6), 0, 0)
        }

        val bar = LinearLayout(context).apply { orientation = HORIZONTAL }
        bar.addView(button("插入") { insert(item) })
        bar.addView(button(context.getString(R.string.clip_words)) {
            if (tokensBox.visibility == GONE) {
                fillTokens(tokensBox, item)
                tokensBox.visibility = VISIBLE
            } else {
                tokensBox.visibility = GONE
            }
        })
        bar.addView(button(if (item.pinned) "取消置顶" else "置顶") {
            store.setPinned(item.id, !item.pinned)
            refresh()
        })
        bar.addView(button("删除") {
            store.delete(item.id)
            refresh()
        })
        box.addView(bar, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        box.addView(tokensBox, LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val wrap = LinearLayout(context).apply { orientation = VERTICAL }
        wrap.setPadding(0, 0, 0, dp(6))
        wrap.addView(box, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return wrap
    }

    private fun fillTokens(container: LinearLayout, item: ClipItem) {
        container.removeAllViews()
        val full = store.fullText(item.id) ?: return
        val tokens = Segmenter.split(full, 120)
        if (tokens.isEmpty()) return
        var row: LinearLayout? = null
        for ((i, t) in tokens.withIndex()) {
            if (i % 4 == 0) {
                row = LinearLayout(context).apply { orientation = HORIZONTAL }
                container.addView(row, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            }
            val tv = TextView(context)
            tv.text = t
            tv.textSize = 14f
            tv.gravity = Gravity.CENTER
            tv.maxLines = 1
            tv.setPadding(dp(2), dp(8), dp(2), dp(8))
            tv.setTextColor(ContextCompat.getColor(context, R.color.accent))
            tv.isClickable = true
            tv.setOnClickListener { listener?.onInsert(t) }
            row?.addView(tv, LayoutParams(0, WRAP_CONTENT, 1f))
        }
        if (tokens.size >= 120) {
            val more = TextView(context)
            more.text = "…（仅显示前 120 个分词）"
            more.textSize = 11f
            more.setTextColor(ContextCompat.getColor(context, R.color.key_text_dim))
            container.addView(more)
        }
    }

    private fun insert(item: ClipItem) {
        val t = store.fullText(item.id) ?: return
        listener?.onInsert(t)
    }

    private fun sizeText(len: Int): String = when {
        len < 1024 -> "$len B"
        len < 1024 * 1024 -> String.format(Locale.CHINA, "%.1f KB", len / 1024f)
        else -> String.format(Locale.CHINA, "%.1f MB", len / 1048576f)
    }

    private fun timeText(t: Long): String {
        val d = System.currentTimeMillis() - t
        return when {
            d < 60_000 -> "刚刚"
            d < 3_600_000 -> "${d / 60_000} 分钟前"
            d < 86_400_000 -> "${d / 3_600_000} 小时前"
            else -> df.format(Date(t))
        }
    }
}
