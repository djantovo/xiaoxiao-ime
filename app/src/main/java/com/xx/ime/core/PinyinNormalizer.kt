package com.xx.ime.core

/** 带声调拼音 -> 纯字母(a-z,v)，并把连续字母切回音节 */
object PinyinNormalizer {

    /**
     * 归一化：去声调（ā→a、ǚ→v，含 NFD 分解出的组合符号）、ü/u: → v、去隔音符号与空格。
     * 允许 1-5 跟在字母后当声调数字（搜狗格式）；出现其它数字或非法字符返回 null。
     */
    fun normalize(raw: String): String? {
        if (raw.isEmpty()) return null
        val out = StringBuilder(raw.length)
        var prevLetter = false
        for (ch in raw.lowercase()) {
            val code = ch.code
            // NFD 分解出来的组合附加符号（U+0300..U+036F）
            if (code in 0x0300..0x036F) {
                if (code == 0x0308 && out.isNotEmpty() && out[out.length - 1] == 'u') {
                    out.setCharAt(out.length - 1, 'v')      // ü
                }
                continue
            }
            when (ch) {
                'ü', 'ǖ', 'ǘ', 'ǚ', 'ǜ' -> { out.append('v'); prevLetter = true }
                'ā', 'á', 'ǎ', 'à' -> { out.append('a'); prevLetter = true }
                'ē', 'é', 'ě', 'è', 'ê', 'ế', 'ề', 'ể', 'ễ', 'ệ' -> { out.append('e'); prevLetter = true }
                'ī', 'í', 'ǐ', 'ì' -> { out.append('i'); prevLetter = true }
                'ō', 'ó', 'ǒ', 'ò' -> { out.append('o'); prevLetter = true }
                'ū', 'ú', 'ǔ', 'ù' -> { out.append('u'); prevLetter = true }
                'ń', 'ň', 'ǹ' -> { out.append('n'); prevLetter = true }
                'ḿ' -> { out.append('m'); prevLetter = true }
                in 'a'..'z' -> { out.append(ch); prevLetter = true }
                ':', '：' -> {
                    if (out.isNotEmpty() && out[out.length - 1] == 'u') out.setCharAt(out.length - 1, 'v')
                    prevLetter = false
                }
                '\'', '’', '·', ' ', '-', '_', '/' -> prevLetter = false
                in '1'..'5' -> { if (prevLetter) prevLetter = false else return null }
                else -> return null
            }
        }
        val py = out.toString()
        return if (py.isEmpty() || py.length > 60) null else py
    }

    /**
     * 把原始拼音列切成音节：
     *   `ā bà`   -> [a, ba]
     *   `A'gǔ`   -> [a, gu]
     *   `nihao`  -> [ni, hao]（贪心最大匹配）
     */
    fun syllablesOfRaw(raw: String): List<String>? {
        val toks = raw.split(' ', '\'', '’', '·', '-', '_').filter { it.isNotEmpty() }
        if (toks.isEmpty()) return null
        val out = ArrayList<String>(4)
        for (t in toks) {
            val n = normalize(t) ?: return null
            if (n in Syllables.valid) {
                out.add(n)
            } else {
                val g = splitGreedy(n) ?: return null
                out.addAll(g)
            }
        }
        return out.ifEmpty { null }
    }

    /** 贪心最大匹配；失败返回 null */
    private fun splitGreedy(s: String): List<String>? {
        if (s.isEmpty()) return null
        val res = ArrayList<String>(4)
        var i = 0
        while (i < s.length) {
            var hit: String? = null
            var len = minOf(6, s.length - i)
            while (len >= 1) {
                val t = s.substring(i, i + len)
                if (t in Syllables.valid) { hit = t; break }
                len--
            }
            if (hit == null) return null
            res.add(hit)
            i += hit.length
        }
        return res
    }
}
