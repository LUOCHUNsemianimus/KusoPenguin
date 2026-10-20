# Kuso Penguin

Minecraft 1.21.1 / NeoForge 21.1.243 / Java 21.

Experimental potion-driven, head-preserving penguin body replacement.

## Implemented prototype

- Effect: `kuso_penguin:penguin`.
- Vanilla potion items with the registered `kuso_penguin:penguin` potion: normal, splash and lingering forms.
- Supported adult entities: player, skeleton, zombie, villager, piglin, zombified piglin.
- Penguin geometry: rounded multi-cube body generated from the Blockbench native export (`226` cubes, `512x512` atlas) with `body`, `right_wing`, `left_wing`, `right_foot` and `left_foot` as the five animation roots; feet pivot at the exported Blockbench Y9 position and their leg columns extend into the torso. The original model supplies the head. See [docs/ROUNDED_MODEL.md](docs/ROUNDED_MODEL.md). This cube body is now the retained legacy comparison/fallback path and is not iterated further; the current modeling direction is the polyhedral mesh, one solid colour per face, with colour boundaries authored as real mesh edges (see the mesh preview below and `AGENTS.md`).
- Vanilla animation poses are read after the original `prepareMobModel`/`setupAnim`; the adapter copies rotations plus translation deltas. The villager's combined crossed `arms` part is mapped explicitly onto both wings.
- Non-head body layers are filtered for supported entities; head gear and head decorations remain.
- Chibi proportion preview shared through `PenguinProportions`: humanoid bodies scale to 12/24 (0.5) and villager bodies to 15/24 (0.625), giving a 2.5-head visible total while the original head keeps its size. Scaling is visual-only and pivots on the foot plane. See [docs/CHIBI_PROPORTIONS.md](docs/CHIBI_PROPORTIONS.md).
- Observer sync bridge for the custom effect only: add/update/remove are broadcast with vanilla effect packets, and a player who starts tracking an already-affected entity receives its current remaining duration. No custom protocol, client cache or periodic resend.
- Experimental low-poly mesh body preview with three mesh tiers: the accepted block model remains the default. Press `F8` in-game to cycle Block -> High (492 triangles) -> Medium (308) -> Low (216) -> Block for players and the five supported mob types while the effect is active; the key is rebindable in Controls and the action-bar message names the current and next tier. The initial state can be forced to High for testing with the system property `kuso_penguin.meshPreview=true`. All tiers share the same parser, renderer, texture, five animation roots, 2.5-head proportions and potion rules; the three JSONs are parsed together only during client resource load/reload and swapped as one immutable cache, with no IO on `F8`. Each triangle has three identical UVs and samples one solid swatch from the shared atlas, so every face is one colour and no face spans black and white; the black/white torso boundary is a real mesh edge with shared boundary vertices, the white faces are narrow at the chest and widen over the belly, and the front shoulder is wider and flatter. The Low tier gained a shoulder ring, so it is now 216 triangles rather than the earlier 200. Wings, feet and the ribbon also use one solid colour per face; the old curved mask and speckles are removed. The pre-revision curved-UV sources, runtime JSONs and atlas are kept unchanged in `models/archive/curved-uv-20261020/` for comparison or a whole-set restore; the active sources are the three `models/prototypes/penguin_lowpoly*.bbmodel` files. The previous X/Y coordinate correction, head size, 2.5-head proportions, animation roots and shader render path are unchanged. Mesh export and parsing follow Blockbench Modded Entity's native coordinates: absolute `(-BBx, 24-BBy, BBz)`, root pivot converted the same way, and local `(px-vx, py-vy, vz-pz)`; reflecting both X and Y keeps the saved Blockbench winding, so normals are recomputed from the converted vertices. The mesh uses no weighted skinning and is not a new dependency. See [docs/POLYGON_PROTOTYPE.md](docs/POLYGON_PROTOTYPE.md).

## Limitations

Baby, invisible, glowing, crouching, riding, swimming, elytra-flying, spectator and unsupported entity types intentionally keep the complete vanilla renderer. First-person arms remain vanilla. No brewing recipe is included in this prototype. Effect sync covers only players currently tracking the affected entity; it relies on the server-side effect lifecycle and does not resend periodically or cover client-side-only fake effects. The experimental mesh preview's earlier live checks predate both the three-tier selector and the per-face solid-colour geometry revision; they remain historical evidence for the shared pipeline and the 492-triangle high mesh only. The current 492/308/216 per-face geometry round passed live acceptance for the player and all five supported mobs: submitted vertices were 1,968/1,232/864, and head size, the 2.5-head proportion, foot anchor and five-root pose mapping all passed. The zombie wing bind still matches the native model, and the `F8` cycle, resource reload, effect-removal restore and Low-tier walking all passed. Geometry audit confirmed identical per-face UVs sampling one solid colour, a closed manifold torso with the black/white boundary on shared edges, shoulder/leg joints inside the torso and overlapping leg columns at +/-1.2 rad; the archive matches the previous delivery hash. See [docs/POLYGON_PROTOTYPE.md](docs/POLYGON_PROTOTYPE.md). The current X/Y correction, head size, 2.5-head proportions, animation roots and shader render path are unchanged by this round. Shader compatibility is user-reported only; Iris and other shaders were not re-tested in this geometry revision, and no FPS benchmark was run.

See [docs/TESTING.md](docs/TESTING.md) for command-line and visual test steps. Representative live-client checks from earlier rounds passed; see [docs/VALIDATION.md](docs/VALIDATION.md) for the tested scope and remaining checks.

## Development

Windows CMD, Java 21:

```bat
set JAVA_HOME=C:\Users\10217\.jdks\ms-21.0.12
gradlew.bat build --offline
gradlew.bat runClient --offline
```

The matching main-mod toolchain is Gradle 9.2.1, ModDevGradle 2.0.142 and Parchment 2024.11.17. Kuso Penguin does not depend on Huhutalk, Touhou Little Maid or JourneyMap. Model source: `models/penguin_body.bbmodel`; compile-time geometry is generated from `models/penguin_body_export.java` by `tools/generate_penguin_geometry.py` (use `--check` to detect drift). That generator belongs to the retained legacy/fallback cube body and is not developed further; the current mesh sources are the three `models/prototypes/penguin_lowpoly*.bbmodel` files exported by `tools/export_penguin_mesh.py`.

The chibi preview is a visual ratio only: it does not change the hitbox, attributes, entity type, AI or first-person viewpoint. The visible head/body ratio follows the single `PenguinProportions` formula. `docs/VALIDATION.md` and the earlier acceptance notes in `docs/CHIBI_PROPORTIONS.md` remain historical records of prior rounds, not proof of the rounded-model round.
