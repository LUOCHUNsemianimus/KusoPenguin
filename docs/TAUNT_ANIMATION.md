# Penguin taunt / 企鹅一次性姿态

The mesh path now includes a resource-loaded taunt clip with 16 controllers: root, pelvis, chest, rigid original-head mount, two three-part wings, and two hip/knee/foot chains. The three accepted static meshes, UV atlas and repaired tail sources are unchanged. Joint loops/weights for continuous deformation are derived once at resource load; the idle path remains the original mesh renderer. Legacy BLOCK remains a comparison path with taunt fully disabled; High is now the default.

多面体路径接入16个控制节点：总根、骨盆、胸腹、原生头挂点、左右三段翼、左右髋—膝—脚。三档原静态网格、图集和尾巴修复保留；关节细分与权重在资源载入时缓存，空闲时回到原网格。旧BLOCK仅作对照，完整关闭taunt；默认切为高档。

## Playback / 播放

A genuinely new penguin effect starts one server-owned pose timestamp. Duration refresh does not restart it; removal cancels it. A player beginning tracking receives the same original start time, not a fresh animation. Entity UUID, network ID, dimension and sequence guard client entries. Source time is in seconds, sampled with gameTime + partial ticks at 20Hz and interpolated from approximately 30Hz source keys. Clip duration 2.833333s plus 0.25s blend-in and 0.35s blend-out gives 3.433333s total. Rendering eligibility does not trigger animation. No logical movement, AI, collision or first-person camera change.

真正新获得效果时，服务器发布一次动作起点；刷新时长不重播、移除即取消。后入观察者收到原起点；UUID、网络ID、维度、序号用于区分状态。按秒采样而非把86个采样帧当86个游戏tick；原片段2.833333秒，淡入0.25秒、淡出0.35秒，总长3.433333秒。渲染gate不触发动作。不改变逻辑位移、AI、碰撞或第一人称相机。

## Retarget and deformation / 重定向与形变

Source: Mixamo Standing Taunt Battlecry, source hash recorded in models/animation/taunt-source-reference.json. The file has no neutral bind pose, so no first-frame-as-T-pose claim is made. Upper-arm/forearm/hand directions are mapped into the target wing axes and lengths; clavicle contribution is retained in the source chest frame. Hip/chest/head rotations use limited reference-relative rotations. Source root travel, human leg lengths and fingers are not copied. The current gesture is an authored retarget preview, not an exact human pose reproduction.

参考源为Mixamo Standing Taunt Battlecry，哈希及最小源数据留档。源无中立绑定姿态；不把首帧冒充T Pose。上臂/前臂/手方向映射到企鹅轴向与骨长，肩锁运动包含在源胸部坐标下；骨盆/胸/头使用有限的参考相对旋转。不复制根移动、人形腿长和手指。本动作为重定向预览，并非人形姿态逐角复刻。

Continuous torso/wing/leg deformation uses two-influence linear blend skinning. Each face keeps its single colour; normals are recalculated from deformed triangle positions for the existing faceted style. The head mount inherits neck movement but uses a rigid rotation so head size stays native. The original head, hats/helmets, enderman eyes, profession decorations and Warden head/tendril passes share one head delta per living-render scope. Original head pivot offsets are preserved.

躯干/翼/腿采用最多双权重连续蒙皮，每面保持单色；按变形后的三角面重算法线，保留棱面光照。头挂点继承颈部移动但使用刚性旋转，原头大小不变；头、头盔/帽子、眼层、职业装饰和监守者头/触须图层共用同一头变换，保留原头转轴偏移。

## Legs and future flying kick / 下肢与后续飞踢

The current taunt leaves hip/knee/foot clip channels neutral and retains native short-leg movement. All three joints nevertheless participate in the runtime matrix/weight path, not just names in a file. Leg roots are planted separately from the upper-body sway. A future kick can supply hip, knee and foot rotations without changing the clip channel layout. Foot planting/airborne mode and kick timing remain future animation work. Whether to lengthen the short legs or merge them into the torso mesh is deliberately left to in-game visual acceptance; the current short-leg shape and embedded leg roots are retained.

本taunt的髋/膝/脚通道保持中立并保留原版短腿运动，但三个关节均进入运行矩阵与顶点权重，不仅是预留名字；腿根与上身摆动分开接地。后续飞踢可在同一通道结构驱动三关节，腾空/落地约束与踢击时序另做。是否加长短腿或并入躯干网格，留待实机判断；当前保留短腿外形及腿根嵌入。

## Rebuild and verification / 重建与验证

`python tools/export_penguin_taunt.py --check` validates generated clip and bind metadata against the minimal checked-in source. Without --check it rebuilds them, using the Python standard library only. No new runtime dependency is added. Resource reload installs clip, three meshes and their baked skins atomically; malformed animation fails explicitly instead of silently replacing motion.

生成源与运行资产独立校验；常态绑定数据不可变，每次实体绘制重新采样临时矩阵，不累积旋转或拉伸。实机/矩阵/事件与资源测试证据记录在工作流中；编译绿灯不等于行为验收。当前尚需用户视觉审阅；光影/FPS另行测试，不能凭RenderType没变宣称兼容已验证。

Source axis correction: directions use C=diag(-1,-1,-1); rotations use C R C^-1 (quaternion unchanged for C=-I). Body global deltas are rebased into the first chest frame. 源方向三轴映射与身体参考坐标分别处理，避免昂头变低头、前后仰变侧倾。
