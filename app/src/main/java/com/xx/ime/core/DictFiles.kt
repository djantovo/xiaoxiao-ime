package com.xx.ime.core

/** 每个词库文件的导入配置（文件名不含扩展名） */
object DictFiles {

    class Cfg(
        val cat: Int,
        /** 没有权重列，用行序当权重 */
        val weightless: Boolean = false,
        /** 该文件的最低权重 */
        val minWeight: Int = 0,
        /** 每个 key 最多保留条数 */
        val topK: Int = 24
    )

    private val TABLE: Map<String, Cfg> = mapOf(
        "zi" to Cfg(DictFormat.CAT_BASE, topK = 64),
        "jichu" to Cfg(DictFormat.CAT_BASE),
        "lianxiang" to Cfg(DictFormat.CAT_BASE, topK = 32),
        "duoyin" to Cfg(DictFormat.CAT_BASE, topK = 32),
        "mixed" to Cfg(DictFormat.CAT_BASE, weightless = true),
        "abbrev" to Cfg(DictFormat.CAT_BASE, weightless = true),
        "t9_abbrev" to Cfg(DictFormat.CAT_BASE, weightless = true),
        "cuoyin" to Cfg(DictFormat.CAT_FUZZY),
        "fangyan" to Cfg(DictFormat.CAT_FUZZY, topK = 16),
        "taifeng" to Cfg(DictFormat.CAT_FUZZY, topK = 16),
        "diming" to Cfg(DictFormat.CAT_NAME, minWeight = 1000),
        "renming" to Cfg(DictFormat.CAT_NAME, minWeight = 1000),
        "mingren" to Cfg(DictFormat.CAT_NAME, minWeight = 1000),
        "yiren" to Cfg(DictFormat.CAT_NAME, minWeight = 1000, topK = 16),
        "shici" to Cfg(DictFormat.CAT_PRO, minWeight = 500),
        "wuzhong" to Cfg(DictFormat.CAT_PRO, minWeight = 500),
        "yixue" to Cfg(DictFormat.CAT_PRO, minWeight = 500),
        "yaopin" to Cfg(DictFormat.CAT_PRO, minWeight = 500),
        "huaxue" to Cfg(DictFormat.CAT_PRO, minWeight = 500)
    )

    fun cfgOf(fileName: String): Cfg {
        val base = fileName.substringBefore('.').lowercase()
        return TABLE[base] ?: Cfg(DictFormat.CAT_BASE)
    }
}
