# Kuso Penguin 实机测试清单

本文件保留完整测试步骤与预期；本轮已执行的代表性实机检查与证据见 [VALIDATION.md](VALIDATION.md)。矩阵中的未执行项目不能自动视为通过。

## 0. 环境

- Minecraft 1.21.1 + NeoForge 21.1.243 + Java 21。
- 普通难度存档，关闭自然生成以免干扰；测试敌对样本时不要使用和平难度。
- 建议创造模式，并给玩家抗性提升避免测试期间死亡：

```mcfunction
/gamemode creative
/difficulty normal
/gamerule doMobSpawning false
/effect give @s minecraft:resistance 99999 255 true
```

和平模式不能生成/保留僵尸、骷髅、猪灵、僵尸猪灵；如果使用和平存档，敌对样本项应记为“未验证”，不能记为通过。

## 1. 效果与药水入口

```mcfunction
# 状态效果入口（推荐用于快速矩阵）
/effect give @s kuso_penguin:penguin 3600 0 true
/effect clear @s kuso_penguin:penguin

# 普通药水
/give @s minecraft:potion[minecraft:potion_contents={potion:"kuso_penguin:penguin"}]

# 喷溅药水
/give @s minecraft:splash_potion[minecraft:potion_contents={potion:"kuso_penguin:penguin"}]

# 滞留药水
/give @s minecraft:lingering_potion[minecraft:potion_contents={potion:"kuso_penguin:penguin"}]
```

预期：三种药水物品均可获得并显示中英文名称；饮用/投掷/滞留云触发同一 `kuso_penguin:penguin` 效果。本轮没有设计酿造配方。

## 2. 测试样本

```mcfunction
/summon minecraft:skeleton ~ ~ ~ {Tags:["penguin_test"]}
/summon minecraft:zombie ~ ~ ~ {Tags:["penguin_test"]}
/summon minecraft:villager ~ ~ ~ {Tags:["penguin_test"]}
/summon minecraft:piglin ~ ~ ~ {Tags:["penguin_test"]}
/summon minecraft:zombified_piglin ~ ~ ~ {Tags:["penguin_test"]}
```

玩家：使用第二个客户端或通过第三人称/F5 观察。村民建议用 `/summon minecraft:villager ~ ~ ~ {VillagerData:{profession:"minecraft:librarian",level:2,type:"minecraft:plains"},Tags:["penguin_test"]}` 同时检查职业帽子/鼻子。

清理：

```mcfunction
/kill @e[tag=penguin_test]
```

### 效果同步边界

- 观察者同步由 `LivingEntityEffectSyncMixin` 负责：`kuso_penguin:penguin` 添加/更新/移除时，服务端用原版 `ClientboundUpdateMobEffectPacket` / `ClientboundRemoveMobEffectPacket` 经 `ServerChunkCache.broadcastAndSend` 发给跟踪该实体的玩家；实体本身是玩家时也发给自己。
- 玩家开始跟踪一个已经带有效果的实体时，`EffectTrackingEvents` 补发当前剩余时长的 update 包；配对数据先于该事件发送，所以包顺序正确。
- 仅同步 `kuso_penguin:penguin`，其他效果仍走原版路径；没有自定义网络协议、客户端状态缓存或周期全量重发。
- 客户端必须实际收到服务端效果包；仅在客户端本地伪造的效果不属于本同步范围。

## 3. 测试矩阵

| ID | 样本/状态 | 操作 | 预期 |
| --- | --- | --- | --- |
| E1 | 玩家，站立 | 给予效果，第三人称 | 原玩家头/皮肤/hat 保留；身体为五 cube 企鹅，无人类躯干/长臂/长腿 |
| E2 | 玩家，行走 | 持续移动 | 左右脚交替；左右翅随手臂摆动；头仍按视角转动 |
| E3 | 玩家，攻击 | 空手/持剑攻击 | 翅膀跟随原版攻击手臂姿态，不出现人类手臂 |
| E4 | 骷髅，站立 | 给予效果 | 原骷髅头保留；身体企鹅；无骷髅身体/长骨臂 |
| E5 | 骷髅，射箭 | 拉弓 | 两翅映射抬臂/拉弓姿态，身体无原骷髅残留 |
| E6 | 僵尸，行走/攻击 | 给予效果并靠近 | 两翅映射僵尸抬臂；左右脚行走 |
| E7 | 村民，站立 | 给予效果 | 原头、鼻子、职业帽子/生物群系头饰保留；两翅呈交叉姿态；无村民身体服装 |
| E8 | 村民，行走 | 推动或使其走动 | 左右脚沿用村民行走；翅膀保持交叉映射；职业层不重画身体 |
| E9 | 猪灵，站立/攻击 | 给予效果 | 原猪灵头/耳朵保留；两翅映射猪灵动作 |
| E10 | 僵尸猪灵，行走/攻击 | 给予效果 | 原头保留；两翅映射僵尸式抬臂；双脚行走 |
| E11 | 任意支持样本，清除效果 | `/effect clear` 或等待结束 | 完整恢复原模型，无企鹅残留 |
| E12 | 任意支持样本，药水过期 | 等待持续时间结束 | 与 E11 相同 |
| E13 | 玩家，隐身 | 给予效果后隐身 | 按本原型限制保持原渲染路径；不出现半头半企鹅 |
| E14 | 玩家，发光 | 给予效果后 `/effect give @s glowing` | 保持原渲染路径 |
| E15 | 幼年僵尸/幼年村民 | 给予效果 | 保持原幼体渲染 |
| E16 | 未支持实体（如女巫、牛） | 给予效果 | 完全原渲染 |
| E17 | 玩家，头盔 + 胸甲/护腿/靴子 | 给予效果 | 原头/头盔保留；胸/腿/靴不悬浮；企鹅身体完整 |
| E18 | 玩家，手持物品/披风/鞘翅/肩鹦鹉 | 给予效果 | 手持物、披风、鞘翅、肩鹦鹉不悬浮；头部装饰仍保留 |
| E19 | 玩家，第一人称 | 给予效果后切第一人称 | 第一人称手臂保持原版（本轮限制） |
| E20 | 玩家，蹲伏/骑乘/游泳/鞘翅飞行 | 给予效果 | 按限制回退完整原模型，避免半头半身 |
| E21 | 玩家，普通/喷溅/滞留药水 | 分别使用 | 效果入口一致，名称正确 |
| E22 | 多实体同屏 | 同时给多个支持实体效果并移动 | 不同实体姿态不串状态；每次绘制从默认企鹅姿态开始 |
| E23 | 远端观察者 | 服务端给五种样本/另一玩家效果，观察客户端检查 effects 列表与模型 | 观察者端 effects 含 `kuso_penguin:penguin` 并替换身体；无效果实体不受影响 |
| E24 | 重新跟踪 | 观察者离开跟踪范围/重登后再看到仍带效果的实体 | 重新进入后仍显示替换，剩余时长正确 |
| E25 | 同步移除 | 观察者在线时清除效果或等待自然到期 | 观察者端恢复完整原模型，effects 列表不再含该效果 |

## 4. 观察重点

- 企鹅几何必须只有躯干、左右翅、左右脚五个 cube；没有企鹅头。
- 头部必须是原模型头，含玩家皮肤/hat、村民鼻子/帽子、猪灵耳朵等；材质与 overlay 不应丢失。
- 身体装备/手持物/披风/鞘翅不应漂浮；头盔/头饰保留。
- 村民职业层不能重画一整只村民身体；职业帽子/群系头饰仍可出现。
- 清除效果后不能残留企鹅身体或异常缩放。
- 多实体连续渲染时不能出现上一只实体的翅膀/脚姿态。

## 5. 当前未验证 / 已知限制

- 此前客户端启动只用于排查 Mixin 加载与效果同步日志；完整矩阵仍待上游执行。
- 隐身、发光、幼体、蹲伏、骑乘、游泳、鞘翅飞行、旁观者及六种类型之外的实体统一保留原渲染，这是本原型的明确回退策略。
- 第一人称手臂保持原版，不替换为企鹅翅膀。
- 本轮没有酿造配方；测试只使用 `/effect give` 和带 `potion_contents` 的原版药水物品。
- 模型纹理使用 `src/main/resources/assets/kuso_penguin/textures/entity/penguin_body.png`，原模型/贴图未修改。
