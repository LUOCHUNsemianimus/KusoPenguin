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

### Gecko fallback: historical Bedrock-only diagnosis / Gecko 回退：历史性的仅 Bedrock 诊断

`EntityMaidRenderer.render` checks `mainInfo.isGeckoModel()` and hands the frame to `GeckoEntityMaidRenderer` (a `GeoReplacedEntityRenderer`), returning before `super.render`; YSM likewise returns earlier. At the time of this Bedrock-only diagnosis those paths had no `BedrockModel`/`BedrockPart`, so the Bedrock adapter could not read them and they had to fall back to the complete original maid (no penguin body, no head-only redirect), never to a mixed penguin body/foreign head.

This historical fallback is superseded by the independent optional Gecko and YSM bridges in section 4. The Bedrock fallback rule below still applies only to unsupported Bedrock models, for example when `hasHead()` is false or a required bone is missing. / 该历史回退已由第 4 节独立的 Gecko 与 YSM 可选桥接取代；下方 Bedrock 回退规则仅适用于不受支持的 Bedrock 模型。

## 3. Tombstone / ItemFilm diagnosis and held-film transaction / 墓碑与胶片诊断及手持胶片事务

### Source-confirmed native behavior / 源码确认的原生行为

- `EntityMaid.dropEquipment()` creates an `EntityTombstone` holding the maid inventory plus the film produced by `ItemFilm.maidToFilm`. The grave itself is a storage container.
- `EntityTombstone.interact` is owner-gated (or NTR-tool gated): on a normal owner right-click it pre-checks inventory space, extracts every slot with `extractItem(i, size, false)`, gives the stacks to the player and only then discards the tombstone. **Retrieval itself never consumes or deletes the film.** The film is a normal returned item.
- `ItemFilm.filmToMaid` is the native altar/use-on-block path and still belongs to the maid mod. It calls `addFreshEntity` without checking the result and then unconditionally `shrink(1)`. That is a loss path only if it is called; this mod never calls it and never touches the native film/altar behavior.
- `AbstractStoreMaidItem.spawnFromStore` checks the film's `Owner` before loading and spawning. That owner rule is the one the held-film transaction reuses.

### Implemented held-film transaction / 已实现手持胶片事务

1. The player retrieves the film by right-clicking the tombstone natively. The grave is not a revival target and the old K grave-target path is removed.
2. The player holds a valid owned film in the main hand (deterministic first choice) or offhand and holds K for 5 seconds with the screen closed and the game focused.
3. On START the server re-reads the live slot, validates `InitItems.FILM`, count 1, `MAID_INFO.id == touhou_little_maid:maid` and `Owner == caster`, then stores a private `ItemStack`/tag snapshot. START carries the exact `InteractionHand` plus a random hold token, with the START/PULSE/STOP action; it contains no item NBT or item data. PULSE/STOP use the same wire format (token, hand, action); the server matches their active token and uses the hand already bound by START.
4. Every tick, and on every 5-tick PULSE, the server compares the live hand, selected main slot, item, count and components with the snapshot. Movement >0.75 blocks, damage, effect loss, dimension/hand/slot/data/count change, GUI/unfocus/release/disconnect or a missing pulse cancels. The 15-tick lease is shorter than the ritual, so a lost STOP cannot finish a revival.
5. On completion the server finds a safe collision-free position near the caster (shared manager helper), creates the candidate, posts the native `MaidAndItemTransformEvent.ToMaid`, owner-checks the result, restores the full native data through `Entity.load` on a private normalized tag with a removed UUID (fresh runtime UUID), checks `addFreshEntity`, then re-checks the exact live stack once more before consuming exactly one film and adding the Penguin effect. A failed spawn or commit discards the candidate and leaves the held film unchanged.

The old tombstone-slot transaction is no longer used; no tombstone slot, unrelated inventory item or the native film ritual/altar is modified. / 旧的墓碑槽事务不再使用；不修改墓碑槽、无关物品或原生胶片/祭坛流程。


## 4. Optional maid body/head compatibility / 可选女仆身体与头部兼容

### Build and loading / 构建与加载

- `build.gradle` adds `compileOnly "maven.modrinth:R0bDWFAW:1.5.3-neoforge+mc1.21.1"` through a dedicated `KusoMaidResolution` exclusive-content repository. The JLayer repository and startup fix are unchanged.
- The maid artifact is never jarJar'd and is not a hard dependency in `neoforge.mods.toml`.
- Dev runtime default: `devCompat=true` (the default). `runClient` first builds the current jar, then launches the real NeoForge 1.21.1 profile in the isolated `run/compat-client/` directory through `tools/launch_compat_client.py`; only the current mod, TLM 1.5.3 and the pinned local YSM 2.6.5 are copied into `run/compat-client/mods`. This is a packaged-jar launch, not source hot swap or the IDE Java run configuration. Nothing is jarJar'd and nothing is added to `neoforge.mods.toml`; production packaging is unchanged. / 开发默认 `devCompat=true`：`runClient` 先构建当前 jar，再由 `tools/launch_compat_client.py` 在隔离 `run/compat-client/` 中启动真实 NeoForge 1.21.1 配置，只复制当前模组、TLM 1.5.3 与固定本地 YSM 2.6.5 到 `run/compat-client/mods`。这是打包 jar 启动，不是源码热替换或 IDE Java 运行配置；不 jarJar、不写硬依赖。
- `-PdevCompat=false` keeps the native ModDevGradle source/debug launch in the separate `run/` directory, so compat copies never enter its `run/mods` classpath. YSM is then absent, so use this for no-YSM/source debugging; add `-PwithMaid=true` to test the Bedrock/Gecko paths with TLM. / `-PdevCompat=false` 保留原生 ModDevGradle 源码/调试启动并继续使用独立的 `run/`，兼容副本不会进入其 `run/mods` 类路径；此时无 YSM，适合无 YSM 或源码调试；加 `-PwithMaid=true` 可测 Bedrock/Gecko 女仆路径。
- YSM local jar: `ysmDevJar`, default `dev-libs/ysm-2.6.5-neoforge+mc1.21.1-release.jar`. `dev-libs/` is gitignored and no machine or user-profile path is hardcoded in the tracked repo; the ignored local config supplies the installation paths. The launcher validates the config, profile, libraries, Java, and all three pinned jars at task execution; missing files fail `runCompatClient` clearly, while `classes`/`build` and IDE import still work. / YSM 路径由 `ysmDevJar` 指定，默认 `dev-libs/...`，目录已忽略，仓库跟踪文件不硬编码机器或用户目录路径；安装路径由本地忽略配置提供。启动器在任务执行时校验配置、profile、libraries、Java 和三个固定 jar，缺失时明确失败，但不影响 `classes`/`build` 与 IDE 导入。
- Ignored local config `dev-libs/compat-launch.json` (upstream supplies it; overrides: `compatConfig`, `compatGameDir`, `compatPython`). Both the config and local deps are already installed by upstream, are untracked/not bundled, and are not added to the repo. Paths in it are machine-local; the tracked repo only contains placeholders:
  ```json
  {
    "profileJson": "<version>/1.21.1-NeoForge.json",
    "librariesDir": "<minecraft>/libraries",
    "assetsDir": "<minecraft>/assets",
    "javaExe": "<jdk>/bin/java.exe"
  }
  ```
  The launcher reads no account/auth files, performs no network access, and never edits the user's profile, original mods or saves; it extracts only native DLL basenames into `run/compat-client/natives` and waits in the foreground. / 本地配置被忽略且由上游提供；仓库只保留占位符示例。启动器不读账号/认证、不联网、不改用户 profile/原 mod/存档，只把原生 DLL 解压到 `run/compat-client/natives` 并前台等待。
- The global dev `runtimeOnly` classpath also applies to `runServer`; use `-PdevCompat=false` for a clean server smoke. Only the client launch is isolated; no server launcher is added. / 该开发运行类路径同样影响 `runServer`，服务端冒烟请用 `-PdevCompat=false`；仅客户端启动被隔离，未新增服务端启动器。
- The exact optional registry id is `touhou_little_maid:maid`, confirmed from `InitEntities.MAID` (`ENTITY_TYPES.register("maid", ...)`) and `TouhouLittleMaid.MOD_ID`. Common eligibility checks that id through `BuiltInRegistries`; they never load a maid class.
- Maid API references live only in lazy optional bridges: `client/compat/MaidCompatClient`, `MaidGeoCompatClient`, `YsmCompatClient` and `compat/MaidRevivalCompat`. Every outer facade call first checks `ModList.get().isLoaded("touhou_little_maid")`.
- The `@Pseudo` Gecko/YSM/held-item mixins are optional client paths: each resolves its target only when the corresponding mod is present, and an absent target is skipped rather than a hard dependency.

### Supported standard-bone contract / 支持的标准骨骼契约

A default/Bedrock maid is replaced only when the active `BedrockModel` has a visible `head` and the bones `body`, `armLeft`, `armRight`, `legLeft`, `legRight`. The head keeps its native geometry and subtree. `armLeft -> rightWing`, `armRight -> leftWing`, `legLeft -> leftFoot`, `legRight -> rightFoot`, matching the existing humanoid historical wing-side mapping; `body -> body`.

- The maid profile keeps the calibrated 8-pixel head, 12-pixel shared body and 24-pixel physical floor, and derives the native rest neck from the stable bind pivot plus initial ancestor rotations. It never measures animated bounds or uses animated rotations/offsets, so the body cannot resize or jitter while the head looks around. For `hakurei_reimu`, the root head JSON pivot Y 18 converts to Java Bedrock Y 6; the resulting head drop places the head pivot at the body top (Y 12), so the true native neck gap is closed without moving legitimate long hair or ribbon head children further.
- The adapter copies native `xRot/yRot/zRot`, `offsetX/Y/Z * 16`, scales and visibility into the shared penguin parts. The native walk arm/leg animation is therefore the animation source, not a second walk simulation.
- Head matrices walk the Bedrock parent chain with `translateAndRotateAndScale`, then apply the head locally. Head rendering uses the actual `BedrockPart.render(..., float r, float g, float b, float a)` signature and also renders sibling `hat`/`hair`/`headwear`/`ahoge`/`ribbon`/`bow`/`horn` roots. Head children, including a child `hat`, are already covered by the head subtree.
- Death head bounds use a `VertexConsumer` collector over the real rendered head/decor vertices, never an arbitrary zero or animated per-frame remeasurement. Dance head placement and death `maxY` share the same `PenguinHeadRenderer.headMatrix`/`maxY` contracts.

### Fallback and layer behavior / 回退与渲染层

- Gecko and the pinned YSM 2.6.5 path are implemented by their own optional bridges (see the YSM and Gecko subsections below). A Bedrock maid with a missing head/bone, invisible head, or non-Bedrock model keeps the complete native model. The body layer and head-only redirect use the same model-aware gate, so the native body is never removed when the replacement cannot render.
- Native render scale (`mainInfo.getRenderEntityScale()`) remains on the entity renderer root and applies to both the shared penguin body and the native head.
- For a replaced Bedrock maid, the native backpack, back item and banner layers are suppressed because they are authored for the native body. The native head layer is re-anchored with the same `applyHead` + head-drop + ancestor chain as the main head, preserving headgear position.
- The native held-item layer is kept, but an optional `@Pseudo` `MaidHeldItemLayerMixin` replaces only its inner `renderArmWithItem` draw. It uses `PenguinRenderContext.applyHand` and the same `PenguinHeldItemRenderer` wing-tip matrix as every other replacement, so both hands follow taunt/dance and the item keeps its native size. TLM's main/off-hand loop, display context, left-hand flag and CarryOn checks are unchanged. The mixin reproduces vanilla's trailing `XP(-90)`, `YP(180)`, `translate(±1/16, .125, -.625)` sequence expected by the shared helper, then calls TLM's native `GunClientUtil.addItemTranslate` and `ItemInHandRenderer.renderItem`. The final matrix is `N*C*hand*vanillaTail` with no trailing `C^-1`: canonical item vertices are the render input, so an extra inverse conjugation would mirror and offset them. A missing or unsupported model leaves the original draw untouched.
- The maid type is eligible for taunt/dance through the shared pose; only the supported Bedrock path replaces the detached death ghost. Geo/YSM death draws keep the complete native maid fallback (see section 4). `PenguinKick.tryStart` explicitly refuses maids, so adding the type cannot start hostile auto-kick AI against the owner or another target; the existing enemy-only mob path is unchanged.

### YSM 2.6.5 native head + shared penguin body / YSM 2.6.5 原生头+企鹅身体

The optional YSM bridge is pinned to the actual user jar `ysm-2.6.5-neoforge+mc1.21.1-release.jar`. `MaidYsmEntryMixin` wraps TLM's `geoRender` delegation in one nestable per-draw frame; `MaidYsmNativeMeshMixin` calls the pinned native mesh utility twice with an identity pose (full mesh, then `AllHead` hidden) and subtracts the exact body quad multiset, so the retained head/hair geometry is the true native difference — no guessed hair bone names, no neck clipping and no cached indices across frames. Both native parameter arrays and the two `AllHead` visibility flags are restored in `finally`. The surviving head quads are replayed with `NativePose * C * Delta * Tdrop * C^-1` and a proper normal matrix; the native vertices are already CPU-skinned, so no bone chain is applied twice.

The shared penguin body is drawn once, after `geoRender` returns, into the saved native modelview; this avoids switching the `MultiBufferSource` while the native body buffer is still open. `ready` stays false until the quad proof succeeds, so on any capture/version failure the complete native body, head, hair, items and layers stay unchanged. Backpack/back-item/banner layers are suppressed only while ready; the native head-gear layer keeps the same conjugated head transform; the existing held-item hook now accepts any public `GeoMaidView`. No YSM jar is bundled, all YSM symbols are reflective in a lazy nested implementation, and the Gecko/Bedrock/standalone paths are unchanged.

可选 YSM 桥接只针对实际用户 jar 2.6.5：原生网格用同一工具函数抓取“完整模型”和“隐藏 AllHead 后”的顶点，按完整四边形多重集相减得到真实头/发几何；原生数组与可见性标志在 finally 中恢复。企鹅身体延迟到原生 geoRender 返回后再绘制，避免中途切换缓冲区；抓取证明失败时完整保留原生外观。不打包 YSM、不新增硬依赖，Gecko/Bedrock/独立运行路径不变。

The default YSM model pack was tested; custom YSM model packs were not all tested and are expected to follow the same `AllHead`/hair contract. / 已测试默认 YSM 模型包；并未测试全部自定义模型包，它们应遵循相同的 `AllHead`/头发契约。

### Gecko current path / Gecko 当前路径

- The Gecko bridge replaces only when its `GeoMaidView` is ready. It draws the shared penguin body in the canonical frame, then the native `AllHead` hair/ear subtree (or `Head` when `AllHead` is not its ancestor) and the separate `Hat` sibling with the native ancestor chain. Head placement uses the shared `Delta * Tdrop` conjugation and the Y-up -> Y-down basis. / Gecko 桥接仅在 `GeoMaidView` ready 时替换：先绘制共享企鹅身体，再按原生祖先链绘制 `AllHead` 头/发子树（或 `Head`）及独立 `Hat`；头部使用共享共轭与 Y-up→Y-down 基。
- The held item uses the shared `PenguinRenderContext.applyHand` wing-tip matrix; the native held-item layer loop, display context and CarryOn checks stay native. The drawn matrix is `N*C*hand*vanillaTail` (+`GunClientUtil`) with no trailing inverse C. / 手持物沿用共享 `applyHand` 翼尖矩阵，原生手持层循环与判定保持不变；最终矩阵为 `N*C*hand*vanillaTail`（含 `GunClientUtil`），不再附加末尾逆 C。
- Detached death ghost: Geo/YSM currently keep the complete native maid fallback because supported native head bounds are missing; a replaced death ghost is not claimed for these paths. Normal taunt/dance are supported. / 分离式死亡幽影：Geo/YSM 目前因缺少受支持的原生头部包围盒而完整回退原生女仆，不声称已替换其死亡幽影；常规嘲讽/跳舞支持。

### Gecko/YSM mapping and held-item correction / Gecko/YSM 映射与手持物修正

- Coordinate-based mapping: `GeoBuilder` changes JSON `pivot.x` to `-pivot.x`, and C changes native X to canonical `-x` again. The winefox source has `RightArm` JSON `pivotX=-3.25` / Z rotation `+18°` and `LeftArm` `+3.25` / `-18°`; therefore `RightArm` lands on canonical `leftWing` (`x=-5.45`, anatomical right) and `LeftArm` on `rightWing` (`+5.45`, anatomical left). The bridge now maps `RightArm -> leftWing`, `LeftArm -> rightWing`, `RightLeg -> leftFoot`, `LeftLeg -> rightFoot`, and keeps the absolute rotation conjugation `(-X,-Y,+Z)`, not a bind-relative subtraction. The Bedrock adapter's `armLeft`/`armRight` mapping is unchanged. / 按坐标而非骨骼名映射：`GeoBuilder` 取反 JSON pivot X，C 再取反一次，故 winefox 的 `RightArm` 落到规范 `leftWing`（解剖学右翼），`LeftArm` 落到 `rightWing`；腿同理。旋转仍按绝对共轭 `(-X,-Y,+Z)` 复制，不减 bind。Bedrock 适配器映射不变。
- YSM proven by native runtime: default YSM 2.6.5 reports native `RightArm` pivotX `+3.4`, `LeftArm` `-3.4`, `RightLeg` `+2.4`, `LeftLeg` `-2.4`; C flips native X, so `YsmCompatClient` now uses the same `RightArm -> leftWing`, `LeftArm -> rightWing`, `RightLeg -> leftFoot`, `LeftLeg -> rightFoot` mapping. Absolute rotations, native capture, head and other rig parts are unchanged. / YSM 原生实测：默认模型 `RightArm` pivotX 为 `+3.4`、`LeftArm` 为 `-3.4`、`RightLeg` 为 `+2.4`、`LeftLeg` 为 `-2.4`；C 翻转原生 X，故采用同一坐标映射。绝对旋转、原生捕获、头部及其他 rig 不变。
- Held item: the draw is `N*C*applyHand*vanillaTail` (+`GunClientUtil`). The old trailing `translate(0,1.5,0)` / `scale(-1,-1,1)` was removed because item geometry is already canonical vertices, not an operator needing conjugation. The initial C, both hands, native display context, CarryOn and Gun behavior are unchanged; the shared hook also covers YSM. / 手持物最终为 `N*C*applyHand*vanillaTail`（含 `GunClientUtil`）；删除末尾 `translate(0,1.5,0)`/`scale(-1,-1,1)`，因为物品几何本身就是规范顶点，不需要再共轭。初始 C、双手、显示上下文、CarryOn/Gun 语义不变；共享钩子同样覆盖 YSM。
- These are development-side corrections. Loading YSM on the classpath alone did not fix `nativeerr54`; the dev JavaExec still fails, which is why the default `runClient` uses the production-compatible packaged launch in `run/compat-client/`. Actual upstream `runClient` verification is still pending. / 以上仅为开发侧修正；仅把 YSM 加入类路径并未修复 `nativeerr54`，开发 JavaExec 仍失败，因此默认 `runClient` 改用 `run/compat-client/` 中的生产兼容打包启动；上游实际 `runClient` 验证仍待完成。

### Verified environment / 已验证环境

Verified on isolated production NeoForge 21.1.262 with portable Zulu 21.0.12, YSM 2.6.5 and TLM 1.5.3. The native dev Forge client on NeoForge 21.1.243 still hits `nativeerr54` with the same JRE and both mods present; adding the jars to the dev classpath did not fix it. The default `devCompat=true` launch therefore reproduces the production-compatible packaged-jar setup in `run/compat-client/`; it does not patch, replace or bypass YSM validation, and it does not change the project `neo_version`. Ordinary `runClient` has been verified to enter the validation world with both mods and a working YSM native model. / 已在隔离生产环境 NeoForge 21.1.262、便携 Zulu 21.0.12、YSM 2.6.5 与 TLM 1.5.3 验证；原生开发 Forge 客户端在 NeoForge 21.1.243、同一 JRE 且两个模组都在时仍出现 `nativeerr54`，仅把 jar 加入开发类路径并未修复。默认 `devCompat=true` 因此在 `run/compat-client/` 复现生产兼容的打包 jar 启动；不修改/替换/绕过 YSM 校验，也不改项目 `neo_version`。普通 `runClient` 已验证可进入测试世界，并正常加载两个模组与 YSM 原生模型。

Held-film revival protocol: see section 3. / 手持胶片复活流程见第 3 节。

## 5. Pole-dance revival / 钢管舞复活

- Default rebindable key: `K` (`key.kuso_penguin.revival`). The player path keeps one press = one 160-server-tick channel; pressing K again requests cancellation. The maid film path requires K to stay held for 100 server ticks (5 s).
- Only a living player with the Penguin effect can start either path, never on themselves. The server validates same dimension, 4-block range, line of sight, look direction, hardcore exclusion and a free target reservation for the player path.
- Player request carries an exact death token: the corpse marker's own entity UUID. The server resolves that precise marker plus its owner/current record and rejects removed, manual-respawned or otherwise stale tokens; there is no owner-UUID fallback across deaths. Reservations are keyed by the same death token.
- The maid grave is not targeted by K. Its native owner right-click returns the film; the player then holds a valid owned film (main hand first, then offhand) and holds K. START opens a separate film channel, carrying the exact `InteractionHand` plus a random hold token, PULSE every 5 ticks refreshes a 15-tick lease, and STOP or a missing pulse cancels. A PULSE cannot start an unknown channel; a stale token cannot refresh or cancel a newer ritual, and the hold packet never touches the corpse channel. START is sent once per physical key press, so a failure/completion is not retried until release.
- Film cancellation triggers: release, GUI, unfocused window, disconnect, dimension change, hand/selected-slot change, item/count/component change, movement, damage, effect loss, invalid stack/owner and lease expiry. The client mirror uses the State packet's `maidTarget` flag to expire film animation at 100 ticks and player animation at 160 ticks.
- Player channel checks server-side: duration, target validity, range, movement (`>0.75` blocks from start), damage, death, effect loss, swimming/gliding/riding/kick, dimension change, target respawn/logout or leaving 4 blocks. A marker that vanishes mid-channel cancels it.
- Caster rendering uses the shared pole style (`PenguinDancePose.evaluatePole`) with one continuous motion clock at 30 FPS/1.5x; both paths reuse it. It does not switch dance index and does not require a jukebox.
- Client state is bounded per body: film entries expire after 5 s and player entries after 8 s plus one second of transit grace, reject a wrong entity id/dimension/alive/effect caster, clear on disconnect/clone/dimension change, and a delayed stop from an older session cannot overwrite a newer start.
- Dead players receive one transient, non-saving, non-colliding `RevivalCorpse` marker. Its client renderer draws a render-only `RemotePlayer` copy from the online GameProfile; no inventory, loot, AI or ticks are copied. The original dead entity is hidden while the marker exists, with a 42-tick delay for a previously penguin player so the existing detached death animation is not duplicated; the corpse visual itself is never hidden by the same-UUID owner filter.
- Corpse records are pruned when the owner is offline, no longer the current native player instance, alive, or in another dimension, and a marker that vanished with an unloaded chunk is recreated with its original token/time once that already-loaded chunk is back (never force-loading a chunk). Manual respawn, logout, a cancelled death and a successful revival remove the corpse and channel immediately.
- Player completion uses `PlayerList.respawn(deadTarget, false, RemovalReason.KILLED)` and overrides only the pending `findRespawnPositionAndUseSpawnBlock` transition with a loaded, world-border-safe, collision-free stand position at the saved corpse position/yaw before native anchor charges are consumed. Keep-inventory stays false, native death/XP rules apply, bed/home respawn data is untouched, and the caster's own instance/inventory/health/home is never modified.
- Maid completion reuses the held-film transaction in section 3: private snapshot, native event/full data restoration/fresh UUID, safe position near the caster, final live-stack recheck, exactly one film consumed only on success, then the Penguin effect so the shared TAUNT runs. The film-intentionally-removed inventory/armor/hands/experience/Health/DeathTime/HurtTime/ActiveEffects are never invented or restored.
- Standalone and dedicated servers load without the maid mod; maid API references remain isolated in the lazy optional bridges described in section 4. Server state, corpses, reservations, film channels and pending respawn transitions are cleared on server stop.
