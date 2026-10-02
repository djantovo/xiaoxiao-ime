package com.xx.ime.keyboard

object K {
    const val DEL = -1
    const val SPACE = -2
    const val ENTER = -3
    const val SHIFT = -4
    const val TO_SYM = -5
    const val MODE_SWITCH = -6   // 中/英
    const val MODE_CYCLE = -7    // 键盘切换（循环）
    const val CLEAR = -8
    const val UNDO = -9
    const val EMOJI = -10
    const val SELECT_ALL = -11
    const val COPY = -12
    const val PASTE = -13
    const val CLIPBOARD = -14
    const val BACK_KB = -15
    const val IME_PICKER = -16
    const val SETTINGS = -17
}

object KeyStyle {
    const val NORMAL = 0
    const val FUNC = 1
    const val ACCENT = 2
}

/** code > 0 表示要上屏的字符码点；code < 0 是功能键 */
data class Key(
    val label: String,
    val code: Int,
    val sub: String? = null,
    val weight: Float = 1f,
    val style: Int = KeyStyle.NORMAL
)

data class Row(val keys: List<Key>, val height: Float = 1f)

data class KeyboardLayout(val rows: List<Row>, val name: String = "")

object Layouts {

    private fun k(
        label: String, code: Int, sub: String? = null,
        weight: Float = 1f, style: Int = KeyStyle.NORMAL
    ) = Key(label, code, sub, weight, style)

    private fun letters(s: String) = s.map { k(it.toString(), it.code) }

    private fun chars(l: List<String>) = l.map { k(it, it.codePointAt(0)) }

    /** 固定工具栏：emoji / 键盘切换 / 全选 / 复制 / 粘贴 / 剪切板 */
    val TOOLBAR = KeyboardLayout(
        listOf(
            Row(
                listOf(
                    k("😀", K.EMOJI, style = KeyStyle.FUNC),
                    k("键盘", K.MODE_CYCLE, style = KeyStyle.FUNC),
                    k("全选", K.SELECT_ALL, style = KeyStyle.FUNC),
                    k("复制", K.COPY, style = KeyStyle.FUNC),
                    k("粘贴", K.PASTE, style = KeyStyle.FUNC),
                    k("剪切板", K.CLIPBOARD, style = KeyStyle.FUNC)
                ), 0.74f
            )
        ), "toolbar"
    )

    /** 拼音九键 */
    val T9 = KeyboardLayout(
        listOf(
            Row(listOf(
                k("符", K.TO_SYM, sub = "，。", style = KeyStyle.FUNC),
                k("ABC", '2'.code, sub = "2"),
                k("DEF", '3'.code, sub = "3")
            )),
            Row(listOf(
                k("GHI", '4'.code, sub = "4"),
                k("JKL", '5'.code, sub = "5"),
                k("MNO", '6'.code, sub = "6")
            )),
            Row(listOf(
                k("PQRS", '7'.code, sub = "7"),
                k("TUV", '8'.code, sub = "8"),
                k("WXYZ", '9'.code, sub = "9")
            )),
            Row(listOf(
                k("中/英", K.MODE_SWITCH, style = KeyStyle.FUNC),
                k("，", '，'.code),
                k("空格", K.SPACE, weight = 2.4f, style = KeyStyle.ACCENT),
                k("⌫", K.DEL, style = KeyStyle.FUNC),
                k("↵", K.ENTER, style = KeyStyle.FUNC)
            ))
        ), "t9"
    )

    /** 英文 26 键 */
    val EN = KeyboardLayout(
        listOf(
            Row(letters("qwertyuiop")),
            Row(letters("asdfghjkl")),
            Row(
                listOf(k("⇧", K.SHIFT, style = KeyStyle.FUNC)) + letters("zxcvbnm") +
                        listOf(k("⌫", K.DEL, style = KeyStyle.FUNC))
            ),
            Row(listOf(
                k("中/英", K.MODE_SWITCH, style = KeyStyle.FUNC),
                k("符", K.TO_SYM, style = KeyStyle.FUNC),
                k("，", '，'.code),
                k("空格", K.SPACE, weight = 2.6f, style = KeyStyle.ACCENT),
                k("。", '。'.code),
                k("↵", K.ENTER, style = KeyStyle.FUNC)
            ))
        ), "en"
    )

    /** 符号 / 数字 */
    val SYM = KeyboardLayout(
        listOf(
            Row(chars("1234567890".map { it.toString() })),
            Row(chars(listOf("，", "。", "？", "！", "；", "：", "、"))),
            Row(chars(listOf("“", "”", "‘", "’", "（", "）", "《", "》"))),
            Row(chars(listOf("@", "#", "￥", "%", "&", "*", "+", "=", "/", "\\"))),
            Row(listOf(
                k("返回", K.BACK_KB, style = KeyStyle.FUNC),
                k("空格", K.SPACE, weight = 2f, style = KeyStyle.ACCENT),
                k("⌫", K.DEL, style = KeyStyle.FUNC),
                k("↵", K.ENTER, style = KeyStyle.FUNC)
            ))
        ), "sym"
    )

    /** 手写面板底部按键行 */
    val HAND_BOTTOM = KeyboardLayout(
        listOf(
            Row(listOf(
                k("清空", K.CLEAR, style = KeyStyle.FUNC),
                k("撤销", K.UNDO, style = KeyStyle.FUNC),
                k("中/英", K.MODE_SWITCH, style = KeyStyle.FUNC),
                k("空格", K.SPACE, weight = 2.2f, style = KeyStyle.ACCENT),
                k("⌫", K.DEL, style = KeyStyle.FUNC),
                k("↵", K.ENTER, style = KeyStyle.FUNC)
            ))
        ), "hand"
    )
}
