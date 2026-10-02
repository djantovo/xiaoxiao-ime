# 手写模型接入

放两个文件到这里就能启用离线手写汉字识别：

- `ink.tflite`     —— 模型本体
- `ink_labels.txt` —— 标签表，一行一个字符，行号 = 模型输出索引

## 输入输出约定（代码已按此实现）

- 输入张量：`[1, seqLen, dim]` float32
  - `seqLen` 任意（按模型 shape 自动读取）
  - `dim = 3`：`[dx/256, dy/256, 抬笔标记]`
  - `dim = 4`：`[dx/256, dy/256, 抬笔标记, 落笔标记]`
- 输出张量：`[1, N]`，softmax 概率，N = 标签数

预处理在 `InkPreprocess.kt`：所有笔画等比缩放到 256x256、按 8px 间距重采样、转增量特征。
这就是 Google digital-ink 的规范，自己训练时按同样方式做特征即可。

## 模型来源建议

1. 中文单字手写（推荐）：用 CASIA-HWDB 数据集训练 3755 类单字分类网络
   （DenseNet / MobileNetV3 小模型即可），导出 TFLite。
   - 笔画序列输入：按上面规范做特征，模型用 1D CNN / LSTM / Transformer
   - 图像输入：把笔画渲染成 64x64 灰度图，模型改成图像分类（此时要改 InkPreprocess）
2. Google digital-ink（TF Hub 有 tflite 版）：英文/数字很准，中文覆盖差，可作英文手写用。
3. 任何 TFLite 模型只要满足上面的输入输出约定，直接丢进来就能用，不用改代码。

## 没有模型时

手写面板仍然可用（当笔迹板），只是不出候选字，会提示
「未检测到手写模型，请把 ink.tflite 放入 assets/model/」。
