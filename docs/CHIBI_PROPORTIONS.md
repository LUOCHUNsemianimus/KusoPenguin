# 2.5 头身视觉预览 / Chibi Proportion Preview

本轮只改变第三人称效果外观。碰撞箱、属性、实体类型、AI、第一人称视点、效果生命周期和网络同步均未改变；幼体与复杂姿态继续走既有完整原版回退。

## 单一公式 / Single formula

比例集中定义在 `com.luochun.kusopenguin.client.PenguinProportions`：

- `TOTAL_HEADS = 2.5`
- `REFERENCE_BODY_HEIGHT = 24`（原模型颈到脚底的身体高度）
- `FOOT_Y = 24`（足底平面）
- 头高只取核心头 cube：人形/玩家/骷髅/僵尸/猪灵为 `8`，村民为 `10`；hat、耳朵、村民鼻子和职业帽不计入测量。

```
targetBodyHeight = (TOTAL_HEADS - 1) * headHeight
bodyScale        = targetBodyHeight / REFERENCE_BODY_HEIGHT
headDrop         = FOOT_Y - targetBodyHeight
```

| 模型族 | 头高 | 目标无头身体高 | bodyScale | headDrop |
| --- | ---: | ---: | ---: | ---: |
| HumanoidModel 及玩家/骷髅/僵尸/猪灵子类 | 8 | 12 | 0.5 | 12 |
| VillagerModel | 10 | 15 | 0.625 | 9 |

整体可见高度 = `headHeight + targetBodyHeight = 2.5 * headHeight`。以后调整头身比只需改 `TOTAL_HEADS` 一处；head/body/头部图层都从同一 `Profile` 取值，不各自重复公式。

## 变换 / Transforms

身体（`PenguinBodyLayer`）在绘制前：

```
translate(0, headDrop / 16, 0)
scale(s, s, s)
```

等价于绕足底平面 Y=24 等比缩放：模型点 `p` 映射为 `s*p + (0, headDrop, 0)`。脚部 Y=24 保持不动，因此脚不悬空也不穿地。原有 renderer 外层缩放保持不变，本层不再乘 entity scale。

头部（`PenguinHeadRenderer`）只做相同的 `translate(0, headDrop / 16, 0)`，不缩放，所以主头/hat、村民头/鼻子、职业帽和装饰的大小不变。村民职业层沿用既有 `RenderLayerMixin` 路径进入该 helper，因此自动获得一次平移，无需再次平移职业图层。

`LivingEntityRendererMixin` 只对 `CustomHeadLayer`、`Deadmau5EarsLayer`、`HumanoidArmorLayer` 三类头部跟随图层统一平移一次；护甲仍保留既有“仅 HEAD 槽位”过滤。其他图层直接调用原 render，`PenguinBodyLayer` 自行缩放，`VillagerProfessionLayer` 经 head helper 平移，均不重复包裹。

所有 push/pop 均在 `try/finally` 中成对执行。

## 边界 / Limits

- 纯视觉：实际碰撞体型、命中判定和属性未缩小；不能宣称实体碰撞箱已变化。
- 不支持的类型、幼体、隐身、发光、蹲伏、骑乘、游泳、鞘翅飞行、旁观状态仍保留完整原版渲染。
- 第一人称手臂仍为原版。
- `docs/VALIDATION.md` 保留前一版全尺寸原型的记录；本轮实际结果见下文。自然 AI 战斗全过程、其他光影、第二客户端和实际碰撞体型缩小不在本次验收范围。


## 本轮实测 / Live validation (2026-10-19)

- 玩家及骷髅、僵尸、村民、猪灵、僵尸猪灵，六种类型已在真实客户端确认 2.5 头身视觉替换。
- 捕获实际绘制顶点，逐点验证头部 X/Z 不变、Y 仅平移；身体五 cube 共 120 顶点逐点满足 XYZ 等比缩放与相应 Y 平移。标准人形 scale=0.5，村民 scale=0.625；绑定足底平面保持 Y=24。
- 同一客户端的六种原模型 setupAnim→适配器运行断言通过，左右脚交替及姿态重置保持；这不等于每种自然 AI 攻击动画都已演示。
- 钻石头盔、南瓜头、村民职业帽近景位置正确；未出现二次位移或悬空头饰。
- 清除效果后六种类型都恢复原版判定/外观，并保存恢复截图。
- 一次 classes/build 成功，无编译修正。Gradle test=NO-SOURCE，未声称单元测试通过。

Live vertex comparisons passed for the player and five adult mob types: head vertices retain their size, all five body cubes scale uniformly, and the bind-pose foot plane stays fixed. Vanilla pose mapping, helmet/head-item alignment and effect removal were checked in the running client. This preview changes visual proportions only; hitboxes and the first-person viewpoint remain unchanged.

本机证据位于 `D:/Moddevelop/.workflow/projects/kuso-penguin/work/chibi-test/`：

- `proportions.txt`：逐顶点比对与实际碰撞高度记录。
- `audit.txt` / `audit-player.txt`：六种模型的原版姿态映射断言。
- `chibi-matrix.png` / `chibi-*.png`：全体、逐个近景、玩家和头饰截图。
- `restore-verified.txt` / `restored.png`：清除效果恢复。
- `capture-settings.json` / `client.log`：1280×720、FOV 70、GUI 3 及相机/运行记录。

样本使用专属隔离存档：受控普通难度以保留敌对生物，玩家创造+抗性，关闭自然刷怪，样本无 AI 且无敌。测试驱动仅留工作流证据目录，不打包进模组。
