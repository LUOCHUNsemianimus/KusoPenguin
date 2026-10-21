# Player postures: crouching, sitting, swimming and gliding

This step extends the penguin player replacement from standing/running to the existing native
player postures, and adds the native Elytra gliding posture without changing flight physics.

## Scope

- Adult `Player` entities with the penguin effect are now replaced while `isCrouching`, while the
  native model riding flag applies, while `Pose.SITTING` applies, while `swimAmount > 0`, and
  while `isFallFlying` (Elytra).
- Other supported mob types keep the previous complete-vanilla fallback in those states, including
  fall-flying.
- Baby, invisible, glowing and spectator exclusions are unchanged.
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

### Gliding (Elytra)

`GLIDING` is classified from the native `isFallFlying` state before crouch/swim, so the body,
head, both wings/held items and the shared mesh all use the same evaluated pose. The posture
suppresses taunt/kick sampling exactly like the other non-standing postures: a standing clip must
not replace the native glide pose. No crouch ground fit is applied because the player is airborne.

Native ownership is unchanged:

- `PlayerRenderer.setupRotations` stays the sole owner of the global fall-flying pitch/yaw/roll,
  including the launch blend and turn roll. The penguin adapter adds no second horizontal rotation.
- `HumanoidModel.setupAnim` still supplies the native head pitch, limb-swing damping, arm/leg
  rotations and use-item arm poses. `PenguinPoseAdapters` copies those values through its existing
  humanoid mapping, so no new animation asset or clip is introduced.
- Translations are converted from native pixels as described above, then reset on every draw.
  Landing therefore returns to the neutral standing penguin pose without a separate reset path.

## Elytra layer mounting

Outside active gliding, replaced penguins keep the previous hidden-Elytra policy. While a replaced
player is actually fall-flying, `ElytraLayerMixin` no longer cancels the layer and
`LivingEntityRendererMixin` wraps that one layer in push/finally-pop plus the shared
`PenguinRenderContext.mountElytraOnBack` helper. Detached death copies are excluded even when
their copied state is fall-flying, so the existing death rendering still wins. Non-replaced and
no-effect entities never enter this path and keep vanilla rendering.

The mount reuses the prepared penguin body frame and appends, in this order:

```text
translate(0, profile.bodyOffsetBlocks, 0)
* scale(profile.bodyScale)
* penguin.body().translateAndRotate
* translate(0, 0, ELYTRA_PIVOT_BACK_PIXELS / 16)
```

The last step is in penguin model pixels after the body part, so it follows the adapted body pose
rather than a copied posture offset. `ELYTRA_PIVOT_BACK_PIXELS` is 2.0: the rounded upper back is
about two penguin pixels behind the body origin. Vanilla `ElytraLayer` still adds its own
`+0.125` blocks inside this scaled frame, which is two further penguin pixels at the humanoid
`bodyScale` of .5; the constant therefore accounts for that native offset instead of duplicating
it. Total pivot depth is about four penguin pixels, and the 20-pixel native Elytra becomes 10
native pixels tall, matching the 12-native-pixel penguin torso.

The layer itself keeps all native behaviour: equipment eligibility through `shouldRender`, skin
cape/custom-Elytra texture selection, glint, its internal push/pop and `ElytraModel` wing-spread
animation. The mod only supplies the outer mount transform; it never draws a second Elytra.

## Clip priority

1. Death copy rendering (`PenguinDeathClient.rendering`) wins; it keeps the dedicated death clip.
2. While a non-standing posture (including gliding) is active, ordinary taunt/kick visual sampling
   is suppressed and the adapted native player pose is drawn.
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

Gliding/Elytra passed upstream in-game runtime review. Native `startFallFlying` plus the
`START_FALL_FLYING` packet and native flight physics drove launch, glide, descent, turning and
landing; teleport was used only to place the fixture at altitude and for the landing check, not to
move during flight. Thirty render capture combinations with the penguin effect and thirty without
covered WIDE/SLIM and all three mesh tiers. During penguin flight the native Elytra layer still
emitted 48 vertices; after landing it was hidden by the existing policy, while the no-effect
baseline kept its Elytra at 48 vertices. The measured Elytra edge-length ratio was
0.49999998066907825, i.e. the intended .5 back-mount scale; the original head stayed rigid at
scale 1, and both native hand items remained mounted. Crouch/swim/stand render regression passed
for all three qualities on the actual player skin; the separate crouch head/neck/ground audit
covered both WIDE/SLIM with error below 0.0001 blocks. Native `PlayerRenderer.setupRotations` remained the sole owner of flight rotation,
and the native Elytra layer retained its equipment, texture/cape override, glint and wing-animation
ownership.

滑翔/Elytra 已通过上游实机运行审核。使用原生 `startFallFlying`、`START_FALL_FLYING` 数据包和
原版飞行物理完成起跳、滑翔、下降、转向与落地；传送仅用于布置测试高度和落地检查，不用于飞行
中的位移。带企鹅效果与无效果的渲染采集各 30 组，覆盖 WIDE/SLIM 和三档网格。企鹅滑翔期间
原生 Elytra 层仍输出 48 个顶点；落地后按既有策略隐藏，无效果基线则保持 48 个顶点。实测
Elytra 边长比例为 0.49999998066907825，即预期的 .5 背挂缩放；原头保持 scale 1 刚性，双手
原生物品均保持挂载。潜行/游泳/站立的渲染回归在当前玩家皮肤的三档质量上通过；独立潜行头部、颈部与
落地审计覆盖 WIDE/SLIM，误差低于 0.0001 格。原版 `PlayerRenderer.setupRotations` 仍是飞行旋转的唯一所有者，
原生 Elytra 层继续拥有装备资格、纹理/披风覆盖、glint 与翅膀动画。

Dedicated servers and special cape/Elytra texture, glint or shader combinations were not
separately tested; third-party renderer/animation rewrites also remain outside this review.

专用服务器、特殊披风/鞘翅纹理、glint 或光影组合未单独验证；第三方渲染器/动画重写同样不在
本次审核范围内。

## Crouch ground fit / 潜行落地修正

The half-height belly cannot keep the full humanoid crouch drop without intersecting terrain.
Once per draw, the helper measures the current mesh torso and each foot, includes the native
player crouch render offset (-2/16 blocks) and .9375 player renderer scale, and derives a ground
correction. Torso, native head and both wing/item mounts share the torso correction. Each short
foot keeps its own ground correction so a low belly does not lift both feet into the air.
Standing, seated, swimming, gliding and death poses do not receive this crouch-only correction.

潜行时按实际网格边界计算腹部和双脚的落地位置，并计入原版潜行渲染偏移。
身体、原头和翼端持物同步修正；短脚分别贴地，避免腹部过低时把双脚整体抬悬空。
不改变模型资源、玩家碰撞或镜头，也不把此修正用于站立、坐姿、游泳、滑翔和死亡动作。
