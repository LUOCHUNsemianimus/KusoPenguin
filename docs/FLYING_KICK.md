# Active Flying Kick / 主动企鹅飞踢

企鹅效果期间，玩家在地面疾跑时按攻击键即可发动，无需瞄准或命中目标。
一次动作前移约 4 格、最高跃起约 1 格，位移持续 12 个游戏 tick；方块碰撞会提前停止。
玩家冷却为 60 tick（3 秒）。保持按键产生的普通跑动不计入技能的 4 格位移。

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

- The empty C2S request contains no victim, position, direction, reach or damage. The server checks
  effect/state/cooldown and approves the motion. Local-player travel follows the approved arc;
  server-controlled mobs use the same controller. `Entity.move` resolves solid collisions.
- Server sweeps use actual received player positions, a front-facing capsule check, line of sight
  and one UUID set per kick. Native `Player.attack` handles each victim once. Native sweeping,
  sprint bonus and airborne critical damage are suppressed only inside that dispatch.
- Monster leaps lock one target and use native `Mob.doHurtTarget` on contact. No bystander sweep.
  Ordinary melee is blocked only during the leap to prevent duplicate or redirected damage.
- `TAUNT`, `FLYING_KICK` and `DEATH` share action timing, sequencing, the 16-joint bind rig and one
  cached skinned mesh per quality. Networking version is 3; clients and servers need matching builds.
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
