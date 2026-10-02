package com.xx.ime.core

/**
 * 九键引擎：数字串 -> 候选词
 *
 * 两路候选合并：
 *   1) 简码全匹配（音节首字母，如 96 -> 我们/文明/无奈），权重放大优先
 *   2) 完整拼音前缀匹配（如 96 -> 我/有/我们…）
 *
 * 单键也成立：打 9 时简码表里“首字母为 w/x/y/z 的单字”直接给出 我/为/要/有/下…
 */
class T9Engine(private val dict: BinDict) {

    private companion object {
        /** 简码全匹配的权重放大倍数 */
        const val AB_BOOST = 4L
    }

    fun candidates(digits: String, limit: Int = 40): List<String> {
        if (digits.isEmpty() || !dict.ready) return emptyList()
        val score = HashMap<String, Int>(limit * 4)

        // 1) 简码：字数必须等于按键数（1 个按键 = 1 个音节首字母）
        for (w in dict.queryAb(digits, limit)) {
            if (w.word.length != digits.length) continue
            val s = w.weight.toLong() * AB_BOOST
            val v = if (s > Int.MAX_VALUE) Int.MAX_VALUE else s.toInt()
            val old = score[w.word]
            if (old == null || v > old) score[w.word] = v
        }

        // 2) 完整拼音前缀
        for (w in dict.queryT9(digits, limit)) {
            val old = score[w.word]
            if (old == null || w.weight > old) score[w.word] = w.weight
        }

        if (score.isEmpty()) return emptyList()
        return score.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key.length }
                    .thenBy { it.key }
            )
            .take(limit)
            .map { it.key }
    }

    /** 只要单字（以后做长按候选栏之类的高级玩法用得上） */
    fun singleCandidates(digits: String, limit: Int = 20): List<String> {
        if (digits.isEmpty() || !dict.ready) return emptyList()
        val out = ArrayList<String>(limit)
        for (w in dict.queryAb(digits, limit * 2)) {
            if (w.word.length == 1) {
                out.add(w.word)
                if (out.size >= limit) break
            }
        }
        return out
    }
}
