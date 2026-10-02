package com.xx.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.xx.ime.core.BinDict
import com.xx.ime.core.ClipboardStore
import com.xx.ime.core.DictFormat
import com.xx.ime.core.DictImporter
import com.xx.ime.core.Prefs
import com.xx.ime.core.T9Engine
import com.xx.ime.handwriting.HandwritingView
import com.xx.ime.handwriting.InkRecognizer
import com.xx.ime.handwriting.Stroke
import com.xx.ime.handwriting.TfliteInkRecognizer
import com.xx.ime.keyboard.K
import com.xx.ime.keyboard.Key
import com.xx.ime.keyboard.KeyboardView
import com.xx.ime.keyboard.Layouts
import com.xx.ime.ui.CandidateBar
import com.xx.ime.ui.ClipboardPanel
import com.xx.ime.ui.EmojiPanel

class XxImeService : InputMethodService(),
    KeyboardView.Listener,
    CandidateBar.Listener,
    EmojiPanel.Listener,
    ClipboardPanel.Listener,
    HandwritingView.Listener {

    private companion object {
        const val PANEL_KB = 0
        const val PANEL_HAND = 1
        const val PANEL_EMOJI = 2
        const val PANEL_CLIP = 3
    }

    private lateinit var prefs: Prefs
    private lateinit var binDict: BinDict
    private lateinit var t9: T9Engine
    private lateinit var clips: ClipboardStore
    private var inkRec: InkRecognizer? = null

    private var candidateBar: CandidateBar? = null
    private var panelHost: FrameLayout? = null
    private var kb: KeyboardView? = null
    private var toolbar: KeyboardView? = null
    private var handView: HandwritingView? = null
    private var handBox: View? = null
    private var emojiPanel: EmojiPanel? = null
    private var clipPanel: ClipboardPanel? = null

    private var layoutType = Prefs.LAYOUT_T9
    private var prevLayout = Prefs.LAYOUT_T9
    private val digits = StringBuilder()
    private var cands: List<String> = emptyList()
    private var lianxiangMode = false
    private var shiftOn = false
    private var capLock = false
    private var lastShiftAt = 0L

    private var dictLoading = false
    private var dictStamp = 0L

    @Volatile private var lastDictError: String? = null

    private val main = Handler(Looper.getMainLooper())

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }

    // ---------------- 生命周期 ----------------

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        clips = ClipboardStore(this)
        binDict = BinDict(this)
        t9 = T9Engine(binDict)
        inkRec = TfliteInkRecognizer(this)
        layoutType = prefs.layout
        binDict.enabledCats[DictFormat.CAT_NAME] = prefs.catName
        binDict.enabledCats[DictFormat.CAT_PRO] = prefs.catPro
        binDict.enabledCats[DictFormat.CAT_FUZZY] = prefs.catFuzzy
        (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.addPrimaryClipChangedListener(clipListener)
        ensureDict()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.removePrimaryClipChangedListener(clipListener)
        } catch (t: Throwable) {
        }
        inkRec?.close()
        binDict.close()
    }

    override fun onCreateInputView(): View {
        val root = LayoutInflater.from(this).inflate(R.layout.ime_main, null)
        candidateBar = root.findViewById(R.id.candidate_bar)
        panelHost = root.findViewById(R.id.panel_host)
        toolbar = root.findViewById(R.id.kb_toolbar)

        candidateBar?.listener = this

        toolbar?.apply {
            listener = this@XxImeService
            layout = Layouts.TOOLBAR
            keyHeightDp = prefs.keyHeightDp
            haptic = prefs.vibrate
            sound = prefs.sound
        }

        // 主键盘
        val mainKb = KeyboardView(this).apply {
            listener = this@XxImeService
            keyHeightDp = prefs.keyHeightDp
            haptic = prefs.vibrate
            sound = prefs.sound
        }
        kb = mainKb
        panelHost?.addView(
            mainKb,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // 手写面板
        val hv = HandwritingView(this).apply { listener = this@XxImeService }
        val hKb = KeyboardView(this).apply {
            listener = this@XxImeService
            layout = Layouts.HAND_BOTTOM
            keyHeightDp = prefs.keyHeightDp
            haptic = prefs.vibrate
            sound = prefs.sound
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        box.addView(
            hv,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (prefs.handPanelHeightDp * resources.displayMetrics.density).toInt()
            )
        )
        box.addView(
            hKb,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        handView = hv
        handBox = box
        panelHost?.addView(
            box,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // emoji / 剪切板
        val ep = EmojiPanel(this, prefs).apply {
            listener = this@XxImeService
            visibility = View.GONE
        }
        val cp = ClipboardPanel(this, clips).apply {
            listener = this@XxImeService
            visibility = View.GONE
        }
        emojiPanel = ep
        clipPanel = cp
        panelHost?.addView(
            ep,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        panelHost?.addView(
            cp,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        applyLayout(layoutType, false)
        ensureDict()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        resetComposing()
        kb?.keyHeightDp = prefs.keyHeightDp
        toolbar?.keyHeightDp = prefs.keyHeightDp
        kb?.haptic = prefs.vibrate
        toolbar?.haptic = prefs.vibrate
        kb?.sound = prefs.sound
        toolbar?.sound = prefs.sound

        // 分类开关可能被设置页改过
        binDict.enabledCats[DictFormat.CAT_NAME] = prefs.catName
        binDict.enabledCats[DictFormat.CAT_PRO] = prefs.catPro
        binDict.enabledCats[DictFormat.CAT_FUZZY] = prefs.catFuzzy

        // 词库被重新导入过？自动重映射，不用重启输入法
        val st = binDict.stamp()
        if (st != dictStamp) {
            binDict.close()
            dictStamp = st
        }
        ensureDict()

        val cls = (info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
        if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) {
            applyLayout(Prefs.LAYOUT_SYM, false)
        } else {
            applyLayout(prefs.layout, false)
        }
        if (inkRec?.available != true) handView?.showHint(getString(R.string.hand_no_model))
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        resetComposing()
        digits.setLength(0)
    }

    /** 不要全屏抽取模式 */
    override fun onEvaluateFullscreenMode(): Boolean = false

    // ---------------- 词库 ----------------

    /**
     * 打开词库；若还没有 dict.bin，就先用内置的 200 条小词库生成一个最小可用词库，
     * 保证没导入之前也能打常用字。
     */
    private fun ensureDict() {
        if (binDict.ready) {
            dictStamp = binDict.stamp()
            return
        }
        if (dictLoading) return
        dictLoading = true
        Thread {
            var ok = binDict.open()
            if (!ok) {
                main.post { candidateBar?.showMessage("正在生成内置基础词库…") }
                val src = listOf(
                    DictImporter.Source("builtin.txt", DictFormat.CAT_BASE, false, 0, 64) {
                        try {
                            assets.open("dict/builtin.txt")
                        } catch (t: Throwable) {
                            null
                        }
                    }
                )
                val opt = DictImporter.Options().apply {
                    targetEntries = 20_000
                    topK = 64
                    hardCap = 20_000
                    abMinWeight = 0
                    lxMinWeight = 0
                }
                var msg = ""
                DictImporter(this).run(src, opt, object : DictImporter.Callback {
                    override fun onStage(stage: String) {}
                    override fun onProgress(percent: Int) {}
                    override fun onDone(o: Boolean, m: String) {
                        msg = m
                    }
                })
                ok = binDict.open(true)
                if (!ok) lastDictError = msg
            }
            dictStamp = binDict.stamp()
            val ready = ok
            main.post {
                dictLoading = false
                candidateBar?.showMessage(
                    if (ready) "词库就绪"
                    else "未导入词库：打开「小小输入法」→ 导入词库（${lastDictError ?: ""}）"
                )
                if (digits.isNotEmpty()) updatePinyin()
            }
        }.start()
    }

    // ---------------- 面板 / 布局 ----------------

    private fun showPanel(which: Int) {
        kb?.visibility = if (which == PANEL_KB) View.VISIBLE else View.GONE
        handBox?.visibility = if (which == PANEL_HAND) View.VISIBLE else View.GONE
        emojiPanel?.visibility = if (which == PANEL_EMOJI) View.VISIBLE else View.GONE
        clipPanel?.visibility = if (which == PANEL_CLIP) View.VISIBLE else View.GONE
        if (which == PANEL_CLIP) clipPanel?.refresh()
    }

    private fun applyLayout(type: Int, rememberPrev: Boolean = true) {
        if (rememberPrev && type != Prefs.LAYOUT_SYM) prevLayout = type
        layoutType = type
        prefs.layout = type
        when (type) {
            Prefs.LAYOUT_T9 -> {
                kb?.layout = Layouts.T9
                showPanel(PANEL_KB)
            }

            Prefs.LAYOUT_EN -> {
                kb?.layout = Layouts.EN
                showPanel(PANEL_KB)
            }

            Prefs.LAYOUT_SYM -> {
                kb?.layout = Layouts.SYM
                showPanel(PANEL_KB)
            }

            Prefs.LAYOUT_HAND -> {
                showPanel(PANEL_HAND)
                handView?.clear()
            }
        }
        kb?.shiftOn = type == Prefs.LAYOUT_EN && shiftOn
        resetComposing()
    }

    private fun cycleKeyboard() {
        val next = when (layoutType) {
            Prefs.LAYOUT_T9 -> Prefs.LAYOUT_EN
            Prefs.LAYOUT_EN -> Prefs.LAYOUT_HAND
            else -> Prefs.LAYOUT_T9
        }
        prevLayout = next
        applyLayout(next, false)
    }

    private fun switchCnEn() {
        if (layoutType == Prefs.LAYOUT_T9) applyLayout(Prefs.LAYOUT_EN, false)
        else applyLayout(Prefs.LAYOUT_T9, false)
    }

    private fun openSettings() {
        try {
            startActivity(
                Intent(this, SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (t: Throwable) {
        }
    }

    // ---------------- 按键 ----------------

    override fun onKey(key: Key) {
        when (val code = key.code) {
            K.EMOJI -> {
                if (emojiPanel?.visibility == View.VISIBLE) showPanel(PANEL_KB)
                else showPanel(PANEL_EMOJI)
            }

            K.MODE_CYCLE -> cycleKeyboard()
            K.SELECT_ALL -> doSelectAll()
            K.COPY -> doCopy()
            K.PASTE -> doPaste()
            K.CLIPBOARD -> {
                if (clipPanel?.visibility == View.VISIBLE) showPanel(PANEL_KB)
                else showPanel(PANEL_CLIP)
            }

            K.MODE_SWITCH -> switchCnEn()
            K.TO_SYM -> {
                if (layoutType == Prefs.LAYOUT_SYM) applyLayout(prevLayout, false)
                else applyLayout(Prefs.LAYOUT_SYM)
            }

            K.BACK_KB -> applyLayout(prevLayout, false)
            K.SHIFT -> onShift()
            K.DEL -> onDelete()
            K.SPACE -> onSpace()
            K.ENTER -> onEnter()
            K.CLEAR -> handView?.clear()
            K.UNDO -> handView?.undo()
            else -> if (code > 0) onChar(code)
        }
    }

    override fun onKeyLongPress(key: Key): Boolean {
        return when (key.code) {
            K.MODE_CYCLE -> {
                try {
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showInputMethodPicker()
                } catch (t: Throwable) {
                }
                true
            }

            K.SHIFT -> {
                capLock = !capLock
                shiftOn = capLock
                kb?.shiftOn = shiftOn
                true
            }

            K.CLIPBOARD -> {
                clips.clear()
                clipPanel?.refresh()
                true
            }

            K.EMOJI -> {
                openSettings()
                true
            }

            else -> false
        }
    }

    private fun onShift() {
        val now = System.currentTimeMillis()
        if (shiftOn && now - lastShiftAt < 400) {
            capLock = true
            shiftOn = true
        } else {
            shiftOn = !shiftOn
            capLock = false
        }
        lastShiftAt = now
        kb?.shiftOn = shiftOn
    }

    private fun onChar(code: Int) {
        when (layoutType) {
            Prefs.LAYOUT_T9 -> {
                if (code in '0'.code..'9'.code) {
                    digits.append(code.toChar())
                    updatePinyin()
                } else {
                    commitRaw(String(Character.toChars(code)))
                }
            }

            Prefs.LAYOUT_EN -> {
                val base = code.toChar()
                val ch = if (shiftOn || capLock) base.uppercaseChar() else base
                commitRaw(ch.toString())
                if (shiftOn && !capLock) {
                    shiftOn = false
                    kb?.shiftOn = false
                }
            }

            else -> commitRaw(String(Character.toChars(code)))
        }
    }

    private fun onDelete() {
        val ic = currentInputConnection
        if (digits.isNotEmpty()) {
            digits.setLength(digits.length - 1)
            updatePinyin()
            return
        }
        if (lianxiangMode) {
            resetComposing()
            return
        }
        if (handBox?.visibility == View.VISIBLE) {
            handView?.undo()
            return
        }
        ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
    }

    private fun onSpace() {
        if (cands.isNotEmpty()) commitCandidate(cands[0])
        else commitRaw(" ")
    }

    private fun onEnter() {
        if (cands.isNotEmpty()) {
            commitCandidate(cands[0])
            return
        }
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    // ---------------- 拼音候选 ----------------

    private fun updatePinyin() {
        if (digits.isEmpty()) {
            resetComposing()
            return
        }
        lianxiangMode = false
        if (!binDict.ready) {
            ensureDict()
            candidateBar?.showMessage("未导入词库：打开「小小输入法」→ 导入词库")
            return
        }
        val list = t9.candidates(digits.toString(), 40)
        cands = list
        if (list.isEmpty()) candidateBar?.showMessage(digits.toString())
        else candidateBar?.setCandidates(list)
    }

    private fun resetComposing() {
        digits.setLength(0)
        cands = emptyList()
        lianxiangMode = false
        candidateBar?.setCandidates(emptyList())
        candidateBar?.showMessage(
            when {
                layoutType == Prefs.LAYOUT_T9 && !binDict.ready ->
                    "未导入词库：打开「小小输入法」→ 导入词库"

                layoutType == Prefs.LAYOUT_T9 -> getString(R.string.subtype_pinyin9)
                layoutType == Prefs.LAYOUT_EN -> getString(R.string.subtype_en)
                layoutType == Prefs.LAYOUT_HAND -> getString(R.string.subtype_hand)
                else -> ""
            }
        )
    }

    override fun onCandidate(text: String) {
        commitCandidate(text)
    }

    private fun commitCandidate(text: String) {
        currentInputConnection?.commitText(text, 1)
        digits.setLength(0)
        val last = text.lastOrNull()
        if (layoutType == Prefs.LAYOUT_T9 && last != null) showLianxiang(last)
        else resetComposing()
    }

    private fun showLianxiang(ch: Char) {
        val list = if (binDict.ready) binDict.lianxiang(ch, 8) else emptyList()
        if (list.isEmpty()) {
            resetComposing()
            return
        }
        lianxiangMode = true
        cands = list
        candidateBar?.setCandidates(list)
    }

    /** 直接上屏（标点、字母、空格、emoji、剪切板内容），并清掉拼音状态 */
    private fun commitRaw(text: String) {
        currentInputConnection?.commitText(text, 1)
        if (digits.isNotEmpty()) digits.setLength(0)
        if (lianxiangMode) resetComposing()
    }

    // ---------------- 工具栏动作 ----------------

    private fun doSelectAll() {
        val ic = currentInputConnection ?: return
        try {
            ic.performContextMenuAction(android.R.id.selectAll)
        } catch (t: Throwable) {
        }
    }

    private fun doCopy() {
        val ic = currentInputConnection ?: return
        try {
            if (ic.performContextMenuAction(android.R.id.copy)) return
        } catch (t: Throwable) {
        }
        val sel = ic.getSelectedText(0)?.toString()
        if (!sel.isNullOrEmpty()) {
            (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.setPrimaryClip(ClipData.newPlainText("xx_ime", sel))
        }
    }

    private fun doPaste() {
        val ic = currentInputConnection ?: return
        try {
            if (ic.performContextMenuAction(android.R.id.paste)) return
        } catch (t: Throwable) {
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = cm?.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(this)?.toString()
        if (!text.isNullOrEmpty()) ic.commitText(text, 1)
    }

    // ---------------- 剪切板监听 ----------------

    private fun captureClip() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            if (!cm.hasPrimaryClip()) return
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
            if (text.isEmpty()) return
            if (clips.add(text)) {
                main.post { if (clipPanel?.visibility == View.VISIBLE) clipPanel?.refresh() }
            }
        } catch (t: Throwable) {
        }
    }

    // ---------------- 面板回调 ----------------

    override fun onEmoji(e: String) {
        commitRaw(e)
    }

    override fun onInsert(text: String) {
        commitRaw(text)
    }

    override fun onClosePanel() {
        showPanel(PANEL_KB)
    }

    // ---------------- 手写 ----------------

    override fun onInk(strokes: List<Stroke>) {
        val rec = inkRec
        if (rec == null || !rec.available) {
            handView?.showHint(getString(R.string.hand_no_model))
            return
        }
        Thread {
            val res = rec.recognize(strokes, 12)
            main.post {
                if (res.isEmpty()) return@post
                cands = res
                lianxiangMode = false
                candidateBar?.setCandidates(res)
            }
        }.start()
    }
}
