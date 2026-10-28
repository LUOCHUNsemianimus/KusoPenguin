# 飞踢生命周期修复（kick-lifecycle-fix-20261028）

基线：`141895a`。范围仅飞踢生命周期、命中确认与同步身份；Maledictus 空身体属于独立上游调查，本卡未改。

## 改动接口

- `PenguinPoseSync`
  - 新增包内 `long startKick(LivingEntity)`：走统一服务器状态边界，成功返回单调序列，失败返回 `0`。
  - 新增包内 `boolean cancelKick(LivingEntity, long sequence)`：只停止序列完全匹配且仍有效的 `FLYING_KICK`，发送更高序列的 `playing=false` 姿态。不取消 TAUNT/DEATH/新踢，不改冷却。
  - 公开 `start(...)`/`removed(...)`/`snapshot(...)` 语义保持不变。
- `PenguinKickMotion`：新增 `long sequence` 与 `boolean playing`；同一载荷启动或停止匹配的本地飞行。协议版本 5→6。
- `PenguinKickCancel`：新增 C2S 小载荷，仅携带 `sequence`。服务端只接受发送者当前 Flight 的序列；陈旧、重复、未知序列均无操作。
- `PenguinKick.startLocal(..., long sequence)`：无兼容构造器或默认序列；客户端镜像只接受更高序列。
- `PenguinKick.stopLocal(Player, long sequence)`：服务端权威停止，不再回传 cancel，避免网络环路。
- `PenguinActionTriggers.playerHit`：保留的旧攻击钩子改为调用 `PenguinKick.tryStart`，不再制造只有姿态的飞踢。

## 行为要点

- 命中确认：玩家路径返回 `PlayerAttackMixin` 的真实 `HIT`；生物路径使用 `doHurtTarget` 返回值。只有成功 `hurt` 后才把目标 UUID 写入 `Flight.hit`。失败（无敌帧/保护）可在后续活动 tick 重试，同一目标每趟最多记录一次成功命中。未改无敌时间、伤害、PVP/队伍/LOS、目标过滤或冷却。
- 中断边界：无效状态/死亡/效果移除/骑乘/游泳/鞘翅/大位移/墙体或低空顶碰/真实正强度击退统一走 `interruptFlight`，移除 Flight、取消匹配姿态，并对本地玩家同步停止运动。真实冲量在中断时保留。
- 自然完成：12 tick 正常结束只结束运动、清理速度与服务器命中状态，保留 36 tick 收招动画尾部，不发送中断。
- 移动：手动控制使用 `goal(t)-goal(tPrev)` 增量步进，保留单步上限、总原点/时间界和普通方块碰撞。`previous` 仍只用于服务器扫掠伤害，新增 `lastTravelPosition`/`lastTravelGoal`。每步后只清除踢击自身速度。步骤间发现新外部冲量时立即中断且不覆盖该冲量。服务器玩家仍不获得服务器应用轨迹。
- 击退事件：继续将踢击受害者收到的原生额外击退归零；只有活动踢击者自身收到正强度击退才中断并保留冲量，0 强度事件不触发中断。
- 失败重试与中断身份均有短注释；没有心跳、任意重试计数、额外冷却、依赖或配置变更。

## 下游交接时状态

- 已通过固定 MCP 编译检查（classes/build）。
- 未运行游戏或 Gradle CLI；无实机行为测试。密集史莱姆无敌帧重试、墙体/空顶中断时序、双向取消序列与击退冲量保留需上游按卡片进行集成服务器验收。

# 首领骑乘解除后的企鹅效果同步（boss-dismount-effect-fix-20261028）

Dismount effect synchronization after a boss grab release.

## 根因 / Root cause

- 实机时间线（`runtime/combat-edges-20261028/boss-timeline.txt`）：原生 state33 年龄约 23 解除骑乘、第一人称相机复位后，服务端仍有企鹅效果，而该玩家的客户端效果与渲染门均为 `false`；随后常规效果更新才恢复。
- 原版 `ServerPlayer.stopRiding()` 在 `super.stopRiding()` 之后，会对前坐骑 `LivingEntity` 当前所有仍生效的效果发送 `ClientboundRemoveMobEffectPacket`。该清理原本只面向骑乘 HUD，但也会清掉观察者同步得到的、实际仍存在的企鹅效果。
- Vanilla `ServerPlayer.stopRiding()` sends remove packets for every still-active effect of the former LivingEntity vehicle after `super.stopRiding()`. This riding-HUD cleanup also invalidates the observer-only client copy of the still-active penguin effect, so the client gate drops until a later regular update.

## 范围 / Scope

- 仅新增 COMMON 侧 `ServerPlayerPenguinEffectSyncMixin`，并在 `kuso_penguin.mixins.json` 的 `mixins` 注册。使用 MixinExtras `@WrapMethod(method = "stopRiding")`，在原方法前记录实际 `getVehicle()`。
- 原方法返回后，仅当前坐骑仍是与玩家同维度、未移除的 `LivingEntity`，且其企鹅效果仍存在时，只向前骑手本人补发一条原版 `ClientboundUpdateMobEffectPacket`，携带该坐骑当前真实效果实例（保留原持续时间与等级）。这发生在原版移除包之后。
- 坐骑为空、已移除、跨维度或没有企鹅效果时不发送；不改变其他效果的原版移除行为，不修改相机、抓取、乘客关系、渲染门或真实的到期移除。无 Cataclysm 依赖：任何带企鹅效果的 `LivingEntity` 坐骑都适用。
- Common-side mixin only, no public API or dependency changes. The original vehicle is captured before the wrap; the resync runs after vanilla removals, is scoped to the former rider, and does nothing when the vehicle is gone, in another level, or lacks the penguin effect.

## 下游交接时状态 / Downstream handoff status

- 本卡仅完成源码、文案与固定 MCP `classes/build` 编译检查；未自行启动游戏，不声称实机通过。
- 待上游用新客户端做集成服务器强制原生 Maledictus state33 释放：确认客户端效果与渲染门保持 `true`、服务端不变、真实到期移除仍生效；并复跑同一构建源码的飞行测试。
- Upstream integrated-server Maledictus state33 release and repeated flight regressions remain pending; no live pass is claimed here.

# 飞踢身份分离与普通接触推力豁免（kick-contact-identity-fix-20261028）

Kick flight identity separation and a narrow ordinary-contact push exception.

## 根因 1：Flight 键按数值 id 相等 / Root 1: Flight keyed by numeric id equality

- 实机审计（`runtime/combat-edges-20261028/entity-identity-audit.txt`、`kick-flight-server-audit.txt`）：集成服务器的 `ServerPlayer` 与客户端的 `LocalPlayer` 是不同对象、但 `Entity.equals/hashCode` 只比较数值 id（同为 id 5），旧的 `WeakHashMap` 因此把两者映射到同一个 `Flight`。客户端 `startLocal` 看到相同服务器序列，不建立自己的镜像；随后客户端中断先移除了服务器条目，C2S cancel 到达时服务端已找不到 Flight，姿态无法取消（审计中 age 6 被移除、姿态仍在）。
- 修复：仅把 `FLIGHTS` 改为 `new MapMaker().weakKeys().makeMap()`。Guava 弱键按对象身份比较相等，且返回并发 Map；不引入 `IdentityHashMap` 强引用、不新增依赖、不依赖共享的 `Entity.equals` 键。PoseSync 自己的服务器端 map 完全未动。
- `Entity.equals/hashCode` compare only the numeric id, so the integrated server player and the client local player (both id 5) shared one `Flight`. `FLIGHTS` now uses identity-based weak keys; the pose-sync maps are unchanged.

## 根因 2：普通实体碰撞被当成外部冲量 / Root 2: ordinary contact read as an external impulse

- 实机审计显示普通 Cow 接触在客户端 step 4 造成水平位移约 `-0.0408`，被 `externalImpulse` 视为外部冲量而中断飞踢。
- 修复：新增 COMMON 侧 `PenguinContactPushMixin`（`@Mixin(Entity.class)`），用 MixinExtras `@WrapOperation(method="push(Lnet/minecraft/world/entity/Entity;)V", @At(INVOKE, target="Lnet/minecraft/world/entity/Entity;push(DDD)V"), require=2)` 只包住原版 `Entity.push(Entity)` 内部的两次原生 `push(DDD)` 调用。若被推实体是 `LivingEntity` 且 `PenguinKick.active(living)`，仅跳过这一次接触推力；否则 `original.call(receiver,x,y,z)` 原样执行。
- 边界：`Entity.push(Entity)` 的 `noPhysics`/`isPushable` 守卫仍在原生代码中；直接 `push(Vec3)`/`push(DDD)`、`LivingEntity.knockback`、`hurt`、爆炸、方块碰撞以及同一次接触中未在飞踢的另一方都不受影响。没有全局取消 push、额外状态或公开 API。
- The common mixin wraps only the two `push(DDD)` calls native to `Entity.push(Entity)`, skipping the impulse solely for an actively kicking `LivingEntity`; every other push path, hurt, knockback, explosion and block collision is preserved.

## 下游交接时状态 / Downstream handoff status

- 已有服务器夹具/审计测试通过（含 Boss 抓取释放、真实到期移除）；本卡只完成源码、文案与固定 MCP `classes/build` 编译检查，未自行启动游戏。
- 实际客户端最终复检待上游执行：正常 4 格/1 高飞踢穿过 Cow、Slime 碰撞，真实击退仍中断并保留冲量，身份分离、陈旧 cancel 与近战回退；并在最终 JAR 上复跑 Boss 抓取修复。本文不声称实机通过。
- Server fixture/audit tests passed; the actual-client final recheck (Cow/Slime contact through a 4-block/1-high flight, genuine knockback interruption with impulse preserved, identity separation, stale cancel, melee fallback, and the Boss grab fix on the final JAR) remains pending upstream. No live pass is claimed.


## 上游最终验收 / Final upstream verification

2026-10-28，生产兼容启动器的本地集成服务器，专用 `KusoPenguin-Validation` 存档：

- 客户端与服务端玩家数字 ID 相同，但同时持有不同 Flight 实例，序列一致。真实玩家飞踢完整前进 4 格、最高 1 格，正常落地；五只 size2 史莱姆接触测试各受伤一次，未中断轨迹。
- 原生 AI 史莱姆真实攻击会正常中断飞踢，两端位移和姿态都结束，保留真实击退。原版无敌期不被绕过；失败伤害不会占用成功命中记录，同一趟不能重复成功命中同一目标。
- 飞踢冷却中普通奔跑攻击仍可用；陈旧序列和其他发送者不能取消；怪物只伤害原目标，队伍、PVP 和视线过滤保留。
- 墙体、早期顶碰、异常位移和原生击退回归通过；无残留踢击速度。临时墙顶测试方块已恢复。新 Motion/Cancel 消息完成编解码往返检查。
- 最终同一 JAR 的 Maledictus 原生 state33 抓取释放、第一人称切换前后，服务端和客户端企鹅效果及替换门均保持有效，持续观察超过 30 秒。真实移除以及 20 tick 自然到期仍正确取消替换。

Passed on the production-compatible integrated client: separate client/server flight ownership, full 4-block/1-block player arc and landing, five-slime contact hits once per target, genuine hurt interruption, native immunity, cooldown melee fallback, sender/sequence checks, mob target-only damage, team/PVP rules, wall/ceiling collision and payload round trips. Maledictus native grab release keeps the penguin replacement; real effect removal and expiry remain effective.

飞踢网络协议现为 **6**，联机客户端与服务端需要同时更新。开发验收使用真实游戏运行与外部探针；Gradle 的 test 为 NO-SOURCE，不作为行为验证依据。

Kick payload protocol is now **6**: update both client and server together. Runtime probes are local QA tools and are not bundled in the mod. Gradle test is NO-SOURCE; behavior acceptance comes from the integrated-game regressions above.
