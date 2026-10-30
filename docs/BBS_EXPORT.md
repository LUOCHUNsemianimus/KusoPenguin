# BBS 模型导出 / BBS Model Export

`tools/export_bbs_models.py` 把捕获的 Cataclysm 原生盒子/UV 与企鹅 HIGH 蒙皮源转换成可直接编辑的 Blockbench free-model `.bbmodel`。工具只读本地输入、不写运行时资源、不修改游戏 Java，也不下载任何内容；仅依赖 Python 标准库与 Pillow。

The converter turns captured Cataclysm native boxes/UVs plus the HIGH penguin skin source into editable Blockbench free-model `.bbmodel` files. It is local-only and deterministic: it never writes runtime assets, touches game Java, or downloads anything, and depends only on the Python standard library and Pillow.

## CLI

```bat
python tools/export_bbs_models.py
python tools/export_bbs_models.py --input <native_scene.json> --textures <dir> --out <dir>
python tools/export_bbs_models.py --weapon-variants --ignis-soul
```

| 参数 / Option | 默认值 / Default | 作用 / Purpose |
| --- | --- | --- |
| `--input` | `dev-libs/bbs-export-input/native_scene.json` | 原生场景输入 / native scene JSON |
| `--textures` | `dev-libs/bbs-export-input/textures` | 已合成 PNG（`scylla.png`、`maledictus.png`、`ignis.png`、`penguin.png`）/ composited PNGs |
| `--out` | `dev-libs/bbs-export-output` | 输出目录，只增不删 / output directory, never deleted |
| `--weapon-variants` | off | 额外输出 Maledictus 弓/长戟变体 / extra Maledictus bow+halberd variants |
| `--ignis-soul` | off | 额外输出 Ignis 灵魂贴图变体（需 `ignis_soul.png`）/ extra Ignis soul-texture variants |

每次运行输出每个模型的 `<out>/<model_id>/<model_id>.bbmodel` 与内嵌纹理对应的 `texture.png`，并写 `<out>/summary.json` 记录三角面数、网格/骨骼数、纹理尺寸和重建误差。默认主模型为三个 Boss 的原生层级、企鹅替换形态、仅身体形态，以及 `penguin_high` 与 `penguin_high_authoring`。

Each run writes `<out>/<model_id>/<model_id>.bbmodel` plus the matching `texture.png` and a `<out>/summary.json` with triangle counts, mesh/bone counts, texture size and the reconstruction error. Default primary models cover each boss's native hierarchy, penguin-replacement form, body-only form, and the `penguin_high` / `penguin_high_authoring` bodies.

## 来源与格式 / Sources and Format

- 几何与 UV 取自实际 Cataclysm 3.33 原生盒子/UV 捕获；企鹅部分复用 HIGH 蒙皮源。原始第三方 PNG 只保留在忽略的 `dev-libs/` 下，不进入模组仓库或发行包。
- Geometry and UVs come from actual Cataclysm 3.33 native box/UV captures; the penguin part reuses the HIGH skin source. Raw third-party PNGs stay under the ignored `dev-libs/` tree and are not committed to or bundled with the mod repository.
- 输出是标准 BBS/Blockbench 自由模型：一张内嵌图集、网格面片和刚体骨骼组。蒙皮权重已按面烘焙成独立刚体组，**不是**连续蒙皮，也不转换原生战斗动作/动画片段。
- 层级按 Blockbench 5.2.2 原生格式序列化：`outliner` 中每个组都是递归嵌套的 `{uuid, isOpen, children}` 对象，只有网格以 UUID 字符串出现；`groups[]` 保留组自身元数据，但其 `children` 为空。这样导入时不会丢失骨骼或网格归属。
- Output is a standard BBS/Blockbench free model: one embedded atlas, mesh faces and rigid bone groups. Skin weights are baked per face into rigid groups; this is **not** continuous skinning and native combat actions/clips are not converted.
- Hierarchy follows the native Blockbench 5.2.2 layout: every group in `outliner` is a recursive `{uuid, isOpen, children}` object and only meshes appear as UUID strings; `groups[]` keeps group metadata but its `children` is empty. This preserves bone/mesh ownership on import.

## 校验状态 / Validation Status

导出器会从序列化的欧拉角与 group origin 重建世界变换，并保留 2e-4 模型像素阈值及 UV/几何完整性检查。原生 Blockbench 打开核验与 BBS 解析器往返 QA 由上游实际执行；在收到该证据前，本工具不声称已完成实际运行验收。

The exporter reconstructs world transforms from serialized Euler rotations and group origins, keeping the 2e-4 model-pixel tolerance and all UV/geometry checks. Opening the results in native Blockbench and the BBS-parser round-trip QA are executed upstream; until that evidence exists, this tool does not claim runtime acceptance.


2026-10-30 Ignis：捕获输入嵌入与mesh_sha256一致的belly_wings描述、body_world及面source_part/source_triangle。导出时只重分配新增BODY翼面到6个独立辅助组；原16骨、袖、脚与其他Boss不改。详见IGNIS_ARTICULATED_WINGS_PLAN.md。
