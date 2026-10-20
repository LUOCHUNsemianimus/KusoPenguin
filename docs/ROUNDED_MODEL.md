# Rounded penguin model

The penguin body is authored in Blockbench and exported with its native Java
codec. The original head, the five animation roots, the 2.5-head proportion
preview and the effect/network code are unchanged by this model round.

## Authoring and export

- Editable source: `models/penguin_body.bbmodel`.
- Native Java export: `models/penguin_body_export.java`.
- Texture: `src/main/resources/assets/kuso_penguin/textures/entity/penguin_body.png`
  on a 512x512 atlas.
- Geometry reference: `models/geometry-spec.json` and the hash/cube/root audit in
  `.workflow/projects/kuso-penguin/work/refined-model/authoring-manifest.json`.

The body is a rounded multi-cube shell (226 cubes total in the native export),
not the earlier five-cube prototype. The five top-level animation roots remain:

| Root | Native export pivot (Java, Y-up) |
| --- | --- |
| `body` | `(0, 0, 0)` |
| `right_wing` | `(5.45, 2.6, 0.2)` |
| `left_wing` | `(-5.45, 2.6, 0.2)` |
| `right_foot` | `(3.3, 15, 0)` |
| `left_foot` | `(-3.3, 15, 0)` |

The full export keeps Blockbench's own X placement and parent chains; only the
foot Y value is worth restating in Blockbench terms: Java Y15 means Blockbench
Y9. Each foot root also carries the leg column
whose cubes span Blockbench Y1..11, so the column continues up into the torso
instead of hanging below the old ankle position. The existing foot-root getters
and pose adapter need no new animation.

The export is kept as-is regarding rotation signs and parent chains. Blockbench
already wrote Java-space Y-up pivots (`24 - BB.y`), so the geometry is not
manually re-rotated or mirrored by the generator.

## Generated helper

`models/penguin_body_export.java` is a complete Blockbench-generated
`EntityModel` class for an older API; it is not compiled directly. The native
export's `createBodyLayer` method is extracted and wrapped in
`PenguinGeometry`. Geometry, pivots, rotations and quoted ModelPart names are
unchanged; the only rewrite is that Java-illegal hyphens in generated local
variable identifiers (`ribbon_tail_-1_0_joint`) become `_neg1_` so the method
compiles. The original string names remain `ribbon_tail_-1_0_joint`.

```text
models/penguin_body_export.java
        |  tools/generate_penguin_geometry.py
        v
src/main/java/com/luochun/kusopenguin/client/PenguinGeometry.java
        |
        v
PenguinBodyModel.createBodyLayer()
```

`PenguinGeometry` contains only the extracted layer method plus the six model
builder imports. `PenguinBodyModel` keeps its public constructor, layer/texture
locations, getters and the five animation roots, and it renders and resets from
the baked root so all child bones are covered.

## Regenerate and verify

From the repository root:

```bat
python tools/generate_penguin_geometry.py
python tools/generate_penguin_geometry.py --check
```

Normal execution rewrites `PenguinGeometry.java`. `--check` never writes: it
exits non-zero if the checked-in file differs from the current native export, so
the mismatch must be reviewed and regenerated intentionally. Both modes print
the SHA-256 of the native export and the `.bbmodel` for the audit trail.

The expected cube count and atlas size are read from the `.bbmodel` metadata
(`elements` with `type: "cube"` and `resolution.width`/`height`), not hardcoded.
The generator cross-checks those values against the native export's `addBox`
count and `LayerDefinition.create` dimensions before writing, and reports
missing or invalid metadata instead of silently accepting it. The current model
snapshot is 226 cubes on a 512x512 atlas, but a later BB edit can change those
numbers as long as the native export is regenerated to match.

## What stays unchanged

- The original head layer still supplies the head, hat layer and head
  decorations.
- The five animation roots and `PenguinPoseAdapter` behavior are unchanged.
- `PenguinProportions` still controls the 2.5-head visual ratio.
- Effect registration, observer sync, supported entity list and first-person
  behavior are out of scope for this model round.

`docs/VALIDATION.md` and the original full-size/chibi evidence in
`docs/CHIBI_PROPORTIONS.md` remain historical records; they do not validate this
multi-cube model round. The live checks for this round are recorded below.

## Live validation (2026-10-19)

This round was checked in a live client, not inferred from compilation:

- Environment: Minecraft 1.21.1 / NeoForge 21.1.243, 1280x720, FOV 70, GUI
  scale 3, no shaders, isolated `KusoPenguin-Validation` world. The world used
  normal difficulty with creative flight and resistance 255, natural spawning
  disabled and NoAI sample mobs; it is not a user save.
- The player and all five supported mobs (skeleton, zombie, villager, piglin,
  zombified piglin) passed: five animated roots, live vanilla `setupAnim` ->
  adapter, wing/foot rotations, alternating walk and reset.
- The proportion/transform audit passed for every entity: 5,424 body vertices
  uniformly scaled and translated; head vertices unchanged except a Y
  translation; body scale 0.5 for humanoids and 0.625 for villagers; visible
  head/body ratio 2.5; bind-floor anchor preserved; collision height unchanged.
- Foot joints and leg columns were checked at -1.2, -0.8, 0, 0.8 and 1.2 rad
  (about +/-69 degrees); they stayed inside the torso across the sampled poses.
- Screenshots `mob-lineup`, `villager-close`/`side`/`back`,
  `player-front-final` and `player-walking-final` were reviewed; the walking
  frame keeps both feet connected.
- After clearing the effect, the five mobs and the player reported
  `replace=false`.

Evidence: `D:/Moddevelop/.workflow/projects/kuso-penguin/work/refined-test/`
(`audit.txt`, `audit-player.txt`, `proportions.txt`, `inspect.txt`,
`inspect-player.txt` and the listed PNGs).

**中文摘要：** 2026-10-19 在独立 `KusoPenguin-Validation` 世界完成实机检查：
玩家与骷髅、僵尸、村民、猪灵、僵尸猪灵均通过五动画根、原版 `setupAnim`
适配、翅膀/脚旋转、交替行走与重置；5424 个身体顶点统一缩放和平移，头部仅
Y 平移，比例 2.5，落地锚点与碰撞高度不变；在约 ±69° 的五个脚关节角度下，
脚关节与腿柱仍在躯干 cube 内，行走截图双脚保持连接；清除效果后全部
`replace=false`。证据同上目录。

Limitations: this is a visual/API sample, not every animation or shader
combination. It does not claim a performance benchmark, modified collision or
view, or shader compatibility. `test NO-SOURCE` and the compile pass are not
behavior tests.
