# 玩家自定义舞蹈库与传输 / Custom Dance Library & Transport

**状态：动作导入、独立动作库、三类触发设置、K 轮盘、8 个默认未绑定快捷键与真实企鹅模型预览已实现。** 开发验收证据位于工作区 `.workflow/projects/kuso-penguin/runtime/dance-20261031/`；最终观感以用户验收为准。

## 使用 / Usage

- 输入 `/kusopenguin dances` 打开管理页，将规范企鹅动画 JSON 或受支持的 Mixamo DAE 拖入窗口，也可填写本地路径后点“导入”。先预览，再点“上传”，等待服务器保存回执。
- 选择动作后，可分别绑定“唱片机音乐”“复活”“女仆音乐”，也可指定轮盘槽位。K 优先处理原有尸体／胶片复活；其他情况打开自己或所指女仆的轮盘。只有主人能控制女仆，服务端核对 4 格范围和视线。
- Minecraft 键位设置内有 8 个舞蹈槽位快捷键，默认均未绑定。重复选择当前手动动作可停止；轮盘中间也可停止主动舞蹈。音乐自动触发仍由音乐是否播放决定。
- 有企鹅效果且未演奏的女仆，在另一女仆 12 格范围内听到上传的 MIDI 时按主人所选动作跳舞；演奏者不被打断，音乐停止／离开范围／效果结束后释放舞蹈 AI 暂停。
- DAE 首版仅支持 **30 FPS、Y_UP、明确静止骨架、同步 LINEAR matrix 通道的 Mixamo 子集**；不支持任意 DAE、FBX、非矩阵 TRS 通道或未知骨骼。源文件不超过 8 MiB；规范动作不超过 2 MiB、901 帧、30 秒（含淡入淡出）。动作只改变视觉，不授予位移、伤害或复活权限；原有 5 秒／8 秒复活计时不变。

Use `/kusopenguin dances` to import supported Mixamo DAE or canonical penguin JSON, preview the actual penguin, then explicitly upload. Assign independent jukebox, revival and maid-music triggers or one of eight wheel slots. K retains film/corpse revival precedence; otherwise it opens the self/target-maid wheel. Eight vanilla key bindings start unassigned. Only the owner can command a maid, with server-side range and sight checks. Idle penguin maids dance within 12 blocks of another maid playing uploaded MIDI; performers keep playing and AI resumes when the source ends. The DAE subset requires 30 FPS, Y_UP, known rest bones and synchronous LINEAR matrix tracks. FBX and arbitrary skeletons are unsupported. Clips are visual only; gameplay and revival timing remain authoritative.

## 1. 范围与复用

- 复用 `com.luochun.kusopenguin.client.PenguinAnimationClip` 作为唯一的规范 JSON 解析/校验器；本步仅将其 `sameRigAs` 改为 public。
- 复用 `PenguinRig` 的 16 关节固定契约和 `assets/kuso_penguin/animations/taunt.json` 作为 bind 参考。
- 复用了 `DiscServer` 的票号、精确偏移、每 tick 配额、预留字节、后台线程回投和断线清理模式，但没有复制其 WAV/唱片语义；舞蹈拥有独立包 ID、独立索引、独立配额。
- 服务端只接收规范 JSON。**不执行 Python/脚本，不接受 XML/DAE/FBX**；受支持的 DAE 在客户端 Java 后台转换后上传规范 JSON。

## 2. 常量（`DanceLibrary` / `DancePackets` / `DanceServer`）

| 常量 | 值 |
|---|---|
| `DanceLibrary.MAX_FILE_BYTES` | 2 MiB（原始与规范化后均检查） |
| `DanceLibrary.MAX_FRAMES` | 901 |
| `DanceLibrary.MAX_SECONDS` | 30.0（含 blend） |
| `DanceLibrary.MAX_CLIPS_PER_PLAYER` | 5 |
| `DanceLibrary.MAX_BYTES_PER_PLAYER` | 10 MiB |
| `DanceLibrary.MAX_GLOBAL_BYTES` | 128 MiB |
| `DanceLibrary.MAX_JSON_DEPTH` / `MAX_JSON_TOKENS` | 32 / 500000 |
| `DancePackets.MAX_CHUNK` | 16384 bytes |
| `DanceServer.MAX_ACTIVE_UPLOADS` / `MAX_ACTIVE_DOWNLOADS` | 4 / 4 |
| `DanceServer.MAX_CHUNKS_PER_PLAYER_TICK` / `MAX_CHUNKS_GLOBAL_TICK` | 4 / 16 |
| `DanceServer.IDLE_TIMEOUT_TICKS` | 1200 |
| `DanceServer.BEGIN_COOLDOWN_TICKS` | 40 |
| `DanceServer.CONTROL_COOLDOWN_TICKS` | 2（每玩家每 2 tick 最多一次 SET_PREF/SET_SLOT/PLAY/STOP） |
| `DanceServer.MAX_ERROR_DETAIL_CHARS` | 256 |
| `DanceServer.WORKER_THREADS` / `WORK_QUEUE` | 2 / 8 |

## 3. 规范 clip 导入

`DanceLibrary.importClip(UUID owner, String title, byte[] raw[, String expectedHash])`

1. 原始 UTF-8/JSON 字节必须非空且 ≤2 MiB；首字符必须是 `{`。随后用有界严格扫描检查 RFC 8259 结构：拒绝注释、未加引号的键、单引号、NaN/Infinity、重复对象键、深度 >32 和超过 500000 个 token，之后才交给 Gson。
2. 帧数必须为 2..901。每帧 `translations` 必须是 16 个向量。
3. 每帧 root（索引 0）平移必须先是**长度 3 且三个元素都是有限数字**的数组；NaN、字符串、长度错误都会直接失败，不会被“规范化”为成功。校验通过后才把 root 写成 `[0,0,0]`，再由唯一的 `PenguinAnimationClip.parse` 校验 schema、绑定、时间、单位四元数、缩放和平移上限。
4. `sameRigAs(taunt 参考 clip)` 必须通过；总时长（`totalDuration()`）≤30 秒。taunt 参考 clip 在首次成功解析后缓存为只读静态值。
5. 对规范化后的完整 JSON 字节计算小写 SHA-256，作为 clip id/文件名；写入 `clips/<sha256>.json`，索引 `index.json`。
6. `expectedHash` 非空时必须等于上述规范化后的哈希，否则在落盘前失败。
7. 同一玩家重复内容直接返回其既有 registration。**其他玩家已有相同 hash 时只新增该玩家的 registration，不重写物理文件，也不重复计入全库字节**；标题和每玩家配额仍按各自 registration 计算。
8. 配额在落盘前检查：每玩家 5 个/10 MiB，全库 128 MiB（物理 hash 只计一次）。落盘先写临时文件，再原子移动到最终名；索引同样临时文件+原子移动。索引失败会删除本次新写的 clip 文件。

导入失败抛出带原因的 `IOException`；不会默认成功。

### 公开纯校验器

```java
DanceLibrary.Canonical c = DanceLibrary.canonicalize(raw); // 不创建 library、不读写世界目录
byte[] canonical = c.bytes();                             // 防御性克隆
String hash = c.hash();
float duration = c.duration();
int frames = c.frames();
PenguinAnimationClip clip = c.clip();                     // 已解析的不可变 clip，供客户端复用
```

`Canonical` 构造与访问器都会克隆字节；客户端因此不需要构造 `DanceLibrary` 或复制校验逻辑。

## 4. 公开数据 API（下一步直接使用）

```java
DanceLibrary library = DanceLibrary.get(server);       // world-root kusopenguin/dances
DanceLibrary.Clip clip = library.get(hash);            // 物理 hash 的代表 registration；不授权编辑
DanceLibrary.Clip own = library.ownedClip(uuid, hash); // 该玩家的 registration，或 null
byte[] canonical = library.read(hash);                 // 校验大小与 SHA-256，worker 线程使用
List<DanceLibrary.Clip> own = library.ownedCatalog(uuid);
Clip renamed = library.rename(hash, actor, operator, title);
boolean deleted = library.delete(hash, actor, operator);
library.importClip(owner, title, raw, expectedHash);
```

`DanceLibrary.Clip(id, hash, owner, title, bytes, duration, frames)`：不可变；`id` 恒等于 `hash`，wire 字段保持不变。一个 hash 可以有多条 owner registration；`get(hash)` 仅返回物理代表项，目录/配额/改名/删除/选择均按实际 owner 的 registration 处理。

删除语义：

- 普通玩家或拥有该 registration 的 OP：只删除自己的 registration；只要还有其他 owner，物理文件保留。
- 没有该 registration 的 OP：作为管理操作删除该 hash 的**全部** registration 和物理文件。
- 最后一条 registration 删除时才删除 `clips/<hash>.json`；只清理受影响玩家的偏好引用。

内置项：

```java
DanceLibrary.BUILTIN_HIP    = "builtin:hip";
DanceLibrary.BUILTIN_POLE   = "builtin:pole";
DanceLibrary.BUILTIN_WAVE   = "builtin:wave";
DanceLibrary.BUILTIN_CYCLE  = "builtin:cycle";
DanceLibrary.isBuiltin(id);
DanceLibrary.builtins();     // List<Builtin(id, translationKey, durationSeconds)>
```

时长沿用现有 5 秒风格节拍：单风格 5 秒，`builtin:cycle` 15 秒。`builtin:hip` 与 `builtin:pole` 的源片段/循环长度分别为 0.4666667 s 和 0.3333333 s，不改变该 gameplay 时钟。

这两个内置动作由 `tools/export_gmod_dances.py` 从检入的 GMod Source 归档离线生成 `dance_hip.json` / `dance_pole.json`；运行时在资源重载时缓存并由共享 `PenguinAnimationPose.evaluate` 采样，不读取 SMD、不做逐帧 IO。源坐标已修正为实际前向 −Y，导出基 `C=(-X,-Z,+Y)` 是 det=−1 反射，旋转按 `C R C^-1` 共轭；目标翼段以固定 canonical bind link 映射到修正后的 Source 动画方向，方向残差纳入 `--check`。源文件、哈希、选取区间、56→16 重定向、方向证明与 `--check` 说明见 `docs/GMOD_DANCE_SOURCES.md`。PUBG male/female 仅作为未来素材归档，未启用，也未通过放宽 30 秒限制来接纳。

### 偏好

```java
public enum Trigger { MUSIC, REVIVAL, MAID_MUSIC }  // 默认 cycle / pole / cycle
public record Prefs(String music, String revival, String maidMusic, List<String> slots)
Prefs.defaults();                    // 前 4 槽 hip/pole/wave/cycle，后 4 槽为空
library.preferences(uuid);           // 自动把失效/非本人选择安全重置
library.setPreference(uuid, trigger, clipId);  // 空白 = 重置该通道默认
library.setSlot(uuid, slot, clipId);           // 空白 = 清空
library.selectable(uuid, clipId);              // builtin 或本人已有上传
DanceServer.preferences(ServerPlayer);
```

偏好持久化在 world-root `kusopenguin/dances/preferences.json`。选择只接受 builtin 或该玩家本人 registration 的 hash；删除该玩家 registration 后仅该玩家的引用自动恢复默认/空槽并写回，其他 owner 的同 hash registration 不受影响。设置类操作（SET_PREF/SET_SLOT）要求玩家拥有企鹅效果；成功的 SET_PREF/SET_SLOT 在回复后立即发送 `Catalog(open=false)`。

## 5. 网络协议（`DanceNetwork`，registrar `"1"`）

独立包 ID：`kuso_penguin:dance_request`、`dance_chunk`、`dance_reply`、`dance_catalog`、`dance_playback`、`dance_control`。

- `DancePackets.Action`：`LIST, BEGIN_UPLOAD, END_UPLOAD, CANCEL_UPLOAD, DOWNLOAD, DELETE, RENAME, PLAY, STOP, SET_PREF, SET_SLOT`。`Request` 只允许前 7 个；`Control` 只允许后 4 个。
- `Request(action, ticket, clipId, title, totalBytes, expectedHash)`：字符串/长度全部有界；`expectedHash` 可空。
- `Chunk(ticket, offset, byte[])`：单块 ≤16 KiB，offset 非负。
- `Reply(action, ticket, success, message, clipId, totalBytes)`。
- 导入失败仍回复通用的 `message.kuso_penguin.dance.import_failed`，并额外向该客户端发送一条带界错误的系统消息：`Component.translatableWithFallback("message.kuso_penguin.dance.import_failed.detail", ..., detail)`，`detail` 截断到 256 字符且替换控制字符；完整异常仍写入服务端日志。**包结构未改变**，不需要新版协议字段。
- `Catalog(open, prefs, builtins, clips)`：**只含本人最多 5 个 clip**、4 个 builtin 元数据和 8 槽偏好，不返回全服目录。
- `Playback(dimension, entity, session, clipId, startTick, source, playing)`。单一优先级来源是 `Source.priority()`：`REVIVAL(-1) > MANUAL(0) > MAID_MUSIC(1) > MUSIC(2)`；枚举声明顺序（wire order）保持 `MANUAL, MAID_MUSIC, MUSIC, REVIVAL` 不变。`DancePlaybackManager.DISPLAY_ORDER` 直接按该 priority 排序，不再另写复活特例。`session` 唯一，旧 STOP 不能停止新会话。
- `Control(action, ticket, target, clipId, trigger, slot)`：`slot ∈ [-1,7]`；`SET_PREF` 必须带 trigger；`PLAY` 必须带 clipId。

`DanceClientHooks.install(Listener)` 接收 `onReply/onCatalog/onChunk/onPlayback`，全部是无客户端类引用的不可变载荷。本步由 `client/dance/DanceClientSetup` 在 client setup 安装 `DanceClient`，不依赖 payload/render 回调。

## 5.1 客户端导入、缓存与界面

`dance/DanceImport.convert(Path)` 是纯 Java 有界导入器，返回 `Result(canonical, format, duration, frames, rootMotionRemoved, name)`：

- 规范 JSON 直接交给 `DanceLibrary.canonicalize`，复用同一个 schema/bind/math 校验器。
- 有限 Mixamo DAE 使用 StAX 构建有界树（DTD/外部实体/实体引用禁用，深度≤64、节点≤200000、文本≤16M 字符），只接受 `Y_UP`、有限正 `asset unit`、视觉场景 JOINT 层级和每骨骼 rest matrix。
- 必须存在唯一规范化别名（`mixamorig:`/`mixamorig_` 去前缀）的 Hips/Spine2/Neck/Head、双臂/前臂/手/HandMiddle1、双腿/小腿/脚/ToeBase 及其祖先；只接受同步 `node/matrix` LINEAR 通道，时间戳必须一致且为 30 FPS、2..901 帧、≤30 秒。
- 矩阵必须有限、无透视行、无剪切/镜像/零缩放；方向 retarget 使用 rest world 校准，不把动画首帧当 neutral，也不复制 root translation。输出固定 16 骨骼、root 旋转单位四元数、全部 translation 为零；缺失骨骼/通道会在错误中给出文件名、骨骼和帧。
- 源文件上限 8 MiB；输出仍由 `canonicalize` 限制为 2 MiB。没有 Python/Blender 运行时依赖。

`client/dance/DanceClient` 与 `DanceTransfers`：

- catalog/reply/chunk/playback 全部在主线程分发；下载在 2 个 IO worker 中做 SHA-256 与 `DanceLibrary.canonicalize` 复核，渲染帧只读取已解析的不可变 `PenguinAnimationClip`。
- 内存缓存 LRU 上限 8 条/32 MiB；下载按 hash 去重，最多 4 条 pending，严格校验 ticket/offset/总长度，断线 generation 使旧回调失效并清理缓冲。
- 上传只在 UI 明确选择后开始：worker 先 `DanceImport.convert`，再按服务器 credit 发 BEGIN/chunks/END，上传的正是 `Canonical.bytes()`，使用 `Canonical.hash()` 作为 `expectedHash`。
- `DanceScreen` 由 `Catalog(open=true)` 或轮盘 Manage 打开；支持路径输入/拖放、异步导入状态、导入格式/时长/帧数/root-motion 报告、标题、上传、重命名/删除本人片段、3 个 trigger 与 8 个 wheel slot 分配、恢复默认。trigger 按钮显示当前分配的动作名和槽位名（过长截断并带完整 tooltip）；选择列表用标题而不是 hash 作为主标签；服务器 catalog 是成功/所有权/偏好的唯一来源。
- `DanceScreen.rebuildWidgets()` 在任何重建（catalog、分页、导入、预览返回、窗口缩放）前保存当前路径和标题框内容，因此已输入路径和标题不会丢失。`Reset defaults` 发送 `Prefs.defaults()` 的真实值，而不是把新默认槽重新清空。
- `Preview` 打开 `DancePreviewScreen`：用一个不在世界中注册的 client-only `RemotePlayer`＋企鹅效果渲染原生企鹅；`PenguinRenderContext.pushPreview(entity, clipId, clip, age)` 建立 ThreadLocal 覆盖，并在 `finally` 关闭。覆盖只影响当前预览绘制，且复用与权威播放相同的 `PenguinAnimationPose.evaluate`/内置程序姿态；不修改真实玩家/女仆、不发送网络播放。返回时恢复同一个 `DanceScreen` 实例，预览打开期间不会误取消上传。
- `DanceWheelScreen` 是 8 扇形径向轮盘：显示自己/女仆目标和每个槽位动作名，点击扇区发送 `PLAY`，中心 Stop 发送 `STOP`，Esc/Stop 关闭；Manage 打开管理界面。打开轮盘只请求 `LIST`（`open=false`），catalog 到达时原地刷新标签，不会强制打开管理页。
- `DanceQuickKeys` 注册 8 个独立 `KeyMapping`，默认 `GLFW_KEY_UNKNOWN`，未绑定时不占按键，绑定值由原版 options 持久化。按键先取准星 4 格内的精确女仆，否则取自己，再发送与服务端校验相同的 `PLAY`；空槽只在本地提示，不发送伪造成功。
- 控制修改进入每 2 tick 一条的客户端队列，匹配服务端限速；catalog 刷新保留路径、标题、页和选择，未请求的保存不会重新打开界面。屏幕 `isPauseScreen=false`，使用分页而不是横向溢出。
- `DanceClient.active(entity, source)`、`clip(entity)`、`elapsed(entity, partial)` 供共享渲染入口使用；它们只读权威 Playback，不修改实体/世界。`builtins()` 返回本地内置元数据。退出管理界面取消本界面的上传（进入本地预览时保留准备结果）；断线才清空全部下载缓冲、缓存与 playback。

## 6. 服务端状态机（`DanceServer`）

- `open(ServerPlayer)`、`sendCatalog(ServerPlayer, boolean)`。
- `installControl(BiConsumer<ServerPlayer, DancePackets.Control>)`。默认 listener 回复 `message.kuso_penguin.dance.unimplemented` 且 `success=false`，不会假装成功。服务端启动时由 `DancePlaybackManager` 安装实际处理器并回复最终结果；默认拒绝器只用于尚未初始化或停止后的边界。
- `PLAY/STOP`：PLAY 要求企鹅效果且 clip 为 builtin 或本人上传；STOP 不要求仍有企鹅效果，但运行管理器仍核对并只移除本人拥有的 MANUAL 层。随后交给已安装 listener。
- 上传在票号、声明字节、每玩家数量/字节、全库预留检查通过后才分配缓冲；END 后预留保持到 worker 回投，**排队中的 import 仍计入 4 个 active upload 配额，同一玩家在 pending 期间不能再次 BEGIN**。
- 上传块必须严格等于已接收偏移；重复/乱序/超长/超 tick 配额会取消并回复失败。CANCEL 可作用于正在接收或已排队待 import 的 upload；取消、断线、服务器停止都会释放对应预留，worker 在发布结果前检查 cancelled/stopped，不再发送成功回复。
- 下载只要求 clip hash 存在（不要求本人所有），供邻近观看客户端拉取权威播放数据；按 4/玩家/tick、16 全局/tick 泵送。
- SET_PREF/SET_SLOT/PLAY/STOP 有轻量每玩家速率限制 `CONTROL_COOLDOWN_TICKS=2`（每 2 tick 最多一次），超限回复 busy；该表随断线/停服清理，不是永久全局映射。
- 断线、服务器停止、超时都会释放预留并清空状态；停止时关闭 library 缓存。IO 失败记录日志并回复失败，不静默忽略。

## 7. 命令

`/kusopenguin dances` 通过独立的 `RegisterCommandsEvent`（LOWEST 优先级）把 `dances` 子节点合并到已有 `kusopenguin` 根，不替换 flying-kick/disc/vocals 节点；打开本人目录。

## 8. K 键、停止与支持边界

1. K 的优先级固定为：手持有效女仆胶片 → 有效尸体复活目标 → 有企鹅效果时打开舞蹈轮盘；没有企鹅效果时 K 不会开始舞蹈。轮盘和快捷键都只发送既有 `Control/Playback`，不复制复活判定、不绕过复活。
2. 轮盘/快捷键的“停止”只发送 `STOP` 给当前目标；服务端只移除该玩家拥有的 `MANUAL` 层，不触碰 `REVIVAL`、`MUSIC`、`MAID_MUSIC` 或其他玩家的层。拖动/关闭界面不会直接改世界状态。
3. 支持边界：规范 JSON 与有限 Mixamo DAE（30 FPS、2..901 帧、≤30 秒、≤2 MiB 规范化、每玩家 5 个/10 MiB、全库 128 MiB）；不支持任意 XML/FBX/脚本、非 30 FPS DAE、根位移或非标准 TRS 包裹。女仆控制只接受精确类型 `touhou_little_maid:maid`、本人所有、4 格内且视线可见。
4. 原生预览是客户端独立实体渲染，不注册世界、不修改真实玩家/女仆、不发送播放；它复用同一 16 关节 `PenguinAnimationPose` 与内置程序姿态，因此预览与权威播放不会出现两套数学。

## 9. 开发验证与限制

- 完整 Gradle build 通过；项目没有 JUnit 测试源码（`test NO-SOURCE`），以下为开发环境探针与实际客户端验证。
- 四份实际参考 DAE（Standing Taunt、Flying Kick、Flying Back Death、Flair）均通过 Java 导入，固定 16 关节、移除 root 平移；不支持的坐标轴／插值、缺骨和外部实体声明均拒绝。独立方向四元数为 `(cross.xyz, 1+dot)` 整体归一化，COLLADA 行排列矩阵转置给 JOML；总时长包含淡入淡出。
- 本地动作库核验：规范哈希幂等、严格数值／重复键／深度／bind 检查、两个 owner 注册同一内容、删除一个注册不影响另一方、重新读取后归属保留。此项是独立库测试，不声称两个人同时上传／删除的完整网络压力测试。
- 实际客户端界面完成 DAE 导入、分块上传、服务器保存回执、目录更新、校验哈希的下载缓存、触发设置与轮盘槽位保存。五种动作同时公告时，经四槽传输队列全部完成下载。
- 两个真实客户端联机验证：无企鹅效果的观看者能下载另一玩家的动作；迟加入跟随原有服务器时钟（验证样本在开始约 47 秒后加入）；停止状态正确同步。
- 原生渲染核验：玩家自定义动作进入共享企鹅姿势、顶点有限、渲染作用域平衡；女仆演奏颈部锚点经基岩／Gecko／YSM 共 36 个绘制组合检查。K 边沿打开轮盘、默认八键未绑定、预览返回后的字段与作用域清理均有实际记录。
- 实际服务端验证：自家女仆手动舞蹈暂时暂停 AI，不改 NoAI；非主人被拒绝；停止立即释放。闲置企鹅女仆听到另一女仆上传的 MIDI 后跳所选舞蹈，演奏者保持演奏，音乐停止后释放 AI。
- 无 IM 的独立客户端到达标题画面，TLM 主人适配可独立加载，DAE 导入、四个内置动作和八个键位初始化正常；此项不冒充无 IM 下所有女仆玩法组合的实机测试。
- 中英语言键一致、无重复键，舞蹈／K 相关占位参数及八个快捷键标签已审计。界面以 1920×1080 GUI 4 为主场景，另查 854×480 GUI 2。
- 未做独立 dedicated-server 进程、任意 DAE／FBX、非 30 FPS 源、耗尽全库或断网中途的大规模压力测试。客户端缓存最多 8 个／32 MiB，传输等待最多 4 个；超出此范围的极端多种动作场景不应视为已验证。

Development checks cover actual import/upload/download, two-client late-join and stop synchronization, ownership, transient AI release, native pose rendering and locale parity. These are runtime probes, not JUnit coverage or a standalone dedicated-server test. The supported DAE subset and quotas above are intentional. Final artistic appearance remains subject to user review.
