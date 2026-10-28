# 焰魔 / Ignis penguin compatibility

目标：Minecraft1.21.1 / Cataclysm3.33 / Lionfish3.1。视觉兼容，无新增硬依赖，不分发灾变资产。

## 2026-10-28 用户验收修正

- 使用原版 HIGH 胖企鹅的 body、right_foot、left_foot，三者顶点／面色／权重与原资产逐项一致。肩部黑白分界随原网格布线，不再生成黑色横带躯干。
- 保留独立短黑翼袖及原生头、完整长臂／拳套／剑盾。身体统一1.55倍缩放，保持原模型比例；翼袖作局部宽度标定并向内搭接2模型像素，连接原生装甲与不变的企鹅肩部；不移动原生手臂。
- 双脚踝固定向下60度，髋膝与腹部沿原连续权重连接。脚不采样原生行走／攻击动画，只随身体整体运动，脚掌不会独立摆动；无落地补偿。
- 新增固定贴腹鳍肢：从原版 HIGH 的 right_wing／left_wing 各取原20面鳍肢，以肩点 ±5.45,2.6,0.2 为轴先内收35°、再前倾32°，逐顶点投影到原躯干 XY 前表面并保留厚度，作为额外40个 body 三角面（body 共308面）刚绑到 joint 1；不占短袖的 joint 4/7，不采样原生手臂动画，也不改原生装甲／武器／AI。
- 主材质 pass 另保留 `root/lowerbody/lowerbody2`。此节点使用焰魔原生逐帧 `ignis_idle_*`／`ignis_soul_idle_*` 喷涌贴图，矩阵、颜色和 phase 原样。保留火焰下身，去掉 lowerbody 的腰甲与左右 thighplate，不搬动火焰骨骼。
- 盾层仍使用其独立模型，只绘制盾及耐久裂纹；甲裂层只绘制保留的头／双臂。附加层不生成第二份企鹅。

## 资源与代码

历史命名 `cataclysm/ignis_legless.json` 保留，但其现为五根模型，不再无脚。`models/prototypes/cataclysm/ignis_legless_source.json` 定义短翼袖及固定抱肚翅膀参数，`tools/export_cataclysm_mesh.py` 将其与原版胖企鹅躯干／双脚组合；`--check` 核对生成结果及翼袖封闭性。最终588三角面、2352运行时顶点（body 308、左右短袖各72、双脚各68；其中40个新增 body 面为固定贴腹鳍肢）；目前三个菜单精度共用这个HIGH躯干试点，BLOCK恢复原生。旧 QA 数量早于本轮鳍肢几何，不声称覆盖新资产。

原版三档BB／导出网格、腿权重sidecar、纹理及动画bind不修改。复用同一严格解析器和原资源reload生命周期，无渲染期文件读取；`ignisSkin()` 返回独立缓存，默认五根验证保持原样。

AdvancedModelBox由 `NativeAdvancedBoxRig` 只读采样；每次重算当前姿态，只缓存反射元数据。变换为T(position/16)RzRyRxS；父scaleChildren=false时在子节点前取消父缩放。Lionfish3.1原生render不消费offsetXYZ/isHidden，不另加假定语义。任何原生骨骼和socket均不修改。

不改变AI、体型、碰撞、攻击、伤害、阶段、动画时序、beam或原生死亡；不让Boss播放Kuso taunt／飞踢／跳舞。缺模组／不支持路径或rest／隐身／发光描边／BLOCK／近零关键链缩放时保持原生回退。

## 验证边界

本轮证据在工作区 `.workflow/projects/kuso-penguin/runtime/boss-visual-feedback-20261028/`，最终交接与源码对应构建见同项目runs/work记录。检查包括43个原生动画／待机、三阶段与菜单精度，共1588项动作／回退／附加层采样；86次脚踝朝向对照确认固定相对姿势；原生矩阵／6个刀光socket、Scylla518项及Maledictus180项回归、服务器体型／AI／效果添加移除到期。实际GPU待机／挥剑／第三阶段及无可选依赖启动另外记录，不以编译绿灯替代。

尚未声称覆盖所有光影或自动完整Boss对战。最终观感待用户确认；测试使用隔离和平存档，测试实体不保存。

## English

The revised pilot reuses the original HIGH penguin torso and connected legs/feet, preserving its exact per-face black/cream boundaries. Only the short black cuffs are custom. Each original 20-face HIGH flipper is additionally baked into the body as a fixed belly-hug: 35-degree inward then 32-degree forward about the shoulder, wrapped onto the original torso front and rigidly bound to body joint 1. The cuffs keep their existing joints 4/7, and native armor, weapons, AI and animation remain untouched. Final mesh is 588 source triangles / 2352 baked vertices. Uniform body scaling preserves the original proportions. Feet have a fixed60-degree downward ankle pitch, following the torso without independent native leg animation.

The native lowerbody2 flame geometry and animated normal/soul textures remain in their original coordinates. Native waist/thigh armor is omitted. Head, full long armored arms, weapons, shield, native animation/AI/combat and particle sockets remain authoritative. Secondary passes never draw another penguin. Existing penguin assets and binds are unchanged; the legacy legless filename now contains five parts. Artistic acceptance remains with the user.

## 固定抱肚翅膀：本轮开发检查 / Fixed hug validation

2026-10-28：新增40个贴腹鳍肢面，本轮独立证据在工作区 `.workflow/projects/kuso-penguin/runtime/ignis-belly-wings-20261028/`。原身体前268面、双脚、双短翼袖数据逐项不变，原Java骨骼及翼袖变换文件哈希不变。1588项焰魔动作／回退检查通过；129个动画／阶段组合、24个新鳍肢顶点的躯干相对位置保持不变，最大误差约3.82e-6模型像素。6个实际renderer刀光socket误差为0世界格。

GPU已查看常态及挥剑姿态；新增鳍肢仅随躯干整体运动，不随原生双臂、翼袖骨骼摆动。原版面色与模型保持原样，未新增渲染pass或修改游戏逻辑。此处是无光影开发环境检查，最终抱肚观感待用户确认；不声称全光影／完整自动Boss战覆盖。

The added40 flipper faces passed1588 native action/fallback checks. Across129 animation/phase combinations,24 new points keep the same torso-relative geometry (max error about3.82e-6model pixels). Existing cuffs, feet, original body faces and all production Java code remain unchanged; six actual sword particle sockets match exactly. Idle and swing GPU previews were inspected. User visual acceptance is pending.
