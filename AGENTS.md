# Kuso Penguin
项目ID kuso-penguin；独立模组，遵守 D:/Moddevelop/AGENTS.md 与 .workflow/README.md。
Minecraft 1.21.1 / NeoForge 21.1.243 / Java 21，与 Huhutalk 相同工具链，但不依赖 Huhutalk、女仆或 JourneyMap。
模型源文件放 models/。用户已取消五 cube 限制，允许多方块与子骨骼精修；保留 body/right_wing/left_wing/right_foot/left_foot 五个动画根，常态复用原版姿态。用户已授权企鹅药水一次性taunt：运行时加入连续蒙皮/三段翼/髋膝脚控制，结束恢复原版姿态；独立动画资产和动作源码放models/animation。腿柱必须进入躯干，摆腿转轴位于身体内部。
当前模型维护方向是多面体网格：每个三角面只用一种纯色，黑白等颜色边界必须由实际网格边和共享边界顶点构成，靠布线分色，不再用纹理曲线 mask 表达分界。三档源文件为 models/prototypes/penguin_lowpoly*.bbmodel（高档 492、中档 308、低档 216 个三角面）。原版五根、原版姿态、头部大小与腿柱嵌入躯干的设计保持不变。现有 cube 模型及代码作为 legacy 对照/回退保留，不修改也不删除；后续不再为 cube 做新美术迭代，新建模按上述多面体每面单色方向进行。
本轮目标是玩家及指定原版人形的效果替换原型。不能修改其他模组。构建检查走工作流 MCP，不能把编译通过当作实机验收。
