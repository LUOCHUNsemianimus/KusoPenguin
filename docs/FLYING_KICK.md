# Active Flying Kick / 主动企鹅飞踢

企鹅效果期间，玩家在地面疾跑时按攻击键即可发动，无需瞄准或命中目标。
无加成时一次动作前移约 4 格、最高跃起约 1 格，位移持续 12 个游戏 tick；方块碰撞会提前停止。
玩家冷却为 60 tick（3 秒）。保持按键产生的普通跑动不计入技能的 4 格位移。

水平距离按实体的实际移动速度属性缩放：以该实体类型的默认移动速度为 1，速度 I/II、装备与
属性修饰符提高距离，缓慢降低距离，跳跃高度与持续时间不变。计算时会先按数值移除原版
`minecraft:sprinting` 的 +0.3 乘法加成，因此普通疾跑仍是默认的 4 格；只读取、不修改
玩家当前属性值本身。服务端在发动瞬间计算一次并把确切距离写入 S2C 动作包，客户端只跟随该
距离，飞行途中改变速度不会改变已批准的路径。

怪物沿用同一缩放函数，默认仍为独立怪物 3 格、其他受支持怪物 1.5 格。

沿途对前方所有可攻击的存活生物各结算一次，排除自己、队友、盟友、旁观者及被墙遮挡的目标；
玩家目标同时遵守队伍和服务器 PVP 设置。伤害沿用当前武器的完整普通攻击，保留原版武器、
附魔与伤害事件，不附加暴击或原版横扫的二次伤害。成功命中后施加一次击退 II 对应的强度，
目标自身击退抗性仍有效。

支持的成年小型敌对生物中，具有原生近战伤害属性者可在距当前目标 2–4.5 格时自行选择飞踢，
不依赖普通攻击先命中。怪物前移约 3 格、跃起约 0.75 格，冷却 80 tick（4 秒），允许落空。
只在接触到起跳时锁定、且仍是当前攻击目标的生物时结算一次原生普通近战伤害；不增加范围伤害。
飞踢以外保留正常 AI 和近战。铁傀儡与坚守者不加入这个小型怪物攻击选项。
苦力怕没有原生近战伤害属性，本轮不为其新增伤害或替换爆炸 AI。

## Implementation boundaries

- The empty C2S request contains no victim, position, direction, reach, speed or damage. The server
  checks effect/state/cooldown, samples the movement-speed attribute once, computes the approved
  flight distance and sends it in the S2C motion payload. Local-player travel follows that approved
  distance; server-controlled mobs use the same controller. `Entity.move` resolves solid collisions.
- Speed scale = `sanitize(nativeAttributesExcludingSprint) / typeDefault`. Re-evaluate native
  ADD_VALUE, ADD_MULTIPLIED_BASE and sequential ADD_MULTIPLIED_TOTAL operations without the
  `minecraft:sprinting` total modifier. `typeDefault` comes from DefaultAttributes (player ~0.1).
  The live attribute is read, never mutated. Normal sprint alone therefore keeps scale 1.
  Distance is `4 / 3 / 1.5 × scale`; height and duration are unchanged. Mid-flight attribute
  changes therefore cannot desynchronize the approved arc.
- The bounded-origin check uses `distance + 2` blocks (6 for the default 4-block kick) and the
  external-displacement catch-up check uses `distance/12 + 4×height/12 + 0.5` per tick. Both derive
  from the approved distance so longer boosts are not aborted; age (`TRAVEL_TICKS + 5`), alive/state
  and collision checks remain unchanged. The derived bound is proportional without an absolute cap:
  server-controlled extreme movement-speed modifiers can therefore request correspondingly long
  travel, still resolved by native collision and swept server hit detection.
- Server sweeps use actual received player positions, a front-facing capsule check, line of sight
  and one UUID set per kick. Native `Player.attack` handles each victim once. Native sweeping,
  sprint bonus and airborne critical damage are suppressed only inside that dispatch.
- Monster leaps lock one target and use native `Mob.doHurtTarget` on contact. No bystander sweep.
  Ordinary melee is blocked only during the leap to prevent duplicate or redirected damage.
- `TAUNT`, `FLYING_KICK` and `DEATH` share action timing, sequencing, the 16-joint bind rig and one
  cached skinned mesh per quality. Networking version is 4; clients and servers need matching builds.
- Flying Kick retains the 46 source frames / 1.5 s clip plus .1/.2 s blends. Visual root lift is
  zero because real travel owns jump height. Native poses return after the animation ends.
- High/Medium/Low support animation; legacy Block is retained without new animation maintenance.

## Validation

Live isolated-client checks: real `Minecraft.startAttack` activation, 4-block travel / 1-block
apex, three front victims each hit once, side/rear exclusions, wall stop and blocked targets;
native monster damage unchanged, no bystander hit, natural tick-based selection, cooldown edges
59/60 and 79/80, real scoreboard allies, server PVP disabled/enabled, knockback strength 1.0.
These are live acceptance probes, not Gradle unit tests (`test` is `NO-SOURCE`). Dedicated-server
latency and third-party combat rewrites have not been exhaustively tested. No new shader benchmark.

Actual final-source movement probes: default 4.0 blocks, Speed I 4.8, Speed II 5.6,
Speed V 8.0, Slowness I 3.4, an extra +50% total-speed attribute modifier 6.0, and Speed II
plus that modifier 8.4. All unobstructed player arcs retained a 1.0-block apex. Removing Speed
mid-flight retained the already-approved 5.6-block path. A boosted wall test stopped at about
1.526 blocks. Distances were sampled at the actual skill endpoint, excluding ordinary movement
afterward. Mob native-collision tests: zombie 3.0 -> 4.2 and iron golem 1.5 -> 2.1 under Speed II;
their apex remained .75. Independent 60-tick player cooldown and vanilla sprint-hit fallback,
plus repeated player death/early-respawn afterimages, passed regression.

实机验证：默认 / 速度 I / 速度 II / 速度 V / 缓慢 I 的水平距离分别为
4 / 4.8 / 5.6 / 8 / 3.4 格；额外 50% 总移速为 6 格，与速度 II 叠加为 8.4 格。
无障碍时最高跃起仍为 1 格。飞行途中移除速度效果不会改写已批准的路径，撞墙会提前停止。
独立冷却和冷却中普通猛击的回归验证通过。

The tests use an isolated integrated server, not a dedicated multi-user server. Extreme speeds
may still meet vanilla server movement validation or other mods' movement constraints; those
checks are not bypassed. No new shader performance benchmark is claimed.

# Cooldown fallback / 冷却期间普通攻击

飞踢拥有独立的 60 tick（3 秒）冷却。客户端依据服务端确认的动作开始时间判断，
冷却期间的疾跑点击回到原版攻击，包括重新疾跑后的猛击；普通攻击不会刷新飞踢冷却。
仅飞踢实际位移期间仍避免重复的普通命中，不把整个动画或冷却当作普通攻击禁用期。
离地、潜行、骑乘等无法启动飞踢的状态同样保留原版攻击入口。

The server-confirmed kick start owns a separate 60-tick cooldown. Sprint attacks during that
cooldown use vanilla attack handling, including sprint knockback after resuming a sprint, and
do not restart the kick cooldown. Only the actual kick travel prevents duplicate ordinary hits.


## Player skill commands / 玩家技能指令

Requires operator permission level 2. Omit the target to change yourself; console must specify
a target. Targets are online player names or selectors. Settings are stored per UUID in the
world's overworld SavedData, surviving death, dimension changes, reconnects and world reloads.
These commands change only player flying kicks, not ordinary attacks or mob kicks.

需要管理员权限等级 2；省略目标时修改自己，控制台必须指定目标。支持在线玩家名和选择器。
按玩家 UUID 保存在当前世界，死亡、换维度、重登和重启存档后保留。仅影响玩家飞踢。

```mcfunction
/kusopenguin flyingkick
/kusopenguin flyingkick get @p
/kusopenguin flyingkick damage 2
/kusopenguin flyingkick damage 2 @a
/kusopenguin flyingkick knockback 3
/kusopenguin flyingkick reset @p
```

- `damage`: multiplier from 0 to 100, default 1. Applied once to native weapon and enchantment
  damage before armor/resistance. This is a multiplier, not a Sharpness level.
- `knockback`: integer enchantment-equivalent level from 0 to 20, default 2. Each level gives
  0.5 knockback strength, subject to target resistance. This replaces the skill's fixed level II;
  it does not add a second native weapon knockback impulse or enchant/change held items.
- `reset`: restores damage 1 and knockback 2 for the selected player(s).

`damage` 为 0–100 倍（默认 1），对武器及原有附魔算出的伤害乘一次，再进行护甲等结算，
不是锋利附魔等级。`knockback` 为 0–20 的整数等级（默认 2），每级强度 0.5，
仍受目标击退抗性影响；替换技能原先固定的击退 II，不额外叠加一遍武器击退，也不改手持物品。
`reset` 恢复默认值。飞踢冷却和范围规则不变。


Normalization implementation note: native `ADD_MULTIPLIED_TOTAL` modifiers multiply sequentially,
not as one sum. The server re-evaluates base + additive + base-multiplied + sequential total
modifiers without `minecraft:sprinting`, then applies the attribute sanitizer and type baseline.
For example Speed II (1.4) and an extra 50% total-speed modifier (1.5) give a 2.1x kick (8.4 blocks).
归一化按原版总倍率逐项相乘；速度 II 与额外 50% 总移速叠加时为 2.1 倍飞踢距离。
