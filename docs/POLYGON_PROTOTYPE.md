# Low-poly mesh prototype

This document describes the experimental low-poly body preview added on top of the accepted
block penguin. The block model remains the default; the prototype is opt-in and now has three
mesh tiers. The block model is retained as the legacy comparison/fallback and receives no new
art iteration: the current modeling direction is this polyhedral mesh, one solid colour per
triangle, with colour boundaries formed by real mesh edges rather than a texture mask.

## Tiers and switching

| Tier | Triangles | Torso faces | Runtime JSON |
| --- | --- | --- | --- |
| Block (default, legacy fallback) | accepted cube model | n/a | none |
| High | 492 | 288 | `models/entity/penguin_lowpoly.json` |
| Medium | 308 | 144 | `models/entity/penguin_lowpoly_medium.json` |
| Low | 216 | 80 | `models/entity/penguin_lowpoly_low.json` |

The Low tier is 216 triangles, not the earlier 200: it gained a shoulder ring so the black/white
shoulder boundary could also be real geometry. The torso geometry therefore did change this round;
the wing and foot silhouettes and bind pivots did not.

- Press `F8` in-game to cycle the global client-side preview:
  **Block -> High -> Medium -> Low -> Block**. The key is rebindable in
  **Options > Controls > Kuso Penguin**.
- A short action-bar message names the current tier, the key and the next tier, so the cycle is
  explicit without requiring a second key.
- Start with the high tier selected by launching the client with
  `-Dkuso_penguin.meshPreview=true` (for example in the run configuration's VM options). The
  property only chooses the initial state; it is not persisted after the game starts.
- Selecting Block restores the original block body immediately. Removing the penguin effect still
  restores the complete original entity, as before.
- The preview applies only while the existing penguin effect replacement is active, for the player
  and the five supported mob types. It does not change which entities are supported.

## Runtime path

- `PenguinBodyLayer` keeps the single existing pipeline: `shouldReplace`, pose adapter,
  `PenguinProportions`, `resetPose`/`apply`, overlay and the shared body scale/translation.
- Only the selected texture and draw call differ. With a mesh tier selected the layer draws the
  five cached mesh roots through the corresponding `PenguinBodyModel` parts'
  `translateAndRotate`; the original head and its decorations are still drawn by the vanilla
  renderer.
- All three tiers reuse the same `PenguinMeshModel.parse(Reader)` and the same instance
  `render(...)`; there is no per-tier rendering code path. `current()` only selects an entry from
  the reloaded cache. While Block is selected it still returns the high entry so the already-loaded
  cache remains observable, and the unchanged `isEnabled()` guard in the body layer keeps the
  block body on screen.
- The mesh uses the existing `RenderType.entityCutoutNoCull`, packed light/overlay and the same
  pose matrix. Each source triangle is submitted as a degenerate quad so standard entity QUADS
  cannot stitch vertices between triangles.
- Normals are the per-face normals from the exported model (flat shading). There is no weighted
  skinning: each triangle belongs to exactly one of the five animation roots and follows that
  root's vanilla-derived transform.

## Resource contract and caching

The three JSON files use schema exactly `1` with exactly five unique parts: `body`, `right_wing`,
`left_wing`, `right_foot`, `left_foot`. Every part has a three-number bind pivot and a list of
triangles; every triangle has three vertices and every vertex has eight numbers
`[x, y, z, u, v, nx, ny, nz]`. The three UV pairs of one triangle must be identical and must sample
one solid colour swatch in the shared atlas; no triangle may span two colours. Every part follows
the same per-face rule, so the torso, wings, feet and ribbon are each made of single-colour faces,
and the black/white torso boundary is carried by shared mesh vertices and edges rather than by a
texture curve. The pivot must match the existing Java animation bind pivot for that root within
`1e-4`:

| Part | Bind pivot (Java model pixels) |
| --- | --- |
| `body` | `[0, 0, 0]` |
| `right_wing` | `[5.45, 2.6, 0.2]` |
| `left_wing` | `[-5.45, 2.6, 0.2]` |
| `right_foot` | `[3.3, 15, 0]` |
| `left_foot` | `[-3.3, 15, 0]` |

These are the bind pivots of the existing Java `PenguinBodyModel` roots (the native right-wing
root is at `+5.45`, not `-5.45`). The exported pivot is the Blockbench origin converted to Java
model space as `[-BBx, 24-BBy, BBz]`; triangle vertices are local to that pivot. The exporter
uses Blockbench Modded Entity's native mapping for both: absolute `(-BBx, 24-BBy, BBz)` and local
`(px-vx, py-vy, vz-pz)`. X and Y are both reflected, so handedness is unchanged: the saved
Blockbench vertex order is kept and face normals are recomputed from the converted local
vertices. The existing mesh, UV, finite-number and degenerate-triangle checks still apply.

All vertex numbers must be finite both as parsed doubles and after conversion to `float` (so a
value such as `1e100` is rejected instead of becoming infinity). UVs must be normalized and each
normal must be non-zero and unit length within `0.001`; the loader never silently normalizes a bad
normal. All three tiers are validated with the same checks.

The JSON is parsed only by the client reload listener registered on the mod event bus, during the
initial resource load and every resource reload (`F3+T`, resource-pack changes, server pack).
`prepare` parses and validates all three tiers, then `apply` atomically replaces one immutable tier
map. A malformed or missing tier fails the whole reload; no partial map or silent fallback to
another tier is installed, and the previously applied cache is not replaced by a mixed one. Draw
calls and `F8` switching only read the cached immutable triangle lists: no file IO and no JSON
parsing. All entities share one cached map and the same `penguin_lowpoly.png` texture.

## Authoring and export

The three prototypes are authored as Blockbench sources
(`models/prototypes/penguin_lowpoly.bbmodel`,
`penguin_lowpoly_medium.bbmodel` and `penguin_lowpoly_low.bbmodel`). They share the embedded
`penguin_lowpoly.png` texture. The pre-revision curved-UV versions of all three sources, their
runtime JSONs and the old shared atlas are preserved unchanged in
`models/archive/curved-uv-20261020/`; open that copy for comparison or restore the whole set
together. The reproducible export flow is:

1. Save the relevant Blockbench project(s).
2. From the repository root run `python tools/export_penguin_mesh.py` to export all three tiers,
   or pass `--level high`, `--level medium` or `--level low` to select which tier JSON is written.
   The exporter always parses all three sources first and reads their saved `.bbmodel` coordinates,
   UVs and embedded texture, then writes the selected runtime mesh JSON file(s) and the shared
   texture.
3. Run `python tools/export_penguin_mesh.py --check` (or add `--level <tier>`) to regenerate the
   selected tiers in memory and compare against the checked-in resources; a mismatch fails the
   check.

Before writing or checking anything, the exporter verifies that **all three** sources embed the
same texture, even when `--level` selects only one tier; a mismatch fails instead of overwriting
the shared atlas from a divergent source. It follows Blockbench Modded Entity's native export
coordinates: absolute `(-BBx, 24-BBy, BBz)`, root pivot with the same conversion, and local
`(px-vx, py-vy, vz-pz)`. Because X and Y are both reflected, triangle handedness and the saved
vertex order are unchanged; normals are recomputed from the converted local vertices. The current
sources use true geometric colour separation: every triangle's three UVs point at one solid swatch,
the torso's black/white boundary is a real mesh edge with shared boundary vertices, the front
shoulder is wider and flatter, and the white faces are narrow at the chest and widen over the belly.
The old curved texture mask and speckles are removed from both the sources and the atlas. The
archive under `models/archive/curved-uv-20261020/` keeps the earlier curved-UV sources (492/308/200),
runtime JSONs and atlas as an unmodified copy, so it can be opened for comparison or copied back as
a complete set; it is not the active model. The exporter supports only the five fixed roots and
their direct, unrotated mesh geometry. Bake rotations in Blockbench first and triangulate before
export. Sub-bones and weighted/skinned geometry are not supported.

## Scope

This is a visual preview only. It does not change the hitbox, attributes, AI, effect networking,
supported entity list, first-person view, or the accepted block geometry and textures. It adds no
new mod dependency and does not use GeckoLib.

## Verification status

### English summary

**Current round (verified).** On Minecraft 1.21.1 / NeoForge 21.1.243 at 1280x720, GUI scale 3,
FOV 70, no shaders, in the isolated `KusoPenguin-Validation` world, the per-face geometry round
passed live review for the player and all five supported mobs. The tiers submit 1,968/1,232/864
vertices for 492/308/216 triangles (`a,b,c,c`); head size, the 2.5-head proportion, foot anchor and
the five-root vanilla pose mapping passed. The zombie's left/right wing bind still matches the native
`PenguinBodyModel`, and the `F8` cycle, a real resource reload, effect-removal restore and Low-tier
walking (no detachment) passed. Geometry audit confirmed every triangle has three identical UVs
sampling one solid colour, the torso is closed and manifold with the black/white boundary on shared
edges, the shoulder root and leg joints sit inside the torso, and leg columns still overlap at
+/-1.2 rad. The archive matches the previous delivery hash, and upstream reviewed the
`panels-high/medium/low`, `panels-low-side` and `panels-player-walk` captures.

Evidence: `D:/Moddevelop/.workflow/projects/kuso-penguin/work/face-colors-test/`; asset audit
`C:/Users/10217/Documents/Codex/audit_penguin_panels.py`. No FPS benchmark was run, and shader
compatibility remains prior user feedback only; shaders were not re-tested this round. The isolated
validation world was restored to peaceful and exited normally.

**Historical round — curved-UV mask (verified before the current geometry revision).** In that
round, on Minecraft 1.21.1 / NeoForge 21.1.243 at 1280x720, GUI scale 3,
FOV 70, no shaders, in the isolated `KusoPenguin-Validation` world, all three tiers passed live
review for the player and all five supported mob types (skeleton, zombie, villager, piglin,
zombified piglin). Actual vertex capture submitted 1,968 vertices for High, 1,232 for Medium and
800 for Low, consistent with that round's 492/308/200 triangles submitted as `a,b,c,c`. The head-only
Y translation, original head size, shared 2.5-head scaling and foot anchor were unchanged.

Vanilla `setupAnim` mapping onto the five roots and pose reset passed. At every tier the zombie's
left/right wing bind matches the real `PenguinBodyModel`; the raised-arm direction differs from the
original cube sub-bone wing-tip direction by about `1.728` degrees because of the sculpt, inside
the 5-degree test threshold, and the static left/right local X axes both point outward. Same-view
screenshots of an actual `setAggressive(true)`, NoAI zombie attack pose compared cube/high/medium/low
and showed no inward reversal. The animation was not reworked and this is not claimed as zero
angular error.

In that historical round, all Blockbench geometry vertex coordinates were unchanged; only UVs and
the shared curve mask changed. Every runtime vertex matched the native `(-BBx, 24-BBy, BBz)`
convention, normal winding stayed consistent, and the triangle counts stayed 492/308/200. Pattern
pixel audit and live screenshots confirmed black front shoulders and wing roots, a cream chest
centre and a white lower belly that widens downward. Those results describe the curved-mask geometry,
not the current per-face geometry.

The `F8` four-state cycle worked, a real resource reload replaced the cached mesh, and walking
screenshots showed no detachment. After clearing the effect, replacement was false for all five
mobs and the player. A single-level export failed before writing anything when an unselected tier
embedded a different texture, and `--check all` passed.

Evidence: `D:/Moddevelop/.workflow/projects/kuso-penguin/work/mesh-refine-test/` (`audit.txt`,
`audit-player.txt`, `proportions-HIGH/MEDIUM/LOW.txt`, `rig-audit-HIGH/MEDIUM/LOW.txt`,
`reload.txt`, `inspect.txt`, `inspect-player.txt`, and the `zombie-cube/high/medium/low-raised`,
`shoulders-high/medium/low` and `player-walking-refined` captures). The asset/mask audit script is
`C:/Users/10217/Documents/Codex/audit_refined_penguin.py`, already run upstream successfully.

The live-client record below is **also historical**: it predates the three-tier selector and covers
the original two-state (off/on) preview with the 492-triangle high mesh. It remains evidence for the
shared pipeline, the high mesh, animation and caching behaviour, but it does not represent the
curved-mask round above or the current per-face geometry revision.

Historical checks passed on Minecraft 1.21.1 / NeoForge 21.1.243 at 1280x720, GUI scale 3, FOV 70,
no shaders, in the isolated `KusoPenguin-Validation` world. The preview defaulted to disabled while
the mesh cache was already loaded; an injected `KeyMapping` click through `ClientTickEvent.Post`
enabled `F8` and a second click disabled it, restoring the block body.

The player and all five supported mob types passed live checks of vanilla `setupAnim` driving the
existing five roots, alternating leg motion and pose reset. Rendering captured 1,968 submitted
vertices for 492 triangles (each triangle submitted as `a,b,c,c`); the shared 2.5-head scaling,
foot anchor and the head-only Y shift matched the original renderer. Ray inside/outside tests on
the upstream triangle meshes, sampling every 0.5 pixels around the joints at `+/-1.2`, `+/-0.8`
and 0 radians, found retained torso and leg-column overlap; player walking showed no detachment.

A real resource reload replaced the cached mesh instance while the toggle stayed enabled. The
valid model parsed, and seven malformed variants were rejected: schema `1.9`, wrong pivot, float
overflow, non-unit normal, missing root, duplicate root and wrong triangle vertex count. After
removing the effect, replacement was false for all five mobs and the player. Night/day captures
showed the body dimming and brightening with the environment, so it is not self-lit.

Evidence: `D:/Moddevelop/.workflow/projects/kuso-penguin/work/mesh-test/`, including
`default-state.txt`, `audit.txt`, `audit-player.txt`, `proportions.txt`, `parser-audit.txt`,
`reload.txt`, `mesh-state.txt` (last state false), `inspect.txt`, `inspect-player.txt`, plus
`cube-comparison`, `mesh-front/side/back`, `mesh-player-standing/walking`,
`mesh-night-light/day-light` and `cube-restored` captures.

Known boundaries: this is a rigid five-root part mesh with no weighted skinning. Iris and other
shaders, and all possible animations, were not verified in the curved-mask historical round, and
shaders were not re-tested in the current per-face geometry round. Shader compatibility is
user-reported from an earlier round only; the existing `RenderType` and render path are unchanged. No
FPS benchmark was run, and the lower vertex count is not claimed as a measured performance gain.
`test NO-SOURCE` is not a behaviour test. The controlled hostile-mob check temporarily used normal
difficulty, creative mode, resistance 255, NoAI and disabled natural spawning, then restored
peaceful; it was an isolated validation world, not the user's save.

### 中文摘要

**本轮已实机验收。** 在 Minecraft 1.21.1 / NeoForge 21.1.243、1280x720、GUI 缩放 3、FOV 70、
无光影、隔离的 `KusoPenguin-Validation` 世界中，玩家及五种受支持生物的三档通过实机检查。高/中/低
分别提交 1,968/1,232/864 个顶点，与 492/308/216 个三角面按 `a,b,c,c` 提交一致；头部大小、2.5
头身比例、落地锚点及五根原版姿态映射均通过。僵尸左右翼 bind 仍与 native `PenguinBodyModel`
一致；`F8` 循环、真实资源重载、清除效果恢复及低档行走（无脱节）均通过。几何审计确认每面三个
UV 相同且只采样一种纯色，躯干闭合流形、黑白边界共用边，肩根与腿关节位于躯干内，`+/-1.2` rad
时腿柱仍重叠；archive 与上一版交付哈希相同，上游已查看 `panels-high/medium/low`、
`panels-low-side`、`panels-player-walk` 截图。

证据：`D:/Moddevelop/.workflow/projects/kuso-penguin/work/face-colors-test/`；资产审计脚本
`C:/Users/10217/Documents/Codex/audit_penguin_panels.py`。未做 FPS 基准；光影兼容仅为此前用户
反馈，本轮未新测光影；隔离验证世界已恢复和平并正常退出。

**历史轮次——曲线 UV mask（在当前几何修订前已实机验证）。** 当时在 Minecraft 1.21.1 /
NeoForge 21.1.243、1280x720、GUI 缩放 3、FOV 70、
无光影、隔离的 `KusoPenguin-Validation` 世界中，玩家及骷髅、僵尸、村民、猪灵、僵尸猪灵五种
受支持生物的三档均通过实机检查。实际顶点捕获为高档 1,968、中档 1,232、低档 800 个提交顶点，
与那一轮的 492/308/200 个三角面按 `a,b,c,c` 提交一致；仅头部 Y 平移、原头大小、统一 2.5 头身
比例和落地锚点均保持不变。

原版 `setupAnim` 到五根映射及姿态重置检查通过。每档僵尸左右翼 bind 均与真实
`PenguinBodyModel` 一致；因造型原因，抬臂方向与原方块子骨骼翼尖方向相差约 `1.728` 度，在 5
度测试阈值内，静态左右局部 X 均向外。实际 `setAggressive(true)`、NoAI 僵尸攻击姿态在
cube/high/medium/low 同视角截图已对照，无反向内收；该轮没有重做动画，也不声称角度严格零误差。

那一历史轮次中，全部 Blockbench 几何顶点坐标未变，仅 UV 与共享曲线 mask 改变。全部运行顶点按
原生 `(-BBx, 24-BBy, BBz)` 对照通过，法线绕序一致，高/中/低为 492/308/200 个三角面。花纹
像素审计和实际截图确认前肩/翼根为黑、胸中心奶白、下腹白色向下渐宽。这些结果描述的是曲线 mask
几何，不是当前按面几何。

`F8` 四态循环正常；真实资源重载成功替换缓存；行走截图无脱节；清除效果后五种生物与玩家的
替换均为 false。未选档贴图不同时，单档导出在任何写入前提前失败；`--check all` 通过。

证据：`D:/Moddevelop/.workflow/projects/kuso-penguin/work/mesh-refine-test/`，包括
`audit.txt`、`audit-player.txt`、`proportions-HIGH/MEDIUM/LOW.txt`、
`rig-audit-HIGH/MEDIUM/LOW.txt`、`reload.txt`、`inspect.txt`、`inspect-player.txt`，以及
`zombie-cube/high/medium/low-raised`、`shoulders-high/medium/low`、`player-walking-refined`
截图。资产/遮罩审计脚本为 `C:/Users/10217/Documents/Codex/audit_refined_penguin.py`，已由
上游执行成功。

以下实机记录是**历史记录**：它早于三档选择器，仅覆盖原来的二态（关/开）预览和 492 三角面
高网格。它仍是共享渲染流程、高网格、动画与缓存的证据，但不代表上方本轮三档、坐标与贴图的
检查结果。

历史检查在 Minecraft 1.21.1 / NeoForge 21.1.243、1280x720、GUI 缩放 3、FOV 70、无光影、
隔离的 `KusoPenguin-Validation` 世界中通过。默认关闭时网格缓存已加载；模拟 `KeyMapping`
点击经 `ClientTickEvent.Post` 使 `F8` 启用，再次点击关闭并恢复方块版。

玩家及五种受支持生物均通过原版 `setupAnim` 驱动现有五根、交替摆腿和姿态重置的实机检查。
渲染捕获 492 个三角面对应的 1,968 个提交顶点（每面按 `a,b,c,c` 提交）；统一的 2.5 头身
缩放、落地锚点及仅头部的 Y 平移与原渲染一致。对上游三角网格做射线内外测试，在关节周围
按 0.5 像素采样、腿角为 `+/-1.2`、`+/-0.8` 和 0 弧度时，躯干与腿柱仍保持重叠；玩家行走
未见脱节。

真实资源重载后缓存实例已替换，开关仍保持启用。有效模型解析通过；schema `1.9`、pivot
错误、float 溢出、非单位法线、缺失根、重复根和三角面顶点数错误七类坏数据均被拒绝。清除
效果后五种生物与玩家的替换均为 false。日夜截图显示身体随环境变暗/变亮，并非自发光。

局限：这是五根刚性分部件网格，无权重蒙皮；本轮未新测 Iris 或其他光影，也未覆盖全部动作。
光影兼容仅为上一轮用户实机反馈，现有 `RenderType` 与渲染通路未改变；未做 FPS 基准，不将
顶点数减少等同于实际性能收益。`test NO-SOURCE` 不是行为测试。受控敌对生物检查临时使用
普通难度、创造模式、抗性 255、NoAI 并关闭自然生成，结束后恢复和平；使用的是隔离验证世界，
不是用户存档。
