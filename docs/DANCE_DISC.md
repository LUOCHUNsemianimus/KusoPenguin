# 自定义唱片与企鹅舞蹈 / Custom discs and penguin dances

## 使用 / Usage

输入 `/kusopenguin disc` 打开服务器音乐库，粘贴音频路径或将文件拖入窗口。
导入完成后选择曲目，点击“写入 / 领取唱片”，然后将唱片放入原版唱片机。
手持已有自定义唱片时会更新该唱片，否则加入背包（背包满时掉落）。
音乐库不暂停单人世界，关闭页面会取消尚未提交的导入，不会停止正在播放的音乐。

Run `/kusopenguin disc`, paste an audio path or drop a file, import, select a saved track,
then click **Write / Get Disc**. Insert the disc into a vanilla jukebox. A held custom disc
is updated; otherwise one is added to the inventory or dropped if full. Closing the screen
cancels an unsubmitted import but leaves existing music playing. The screen never pauses
the integrated server. This command needs no operator permission; flyingkick settings still do.

支持 WAV、OGG/Vorbis 和 MP3，单曲最多五分钟；输入文件和转换后的 WAV 均不得超过32 MiB。
音频会转换为单声道 PCM16，服务器按存档保存。附近玩家首次收听时下载并校验缓存，
迟到者按唱片机当前进度播放；音量使用原版“唱片机 / 音符盒”设置。

WAV, OGG/Vorbis and MP3 are supported, up to five minutes. Both input and normalized WAV
are limited to 32 MiB. Audio is normalized to mono PCM16 and saved with the server world.
Listeners within 64 blocks download and hash-check a local cache; late listeners seek to
the current record position. Use the native Records volume slider.

## 舞蹈 / Dance behavior

只有本模组自定义唱片触发舞蹈。默认12格内、带企鹅效果的成年兼容生物会在唱片机周围
寻找可行走、无碰撞的站位排圈，暂停原 AI。三套舞蹈每套播放5秒并循环：

1. 双翼抱头、前后顶腰、站蹲交替；周期0.5秒。
2. 双翼抱柱、身体左右旋转45度、两侧站立/中间下蹲；周期1秒，掌心锚点固定。
3. 下蹲抖动、双翼朝前反向上下摆动45度；周期0.5秒。

Only this custom disc triggers dancing. Affected supported adult mobs within 12 blocks
reserve safe walkable ring positions around the jukebox and temporarily pause native AI.
Three procedural styles cycle for five seconds each: hands behind head/hip thrust (0.5 s),
fixed-palm pole hug with +/-45 degree body swing and center crouch (1 s), and crouching bob
with forward opposite-phase +/-45 degree wings (0.5 s). All three current mesh tiers use
one shared 16-joint rig, including the unchanged-size native head and held-item mounts.
Legacy Block preview does not animate the procedural dances.

玩家仅播放动画，不拉扯位置或锁定操作。原有潜行、骑乘、游泳、鞘翅和飞踢/死亡动画优先。
爬行企鹅临时站起跳舞，音乐结束后恢复原姿态。没有企鹅效果的实体保持原行为。
坐下/趴卧、游泳、骑乘、飞行中的生物不加入；原先设置 NoAI 的生物只播放动画，不擅自移动。
音乐停止、唱片弹出/损坏、离开范围、效果结束或实体死亡后，解除舞蹈并恢复原 AI，保留攻击目标。

Players retain all controls and are never pulled to a slot. Native crouch/riding/swim/Elytra
and active kick/death take animation priority. Prone penguins stand temporarily, then return
to sliding. Unaffected entities keep ordinary behavior. Sitting/lying, swimming, riding or
flying mobs do not join. Pre-existing NoAI mobs animate in place. Music stop, ejection/block
break, range exit, effect loss and death release dance ownership without changing saved NoAI
or attack targets. Failed paths retry at most once per second; mobs are never teleported.

服务器配置 `dance.radius` 默认12格，`dance.spacing` 默认0.5格。
间距指原版碰撞箱边缘间隔；视觉缩小不等于碰撞箱缩小，因此不能让站位重叠。

Per-world server config `dance.radius` defaults to 12, and `dance.spacing` defaults to .5.
Spacing is the minimum gap between reserved native collision boxes; visual shrinkage does
not shrink those hitboxes. Additional concentric rings accommodate more participants.

## 存储和边界 / Storage and limits

- Server: `<world>/kusopenguin/music/index.json` and `audio/<sha256>.wav`.
- Client: `<gameDir>/kusopenguin/music-cache/<sha256>.wav`.
- Library: 100 unique songs total, 20 per uploader, 512 MiB per world; SHA-256 deduplication.
- Transfers: 16 KiB chunks, server-acknowledged four-chunk byte window; one upload/download
  per player, four concurrent each, 128 MiB reserved each direction; bounded two-worker IO.
- Exact offsets, declared-size bounds, canonical WAV metadata and hash checks remain enforced.
  Local file paths never cross the network. Connection/generation/session tokens prevent late
  asynchronous results from reopening a closed page or restarting an old record.
- Server music storage should be backed up with its world. No music deletion UI in this version.
- MP3 decoder: unmodified bundled JLayer 1.0.1; see `THIRD_PARTY_NOTICES.md` and bundled LGPL text.
