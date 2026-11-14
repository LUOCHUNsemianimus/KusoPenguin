# Yes Steve Model Player Compat / YSM 玩家兼容

中文：本兼容是**可选**路径，只在客户端安装固定版本 **Yes Steve Model 2.6.5** 时生效；不安装 YSM 时模组照常启动，玩家继续走原有原版玩家企鹅替换。**仅 2.6.5 受支持并验证**；其他版本不保证可用，混淆类/方法签名一旦变化可能导致 mixin 加载失败，需要重新核对描述符后才能声明兼容。本兼容不做版本检测或协商。玩家路径**不再要求东方小女仆（TLM）**：只要存在 YSM 的 pinned 工具类即可尝试；女仆路径仍只对精确类型 `touhou_little_maid:maid` 生效并惰性链接 TLM。

English: This optional path requires the pinned **Yes Steve Model 2.6.5** on the client only. Without YSM the mod starts normally and players keep the existing vanilla-player penguin replacement. **Only 2.6.5 is supported and verified**; other versions are not guaranteed, and changed obfuscated class/method signatures may fail mixin loading, so descriptors must be revalidated before claiming compatibility. There is no version detection or negotiation. The player path **no longer requires Touhou Little Maid (TLM)**: presence of the pinned YSM utility class is sufficient. The maid path still admits only `touhou_little_maid:maid` and links TLM lazily.

## 固定版本与钩子 / Pinned version and hooks

- 固定版本：Yes Steve Model **2.6.5**（混淆名即契约）。升级 YSM 前必须重新核对下列描述符。
- `PlayerYsmRenderMixin`（`@Pseudo`）包裹 `com.elfmcys.yesstevemodel.OO0OOoo0ooooOoO0O0o00Ooo` 的实际共享高-level 玩家渲染体：
  `oOo0OO0O0o000OO0O000oo0o(Lcom/elfmcys/yesstevemodel/O0OOoooOOoOo0O00O0oOoo0O;Lnet/minecraft/resources/ResourceLocation;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V`。
  这里**不是**原版 `LivingEntityRenderer.render`。包装内用 try/finally 平衡 `enterPlayer` / `exit`；通过反射调用继承的 `ooo00OoO00OOOO0oOooOo0Oo():Entity`，只有实际 `Player` 才进入。
- `PlayerYsmLayerMixin`（`@Pseudo`）包裹基类层循环
  `oOo0OO0O0o000OO0O000oo0o(O0OOoooOOoOo0O00O0oOoo0O;FPoseStack;MultiBufferSource;I Oo0Oo0O0OoOoO0oooO00O0o0;OoooO0Ooo0O0o0OO0ooOOooO;)V`
  中对 `O000o00OooO00oO0oo0o0o0o.oOo0OO0O0o000OO0O000oo0o(PoseStack,MultiBufferSource,int,OoO0oo0o0o0oOoo0oOOO0Ooo,FFFFFF)V` 的 INVOKE。四个原生层类型按类名识别：held item `o000oOO0000oOOOOo0O0oooO`、elytra `oO0OO0o0oo00OOOoOoOO00Oo`、shoulder parrot `ooOoOoo00o0O0OO0oO0O0ooo`、fishing `oOO0Oo000ooooO0Oo00Oo000`。
- 复用既有 `Frame` / `YsmView` / `capturePass` / 四边形多重集减法 / 头顶点重放 / 延迟 canonical body / 共享 `PenguinRenderContext`。未新增第二套动画器、模型或舞蹈状态。

## 保留与替换 / Retention and replacement

- 原生 YSM 头与头发保留：仅把 full 与 AllHead-hidden 两次本地姿态顶点的**精确差值**重放到原生 consumer。
- 身体替换为共享企鹅 canonical body，使用原生 rest neck 的同一 8px 头 / 12px 身校准；玩家 `YsmView` 不被当成女仆视图（`isMaidView`）。
- 原生走路骨骼适配、动画选择、材质与用户 YSM 模型/配置/选择全部保留；本兼容不写 YSM 配置。
- 玩家原生层不是原版 `RenderLayer`。活动玩家帧把所有层回调连同 PoseStack pose/normal 与全部参数入队，原生 mesh 完成后按稳定顺序**只回放一次**：
  - held item：用实际主/副手物品在共享翼端 `PenguinRenderContext.applyHand` 绘制，保持实际 dominant arm 与 `THIRD_PERSON_*` 显示上下文；玩家路径不引用 TLM `GunClientUtil`。
  - elytra：替换成功且未滑翔时隐藏；滑翔时经既有 `mountElytraOnBack` 尝试挂到共享背部。parrot 替换成功时隐藏。
  - fishing tether、盔甲/头部饰品与未知 addon 层原样回放，不做“一刀切”取消。

## 回退与恢复 / Fallback and restoration

- 无 YSM、无生效企鹅效果、隐身、发光、旁观、幼年、不支持的模型、未初始化模型或未通过原生捕获证明：完整原生渲染，不写共享 body。在 2.6.5 内缺少可选模组，或缺少/不支持原生模型与骨骼契约时同样回退原生渲染。
- 捕获过程中原生骨骼/辅助数组在 finally 中按克隆恢复；失败帧不生成 deferred body，所有已排队层原样回放。
- 原生方法异常退出时清空队列、不重放、不绘制 deferred body，并 pop 共享上下文；正常与异常路径的帧/上下文都成对。
- 效果移除后下一次绘制直接回到完整原生 YSM 玩家渲染，头/身体无残留替换。
- 死亡副本仍走既有排除逻辑；detached 舞蹈预览的原版 `RemotePlayer` 路径不变。
- 原生 YSM 动画时钟只推进一次；玩家 IM 快照不误用女仆专用可视进度时钟。

## 限制 / Limitations

- 只针对固定 YSM 2.6.5 的混淆描述符。仅 2.6.5 受支持/验证，其他版本不保证；目标类或方法签名变化可能导致 mixin 加载失败，必须重新核对描述符。2.6.5 内缺少可选模组或原生模型/骨骼契约不满足时回退原生渲染；不做版本检测或协商。
- held item 的翼端位置沿用共享企鹅数值；不同 YSM 模型或附加层可能仍需单独观感校准。
- YSM elytra 滑翔挂载为“可行时复用”路径，未做运行时观感确认。
- 未知 addon 层按原生回放；若其几何专为原生人形躯干设计，可能仍在企鹅身体上浮动，需要上游/实机确认是否加入显式过滤。
- 头发/头部材质若在 YSM 内部走非顶点通道（而非 pinned 工具类绘制），不在本证明范围内。

## 验证状态 / Verification status

- 首版原生启动曾因层回调注入首参类型不匹配报 `InvalidInjectionException`；已在上游复核步骤中补 `@Coerce` 修正，随后启动通过。
- 上游实际隔离原生客户端（Kuso + YSM 2.6.5 + TLM 1.5.3 + IM 0.8）启动通过：1920×1080、GUI 4、FOV 70、第三人称正面。默认模型与 wine_fox/19_nine_tailed 均保留原生头/头发并显示企鹅身体；移除效果后恢复原生身体并保留同一模型选择；剑与火把挂在共享翼端；蹲下、经真实服务端/客户端槽位播放 `builtin:hip`，以及刻意触发原生缓冲异常后的队列/栈清理均通过。
- 仅安装 Kuso + YSM（无 TLM/IM）时，原生游戏启动与实际企鹅玩家渲染通过。
- 证据位于 `.workflow/projects/kuso-penguin/runtime/player-access-20261114/`：`ysm-audit-penguin`、`ysm-audit-restored`、`ysm-audit-winefox`、`ysm-audit-winefox-restored`、`ysm-audit-ysm-only`、`ysm-audit-dance`、`ysm-exception.txt`；截图为 `ysm-shot-held.png`、`ysm-shot-dance.png`、`ysm-shot-winefox.png`。
- 非 YSM 的 Bedrock 女仆视觉上仍为企鹅；YSM 专用女仆路径的非回归**未测试**。
- 仅 Kuso、不安装 YSM/TLM/IM 的独立启动通过：成功进入私有世界；两个默认未绑定管理键已注册，实际 `DanceScreen` 与原生 `RecipeManager` 的 1×3 麦克风配方均通过。证据为同一目录下 `standalone-passed.log`、`keys.txt`、`recipe.txt`、`screen.txt`。
- 游泳/骑乘/滑翔、双客户端观察者/多人、任意 addon 层均未做运行时验证；用户主观观感验收仍待确认。本文不声称覆盖所有模型或所有姿态。

English: Runtime validation covers the pinned YSM 2.6.5 in an actual native client (with TLM 1.5.3 and IM 0.8), and a Kuso+YSM-only startup; the initial `@Coerce` injection fix is included. The YSM-specific maid regression, swimming/riding/gliding, two-client observer/multiplayer and arbitrary addon layers remain untested, and subjective visual acceptance is pending. No blanket all-model/all-pose guarantee is made.
