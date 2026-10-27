# 施法兼容 / Casting compatibility

## 中文

本兼容覆盖原版玩家模型（vanilla PlayerModel），以及 Iron's Spellbooks 3.16.3 的七个
普通人形 NPC 施法者。玩家带有企鹅效果、`irons_spellbooks:animation` 关联动画层处于
活动状态（含淡入淡出），且 PlayerAnimator 的 applier 也处于活动状态时，原生施法动画
重定向到现有企鹅 16 关节蒙皮骨架。替换受可选依赖门控与模型预检约束；不满足时完整保留
原生渲染。

- 可选依赖：`irons_spellbooks` 与 `playeranimator` 都必须加载。两者均通过缓存的
  反射快照访问，不新增编译依赖，也不在专用服务器硬链接可选类；反射失败时只关闭该
  兼容桥并保留原生渲染。
- 复用范围：已复制的原生根节点位置/旋转、姿态修正（蹲伏/骑乘/游泳/滑翔）、原生
  PlayerAnimator 缩放、现有 16 关节 bind 与 `PenguinAnimationPose` 工厂、现有头部
  与手持物品辅助方法。没有自定义动画 JSON 或手写施法姿势。
- 动作优先级：死亡与活动中的 `FLYING_KICK` 优先；其余情况下 Iron 层（含淡出）覆盖
  TAUNT/音乐舞蹈，层不活动后自然恢复旧行为。
- 弯曲：躯干 bend 补充到胸部，手臂 bend 作用于肘部/中段且翼尖跟随，腿部 bend 作用
  于膝盖；头部保持刚性、不缩放。原生上层 layer 的 body bend 在施法帧按
  `y = 0.375` block 轴心精确反向消除，避免重复弯曲。
- 手持物品：除主/副手循环、display context 与左手标志保持原版外，活动施法帧会完整
  接管 `ItemInHandLayer.renderArmWithItem`，在香草手部尾变换之后按原生
  `leftItem`/`rightItem` 的 SCALE、POSITION（像素）、ROTATION（弧度）顺序追加一次。
  通道与弯曲来自同一份每帧不可变快照，不推进时间、不重复镜像。`continuous_thrust`
  的 rightItem 非单位旋转/位移因此得以保留。
- 头部朝向：保留独立动画的原生头部 ModelPart 朝向，只补充原生上层 body bend；不再
  把原生 torso/body ModelPart 欧拉角乘入头部第二次。

## English

This compatibility covers the vanilla player model and the seven ordinary Iron's Spellbooks
3.16.3 humanoid NPC casters. For a player with the penguin effect, an active associated
`irons_spellbooks:animation` modifier layer (including fade), and an active PlayerAnimator
applier, the native spell animation is retargeted onto the existing penguin 16-joint skinned
rig. Optional-dependency gating and the model preflight apply; when either fails, the complete
native renderer remains in charge.

- Optional dependencies: both `irons_spellbooks` and `playeranimator` must be loaded. Both APIs
  are read through cached reflection snapshots, so there is no new compiled dependency and no
  hard optional class reference on a dedicated server. Expected reflection failures disable only
  this bridge and leave the native renderer in charge.
- Reuse: already-copied native root position/rotation, posture corrections (crouch/riding/
  swimming/gliding), native PlayerAnimator scales, the existing 16-joint bind and
  `PenguinAnimationPose` factory, and the existing head/hand helpers. No custom animation JSON
  and no hand-authored spell poses.
- Priority: death and an active `FLYING_KICK` win; otherwise the Iron layer (including fade)
  overrides TAUNT/music dance until it becomes inactive, then the previous behaviour restores.
- Bends: the body bend supplements the chest, arm bend is applied at the elbow/mid joint with the
  tip following, and leg bend is applied at the knee. The head stays rigid and unscaled. The
  native upper-layer body bend is inverted once around the exact `y = 0.375` block pivot on an
  active casting frame, so no bend is doubled.
- Held items: the native main/off-hand loop, display context and left-hand flag stay untouched.
  For an active casting frame the complete `ItemInHandLayer.renderArmWithItem` is wrapped; after
  vanilla's hand tail it appends the native `leftItem`/`rightItem` SCALE, POSITION (pixels) and
  ROTATION (radians) exactly once, in native order. Channels and bends come from the same
  immutable per-draw snapshot; time is never advanced and the mirror is never reapplied. The
  non-identity `continuous_thrust` rightItem rotation/position is therefore preserved.
- Head orientation keeps the independently animated native head ModelPart orientation and adds
  only the native upper-layer body bend; the native torso/body ModelPart Euler is not multiplied
  into the head a second time.

## NPC 施法兼容（玩家之外） / NPC casting compatibility (outside players)

除玩家外，本阶段只重定向 Iron's Spellbooks 3.16.3 的七个普通人形施法者，注册名均在
`irons_spellbooks` 命名空间：`pyromancer`、`cryomancer`、`necromancer`、`archevoker`、
`priest`、`apothecarist`、`cultist`。它们是独立的“仅渲染”白名单，不加入动作/AI/法力/
冷却/碰撞判定集合；原生 Geo4 控制器、动画时钟、法术 AI 与游戏状态全部保留。

原生 `GeoEntityRenderer` 负责骨层级、动画、渲染器缩放/朝向/死亡旋转、标签和特效。桥接层
只在原生动画已求值后读取一次骨骼，绘制企鹅身体、重锚头部与手持物定位点；不修改骨骼、
不重跑 `handleAnimations`、不播放 Kuso 的 TAUNT/KICK/舞蹈。头部整棵子树与头盔保持原生
尺寸和动画；身体/四肢/披风护甲在替换就绪时按具体骨骼抑制。七类模型必须通过完整骨骼
预检（body/torso/head/双臂/双腿/双手）才会替换；缺骨时完整回退原生渲染。双手只校验
存在与父链，不同原生人形的手部定位点（如 necromancer 与 archevoker/priest 的差异）
均可接受；身体/头/四肢的稳定枢轴约束保持不变。

Boss、keeper、召唤物、非人形、Cataclysm 及其他 Gecko 4 实体明确排除。该 NPC 阶段不做
动作/网络/法术/AI/碰撞/资源/模型编辑。七个目标已在客户端实例中完成原生控制器与渲染
矩阵实测；真实 AI 战斗不在本次验证范围，详见下节。

In addition to players, this stage retargets only the seven ordinary Iron's Spellbooks 3.16.3
humanoid casters in the `irons_spellbooks` namespace: `pyromancer`, `cryomancer`, `necromancer`,
`archevoker`, `priest`, `apothecarist`, `cultist`. This whitelist is visual-only and is not added
to the action/AI/mana/cooldown/collision sets; native Geo4 controllers, animation clocks, spell AI
and game state are untouched.

The native `GeoEntityRenderer` keeps the bone hierarchy, animations, renderer scale/yaw/death
rotation, labels and FX. The bridge snapshots the already-animated bones once, draws the penguin
body and reanchors head and held-item locators; it does not mutate bones, rerun
`handleAnimations`, or play Kuso TAUNT/KICK/dance. The whole native head subtree and helmet keep
their original size and animation; body/limb/cape armor is suppressed per specific bone when the
replacement is ready. All seven models must pass a complete required-bone preflight
(body/torso/head/arms/legs/hands); a missing bone keeps the complete native renderer. Hands are
checked for presence and parent chain only, so canonical humanoid variants with different hand
locators (for example necromancer versus archevoker/priest) are accepted while the stable
body/head/limb pivot contract stays unchanged.

Bosses, keepers, summons, non-humanoids, Cataclysm and other Gecko 4 entities are explicitly
excluded. This NPC stage makes no gameplay/network/spell/AI/collision/asset/model edits. The
seven targets were measured on client instances with their native controllers and render
matrices; live AI combat is outside that validation, as detailed below.

## 验证状态 / Verification status

以下为当前实现的已测结果，不是全法术或全姿态覆盖。

### 中文

- 范围：原版玩家模型与 Iron's Spellbooks 3.16.3 的七个普通人形 NPC（`pyromancer`、
  `cryomancer`、`necromancer`、`archevoker`、`priest`、`apothecarist`、`cultist`）。
  Boss、Cataclysm、keeper、召唤物、非人形及其他 Gecko 4 实体不在已验证范围。
- 玩家：原版生存模式地面蹲伏施法通过（`LocalPlayer.isCrouching` 为 true）；已测
  continuous 右手物品通道、charged 头部旋转、左手镜像、取消/淡出和真实 instant firebolt
  事件。
- NPC：七个目标均在客户端实例中初始化原生 electrocute 控制器（detached client-only），
  未进行真实 AI 战斗；原生头/手/臂/腿矩阵误差不超过 `8.8e-7`，企鹅身体 4464 顶点、
  头盔 24 顶点。七个目标均保留完整原生渲染回退。
- 可选依赖：在 Iron's Spellbooks、Gecko 4、PlayerAnimator、IronLib 均未加载时可到达
  标题画面，两处可选 API 门控均为 false，且未打开世界；缺失时保留原生渲染。
- 环境：MC 1.21.1、Java 21、NeoForge 编译 21.1.243 / 运行时 21.1.262；Iron 3.16.3、
  Gecko 4.9.3、PlayerAnimator 2.0.4。与 TLM 1.5.3、YSM 2.6.5 共存安装，但不宣称
  自定义 YSM 玩家模型的施法已验证。
- 未覆盖：全部法术/全部施法姿态、骑乘/游泳/滑翔中的施法重叠、真实 AI 战斗、
  BendyLib、自定义资源。上述项目不能据本文档视为已验收。

### English

- Scope: the vanilla player model and the seven ordinary Iron's Spellbooks 3.16.3 humanoid NPCs
  (`pyromancer`, `cryomancer`, `necromancer`, `archevoker`, `priest`, `apothecarist`, `cultist`).
  Bosses, Cataclysm, keepers, summons, non-humanoids and other Gecko 4 entities are outside the
  validated scope.
- Player: grounded survival crouch + cast passed (`LocalPlayer.isCrouching` true); tested channels
  include continuous right-hand item, charged head rotation, left-hand mirror, cancel/fade and a
  real instant firebolt event.
- NPC: all seven targets initialized their native electrocute controllers on client instances
  (detached client-only, not live AI combat); native head/hand/arm/leg matrix error was at most
  `8.8e-7`, with 4464 penguin body vertices and 24 helmet vertices. All seven retain a complete
  native-render fallback.
- Optional dependencies: with Iron's Spellbooks, Gecko 4, PlayerAnimator and IronLib all absent,
  startup reached the title screen; both optional API gates were false and no world was opened.
  Missing optional dependencies leave the native renderer in charge.
- Environment: MC 1.21.1, Java 21, NeoForge compile 21.1.243 / runtime 21.1.262; Iron 3.16.3,
  Gecko 4.9.3, PlayerAnimator 2.0.4. Coexists with TLM 1.5.3 and YSM 2.6.5 installed, but custom
  YSM player-model casting is not claimed as validated.
- Not covered: every spell/every casting posture, casting overlap while mounted/swimming/gliding,
  live AI combat, BendyLib and custom resources. These must not be treated as accepted based on
  this document.
