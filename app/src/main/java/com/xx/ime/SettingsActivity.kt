package com.xx.ime

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.xx.ime.core.BinDict
import com.xx.ime.core.ClipboardStore
import com.xx.ime.core.DictFiles
import com.xx.ime.core.DictImporter
import com.xx.ime.core.Prefs

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var pb: ProgressBar
    private lateinit var tvStage: TextView
    private val main = Handler(Looper.getMainLooper())

    private var importing = false
    private var importer: DictImporter? = null

    private val dirLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (t: Throwable) {
                }
                startImport(sourcesFromTree(uri))
            }
        }

    private val filesLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) startImport(sourcesFromUris(uris))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = Prefs(this)
        status = findViewById(R.id.tv_status)
        pb = findViewById(R.id.pb_import)
        tvStage = findViewById(R.id.tv_status)

        findViewById<Button>(R.id.btn_enable).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            } catch (t: Throwable) {
                toast("请到系统设置里手动启用")
            }
        }

        findViewById<Button>(R.id.btn_picker).setOnClickListener {
            try {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            } catch (t: Throwable) {
            }
        }

        val swVib = findViewById<Switch>(R.id.sw_vibrate)
        swVib.isChecked = prefs.vibrate
        swVib.setOnCheckedChangeListener { _, v -> prefs.vibrate = v }

        val swSnd = findViewById<Switch>(R.id.sw_sound)
        swSnd.isChecked = prefs.sound
        swSnd.setOnCheckedChangeListener { _, v -> prefs.sound = v }

        val tvKeyH = findViewById<TextView>(R.id.tv_keyh)
        val sb = findViewById<SeekBar>(R.id.sb_keyh)
        sb.progress = (prefs.keyHeightDp - 36).coerceIn(0, 30)
        tvKeyH.text = "按键高度：${prefs.keyHeightDp}dp"
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar?, p: Int, fromUser: Boolean) {
                val v = 36 + p
                tvKeyH.text = "按键高度：${v}dp"
                prefs.keyHeightDp = v
            }

            override fun onStartTrackingTouch(seek: SeekBar?) {}
            override fun onStopTrackingTouch(seek: SeekBar?) {}
        })

        val swName = findViewById<Switch>(R.id.sw_cat_name)
        swName.isChecked = prefs.catName
        swName.setOnCheckedChangeListener { _, v -> prefs.catName = v }

        val swPro = findViewById<Switch>(R.id.sw_cat_pro)
        swPro.isChecked = prefs.catPro
        swPro.setOnCheckedChangeListener { _, v -> prefs.catPro = v }

        val swFuzzy = findViewById<Switch>(R.id.sw_cat_fuzzy)
        swFuzzy.isChecked = prefs.catFuzzy
        swFuzzy.setOnCheckedChangeListener { _, v -> prefs.catFuzzy = v }

        findViewById<Button>(R.id.btn_import_dir).setOnClickListener {
            dirLauncher.launch(null)
        }

        findViewById<Button>(R.id.btn_import_files).setOnClickListener {
            filesLauncher.launch(arrayOf("*/*"))
        }

        findViewById<Button>(R.id.btn_dict_delete).setOnClickListener {
            DictImporter.dictFile(this).delete()
            toast("已删除词库，重新进入输入法后生效")
            loadDictAsync()
        }

        loadDictAsync()
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    }

    private fun loadDictAsync() {
        status.text = getString(R.string.dict_loading)
        Thread {
            val bd = BinDict(this)
            val ok = bd.open(true)
            val f = DictImporter.dictFile(this)
            val size = if (f.exists()) f.length() / 1048576.0 else 0.0
            val meta = bd.meta
            bd.close()
            main.post {
                status.text = if (ok)
                    "词库已就绪：%.1f MB\n%s".format(size, meta ?: "")
                else
                    "尚未导入词库（在下面选 dicts 文件夹导入）"
            }
        }.start()
    }

    // ---------------- 导入 ----------------

    private fun sourcesFromTree(uri: Uri): List<DictImporter.Source> {
        val tree = DocumentFile.fromTreeUri(this, uri) ?: return emptyList()
        val res = ArrayList<DictImporter.Source>()
        for (f in tree.listFiles()) {
            if (!f.isFile) continue
            val name = f.name ?: continue
            if (!name.endsWith(".yaml") && !name.endsWith(".yml") && !name.endsWith(".txt")) continue
            val cfg = DictFiles.cfgOf(name)
            val fu = f.uri
            res.add(
                DictImporter.Source(name, cfg.cat, cfg.weightless, cfg.minWeight, cfg.topK) {
                    try {
                        contentResolver.openInputStream(fu)
                    } catch (t: Throwable) {
                        null
                    }
                }
            )
        }
        return res
    }

    private fun sourcesFromUris(uris: List<Uri>): List<DictImporter.Source> {
        val res = ArrayList<DictImporter.Source>()
        for (u in uris) {
            val name = u.lastPathSegment?.substringAfterLast('/') ?: continue
            val cfg = DictFiles.cfgOf(name)
            res.add(
                DictImporter.Source(name, cfg.cat, cfg.weightless, cfg.minWeight, cfg.topK) {
                    try {
                        contentResolver.openInputStream(u)
                    } catch (t: Throwable) {
                        null
                    }
                }
            )
        }
        return res
    }

    private fun startImport(sources: List<DictImporter.Source>) {
        if (importing) {
            toast("正在导入，请稍候")
            return
        }
        if (sources.isEmpty()) {
            toast("没找到 .yaml / .txt 词库文件")
            return
        }
        val free = try {
            StatFs(filesDir.path).availableBytes
        } catch (t: Throwable) {
            Long.MAX_VALUE
        }
        if (free < 200L * 1024 * 1024) {
            toast("存储空间不足 200MB，无法导入")
            return
        }
        importing = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        pb.visibility = View.VISIBLE
        pb.progress = 0
        val imp = DictImporter(this)
        importer = imp
        Thread {
            imp.run(sources, DictImporter.Options(), object : DictImporter.Callback {
                override fun onStage(stage: String) {
                    main.post { tvStage.text = stage }
                }

                override fun onProgress(percent: Int) {
                    main.post { pb.progress = percent }
                }

                override fun onDone(ok: Boolean, message: String) {
                    main.post {
                        importing = false
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        pb.visibility = View.GONE
                        tvStage.text = message
                        toast(message)
                        loadDictAsync()
                    }
                }
            })
        }.start()
    }
}
