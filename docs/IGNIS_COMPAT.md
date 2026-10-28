# 焰魔 / Ignis floating penguin pilot

目标版本：Minecraft 1.21.1、Cataclysm 3.33、Lionfish 3.1。可选兼容，无新增硬依赖；不包含灾变的模型或贴图。与 Scylla 一同等待用户观感验收。

## 外观与边界

- 独立封闭的无脚企鹅躯干替换原生躯干／悬浮裙甲；原生头、头盔、完整长臂、拳套、剑盾保留。上臂覆盖短黑翼袖，肩部加宽以衔接原生关节。当前是单一精度的试点，HIGH／MEDIUM／LOW 均使用同一独立模型，BLOCK 恢复原生。
- 不是完整长翼版。没有把通用企鹅短翼强行拉到原生手端；下一步美术调整需单独标定长翼。原生手臂和肩甲仍然可见。
- 不改变 AI、动画与阶段时序、伤害、攻击范围、体型、碰撞、死亡、刀光或 beam。不让焰魔触发 Kuso taunt／飞踢／舞蹈／视觉尸体。
- 缺少可选模组、模型路径／rest 不匹配、隐身、发光描边、BLOCK，或原生关键链隐藏／接近零缩放时，保留完整原生绘制。

## 模型和数学

源文件 `models/prototypes/cataclysm/ignis_legless_source.json` 使用可编辑多边形环参数；`python tools/export_cataclysm_mesh.py` 生成独立 `cataclysm/ignis_legless.json`，`--check` 检查漂移、封闭边、面积及有向体积。每面单色，共用现有调色板；没有脚或橙色腿。通用三档 BB／网格、腿部 sidecar 与动画 bind 均不修改。

当前 312 三角面：躯干 168，左右翼袖各 72；运行时 1248 个 QUADS 顶点。资源只在 reload 时由同一严格解析器验证并烘焙。新增 `PenguinMeshModel.ignisSkin()` 和 `PenguinSkinnedMesh.bake(List<Part>,clip)`；默认五根的验证与渲染逻辑保持原样。

`NativeAdvancedBoxRig` 仅缓存反射元数据，逐次读取当前姿态，不重置或修改原生骨骼。Lionfish3.1 的实际变换为 `T(position/16) Rz Ry Rx S`；父级 `scaleChildren=false` 时，绘完本级后用 `1/max(scale,1e-4)` 取消缩放再进入子级。像素矩阵和保留件绘制采用同一语义。该版本 render 不消费 `offsetXYZ/isHidden`，不人为添加这些语义。

躯干使用 `F_body inverse(B_body) A`，静态对齐基于完整合成的头枢轴和悬浮基准 Y=19，而不是地面 Y=24。短翼袖使用原生左右上臂的绝对帧、逆 bind 和固定局部尺寸；原生预旋转不被消掉，也不施加普通企鹅短腿／落地修正。

主 pass 保留互不重叠的 `head`、`left_arm`、`right_arm` 子树；先完成原生 consumer 再申请企鹅材质。独立盾模型的盾／裂纹两处调用只重放 `shield_root`；甲裂层只保留头／双臂，去掉旧躯干上的裂纹几何。所有材质、颜色、透明度、盾可见性和耐久分支沿用原生。

## 开发验证记录

工作流记录：`ignis-upstream-implementation-20261028`、`ignis-shoulder-delivery-20261028`；运行证据在工作区 `.workflow/projects/kuso-penguin/runtime/ignis-20261028/`。固定 `classes build --offline`，`test NO-SOURCE` 不作为行为测试。

- 实际兼容客户端：43 个原生动画／待机，三个阶段、四个动作采样时间、三档菜单设置，合计 1588 项检查；包含破盾／耐久、甲裂、移除重加、实体 A/B/A、隐身／描边／BLOCK。代表性动作保留顶点是原生绘制的同位置子集，native pose 无修改。
- Lionfish 原生 `translateAndRotate` 对照：8 组非均匀缩放／scaleChildren／忽略 offset 的路径矩阵，最大误差约 `3.44e-5` 模型像素；6 个动作的实际 renderer `blade2` 客户端刀光 socket 误差为 0 世界格。这个 socket 不是服务端命中框。
- rest 不匹配／恢复与实际 HP=0 的原生死亡路径检查。服务端可施加／移除企鹅效果，焰魔体型 `2.25×3.5`、生命上限 450 和 AI 不变；Scylla／Maledictus 同时检查。既有 Scylla 518 项、Maledictus 180 项回归通过。
- 最终肩部校准包已复测上述动作／矩阵／回归／服务端检查，GPU 预览覆盖待机、挥剑与第三阶段。无灾变／Lionfish 的真实客户端已进入标题，通用与独立模型缓存加载正常。效果自然到期、默认五根资源严格验证也通过；两个自建客户端均已正常关闭，QA 存档恢复和平／生存。最终观感交付用户确认。

尚未声称覆盖所有光影包、完整自动 Boss 对战或全部多人联机组合。无人验收使用隔离和平存档，测试实体不写入服务端存档。

## English

Optional Cataclysm 1.21.1-3.33 / Lionfish3.1 visual-only pilot. A separate closed, legless floating penguin torso and short black cuffs replace the torso/skirt; the original head, full long arms, gauntlets, sword and shield remain. This is not the future full-long-wing model. All three mesh menu tiers currently share this single pilot mesh; BLOCK restores native rendering.

Native bones, weapon/particle sockets, AI, phases, attacks, hitboxes and death remain authoritative. AdvancedModelBox matrices and retained geometry honor native parent scale cancellation. The shield layer uses its own model instance; secondary passes never emit another penguin body. Existing penguin models and bind data are untouched. Runtime records distinguish numeric checks, actual GPU inspection and user artistic acceptance, which is pending.
