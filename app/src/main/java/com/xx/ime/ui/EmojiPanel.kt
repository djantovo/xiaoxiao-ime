package com.xx.ime.ui

import android.content.Context
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ArrayAdapter
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.xx.ime.R
import com.xx.ime.core.EmojiRepo
import com.xx.ime.core.Prefs

/** 离线 emoji 面板：最近使用 + 6 个分类 */
class EmojiPanel(private val ctx: Context, private val prefs: Prefs) : LinearLayout(ctx) {

    interface Listener {
        fun onEmoji(e: String)
    }

    var listener: Listener? = null

    private val tabs = LinearLayout(ctx)
    private val grid = GridView(ctx)
    private val adapter: ArrayAdapter<String>
    private val recent = ArrayList<String>()
    private var curTab = 0

    init {
        orientation = VERTICAL
        setBackgroundColor(ContextCompat.getColor(ctx, R.color.ime_bg))

        val hs = HorizontalScrollView(ctx)
        tabs.orientation = HORIZONTAL
        tabs.setPadding(dp(6), 0, dp(6), 0)
        hs.addView(tabs)
        addView(hs, LayoutParams(MATCH_PARENT, dp(36)))

        adapter = ArrayAdapter(ctx, R.layout.item_emoji, ArrayList<String>())
        grid.numColumns = 8
        grid.adapter = adapter
        grid.verticalSpacing = dp(1)
        grid.horizontalSpacing = dp(1)
        grid.setPadding(0, dp(2), 0, dp(2))
        grid.setOnItemClickListener { _, _, pos, _ ->
            val e = adapter.getItem(pos) ?: return@setOnItemClickListener
            listener?.onEmoji(e)
            pushRecent(e)
        }
        addView(grid, LayoutParams(MATCH_PARENT, dp(188)))

        loadRecent()
        buildTabs()
        showTab(0)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun loadRecent() {
        recent.clear()
        prefs.emojiRecent.split(' ').forEach { if (it.isNotBlank()) recent.add(it) }
    }

    private fun pushRecent(e: String) {
        recent.remove(e)
        recent.add(0, e)
        while (recent.size > 24) recent.removeAt(recent.size - 1)
        prefs.emojiRecent = recent.joinToString(" ")
        if (curTab == 0) showTab(0)
    }

    private fun buildTabs() {
        tabs.removeAllViews()
        val names = ArrayList<String>()
        names.add("最近")
        EmojiRepo.categories.forEach { names.add(it.name) }
        for ((i, n) in names.withIndex()) {
            val tv = TextView(ctx)
            tv.text = n
            tv.textSize = 14f
            tv.setPadding(dp(12), dp(8), dp(12), dp(8))
            tv.setTextColor(
                ContextCompat.getColor(ctx, if (i == 0) R.color.accent else R.color.key_text_dim)
            )
            tv.isClickable = true
            tv.setOnClickListener { showTab(i) }
            tabs.addView(tv)
        }
    }

    private fun showTab(i: Int) {
        curTab = i
        val data: List<String> = if (i == 0) recent else EmojiRepo.categories[i - 1].emojis
        adapter.clear()
        adapter.addAll(data)
        adapter.notifyDataSetChanged()
        for (j in 0 until tabs.childCount) {
            val tv = tabs.getChildAt(j) as TextView
            tv.setTextColor(
                ContextCompat.getColor(ctx, if (j == i) R.color.accent else R.color.key_text_dim)
            )
        }
        grid.setSelection(0)
    }
}
