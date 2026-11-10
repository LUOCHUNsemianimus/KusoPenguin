# 人声与麦克风 / Vocals and microphone

乐器弹唱、人声绑定和独立清唱麦克风共用同一套人声播放。支持嘴型骨骼的 Gecko 酒狐女仆会随人声改变嘴型，麦克风清唱另有音符和身体轻摆。

## 使用方法

- 乐器弹唱：先在沉浸式奏乐中选曲，再点击“人声设置”或输入 `/kusopenguin vocals`。导入或选择 MP3/WAV/OGG，保存绑定；用“弹唱／仅弹奏”切换人声。配置好的乐器交给女仆后保留绑定。
- 对齐人声：偏移单位为毫秒，正数让人声延后，负数跳过音频开头。录音需与 MIDI 的速度匹配；不会自动生成歌声或变速。
- 清唱：取得 `/give @s kuso_penguin:microphone`，右击打开设置。只需导入或选择人声、保存绑定，再按“播放／暂停”，无需 MIDI。已配置且未显式暂停的麦克风交给女仆后会自动播放；显式暂停会保留。
- 麦克风持在胸前；持麦角色的普通乐器音符会静音，其他角色不受影响。清唱独立于附近的 MIDI 合奏。
- 音频沿用服务器曲库与附近客户端下载缓存；最多 5 分钟／32 MiB，音量 0–2。麦克风同时保存一首当前音频，曲库内音频可切换。普通乐器可保存 16 条曲目绑定。
- 编辑会话有效期十分钟，操作时续期；换物品、换手或换曲后，旧页面不能修改新物品。超时请重新打开。
- 麦克风功能使用可选的沉浸式奏乐适配；未安装时模组仍可启动，麦克风会提示安装需求。

## Persistent data

`com.luochun.kusopenguin.vocals.VocalData` stores optional bindings on the instrument's own
`CUSTOM_DATA` under `KusoVocals`. It preserves every unrelated component/tag, allows up to 16
bindings keyed by exact melody resource id plus melody SHA-256, and validates the 64-character
lowercase track hash, `offsetMs` in `[-30000, 30000]`, finite `gain` in `[0, 2]`. Bindings are
disabled by default; `bind` enables, while remove and toggle remain independent. The existing music
library is only queried, not replaced.

`com.luochun.kusopenguin.compat.MelodiesVocalCompat` is the optional Immersive Melodies bridge:
`available`, `isInstrument`, `playing`, `selectedMelody`, `serverFingerprint` and `fingerprint`.
The last one streams `Melody.STREAM_CODEC` into a 4 MiB-capped Netty buffer once and hashes it with
`WaveFile.hash`; the buffer is always released. The server checks datapack/index membership before
`ServerMelodyManager.getMelody`, so a missing id never triggers a disk read or deletion. No client
class is referenced.

## Standalone microphone

`com.luochun.kusopenguin.vocals.VocalItems` registers `kuso_penguin:microphone` and adds it to
`TOOLS_AND_UTILITIES`. Immersive Melodies is optional. With IM present the item is a lazy
`InstrumentItem` subclass with a silent eight-octave adaptor; that adaptor deletes only its own
freshly-created `Sounds.sounds` descriptor keys, and `inventoryClientTick` is deliberately empty so
the microphone never fetches or plays MIDI notes. Without IM the same id is a plain item whose use
message asks for Immersive Melodies, and no IM class is loaded.

`VocalData` adds the sentinel `MICROPHONE_MELODY = "kuso_penguin:microphone"`, plus
`microphoneBinding`, `microphoneDurationMs`, `configureMicrophone` and `clearMicrophone`. A
microphone binding stores the audio SHA-256 in both hash fields, offset 0, and its duration under
`KusoVocals`; configuring replaces the previous sentinel binding (one active clip), while unrelated
bindings, tags and components are preserved. An unconfigured microphone has no binding and an
empty session hash.

The native IM `PLAYING`, `START_TIME` and `PAUSED_TIME` components are the clip clock. `play` is a
no-op only while already playing with a valid non-future start; it repairs a future/negative start
explicitly, shifts `START_TIME` by the paused duration when resuming, or starts at `world.gameTime`.
`pause` records the current time only while playing and clears `PLAYING`; `reset` clears both
timestamps. `startTime` and `playing` expose the clock to the client. The microphone never writes
MELODY/TRACKS and has no random native-song autoplay: a non-player with a valid binding starts the
configured clip only when not playing and not explicitly paused.

`VocalPackets.Action` appends `PLAY` and `PAUSE` without changing the wire shape. `VocalServer`
opens an unconfigured microphone with the sentinel id and empty hash, binds a library track with
`durationTicks * 50` ms clamped to `[1, 300000]`, ignores offset and uses request gain, then replaces
the session with the same ticket/hand/stack/connection and the new audio hash. Removing the
microphone clears its binding/duration, resets the clock and returns the session hash to empty.
`PLAY` requires an existing library clip, enables vocals and starts the clock; `PAUSE` only pauses.
Both opcodes are rejected for ordinary MIDI instruments, whose existing bind/toggle/playback path
is unchanged.

## Client microphone

`VocalTimeline.snapshot` checks a held microphone (main hand, then off hand) before IM's native
melody path. A held but paused/unconfigured microphone returns null and never falls back to another
instrument's voice. A playing microphone uses only `VocalItems.startTime` and the synchronized
`entity.level().getGameTime()`: `elapsed = (gameTime - start) * 50`, `phase = elapsed % duration`, and
`loop = elapsed / duration`. Future or negative starts remain silent until an explicit Play repairs the invalid clock. The snapshot uses the `kuso_penguin:microphone` sentinel id, the audio
track hash as fingerprint and the stored duration as length. It performs no `getMelody`, progress
request, native fake-MIDI setup or nearby-ensemble sync, so an ordinary MIDI leader cannot overwrite
the microphone's clear-vocal clock.

`MicrophonePose` is an optional lazy client facade registered from `VocalClientSetup`. With IM
present it registers an `ItemAnimators` entry for the microphone; the animator writes only the
physical held arm's model part (`getMainArm()` for the main hand, opposite for the off hand) with
`x=-1.05`, `y=±0.35`, `z=±0.25`, leaving the other arm and head untouched. The same animator feeds
the native player and the existing shared penguin/maid snapshot; there is no second renderer. The
`@Pseudo` `MelodiesMicrophonePoseMixin` makes a held microphone win `EntityModelAnimator.getInstrument`
even during dual-wield so the chest pose is stable.

`@Pseudo` `MelodiesMicrophoneSoundMixin` redirects the private IM `InstrumentItem.playNote` sound call
to the pinned `SoundManager` interface with volume 0 only when the source living entity holds a
microphone. Pitch, length, sustain, delay, note progress and particles remain unchanged, and other
entities/global MIDI audio are untouched; this is not a second music engine. Both optional mixins are
client-only and absent-IM startup skips them.

The vocal screen detects the microphone from `State.itemId`. It displays the selected audio title
from the library rather than the sentinel/hash, disables the offset at 0, and replaces the ordinary
sing/instrumental row with Play/Pause (`PLAY`/`PAUSE`). Upload, catalog selection and BIND/REMOVE work
without any MIDI melody; a binding hash change preserves typed fields and the selected clip. Only an
in-flight import of this screen may show the shared uploader status. Successful END_UPLOAD becomes a
vocal-specific imported status, failures use the reply message, and save/toggle/play/pause replies
honor the current `State.status`.

## Packet contract

`VocalPackets.Request(Action action, UUID ticket, InteractionHand hand, String trackId,
int offsetMs, float gain, boolean enabled)` uses `OPEN`, `BIND`, `REMOVE`, `TOGGLE`, `PLAY`,
`PAUSE`.
`VocalPackets.State(boolean open, UUID ticket, InteractionHand hand, String itemId,
String melodyId, String melodyHash, VocalData.Binding binding, boolean enabled, String status)`
carries the optional binding as a boolean followed by bounded fields. Strings and numbers are
validated at construction, including empty-or-lowercase-64 hashes and a 128-character status.

`VocalNetwork` registers the typed request/state payloads. `VocalClientHooks` is a common
`Listener.onState(State)` plus `install`/`receive`; `VocalScreens` installs the listener and
`VocalScreen` sends requests.

## Sessions and commands

`/kusopenguin vocals` (and `VocalServer.open`) chooses the main-hand instrument first, then the
off hand. Opening resolves the selected melody and its server fingerprint. A session lasts 12000
ticks (ten minutes) and each accepted edit refreshes it. Edits require the same connection, ticket, hand, exact
held stack object, selected melody and current fingerprint; stale edits return a failure state and
change nothing. Requests are limited to one per 5 ticks, including OPEN. BIND requires an existing
music-library track and refuses a seventeenth distinct binding with an explicit error. Logout and
server stop clear transient state.

Status values are translation keys under `message.kuso_penguin.vocals.*` and
`message.kuso_penguin.microphone.*`; the bilingual entries are supplied with the item/editor.

## Client playback

`client.vocals.VocalTimeline` is an optional client facade. Its lazy implementation reads IM's
persisted `PLAYING` flag from the first playing held instrument and uses the actual effective
melody, phase, length, native loop counter and effective start. It never calls `tick`/`sync`, never
uses `MelodyProgress.isPlaying`, and only targets the actual player or exact maid type. A
`@Pseudo` read-only mixin (`MelodyProgressAccessor`) exposes `loop` and `getStartTime` without IM
types in its public signatures.

`VocalClient` scans nearby players/maids within 32 blocks, caps at 64 slots, and matches the
effective melody id and fingerprint against the instrument's enabled `VocalData.Binding`. It keys
one voice per entity by the binding plus native loop/start, caches item settings by the custom-data
component identity, and reuses `DiscTransfers.audio`. Positive offsets delay the start; negative
offsets seek into the audio. Long MIDI rests do not stop playback. A shorter audio ends at EOF and
waits for the next native loop; an audio longer than the MIDI cycle is truncated at the loop. Drift
over 400 ms may reseek at most once per second. Downloads fail once per key/generation and expose
`VocalClient.status()`, without a per-tick retry.

`VocalSound` uses `MusicItems.CUSTOM_MUSIC` with `SoundSource.RECORDS`, linear attenuation and a
living-entity position. It opens `CachedPcmStream` asynchronously at the latest atomic expected
position, estimates progress from the opened offset plus `System.nanoTime()`, and stops at the
stream end or native MIDI boundary. `CachedPcmStream` is the single shared WAV reader used by both
discs and vocals.

`VocalClient` implements `VocalClientHooks.Listener`, stores the latest editing `State`, and exposes
`state()` plus `installStateListener(Consumer<State>)`. `VocalClientSetup` installs both the playback
listener and `VocalScreens::accept` at client setup.

## User steps

Run `/kusopenguin vocals`, or open an Immersive Melodies instrument's native melody screen and press
the `人声设置 / Vocals` button below its icon row. The native search, song selection, pause/play and
track controls stay in place. The vocal screen shows the effective selected song and hand, lets you
paste or drag a WAV/OGG/MP3 into the shared music library, pick a catalog track, enter an offset in
milliseconds (positive delays; negative skips the intro) and a gain in `[0, 2]`, then save or remove
the binding and toggle `弹唱 / Sing` versus `仅弹奏 / Instrumental`. Done/Back returns to the native
melody screen when it was the parent; otherwise Done closes the screen. Giving the configured
instrument to a maid carries the binding with the item.

Right-click a microphone to open the same editor directly. No MIDI song is needed: import or choose
a library clip, press Save binding, then Play/Pause. The microphone binding uses its own persisted
clock, so nearby MIDI ensemble changes do not move the vocal. Give a configured microphone to a maid
to carry the clip; she autoplays from the server clock until explicitly paused. The item is a plain
microphone when Immersive Melodies is absent, with an install message; the optional IM adapter is
required for the clock/animation path. During playback the singer sways gently and emits note
particles, which are hidden for the local singer in first person.

Only an actual OPEN reply creates a screen. Busy, save, remove, toggle, play and pause replies update
the live screen or show a translated status; they never recreate a screen the user closed. Upload
progress, catalog changes and state replies preserve typed path/offset/gain values and the current
page; microphone hash changes preserve the selected clip and inputs.

Limits: one active microphone clip per item, audio up to 5 minutes / 32 MiB through the shared
importer, gain `[0, 2]`, microphone offset fixed at 0, and one positional vocal voice per entity.
The same vocal stream drives mouth motion for Gecko Wine Fox maids with compatible mouth bones,
including the retained head in penguin form. Mouth shapes follow the measured PCM amplitude
relative to nearby active audio at the current playback position, with a resting mouth in quiet
passages. The draw restores the model's
own expression as soon as playback stops. This amplitude-based effect applies to both microphone
and instrument vocals.

## 验证 / Validation

- 完整构建通过；`test NO-SOURCE`，不宣称存在 JUnit 用例。
- 实际客户端完成 MP3 导入、无 MIDI 清唱、保存绑定、弹唱切换与原曲目页入口检查；1920×1080、GUI 4 页面无重叠。默认和平生存验证存档。
- 两个真实客户端：另一玩家中途加入，接收玩家／女仆两路人声并定位当前进度；暂停后两路停止。音频、网络、数据类与最终包的对应二进制哈希一致。
- 实际普通女仆 MIDI＋人声，长休止仍有音频；真实 IM 音符调用仅对持麦玩家／女仆置零，其他实体音量保留。
- 玩家与基岩／Gecko／YSM 女仆的持麦正面、副手及代表性侧面已作 GPU 检查。最终第三人称比例 0.4；Minecraft／BB 源同步。IM 的独立 `microphone_hand` 模型已注册并加载。
- 无 IM 的实际原生客户端到达标题，注册 PlainMicrophone，IM 实现与静音适配类未链接。

证据位于工作区 `.workflow/projects/kuso-penguin/runtime/vocals-20261030/`。专用服务器进程未另行启动；联机行为通过局域网集成服务器验证。所有模型的用户主观观感仍待人工确认。
