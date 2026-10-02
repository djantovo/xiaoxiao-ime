package com.xx.ime.core

import java.text.BreakIterator
import java.util.Locale

/**
 * 分词。用 Android 自带 ICU 的 BreakIterator（中文词典分词，完全离线，无需额外词典），
 * 再把连续的标点/空白合并成单独 token。
 */
object Segmenter {

    fun split(text: String, maxTokens: Int = 400): List<String> {
        if (text.isEmpty()) return emptyList()
        // 超长文本只处理头部，避免卡顿（剪切板本身不限字数）
        val src = if (text.length > 20000) text.substring(0, 20000) else text
        val out = ArrayList<String>(64)
        try {
            val bi = BreakIterator.getWordInstance(Locale.SIMPLIFIED_CHINESE)
            bi.setText(src)
            var s = bi.first()
            var e = bi.next()
            while (e != BreakIterator.DONE && out.size < maxTokens) {
                val tk = src.substring(s, e).trim()
                if (tk.isNotEmpty() && tk.any { !it.isWhitespace() }) out.add(tk)
                s = e
                e = bi.next()
            }
        } catch (t: Throwable) {
            for (c in src) if (!c.isWhitespace()) out.add(c.toString())
        }
        return out
    }
}
