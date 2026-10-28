# Kuso Penguin
项目ID kuso-penguin；独立模组，遵守 D:/Moddevelop/AGENTS.md 与 .workflow/README.md。
Minecraft 1.21.1 / NeoForge 21.1.243 / Java 21，与 Huhutalk 相同工具链，但不依赖 Huhutalk、女仆或 JourneyMap。
模型源文件放 models/。用户已取消五 cube 限制，允许多方块与子骨骼精修；保留 body/right_wing/left_wing/right_foot/left_foot 五个动画根，常态复用原版姿态。用户已授权企鹅药水一次性taunt：运行时加入连续蒙皮/三段翼/髋膝脚控制，结束恢复原版姿态；独立动画资产和动作源码放models/animation。腿根与下腹必须共享连续网格，连接边锚定骨盆，髋／膝转轴位于身体内部；不再使用外露长橙色腿柱。
当前模型维护方向是多面体网格：每个三角面只用一种纯色，黑白等颜色边界必须由实际网格边和共享边界顶点构成，靠布线分色，不再用纹理曲线 mask 表达分界。三档源文件为 models/prototypes/penguin_lowpoly*.bbmodel（高档 444、中档 308、低档 240 个三角面）。原版五根、原版姿态、头部大小与腿柱嵌入躯干的设计保持不变。现有 cube 模型及代码作为 legacy 对照/回退保留，不修改也不删除；后续不再为 cube 做新美术迭代，新建模按上述多面体每面单色方向进行。
本轮目标是玩家及指定原版人形的效果替换原型。不能修改其他模组。构建检查走工作流 MCP，不能把编译通过当作实机验收。

腿部连续模型：BB 源与 `models/prototypes/penguin_leg_bindings.json` 同步维护，使用 `tools/export_penguin_mesh.py` 导出（`--check` 只读核验）。权重保存在 sidecar，勿靠 BB 未知自定义字段。三档修改前副本在 `models/archive/leg-connection-20261027/`；legacy cube 不维护。
短腿变形维护：玩家与可选 Boss 的腿经共享 `PenguinLegPose` 使用虚拟解剖控制点 `20/21.3/22.3`（模型像素，Y 向下，向下靠近羽毛腿根），补偿重绑定到不可变原 clip bind `15/20.5/22.3`；不要为修腿改关键帧或 rig bind。显式权重网格的蹲下只用一次带符号整机落地修正（body、双翼、双脚、头部同一量），勿恢复分件独立抬升；schema 1 与非接地/空中姿态保持原逻辑。修复前模型与 sidecar 副本在 `models/archive/leg-deformation-20261028/`，旧 `leg-connection-20261027` 历史保留。保留五动画根、原版常态、legacy 与工作流编译/验收规则。
