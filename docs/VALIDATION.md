# Kuso Penguin 占位原型验收 / Prototype Validation

日期 / Date: 2026-10-19。
工程 / Project: D:/Moddevelop/KusoPenguin。

## 动画复用结论 / Animation reuse

可以复用原生物已经计算好的姿态，无需新做动画文件。原 renderer 正常运行 prepareMobModel/setupAnim；共享适配器将躯干、双臂、双腿的旋转及相对初始姿态的平移增量映射至企鹅躯干、翅膀和脚。企鹅关节仍使用自己的绑定位置，不能直接复制整套人形关节位置。村民只有一个交叉 arms 部件，两个翅膀由该姿态分别映射；这不是完整普通双臂骨架。

Vanilla poses are reused after the original model computes its animation. Rotations and translation deltas are mapped to the five penguin parts without replacing their bind pivots. Villager crossed arms need a separate adapter. No new animation clips were authored.

模型原件为在 Blockbench 5.2.2 实际创建的 Modded Entity 工程，严格五个 cube，无头。Java 几何逐部件与 BB 尺寸、关节及 UV 核对通过；不是运行时读取 bbmodel，也不声称已有自动导出生成管线。当前按颈部到脚底的绑定高度匹配，以保持原头位置和脚部落地；未将碰撞箱或胸腹高度直接当作整身高度。

## 实际通过 / Executed and passed

| 检查 | 证据与结论 |
|---|---|
| 五种成年生物静止替换 | 骷髅、僵尸、村民、猪灵、僵尸猪灵的前后截图及逐个近景。原头保留；村民鼻子、职业帽保留，无原身体服装残留 |
| 玩家第三人称 | 真实玩家皮肤头保留，身体为企鹅；有独立截图 |
| 原版动画姿态映射 | 真实客户端对上述五种模型及玩家调用原版 setupAnim，以非零行走输入核对翅/脚旋转、左右交替和 reset。属于运行时姿态断言，不能等同于完整 AI 攻击/射箭过程已演示 |
| 服务器到观察者同步 | 客户端五个实体实际效果列表包含企鹅效果，shouldReplace=true |
| 命令清除 | 五种生物效果列表和替换判定均恢复，另有恢复原身截图 |
| 自然到期 | 五秒效果施加时为 true，到期后五种均为 false |
| 重新进入跟踪范围 | 离开 400 格再回到场地，五种已有剩余效果重新同步且显示企鹅 |
| 实际饮用 | 生存模式通过真实使用键饮用，药水变为空瓶、效果生效、玩家身体替换；测试后恢复创造。创造模式也能饮用生效，但按原版规则不消耗药水 |
| 资源 | 中英文五个键一致；BB 与 Java 恰好五 cube，几何/关节/UV 一致 |
| 构建 | classes/build 成功。Gradle test 为 NO-SOURCE，不称为单元测试通过 |

Live checks cover five adult mob appearances, the player in third person, pose-mapping assertions, tracked-effect delivery, removal, natural expiry, tracking re-entry, and real potion drinking. Build success is separate from runtime verification.

## 发现并修复 / Issues found and fixed

1. 移除编译修正中引入的 ThreadLocal 渲染栈，改为 Redirect 直接捕获原方法实参。
2. 修复 Mixin 包中的普通接口直接加载问题，护甲过滤使用精确槽位方法入口，头盔继续原版流程。
3. 核对图层实体参数擦除类型及完整描述符，修复客户端加载错误；未降低 defaultRequire。
4. 纠正研究假设：1.21.1 普通效果不会自动向所有观察者提供完整 activeEffects。增加仅本效果的原版网络包同步桥及 StartTracking 快照，无自定义协议或每 tick 扫描。

## 原型边界 / Prototype limits

- 支持本轮六种成年类型；幼体和未适配实体保留原模型。
- 隐身、发光、蹲伏、骑乘、游泳、鞘翅飞行、旁观状态暂保留原渲染；第一人称手臂保持原版。
- 头盔/身体装备/披风等过滤经过源码审核；完整装备组合未逐一实机截图。
- 原版行走姿态映射已断言；自然 AI 追击、射箭、猪灵战斗/舞蹈全过程未逐项实测。
- 未进行第二客户端多人、专用服务端或 Iris/其他光影测试；本轮客户端只有 Minecraft、NeoForge 与 Kuso Penguin。
- 已注册普通、喷溅、滞留药水物品对应的同一 Potion；本轮实际使用入口验证为普通饮用。未制作酿造配方。
- 五方块外观仅供替换与动画映射验证，不代表最终企鹅美术。

## 可复查证据 / Local evidence

工作流分区：D:/Moddevelop/.workflow/projects/kuso-penguin。

- work/resource-audit.json
- work/runtime-test/client-sync.log
- work/runtime-test/matrix-before.png、matrix-after.png、matrix-restored.png
- work/runtime-test/close-*.png、player-penguin.png、player-after-drinking.png
- work/runtime-test/audit.txt、audit-player.txt
- work/runtime-test/clear-verified.txt、short-active-verified.txt、expiry-verified.txt、retracking-verified.txt
- work/runtime-test/potion-drink-result.txt
- work/runtime-test/capture-settings.json
- runs/ 下各步骤的源码 diff、构建与审核记录。

测试驱动仅放工作流证据目录，不打包入模组。运行条件：1280×720、FOV 70、GUI 3、原版光照。敌对样本采用独立存档的受控普通难度，创造/抗性玩家、关闭自然刷怪、样本 NoAI；不把和平模式当成敌对生物验证。短暂生存饮用后已恢复创造。
