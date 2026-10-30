# 玩家自定义舞蹈可行性研究 / Custom Dance Feasibility

**状态：仅研究与实施方案，未实现任何上传、解析或播放代码。** 本文不改变现有游戏行为、网络协议、公开接口或渲染优先级。

## English summary

The proven path is offline, not runtime: a trusted Python extractor reads a supported Mixamo COLLADA file, and repository exporters retarget named humanoid bones onto the fixed 16-joint penguin rig, producing validated schema-1 keyframe JSON (16 joints, ≤30 s total, ≤2000 frames). The Java runtime already validates and samples that immutable clip, and all player/maid/prone-style render paths converge on one shared penguin pose.

Player-uploaded dance files are **not implemented**. A first release should accept (a) a canonical 16-joint penguin JSON and (b) a deliberately limited Mixamo DAE subset, with an explicit bone-mapping report and local preview. Missing bones, unknown bind data, non-finite/non-TRS transforms, unsupported interpolation and unsupported up-axis/unit combinations must fail with a clear error; there is no generic "best effort" fallback.

A direct Java importer is feasible by porting the existing matrix decomposition, alias/name checks, meter/up-axis checks and LINEAR-channel rules, but FBX (binary/ASCII) and generic glTF/skeleton mapping remain later, costlier paths. No Python, Blender or FBX SDK is installed on players' machines or the dedicated server. The server should receive only bounded, validated canonical pose JSON over new animation payloads, never execute scripts, and never alter damage, teleport, AI or kick/death/revival priority. Existing native AnimatedBoss dance eligibility stays unchanged.

## 1. 已证实存在的路径（离线，当前代码）

1. `extract_dae.py` 用标准库解析 COLLADA：只接受 Y_UP，读取 `asset/unit` 的米制比例，遍历 visual scene 节点并建立唯一别名；动画只接受 `matrix` 通道、`LINEAR` 插值、递增且同步的时间网格，拒绝重复通道/别名、异步轨道和未支持的场景变换（`extract_dae.py:59-61,63-77,88-100`）。
2. 它把每帧矩阵分解为平移/四元数/正缩放，拒绝非仿射、镜像或零缩放、非有限值，校验 TRS 重建误差，并用首帧作为 **参考姿势而不是中立 bind**（`extract_dae.py:43-55,102-118`）。输出中的 `wing_motion_reference.json` 明确标注“SOURCE MOTION ONLY, NOT final retargeted penguin rotations”（`extract_dae.py:130-152`）。
3. `export_penguin_taunt.py` 与 `export_penguin_actions.py` 再把具名 Mixamo 骨骼方向重定向到固定 16 关节：不使用源骨骼长度，按目标 bind 方向做 aim/限角，root 平移通常清零，输出 schema-1 关键帧（`export_penguin_taunt.py:22-53`；`export_penguin_actions.py:27-73`）。踢腿只保留方向和有限根位移，实际位移仍由服务端碰撞/授权逻辑拥有（`export_penguin_actions.py:46-53,71-73`）。
4. 固定 rig 是 16 个具名关节和固定父子关系（`PenguinRig.java:4-10`）。`PenguinAnimationClip.parse` 强制 schema 1、`blend_in+blend_out+duration<=30`、恰好 16 关节、名称/父级与固定 rig 一致、root/head bind 为零、肩/髋 pivot 固定、帧数 2..2000、时间有序且首尾等于 duration、有限数字、单位四元数、正且有上限的缩放、平移长度上限（`PenguinAnimationClip.java:50-85,45-49`）。全体 bind 与 Taunt 一致由加载器另行调用 `sameRigAs` 保证（`PenguinMeshModel.java:606`，不是 parse 单独完成）；上传入口必须同样执行此校验。采样只读内存中的不可变数组，不打开资源（`PenguinAnimationClip.java:9,21-42`）。
5. 现有玩家/女仆/原生施法/舞蹈等路径最终都通过同一个 `PenguinAnimationPose` 共享姿势；`fromNativeRig` 只是把原生 16 关节矩阵桥接进同一姿势工厂（`PenguinRenderContext.java:225-252`）。死亡、踢腿、prone、乐器、施法有各自明确的优先级分支（`PenguinRenderContext.java:260-279`）。

因此可复用的是**离线提取/重定向数学、固定 rig、严格 JSON 校验、采样器和共享姿势**，而不是“运行时任意文件直接播放”。

## 2. 尚不存在、不能承诺的能力

- 没有客户端文件选择/上传 UI，没有动画网络包，没有服务端动画库，没有运行时 DAE/FBX/glTF 导入。
- 不能承诺通用 FBX、任意骨架、任意 Mixamo 角色都能自动成功；Mixamo 官方文档也只描述其双足人形 rig 的适用范围，不是通用格式保证（<https://helpx.adobe.com/creative-cloud/faq/mixamo-faq.html>）。
- 不能承诺在专用服务器上“丢一个 Python 脚本就能导入”。服务器不应依赖 Python/Blender/FBX SDK，也不应执行玩家文件。
- 不能承诺任意 DAE 的单位、Z_UP、非矩阵通道、贝塞尔/STEP 插值、异步轨道、镜像缩放或缺失骨骼可自动修复。

## 3. 三条导入路径评估

### 3.1 推荐首发：规范 16 关节 JSON + 受限 Mixamo DAE 子集

- **规范 JSON**：直接使用现有 schema-1 形状。它已经是固定 16 关节、固定 bind、有限数值、有限时长/帧数的安全数据格式；客户端解析后只得到姿势数组，不执行任何脚本。首发可把该格式作为“已重定向动画”的交换格式和缓存/上传格式。
- **受限 DAE 子集**：在客户端 Java 侧实现，禁止在服务端运行 Python。移植现有离线逻辑：
  - 仅接受 Y_UP；若以后支持 Z_UP，必须走显式、测试过的轴变换，不能静默改轴。
  - `asset/unit/@meter` 必须有限且为正；平移乘该比例。源单位和目标模型像素的换算固定、可测试。
  - 只接受 `matrix` 通道与 `LINEAR`；时间戳必须严格递增且同步。首版可拒绝非均匀网格/异步轨道，而不是猜测重采样。
  - 矩阵分解必须拒绝非仿射、剪切、镜像/零缩放、NaN/Inf；四元数保持符号连续。
  - 骨骼只允许显式映射表：Hips、Spine2、Neck、Head、左右 Shoulder/Arm/ForeArm/Hand/HandMiddle1、左右 UpLeg/Leg/Foot/ToeBase。前缀 `mixamorig:`/`mixamorig_` 和大小写做规范化，但**不做最近名称模糊匹配**。
  - 缺失必选骨骼、重复/歧义别名、未知 bind、无法映射的手指数或非人形层级 → 逐项报错并停止，不生成“默认成功”的动画。
  - 重定向复用离线脚本的 aim/限角/root 处理，但不复制其硬编码数组；目标 bind 仍由固定 rig 提供。
- **预览**：在客户端用现有 16 关节姿势预览，不上传前先显示映射报告、时长、帧数、被裁剪的 root 位移和所有错误/警告。预览是本地只读，不改变实体状态。
- **不安装 Python/Blender**：导入器是模组内的 Java 代码；离线 Python 仅作为开发/导出工具保留。

这是成本最低且与现有运行时契约最一致的首发：规范 JSON 几乎直接复用解析器，DAE 子集只需移植已经证实的矩阵/名称/单位/插值规则。

### 3.2 直接 Java Mixamo DAE 导入器的具体工作量

需要新增一个**不依赖 Minecraft 客户端类**的纯数据解析模块，至少包含：受限 XML 读取器（StAX/SAX，关闭 DTD 和外部实体）、COLLADA 节点/别名/accessor 读取、矩阵与四元数数学、通道时间网格校验、显式骨骼映射、重定向和规范 JSON 生成。现有 `extract_dae.py` 已给出可移植的算法和拒绝条件（`extract_dae.py:43-55,59-118`），`export_penguin_taunt.py`/`export_penguin_actions.py` 给出目标限角和 root 策略。

现有 actions 导出器还借用了仓库中 taunt 的参考姿势，不能将任意新动画首帧当作中立 bind。直接导入器需要从受支持文件中取得可靠 rest/bind（例如可验证的 inverse-bind 数据），或使用经过验证且适用的 Mixamo 参考配置；缺少可靠来源时应报告需要映射/参考姿势。这个条件是实现工作，当前 Python 提取并未自动解决所有动画的基准姿势。

主要风险不是数学，而是**来源差异**：Mixamo 导出选项、单位、Y_UP/Z_UP、骨骼命名、是否含手指、采样率、是否含 root 平移。首发应以“明确支持的子集 + 明确失败”换取可验证性，而不是追求“什么 DAE 都吃”。

### 3.3 FBX 与 glTF：更晚、更贵

- **FBX binary/ASCII**：需要处理版本化二进制、节点属性、skin/cluster、动画曲线、轴和单位、预旋转/后旋转；Blender 文档也专门提示 FBX 骨骼朝向修正（<https://docs.blender.org/manual/en/5.2/files/import_export/fbx_legacy.html>）。自己实现成本高，引入 FBX SDK 又增加依赖/许可/平台负担。建议以后走“离线转换为规范 JSON”的工具链，而不是让服务器或玩家客户端直接吃 FBX。
- **glTF/GLB**：容器和动画语义比 FBX 规范，但仍需 skin、节点层级、插值、轴/单位、蒙皮与通用骨架映射；通用骨架重定向不是首发范围。可作为第二批候选，但同样应先转成固定 16 关节规范数据。

## 4. 建议的端到端流程（提案，未实现）

1. **客户端导入**：文件选择（后续 UI）→ 后台线程读取，限制源文件 8 MiB → 受限 DAE 子集或规范 JSON 解析 → 显式骨骼映射 → 重定向到固定 16 关节 → 本地预览。
2. **规范化**：生成不可变规范 clip：16 关节、固定 bind、30 fps 目标、总时长 ≤30 s、帧数 ≤901、旋转单位化、缩放正且有界、root 位移裁剪或清零。客户端计算 SHA-256 与字节长度。
3. **服务端上传**：新动画 payload（BEGIN/CHUNK/END/清单/播放），不要塞进现有 WAV/唱片字节端点。BEGIN 声明字节数、哈希、schema、关节数、时长；服务端检查配额、并发、声明范围后才分配缓冲。CHUNK 严格校验 ticket、偏移、长度、总字节和每 tick 配额；END 校验完整性和哈希，交给有界后台执行器验证/落盘。
4. **持久化与哈希**：以内容哈希作为 clip id，原子写入（临时文件 + rename）到独立的动画库目录；索引保存 id/大小/时长/关节数/所有者。重复哈希直接复用，避免同一文件多份。
5. **邻近客户端缓存**：目录/播放包携带 id、哈希、大小、时长；客户端按哈希 LRU 缓存，下载后再次校验 SHA-256；首次播放前异步加载，绝不在渲染帧循环里解析 JSON。
6. **权威播放**：服务端下发 clip id、开始 game tick、循环时长/会话 id；客户端只按服务端 tick 计算相位并循环。停止/切换/断线清理都发送显式状态，客户端不会自行决定“开始跳舞”。
7. **优先级**：上传动画复用现有 `isDanceFrame` 门禁：死亡、有效飞踢、乐器、施法和玩家非站立姿态的优先级保持；复活 pole 仍高于普通唱片舞蹈。现有 prone 生物可在唱片范围内站起跳舞，不能因静态 prone 回退分支而禁止其自定义舞蹈；不得覆盖 kick/death/revival，也不改变原生 AnimatedBoss 的舞蹈资格。现有 `DanceManager` 的资格/停顿/邻近广播模式可参考（`DanceManager.java:49-65,85,153-174`），但动画库、包和缓存必须是独立实现。

## 5. 服务端与网络边界

- **复用模式，不复用载荷**：`DiscServer` 的票号、偏移、每 tick 配额、预留字节、断线清理、后台执行器与主线程回投是正确模式（`DiscServer.java:38-49,95-113,180-251,276-287,374-443`）；`DiscClient` 的“只信服务端播放状态 + 邻近缓存 + 维度校验”也可参考（`DiscClient.java:44-58,67-77,86-89`）。但动画需要独立的 `AnimationLibrary`、独立包类型和独立配额，不能把 JSON 当 WAV 塞进 `WaveFile.MAX_CHUNK/MAX_BYTES` 语义。
- **包大小**：本地 1.21.1 代码当前上传块是 16 KiB（`WaveFile.java:19-23`；`DiscPackets.java:66,77-83`）。NeoForge 1.21.4 文档写的是 clientbound ≤1 MiB、serverbound <32 KiB（<https://docs.neoforged.net/docs/1.21.4/networking/payload/>）；这是**文档版本 1.21.4**，实施前必须核对实际本地 1.21.1 的常量/校验，不能直接宣称相同。提案沿用 16 KiB 块，天然低于两者。
- **并发/配额（提案）**：最多 4 个并发上传、2 个后台工作线程；单玩家最多 5 个 clip、10 MiB；全局动画预留 128 MiB；单文件规范上限 2 MiB；清单只返回元数据，不返回文件内容。
- **权限/所有权**：默认允许有企鹅效果的玩家上传，但仅所有者可删除/替换；配置可整体关闭上传；管理员可删除任意条目。删除、过期、封禁、断线都要释放预留并清理临时文件。
- **错误可见**：错误码 + 具体位置（骨骼名、帧号、字段、期望/实际），客户端显示可读消息并保留本地文件；禁止只回“导入失败”或静默使用默认值。
- **多人哈希同步**：清单带哈希；客户端已有则跳过上传；服务器已有则只登记所有权；下载后校验哈希；同一哈希的并发上传合并为一次落盘。

## 6. 安全与数据校验

- 规范文件**只有姿势数据**：关节名/父级/bind、时间、旋转、平移、缩放、混合时长。禁止脚本、Molang、Python、着色器、资源路径、任意类名或可执行字段。
- XML 解析必须禁用 DOCTYPE 和外部实体（XXE），限制总字节、节点数、深度、属性长度和单文本长度；DAE 源上限 8 MiB。
- 所有数字必须有限；四元数长度在容差内并归一化；缩放每个分量 >0 且 ≤2；平移长度 ≤24；时间严格递增；首尾时间匹配 duration；拒绝 NaN/Inf、负缩放、剪切、镜像。
- 绑定必须严格匹配固定 16 关节契约；缺失骨骼/未知 bind 直接失败，绝不回退到单位矩阵或“最近骨骼”。
- root 运动必须裁剪或清零（可保留有限、可审计的垂直位移，但不得改变服务端碰撞/传送/伤害）；动画本身不授予移动、伤害、传送或 AI 能力。
- 时长上限为总 clip 时长（含 blend）≤30 s；采样器按服务端 tick 循环，不改变实体 AI 或服务端动作集合。

## 7. 性能与限制（全部为提案，未实现）

| 项目 | 建议上限 | 说明 |
|---|---|---|
| 总时长 | ≤30 s | 与现有解析器硬上限一致（`PenguinAnimationClip.java:55`） |
| 采样率 | 30 fps | 目标规范采样率；源可更高，导入时重采样并记录 |
| 帧数 | ≤901 | 30 s × 30 fps + 端点；仍低于现有 2000 上限 |
| 关节数 | 16 | 固定 rig（`PenguinRig.java:4-10`） |
| DAE 源文件 | 8 MiB | 覆盖常见 Mixamo DAE，限制 XML 内存/解析成本 |
| 规范 JSON | 2 MiB | 901 帧 × 16 关节 × 10 个 float 的原始数据约 0.58 MiB；JSON 文本留约 3 倍余量 |
| 上传块 | 16 KiB | 与本地唱片块一致，低于 1.21.4 文档的 serverbound 32 KiB 限制 |
| 客户端缓存 | 8 个/32 MiB | 按哈希 LRU；播放前后台加载，一次解析 |
| 服务端动画预留 | 128 MiB | 沿用现有预留思路，独立计数 |

- 解析/重定向/哈希全部在后台线程；主线程只接收已完成的不可变 clip。渲染帧内只做采样，不读文件、不解析 JSON、不分配无界集合。
- 数组按声明帧数/关节数预先分配并做乘法溢出检查；缓存按字节和条目双重限制；上传/下载缓冲区在配额检查后才分配。
- 动画采样沿用现有 16 关节数组；未来若要进一步降分配，可在共享采样层做对象复用，但不能为此改变公开行为。

## 8. 现有优先级与兼容边界

- 玩家、女仆、prone/原生视图都走同一共享姿势；不要新增第二套 pose 管线或复制三份渲染逻辑（`PenguinRenderContext.java:225-252`）。
- 现有舞蹈门禁在 `isDanceFrame`（`PenguinRenderContext.java:49-72`），舞蹈分支先于静态 prone 回退。prone 生物已支持进入唱片舞蹈，只有不跳舞时才返回静态滑行姿势；新动画不能把它们错误排除。复活 pole 与普通唱片舞蹈共享该入口，但 pole 必须保持优先；施法、乐器、死亡、有效飞踢及玩家姿态门禁按现有逻辑保留。
- 原生 AnimatedBoss 的舞蹈资格与实现保持不变；本次研究不把上传动画扩展到 Boss，也不改变其动画选择。
- 服务端 `DanceManager` 的 AI 停顿、资格和邻近广播只是模式参考；上传舞蹈不应改变其 eligibility，也不应让动画文件影响 AI、伤害或传送。

## 9. 明确的首发错误语义

以下情况必须失败并给出具体原因，不允许“默认成功解析”：

- 缺少 Hips/Spine2/Neck/Head/任一侧手臂或腿链；
- 骨骼别名重复/歧义，或只有未知骨骼；
- 非 Y_UP 且无受支持的显式转换；
- unit 缺失/非正/非有限；
- 通道不是 matrix，或插值不是 LINEAR；
- 时间戳重复/倒退/异步，或采样网格不一致；
- 矩阵含剪切、镜像、零/负缩放、NaN/Inf；
- bind 与固定 16 关节契约不一致；
- 帧数/时长/字节/缩放/平移越界；
- XML 含 DOCTYPE/外部实体，或超出解析预算；
- 上传偏移/哈希/长度/所有权不匹配。

## 10. 结论

首发可行路线是：**客户端 Java 受限导入（规范 16 关节 JSON + 受支持的 Mixamo DAE 子集）→ 本地预览/显式骨骼映射 → 规范不可变 clip → 独立的有界分块上传/内容哈希持久化 → 邻近客户端哈希缓存 → 服务端权威 clip id + start tick 循环播放**。它复用已证实的离线重定向数学、固定 rig、严格校验和共享姿势；它不承诺通用 FBX/任意骨架，也不在玩家或服务器上安装/执行 Python、Blender 或任意脚本。所有性能数字、配额和网络包均为待实现提案，需在真实 1.21.1 环境核对包上限并完成解析/优先级/多人同步测试后才能宣称可用。
