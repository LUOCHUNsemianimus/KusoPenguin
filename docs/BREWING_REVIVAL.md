# Brewing chain and revival notes

## 1. Implemented brewing chain / 已实现酿造链

Recipes, in exact order / 按顺序的精确配方：

| # | Input potion / 输入药水 | Ingredient / 材料 | Output / 输出 | Source / 来源 |
|---|---|---|---|---|
| 1 | Water Bottle / 水瓶 | Nether Wart / 下界疣 | Awkward Potion / 粗制的药水 | vanilla / 原版 |
| 2 | Awkward Potion / 粗制的药水 | Fermented Spider Eye / 发酵蛛眼 | Stinky Base / 臭味基底（无效果） | this mod / 本模组 |
| 3 | Stinky Base / 臭味基底 | Feather / 羽毛 | Potion of Penguin / 企鹅药水（3:00） | this mod / 本模组 |
| 4 | Potion of Penguin / 企鹅药水 | Redstone / 红石 | Long Potion of Penguin / 长效企鹅药水（8:00） | this mod / 本模组 |

中文完整顺序：水瓶 + 下界疣 = 粗制的药水；粗制的药水 + 发酵蛛眼 = 臭味基底（无效果）；臭味基底 + 羽毛 = 企鹅药水（3 分钟）；企鹅药水 + 红石 = 长效企鹅药水（8 分钟）。

English full order: water bottle + nether wart = awkward potion; awkward potion + fermented spider eye = Stinky Base (effect-free); Stinky Base + feather = potion of penguin (3 minutes); potion of penguin + redstone = long potion of penguin (8 minutes).

Vanilla's own water + fermented spider eye -> weakness and every other vanilla mix remain untouched. The old awkward -> weakness and weakness -> penguin shortcuts are removed; a weakness potion is never a shortcut to the penguin effect.

Code / 代码：
- `src/main/java/com/luochun/kusopenguin/registry/PenguinBrewing.java` — main-bus `RegisterBrewingRecipesEvent`, three `addMix` calls above.
- `src/main/java/com/luochun/kusopenguin/registry/ModPotions.java` — `penguin_base` (no effects), `penguin` (3600 ticks) and `long_penguin` (9600 ticks).
- Lang keys / 语言键：`item.minecraft.potion.effect.penguin_base`, `item.minecraft.splash_potion.effect.penguin_base`, `item.minecraft.lingering_potion.effect.penguin_base`, `item.minecraft.tipped_arrow.effect.penguin_base`, plus the same four forms for `penguin` and `long_penguin`, in both `zh_cn.json` and `en_us.json`.

## 2. Maid renderer diagnosis / 女仆渲染器诊断（源码参考；实现见第 4 节）

Reference: Touhou Little Maid 1.5.3 source under `maid-reference`. Source read: `EntityMaid.java` (near 1530), `EntityTombstone.java`, `ItemFilm.java`, `BedrockModel.java`, `BedrockPart.java`, `AbstractBedrockEntityModel.java`, `EntityMaidRenderer.java`, `GeckoEntityMaidRenderer.java`, plus `LayerMaidBipedHead.java`, `MaidBaseAnimation.java`, `ModelRendererWrapper.java`.

### Bedrock model and head selection / 基岩模型与头部选择

- `EntityMaidRenderer` is `MobRenderer<Mob, BedrockModel<Mob>>`; its constructor installs `new BedrockModel<>()`. The draw path first resolves `MaidModels.ModelData` (and can be replaced by `RenderMaidEvent`), then `CustomPackLoader.MAID_MODELS.getModel(maid.getModelId())` selects the active Bedrock model.
- A model exposes its head through `BedrockModel.hasHead()` = `modelMap.containsKey("head")` and `getHead()` = `modelMap.get("head")`. The bone contract used by TLM's own animations is `head`, `hat`, `armLeft`, `armRight`, `legLeft`, `legRight`, `body` (see `MaidBaseAnimation` / `PlayerMaidAnimation`).
- `LayerMaidBipedHead` gates equipment/decor head rendering with `mainInfo.isShowCustomHead() && getParentModel().hasHead()`, then calls `getHead().translateAndRotate(poseStack)`. The head bone's own cubes are part of the normal model render; the layer only adds helmet/backpack-show-item.
- `BedrockPart` is not a vanilla `ModelPart`: pivots are `x/y/z` in model pixels and divided by 16 in `translateAndRotate`; animation rotations are `xRot/yRot/zRot` (ZYX order), animation translation is `offsetX/Y/Z` in blocks, and `render` applies `translateAndRotateAndScale` then children. `ModelRendererWrapper` writes only rotations, offsets and visibility, so `x/y/z` remain stable pivots.
- Head pose after TLM's inner animations: `MaidBaseAnimation.getHeadDefault()` sets `head.xRot = headPitch * DEG`, `head.yRot = netHeadYaw * DEG`, with a 15° downward override while sleeping; `hat` is hidden while sleeping.
- Normal walk pose: `MaidBaseAnimation.getLegDefault()` sets `legLeft.xRot = cos(limbSwing*0.67) * 0.3 * limbSwingAmount`, `legRight.xRot` to the opposite sign, and restores each leg's initial Y/Z rotations. Arm swing follows the same phase with `0.7` amplitude in `getArmDefault()`. `BedrockModel.setupAnim` first runs its configured inner/JS animations, then `HardcodedAnimationManger.playMaidAnimation`; this is the "normal walk" source an adapter must read, not a second walk simulation.

### Adapter needed by this repo / 本仓库需要的适配器

At the time of this diagnosis our replacement was generic: `LivingEntityRendererMixin` redirected `EntityModel.renderToBuffer` to `PenguinHeadRenderer.renderHead`, and `PenguinBodyLayer` drew the five-part body after `PenguinPoseAdapters.find(parentModel)` and `PenguinProportions.forModel(parentModel)`. Neither knew `BedrockModel`/`BedrockPart`, so a maid then kept the complete Bedrock model (safe fallback) instead of becoming a penguin. The implemented adapter is in section 4.

Source-confirmed minimal adapter contract (now implemented in section 4):
1. A renderer gate that runs before the head-only redirect: replace only when the parent is `EntityMaidRenderer` with a `BedrockModel`, `mainInfo.isGeckoModel()` is false, YSM is not active, and `hasHead()` is true. Unlike the current entity-only `PenguinRenderSupport.shouldReplace`, this gate needs the model/renderer instance.
2. `BedrockMaidPoseAdapter.apply`: read `BedrockModel`'s public `getModelMap()` (`BedrockModelProvider` exposes it) and copy each selected bone's `xRot/yRot/zRot` to the corresponding penguin `ModelPart`; copy animated translation as `offsetX/Y/Z * 16` into the part pivots. Explicit map: `body` -> torso, `armLeft` -> `rightWing`, `armRight` -> `leftWing`, `legLeft` -> `rightFoot`, `legRight` -> `leftFoot`, mirroring the existing humanoid adapter's historical wing-side convention. Unknown/missing bones fall back to the complete vanilla maid.
3. Proportion profile: keep the native Bedrock head size; measure the selected `head` bone's cube bounds (or use the model's declared head height) for `Profile.headHeight`, and anchor the short penguin body at the native foot plane (`FOOT_Y` pattern from `PenguinProportions`). Do not reuse the humanoid `chibi(8, 0, 24)` blindly for custom packs with larger heads.
4. Head rendering: extend `PenguinHeadRenderer` with a `BedrockModel` branch. After the same body offset/scale and head anchor, call `getHead().render(poseStack, buffer, light, overlay, color)` so the head and all its children (hat/ears/hair) keep native geometry. `BedrockPart.render` already applies its own pivot/rotation/scale, so do not also copy the head transform.
5. Keep the existing models/animations, 30 FPS 1.5x jelly dance policy, AI/combat/death unchanged. The adapter runs only when `PenguinRenderContext.isDanceFrame` is false; dance frames keep the procedural rig.

### Gecko fallback / Gecko 回退

`EntityMaidRenderer.render` checks `mainInfo.isGeckoModel()` and hands the frame to `GeckoEntityMaidRenderer` (a `GeoReplacedEntityRenderer`), returning before `super.render`; YSM likewise returns earlier. Those paths have no `BedrockModel`/`BedrockPart`, so the Bedrock adapter cannot read them. They must deliberately fall back to the complete original maid (no penguin body, no head-only redirect), not to a mixed penguin body/foreign head. The same fallback applies when `hasHead()` is false or a required bone is missing.

## 3. Tombstone / ItemFilm diagnosis and safe transaction / 墓碑与胶片诊断及安全事务

### Source-confirmed current behavior / 源码确认的现状

- `EntityMaid.dropEquipment()` (around lines 1545-1562) creates `EntityTombstone`, extracts the combined armor/hands/maid inventory/bauble/hide/task wrapper into the tombstone's 256-slot `ItemStackHandler`, adds the backpack take-off stack and backpack data, then calls `ItemFilm.maidToFilm(this)` and inserts the resulting film. **The tombstone therefore holds the inventory PLUS the film.**
- `ItemFilm.maidToFilm` saves the maid, calls `removeMaidSomeData` (removes inventory, baubles, armor, hands, effects, etc.), stores the result in `InitDataComponent.MAID_INFO` `CustomData`, and puts the entity id in the `id` tag.
- `ItemFilm.filmToMaid(ItemStack, Level, BlockPos, Player)` is `void`. It reads `MAID_INFO`, validates `id`, constructs `new EntityMaid(worldIn)`, posts `MaidAndItemTransformEvent.ToMaid`, calls `readAdditionalSaveData(data)`, sets the position, and on the server calls `worldIn.addFreshEntity(maid)` **without checking the result**, sends the spawn particle/sound, then unconditionally calls `film.shrink(1)`. A failed `addFreshEntity` therefore consumes the film and loses the maid. It also mutates the caller's stack directly, so it must not be handed a tombstone slot stack without a transaction.

### Proposed safe transaction for the next card / 下一步建议的安全事务

All steps server-side, after the player interaction and owner/permission checks; no AI/combat/death changes.

1. **Preflight, no mutation**: scan tombstone slots for exactly one film whose `MAID_INFO.id` is the maid id; verify the spawn position/space and permission. If no film or invalid data, do nothing and leave every slot untouched.
2. **Build a candidate**: create `new EntityMaid(level)`, post `MaidAndItemTransformEvent.ToMaid`, copy the film's `CustomData` tag and `readAdditionalSaveData`, set the position. Do not call `filmToMaid` on the stored stack and do not add a second film.
3. **Spawn and check**: call `level.addFreshEntity(maid)` and inspect the boolean result. On `false`, discard the candidate, send a failure message, and leave the film and all other tombstone items untouched.
4. **Commit only after success**: on `true`, play the native post-spawn particle/sound, then extract exactly one item from that film slot (`items.extractItem(slot, 1, false)`). If extraction unexpectedly fails, discard the just-spawned maid to avoid duplicating it.
5. **Retain the rest**: do not discard/clear the tombstone and do not give the tombstone inventory to the new maid. The remaining tombstone items stay available to the player; the film stack only loses the one consumed item. Never call `maidToFilm` on the tombstone, never clone the film, never copy the inventory into the film or the new maid's inventory.
6. **Guard**: consume the film before returning; a second interaction then sees no film. Do not rely on `filmToMaid`'s unconditional `shrink` as the commit point.

This follows the native data/events (`InitDataComponent.MAID_INFO`, `MaidAndItemTransformEvent.ToMaid`, `readAdditionalSaveData`, `addFreshEntity`) while changing only the order that makes consumption conditional on a successful spawn. The existing `MaidTombstoneEvent` cancellation remains honored because a canceled tombstone is never created.

## 4. Optional maid body/head compatibility / 可选女仆身体与头部兼容

### Build and loading / 构建与加载

- `build.gradle` adds `compileOnly "maven.modrinth:R0bDWFAW:1.5.3-neoforge+mc1.21.1"` through a dedicated `KusoMaidResolution` exclusive-content repository. The JLayer repository and startup fix are unchanged.
- The maid artifact is never jarJar'd and is not a hard dependency in `neoforge.mods.toml`.
- `-PwithMaid=true` adds the same artifact to the dev runtime as a `runtimeOnly` mod, so NeoForge discovers it in the Mod List; without that flag, standalone and dedicated-server runs have no maid classes on the runtime classpath.
- The exact optional registry id is `touhou_little_maid:maid`, confirmed from `InitEntities.MAID` (`ENTITY_TYPES.register("maid", ...)`) and `TouhouLittleMaid.MOD_ID`. Common eligibility checks that id through `BuiltInRegistries`; they never load a maid class.
- All maid API references live in `client/compat/MaidCompatClient` and its lazy nested implementation. Every outer facade call first checks `ModList.get().isLoaded("touhou_little_maid")`.

### Supported standard-bone contract / 支持的标准骨骼契约

A default/Bedrock maid is replaced only when the active `BedrockModel` has a visible `head` and the bones `body`, `armLeft`, `armRight`, `legLeft`, `legRight`. The head keeps its native geometry and subtree. `armLeft -> rightWing`, `armRight -> leftWing`, `legLeft -> leftFoot`, `legRight -> rightFoot`, matching the existing humanoid historical wing-side mapping; `body -> body`.

- The maid profile keeps the calibrated 8-pixel head, 12-pixel shared body and 24-pixel physical floor, and derives the native rest neck from the stable bind pivot plus initial ancestor rotations. It never measures animated bounds or uses animated rotations/offsets, so the body cannot resize or jitter while the head looks around. For `hakurei_reimu`, the root head JSON pivot Y 18 converts to Java Bedrock Y 6; the resulting head drop places the head pivot at the body top (Y 12), so the true native neck gap is closed without moving legitimate long hair or ribbon head children further.
- The adapter copies native `xRot/yRot/zRot`, `offsetX/Y/Z * 16`, scales and visibility into the shared penguin parts. The native walk arm/leg animation is therefore the animation source, not a second walk simulation.
- Head matrices walk the Bedrock parent chain with `translateAndRotateAndScale`, then apply the head locally. Head rendering uses the actual `BedrockPart.render(..., float r, float g, float b, float a)` signature and also renders sibling `hat`/`hair`/`headwear`/`ahoge`/`ribbon`/`bow`/`horn` roots. Head children, including a child `hat`, are already covered by the head subtree.
- Death head bounds use a `VertexConsumer` collector over the real rendered head/decor vertices, never an arbitrary zero or animated per-frame remeasurement. Dance head placement and death `maxY` share the same `PenguinHeadRenderer.headMatrix`/`maxY` contracts.

### Fallback and layer behavior / 回退与渲染层

- Gecko and YSM maids return through their own renderers; they keep the complete original appearance. A Bedrock maid with a missing head/bone, invisible head, or non-Bedrock model also keeps the complete native model. The body layer and head-only redirect use the same model-aware gate, so the native body is never removed when the replacement cannot render.
- Native render scale (`mainInfo.getRenderEntityScale()`) remains on the entity renderer root and applies to both the shared penguin body and the native head.
- For a replaced Bedrock maid, the native backpack, back item and banner layers are suppressed because they are authored for the native body. The native head layer is re-anchored with the same `applyHead` + head-drop + ancestor chain as the main head, preserving headgear position.
- The native held-item layer is kept, but an optional `@Pseudo` `MaidHeldItemLayerMixin` replaces only its inner `renderArmWithItem` draw. It uses `PenguinRenderContext.applyHand` and the same `PenguinHeldItemRenderer` wing-tip matrix as every other replacement, so both hands follow taunt/dance and the item keeps its native size. TLM's main/off-hand loop, display context, left-hand flag and CarryOn checks are unchanged. The mixin reproduces vanilla's trailing `XP(-90)`, `YP(180)`, `translate(±1/16, .125, -.625)` sequence expected by the shared helper, then calls TLM's native `GunClientUtil.addItemTranslate` and `ItemInHandRenderer.renderItem`. A missing or unsupported model leaves the original draw untouched.
- The maid type is eligible for taunt/dance/death through the shared pose. `PenguinKick.tryStart` explicitly refuses maids, so adding the type cannot start hostile auto-kick AI against the owner or another target; the existing enemy-only mob path is unchanged.

### Implemented transaction / 已实现事务

The tombstone path now follows the safe transaction above: only the tombstone owner may start it, exactly one valid owned film is required, the native `MAID_INFO` data and `MaidAndItemTransformEvent.ToMaid` are reused, the post-event tag is owner-checked and fully restored through native `Entity.load` on a private normalized copy (saved UUID removed so the candidate keeps a fresh UUID), `addFreshEntity` success is checked, the film slot is rechecked, exactly one film is consumed, and a failed commit discards the candidate. Every other tombstone slot and the tombstone itself remain untouched.

墓碑路径会先校验唯一有效胶片，复用原生 `MAID_INFO` 与 `ToMaid`，事件后再次校验主人，并用私有规范化标签调用原生 `Entity.load` 恢复完整存储数据（移除旧 UUID 以保留新 UUID）；生成失败、槽位复检失败或提交失败都不改变其余 256 个槽位。

## 5. Pole-dance revival / 钢管舞复活

- Default rebindable key: `K` (`key.kuso_penguin.revival`). One press starts one 160-server-tick channel; holding is not required. Pressing K again requests cancellation.
- Only a living player with the Penguin effect can start a channel, never on themselves. The server validates same dimension, 4-block range, line of sight, look direction, hardcore exclusion and a free target reservation.
- The client request carries an exact death token: the corpse marker's own entity UUID for a player, or the exact tombstone UUID for a maid. The server resolves that precise marker plus its owner/current record and rejects removed, manual-respawned or otherwise stale tokens; there is no owner-UUID fallback across deaths. Reservations are keyed by the same death token.
- Duration, target validity, range, movement (`>0.75` blocks from the start), damage, death, effect loss, swimming/gliding/riding/kick, dimension change, target respawn/logout or leaving 4 blocks are checked server-side. A marker that vanishes mid-channel cancels it.
- Caster rendering uses the shared pole style (`PenguinDancePose.evaluatePole`) with one continuous 8-second motion clock at 30 FPS/1.5x. It does not switch dance index and does not require a jukebox.
- Client state is bounded: active entries expire after 8 seconds plus 1 second of transit grace, reject a wrong entity id/dimension/alive/effect caster, clear on disconnect/clone/dimension change, and a delayed stop from an older session cannot overwrite a newer start.
- Dead players receive one transient, non-saving, non-colliding `RevivalCorpse` marker. Its client renderer draws a render-only `RemotePlayer` copy from the online GameProfile; no inventory, loot, AI or ticks are copied. The original dead entity is hidden while the marker exists, with a 42-tick delay for a previously penguin player so the existing detached death animation is not duplicated; the corpse visual itself is never hidden by the same-UUID owner filter.
- Corpse records are pruned when the owner is offline, no longer the current native player instance, alive, or in another dimension, and a marker that vanished with an unloaded chunk is recreated with its original token/time once that already-loaded chunk is back (never force-loading a chunk). Manual respawn, logout, a cancelled death and a successful revival remove the corpse and channel immediately. The lowest-priority uncancelled `LivingDeathEvent` creates the marker; a tick safety pass covers later cancellations.
- Player completion uses `PlayerList.respawn(deadTarget, false, RemovalReason.KILLED)`, assigns the returned player to the target's connection, and overrides only the pending `findRespawnPositionAndUseSpawnBlock` transition with a loaded, world-border-safe, collision-free stand position at the saved corpse position/yaw before native anchor charges are consumed. Keep-inventory stays false, native death/XP rules apply, bed/home respawn data is untouched, and the caster's own instance/inventory/health/home is never modified. The Penguin effect (3 minutes) is added only after the new target player exists.
- Maid completion keeps the native film data/owner/model/name/task, honors the native transformation event, verifies duplicate UUID and safe placement, and consumes exactly one film only after a successful spawn and slot recheck. `CustomName`, `CustomNameVisible`, `NeoForgeData`, native serialized attachments and scoreboard `Tags` survive because the private normalized tag goes through full native `Entity.load`; the saved UUID is removed so the fresh runtime UUID is kept, `Pos` comes from the validated safe position, `Motion` is zero, and `Rotation` defaults only when missing. The film-intentionally-removed inventory/armor/hands/experience/Health/DeathTime/HurtTime/ActiveEffects are never invented or restored, and tombstone inventory is never copied. The Penguin effect is added after commit so the shared TAUNT runs.
- 女仆完成：保留原生胶片数据/主人/模型/名称/任务，事件后复验主人；私有规范化标签经完整原生 `Entity.load` 恢复命名、持久数据、附件与计分板标签，同时移除旧 UUID、使用安全位置、零速度并在缺失时补默认旋转。胶片有意移除的生命/背包/装备/经验/效果等字段不恢复、不臆造。仅在生成与槽位复检成功后消耗 1 张胶片并添加企鹅效果。
- Standalone and dedicated servers load without the maid mod; all maid API references remain isolated in the guarded optional compat implementation. Server state, corpses, reservations and pending respawn transitions are cleared on server stop.
