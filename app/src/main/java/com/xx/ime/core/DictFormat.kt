package com.xx.ime.core

/** dict.bin 格式常量（写入端 / 读取端共用） */
object DictFormat {
    const val MAGIC = "XXD1"
    const val VERSION = 1

    const val SEC_META = 0
    const val SEC_WORDS = 1
    const val SEC_ENTRIES = 2
    const val SEC_PY = 3
    const val SEC_T9 = 4
    const val SEC_AB = 5
    const val SEC_AB_T9 = 6
    const val SEC_LX = 7
    const val SEC_MAX = 8

    /** 条目：u32 wordOff, u32 wordLen, u32 weight, u8 cat */
    const val ENTRY_SIZE = 13
    /** 键记录：u32 keyOff, u16 keyLen, u16 pad, u32 entStart, u32 entCount */
    const val KEY_REC_SIZE = 16
    const val HEADER_SIZE = 12
    const val SECTION_REC_SIZE = 24

    const val CAT_BASE = 0
    const val CAT_NAME = 1
    const val CAT_PRO = 2
    const val CAT_FUZZY = 3
    const val CAT_COUNT = 4
}
