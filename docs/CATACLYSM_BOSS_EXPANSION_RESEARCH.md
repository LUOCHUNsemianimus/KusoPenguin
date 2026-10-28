# 灾变四 Boss 企鹅替换调研 / Cataclysm boss expansion research

日期：2026-10-28。范围：Ignis、Scylla、Ender Guardian、Netherite Monstrosity。

**后续实施更新：Scylla 与 Ignis 首版视觉替换已实现，分别见 [SCYLLA_COMPAT.md](SCYLLA_COMPAT.md)、[IGNIS_COMPAT.md](IGNIS_COMPAT.md)，均等待用户观感验收；Scylla 肩根修正已获用户观感确认。Ender Guardian 视觉-only 替换已实现，开发检查完成、待用户观感验收（[ENDER_GUARDIAN_COMPAT.md](ENDER_GUARDIAN_COMPAT.md)）；Netherite Monstrosity 是唯一仍未实现的 Boss。下文保留原始调研结论与计划。**
**以下为原调研时点记录，当时尚未实现四个 Boss；最新状态以上方实施更新及专用文档为准。** 不修改原生 AI、攻击、伤害、碰撞体、体型、位移或动画时序。

## 结论与推荐顺序

四者从骨骼与渲染结构上均有实现路径，可继续采用“原生模型计算动作、企鹅网格读取姿态、原生附件按原位置绘制”。不需要重做全部动画，也不需要为此新增 GeckoLib。

| Boss | 已确认的结构 | 推荐外观 | 相对工作量 / 开发顺序 |
| --- | --- | --- | --- |
| Scylla / 斯库拉 | `HierarchicalModel` / `ModelPart`，原生 `AnimationDefinition`；**自己实现主 render，不调用 super** | 已实现（见 `docs/SCYLLA_COMPAT.md`）：企鹅身体＋保留原生头、眼、锚、链与蛇饰；待实机验收。Ignis/Guardian/Monstrosity 仍未实现 | 中；第 1 个试点 |
| Ignis / 焰魔 | Lionfish `AdvancedEntityModel` / `AdvancedModelBox`、`ModelAnimator`；没有独立双腿，是悬浮裙甲下身 | 无腿悬浮企鹅，长翼或翼袖连接原生长臂、拳套、剑盾；不缩短原生附件链 | 高；第 2 个，建立 AdvancedBox 读取器 |
| Ender Guardian / 末影守卫 | 同为 `AdvancedModelBox`；脸是 `helmet` 下的小 `head`，并非一个孤立的大人头 | 宽肩企鹅，上胸部／肩领位置为原生头盔总成留出开口，保持脸朝前 | 中高；第 3 个，复用 AdvancedBox |
| Netherite Monstrosity / 下界合金巨兽 | `ModelPart`、原生 `AnimationDefinition`；头含大块装甲和下颌，另有真实 multipart 头部受击框 | 宽体企鹅，胸前大开口容纳原生头／下颌／角，保留手部炮口与火焰 | 最高；第 4 个 |

Scylla 可以沿用 Maledictus 的**替换思路与矩阵算法**，不能原样复用它的渲染拦截点、骨骼路径和附件列表。四者都不应直接套原版短腿的 `PenguinLegPose` 处理：Scylla 也需要独立标定，Ignis 没有可接地的原生双腿。

## 证据版本与检查边界

- Kuso Penguin 调研基线：`f2f82922...`，调研开始时工作区干净。上一轮已提交的 Maledictus／飞踢修复保持不动。
- 官方 Cataclysm 源码：Minecraft 1.21.1、3.33，提交 `10e21131b8eb2ce92be416409f91925e8c5decf0`。
- 实际安装 JAR：`run/compat-client/mods/L_Ender's Cataclysm 1.21.1-3.33.jar`；SHA256：`371956d0c2e3b56729ee93b955e936875d210e372fe734cdb74326d89ef79875`。Lionfish 3.1。
- 已用本机 `javap` 核对四模型的父类及声明骨骼字段，分别为 53 / 51 / 20 / 39，源码与 JAR 一致；另外核对 10 个 renderer/layer 的实际调用 owner、参数描述符。
- 已解析默认骨骼父链、局部平移／Euler 旋转，按 `T × Rz × Ry × Rx` 合成静态矩阵；已检查选定原生贴图 alpha。**这些是静态证据，不是完整字节码等价证明、动态动画采样或游戏截图。** AABB 为默认姿态的未膨胀 cube 包围盒，包含所选节点的子件，不能替代动画边界或 GPU 验收。
- 本轮没有编译、启动游戏或制作新模型；后续仍需要各阶段的固定构建、动作采样及代表性运行验收。

本机可复核资料位于工作区 `.workflow/projects/kuso-penguin/work/cataclysm-expansion-20261028/`：
`model-audit.json`、`layer-audit.json`、`*-javap.txt`、`*-bytecode.txt`、两份 Python 审计脚本、下游原报告和上游复核记录。模型／贴图未复制到本模组，也不打包进发布 JAR。

官方源码：[固定提交](https://github.com/lender544/new1.20.1/tree/10e21131b8eb2ce92be416409f91925e8c5decf0)。下文源码均相对其 `src/main/java/com/github/L_Ender/cataclysm/`。

## Ignis：无腿不是障碍，长臂端点才是关键

已确认路径（`client/model/entity/Ignis_Model.java:69–423`）：

- `root → upperbody → head`，原生头含头盔、角。
- `root → lowerbody → lowerbody2` 为悬浮下身／裙甲，不是左右腿。
- `upperbody → right_arm_joint → right_arm → right_hand → right_fist → sword → blade2`。
- `upperbody → left_arm_joint → left_arm → left_hand → left_big_guantlet → shield_root → shield`。

默认 root 位于 Y=19，下身末端也约在 Y=19，常规模型地面为 Y=24，已有约 5px 悬浮间距。父节点带预旋转，实际头枢轴约 `(0,-19.33964,-1.90937)`，双手约 `(±28.48287,-4.03098,-0.96969)`。**不能只累加局部 Y，也不能套 `(24-neckY)/24` 强行接地。** 无腿企鹅需保留原生 root 的呼吸、前倾与悬浮基准，不额外补脚或应用地面修正。

推荐两步：先做“企鹅躯干＋翼袖，保留原生长臂／拳套及剑盾”的可运行版本，再做覆盖完整长臂的三段长翼变体。后者需要独立的翼形／权重／绑定标定，不能把现有短翼简单放大后声称仍贴合原生手端点。当前全局动画 bind、三档通用企鹅和 legacy cube 不改。

`Ignis_Renderer.java:53–74` 先调用原生 `super.render`，然后读取 `blade2` 世界位置。**该 socket 用于客户端 `SwingParticles/Flameswing`（`Ignis_Entity.java:2012–2036`），不是服务端伤害命中框。** 仍应原样保留它，避免刀光与武器脱离；服务端范围攻击也不能为了视觉手长而缩短。

附加层：`Ignis_Shield_Layer` 使用另一份 `Ignis_Model`，每帧 copy/setup 后绘制盾与裂纹；不能假定它与主模型为同一实例。`Ignis_Armor_Crack_Layer` 则走通用 `renderColoredCutoutModel`。保留原生 `showModel/getShowShield`、破盾、阶段贴图、盾耐久和独立 beam 逻辑。

## Scylla：适合首个试点，需要专用主入口

**2026-10-28 后续状态：Scylla 与 Ignis 视觉替换已实现，Ender Guardian／Netherite Monstrosity
仍未实现。** 实现细节、固定 rest/路径、三 pass 保留策略与待验证项见 `docs/SCYLLA_COMPAT.md`。
下面的调研内容保留为设计依据；Ignis 请读其独立实现文档，不要把 Guardian/Monstrosity 当作已支持。

`Scylla_Model.java:141–444` 主干为 `everything → scylla → body → chest → head`；默认头枢轴 `(0,-7.5,0)`，地面到枢轴 31.5px，renderer 另有 1.15 倍缩放。

- 左臂为 `chest/l_arm/l_arm2`，锚位于其下；另有链和原生轨迹节点。
- `anchor2` 是睡眠用锚，`anchor2.visible = entity.isSleep()`；不能只保留手中的一把锚。
  （路径更正：原调研写作 `everything/anchor2`，经实际构造器与 `model-audit.json` 核对，
  `anchor2` 直接挂在 native root 下，即 `root/anchor2`；实现按 `root/anchor2`，保留此条历史说明。）
- `translateToEye`、`getChainPosition`、`translateChainAnchor`、`translateHand` 必须继续读取未经修改的原生模型（`Scylla_Model.java:485–542`）。
- 主 renderer 自己准备模型、处理颜色／透明度、调用各 layer。JAR 证实直接调用 `Scylla_Model.renderToBuffer(PoseStack,VertexConsumer,int,int,int)`，**绕过当前 `LivingEntityRenderer.render` 内的拦截点**。
- 自写入口还有死亡状态相关 alpha 分支（`Scylla_Renderer.java:132–147`），应传递原生已计算的 color，不另设死亡逻辑；眼部闪电、锚链特效继续原生。

先从 Maledictus 提取实际可共用的 `ModelPart` 父链矩阵／保留几何 helper，给 Scylla 增独立 profile 与主入口。不要先建立一整套四 Boss 空框架，也不要复制四份 Maledictus 适配器。通用企鹅网格先用于试跑，肩翼与武器端点是否需要小幅美术调整，以运行结果决定。

## Ender Guardian：上胸部头盔开口，不把脸搬到脖子上

`Ender_Guardian_Model.java:39–143`：
`root/lowerbody/upperbody/helmet/head`，另有 `helmet/upperhelmet`。

- 实际小脸 cube 只有 `6×6×6`，默认包围盒约 X±3、Y[-37,-31]、Z[-18,-12]。
- **整个头盔子树**约 X±10、Y[-42.5,-24]、Z[-23.7,-7]；只保留名为 head 的小 cube 会丢失完整头盔和发光部位。
- 因此更接近“脸藏在上胸／肩领中”，不是要在企鹅腹部中央挖洞。保留原生头盔／脸的矩阵，在企鹅上部轮廓让位，不能统一按名字 head 自动定位。
- `Ender_Guardian_Layer` 的发光贴图需要按头盔等保留件筛选；默认核心 upperbody UV 的 alpha 为 0，不代表该层的所有头盔／四肢都可省略。
- 原生注册尺寸 `2.5×3.8`，不改 AABB、拳击范围、攻击动画或阶段逻辑。是否保留原生拳甲作为翼端附件，应在外观试点中确认。

## Netherite Monstrosity：头部装甲、下颌和真实受击框一起保留

`Netherite_Monstrosity_Model.java:111–277`：
`roots/lowerbody/upperbody/head`，其下有 `jaw`、`horns`；手臂还包含炮口／core／flame。

- 使用 `HierarchicalModel + AnimationDefinition`，**不是 ModelAnimator**（同文件 `:289–311`）。
- `head` 本身有 8 个 cube。主脸块为 `28×31×22`，但头节点还含左右装甲，整体头子树宽约 **68px**，不能只按 28px 开口。
- 默认头枢轴约 `(0,-42.43571,-17.31441)`；父节点预倾 5°，直接相加会错误得到 Y=-44、距地 68px。真实头总成默认未膨胀包围盒约 X±34、Y[-69.569,-16.128]、Z[-39.306,-14.441]。
- 推荐宽肩／宽体企鹅，胸前切出真实开口，原生头、下颌、角保持原矩阵。通过企鹅壳体布线留空间，不能以黑色贴图、绘制先后或移动原生头代替开口。必要的口沿退让应改企鹅顶点，不移动原生脸。
- `Netherite_Monstrosity_Entity.java:104–129,603` 存在 `headPart`（1.6×2.5）及单独定位，受击由 `hurtParts` 转给主体；renderer 的裁剪也考虑该 part。**此受击框与模型 bone 不是同一个对象**。保持位置、伤害分流和裁剪原样，不能随视觉头部另行搬动或对 part 单独施加效果。
- 注册主体尺寸 `3.0×5.75`；本轮不缩小主体或 part，不更改火球、炮击及原生 AI。

## 附加层：按材质透明区域判断，不把整模型调用等同于整身体可见

JAR 调用已确认，四参 `…;II)V` 与五参 `…;III)V` 都存在。不能只写一个五参拦截器就假定所有层都已覆盖。

| 层／贴图 | 静态核验 | 开发策略 |
| --- | --- | --- |
| Ignis shield / shield crack | 独立模型整模型提交；盾贴图在 upperbody 与 head 的源 UV 范围内 alpha 为 0 | 保留盾链与耐久裂纹；按盾子树筛选可减少无效提交，不宣称旧身体必然完全重画 |
| Ignis armor crack1 | upperbody 源 UV 中存在 24 个非透明像素 | 禁止旧躯干裂纹悬在企鹅之外；可只保留原生附件裂纹，企鹅体表阶段纹理另作美术决策 |
| Scylla eye / snake | 全贴图仅 2 / 400 个非透明像素，核心 body UV 为 0 | 保留眼子件和指定蛇饰；蛇 ghost 材质、alpha 继续原生，不生成第二个企鹅 body |
| Ender Guardian glow | 核心 upperbody UV 为 0，但全贴图有 2749 个非透明像素 | 按原生头盔／选定拳甲等真实保留件筛选，不凭层名推断只有小脸 |
| Monstrosity layer / layer2 | upperbody UV 分别含 3681 / 2555 个非透明像素 | 必须排除旧躯干的发光几何，保留头／下颌／炮口等附件上的合法发光 |
| Monstrosity flare outer | 核心 upperbody/head UV 为 0，图中仍有 384 个非透明像素 | 根据实际 flame 子件与可见性保留，不能为省事直接取消整个 flare 层 |

UV 审计是默认源 box 面区域的保守像素检查，不是全附件的最终 alpha 证明，也不是动态混合／光影测试。各 layer 仍需实际顶点计数和 GPU 验收。主 pass 完成原生 consumer 的提交后再请求企鹅材质，保留已修复的 BufferSource 生命周期纪律。

## 共享边界与开发纪律

1. 只扩展独立的 native-animated-boss 精确 ID 集合；**不加入**普通 `SUPPORTED_TYPES` 或趴行集合。不能给它们 Kuso taunt、飞踢、舞蹈、AI 停止、替换死亡动画或尺寸缩放。
2. 当前 `data/cataclysm/tags/mob_effect/effective_for_bosses.json` 已包含企鹅效果。源码基类允许该标签；四 Boss 实际施加／到期／移除仍需运行核验，不把标签存在当作实机成功。
3. 复用现有企鹅 effect/tracking 同步、上一轮下马同步修复、可选类缺失时回退；不新增硬性 Cataclysm／Lionfish／GeckoLib 依赖，不分发其模型或贴图。
4. 共用逻辑为：profile 识别、矩阵采样、网格提交、保留件与 pass 策略。ModelPart 与 AdvancedModelBox 各有读取器；后者需核对 `defaultPosition* / defaultRotation* / defaultscale*`、`showModel`、当前 scale 和 `scaleChildren`，不能只把字段名替换一下。
5. 不修改原生 part 的平移、旋转、可见性来“藏身体”；按保留终端绘制、走完整祖先链。尤其不能为了长臂或胸前头部而搬原生骨骼，破坏 socket／头部受击对应。
6. Maledictus 的特定对齐比例、左右映射、保留表、ghost 层和短腿补偿不是通用参数。先保住其已验收行为，再按不同 Boss 标定。
7. 黑白边界按真实多边形面分色；新变体单独存源文件／权重／导出网格，不覆盖现有三档企鹅，不维护 legacy cube。

## 峰谷开发的接手入口

草稿卡在工作区 `.workflow/projects/kuso-penguin/work/cataclysm-expansion-20261028/`，**尚未初始化或派发**：

1. `draft-01-scylla.json`：最小共享提取＋Scylla 主／附加层接入，先验证可复用路线。
2. `draft-02-ignis.json`：AdvancedBox 读取器＋悬浮／长翼原型，保持剑盾链和客户端刀光。
3. `draft-03-ender-guardian.json`：复用读取器，上胸头盔开口＋眼层。
4. `draft-04-monstrosity.json`：宽体胸前开口＋头／炮口／三层材质与 multipart 保持。

下次先核对最新 HEAD、Cataclysm/Lionfish JAR 哈希、项目归属与本文件；为草稿生成唯一任务 ID，再按 `project.py kuso-penguin workflow init` 建立新基线。草稿的拟定新类名和允许文件只约束首轮方案，若当前源码已演进须由上游重新核对，不能盲派。**本次高峰授权仅用于本次调研，不自动授权未来高峰开发。**

同一共享模块可在实际 CTX<30% 时续派，达到迁移阈值就写精简交接；不自动拆成四个新会话重复读公共部分，也不为凑上下文追加任务。

每个 Boss 的必要验收：添加／移除／到期、主 pass 只一份企鹅、secondary pass 无旧躯干残留、附件及关键 socket 世界位置对照、代表性原生动作与阶段／死亡／睡眠、碰撞与 multipart 不变、Maledictus 回归、可选模组缺失回退、原生不可见／描边门禁。Ignis 剑尖误差是**视觉刀光对齐测试**；Monstrosity headPart 则是**真实物理／受击保持测试**，两者不可混写。允许误差应分别按矩阵像素与世界坐标记录，不混用单位。

无人验收仍用隔离和平存档；原生战斗需要非和平时须另设受控场地并恢复难度。普通编译绿灯不能替代动作／渲染验证。

## English handoff

This is a static feasibility study, not shipped support. Pinned target: Cataclysm 1.21.1-3.33 + Lionfish 3.1. Scylla and Monstrosity use vanilla ModelPart/AnimationDefinition; Ignis and Ender Guardian use AdvancedModelBox/ModelAnimator. Scylla overrides the main renderer without calling super, so it needs its own hook despite sharing the Maledictus matrix approach.

Recommended order: Scylla first, then legless floating Ignis with long arm/wing attachments, then an upper-chest helmet opening for Ender Guardian, finally a wide chest opening for Monstrosity. Keep native head/weapon transforms and combat geometry intact. Ignis blade2 socket drives client sword particles, not authoritative damage. Monstrosity has a separate physical headPart that must not move with a visual redesign. Whole-model layer submissions must be audited against their alpha masks; do not assume every such call visibly redraws the entire body.

The four uninitialized draft cards and local JAR/pose/layer audit evidence are stored in the workspace handoff directory above. Runtime potion application, full animation coverage, GPU/shader behavior and final appearance remain unverified. No Java, model assets, dependencies or gameplay changed in this research task.
