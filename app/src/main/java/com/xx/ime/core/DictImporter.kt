package com.xx.ime.core

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.HashMap

/**
 * 运行时词库导入。
 *
 * 第 1 遍：只统计权重分布，自动选出裁剪阈值（把总量压到 Options.targetEntries 以内）
 * 第 2 遍：按阈值过滤 + 每个 key 在线 top-K，直接写成 dict.bin
 *
 * 峰值内存被 targetEntries 约束（默认 25 万条 ≈ 30MB 堆），不会把 300 万条塞进内存。
 */
class DictImporter(private val ctx: Context) {

    class Source(
        val name: String,
        val cat: Int,
        val weightless: Boolean,
        val minWeight: Int,
        val topK: Int,
        val open: () -> InputStream?
    )

    interface Callback {
        fun onStage(stage: String)
        fun onProgress(percent: Int)
        fun onDone(ok: Boolean, message: String)
    }

    class Options(
        /** 目标条目数（所有索引合计）；越小越快越省内存 */
        var targetEntries: Int = 250_000,
        /** 每个 key 保留条数 */
        var topK: Int = 24,
        /** 硬上限，超过就停止接收新条目 */
        var hardCap: Int = 600_000,
        /** 自动生成简码的最小权重（简码只对常用词有意义） */
        var abMinWeight: Int = 3000,
        /** 联想词最低权重 */
        var lxMinWeight: Int = 2000,
        var lxTopK: Int = 16
    )

    private class Rec(val word: String, val weight: Int, val cat: Int)

    private class Parsed(
        val word: String,
        val weight: Int,
        val cat: Int,
        val t9Key: String,
        val abKey: String?
    )

    @Volatile private var cancelled = false

    fun cancel() { cancelled = true }

    companion object {
        private val THRESHOLDS = intArrayOf(
            0, 50, 100, 200, 300, 500, 800, 1200, 2000, 3500, 6000,
            10000, 20000, 40000, 80000, 150000
        )
        private val HEADERS = arrayOf(
            "---", "...", "name:", "version:", "sort:", "use_preset",
            "columns:", "import_tables", "vocabulary:", "encoder:"
        )

        fun dictFile(ctx: Context) = File(ctx.filesDir, "dict.bin")
    }

    // ---------------- 入口 ----------------

    fun run(sources: List<Source>, opt: Options, cb: Callback) {
        cancelled = false
        val t0 = System.currentTimeMillis()
        try {
            // ---------- 第 1 遍：权重分布 ----------
            cb.onStage("第 1/2 遍：统计词库规模…")
            cb.onProgress(0)
            val counts = IntArray(THRESHOLDS.size)
            var single = 0
            var parsedCount = 0
            val total = sources.size.coerceAtLeast(1)
            for ((si, src) in sources.withIndex()) {
                if (cancelled) { cb.onDone(false, "已取消"); return }
                val ins = src.open() ?: continue
                ins.bufferedReader(Charsets.UTF_8).useLines { seq ->
                    var idx = 0
                    for (line in seq) {
                        idx++
                        val p = parseLine(line, idx, src) ?: continue
                        parsedCount++
                        if (p.word.length == 1) {
                            single++
                            continue
                        }
                        for (i in THRESHOLDS.indices) {
                            if (p.weight >= THRESHOLDS[i]) counts[i]++
                        }
                    }
                }
                cb.onProgress(((si + 1) * 45) / total)
            }
            if (parsedCount == 0) {
                cb.onDone(false, "没有解析到任何词条，请检查词库格式")
                return
            }

            val budget = (opt.targetEntries - single).coerceAtLeast(20_000)
            var threshold = THRESHOLDS[THRESHOLDS.size - 1]
            for (i in THRESHOLDS.indices) {
                if (counts[i] <= budget) {
                    threshold = THRESHOLDS[i]
                    break
                }
            }
            cb.onStage("解析 $parsedCount 条，单字 $single 条，裁剪阈值 $threshold")

            // ---------- 第 2 遍：在线 top-K ----------
            val t9 = HashMap<String, ArrayList<Rec>>(1 shl 17)
            val ab = HashMap<String, ArrayList<Rec>>(1 shl 15)
            val lx = HashMap<String, ArrayList<Rec>>(1 shl 14)
            val wordPool = HashMap<String, String>(1 shl 17)
            var kept = 0
            var truncated = false

            for ((si, src) in sources.withIndex()) {
                if (cancelled) { cb.onDone(false, "已取消"); return }
                if (truncated) break
                val ins = src.open() ?: continue
                ins.bufferedReader(Charsets.UTF_8).useLines { seq ->
                    var idx = 0
                    for (line in seq) {
                        idx++
                        val p = parseLine(line, idx, src) ?: continue
                        val isSingle = p.word.length == 1
                        if (!src.weightless && !isSingle && p.weight < threshold) continue
                        if (p.weight < src.minWeight) continue
                        if (kept >= opt.hardCap) {
                            truncated = true
                            break
                        }
                        kept++
                        val word = wordPool.getOrPut(p.word) { p.word }
                        val rec = Rec(word, p.weight, p.cat)
                        addTop(t9, p.t9Key, rec, opt.topK)
                        if (p.abKey != null) addTop(ab, p.abKey, rec, minOf(src.topK, 32))
                        if (p.word.length in 2..8 && p.weight >= opt.lxMinWeight) {
                            val first = p.word[0]
                            if (first.code <= 0xFFFF) addTop(lx, first.toString(), rec, opt.lxTopK)
                        }
                    }
                }
                cb.onProgress(45 + ((si + 1) * 35) / total)
            }

            // ---------- 序列化 ----------
            cb.onStage("写入 dict.bin…")
            val writer = BinWriter()
            writer.buildIndex(DictFormat.SEC_T9, t9)
            cb.onProgress(84)
            writer.buildIndex(DictFormat.SEC_AB_T9, ab)
            cb.onProgress(90)
            writer.buildIndex(DictFormat.SEC_LX, lx)
            cb.onProgress(94)

            val meta = buildString {
                append("{\n")
                append(" \"version\": ").append(DictFormat.VERSION).append(",\n")
                append(" \"built\": ").append(System.currentTimeMillis()).append(",\n")
                append(" \"entries\": ").append(kept).append(",\n")
                append(" \"threshold\": ").append(threshold).append(",\n")
                append(" \"t9Keys\": ").append(t9.size).append(",\n")
                append(" \"abKeys\": ").append(ab.size).append(",\n")
                append(" \"lxKeys\": ").append(lx.size).append(",\n")
                append(" \"truncated\": ").append(truncated).append("\n}")
            }

            val tmp = File(ctx.filesDir, "dict.bin.tmp")
            if (tmp.exists()) tmp.delete()
            tmp.outputStream().buffered(1 shl 16).use { os -> writer.writeTo(os, meta) }

            t9.clear()
            ab.clear()
            lx.clear()
            wordPool.clear()

            if (tmp.length() < 4096L) {
                val n = tmp.length()
                tmp.delete()
                cb.onDone(false, "生成的文件异常（$n 字节）")
                return
            }
            val out = dictFile(ctx)
            if (out.exists()) out.delete()
            if (!tmp.renameTo(out)) {
                tmp.copyTo(out, true)
                tmp.delete()
            }

            cb.onProgress(100)
            val mb = out.length() / 1048576.0
            val secs = (System.currentTimeMillis() - t0) / 1000
            cb.onDone(
                true,
                "导入完成：%d 条词条，%.1f MB，耗时 %d 秒%s".format(
                    kept, mb, secs, if (truncated) "（已达硬上限，词库被截断）" else ""
                )
            )
        } catch (t: Throwable) {
            cb.onDone(false, "导入失败：${t.message ?: t.toString()}")
        }
    }

    // ---------------- 解析 ----------------

    private fun looksLikeWord(s: String): Boolean {
        if (s.isEmpty() || s.length > 32) return false
        var hasCjk = false
        for (c in s) {
            when {
                c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF ||
                        c.code in 0xF900..0xFAFF -> hasCjk = true
                c.isLetterOrDigit() -> {
                }
                c == '\'' || c == '’' || c == '.' || c == '-' || c == '_' ||
                        c == '+' || c == '#' || c == '&' -> {
                }
                else -> return false
            }
        }
        return hasCjk || s.all { it.isLetterOrDigit() }
    }

    private fun parseLine(raw: String, lineIndex: Int, src: Source): Parsed? {
        var line = raw.trim()
        if (line.isEmpty() || line[0] == '#') return null
        for (h in HEADERS) if (line.startsWith(h)) return null
        val hi = line.indexOf(" #")
        if (hi > 0) line = line.substring(0, hi).trim()

        val sep = when {
            line.indexOf('\t') >= 0 -> '\t'
            line.indexOf(',') >= 0 -> ','
            line.indexOf('|') >= 0 -> '|'
            else -> ' '
        }
        val parts = line.split(sep).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return null

        val a = parts[0]
        val b = parts[1]
        val c = if (parts.size > 2) parts[2] else null

        val pyA = PinyinNormalizer.normalize(a)
        val pyB = PinyinNormalizer.normalize(b)
        val word: String
        val rawPy: String
        if (pyA != null && looksLikeWord(b)) {
            word = b
            rawPy = a
        } else if (looksLikeWord(a) && pyB != null) {
            word = a
            rawPy = b
        } else {
            return null
        }
        if (word.length > 32) return null

        val py = PinyinNormalizer.normalize(rawPy) ?: return null

        var w = c?.toIntOrNull() ?: 0
        if (w <= 0 || src.weightless) w = (5000 - minOf(lineIndex, 4999)).coerceAtLeast(1)

        val syl = PinyinNormalizer.syllablesOfRaw(rawPy)
        val abKey = if (syl != null && word.length in 1..4) {
            val initials = buildString { for (s in syl) append(s[0]) }
            Syllables.codeOf(initials)
        } else {
            null
        }

        return Parsed(word, w, src.cat, Syllables.codeOf(py), abKey)
    }

    /** 每 key 只留 top-K（边读边替换，内存 O(总保留条数)） */
    private fun addTop(map: HashMap<String, ArrayList<Rec>>, key: String, e: Rec, k: Int) {
        val list = map.getOrPut(key) { ArrayList(4) }
        for (i in list.indices) {
            if (list[i].word == e.word) {
                if (e.weight > list[i].weight) {
                    list[i] = e
                    list.sortWith(compareByDescending { it.weight })
                }
                return
            }
        }
        if (list.size < k) {
            list.add(e)
            list.sortWith(compareByDescending { it.weight })
        } else if (list[list.size - 1].weight < e.weight) {
            list[list.size - 1] = e
            list.sortWith(compareByDescending { it.weight })
        }
    }

    // ---------------- 二进制写入 ----------------

    private class BinWriter {
        private val entries = ByteArrayOutputStream(1 shl 22)
        private val words = ByteArrayOutputStream(1 shl 20)
        private val wordRef = HashMap<String, IntArray>(1 shl 17)
        private var entryCount = 0
        private val sections = HashMap<Int, ByteArray>(8)

        private fun u16(b: ByteArrayOutputStream, v: Int) {
            b.write(v and 0xFF)
            b.write((v ushr 8) and 0xFF)
        }

        private fun u32(b: ByteArrayOutputStream, v: Int) {
            b.write(v and 0xFF)
            b.write((v ushr 8) and 0xFF)
            b.write((v ushr 16) and 0xFF)
            b.write((v ushr 24) and 0xFF)
        }

        private fun u64(b: ByteArrayOutputStream, v: Long) {
            for (i in 0 until 8) b.write(((v ushr (i * 8)) and 0xFF).toInt())
        }

        private fun wordRefOf(w: String): IntArray = wordRef.getOrPut(w) {
            val bytes = w.toByteArray(Charsets.UTF_8)
            val off = words.size()
            words.write(bytes)
            intArrayOf(off, bytes.size)
        }

        fun buildIndex(type: Int, d: HashMap<String, ArrayList<Rec>>) {
            val keys = ArrayList(d.keys)
            keys.sort()
            val recs = ByteArrayOutputStream(keys.size * DictFormat.KEY_REC_SIZE)
            val keyBlob = ByteArrayOutputStream(keys.size * 8)
            for (k in keys) {
                val list = d[k] ?: continue
                val start = entryCount
                for (r in list) {
                    val wr = wordRefOf(r.word)
                    u32(entries, wr[0])
                    u32(entries, wr[1])
                    u32(entries, r.weight)
                    entries.write(r.cat and 0xFF)
                    entryCount++
                }
                val kb = k.toByteArray(Charsets.UTF_8)
                u32(recs, keyBlob.size())
                u16(recs, kb.size)
                u16(recs, 0)
                u32(recs, start)
                u32(recs, list.size)
                keyBlob.write(kb)
            }
            val sec = ByteArrayOutputStream(8 + recs.size() + keyBlob.size())
            u32(sec, keys.size)
            u32(sec, DictFormat.KEY_REC_SIZE)
            recs.writeTo(sec)
            keyBlob.writeTo(sec)
            sections[type] = sec.toByteArray()
        }

        fun writeTo(os: OutputStream, meta: String) {
            sections[DictFormat.SEC_META] = meta.toByteArray(Charsets.UTF_8)
            sections[DictFormat.SEC_WORDS] = words.toByteArray()
            sections[DictFormat.SEC_ENTRIES] = entries.toByteArray()

            val order = intArrayOf(
                DictFormat.SEC_META, DictFormat.SEC_WORDS, DictFormat.SEC_ENTRIES,
                DictFormat.SEC_T9, DictFormat.SEC_AB_T9, DictFormat.SEC_LX
            )
            val headerLen = DictFormat.HEADER_SIZE + order.size * DictFormat.SECTION_REC_SIZE
            var off = headerLen.toLong()
            val table = ByteArrayOutputStream(order.size * DictFormat.SECTION_REC_SIZE)
            val body = ByteArrayOutputStream(1 shl 20)
            for (t in order) {
                val d = sections[t] ?: ByteArray(0)
                u32(table, t)
                u32(table, 0)
                u64(table, off)
                u64(table, d.size.toLong())
                body.write(d)
                val pad = (8 - d.size % 8) % 8
                repeat(pad) { body.write(0) }
                off += d.size + pad
            }
            val head = ByteArrayOutputStream(DictFormat.HEADER_SIZE + table.size())
            head.write(DictFormat.MAGIC.toByteArray(Charsets.US_ASCII))
            u32(head, DictFormat.VERSION)
            u32(head, order.size)
            table.writeTo(head)
            head.writeTo(os)
            body.writeTo(os)
            os.flush()
        }
    }
}
