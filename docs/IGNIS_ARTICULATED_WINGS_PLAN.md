# Ignis 三段贴腹翼 / Three-link belly wings

2026-10-30：每侧 upper → fore → tip 三段闭合鳍肢，上段下垂、前段内收、尖端贴腹。左右尖端间隙约0.79模型像素。小范围关节重叠避免刚体摆姿势露出空截面。

唯一几何源为 `models/prototypes/cataclysm/ignis_legless_source.json` 的 `belly_wing_chain`；`tools/ignis_belly_wings.py` 生成6段各12面，`export_cataclysm_mesh.py` 输出运行时五part schema2与 `ignis_belly_wings.json`。原身体268面、72面短袖和68面脚的每侧数据不变，总620三角面。所有新增面仍固定绑定body1；没有修改Java16骨架、原生臂、武器或下垂脚。

BBS使用同一已捕获几何，不追加第二套翼。捕获输入penguin包含实际资源 `mesh_sha256`、`body_world = world[1] * translate(-bind[1])`，每面包含 `source_part/source_triangle`，另嵌入本次生成的 `belly_wings` 描述。描述以运行时哈希绑定每段pivot/parent及BODY面序号；不能删除right_wing/left_wing，因为它们是短翼袖。

导出器仅把指定BODY面重分配到6个独立辅助组，原16骨不变。每段绝对帧为 `rigid(H * body_world * translate(pivot) * C)`，父链为body1→upper→fore→tip。面归属必须唯一、完整且不触碰268面本体或袖/脚。普通、灵魂和纯身体版本共用此路径。

验证：生成器 `--check` 校验两个文件；上游检查镜像、有限顶点、闭合正体积、尖端间隙、未变部件及严格面归属。原生Blockbench坐标/层级、独立前臂旋转和BBS2.7解析验收由上游实际执行并保留证据。

Runtime flippers stay rigidly baked to torso joint1. BBS gets independent upper/fore/tip bones using the exact same captured faces and canonical source pivots. The native cuff/arm frames and global16-joint clips remain unchanged. Continuous skinning and native combat animation conversion remain outside the standard BBS export.
