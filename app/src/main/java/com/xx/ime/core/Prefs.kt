package com.xx.ime.core

import android.content.Context

class Prefs(ctx: Context) {

    companion object {
        const val LAYOUT_T9 = 0
        const val LAYOUT_EN = 1
        const val LAYOUT_HAND = 2
        const val LAYOUT_SYM = 3
    }

    private val sp = ctx.applicationContext.getSharedPreferences("xx_ime", Context.MODE_PRIVATE)

    var layout: Int
        get() = sp.getInt("layout", LAYOUT_T9)
        set(v) = sp.edit().putInt("layout", v).apply()

    var vibrate: Boolean
        get() = sp.getBoolean("vibrate", true)
        set(v) = sp.edit().putBoolean("vibrate", v).apply()

    var sound: Boolean
        get() = sp.getBoolean("sound", false)
        set(v) = sp.edit().putBoolean("sound", v).apply()

    var keyHeightDp: Int
        get() = sp.getInt("key_h", 52)
        set(v) = sp.edit().putInt("key_h", v).apply()

    var handPanelHeightDp: Int
        get() = sp.getInt("hand_h", 200)
        set(v) = sp.edit().putInt("hand_h", v).apply()

    /** 候选分类开关 */
    var catName: Boolean
        get() = sp.getBoolean("cat_name", true)
        set(v) = sp.edit().putBoolean("cat_name", v).apply()

    var catPro: Boolean
        get() = sp.getBoolean("cat_pro", true)
        set(v) = sp.edit().putBoolean("cat_pro", v).apply()

    var catFuzzy: Boolean
        get() = sp.getBoolean("cat_fuzzy", true)
        set(v) = sp.edit().putBoolean("cat_fuzzy", v).apply()

    /** 最近使用 emoji，空格分隔 */
    var emojiRecent: String
        get() = sp.getString("emoji_recent", "") ?: ""
        set(v) = sp.edit().putString("emoji_recent", v).apply()
}
