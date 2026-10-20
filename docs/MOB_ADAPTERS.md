# Additional upright mob adapters / 直立生物适配

All three accepted mesh tiers share the same adapters and keep the original head size. The legacy cube is retained without new art. These are visual changes only: collision, AI, held-block data and effect lifecycle remain native.

三档网格共用适配器，头颅保持原大小；不修改碰撞箱、AI、搬运方块数据或效果生命周期。旧 cube 只保留回退。

| Family | Head height | Native neck Y | Floor Y | New body height | Head shift | Body offset |
|---|---:|---:|---:|---:|---:|---:|
| Humanoid / wither skeleton 人形、凋零骷髅 | 8 | 0 | 24 | 12 | 12 | 12 |
| Villager / zombie villager 村民、僵尸村民 | 10 | 0 | 24 | 15 | 9 | 9 |
| Creeper 苦力怕 | 8 | 6 | 24 | 12 | 6 | 12 |
| Enderman 末影人 | 8 | -13 | 24 | 12 | 25 | 12 |
| Iron golem 铁傀儡 | 10 | -9 | 24 | 33 | 0 | -9 |
| Warden 监守者 | 16 | -10 | 24 | 34 | 0 | -10 |

Coordinates are native model pixels before renderer scale. Enderman's original long legs extend to Y25 (one pixel beneath the renderer's actual ground at Y24); the penguin uses the actual Y24 ground plane so its small feet are not buried. Wither skeleton retains its native renderer scale. Golem and Warden keep their native core-head-to-floor height instead of the small mobs' 2.5-head ratio. Head decorations are not counted.

表中单位为模型像素，未计渲染器缩放；末影人原长腿末端 Y25 比实际地面 Y24 低一个像素，企鹅小脚改锚定实际地面，防止埋地；凋零骷髅保留原生整体缩放。铁傀儡、监守者保持原本的大体型，其余为 2.5 头身，装饰不计入头高。

Creeper diagonal hind-leg phases drive the two feet; wings rest because the source has no arms. Enderman, zombie villager and wither skeleton reuse native humanoid poses. Golem wings inherit walking, attack and flower-offer arm angles. Warden root/bone/body matrices are flattened onto the five penguin roots, keeping custom shoulder/hip pivots; its original head hierarchy, tendrils and look animations remain intact. Source poses are read, never rewritten.

苦力怕用对角步态驱动双脚，无原生手臂故翅膀静置。末影人、僵尸村民、凋零骷髅复用人形姿态；铁傀儡继承走路、攻击及献花手臂角度。监守者继承 root/bone/body 父级运动，保留企鹅自己的肩髋转轴及原头部、触须动画。只读取原生姿态。

Enderman eyes and charged creeper aura render on the retained head only. Warden emissive selection is respected: head/tendril emissions remain, original torso/heart/arms/legs are not redrawn. Golem cracks and zombie villager profession decorations are head-only. Carried blocks and offered flowers are hidden while replaced, like the existing held-item filter; underlying state is untouched and rendering returns when the potion expires. Existing unsupported-state fallbacks still apply.

末影人发光眼、带电苦力怕光效只绘制原头。监守者保留头与触须发光，不重绘原胸腹及四肢；傀儡裂纹、僵尸村民职业装饰只保留头部。效果期间隐藏搬运方块与献花的附属渲染，状态不变，效果结束恢复。原有不支持状态仍回退原版。

Live validation evidence is recorded separately in the project workflow; compilation alone does not prove visual acceptance. No new shader/FPS claim is made.

## Validation / 验证

The isolated development-client check covers the player plus eleven mob types in all three mesh tiers. It captures actual submitted vertices (1,968/1,232/864 per body), compares the retained head against native geometry, checks per-family scale/offset/floor contracts and alternating walk poses, and exercises combined Warden ancestor rotations and translations. Existing zombie wing direction remains within 1.73 degrees of the legacy reference in all three tiers.

隔离开发客户端覆盖玩家及十一种生物的高/中/低三档：实际提交顶点为 1968/1232/864，检查原头顶点、各族缩放/偏移/落脚点、交替步态和监守者多轴父骨骼矩阵。原僵尸翼方向三档均保持与旧参考小于 1.73 度的误差。

Special-layer checks exercise charged creeper aura, enderman eyes and carried blocks, golem cracks and offered flowers, zombie villager profession layers, and all five Warden emissive passes. Clearing the effect restores native body and special-layer geometry, with zero penguin vertices. The three mesh tiers, textures, and Blockbench sources are not modified by this adapter round. Screenshots and raw evidence are kept locally under the kuso-penguin workflow work/more-mobs-test directory; the final workflow build records the matching source fingerprint. Gradle test is NO-SOURCE; these are live-client checks, not repository unit tests. Shaders and FPS were not re-tested.

特殊层测试覆盖带电苦力怕光效、末影人眼与搬运方块、傀儡裂纹与献花、僵尸村民职业层和监守者五个发光 pass。清除效果后原身体/图层恢复，企鹅顶点为零。未修改三档网格、贴图或 BB 源文件。本地工作流 work/more-mobs-test 留有截图与原始证据，最终构建绑定源码指纹。Gradle test 为 NO-SOURCE；本轮为实机验收，未重测光影及 FPS。
