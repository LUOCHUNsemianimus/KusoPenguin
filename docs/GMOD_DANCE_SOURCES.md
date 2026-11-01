# GMod 舞蹈源归档、重定向与导出 / GMod Dance Source Archive & Export

本文件记录内置 `builtin:hip` 与 `builtin:pole` 的 GMod Source 来源、可复现导出流程和离线核验边界。运行时**不读取 SMD、ValveBiped bind 或本工具**；客户端在正常资源重载时解析两份导出的 JSON clip，再用共享的 `PenguinAnimationPose.evaluate` 采样。视觉观感与实机验收由上游原生运行时检查，本文件不宣称已通过。

## 1. 归档位置

- 默认源目录：`models/animation/gmod/source`
- clip 输出：`src/main/resources/assets/kuso_penguin/animations/dance_pole.json`、`dance_hip.json`
- 扫描报告：`models/animation/gmod/getdown_scan_report.json`
- 源清单：`models/animation/gmod/source/archive-manifest.json`（逐文件 SHA256、源 MDL/ANI 哈希、帧数、FPS、用途）
- 抽取时的完整 provenance：`models/animation/gmod/source/animations/manifest.json`

`archive-manifest.json` 只登记实际检入的复现所需文件：Spooky male、完整 GetDown male、GetDown 885..899 参考裁片、PUBG male/female 及其 bind。未检入的 female Spooky/GetDown、last2s 裁片仍保留在抽取清单中，不复制进仓库。

已核验：检入 SMD 的 SHA256 与 `D:\Moddevelop\exports\gmod-dance-sources-20261101\animations\manifest.json` 一致；三个 bind 的 SHA256 与外部原文件一致；检入的 `animations/manifest.json` 与外部清单字节一致。

## 2. 可复现命令

在仓库根目录，使用 Python 标准库（无新依赖）：

```bat
python tools\export_gmod_dances.py
python tools\export_gmod_dances.py --check
```

默认 `--source-dir` 已指向检入的 `models/animation/gmod/source`，因此干净检出即可复现。仍可用 `--source-dir` 指向外部抽取目录；`--out-dir`、`--report` 保留为可选覆盖。

`--check` 不只比较帧数/哈希：它从源 SMD 与 bind 重建两份 clip，对完整 JSON 文档做确定性比较（所有帧时间、16 关节的旋转/平移/缩放、joint/bind 契约、时长、来源哈希与元数据），同时重算 `getdown_scan_report.json` 的全部语义字段。任何被修改但仍有限、仍归一化的旋转都会报告精确路径，例如：

```text
hip asset drift from source rebuild:
hip.frames[3].rotations[7][2]: checked-in ... != source ...
```

无漂移时退出码为 0；有漂移时退出码为 1。该检查不写文件。

## 3. 坐标与重定向

**坐标修正（2026-11-01，取代旧卡的 +Y 前提）**：实际中性 MDL FK 的脚→趾向量为 `R_Foot=(-4.069599,+1.811237,4.279079)` → `R_Toe0=(-4.369892,-3.918735,0.483803)`，Y 差 `-5.72997`，左腿对称；右上臂 X 为负、上方向为 +Z。因此 Source ValveBiped 的实际前向是 **−Y**、上 +Z、右 −X，而不是 +Y。企鹅模型约定：前 −Z、上 −Y（模型 +Y 向下）、右 +X。

导出使用固定基 `C = (-sourceX, -sourceZ, +sourceY)`：
- 前 `(0,-1,0)` → `(0,0,-1)`；右 `(-1,0,0)` → `(+1,0,0)`；上 `(0,0,1)` → `(0,-1,0)`。
- `det(C) = -1`，这是左右手约定之间的反射，不是 proper rotation。旋转必须按 `C R C^-1` 共轭；反射本身没有单位四元数表示，所以四元数向量部分按 `det(C)*C*q.xyz = (qx, qz, -qy)`、标量 `w` 不变，再归一化。导出器的 `--check` 对多个旋转/基向量同时核对矩阵共轭与四元数公式。

旧版 `Q_C = (0,√.5,-√.5,0)` 是错误 proper rotation，已移除，不能沿用。方向重定向也已修正：旧实现把“Source rest 方向 → Source 动画方向”的摆动直接当作目标世界旋转，作用在不同目标 bind 向量上，留下约 20.99..21.86° 残差。现在先取修正后 C 的 Source 动画 FK 方向，再求“目标固定 canonical bind link → 该方向”的旋转，并由实际父级逆一次导出局部四元数。目标 bind 长度不变。

中性姿态来自导出的 MDL bind，而不是动画首帧。躯干/头部的中性局部旋转仍为 identity；但 Source 与目标的中性肢体方向不同，因此**不要求**中性肢体四元数为 identity。`--check` 改为验证中性肢体目标世界方向与修正后 C 的 Source 方向一致、目标长度保持，并报告肢体局部旋转角。源 56 根骨重定向到固定 16 关节企鹅 rig：pelvis/chest/head、左右翼各三段、左右腿各三段。腿部世界位置由 `PenguinLegPose` 的解剖重绑定负责；导出的 clip 只保留 pelvis 的零均值局部平移，根/世界/实体平移为零。pelvis 平移参考为该段源世界 pelvis 均值。

**翼尖 distal 校准（2026-11-01）**：运行时实际可见翼尖偏移是 `PenguinHeldItemRenderer.ACTIVE_TIP_OFFSET=(0.78,2.0,0.17)`（负翼镜像 X），Source distal 参考为 `ValveBiped.Bip01_R_Finger2` / `L_Finger2` 相对 `Hand`。导出器先从真实 MDL rest FK 求固定中性校准 `qTipRestCal = rotation_between(target_visible_tip_offset, C*(restFinger2Pos-restHandPos))`，再令 `qTipWorld = c_rotate(handDelta) * qTipRestCal`，最后用实际 fore 世界旋转的逆一次导出 `q[tip]`。这样保留 Source 手部相对扭转，同时把实际可见翼尖轴对齐到 Source 动画 `Hand→Finger2` 方向。已测量两份 clip 中被审阅帧的 Finger2 相对 Hand 局部位置恒定，因此固定校准是精确的，不需要逐帧 corrective swing；若未来源出现变量局部位置，才应改用保留 twist 的逐帧修正并报告。所有导出帧、左右两侧的最大翼尖方向残差约 `5.4e-6°`，可见偏移长度变化约 `1.3e-15`。

## 4. `builtin:pole`（Spooky Month Dance）

| 项 | 值 |
| --- | --- |
| 源文件 | `animations/Amod_AM4_Spooky_Month_Dance_male.smd` |
| 源 SHA256 | `01bc242f9a8ce7452662fa1cbfacdae11d4f4b7fe8565908d10bed0dc03d3216` |
| 源序列 | `Amod_AM4_Spooky_Month_Dance`，30 FPS，10 帧 |
| bind | `add_taunt-male-bind.json`，56 骨 |
| 导出 | 11 帧，0.3333333 s（10/30 s），runtime rate 0.8 |
| 来源 MDL / ANI | `71fda46c...988b0` / `03a46b5b...e2f46`（全值见 archive-manifest） |

原始 10 帧全部保留，然后在 `10/30 s` 显式重复第 0 帧，使 `sample()` 连续回绕。这是刻意的循环闭合；源第 9 帧到第 0 帧的真实接缝单独测量并报告（本次核验为 0°，位置 0）。runtime rate 取原插件对该动作的 `0.8`，不额外乘旧的程序化 `1.5x`。

## 5. `builtin:hip`（GetDown 选取段）

| 项 | 值 |
| --- | --- |
| 源文件 | `animations/Amod_MMD_GetDown_male.smd` |
| 源 SHA256 | `11478f245a553d334cb1a45bda05652abe327ff9123fc33cf9f00d8105594d9d` |
| 源序列 | `Amod_MMD_GetDown`，30 FPS，1198 帧，39.9 s |
| bind | `add_mmd-male-bind.json`，56 骨 |
| 选取 | 源帧 `885..899`，15 样本，0.4666667 s，runtime rate 1.0 |
| 导出 | 15 帧，末帧保留，循环回绕到首帧 |

选取段的源世界测量：

- pelvis 前后振幅 21.320963（Source 前向为 −Y；带符号轴标签改为 −Y，振幅/选择不变），侧向 17.559129（右为 −X），上下 6.246285（Source 单位）
- 头部世界位移 7.193727，手部世界位移 5.614519，手相对头 4.168139
- 端点最大世界旋转接缝 0.260534°；pelvis 前后不匹配 0.0，头部位置不匹配 0.0
- 未添加合成端点；接缝按实测报告，未归一化掩盖

拒绝的尾部 `1167..1182`：pelvis/spine/upperarms/legs 在该区间基本静止，只有头/手旋转，不能作为 hip 动作；因此没有采用“最后 2 秒”方案。完整 1198 帧源仍保留在仓库中。

`animations/Amod_MMD_GetDown_male_885_899.smd` 是把 `885..899` 原始采样行裁出并重编号为 `time 0..14` 的艺术家参考文件，15 帧数值与完整源逐样本相等；导出器不读取该裁片，仍从完整源选取。

## 6. PUBG 归档（仅未来备用，未启用）

| 文件 | 帧 / FPS / 时长 | SHA256 |
| --- | --- | --- |
| `Amod_PUBG_2PhutHon_male.smd` | 901 / 30 / 30.0 s | `576bca037609835094716a7791a0f93db0196c54e0c729a9c3088c236f4ce178` |
| `Amod_PUBG_2PhutHon_female.smd` | 901 / 30 / 30.0 s | `90c507a46b4bdd38b26cb9a53194c6b51d6620bd73d1e5dfbbd824c044b04a7a` |

两者是 raw male/female 源，只作未来素材归档：没有导出 clip、没有注册为 builtin、没有改变现有 30 秒 DAE 导入上限来“接纳”它们。其 MDL/ANI 哈希与 bind 见 `archive-manifest.json`。

## 7. 运行时与时长

- `builtin:hip`、`builtin:pole` 仍属于既有 5 秒风格节拍（`builtin:cycle` 为 15 秒）；上面 0.4666667 s / 0.3333333 s 是源片段循环长度，不是替换 gameplay 时钟。
- 动画资源在客户端资源重载时一次性解析并缓存；每帧只走共享 `PenguinAnimationPose.evaluate`，没有逐帧文件 IO，也没有第二套动画管线。
- 服务端时钟、触发优先级、wave/cycle、上传/重载缓存与偏好/槽位逻辑均未因本次导出改动。

## 8. 验证边界

已完成的离线核验：源/绑定哈希、`--check` 全文档重建一致性、正常导出、以及临时 out-dir 中修改一个仍归一化旋转后被精确拒绝。坐标修正后另加方向证明：两份 clip、左右两侧、全部导出帧（含 pole 闭合帧）的目标翼上/下段世界方向与修正后 C 的 Source FK 方向最大残差约 `1.2e-6°`；翼尖可见轴对 `C*(Hand→Finger2)` 最大残差约 `5.4e-6°`；手/脚末端相对校准目标朝向残差约 `1.7e-6°`；目标 canonical bind 链接长度差 `0`，可见翼尖偏移长度变化约 `1.3e-15`。中性帧躯干/头部旋转与平移为 identity/零，肢体方向与翼尖方向残差约 `8.5e-7°`；中性肢体局部旋转最大约 `20.41°`，因此明确不要求肢体 identity。`--check` 同时验证 `C` 的 `det=-1`、六个基向量、四组旋转的四元数/矩阵共轭一致性，并把方向/翼尖残差与长度变化纳入失败条件。未完成/不由本文件宣称：上游源码审查、原生运行时视觉验收、所有客户端组合的实机测试。Gradle 构建结果以工作流构建证据为准。
