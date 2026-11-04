# Flair 导入与循环播放修正

## 状态

- 已修正自定义舞蹈循环时错误进入淡出、最终回到直立姿态的问题。
- 原生 Flair 姿态的视觉验收仍待上游/用户确认；本文不宣称视觉已通过。

## 既有导入修正

- `DanceImport` 的 DAE 路径不施加艺术性关节 clamp，只保留有限值/单位四元数、固定 16 关节、严格父级与同步 LINEAR 通道校验；不复制 root 位移。
- 参考导入结果为 canonical SHA-256 `1a8b109d684d5950c6e29b79c3fe0440adae4faae9b70263d0b09a1288f9b7d3`，31 帧，源动作 `duration=1s`，`blend_in=0.1`、`blend_out=0.2`。本次不重写存储 clip、hash 或 catalog。

## 当前循环修正

- 旧的自定义预览与正式播放使用 `age % totalDuration()`（1.3s）再调用 `sample()`，在 1.0s 后的淡出区权重降到 0，形成冻结/回正循环。
- `PenguinAnimationClip.sampleLoop(float age)` 改为按源周期 `duration`（1.0s）取模，固定返回权重 1，只采样作者动作；负时间同样归一化，非有限时间继续拒绝。
- 自定义预览和权威播放共用该采样器；`DancePreviewScreen` 删除对 `totalDuration()` 的冗余取模。一次性动作仍使用原有 `sample()`/`sampleTerminal()` 语义。
- 自定义姿态求值传入调用方已解析的 profile 显式重载，与内置舞蹈一致，俯卧家族模型在起身舞蹈时不会因再次 `forModel` 得到 null。

## 待确认

- 需要在原生客户端重启并运行 fixture 后检查真正的循环接缝与完整周期；现有截图中的躯干/头部水平旋转尚不能作为验收结论。
