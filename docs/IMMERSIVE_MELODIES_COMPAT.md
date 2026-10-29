# Immersive Melodies 兼容 / Immersive Melodies Compatibility

## 中文

### 固定版本

- Immersive Melodies `0.8.0+1.21.1`（Modrinth version `RLpigSXC`，NeoForge 1.21.1）。
- Kuso Penguin 通过 `compileOnly` 和 `devCompat` 下的 `runtimeOnly` 使用同一坐标；`transitive = false`。
- 不写入 `neoforge.mods.toml` 的必选依赖，不 jarJar、不打包 IM。IM 缺失时原有渲染路径完全不变。

### 开发启动

默认 `runClient` 在 `devCompat` 下委托隔离的 `runCompatClient`：Gradle 从 Modrinth Maven 解析固定 `RLpigSXC`，要求实际 `immersive-melodies-RLpigSXC.jar` 存在，并把该绝对路径传给启动器。启动器以官方文件名 `immersive_melodies-neoforge-0.8.0+1.21.1.jar` 复制到隔离 mods 目录，避免 Maven 文件名造成重复模组。直接运行 `tools/launch_compat_client.py` 时 `--melodies-jar` 可选；省略即不安装 IM，用于缺失模组 QA。`-PdevCompat=false` 的 native `runClient` 不经过该 staging。

### 支持范围

- 仅实际 `Player`，以及实体类型精确为 `touhou_little_maid:maid` 的女仆。
- 仅当企鹅替换实际生效：企鹅效果、非隐形/发光/婴儿/旁观、当前模型有企鹅 profile 与 adapter、网格预览开启。
- 玩家 `HumanoidModel`、女仆 Bedrock 标准骨骼路径、Gecko/YSM 原生视图共享同一实现；Boss、其他实体和其他女仆模型不进入该快照。
- 主手/副手与左右手语义直接使用 IM 的 `EntityModelAnimator.getInstrument`、`ArmsAndHeadAccessor` 和 `ModelAccessor`，不复制一套手性判断。
- 吉他、笛、风笛等所有 IM 乐器使用 `ItemAnimators.get(乐器注册 ID)` 的官方动画；音频、网络、AI、输入和原生旋律时间线仍归 IM/TLM 所有。原生玩家/Bedrock/Gecko 路径保留自己的进度时钟；仅实际 YSM 女仆渲染视图（其原生 wrapper 缺少玩家 IM 更新）在每帧缓存快照中补一次 `visualTick`，不会重复推进。

### 姿态行为

- 每个渲染 Frame 只生成一个不可变快照，头部、身体和手持物图层共享。
- 在独立 scratch `ModelPart` 上从零角度求值，不读取 IM 已改过的玩家/女仆原生手臂，避免 Lute 的 `rightRoll * 0.25` 叠乘。
- 求值后只把解剖左臂旋转写入 `right_wing`（正 X 侧）、解剖右臂写入 `left_wing`（负 X 侧）；不改企鹅绑定枢轴、缩放、身体/腿姿态和共享翼尖手持物挂点。
- 头部只对 Geo/YSM 替换 IM 实际写入的 pitch/yaw 通道：从当前规范头部矩阵的 ZYX 欧拉角中仅替换写入通道（0 也是有效写入值），保留 Z 与未写入通道，再以当前头部枢轴构造纯刚体增量，不改变原生头部大小、缩放/剪切或位置。ZYX 分解由归一化四元数构造纯单位旋转矩阵后交给 JOML 矩阵欧拉提取，规避 JOML 1.10.5 四元数 ZYX 提取器的 X 通道分母缺陷；不升级依赖，也不直接分解带非均匀缩放的 canonical 矩阵。玩家与 Bedrock 原生头部已经带有 IM 头部角度，因此不叠加。该数学已在最终实机环境中验证。
- 乐器在手即进入持琴姿态，即使暂停或已停止演奏，与 IM `getInstrument` 的原生语义一致；移除乐器或药水效果后立即回到原路径。

### 鲁特琴贴合

- 短胖企鹅的腹部会包住鲁特琴琴身。共享 `applyHand` 在持琴快照有效时，按实际物理手检查精确 `immersive_melodies:lute`，在物品局部旋转之前沿规范 -Z（实体前方）平移 8 个企鹅模型像素（按 `bodyScale/16` 换算）；副手与左手玩家使用同一物理手映射。该偏移只对精确鲁特琴生效，其他 IM 乐器、普通武器、空手及所有 null 快照路径返回 0，不产生额外矩阵操作；不改翼根、网格、物品大小、头部或既有原生动画/优先级。
- 实机 GPU 已按玩家与 Bedrock/Gecko/YSM 三条女仆路径、正面+侧面、主手+副手复核：完整圆润琴身与琴颈均位于腹部之外，侧面贴合合理，普通玩家参考不变。截图直接来自原生 `EntityRenderer` GUI（854×480），是代表性视觉检查，不声称覆盖任意模型或所有 shader。
- 精确四个主导左右主/副手组合的矩阵对照：profile .5 时鲁特琴 deltaZ 为 -0.25 规范方块；相对旧挂点仅 translation Z 改变，旋转、缩放及其余矩阵分量逐位相同。空手、笛、武器、笛主导混持及非鲁特琴路径不受影响。

### 优先级

死亡/分离尸体副本 > 未结束的飞踢 > Iron 施法（含原生 Gecko caster）> 非站立玩家姿态（蹲、坐、游、鞘翅）> Immersive Melodies 持琴 > 企鹅舞蹈/taunt。持琴帧中 `isDanceFrame` 让位，`pose()` 返回 `null` 走普通预置网格；不会启动或停止音乐。

### MIDI/ABC 与 WAV

IM 0.8.0 当前读取 MIDI 与 ABC 旋律，不读取 WAV。用户已明确推迟 WAV/音频 UI 与转换器；本轮只做乐器姿态兼容，不新增音频导入、播放或界面。

### 原生使用

获得企鹅效果后，在主手或副手持有 IM 乐器，按 IM 原界面选择或播放旋律即可；不新增 Kuso 输入，也不从唱片页面导入。IM 0.8.0 只读取 MIDI/ABC，WAV 功能未加入且用户已明确推迟。

### 验证状态

最终实机环境：Minecraft 1.21.1、production-compatible NeoForge 21.1.262、Java 21、IM 0.8.0+1.21.1、TLM 1.5.3、YSM 2.6.5；Gradle 编译仍为 NeoForge 21.1.243。已验证原版玩家 `HumanoidModel`、Bedrock `hakurei_reimu`、Gecko `geckolib:winefox` 与 YSM 默认模型；不支持的既有原生头/骨骼契约继续走原路径。本轮玩家只覆盖原版 HumanoidModel，不声明 YSM 玩家自定义模型支持。

三种女仆模型模式各执行 44 个渲染用例（11 种乐器 × 主/副手 × 玩家/女仆），共 132 个：每例恰好一个 2832 顶点共享身体，顶点有限、scope 平衡、原生手持物可见；双持笛与鲁特琴时两手共 216 个物品顶点；并覆盖四个主导的左右手主/副手组合。API pitch `.25→1.4` 时 Lute 翼 yaw `-.65→.50`，在玩家与三种女仆上一致；实际 YSM 渲染会推进缺失的可视包络，其他原生路径保留自己的时钟，快照按帧缓存。原生音频同步/网络未修改，也未在专用服务器上独立验证；不保证任意第三方模型或广泛多人场景。

优先级与移除：持琴时 taunt 让位，卸下后 taunt 恢复；活动飞踢优先；完整原生蹲姿 `Pose.CROUCHING` 优先，站起恢复持琴；移除企鹅效果恢复原生。18 个 authored 通道数学用例加无头通道/原生头旁路，验证实际零角、头部枢轴、非均匀缩放、roll 与输入保持；以独立双精度三角参考重复应用，最大矩阵误差 `4.7683716E-7`。此前 JOML 提取缺陷与相对头部叠加问题已修复。

此前 132 个抽取用例只证明顶点被输出，并未证明轮廓可见，也没有捕获旧遮挡问题；本轮新增 GPU 检查是补充证据，不声称全部 132 场景都重新截图。**用户主观观感验收仍待人工确认**，不等于审美验收。

最终缺 IM 的原生客户端在标题界面启动通过，`Impl` 未链接，成品 jar 从不包含 IM。实际 `runClient` 的离线 Gradle argv 已测试（仅替换最终 JavaExec 动作）；Python staging 在有/无 IM 与缺 jar 情况下测试（仅 mock 最终 Java 子进程）。另做了完整原生客户端启动；mock 启动不计作实际 GPU 测试。和平生存 QA 存档已清理，所有自有客户端已停止。构建 `test NO-SOURCE` 不代表存在 JUnit 套件。**用户主观观感验收仍待人工确认**，不等于审美验收。

## English

### Pinned version

- Immersive Melodies `0.8.0+1.21.1` (Modrinth version `RLpigSXC`, NeoForge 1.21.1).
- Kuso Penguin consumes the same coordinate as `compileOnly` and, under `devCompat`, `runtimeOnly`; both use `transitive = false`.
- No mandatory dependency is added to `neoforge.mods.toml`, and IM is neither jarJar'd nor bundled. A game without IM keeps every previous render path unchanged.

### Dev startup

The default `runClient` under `devCompat` delegates to the isolated `runCompatClient`: Gradle resolves the pinned `RLpigSXC` from Modrinth Maven, requires the actual `immersive-melodies-RLpigSXC.jar`, and passes that absolute path to the launcher. The launcher stages it in the isolated mods directory under the official filename `immersive_melodies-neoforge-0.8.0+1.21.1.jar`, so the Maven artifact name cannot create a duplicate mod. When `tools/launch_compat_client.py` is run directly, `--melodies-jar` is optional; omitting it installs no IM and tests the absent-mod path. The native `runClient` with `-PdevCompat=false` does not use this staging.

### Supported paths

- Actual `Player` instances only, plus entities whose type is exactly `touhou_little_maid:maid`.
- Only while penguin replacement is really active: penguin effect, not baby/invisible/glowing/spectator, a penguin profile and adapter for the current model, and skinned mesh mode enabled.
- The player `HumanoidModel`, the maid standard-bedrock-bone path and native Gecko/YSM views share the implementation. Bosses, other entities and other maid models never enter the snapshot.
- Main/offhand and handedness semantics come directly from IM's `EntityModelAnimator.getInstrument`, `ArmsAndHeadAccessor` and `ModelAccessor`; no second handedness rule is duplicated.
- Lute, flute, bagpipe and every other IM instrument use the official `ItemAnimators.get(registry id)` animation. Audio, networking, AI, input and the native melody timeline remain owned by IM/TLM. Native player/Bedrock/Gecko paths keep their own progress clock; only an actual YSM maid render view, whose native wrapper lacks the player IM update, advances `visualTick` once per cached per-frame snapshot, so there is no duplicate advancement.

### Pose behaviour

- One immutable snapshot per render frame, shared by the head, body and held-item layers.
- Evaluation runs on disposable scratch `ModelPart`s seeded at neutral, not on IM-modified native player/maid arms, so Lute's `rightRoll * 0.25` cannot compound.
- Only the physical arm rotations are copied afterwards: anatomical left arm to the positive-X `right_wing`, anatomical right arm to the negative-X `left_wing`. Penguin bind pivots, scales, prepared body/leg transforms and the shared wing-tip held-item mount stay untouched.
- Head writes replace only the channels IM actually wrote, for Geo/YSM views only. The current canonical rotation is decomposed as ZYX; written pitch/yaw values (including authored zero) replace those components while Z and unwritten channels remain. A pure rigid delta is applied around the current head pivot, preserving native head size, scale/shear and translation. ZYX extraction builds a pure unit rotation matrix from the normalized quaternion before calling JOML's matrix Euler extractor, avoiding the JOML 1.10.5 quaternion ZYX X-channel denominator defect without upgrading the dependency or decomposing the nonuniformly scaled canonical matrix directly. Player and Bedrock native heads already receive IM's angles and are not doubled. This math has been validated in the final runtime environment.
- Holding an instrument selects the pose even while paused or after playing stops, matching IM's `getInstrument` semantics. Removing the instrument or the penguin effect restores the previous path immediately.

### Lute fitting

- The short, rounded penguin belly occludes the lute body. While an instrument snapshot is live, the shared `applyHand` checks the exact `immersive_melodies:lute` in the actually requested physical hand and translates 8 penguin model pixels along canonical -Z (entity front) before the rotated item frame; offhand and left-handed players use the same physical-hand mapping. The offset applies to the exact lute only: other IM instruments, ordinary weapons, empty hands and every null-snapshot path return zero and add no matrix operation. Wing roots, meshes, item size, head and the existing native animation/priority behaviour are unchanged.
- Runtime GPU review covered the player and all three maid paths (Bedrock/Gecko/YSM), front and side, main and offhand: the full rounded lute body and neck are now visible outside the belly, the side fit was checked, and the normal player reference is unchanged. Screenshots came directly from the native `EntityRenderer` GUI at 854×480; this is a representative visual check, not a claim for arbitrary models or every shader.
- Matrix comparison across the four dominant left/right main/offhand mappings: at profile .5 the lute deltaZ is -0.25 canonical blocks, and only translation Z changed relative to the old mount; rotations, scales and every other matrix component are bit-identical. Empty hand, flute, weapons, mixed flute-leading and non-lute paths are unaffected.

### Priority

Death/detached copy > active unexpired flying kick > Iron casting (including native Gecko caster) > non-standing player posture (crouch, sit, swim, elytra) > Immersive Melodies instrument > penguin dance/taunt. On an instrument frame `isDanceFrame` yields and `pose()` returns `null` for the ordinary prepared mesh. No music is started or stopped.

### MIDI/ABC versus WAV

IM 0.8.0 reads MIDI and ABC melodies, not WAV. The user explicitly postponed WAV/audio UI and any converter; this step only adds instrument poses and does not add audio import, playback or UI.

### Native use

After obtaining the penguin effect, hold an IM instrument in the main or offhand and select/play a melody in IM's own UI. No new Kuso input or disc-page import is added. IM 0.8.0 reads MIDI/ABC only; WAV is not implemented and remains explicitly postponed by the user.

### Verification status

Final runtime environment: Minecraft 1.21.1, production-compatible NeoForge 21.1.262, Java 21, IM 0.8.0+1.21.1, TLM 1.5.3 and YSM 2.6.5; Gradle compilation remains NeoForge 21.1.243. Validated models are vanilla player `HumanoidModel`, Bedrock `hakurei_reimu`, Gecko `geckolib:winefox` and the YSM default model. Unsupported existing native head/bone contracts still fall back unchanged. Players were tested only as vanilla `HumanoidModel`; no YSM player custom-model support is claimed.

Each of the three maid model modes ran 44 rendering cases (11 instruments × main/offhand × player/maid), 132 total: exactly one shared body of 2832 vertices per case, finite vertices, balanced scopes and visible native held items; simultaneous flute+lute produced 216 item vertices across both hands; four dominant left/right main/offhand cases were covered. API pitch `.25→1.4` moved Lute wing yaw `-.65→.50` identically on players and all three maids; actual YSM rendering now advances the missing visual envelope, other native paths keep their own clock, and the snapshot is cached. Native audio sync/networking were not changed and were not independently verified on a dedicated server. No guarantee is made for arbitrary third-party models or broad multiplayer coverage.

Priority/removal: taunt yields while holding and resumes on unequip; an active kick wins; full native crouch `Pose.CROUCHING` wins and standing restores the instrument; removing the effect restores native rendering. Eighteen authored-channel math cases plus no-channel/native-head bypass validated actual zero angles, head pivot, nonuniform scale, roll and input preservation; repeated application against an independent double-precision trig reference stayed within maximum matrix error `4.7683716E-7`. The earlier JOML extraction defect and relative-head-tilt issue are fixed.

The earlier 132 draw cases proved vertex emission, not silhouette visibility, and did not catch the old occlusion. The new GPU checks are additional evidence and do not claim all 132 scenes were re-captured. **Manual user aesthetic acceptance remains pending.**

A final absent-IM native client reached the title screen with `Impl` unlinked; the production jar never contains IM. The actual `runClient` offline Gradle argv was tested with only the final JavaExec action replaced; Python staging was tested with/without IM and with a missing jar while only the final Java child was mocked. A separate full native client boot was performed; the mocked launch is not an actual GPU test. Peaceful survival QA saves were cleaned up and all owned clients were stopped. `test NO-SOURCE` means no JUnit suite is claimed. **Manual user aesthetic acceptance remains pending** and is not implied by these checks.
