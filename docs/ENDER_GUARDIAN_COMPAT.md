# Ender Guardian Compat / 末影守卫兼容

Status / 状态: **Developer checks completed / 开发检查完成; user artistic review pending / 待用户观感验收**. Fixed target: Cataclysm `1.21.1-3.33` / 固定目标版本。No upstream acceptance is claimed here. / 本文不代表上游验收通过。

Runtime evidence / 运行证据: `D:/Moddevelop/.workflow/projects/kuso-penguin/runtime/guardian-20261028`.

## Pure visual / 纯视觉

The exact entity `cataclysm:ender_guardian` and exact model
`com.github.L_Ender.cataclysm.client.model.entity.Ender_Guardian_Model` are replaced only when the
penguin effect, preview and skin/clip are active and the native draw is otherwise eligible. Native
AI, damage, AABB, animations, phase, death and renderer scale are untouched; no Cataclysm or
Lionfish type is imported. Invalid paths/rest values/scales fall back to the complete native draw.
Native invisible/glowing/baby/spectator draws are never replaced. / 仅替换该实体与模型的主渲染；
原生 AI、伤害、碰撞箱、动画、阶段、死亡与渲染缩放全部保留，且不导入灾变／Lionfish 类型。路径、
rest 或缩放不符时完整回退原生绘制；隐身、发光、幼体、旁观者同样回退。

## Parts and mesh / 保留部件与网格

- Full native helmet subtree (`root/lowerbody/upperbody/helmet`), including `helmet/head` and
  `helmet/upperhelmet`: retained under the original matrix, phase visibility and glow material.
  Their `showModel` flags are never used to gate the replacement. / 完整原生头盔子树按原矩阵、阶段
  可见性与发光材质保留，绝不用 head/upperhelmet 可见性决定是否替换。
- Native `left_fist` and `right_fist` retained as terminals. / 原生左右拳套作为保留终端。
- No old torso, armor, upper-arm or leg native geometry is replayed. / 不重放旧躯干、护甲、上臂或腿。
- Independent head-notch mesh: source `models/prototypes/cataclysm/ender_guardian_source.json`,
  exporter `tools/export_ender_guardian_mesh.py`, strict export/reload check. The single baked pilot
  mesh has **2956 vertices / 739 triangles**. The source asset has **475 triangles** (299 body + 20
  per wing + 68 per foot). Original wing baking cuts add **264** triangles; the independent hole
  removes **88.276683 model px²** of the original body area (visual area only, not the physical
  hitbox). Native rest/global bind assets are unchanged. / 独立头部开口网格由上述源文件与导出器
  生成，严格导出／重载检查通过；单体试跑网格 2956 顶点／739 三角，源资产 475 三角（身体 299、
  每翼 20、每脚 68）；原始翼部裁切新增 264 三角，独立开口移除原身体 88.276683 模型平方像素
  （视觉面积，不是碰撞箱）。原生 rest／全局 bind 资产未改。

## Data / 数据约定

- Rest layout: `root(0,24,0)`, `lowerbody(0,-22,0)`, `upperbody(0,-7,0)`,
  `helmet(0,-28,-7)`, `head(0,-4,-8)`, `upperhelmet(0,0,0)`,
  `left_arm(20,-25,0)/left_arm2(8.5,14,0)/left_fist(5.5,14,0)` and mirrored right fields,
  `left_leg(11,-22,0)`, `right_leg(-11,-22,0)` (rot 0, scales 1). / 固定 rest 布局，旋转为 0、
  缩放为 1。
- Delta retargeting: `D = F * inverse(B)`, ground-anchored `A = T(0,24,0) * S(2.25) * T(0,-24,0)`,
  and `world[i] = D * A * T(bind(i))`. Native ground is Y=24 and the scaled top is Y=-30; no
  Ignis-style floating offset is copied. / 采用 D=F·B⁻¹、以原生地面为锚的 A 与不可变 bind；
  不复制 Ignis 的悬浮偏移。
- Side axis: penguin positive-X wing follows the native `left_arm` chain; negative-X follows
  `right_arm`. Wing roots keep the body-aligned origin from the base world but use the native
  arm orientation scaled 2.25; elbow/fist use absolute native frames scaled 2.25. No outward
  offset, so the shoulder stays connected. / 正 X 翼对应原生左臂链，负 X 对应右臂；翼根保留
  body 对齐原点与原生朝向，肘／拳用原生绝对矩阵乘 2.25，不额外外移，肩部保持连接。
- The shared `PenguinLegPose` is applied once by `PenguinAnimationPose`; this adapter does not
  apply it again or change the bind. / 短腿补偿只由 `PenguinAnimationPose` 应用一次。

## Hooks / 注入点

- Main pass: existing five-argument `LivingEntityRenderer` redirect, after the Ignis branch.
  Retained native geometry is submitted into the already-acquired native buffer first, then the
  penguin material is requested and `guardianSkin()` is drawn once. / 主渲染走既有五参数重定向，
  先提交原生保留几何，再申请企鹅材质并只绘制一次 `guardianSkin()`。
- Glow pass: `Ender_Guardian_Layer` only, one concrete four-argument model call; same helmet/fist
  terminals under the layer's own eye texture, no second body. Native `deathTime < 100` is unchanged.
  Config is registered in `kuso_penguin.mixins.json` without duplicates. / 发光层只重定向同一
  四参数模型调用，复用头盔／拳套终端，不生成第二个身体；原生死亡条件不改。

## Developer checks / 开发检查

Runtime QA recorded **520 Guardian cases** across **19 native animations** including idle,
3 menu qualities (one HIGH pilot mesh), helmetless 0/1, 4 timestamps, native geometry subset with
unchanged native pose / removal / re-add, 15 look cases, A/B/A, hidden / glow / BLOCK, and HP0 death
at native deathTime ticks 1/65/99/100/101/160/195 with the original glow cutoff at **100**. **76 fist frame
comparisons** had max **0 model px** error; a rest mismatch restored the native fallback. / 运行检查
覆盖 520 个案例、19 个原生动画（含 idle）、3 档菜单质量（单体 HIGH 试跑网格）、helmetless 0/1、
4 个时间点、原生几何子集且原生姿态不变／移除／重加、15 个转头角度组合、A/B/A、隐藏／发光／BLOCK，
以及 HP0 时 deathTime=1/65/99/100/101/160/195 tick（原生发光截断为 100）。76 组拳套帧对照最大误差
0 模型像素；rest 不符时原生回退已恢复。

Server checks: Guardian `2.5 × 3.8` / max HP **333** / NoAI **false** unchanged; effect add/remove
and **22-tick** expiry, no Kuso action or AI pause; other bosses unchanged. Regression passed:
**1588 Ignis / 518 Scylla / 180 Maledictus**. / 服务端尺寸 2.5×3.8、最大生命 333、NoAI=false 不变；
效果添加／移除与 22 tick 到期正常，未触发 Kuso 动作或暂停 AI；其他 Boss 不变。回归通过：
Ignis 1588、Scylla 518、Maledictus 180。

GPU: shader-free front, oblique side, helmetless and punch frames inspected, plus a client-only
frozen-boss real-world screenshot; absence of Cataclysm/Lionfish passed the title screen with
`guardianSkin` loaded. This is **not** an all-shader or full auto-battle claim. / GPU 检查了无光影
正面、斜侧、helmetless 与出拳帧，并查看真实客户端冻结 Boss 截图；无灾变／Lionfish 时可进标题界面
且 `guardianSkin` 已加载。这里**不**声称覆盖全部光影或全自动战斗。

## Limitations / 限制

User artistic acceptance is still pending; upstream has normally stopped both owned clients and verified the isolated world remains peaceful /
survival. This document does not replace that
review. / 用户观感验收仍待完成；上游已正常关闭两个自建客户端，并核对隔离世界保持和平／生存模式。本文
不能替代该审核。
