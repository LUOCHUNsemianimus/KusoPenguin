# 腿部变形核心 / Leg Deformation Core

## 中文

### 解剖控制枢轴
- 共享助手 `PenguinLegPose` 在每一次 `PenguinAnimationPose` 构造中只调用一次，且在生成 skin 矩阵之前：动作/嘲讽/飞踢/死亡、普通 `nativeBodyOnly`、舞蹈与施法以及 `fromNativeRig`（Iron/Maledictus）全部覆盖。
- 虚拟解剖控制点使用企鹅模型像素：正 X 侧髋／膝／脚为 `(3.3,20,0)`、`(3.3,21.3,0)`、`(3.3,22.3,0)`，负 X 侧镜像 X。正 X 是右腿，负 X 是左腿。
- 原版 16 关节 clip 的不可变 bind 仍是髋 15／膝 20.5／脚 22.3，因为关键帧、烘焙 skin 与 rig 都以它为准。控制点不是对旧 bind 的修改，而是为连体羽毛腿根另设的骨盆锚定控制。
- 模型空间 Y 向下，因此新控制在 Y 上数值更大，即相对旧腿部绑定**向下移动**、靠近羽毛腿根，而不是“更高”。旧“外露腿柱”式绑定不再决定可见腿的旋转中心。
- 中立姿态下，每个解剖控制帧等于它自己的绑定帧；通过下面的补偿，skin 恢复原来的 clip-world／单位矩阵。控制帧与旧 clip 帧并不字面上是同一个矩阵；两者只在最终 skin 结果上等价。

### 重绑定算法
- `pelvisSkin = world[PELVIS] * T(-clip.bind(PELVIS))`；物理髋点 = `pelvisSkin` 变换解剖髋控制点。body scale 取自 `pelvisSkin.getScale`。
- 先从“原始传入”的 `world[hip/knee/foot]` 读取绝对旋转，避免父级旋转被重复叠加。随后用短解剖偏移链构建控制世界帧：`H = T(hip) * Rhip * S`，`knee = H * (ctrlKnee-ctrlHip)`，依此类推。肢体平移只使用短控制偏移（仅受统一 body scale 影响），不采用人类／独立根骨骼的原点平移；角动画保持原样。
- 对既有 clip skin 契约重绑定：`world[j] = controlWorld[j] * T(-ctrlBind[j]) * T(clip.bind(j))`。于是既有 `skin = world * T(-clip.bind)` 等价于 `controlWorld * T(-ctrlBind)`。因此不会出现静止模型跳变，也没有修改原始 bind 或 clip 数据。
- 只改髋／膝／脚世界矩阵；root、head、wing、body（骨盆／胸）不变。死亡、舞蹈、趴下的地面边界继续测量修正后的真实蒙皮。

### 蹲下整体落地（仅显式权重新网格）
- `PenguinPlayerPosture` 对 schema 2 显式权重网格先完成原生姿态准备，再用实际修正后的 `body`、`right_foot`、`left_foot` 蒙皮上界的最大值，相对既有蹲下地面目标计算**一个带符号的整机修正**。
- 同一个修正一次性作用于 body、双翼、双脚根和头部挂点：既可能抬高，也可能让悬空的整机下沉；不再分别抬躯干或脚而拉开连体接缝。
- 旧 schema 1 分支保持原来的独立刚性根抬升。坐、骑、游泳、滑翔与空中飞踢不做地面拟合，行为不变。

### 三档几何与归档
- 三档模型的中央、骨盆固定的裆部已由 runtime Y `22.6` 抬到 `20`（BB Y `1.4` 到 `4`），使下腹到羽毛腿根的过渡对接新控制点。每档 BB 都恰好只改了这 2 个中央裆部顶点的 Y。
- 中段羽毛环的膝权重为分数 `10/13 ≈ 0.76923`，髋权重为 `3/13 ≈ 0.23077`，两者之和为 1；不是髋、膝分别取 10 和 13。
- 脚掌／脚趾轮廓、上半身、翅膀／手臂、尾巴与调色板保持不变；groups/outliner/faces/UV 也未改。高／中／低档三角面数仍为 `444 / 308 / 240`。
- 修改前三档模型、sidecar 与生成 JSON 的副本保存在 `models/archive/leg-deformation-20261028/`，归档哈希已与修改前核对。

### 验证状态
- 实际游戏验证（证据在 `runtime/leg-deformation-20261028/`）：39 个真实玩家渲染用例（idle、kick、crouch、cast-crouch、ride、swim、elytra，以及 taunt/kick 的进入-中段-退出），3 个趴下用例，174 个真实 Boss 动作变形采样加 6 项武器／隔离／抓取／恢复检查，9 次真实 RemotePlayer 死亡 ghost 绘制；另有 180 个程序化舞蹈采样。旧 live-DEATH 测试按既有 HIDDEN 规则被跳过，由真实 ghost 路径替代。
- 物理骨盆锚点与固定解剖长度 `1.3 + 1.0` 像素；髋附着与骨长误差均小于 `1e-4` 像素。玩家最大边长因子 `2.393`、最小面积比 `0.08696`；Boss 为 `3.027`、`0.05136`。舞蹈最大边长 `1.326`、最小面积 `0.01973`、最大地面 `23.939 < 24`；自然压缩褶皱（舞蹈最小面积约 `0.0197`）被保留，不保证所有角度都无畸变。
- 蹲下渲染下界 `1.3666666` 对地面目标 `1.3666667`，偏差约 `1e-7`。
- 189 个独立 CPU 控制姿态（三档各 63）最大边长 `2.155`、最小面积 `> 0.05`，与实机计数分开；不声称所有角度都被限制在 `2.16` 内。对比旧反例的 `8.56 / 9`，P95 最差因子为 `1.668 / 1.639 / 1.439`。这些 CPU 指标不是运行时结果或逐像素截图重建。
- GPU 截图 `player-kick-high`、`boss-halberd-high`、`dance-pole-low` 已确认真实渲染中不再出现长腿柱拉扯，舞蹈低姿自然折叠。本文不声称完成自动全战斗、多人或完整场景验收。

## English

### Anatomical control pivots
- Shared helper `PenguinLegPose` is applied exactly once in every `PenguinAnimationPose` construction, before skin matrices: action/taunt/kick/death, ordinary `nativeBodyOnly`, dance and casting, and `fromNativeRig` (Iron/Maledictus).
- Virtual anatomical controls in penguin model pixels: positive-X hip/knee/foot are `(3.3,20,0)`, `(3.3,21.3,0)`, `(3.3,22.3,0)`; negative X mirrors X. Positive X is the right leg, negative X the left.
- The legacy 16-joint clip keeps its immutable hip 15 / knee 20.5 / foot 22.3 bind, because keyframes, baked skins and the rig reference it. The controls are not an edit of that bind; they are separate pelvis-anchored controls for the connected feathered thigh roots.
- Model space has Y pointing down, so the new control values are numerically larger in Y: they move **downward** toward the feathered thigh roots, not “higher”. The old exposed-pole bind no longer decides the visible leg's rotation centre.
- At neutral, each anatomical control frame equals its own bind frame; the compensation below restores the original clip-world / identity skin. The control frame is NOT literally the same matrix as the old clip frame; the two only agree in the resulting skin.

### Rebinding
- `pelvisSkin = world[PELVIS] * T(-clip.bind(PELVIS))`; the physical hip point is the pelvis skin transforming the anatomical hip control. Body scale is `pelvisSkin.getScale`.
- Absolute rotations are read from the ORIGINAL incoming `world[hip/knee/foot]` first, so parent rotations are not compounded twice. The short anatomical chain builds the control world frames (`H`, `K`, `F`). Limb translation uses only the short control offsets (subject to the common body scale), never human/independent-root bone-origin translations; angular animation is preserved.
- Rebind for the existing immutable clip skin contract: `world[j] = controlWorld[j] * T(-ctrlBind[j]) * T(clip.bind(j))`, so `skin = world * T(-clip.bind) = controlWorld * T(-ctrlBind)`. This leaves no rest-model pop and mutates no original bind or clip data.
- Only hip/knee/foot matrices change; root, head, wings and body (pelvis/chest) are untouched. Death, dance and prone floor bounds still measure the corrected skin.

### Whole-assembly crouch fit (explicit weighted meshes only)
- `PenguinPlayerPosture` prepares the native posture, then takes the maximum actual corrected-skin bound over `body`, `right_foot` and `left_foot` and computes ONE signed whole-assembly correction relative to the existing crouch floor target.
- That single correction is applied once to body, both arms/wings, both foot roots and the head mount. It may lower a hovering assembly; torso and feet are never lifted independently, so the welded seam cannot split.
- The old schema 1 branch keeps its independent rigid-root lifts. Sitting, riding, swimming, gliding and airborne kicks remain ungrounded and unchanged.

### Three-tier geometry and archive
- The central, pelvis-pinned crotch in all tiers moved from runtime Y `22.6` up to `20` (BB Y `1.4` to `4`), aligning the lower-abdomen-to-feathered-thigh transition with the new controls. Each tier's BB changed exactly these 2 central crotch vertices' Y.
- The middle feather ring has knee weight `10/13 ≈ 0.76923` and hip weight `3/13 ≈ 0.23077`, summing to 1. The fraction is not a pair of unnormalized weights 10 and 13.
- The toe/footprint outline, upper body, wings/arms, tail and palette are retained; groups/outliner/faces/UV are also unchanged. Triangle counts stay `444 / 308 / 240` for high/medium/low.
- Pre-change copies of all tiers, the sidecar and generated JSON are archived in `models/archive/leg-deformation-20261028/`; archive hashes were checked against the originals.

### Verification status
- Actual game verification (evidence under `runtime/leg-deformation-20261028/`): 39 real player renderer cases (idle, kick, crouch, cast-crouch, ride, swim, elytra, plus taunt/kick entry-mid-exit), 3 prone cases, 174 real boss motion deformation samples plus 6 weapon/isolation/grab/restoration checks, and 9 real RemotePlayer death-ghost draws; 180 procedural dance samples were also taken. The old live-DEATH test is correctly skipped by the existing HIDDEN rule and replaced by the real ghost path.
- The physical pelvis anchor and fixed anatomical lengths `1.3 + 1.0` pixels; hip attachment and bone-length errors stay below `1e-4` pixels. Player maximum edge factor `2.393`, minimum area ratio `0.08696`; boss `3.027`, `0.05136`. Dance maximum edge `1.326`, minimum area `0.01973`, maximum floor `23.939 < 24`; natural compressed creases (dance minimum area about `0.0197`) are retained, and distortion-free results are not guaranteed for every angle.
- Crouch rendered bound `1.3666666` versus floor target `1.3666667`, a difference of about `1e-7`.
- 189 separate CPU control poses (63 per tier) give maximum edge `2.155` and minimum area `> 0.05`; these are separate from the actual-game counts and do not claim every angle is bounded by `2.16`. Against the old counterexamples `8.56 / 9`, P95 worst factors are `1.668 / 1.639 / 1.439`. These CPU metrics are not runtime results or exact screenshot reconstruction.
- GPU screenshots `player-kick-high`, `boss-halberd-high` and `dance-pole-low` confirm no long thigh straps in real rendering, with natural folding at dance low pose. This document does not claim automated full-combat, multiplayer or exhaustive-scene acceptance.
