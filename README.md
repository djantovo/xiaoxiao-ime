# 小小输入法 (com.xx.ime)

完全离线的 Android 输入法：九键拼音 + 英文 26 键 + 手写。无网络、无语音。

## 功能

- 拼音九键（融合布局）：数字串 → 音节切分 → 词库匹配 → 候选词
  - 打 `96` 出「我们 / 文明 / 无奈」（音节首字母简码）
  - 打 `96636` 出「我们」（完整拼音）
  - 上屏后自动出末字联想词
- 英文 26 键：Shift 单击大小写、双击锁大写（长按 Shift 也可锁定）
- 手写键盘：笔迹采集 + 离线 TFLite 识别（需自备模型），支持清空/撤销
- 固定工具栏：😀 emoji / 键盘切换 / 全选 / 复制 / 粘贴 / 剪切板
  - 「键盘」单击循环切换三种键盘，长按调系统输入法选择器
  - 「剪切板」长按清空记录，长按 😀 打开设置页
- 剪切板：上限 300 条（置顶不受裁剪影响）、正文不限字数、列表只读预览、点击插入全文、每条可分词
- 完全离线，无任何网络权限

## 词库：运行时导入（不进 APK）

APK 里只带 200 条兜底词库，正式词库在手机上导入：

1. 把 rime 词库目录（`*.dict.yaml`）放到手机里，如 `/storage/emulated/0/TT2/mcp/dicts`
2. 打开「小小输入法」→ 「导入词库（选 dicts 文件夹）」
3. 第 1 遍统计规模、第 2 遍建索引，约 30~60 秒，产出 `filesDir/dict.bin`（约 8~15MB）
4. 回到输入法直接用，重新导入无需重启（自动检测并重映射）

支持的格式：rime `词\t拼音\t权重`、`词\t拼音`、搜狗 `拼音,词,权重`、`词,拼音`，带声调拼音会自动去调（`ā bà` → `aba`）。

裁剪默认值：目标 25 万条、每 key 24 条、联想词权重 ≥2000。
想更全就改 `DictImporter.Options.targetEntries`（如 600000，需 largeHeap）。

## 构建

推送到 GitHub，Actions 自动出 APK：

- `xiaoxiao-ime-debug`：已用调试签名，可直接安装
- `xiaoxiao-ime-release-unsigned`：需要自行签名

本地构建：Android Studio 打开此目录即可（Gradle 8.7 / AGP 8.5.2 / JDK 17）。

## 安装后必做

1. 打开 App → 「启用输入法（系统设置）」→ 勾选「小小输入法」
2. 任意输入框长按 → 选择输入法 → 小小输入法
3. 导入词库（见上）
4. 手写：见 `app/src/main/assets/model/README.md`

## 代码结构

```
app/src/main/java/com/xx/ime/
├── XxImeService.kt          IME 主服务：按键分发、候选、面板、剪切板监听
├── SettingsActivity.kt      设置页：启用输入法、导入词库、分类开关
├── core/
│   ├── Syllables.kt         410 音节表 + T9 映射
│   ├── PinyinNormalizer.kt  带声调拼音归一化 + 音节切分
│   ├── DictFormat.kt        dict.bin 格式常量
│   ├── DictFiles.kt         每个词库文件的导入配置
│   ├── DictImporter.kt      两遍扫描 + 在线 top-K + 写 dict.bin
│   ├── BinDict.kt           mmap 读取器：二分查找、分类过滤
│   ├── T9Engine.kt          九键引擎：简码 + 完整拼音合并排序
│   ├── ClipboardStore.kt    剪切板 SQLite（300 条、预览/全文分离）
│   ├── Segmenter.kt         ICU 分词
│   ├── Prefs.kt             偏好设置
│   └── EmojiRepo.kt         离线 emoji
├── keyboard/                自绘键盘 + 全部布局数据
├── ui/                      候选栏 / emoji 面板 / 剪切板面板
└── handwriting/             笔迹采集 + TFLite 推理
```

## dict.bin 格式

```
[Header] magic "XXD1" + version + section 表（每段 24B）
[Section]
  0 META      构建信息（文本）
  1 WORDS     去重词条 UTF-8 池
  2 ENTRIES   条目池，每条 13B：u32 wordOff, u32 wordLen, u32 weight, u8 cat
  3~6 保留     PY / T9 / AB / AB_T9 索引
  7 LX        联想索引（key = 词首字）
[KeyTable] u32 count, u32 recSize(16), count×16B 记录, key 字节块
```

## 已知限制

- 剪切板记录：Android 10+ 需把本输入法设为默认输入法，才能读到其它 App 的剪贴板
- 分词：用系统 ICU 的 BreakIterator（离线），专有名词可能切错
- 手写：模型需自备（本仓库不包含训练好的二进制模型）
- 九键前缀查询最多扫 512 个 key，单键候选的长尾不如简码表全（刻意取舍）
