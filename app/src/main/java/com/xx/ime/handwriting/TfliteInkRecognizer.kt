package com.xx.ime.handwriting

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * 离线手写汉字识别（TFLite）。
 *
 * 约定：
 *   模型：assets/model/ink.tflite
 *   标签：assets/model/ink_labels.txt（一行一个字符，行号 = 输出索引）
 *   输入：[1, seqLen, dim]，dim = 3 或 4（见 InkPreprocess）
 *   输出：[1, N] 已 softmax 的概率
 *
 * 没有模型时 available = false，面板会给出提示，不影响其它功能。
 */
class TfliteInkRecognizer(private val ctx: Context) : InkRecognizer {

    private var interp: Interpreter? = null
    private var labels: List<String> = emptyList()
    private var seqLen = 128
    private var dim = 3
    private var outSize = 0

    override val name: String get() = "TFLite"
    override val available: Boolean get() = interp != null && labels.isNotEmpty()

    init {
        tryLoad()
    }

    private fun tryLoad() {
        try {
            val model = loadModelBuffer("model/ink.tflite") ?: return
            val it = Interpreter(model, Interpreter.Options().setNumThreads(2))

            val inShape = it.getInputTensor(0).shape()   // [1, seq, dim]
            if (inShape.size < 3) return
            seqLen = inShape[inShape.size - 2]
            dim = inShape[inShape.size - 1]

            val outShape = it.getOutputTensor(0).shape()
            outSize = outShape[outShape.size - 1]

            val raw = readAssetText("model/ink_labels.txt") ?: return
            labels = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (labels.isEmpty() || outSize < 1) return

            interp = it
        } catch (t: Throwable) {
            interp = null
        }
    }

    private fun loadModelBuffer(path: String): ByteBuffer? {
        // 走 FileChannel 映射，内存友好（build.gradle 已对 tflite 关闭压缩）
        try {
            ctx.assets.openFd(path).use { fd ->
                FileInputStream(fd.fileDescriptor).use { fis ->
                    val buf = fis.channel.map(
                        FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength
                    )
                    return buf.order(ByteOrder.nativeOrder())
                }
            }
        } catch (t: Throwable) {
            val bytes = readAssetBytes(path) ?: return null
            return ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        }
    }

    private fun readAssetBytes(path: String): ByteArray? =
        try {
            ctx.assets.open(path).use { it.readBytes() }
        } catch (t: Throwable) {
            null
        }

    private fun readAssetText(path: String): String? =
        try {
            ctx.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (t: Throwable) {
            null
        }

    override fun recognize(strokes: List<Stroke>, maxResults: Int): List<String> {
        val it = interp ?: return emptyList()
        if (strokes.isEmpty()) return emptyList()
        return try {
            val feat = InkPreprocess.features(strokes, seqLen, dim)
            val input = arrayOf(feat)
            val out = arrayOf(FloatArray(outSize))
            it.run(input, out)
            val scores = out[0]
            scores.indices
                .sortedByDescending { scores[it] }
                .take(maxResults)
                .mapNotNull { idx -> labels.getOrNull(idx) }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    override fun close() {
        try {
            interp?.close()
        } catch (t: Throwable) {
        }
        interp = null
    }
}
