package com.xx.ime.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

data class ClipItem(
    val id: Long,
    val preview: String,
    val length: Int,
    val time: Long,
    val pinned: Boolean
)

/**
 * 剪切板存储。上限 300 条（置顶的不算在裁剪范围内）。
 *
 * “无字数限制”的实现要点：
 *   正文用 TEXT 存全量，但列表查询只读 substr(text,1,200) 做预览，
 *   避开 Android CursorWindow 单行 2MB 限制导致的读取异常；
 *   全文在用户点击某条时按 id 单独取。
 */
class ClipboardStore(ctx: Context) {

    companion object {
        const val MAX = 300
        private const val PREVIEW_LEN = 200
    }

    private val helper = object : SQLiteOpenHelper(ctx.applicationContext, "clips.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE clips(" +
                        "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "text TEXT NOT NULL," +
                        "hash TEXT NOT NULL," +
                        "time INTEGER NOT NULL," +
                        "pinned INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL("CREATE INDEX idx_clip_hash ON clips(hash)")
            db.execSQL("CREATE INDEX idx_clip_time ON clips(time)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
        }
    }

    private fun md5(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(d.size * 2)
        for (b in d) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    /** 新增/去重上移；返回是否真的新增 */
    fun add(text: String): Boolean {
        if (text.isEmpty()) return false
        val db = helper.writableDatabase
        val h = md5(text)
        val existed = db.rawQuery("SELECT _id FROM clips WHERE hash=? LIMIT 1", arrayOf(h)).use {
            it.moveToFirst()
        }
        val cv = ContentValues().apply {
            put("text", text)
            put("hash", h)
            put("time", System.currentTimeMillis())
            put("pinned", 0)
        }
        if (existed) {
            db.delete("clips", "hash=?", arrayOf(h))
            db.insert("clips", null, cv)
        } else {
            db.insert("clips", null, cv)
            trim(db)
        }
        return !existed
    }

    private fun trim(db: SQLiteDatabase) {
        db.execSQL(
            "DELETE FROM clips WHERE pinned=0 AND _id NOT IN (" +
                    "SELECT _id FROM clips WHERE pinned=0 ORDER BY time DESC LIMIT $MAX)"
        )
    }

    fun list(limit: Int = MAX): List<ClipItem> {
        val sql = "SELECT _id, substr(text,1,$PREVIEW_LEN) AS preview, length(text) AS len, time, pinned " +
                "FROM clips ORDER BY pinned DESC, time DESC LIMIT $limit"
        val out = ArrayList<ClipItem>()
        helper.readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                out.add(
                    ClipItem(
                        c.getLong(0),
                        c.getString(1) ?: "",
                        c.getInt(2),
                        c.getLong(3),
                        c.getInt(4) == 1
                    )
                )
            }
        }
        return out
    }

    fun fullText(id: Long): String? {
        helper.readableDatabase.rawQuery("SELECT text FROM clips WHERE _id=?", arrayOf(id.toString()))
            .use { c ->
                if (c.moveToFirst()) return c.getString(0)
            }
        return null
    }

    fun setPinned(id: Long, pinned: Boolean) {
        val cv = ContentValues().apply { put("pinned", if (pinned) 1 else 0) }
        helper.writableDatabase.update("clips", cv, "_id=?", arrayOf(id.toString()))
    }

    fun delete(id: Long) {
        helper.writableDatabase.delete("clips", "_id=?", arrayOf(id.toString()))
    }

    fun clear() {
        helper.writableDatabase.delete("clips", null, null)
    }

    fun count(): Int {
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM clips", null).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 0
    }
}
