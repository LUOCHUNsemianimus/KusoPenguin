# 女仆弹唱可行性 / Maid vocals feasibility

2026-10-30。**仅调研，尚未实施人声绑定、麦克风或口型。** 提琴手持修复属于另一个已验证的代码改动。

## 结论

两个方向均可行，应共用一套“预录人声资源、曲目绑定、同步播放”后端。建议先完成曲目绑定，再增加麦克风作为新的物品入口。MIDI 本身不包含录制的人声；这里的“唱”是播放玩家提供的音频，不是语音合成，也不会自动把任意人声改成 MIDI 的节奏。

现有 MP3/WAV/OGG 导入、服务端存储、校验、分块传输和客户端缓存可以复用。已有 JLayer 解码器，不需要为 MP3 再引入一个解码库，也不要求玩家安装 Python/FFmpeg。现有唱片音源绑定方块位置，不能原样用于移动女仆；需要抽出音频流工厂并新增跟随实体位置的声音实例。

## 已核对的代码事实

- 版本范围：开发环境 Immersive Melodies `0.8.0+1.21.1`、车万女仆 `1.5.3`。本地 IM 缓存源码见 `.workflow/projects/kuso-penguin/work/melodies-20261028/source/immersive_melodies/`，女仆源码见 `work/maid-reference/`。缓存可能含本地修正，不能当作所有官方版本的保证；本轮还对实际 JAR 的 `MelodyProgress` 进行了 javap 核对。
- IM `InstrumentItem` 使用 `PLAYING/MELODY/START_TIME/PAUSED_TIME/TRACKS`；`MelodyProgressManager.sync` 允许附近领奏覆盖当前旋律。人声必须跟随 **实际有效旋律**，不能只读取物品上选中的曲目和起始时间。
- `MelodyProgress.getCurrentlyPlaying()` 与 `getTime()` 提供有效曲目及循环内时间；`getStartTime()` 是包可见方法，外部模组不能直接调用。
- `MelodyProgress.isPlaying()` 实际依据距最后音符的时间。不可用它作为人声启停开关，否则长休止、尾音或仅人声段可能中断、重启。
- `MidiParser` 会调用 `melody.trim()` 去掉首音符前空白；绑定必须提供可调的毫秒偏移量。
- 女仆 `ImmersiveMelodiesCompatServerInner.isInstrumentItem` 使用 `instanceof InstrumentItem`；客户端兼容层已有演奏进度桥接。新麦克风需要满足音乐任务的识别条件。
- KusoPenguin `MusicImport` 已支持上述格式；`WaveFile` 限制标准化 PCM 为单声道、16-bit、8–48 kHz，单资源最多 32 MiB/300 秒，分块最多 16384 字节。新入口应复用验证、配额与权限，不能直接接受任意客户端路径。

## 方向一：曲目绑定人声（推荐先做）

建议保存绑定：曲目资源 ID、曲目内容哈希、音频哈希、主人/女仆主人权限范围、偏移毫秒、音量、循环及结尾策略。哈希可避免同名曲目被替换后错误套用旧人声。编辑时可试听并微调偏移，默认一名表演者只产生一路人声。

服务端管理上传资源、绑定与权限，附近客户端按需缓存；实际播放跟随 IM 的有效旋律进度。不能另起一套与 IM 合奏覆盖不一致的唱片时钟。下载完成或中途进入范围时，按当前进度定位，不从头补唱。

必须覆盖暂停/继续、循环、换曲、领奏变化、离开范围、跨维度、死亡/卸载与删除资源；多人演奏时明确每位表演者是否唱，防止同一实体重复发声。人声可跟随移动女仆，并具有距离衰减。

IM 的游戏 tick 时钟与音频设备采样时钟不是同一个时钟。服务器卡顿时需要误差阈值、定位或重启策略；不能承诺任何卡顿情况下都逐采样对齐。初版只播放与 MIDI 速度匹配的录音，不自动变速；单纯改变声音 pitch 也会改变音高。

## 方向二：麦克风

复用方向一的上传、绑定和播放器，新增物品、模型、演唱持握姿态和音乐任务识别。麦克风应跟随当前曲目的有效时间播放人声，不再触发普通乐器音色。

需要特别验证 IM 的静音入口：`inventoryClientTick` 调用私有四参数 `playNote`，**仅覆盖公开三参数方法不能阻止默认 MIDI 发声**。候选方案是覆盖全部音域的静音音色，或加入范围明确的兼容钩子；二者尚未实机验证，不复制整套 IM 播放循环。还需为麦克风 ID 注册专用 `ItemAnimators`，避免回退到默认长笛姿态。

保持 IM 可选依赖：注册和类加载必须隔离，未安装 IM 时有安全行为，不让普通客户端或专用服务器因缺类崩溃。麦克风是否允许无企鹅效果的女仆使用，可作为后续产品规则；现有企鹅模型替换仍受药水效果控制。

## 边界与实施顺序

1. 先实现共用资源/绑定/实体声音及有效时间同步；验证一名女仆、一首 MIDI 与录音。
2. 加入配置界面、多人缓存和完整生命周期，再扩展麦克风。
3. 嘴部开合单独考察。默认基岩、Gecko 和 YSM 不保证存在相同嘴部骨骼；首版可有演唱姿态而没有口型。音量驱动开合也不等于音素级口型。

验收至少使用两个真实客户端，覆盖晚加入、下载等待、长休止、暂停继续、循环换曲、合奏领奏切换、移动女仆、服务器卡顿、资源缺失及偏移/时长不一致；检查权限、带宽/存储限制、停止后无残留声音与原 AI 不变。不得把编译通过当作音频同步验收。

## English summary

Both prerecorded-vocal approaches are feasible. Build one shared asset/binding/synchronization backend first, then add a microphone as another entry point. Reuse the existing MP3/WAV/OGG importer and bounded transfer/cache infrastructure, but add an entity-following sound source. Follow Immersive Melodies' effective ensemble timeline, with configurable vocal offset and explicit pause/loop/late-join handling. This is neither MIDI-to-singing synthesis nor automatic lip synchronization. No singing implementation is included in this change.

## 工作流记录

下游本次调研 CTX 7.65%、缓存命中约 98%，在单次响应输出上限处停止，未产生文档或源码修改；没有反复续跑。上游复核已有源码证据并完成本文，保留原运行记录。提琴修复另外通过完整构建及玩家/三类女仆模型、主副手和正侧面的真实渲染检查。
