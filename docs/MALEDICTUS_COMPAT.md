# Maledictus 视觉替换 / Visual replacement

## 中文

### 范围

本兼容只替换 Cataclysm `maledictus` 的可见身体。原生头部（含角）、当前武器、灵翼、
攻击/抓取/死亡状态、粒子/VFX、物理、服务端 AI、伤害、碰撞箱和尺寸继续由 Cataclysm 管理。固定目标为
Cataclysm 1.21.1-3.33 与 Lionfish 3.1；只验证该 pinned 模型/层字节码，不新增 Cataclysm
编译依赖，模型通过精确类名和注册名识别，其他 Cataclysm 版本不在支持范围。

### 视觉-only 门禁

只有效果存在、网格预览开启、Cataclysm 已加载、模型类名精确匹配且完整层级/preflight
通过时才替换。隐身、发光、旁观、幼体以及缺少模型/网格/层级时完整回退；死亡和作为载具
仍可替换。`PenguinPoseSync.start` 对 `cataclysm:maledictus` 直接返回 false，因此不会启动
TAUNT/KICK/DEATH，也不会进入普通动作、趴下或舞蹈集合。效果标签只通过
`data/cataclysm/tags/mob_effect/effective_for_bosses.json` 的 `replace:false` 追加
`kuso_penguin:penguin`，不覆盖或扩大其他 Boss 免疫。

### 矩阵与渲染顺序

对每个关节沿 `root→roots→berserker→…` 完整父链，绑定姿势用 `getInitialPose()`，当前
姿势读取平移、旋转和缩放，得到 `B`、`F` 与 `D=F×inverse(B)`。当前对齐为：

```text
A = T(0, 24, -3.4) × S(37/24) × T(0, -24, 0)
world[j] = (D × A) × T(penguinBind[j])
```

其中 Z=-3.4 位于外层平移，避免被 `37/24` 缩放误乘。这里的 `D=F×inverse(B)` 描述传入的
上身/肢体朝向采样；最终腿部还会经过共享 `PenguinLegPose` 的短腿重绑定（解剖控制
20/21.3/22.3 重绑定到不可变原 clip bind 15/20.5/22.3），而不是直接照搬人形髋点平移。
随后复用
`PenguinRenderContext.fromNativeRig(world, identity, bind)` 与已烘焙的 `currentSkin`；
不重跑 `setupAnim`，不修改任何原生 `ModelPart`。左右沿用企鹅历史命名：正 X 的
`right_wing`/`right_hip` 跟随原生 LEFT 肩/臂/前臂与 LEFT 腿/前腿，负 X 的
`left_wing`/`left_hip` 跟随 RIGHT；原生无独立脚骨，故膝/脚共用 `front_leg` 的 `D`。

主 pass 先完成原生头/可见武器/灵翼的提交，再向 `MultiBufferSource` 请求企鹅材质
consumer。这个顺序是必需的：真实 `BufferSource` 可能在请求另一种 RenderType 时
flush/复用 builder；先请求企鹅材质会使原生 consumer 失效。该问题由实际 GUI 渲染发现并
修正，最终正常世界预览与 GUI 均通过。

原生主 pass 和 ghost pass 共用同一个保留几何 helper。企鹅身体只在主 pass 画一次；ghost 层只
重放原生头部、当前可见的 `right_mace`/`left_mace`/`bow`/`halberd`（含子件）和
`left_wing`/`right_wing` 灵翼，并继续使用原生 ghost 材质、透明度、受伤/死亡颜色与
`getWeapon()` 可见性。旧躯干、骨盆、人形四肢、肩甲和前布不绘制。外部头/武器/灵翼保持
原生尺寸与动画，仅身体被替换；没有动作、AI、伤害或碰撞箱缩小修改。

### 开发环境依赖

本机普通 `runClient` 的 `run/compat-client/mods/` 现包含 Cataclysm 3.33 与 Lionfish 3.1，
同时保留原有可选 TLM、YSM、Iron 等开发 JAR。它们只用于本地开发/验收，不写入
`neoforge.mods.toml`，不 jarJar、不打包进发布 JAR，也不附带 Cataclysm/Lionfish 资源。
发布包仍是可选兼容：缺少 Cataclysm 时不替换，也不新增运行时依赖。

- Cataclysm 3.33：<https://modrinth.com/mod/l_enders-cataclysm/version/PsPYpoCC>
  （SHA256 `371956d0c2e3b56729ee93b955e936875d210e372fe734cdb74326d89ef79875`）
- Lionfish 3.1：<https://modrinth.com/mod/lionfish-api/version/fTRMVgyZ>
  （SHA256 `aeebf0692650afbbc78259c0d4e3d5b7edf037707e8ae5d56045066d83a19cf6`）

### 验证状态

以下为已完成的实际验证，证据位于仓库外的
`../.workflow/projects/kuso-penguin/runtime/maledictus-20261028/`：

- `runtime-actions.txt`：180 个渲染采样 PASS。HIGH 覆盖全部 34 个原生状态 × 3 个时间点；
  MEDIUM/LOW 各覆盖 12 个代表状态 × 3 个时间点。另含三种武器、A/B/A 姿势隔离、
  嵌套抓取乘客、效果移除/原生回退与 BLOCK 回退。
- 三档身体顶点数分别为 2832/2000/1728，无重复企鹅 skin；原生主/ghost 保留几何计数
  按武器为 720/576/792，完整回退时原生计数 1272。源网格接缝位置一致（0 像素），
  所有渲染坐标有限且在界内。
- 实际 GUI 截图和 `world-high.png` 正常世界场景已通过真实 GPU/BufferSource 渲染；
  头部、武器、灵翼完整。该世界预览 actor 仅客户端存在，没有服务端 Boss 或存档 Boss。
- `server-effect.txt`：真实服务端 `canBeAffected`、效果添加/移除通过；未启动
  TAUNT/KICK/DEATH，不进入舞蹈，AI 未暂停；尺寸 `1.5×3.0`、生命 `420` 不变。
- 源码 `classes build` 通过；运行时测试的构建 JAR SHA256 为
  `9717c7a098275493b9a5ebd0773d6adb7113303e1ef30a86857c0ab7c01be121`，
  已与构建产物核对。`test` 为 `NO-SOURCE`，不代表存在自动行为测试。
- 短腿变形扩展检查：174 个真实 Boss 动作变形采样加 6 项武器/隔离/抓取/恢复检查；最大边长
  因子 `3.027`、最小面积比 `0.05136`，髋锚点与固定解剖长度 `1.3 + 1.0` 像素误差小于
  `1e-4` 像素。矩阵公式中的 `D=F×inverse(B)` 仍描述传入上身/肢体朝向；最终腿另经共享
  短腿重绑定，而不是盲用人形髋点平移。原有 180 个渲染/状态采样计数保留，详见
  `docs/LEG_DEFORMATION.md`。
- 本兼容不声称完成自动全战斗测试、真实 AI 战斗伤害/抓取或多人场景验证；上述采样是
  渲染/状态探针，不是完整战斗覆盖。`absence.txt` 另行确认：未安装 Cataclysm、Lionfish、
  Iron 或 Gecko4（保留 TLM/YSM）时，可以进入标题界面，网格资源加载与兼容入口回退均正常。

## English

### Scope

This compatibility replaces only the visible body of Cataclysm `maledictus`. The native head
(including horns), current weapon, spiritual wings, attack/grab/death state, particles/VFX,
physics, server AI, damage, collision box and dimensions remain owned by Cataclysm. Pinned targets are Cataclysm
1.21.1-3.33 and Lionfish 3.1. Only the pinned model/layer bytecode is validated. No Cataclysm
compile dependency is added; the model is identified by exact class name and registry id, and
other Cataclysm versions are outside the supported range.

### Visual-only gate

Replacement requires the effect, enabled mesh preview, Cataclysm loaded, the exact model class
and a complete hierarchy/preflight. Invisible, glowing, spectator, baby, or missing
model/mesh/hierarchy states keep the complete native renderer; death and being a vehicle remain
supported. `PenguinPoseSync.start` returns false for `cataclysm:maledictus`, so TAUNT/KICK/DEATH
never start and the boss never enters the ordinary action, prone or dance sets. The effect tag
merges only `kuso_penguin:penguin` with `replace:false` in
`data/cataclysm/tags/mob_effect/effective_for_bosses.json`; no other boss immunity is replaced
or widened.

### Matrices and draw order

For each joint, walk the full `root→roots→berserker→…` ancestor chain; compose `B` from
`getInitialPose()` and `F` from the current translation/rotation/scale, then
`D=F×inverse(B)`. The current alignment is:

```text
A = T(0, 24, -3.4) × S(37/24) × T(0, -24, 0)
world[j] = (D × A) × T(penguinBind[j])
```

Z=-3.4 is part of the outer translation so it cannot be accidentally multiplied by `37/24`.
Here `D=F×inverse(B)` describes the incoming upper-body/limb orientation samples; the final legs
additionally undergo the shared `PenguinLegPose` short-leg rebind (anatomical controls
20/21.3/22.3 rebound into the immutable legacy clip bind 15/20.5/22.3), rather than blindly
reusing human-pivot translations.
The bridge then reuses `PenguinRenderContext.fromNativeRig(world, identity, bind)` and the
already-baked `currentSkin`; it never reruns `setupAnim` or mutates a native `ModelPart`.
Historical penguin sides are retained: positive-X `right_wing`/`right_hip` follow native LEFT
shoulder/arm/front_arm and LEFT leg/front_leg; negative-X `left_wing`/`left_hip` follow RIGHT.
The pinned native model has no separate foot bone, so knee/foot share the `front_leg` delta.

The main pass submits the retained native head/visible weapons/spirit wings BEFORE requesting
the penguin material consumer from `MultiBufferSource`. This order is required because a real
`BufferSource` may flush or reuse its builder when another RenderType is requested; requesting
the penguin material first invalidates the native consumer. The bug was found by actual GUI
rendering and fixed; the final normal-world preview and GUI both pass.

The main and ghost passes share one retained-geometry helper. The penguin body is drawn
exactly once in the main pass; the ghost layer replays only the native head, currently visible
`right_mace`/`left_mace`/`bow`/`halberd` terminals (with children), and the
`left_wing`/`right_wing` spiritual wings, keeping the native ghost material, alpha, hurt/death
color and `getWeapon()` visibility. Old torso, pelvis, humanoid limbs, shoulder armor and front
cloth are not drawn. The external head/weapons/spirit wings keep their native size and
animation; only the body is replaced. No action, AI, damage or collision-box shrink change is
made.

### Development dependencies

On this machine, the normal `runClient` now has Cataclysm 3.33 and Lionfish 3.1 in
`run/compat-client/mods/`, while the existing optional TLM/YSM/Iron development jars remain.
They are local development/validation artifacts only: they are not declared in
`neoforge.mods.toml`, not jarJar'd, not packaged into the release JAR, and no
Cataclysm/Lionfish assets are bundled. The published mod remains optionally compatible:
without Cataclysm it does not replace the boss and adds no runtime dependency.

- Cataclysm 3.33: <https://modrinth.com/mod/l_enders-cataclysm/version/PsPYpoCC>
  (SHA256 `371956d0c2e3b56729ee93b955e936875d210e372fe734cdb74326d89ef79875`)
- Lionfish 3.1: <https://modrinth.com/mod/lionfish-api/version/fTRMVgyZ>
  (SHA256 `aeebf0692650afbbc78259c0d4e3d5b7edf037707e8ae5d56045066d83a19cf6`)

### Verification status

The following actual results are recorded outside the repository under
`../.workflow/projects/kuso-penguin/runtime/maledictus-20261028/`:

- `runtime-actions.txt`: 180 render samples PASS. HIGH covers all 34 native states at 3
  timestamps; MEDIUM/LOW each cover 12 representative states at 3 timestamps. It also includes
  the three weapons, A/B/A pose isolation, a nested grab passenger, effect removal/native
  fallback and BLOCK fallback.
- Body vertex counts are exactly 2832/2000/1728 with no duplicate penguin skin. Native
  main/ghost retained counts are 720/576/792 depending on weapon; removal fallback shows the
  full native count 1272. Source seam positions coincide (0 pixels), and all rendered
  coordinates are finite and bounded.
- The real GUI screenshots and the `world-high.png` normal-world scene render correctly through
  the actual GPU/BufferSource path; head, weapon and spirit wings are complete. That world
  preview actor exists only client-side, with no server boss and no saved boss.
- `server-effect.txt`: real server `canBeAffected` and effect add/remove pass; no
  TAUNT/KICK/DEATH starts, no dance eligibility, AI not paused; dimensions `1.5×3.0` and health
  `420` are unchanged.
- Source `classes build` passes. The runtime-tested build JAR SHA256 is
  `9717c7a098275493b9a5ebd0773d6adb7113303e1ef30a86857c0ab7c01be121` and was checked against
  the built artifact. `test` is `NO-SOURCE` and is not evidence of an automated behaviour
  suite.
- The short-leg deformation extension adds 174 real boss motion deformation samples plus 6
  weapon/isolation/grab/restoration checks; maximum edge factor `3.027`, minimum area ratio
  `0.05136`, and hip-anchor / fixed anatomical length `1.3 + 1.0` pixel errors below `1e-4`
  pixels. `D=F×inverse(B)` still describes the incoming upper-body/limb orientation; the final
  legs additionally use the shared short-leg rebind instead of blindly reusing human-pivot
  translations. The existing 180 render/state sample count remains; see
  `docs/LEG_DEFORMATION.md`.
- This compatibility does not claim automated full-combat testing, real AI combat damage/grab,
  or multiplayer validation; the samples above are render/state probes, not complete combat
  coverage. Separately, `absence.txt` confirms an actual title-screen startup without
  Cataclysm, Lionfish, Iron or Gecko4 (TLM/YSM remain): mesh resources load, and both
  native/ghost compatibility entry points safely decline replacement.


## Scylla 共享提取回归 / Shared-rig regression

2026-10-28：祖先矩阵／rest 路径与保留件绘制提取为 `NativeModelPartRig` / `NativeRetainedGeometry`，Maledictus 的路径、对齐、层与短腿处理保持原样。新构建完成 180 项原有动作／武器／抓取乘客／原生回退采样。Scylla 的前屈翼标定仅位于 `ScyllaCompatClient`，不进入 Maledictus。详见 `SCYLLA_COMPAT.md`。

The shared helper extraction passed 180 existing Maledictus render regressions. Scylla-only wing calibration does not affect this boss.
