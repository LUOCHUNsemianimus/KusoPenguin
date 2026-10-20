# Tail repair and segmented-wing study / 尾部修补与多段翼考察

## Implemented tail repair / 本轮已实现

The old tail and torso shared the `body` animation root, but the tail attachment vertices were outside the closed torso. A common root does not guarantee physical contact. The three active Blockbench sources now extend the tail base into the lower back, while keeping its tip outside. High/Medium have six embedded root vertices; Low has four. The tail and torso move under the same transform, so this overlap survives native body animations and uniform scaling.

原尾巴虽然挂在 body 根下，但根部顶点位于躯干外。已将三档尾根延伸进下背部，尾尖仍露在外；高/中各六个根部顶点进入身体，低档四个。尾部与躯干共用变换，因此原版身体动作和整体缩放不会拉开连接。

Only tail vertex positions and the three derived runtime JSONs change. Triangles remain High 492 / Medium 308 / Low 216. Other geometry, five animation roots, UVs, atlas, vanilla-pose adapters and the legacy cube path are unchanged. The body and tail are overlapping closed components, not one welded topology. A dedicated root-inside/tip-outside geometry audit and Blockbench rear/side/underside views cover the previous audit's blind spot. The original files are kept in the project workflow `work/tail-rig/before/` directory.

仅修改尾部顶点并重导出三档 JSON，面数仍为 492/308/216；其余几何、五根、UV、图集、原版动作和旧 cube 不改。连接方式为实体嵌入，并非拓扑焊接。增加尾根在躯干内、尾尖在外的几何检查，及 BB 背/侧/底视图。原文件保留于工作流 work/tail-rig/before/。

## Feasibility result / 可行性结论

Both two- and three-segment wings work in independent Blockbench studies. They retain `right_wing` and `left_wing` as native-pose shoulder roots. Two segments add a tip child; three add mid and tip children. Cut planes have caps and 0.8 model-pixel overlap at the joints. These are rigid segments, not weighted skinning: very large bends still need seam/collision testing, and the illustrative pose is not final animation art.

两段、三段翼均已在独立 BB 样板验证。保留原左右翼为肩根；两段增加翼尖，三段增加中段及翼尖。切口封口，关节区重叠 0.8 模型像素。它们是刚性分段而非权重蒙皮，大幅弯折仍须检查接缝和穿模；样例动作仅为可行性演示。

The studies include native editor keyframes: rest at 0s, pose at 0.45s, hold to 0.9s, rest at 1.4s. World matrices change during the pose and return exactly to their initial values at 1.4s (maximum difference 0 for both studies). This proves editor-level hierarchy/restore behavior. It is not a game-side potion animation implementation. Study files and evidence live under workflow `work/tail-rig/`, outside production model inputs.

样板带 BB 原生关键帧：0 秒静置、0.45 秒摆姿、保持到 0.9 秒、1.4 秒恢复。两版世界矩阵在摆姿时变化，结束时与初始最大差值均为 0。此证据只证明编辑器层级和归零恢复；尚未实现游戏中获得药水自动播放。研究文件不在正式模型输入目录。

## Recommended integration / 建议接入方式

Keep a permanent hierarchy rather than replacing skeleton objects at runtime. The original mob pose drives the shoulder. A short per-entity pose controller blends an extra shoulder rotation and child rotations in/out. Outside the pose, child translations return to their bind positions, rotations to zero and scales to one; the complete wing then follows the shoulder rigidly like the current one-bone wing. Reset every child before each entity is drawn, because the current body model is shared by renderers. Preserve the native shoulder translation and Warden ancestor transform before applying the local gesture.

建议保留固定层级，不在运行时换骨架。原生姿态驱动肩根，按实体保存的短动作控制器平滑叠加肩/子骨骼旋转；结束后子骨骼位移回绑定值、旋转归零、缩放归一，整只翼便像单段一样跟随肩根。现有模型实例共用，因此每次绘制实体前必须重置全部子骨骼，防止串姿态；叠加动作前保留原肩位移和监守者父级矩阵。

The current exporter explicitly rejects nested groups. The runtime parser and `Part`/renderer contain five flat parts and no child rig. Integration therefore needs exporter recursion, a validated optional wing hierarchy, local child transforms and a pose sampler; adding bones in BB alone cannot make the game animate them. Existing flat schema/model inputs must remain supported. No mandatory GeckoLib dependency is needed: the existing custom mesh renderer can apply these few extra transforms while retaining the entity render type. Shader behavior and frame time still require game-side acceptance after integration.

当前导出器明确拒绝嵌套组，运行解析/Part/绘制也只有五个平面部件。需要增加导出递归、受校验的翼子层级、局部变换与动作采样器，不能只在 BB 加骨骼就算游戏已支持；旧五根平面模型输入必须继续兼容。现有自绘网格路径可完成这些额外变换，无须强制加入 GeckoLib。正式接入后仍须做光影和帧耗验收。

Use effect lifecycle to trigger the gesture, not the rendering eligibility gate: temporary invisibility, crouching or leaving render range must not restart it. Recommended behavior is once on a genuinely new effect, no replay on duration refresh, cancellation on removal and time-progress sync for observers who begin tracking later. Infinite duration must not be treated as a timestamp. A server-owned pose start plus tracking snapshot can support this; it is a future change to the current native-effect-packet-only sync bridge, not an already implemented feature.

触发应基于效果生命周期，不能监听“当前能否替换渲染”的门：隐身、蹲伏或暂时离开渲染距离不应重新起播。建议首次真正获得效果播一次，刷新时长不重播，清除即取消，新观察者同步当前进度；无限时长不能当时间戳。可增加服务器记录的动作起点与跟踪快照，这是对现有仅原版效果包同步桥的后续扩展，并非本轮已做。

Three segments offer a shoulder/mid/tip curve with only four extra child bones across both wings. Two segments offer a simpler elbow-like bend. Recommend three for the requested gesture, keeping child transforms neutral during normal Minecraft animation. Strong bends need overlap and collision limits; weighted skinning is an optional larger project, not a prerequisite for this rigid low-poly gesture.

三段能做肩—中段—翼尖的折线弧度，双翼共增加四根子骨骼；两段更简单，弯折更像肘部。建议采用三段，常态子骨骼中立即可兼容原版动作。大角度需限定重叠和碰撞范围；权重蒙皮是更大方案，不是此类低多边形姿态的前置条件。
