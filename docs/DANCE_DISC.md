# 自定义唱片与企鹅舞蹈 / Custom discs and penguin dances

## 使用 / Usage

输入 `/kusopenguin disc` 打开服务器音乐库，粘贴音频路径或将文件拖入窗口。
也可以使用默认未绑定的“打开自定义唱片库”客户端快捷键；它只在游戏内（无界面、窗口活跃、未暂停）通过同一命令打开，不要求企鹅效果。
导入完成后选择曲目，点击“写入 / 领取唱片”，然后将唱片放入原版唱片机。
手持已有自定义唱片时会更新该唱片，否则加入背包（背包满时掉落）。
音乐库不暂停单人世界，关闭页面会取消尚未提交的导入，不会停止正在播放的音乐。

Run `/kusopenguin disc`, paste an audio path or drop a file, import, select a saved track,
then click **Write / Get Disc**. The same screen is available from an unbound client key that
sends this exact command only during active, unpaused gameplay and does not require the penguin
effect. Insert the disc into a vanilla jukebox. A held custom disc
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

自定义舞蹈本身仍从 `/kusopenguin dances` 或默认未绑定的“打开舞蹈管理”快捷键进入；快捷键同样复用既有命令，不检查企鹅效果，也不改变唱片舞蹈的判定。

只有本模组自定义唱片触发舞蹈。默认12格内、带企鹅效果的成年兼容生物会在唱片机周围
寻找可行走、无碰撞的站位排圈，暂停原 AI。三套舞蹈每套播放5秒并循环：

1. 双翼抱头、前后顶腰、站蹲交替；编排周期0.5秒，1.5倍播放为1/3秒。
2. 双翼前伸抱柱、身体左右旋转45度、两侧站立/中间下蹲；编排周期1秒，1.5倍播放为2/3秒。杆臂改为明显前伸的开放姿态，约25度摆动（小于摆手舞的45度），前臂相对上臂保留约20度微弯；原“掌心完全固定”约束已放宽，允许少量掌部位移，但仍保持双手朝前/朝柱的环柱观感。
3. 下蹲抖动、双翼朝前反向上下摆动45度；编排周期0.5秒，1.5倍播放为1/3秒；躯干同一周期上下起伏约1企鹅像素，并前后俯仰45±6度、左右侧倾±4度。

三套舞蹈在评估舞姿时都会应用以脚底为锚的模型局部“果冻”形变：高度在80%至120%之间，
水平方向取高度的平方根倒数（高点收窄、低点变宽，体积近似守恒）。抱头和抱柱在高站立时
拉伸、低蹲时压缩；摆手舞跟随实际上下起伏相位。头部随变形后的颈部移动，但保持原生大小和
刚性旋转，不做头部压扁，也不对装备/头层重复应用形变。脚底触地点固定，实体跳跃、物理和
碰撞箱均不改变。

Only this custom disc triggers dancing. Affected supported adult mobs within 12 blocks
reserve safe walkable ring positions around the jukebox and temporarily pause native AI.
Three procedural styles cycle for five seconds each: hands behind head/hip thrust (authored
0.5 s, played at 1.5x = 1/3 s), open forward pole arms with +/-45 degree body swing and center
crouch (authored 1 s, played at 2/3 s), and crouching bob with forward opposite-phase
+/-45 degree wings (authored 0.5 s, played at 1/3 s). The pole arms swing about 25 degrees
instead of the wave's 45 and keep a slight 20 degree forearm bend; the former fixed-palm
constraint is deliberately relaxed, allowing modest palm movement while the hands stay
forward/toward the column. The wave torso bobs about 1 penguin pixel and pitches 45+/-6 degrees
with +/-4 degrees roll on the same arm cycle. All three current mesh tiers use one shared
16-joint rig, including the unchanged-size native head and held-item mounts.
Legacy Block preview does not animate the procedural dances.

While a dance pose is evaluated, all three styles and mesh tiers also apply a model-local jelly
deformation anchored at the foot floor: height ranges from 80% to 120% and horizontal scale is
the inverse square root of height, so stretched phases narrow and crouched phases widen at
near-constant volume. Hip/pole phases stretch when standing tall and compress when crouched;
the wave follows its actual bob phase. The native head follows the deformed neck but keeps its
native unit size and rigid rotation, with no head squash and no double application to gear or the
head layer. Foot contact stays fixed; entity hopping, physics and hitboxes are unchanged.

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


## 舞姿调整 / Pose sampling

三套舞蹈统一以30FPS更新姿势（每0.033秒一帧，每套5秒/150帧），不限制游戏帧率、移动或音乐；
风格索引仍按真实时间每5秒切换一次。动作时钟先取当前风格内已量化的真实经过时间对5秒取模，
再乘1.5倍；因此每套动作都从零相位开始，15秒整轮结束时不会累积出半个周期的反向。
内部编排以1.5倍速播放：编排周期0.5秒的抱头/摆手变为1/3秒，1秒的抱柱变为2/3秒。

抱头舞上臂相对水平上抬30度，前臂相对上臂仅弯20度（夹角160度），翼尖朝向跟随前臂，
不再叠加旧版约90度的腕部回折；两段翼骨保持绑定长度并左右镜像。抱柱舞中点前倾45度，
左右旋转终点保持站立与正负45度转身；杆臂明显前伸开放，约25度摆动，前臂保留约20度微弯，
翼尖跟随前臂。原固定掌心约束已明确放宽，允许少量掌部位移，但仍保持双手朝前/朝柱的
环柱观感，不渲染杆、环面或额外道具。摆手舞双翼保持朝前并反相上下摆动45度；躯干随同一
1.5倍手臂周期起伏约1企鹅像素、俯仰45±6度、侧倾±4度。触地补偿只防止向下穿地，
不阻止躯干起伏。30FPS采样直接使用标准正弦/余弦，不使用旧5FPS峰值归一化，也不限制游戏帧率。

果冻形变在三套舞蹈、三档网格评估舞姿时都会应用：以脚底Y=24企鹅像素为锚，在模型局部
左乘到包括根骨骼在内的全部世界矩阵；高度系数0.8至1.2，水平系数为高度的平方根倒数，
因此拉高时变窄、压低时变宽且行列式约为1。抱头/抱柱在站立高位拉伸、下蹲低位压缩；
摆手舞与实际上下起伏同相位。脚底触地点保持固定，不产生实体跳跃、物理或碰撞箱变化。
原生头部随变形后的颈部移动，但保持原生单位大小和刚性旋转；旋转使用变形前缓存的未缩放
胸部朝向，避免头部被压扁或装备/头层重复应用形变。持物锚点仍使用同一翼尖世界变换，
物品本身不随身体缩放。

All three dances update poses at30FPS (one pose per0.033seconds,150poses per5second style),
without limiting game FPS, movement or audio. The style index still changes every5real
seconds. The motion clock takes the quantized style-local elapsed time modulo5seconds and
only then applies1.5x, so every style starts at phase zero and a full15second cycle does not
accumulate a reversed half-cycle. The internal choreography plays at1.5x, so authored
0.5second head-hug/wave cycles become1/3second and the1second pole cycle becomes2/3second.
Head-hug upper wings rise30degrees above horizontal and the forearms bend only20degrees away
from straight (160degree included angle); tip frames follow the forearm instead of adding the
old ~90degree wrist fold, and both bind link lengths are preserved with mirrored left/right.
Pole-hug keeps its45degree center lean and upright +/-45degree endpoints, but its arms now open
visibly forward with about25degrees of swing, a slight20degree forearm bend and forearm-following
tips. The former fixed-palm constraint is deliberately relaxed, allowing modest palm movement
while the hands stay forward/toward the column for the around-column impression; no pole, torus
or prop is rendered. The arm wave keeps both wings forward with opposite-phase +/-45degree swing;
the torso bobs about1penguin pixel and pitches45+/-6degrees with +/-4degrees roll on the same1.5x
arm cycle. Floor fit only compensates downward penetration, so the torso can still rise and fall.
Standard sine/cosine sampling at30FPS reaches the authored angles without old5FPS peak
normalization and never throttles game FPS.

Jelly deformation applies to all three styles and all three mesh tiers while a dance pose is
evaluated: it is anchored at the foot floor (Y=24 penguin pixels) and left-multiplied onto every
world matrix, root included. Height stays within0.8..1.2 and horizontal scale is the inverse
square root of height, so stretched phases narrow and crouched phases widen at near-constant
volume (determinant around1). Hip/pole phases stretch when standing tall and compress when
crouched; the wave follows its actual bob phase. Foot contact remains fixed and there is no
entity hop, physics or hitbox change. The native head follows the deformed neck but keeps its
native unit size and a rigid rotation built from the unscaled chest orientation cached before
deformation, so the head is never squashed and gear/head layers are not scaled twice. Held items
keep the same visible wing-tip anchor transform and are not body-scaled in size.
