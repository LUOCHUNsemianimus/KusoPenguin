# Penguin death afterimage / 企鹅死亡残影

已适配的成年玩家和生物在企鹅效果期间正常死亡时播放 Flying Back Death。
仅使用源文件前 60 个采样帧（索引 0–59，30 fps，0–1.966667 秒），加入短暂过渡，
总视觉时长约 2.07 秒。结束保持最后姿态，不回混为站立姿态。

服务端仍执行原版死亡、掉落、经验、尸体移除和复活，不延长实体生命，也不生成替身实体。
客户端在收到已确认的死亡动作时保存独立模型副本，继续绘制原头、皮肤和装备。
副本不加入世界、不 tick、无碰撞、不能被攻击、不产生掉落。

残影在开始的 12 tick 内向身体朝向的后方移动：生物 3 格、玩家 1 格，
附带最高 0.75 / 0.35 格的小抛物线。轨迹以原版方块碰撞几何预先裁剪，
并在渲染帧间平滑插值；遇墙缩短位移。地形按收到死亡包时采样，后续方块变化不会重算路径。
这些位移仅用于视觉副本；实际死亡位置、掉落物、经验和复活逻辑保持原版行为。

Afterimages travel backward relative to body facing over 12 ticks: 3 blocks for mobs and
1 block for players, with 0.75 / 0.35-block arcs. Vanilla block collision queries clip the
cached path; render interpolation keeps it smooth. Geometry is sampled when the death packet
arrives. The real entity, loot, experience and respawn positions are unaffected.

头颅保持原大小，以刚体方式跟随骨架。躯干和头部的实际模型边界用于倒地时整体抬升，
头、身体和手持物共享同一修正，不拉伸头颅。三档多面体模型均适用；旧 Block 对照不新增动画。

玩家提前复活不打断旧残影；再次死亡可产生另一份独立残影。原版会在复活后复用数字实体编号，
隐藏原实体的记录因此按对象身份区分，并在移除/复活后清理，不能按数字 ID 误伤新玩家。
残影上限 128，按动作时长清理，切换世界或断线时清空。

## Assets and reproducibility

- Source: `models/animation/flying_back_death-source-reference.json`.
- Original COLLADA SHA-256: `1fc686bee11139387afb98fde466f2a54a451139229ceb29acd70b78c9bcef71`.
- Export: `python tools/export_penguin_actions.py`; drift check: append `--check`.
- Runtime clip: `assets/kuso_penguin/animations/flying_back_death.json`.
- Same exact 16-joint bind contract and two-influence skinning as Taunt/Flying Kick.

## Validation

Actual death packets and real ghost renders were checked for all 11 supported mob species and
the player. Mob originals were removed on native timing; dropped item counts stayed unchanged
after the afterimages ended. Twelve model families × three quality tiers × 60 source samples
passed finite/noncollapsed skinning, rigid original-head and head/body floor-bound checks.
An early-respawn/two-death player test reused the numeric entity ID, retained a sword and shield,
rendered both independent afterimages and expired both without registering either copy in the level.

The death screen, inventory rules and server respawn remain vanilla. Flair/disc behavior is not
implemented in this round. Dedicated-server latency, simultaneous shader-heavy mass deaths and
third-party resurrection/death rewrites still require separate compatibility testing.
