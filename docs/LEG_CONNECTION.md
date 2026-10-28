# 连体企鹅腿运行时 / Connected Penguin Legs Runtime

## 中文

### 数据格式（schema 1 / schema 2）
- `schema 1` 保持旧的 8 浮点顶点：模型像素坐标 x/y/z、UV u/v、单位法线 nx/ny/nz。旧资源、旧加载路径和自动蒙皮完全不变。
- `schema 2` 允许顶点为 11 浮点：在旧 8 浮点后追加 `jointA`、`jointB`、`weightB`。关节是 16 骨骼 rig 的整数下标（0..15），`weightB` 为 `jointB` 权重，另一侧权重为 `1-weightB`。单位仍是模型像素，绑定空间仍是各动画根的原始 bind 空间。
- 每个三角面必须“全显式”或“全自动”，加载器拒绝部分绑定的三角形。显式顶点只验证有限性、整数关节和 `[0,1]` 权重，不做静默修正。
- 躯干和翅膀继续使用旧自动权重；只有脚/腿根使用显式权重。骨盆接缝顶点由 sidecar 固定为 pelvis（关节 1），因此接缝随躯干移动，不会在普通行走、蹲下、施法淡入淡出或趴下时被腿根拉开。

### 导出器
`tools/export_penguin_mesh.py` 可选读取 `models/prototypes/penguin_leg_bindings.json`（schema 1，按 high/medium/low 三档、right_foot/left_foot 两个根给出每个源顶点 ID 的 `[jointA,jointB,weightB]`）。有 sidecar 才输出 schema 2；没有 sidecar 时输出与旧版逐字节一致的 schema 1。`--check` 只读比较，不写资源、不写 sidecar；`.bbmodel` 是可编辑源，sidecar 保存稳定顶点 ID 的权重；编辑时应同步维护两者。运行时只读取已导出的 JSON。

### 运行时
- `PenguinMeshModel` 在资源加载时一次性解析并校验 schema、顶点宽度、关节与权重，同时烘焙 `PenguinSkinnedMesh`。显式脚部三角形直接采用给定权重，绕开旧的自动 Y 切分；旧自动躯干/翅膀/legacy 路径不变。
- draw 与 bounds 共用 `PenguinSkinnedMesh` 的两影响变形函数。地面校正同时使用源根标签和实际影响骨骼：骨盆固定的腿根计入躯干控制区域，脚部只计算随腿移动的点，避免错误抬脚或遗漏下腹。
- 普通非动作绘制（`PenguinRenderContext.pose()` 为空）对 schema 2 网格使用纯 `nativeBodyOnly` 16 关节工厂：root 为 identity，body chain 取原生 body matrix，头部增量为 identity。共享 `PenguinLegPose` 随后把骨盆附着的解剖控制帧（20／21.3／22.3）重绑定进不可变的原 16 关节 clip bind（15／20.5／22.3）；因此腿部不再简单跟随独立原生 root 的平移，髋点锚定骨盆而原有角动画保留。原五根 ModelPart 与 rig 资源未改；详见 `docs/LEG_DEFORMATION.md`。它只给新权重提供矩阵，不参与动作选择；head/hand 的既有语义和旧静态 root 运动保持不变。
- 趴下/死亡/舞蹈的落地边界来自同一蒙皮的实际变形点。旧 schema 1 仍走原 rigid 路径。

### 模型与验证
- 高／中／低档为 444 / 308 / 240 个三角面。下腹与两条羽毛腿根共享连接边，脚掌保留原蹼形轮廓；长橙色腿柱被短脚踝替代。每面仍为纯色，上身、翅膀、蝴蝶结与尾部造型保留。
- 修改前的三档 BB、导出 JSON 与调色板保存在 `models/archive/leg-connection-20261027/`，可单独使用导出器 `--root <副本目录> --check` 核验。
- 后续短腿解剖控制修复的三档修改前副本、sidecar 与生成 JSON 归档在 `models/archive/leg-deformation-20261028/`；旧 `leg-connection-20261027` 历史归档保留。控制点、重绑定与整体蹲下算法见 `docs/LEG_DEFORMATION.md`。
- 三档均通过连接边／封口拓扑和权重核验；飞踢 46 帧、死亡 60 帧、嘲讽 86 帧的全部原始动作帧通过新腿部非塌缩检查。
- 实际游戏测试覆盖三档站姿、飞踢、Iron 原生施法、真实潜行（含施法）、趴地滑行及腿根额外 +8 像素三轴位移／1.1 弧度旋转。27 项检查通过；连接边间隙为 0，潜行下界与地面误差小于 0.0001 模型块。
- 后续修复另含 39 个玩家渲染用例、3 个趴下、174 个 Boss 动作变形采样加 6 项武器／隔离／抓取／恢复检查、9 次 death-ghost 绘制与 180 个程序化舞蹈采样；髋锚点与固定骨长误差小于 `1e-4` 像素。边界、最大边长与仍存在的自然压缩褶皱见 `docs/LEG_DEFORMATION.md`。
- 未改变动作关键帧、飞踢伤害／位移／冷却或原生头部、持物语义。死亡／嘲讽逐帧检查属于离线变形验证，不表示全部第三方骨架均已实机验收。

## English

### Format
- Schema 1 keeps the legacy 8-float vertex (model-pixel position, UV, unit normal). Legacy resources and automatic skinning are unchanged.
- Schema 2 optionally uses 11 floats: the same 8 plus `jointA`, `jointB`, `weightB`. Joints are integer indices into the shared 16-joint rig; `weightB` belongs to `jointB`. Units and bind space stay in original penguin model pixels.
- A triangle is entirely explicit or entirely automatic; partially bound triangles are rejected. Explicit joints/weights are validated (finite, integral 0..15, weight 0..1), never auto-clipped.
- Body and wings keep automatic weights. Only foot/leg roots use explicit weights. Pelvis-pinned seam vertices stay on the torso during ordinary walk, crouch, casting fade and prone.

### Exporter
`tools/export_penguin_mesh.py` optionally reads `models/prototypes/penguin_leg_bindings.json` and emits schema 2 only when that sidecar exists; without it the schema 1 bytes are identical to the legacy export. `--check` is read-only and never writes resources or the sidecar.

### Runtime
- `PenguinMeshModel` validates schemas, vertex widths, joints and weights once at load and bakes `PenguinSkinnedMesh`. Explicit foot triangles bypass the old automatic Y cuts.
- Drawing and bounds share the same two-influence deformation. Floor correction uses source tags and effective influencing joints: pelvis-pinned leg seams count with the torso, while moving foot bounds exclude those anchors.
- Outside action clips, schema 2 uses a pure `nativeBodyOnly` 16-joint factory copied from the prepared native parts (root identity, native body matrix for the body chain, identity head delta). The shared `PenguinLegPose` then rebinds pelvis-attached anatomical control frames (`20 / 21.3 / 22.3`) into the immutable legacy 16-joint clip bind (`15 / 20.5 / 22.3`). The legs therefore no longer simply follow independent native-root translations: the hip point is pelvis-anchored while existing angular animation is preserved. The native five ModelParts and rig assets are unchanged; see `docs/LEG_DEFORMATION.md`. The factory does not affect action selection, head/hand semantics or legacy static-root motion.
- Prone/death/dance floor bounds use the same deformed skin points. Schema 1 keeps the old rigid path.

### Models and validation
- High / medium / low use 444 / 308 / 240 triangles. The lower abdomen and feathered thighs share welded attachment boundaries; the webbed footprint remains, with short orange ankles instead of long poles. Faces stay single-colour and the upper body, wings, bow and tail retain their design.
- Original BB sources, runtime JSON and palette are preserved in `models/archive/leg-connection-20261027/`; the exporter can check this archive independently.
- Pre-change copies of the later short-leg anatomical control repair (all tiers, sidecar, generated JSON) are archived in `models/archive/leg-deformation-20261028/`; the older `leg-connection-20261027` history remains intact. Controls, rebinding and the whole-assembly crouch algorithm are documented in `docs/LEG_DEFORMATION.md`.
- All tiers passed topology/binding audits. Every source flying-kick (46), death (60) and taunt (86) frame passed new-leg triangle-collapse checks.
- 27 actual runtime checks covered all tiers: idle, kick, native Iron casting, grounded crouching with/without casting, prone animals and an extra +8-pixel XYZ / 1.1-radian native-leg stress. Weld gaps were zero; crouch floor error stayed below 0.0001 model blocks.
- Later checks add 39 player renderer cases, 3 prone cases, 174 boss motion deformation samples plus 6 weapon/isolation/grab/restoration checks, 9 death-ghost draws and 180 procedural dance samples; hip-anchor and fixed bone-length errors stay below `1e-4` pixels. See `docs/LEG_DEFORMATION.md` for the bounds, maximum edge factors and the natural creases that remain.
- Animation keyframes, kick gameplay and native head/item semantics are unchanged. Death/taunt frame checks were offline deformation tests, not exhaustive third-party skeleton validation.
