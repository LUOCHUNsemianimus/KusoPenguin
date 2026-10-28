# Scylla 视觉替换 / Visual replacement

持锚翼标定：Scylla 默认原生手臂已经前屈，单纯抵消 rest 会让企鹅翼下垂而锚悬空。
本 profile 保留企鹅肩部接缝位置，肘／腕使用原生前臂和握持点；腕位置读取锚的局部平移，
不继承锚 mesh 的隐藏缩放，避免甩锚时翼端消失。原生锚与骨骼均不修改。

Wing calibration retains the body-aligned shoulder attachment but follows Scylla's native bent
elbow and grip frames. Wrist placement reads the anchor translation without inheriting its mesh
visibility/scale. This affects only the Scylla penguin pose, not native parts or global bind data.

睡眠隐藏阶段：原生 SPAWN_IDLE 将 `everything` 缩放为零，只留下根级 `anchor2`。
出场动画也有极短的零／微小缩放阶段；这些阶段完整保留原生绘制，不向连续蒙皮提交塌陷三角形。
仅 Scylla 身体祖先不可见或任一缩放轴绝对值低于 0.01 时使用此回退，不改原生姿态、动画或全局网格检查。

Native SPAWN_IDLE hides the body at zero scale and leaves the separate root-level sleep anchor.
The bridge preserves the full native draw while a body ancestor is invisible or any scale axis is
below 0.01 in magnitude, including the brief birth transition. The global triangle-collapse check,
native matrices and Maledictus behavior remain unchanged.

## 中文

### 范围与状态

本兼容只替换 Cataclysm `scylla` 的可见身体，使用现有企鹅网格/皮肤与共享原生
`ModelPart` rig；原生头部、眼睛、头发、锚、链、蛇饰、闪电/轨迹特效、AI、动作状态与时序、
攻击、伤害、碰撞箱、尺寸、sleep 与死亡继续由 Cataclysm 管理。固定目标为 Cataclysm
1.21.1-3.33（SHA256 `371956d0c2e3b56729ee93b955e936875d210e372fe734cdb74326d89ef79875`）
与 Lionfish 3.1；只按该 pinned JAR 的类名/描述符/rest 数据验证，不新增 Cataclysm、
Lionfish 或 GeckoLib 编译/运行时依赖，也不分发其模型或贴图。

**当前状态：已通过开发环境动作采样、实际 GPU 与可选模组缺失检查，等待用户实机观感验收。**
`classes build` 仅为编译证据，行为证据见文末；未声称完成全部 AI 战斗或所有光影包验证。

### 视觉-only 门禁

只有以下条件全部满足才替换：Cataclysm 已加载、模型类名精确为
`com.github.L_Ender.cataclysm.client.model.entity.Scylla_Model`、注册 id 精确为
`cataclysm:scylla`、企鹅效果存在、网格预览开启、`currentSkin`/`animationClip` 可用、完整
层级与 rest 校验通过，且实体不是幼体、隐身、发光或旁观。任一条件失败都重放完整的原始原生
调用，包括缺少模型/皮肤/层级、sleep 锚点缺失或模型版本不符。不会吞异常来伪装 fallback。

`PenguinEntitySupport` 只把 Scylla 加入独立的 `isScylla` 精确集合；普通动作/趴行集合不变。
`PenguinPoseSync` 对 `isNativeAnimatedBoss` 返回 0，因此 Scylla 不会进入 Kuso
TAUNT/KICK/DEATH、普通动作、趴行或舞蹈。效果标签沿用 Cataclysm 的
`effective_for_bosses` 追加方式。

### 固定路径与 rest 锚点

模型根到躯干：`everything → scylla → body → chest → head`；sleep 锚是根级独立子件
`anchor2`。固定 rest 平移（模型像素，Y 向下，地面 Y=24）：

```text
everything (0,24,0)      scylla (0,0,0)        body (0,-20,0)
chest (0,-5,0)           head (0,-6.5,0)
l_arm (5.5,-4,0)         l_arm2 (0,5,0)       r_arm (-5.5,-4,0)   r_arm2 (0,5,0)
l_leg (2,-20,0)          l_leg2 (0,9,0)       r_leg (-2,-20,0)    r_leg2 (0,9,0)
anchor (-1.5,8,0)        chain_main (0,8,0)   anchor2 (4,8,0)
```

固定路径还要求 `anchor` 与 `chain_main` 位于 `l_arm2` 下，`anchor2` 位于模型根下，
`belt_sea_snake` 位于 `body/belt_sea_snake_mody` 下，左右肩蛇分别位于
`l_arm/l_armor/l_sea_snake` 与 `r_arm/r_sea_snake` 下。任一名称或 rest 不符即完整回退。

### 16 关节映射与对齐

`ROOT=scylla`、`PELVIS=body`、`CHEST=chest`、`HEAD=head`。沿用企鹅历史左右命名：
正 X 的 `right_wing` 三段跟随原生 `l_arm/l_arm2`，负 X 的 `left_wing` 跟随
`r_arm/r_arm2`；`right_hip` 跟随 `l_leg/l_leg2`，`left_hip` 跟随 `r_leg/r_leg2`。原生没有
独立脚骨，膝/脚共用 `l_leg2`/`r_leg2` 的 `D`。所有关节使用完整父链的
`D = F × inverse(B)`，尊重原生默认旋转、动画旋转和 scale。

头部真实世界枢轴为 `24 - 20 - 5 - 6.5 = -7.5`。第一步对齐：

```text
A = T(0, 24, 0) × S(31.5/24) × T(0, -24, 0)
baseWorld[j] = (D × A) × T(penguinBind[j])
```

`31.5/24 = 1.3125`，Z 为 0。renderer 原生的 `1.15` 缩放发生在模型调用之外，这里不再乘一次。
头部/眼睛/锚/链的 socket 继续读取未经修改的原生 `ModelPart`，没有移动任何原生骨骼来补手部
或翼端间隙。当前企鹅网格仍只是 pilot；若实机显示原生锚到翼端有明显间隙，只允许新增
Scylla-profile 专用的小幅静态翼部对齐修正，不修改全局比例或原生锚点。

### 三个原生 pass 的保留策略

1. **主 pass**：native buffer 先提交保留件，再向 `MultiBufferSource` 请求企鹅材质并画
   **唯一一份**企鹅身体。保留件为 `head` 终端（含眼睛/头发子树）、`anchor`、
   `chain_main`（含完整链/`trail` 子树）、`anchor2`（仅原生 visible 时绘制）以及
   `belt_sea_snake`、`l_sea_snake`、`r_sea_snake` 三个蛇饰终端。原生 body/chest、腿、
   `l_armor`/`r_armor` 装甲和 `l_arm`/`l_arm2` 基础手臂几何不重画。保留终端通过完整祖先链
   绘制，终端的原生子件自动保留，避免祖先+后代重复绘制。
2. **Snake ghost layer**：只重放三个蛇饰终端，继续使用原生 `scylla_snake.png` ghost
   `RenderType`、固定 alpha、overlay 和 color；不画原生躯干，也不生成第二份企鹅身体。
3. **LayerGenericGlowing 眼层**：只在该层确实使用 Scylla 模型/实体时重放 `head` 终端，
   继续使用原生 `scylla_eye.png` 眼材质与隐式 color；其他使用该通用层的 Boss 保持完整原生
   调用。`Scylla_Eye_Spark_Layer` 与原生锚/链/轨迹 socket 完全不拦截。

主 pass 的“先原生、后企鹅材质”顺序沿用 Maledictus 已修复的 BufferSource 生命周期纪律；
真实 BufferSource 可能在切换 RenderType 时 flush/复用 builder。颜色 alpha（含受伤/死亡
分支）由原生 renderer 计算后原样传入三个 pass，不另写死亡逻辑。

### 共享提取与回退

`NativeModelPartRig` 只拥有不可变祖先路径、rest 校验、当前/初始矩阵计算和 alignment；
`NativeRetainedGeometry` 拥有保留终端列表及完整祖先/可见性绘制。Maledictus 保留原有
路径、对齐、保留列表和 wrapper API，只把矩阵/路径/终端绘制委托给这两个共享类。Scylla 使用
独立的 model 类名、id、rest 集合、16 关节映射、alignment 与 pass 保留策略，没有复制完整
`JointPath`/16 关节计算/渲染循环。

### 下游交接时的验证边界（历史）

源码 `classes build` 在实现完成后由固定工作流 MCP 执行。源码/JAR 指纹、包围盒、顶点数、
`translateToEye`/`translateHand`/`translateChainAnchor` 与未修改原生模型的对照、A/B/A
姿势隔离、sleep/死亡、效果移除、BLOCK/隐身/发光回退、三档网格和真实 GPU 世界/第一人称
场景尚未在本步骤运行；这些由上游 QA 单独安排。`test` 没有自动化行为测试。

## English

### Scope and status

This compatibility replaces only the visible body of Cataclysm `scylla`, using the existing
penguin mesh/skin and a shared native `ModelPart` rig. The native head, eyes, hair, anchor, chain,
snake ornaments, lightning/trail effects, AI, animation states/timing, attacks, damage, collision
box, dimensions, sleep and death remain owned by Cataclysm. Pinned target: Cataclysm
1.21.1-3.33 (SHA256 `371956d0c2e3b56729ee93b955e936875d210e372fe734cdb74326d89ef79875`) and
Lionfish 3.1. No Cataclysm/Lionfish/GeckoLib compile or runtime dependency is added and none of
their models or textures are redistributed.

**Status: implemented, awaiting upstream runtime/GPU acceptance.** This document records the
implementation and pending validation; it does not claim runtime acceptance. `classes build` is
compile evidence, not behaviour evidence.

### Visual-only gate

Replacement requires all of: Cataclysm loaded, exact model class
`com.github.L_Ender.cataclysm.client.model.entity.Scylla_Model`, exact registry id
`cataclysm:scylla`, the penguin effect, enabled mesh preview, available
`currentSkin`/`animationClip`, a complete hierarchy/rest preflight, and a non-baby,
non-invisible, non-glowing, non-spectator entity. Any failure replays the complete original native
call, including missing model/skin/hierarchy, sleep-anchor failure or a different model version.
Exceptions are never swallowed to hide a broken profile.

`PenguinEntitySupport` adds Scylla only to the separate exact `isScylla` set; the ordinary action
and prone sets are unchanged. `PenguinPoseSync` returns 0 for `isNativeAnimatedBoss`, so Scylla
never enters Kuso TAUNT/KICK/DEATH, ordinary action, prone or dance clips. The Cataclysm
`effective_for_bosses` tag merge is unchanged.

### Pinned paths and rest anchors

Root-to-torso: `everything → scylla → body → chest → head`; the sleep anchor is the separate
root child `anchor2`. Pinned rest translations (model pixels, Y down, ground Y=24):

```text
everything (0,24,0)      scylla (0,0,0)        body (0,-20,0)
chest (0,-5,0)           head (0,-6.5,0)
l_arm (5.5,-4,0)         l_arm2 (0,5,0)       r_arm (-5.5,-4,0)   r_arm2 (0,5,0)
l_leg (2,-20,0)          l_leg2 (0,9,0)       r_leg (-2,-20,0)    r_leg2 (0,9,0)
anchor (-1.5,8,0)        chain_main (0,8,0)   anchor2 (4,8,0)
```

Exact paths also require `anchor`/`chain_main` under `l_arm2`, `anchor2` at the model root,
`belt_sea_snake` under `body/belt_sea_snake_mody`, and the shoulder snakes under
`l_arm/l_armor/l_sea_snake` and `r_arm/r_sea_snake`. Any name or rest mismatch falls back to the
complete native renderer.

### 16-joint mapping and alignment

`ROOT=scylla`, `PELVIS=body`, `CHEST=chest`, `HEAD=head`. Historical penguin side names are kept:
positive-X `right_wing` follows native `l_arm/l_arm2`, negative-X `left_wing` follows
`r_arm/r_arm2`; `right_hip` follows `l_leg/l_leg2`, `left_hip` follows `r_leg/r_leg2`. The native
model has no separate foot bone, so knee/foot share the `l_leg2`/`r_leg2` delta. Every joint uses
the full ancestor `D = F × inverse(B)`, honouring native default rotations, animated rotation and
scale.

The real head-pivot world Y is `24 - 20 - 5 - 6.5 = -7.5`. First alignment:

```text
A = T(0, 24, 0) × S(31.5/24) × T(0, -24, 0)
baseWorld[j] = (D × A) × T(penguinBind[j])
```

`31.5/24 = 1.3125`, Z=0. The renderer's native `1.15` scale happens outside the model call and is
not multiplied again. Head/eye/anchor/chain sockets still read the unmodified native `ModelPart`;
no native bone is moved to close a hand or wing-tip gap. The current penguin mesh is a pilot only.
If runtime evidence shows a clear native-anchor-to-wing-tip gap, only a small Scylla-profile-only
static wing alignment correction is allowed; global proportions and native anchors must not move.

### Three native passes

1. **Main pass**: submit retained pieces into the native buffer first, then request the penguin
   material and draw exactly one penguin body. Retained terminals: `head` (including the
   eye/hair subtree), `anchor`, `chain_main` (full chain/`trail` subtree), `anchor2` (only when
   natively visible) and the three snake ornaments `belt_sea_snake`, `l_sea_snake`,
   `r_sea_snake`. Native body/chest, legs, `l_armor`/`r_armor` armor and the base
   `l_arm`/`l_arm2` arm geometry are not redrawn. Retained terminals render through their full
   ancestor chain and keep native children, avoiding ancestor+descendant double draws.
2. **Snake ghost layer**: replay only the three snake terminals under the native
   `scylla_snake.png` ghost `RenderType`, fixed alpha, overlay and color. No native torso and no
   second penguin body.
3. **LayerGenericGlowing eye layer**: only when the layer is actually used by the Scylla
   model/entity, replay the `head` terminal under the native `scylla_eye.png` eye material and
   implicit color; every other user of the shared layer keeps the complete native call.
   `Scylla_Eye_Spark_Layer` and the native anchor/chain/trail sockets are not intercepted.

The main pass keeps the Maledictus-accepted "native first, penguin material second" BufferSource
lifetime order. The native renderer computes hurt/death alpha and passes the same color to all
three passes; no separate death logic is added.

### Shared extraction and fallback

`NativeModelPartRig` owns immutable ancestor paths, rest validation, current/initial matrix
calculation and alignment; `NativeRetainedGeometry` owns retained terminal lists and the full
ancestor/visibility draw. Maledictus keeps its exact paths, alignment, retained list and wrapper
APIs while delegating the shared mechanics. Scylla has its own model class, id, rest set, 16-joint
mapping, alignment and per-pass retained policy; no full `JointPath`/16-joint calculation/render
loop is copied.

### Validation boundary at downstream handoff (historical)

Source `classes build` is run once after the final code/docs edits through the fixed workflow MCP.
Source/JAR fingerprints, bounding boxes, vertex counts, `translateToEye`/`translateHand`/
`translateChainAnchor` comparisons against the unchanged native model, A/B/A pose isolation,
sleep/death, effect removal, BLOCK/invisible/glowing fallback, all mesh tiers and a real-GPU world
scene have not been run in this step; upstream QA owns them. `test` has no automated behaviour
suite.


## 上游开发环境验收 / Upstream development verification

2026-10-28，Cataclysm 1.21.1-3.33 / Lionfish 3.1，隔离 `KusoPenguin-Validation` 存档。

- 三档网格：原生 state0–27，每状态采样 age0/4/5/8/25/55，加效果移除／重加、A/B/A、隐身／发光／BLOCK 回退，共 **518 项 PASS**。隐藏阶段按原生只显示锚，可见阶段仅一份企鹅身体。
- **180 项 Maledictus 渲染回归 PASS**；共享提取未改变原有武器、ghost、抓取乘客与回退。
- 160 个可见 Scylla 姿态的企鹅持锚翼端与原生握持点最大误差 **0.000002708 模型像素**；8 个原生隐藏姿态不调用连续蒙皮。比较单位是模型像素，外层原生 1.15 缩放不重复应用。
- 原生部件 pose/visibility/scale 在 10 个代表动作中与原生完整绘制精确一致；锚、链、眼部函数继续读取同一套未修改部件。持锚点另做上述直接位置对照，不把全部 socket 声称为独立逐点数值测量。
- 真实死亡标记＋state12、rest 锚点错版回退／恢复、20 tick 效果自然到期通过。服务端 Scylla 1.4×3.0、maxHP390 和 Maledictus 1.5×3.0、maxHP420 均不变；效果添加／移除不启动 Kuso 动作或暂停 AI。
- 实际 GPU 待机／攻击／睡眠截图与正常世界预览已检查，无网格塌陷、重复身体或失效 consumer 异常；睡眠仅锚为原生设计。未开光影，不宣称所有 shader pack 已验证。
- 独立目录不安装 Cataclysm/Lionfish 时，已进入 TitleScreen，企鹅 skin/clip 加载正常；没有新增硬依赖。

通用基底公式 `D × A × T(bind)` 保留给躯干／腿；Scylla 翼部再使用本 profile 的原生前屈关节标定，肩接缝位置不变，肘和握持点来自当前原生帧。Maledictus 全部关节仍沿用其原有公式。

证据：工作区 `.workflow/projects/kuso-penguin/runtime/scylla-20261028/` 中的
`runtime-actions.txt`、`maledictus-regression/runtime-actions.txt`、`server-effect.txt`、
`grip-audit.txt`、`special-cases.txt`、`expiry.txt`、`absence.txt` 及 `preview-*.png`／`world-0.png`。
所有测试 Boss 为仅客户端或未插入世界的 detached actor；未保存服务端 Boss。验收结束恢复和平／生存并关闭自行启动的客户端。未提交 Git。

Passed: 518 Scylla render/state/tier cases, 180 Maledictus regressions, 160 visible grip comparisons
(max error 2.708e-6 model pixels), 8 native-hidden stages, native-part pose isolation, real death flag,
pinned-rest fallback, effect expiry and server dimensions/HP/AI exclusion. Actual shader-free GUI
and world rendering was inspected. A separate client reached the title screen without Cataclysm
or Lionfish, with penguin resources loaded. Comprehensive AI combat and every shader pack remain
outside this validation. Final artistic appearance awaits user acceptance.

The base D*A*T(bind) expression applies to the shared body/leg mapping; Scylla's wings use its
calibrated native bent frames. Native sockets and parts are never moved. All runtime tools are
external QA probes and are not bundled. Gradle test is NO-SOURCE, not a behaviour test suite.
