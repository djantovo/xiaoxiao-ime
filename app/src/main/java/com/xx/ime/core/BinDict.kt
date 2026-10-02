package com.xx.ime.core

import android.content.Context
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * dict.bin 读取器：整文件 mmap + 二分查找，词库不进堆。
 * 对外三件事：queryT9（完整拼音数字）、queryAb（简码数字）、lianxiang（词首字联想）。
 */
class BinDict(private val ctx: Context) {

    data class Word(val word: String, val weight: Int, val cat: Int)

    private var raf: RandomAccessFile? = null
    private var buf: MappedByteBuffer? = null
    private var fileStamp = 0L
    private val secOff = LongArray(DictFormat.SEC_MAX) { -1L }
    private val secLen = LongArray(DictFormat.SEC_MAX) { 0L }

    @Volatile var ready = false; private set
    @Volatile var meta: String? = null; private set

    /** 分类开关：0 基础 / 1 人名地名 / 2 专业 / 3 容错方言 */
    var enabledCats: BooleanArray = BooleanArray(DictFormat.CAT_COUNT) { true }

    private fun dictFile() = DictImporter.dictFile(ctx)

    fun stamp(): Long {
        val f = dictFile()
        return if (f.exists()) f.lastModified() * 131 + f.length() else 0L
    }

    /** 打开 / 自动重映射（重新导入词库后无需重启输入法） */
    fun open(force: Boolean = false): Boolean {
        val f = dictFile()
        if (!f.exists()) {
            close()
            return false
        }
        val st = stamp()
        if (!force && ready && st == fileStamp) return true
        close()
        return try {
            val r = RandomAccessFile(f, "r")
            val ch = r.channel
            val m = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size())
            m.order(ByteOrder.LITTLE_ENDIAN)
            if (m.get(0) != 'X'.code.toByte() || m.get(1) != 'X'.code.toByte() ||
                m.get(2) != 'D'.code.toByte() || m.get(3) != '1'.code.toByte()
            ) {
                r.close()
                return false
            }
            val nsec = m.getInt(8)
            for (i in 0 until nsec) {
                val base = DictFormat.HEADER_SIZE + i * DictFormat.SECTION_REC_SIZE
                val type = m.getInt(base)
                val off = m.getLong(base + 8)
                val len = m.getLong(base + 16)
                if (type in 0 until DictFormat.SEC_MAX) {
                    secOff[type] = off
                    secLen[type] = len
                }
            }
            raf = r
            buf = m
            fileStamp = st
            meta = sectionText(DictFormat.SEC_META)
            ready = secOff[DictFormat.SEC_T9] >= 0
            true
        } catch (t: Throwable) {
            close()
            false
        }
    }

    fun close() {
        ready = false
        try { raf?.close() } catch (t: Throwable) {
        }
        raf = null
        buf = null
        for (i in secOff.indices) {
            secOff[i] = -1L
            secLen[i] = 0L
        }
    }

    // ---------------- 底层读取 ----------------

    private fun keyCount(sec: Int): Int {
        val o = secOff[sec]
        return if (o < 0 || buf == null) 0 else buf!!.getInt(o.toInt())
    }

    private fun recBase(sec: Int, idx: Int): Int =
        (secOff[sec] + 8 + idx * DictFormat.KEY_REC_SIZE).toInt()

    private fun keyOff(sec: Int, idx: Int) = buf!!.getInt(recBase(sec, idx))

    private fun keyLen(sec: Int, idx: Int) =
        buf!!.getShort(recBase(sec, idx) + 4).toInt() and 0xFFFF

    private fun entStart(sec: Int, idx: Int) = buf!!.getInt(recBase(sec, idx) + 8)

    private fun entCount(sec: Int, idx: Int) = buf!!.getInt(recBase(sec, idx) + 12)

    private fun keyBlobBase(sec: Int, idx: Int): Int =
        (secOff[sec] + 8 + keyCount(sec) * DictFormat.KEY_REC_SIZE + keyOff(sec, idx)).toInt()

    private fun sectionText(sec: Int): String? {
        val o = secOff[sec]
        val m = buf
        if (o < 0 || m == null) return null
        val len = secLen[sec].toInt().coerceAtMost(65536)
        if (len <= 0) return null
        val bytes = ByteArray(len)
        for (i in 0 until len) bytes[i] = m.get(o.toInt() + i)
        return String(bytes, Charsets.UTF_8).trimEnd('\u0000', '\n', ' ')
    }

    private fun cmpKey(sec: Int, idx: Int, target: ByteArray): Int {
        val n = keyLen(sec, idx)
        val base = keyBlobBase(sec, idx)
        val m = buf!!
        val lim = minOf(n, target.size)
        for (i in 0 until lim) {
            val a = m.get(base + i).toInt() and 0xFF
            val b = target[i].toInt() and 0xFF
            if (a != b) return a - b
        }
        return n - target.size
    }

    private fun startsWithKey(sec: Int, idx: Int, target: ByteArray): Boolean {
        val n = keyLen(sec, idx)
        if (n < target.size) return false
        val base = keyBlobBase(sec, idx)
        val m = buf!!
        for (i in target.indices) {
            if ((m.get(base + i).toInt() and 0xFF) != (target[i].toInt() and 0xFF)) return false
        }
        return true
    }

    private fun lowerBound(sec: Int, target: ByteArray): Int {
        var lo = 0
        var hi = keyCount(sec) - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (cmpKey(sec, mid, target) < 0) lo = mid + 1 else hi = mid - 1
        }
        return lo
    }

    private fun readEntry(index: Int): Word? {
        val eo = secOff[DictFormat.SEC_ENTRIES]
        val wo0 = secOff[DictFormat.SEC_WORDS]
        val m = buf ?: return null
        if (eo < 0 || wo0 < 0) return null
        val base = (eo + index.toLong() * DictFormat.ENTRY_SIZE).toInt()
        val wOff = m.getInt(base)
        val wLen = m.getInt(base + 4)
        val weight = m.getInt(base + 8)
        val cat = m.get(base + 12).toInt()
        val wb = (wo0 + wOff).toInt()
        val bytes = ByteArray(wLen)
        for (i in 0 until wLen) bytes[i] = m.get(wb + i)
        return Word(String(bytes, Charsets.UTF_8), weight, cat)
    }

    /** 前缀查询；返回 true 表示已取满 limit */
    private fun queryPrefix(sec: Int, prefix: String, limit: Int, out: ArrayList<Word>): Boolean {
        if (!ready || buf == null || prefix.isEmpty()) return false
        val tb = prefix.toByteArray(Charsets.UTF_8)
        var i = lowerBound(sec, tb)
        val n = keyCount(sec)
        var scannedKeys = 0
        while (i < n && scannedKeys < 512 && startsWithKey(sec, i, tb)) {
            scannedKeys++
            val start = entStart(sec, i)
            val cnt = entCount(sec, i)
            for (j in 0 until cnt) {
                val e = readEntry(start + j) ?: continue
                if (e.cat in enabledCats.indices && !enabledCats[e.cat]) continue
                out.add(e)
                if (out.size >= limit) return true
            }
            i++
        }
        return false
    }

    // ---------------- 对外查询 ----------------

    /** 完整拼音数字串，如 "96636" -> 我们 */
    fun queryT9(digits: String, limit: Int = 40): List<Word> {
        val out = ArrayList<Word>(limit)
        queryPrefix(DictFormat.SEC_T9, digits, limit, out)
        return out
    }

    /** 简码数字串，如 "96" -> 我们 / 为什么 */
    fun queryAb(digits: String, limit: Int = 20): List<Word> {
        val out = ArrayList<Word>(limit)
        queryPrefix(DictFormat.SEC_AB_T9, digits, limit, out)
        return out
    }

    /** 联想：给已上屏的最后一个字 */
    fun lianxiang(lastChar: Char, limit: Int = 10): List<String> {
        if (!ready) return emptyList()
        val out = ArrayList<Word>(limit)
        queryPrefix(DictFormat.SEC_LX, lastChar.toString(), limit, out)
        return out.map { it.word }
    }
}
