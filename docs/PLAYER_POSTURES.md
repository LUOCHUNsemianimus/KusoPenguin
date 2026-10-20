# Player postures: crouching, sitting and swimming

This step extends the penguin player replacement from standing/running to three native player
states. It does not add a new action, input, collision box or movement rule.

## Scope

- Adult `Player` entities with the penguin effect are now replaced while `isCrouching`, while the
  native model riding flag applies, while `Pose.SITTING` applies, and while `swimAmount > 0`.
- Other supported mob types keep the previous complete-vanilla fallback in those states.
- Baby, invisible, glowing, fall-flying and spectator exclusions are unchanged.
- Head size, the five legacy cube roots and all three mesh tiers are untouched.

## One posture per render frame

`PenguinPlayerPosture` is evaluated once per `PenguinRenderContext` frame and cached. It is the
single source for body translation corrections and for the head-follow delta. The body layer,
held-item mount and animation preparation all enter through `PenguinRenderContext.prepareBody`,
so the body cannot be reset/adapted with a different posture than the head or hands.

### Body translation conversion

`PenguinPoseAdapters` copies rotations and the difference from each source part's initial pose.
Player-model translations are authored in native model pixels, while the penguin body is drawn
with `profile.bodyScale()`. For player postures the helper therefore rewrites each target
translation as:

```text
target = penguinInitial + (sourceCurrent - sourceInitial) / bodyScale
```

This keeps the block-space displacement of the native pose. It applies to the body, both arms and
both legs; the humanoid arm-to-wing side mapping stays the adapter's existing mapping. With the
default humanoid profile (`bodyScale = 0.5`) a native `+3.2 px` crouch drop becomes `+6.4 penguin
px`, i.e. the same `0.2` blocks.

### Head follow

The head remains native-sized and keeps its native rotation. The helper builds the penguin body
frame in renderer-root block units for the neutral pose and the current pose:

```text
bodyFrame = translate(0, bodyOffsetBlocks, 0)
          * scale(bodyScale / 16)
          * translate(correctedPartPixels)
          * rotateZYX(body rotations)
```

The translation-only body change `current * inverse(neutral)` is applied to the neutral head
mount. The resulting delta is the single head mount used by `PenguinHeadRenderer` and every
head-following layer. Body rotation is not copied onto the head, so the native crouch/swim head
pitch is preserved.

### Sitting

The vanilla riding flag already sets the short legs to the forward-folded riding rotations, lowers
the wings and moves the arms. The adapter copies those rotations, so the body and both held items
follow. `Pose.SITTING` without the model riding flag applies the same forward-folded short-foot
rotations in the shared helper; no new sitting input or dependency is introduced.

### Swimming

`PlayerRenderer.setupRotations` remains the only owner of the global swim pitch/translation. The
helper adds no second horizontal or pitch rotation. Native humanoid arm/leg swim cycles are copied
by the existing adapter, so the penguin wings and short feet follow the native motion. Because
translations are reset every draw, returning to standing restores the neutral penguin pose.

## Clip priority

1. Death copy rendering (`PenguinDeathClient.rendering`) wins; it keeps the dedicated death clip.
2. While a non-standing posture is active, ordinary taunt/kick visual sampling is suppressed and
   the adapted native player pose is drawn.
3. Outside a non-standing posture, the existing standing taunt/kick/death selection is unchanged.

## Verification status

Actual final-version render probes passed for crouching, boat and minecart riding, swimming
and return to standing at High/Medium/Low, with the original head and sword/shield. Wide and
slim native player renderers passed head scale=1, joined neck and per-part ground-bound checks
for all tiers. Torso and each short foot have ground error below 0.0001 blocks after accounting
for the native crouch render offset/scale. Repeated death with early respawn passed regression.

已实机验证潜行、原版船/矿车坐姿、游泳和恢复站立，覆盖三档多面体网格与双手持物。
宽臂/细臂皮肤的原头大小和颈部连接、腹部及双脚落地误差检查通过。
坐姿使用已有骑乘或原生 SITTING 状态，不新增坐下按键；第三方坐姿模组尚未专门验证。

Dedicated servers, third-party sitting/renderer/animation rewrites, glow/invisibility special
rendering and every possible item-use pose still need separate compatibility checks.

## Crouch ground fit / 潜行落地修正

The half-height belly cannot keep the full humanoid crouch drop without intersecting terrain.
Once per draw, the helper measures the current mesh torso and each foot, includes the native
player crouch render offset (-2/16 blocks) and .9375 player renderer scale, and derives a ground
correction. Torso, native head and both wing/item mounts share the torso correction. Each short
foot keeps its own ground correction so a low belly does not lift both feet into the air.
Standing, seated, swimming and death poses do not receive this crouch-only correction.

潜行时按实际网格边界计算腹部和双脚的落地位置，并计入原版潜行渲染偏移。
身体、原头和翼端持物同步修正；短脚分别贴地，避免腹部过低时把双脚整体抬悬空。
不改变模型资源、玩家碰撞或镜头，也不把此修正用于站立、坐姿、游泳和死亡动作。
