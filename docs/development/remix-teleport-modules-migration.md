# Remix 特色模块严格迁移记录

## 范围与参考版本

本次迁移以以下两个工作树的固定提交为唯一行为参考：

- Remix fork：`C:/Users/27881/Documents/SkidProjects/Remix-1.21.11-fork-by-Sling`，提交 `2e327b22a7b00b3aa30503c7a7f6a4ef37629c64`。
- Remix 上游：`C:/Users/27881/Documents/SkidProjects/remix-1.21.11`，提交 `182c09ea39ad1e2b5ad2e217c2dd44ed8e036aeb`。
- Graven 目标环境：Minecraft `26.1.2`、Java `25`、Fabric 与 NeoForge 共用 `common` 实现。

迁移范围包括 `TpAura`、`TpAuraPlus`、`TpauraRise`、`AntiVoid`、`TargetStrafe`、`Regen`、`ItemPhysics`、`ClickTP`、`Derp`、CubeCraft `Disabler` 模式以及上游 `AutoGapple`。CubeCraft 模式按用户指定公开为 `CubeCraftDisbaler`；其余模块保留参考项目公开名称。

## 架构映射

| Remix 参考项 | Graven 对应项 | 适配说明 |
|---|---|---|
| `TargetManager#getTargets()` | `entitiesForRendering()` + `RemixEntityUtils` | 保留选择过滤和模块本地排序；过滤开关由 `Targets` 承载。 |
| `EntityUtil.isSelected` | `RemixEntityUtils.isSelected` | 保留玩家、死亡、村民、隐身、怪物和动物过滤语义。 |
| `FriendManager` | `Managers.FRIEND` | `TpauraRise` 与目标过滤复用 Graven 好友数据。 |
| `SubCore` Blink 队列 | `RemixBlinkManager` | 保留共享包队列、holder 计数、释放与丢弃两种退出路径。 |
| `PacketUtil.sendPacket` | `ClientPacketListener.send` | 普通发送继续发布 `PacketEvent.Send`。 |
| `PacketUtil.sendPacketNoEvent` | `PacketUtils.sendSilently` | 延迟队列、回放和 Watchdog 路径不再次进入发送事件。 |
| Remix `RotationManager` | Graven `RotationManager` | 保留移动包 yaw 小于一圈时加 `720` 的全局包变换，并继续使用 Graven 优先级仲裁。 |
| `MotionEvent` | `SendPositionEvent` | 保留取消原版移动包、改写坐标和旋转的时机。 |
| `WorldEvent` | `GameLeftEvent` | 离开世界时释放/清空缓存并复位状态机。 |
| `BlockCollisionEvent` shape 替换 | `BlockShapeEvent` | 在 `BlockCollisions.computeNext()` 的 `CollisionContext.getCollisionShape` 返回点发布。 |
| `ItemEntityRendererMixin` | `MixinItemEntityRenderer` | 接入 26.1.2 `ItemEntityRenderer.submit(...)`，只替换启用模块时的地面物品姿态。 |
| 旧版 2D/3D immediate render | `UiTree`、`WorldToScreen`、`Render3DScheduler` | 适配 26.1.2 提取/提交流程，投影仍使用八个 AABB 顶点。 |

未增加第三方运行时依赖。所有移植依赖均由 `common` 内的事件、Manager、Mixin 和工具类实现，Fabric/NeoForge 平台层不复制业务代码。

## TpAura

`TpAura` 保留 `IDLE -> OUTBOUND -> AIM -> HOLD -> RETURNING` 六阶段状态机和 `PAUSED` 回弹状态。周期开始时先锁定 `chaseTarget`，距离不超过 `3` 格直接攻击；远距离目标先按 `Prev` 在“上一位置到当前位置”之间插值，再寻找可见且不碰撞的攻击位置。

路径首先测试玩家和目标上方一格之间的直线视线。视线受阻时分别搜索起点和终点上方 `2.0..VClip` 的安全点；两安全点互相可见时构建 `from -> aboveFrom -> aboveTo -> to` 四段路径，否则回退直线插值。每段按 `Step Size` 分割、去重并截断为 `80` 点。每个移动包根据该点脚下碰撞形状计算 `onGround`；单 tick 未走完且最后一点悬空时补一个相同坐标的落地包。

最后一个出站点携带目标旋转。`Pre-Attack Delay` 期间持续固定攻击位置，攻击后按 `Return Delay Ticks` 保持，再根据攻击位置和当前客户端位置重建回程。普通移动和攻击包经过发送事件；CubeCraft 模式固定攻击一次，Vanilla/Paper 才使用 `Attack Times`。`Use Mace` 不自动换槽，主手不是重锤时终止周期，这与参考代码一致。

预热包不在 CubeCraft 模式发送；Vanilla 最多发送四个 `StatusOnly(false, horizontalCollision)`。`Cancel Ping Packets` 取消能力更新和 Pong。收到目标状态 `35` 且处于 `IDLE` 时，`Try Miss Totem` 立即以零预测位置启动新出站周期。收到位置/旋转修正时先关闭 `CubeCraftDisbaler`，随后禁用模块或进入 `1200ms` 暂停。

视觉缓存最多保存十个路径点并在 `1200ms` 内按平方曲线淡出。位置框和轨迹分别保留距离透明度、颜色和缩放。`2D` 目标框投影实体 AABB 八个顶点；参考项目的 `Glow` 分支本身无实现，因此仍保持无效果。`Fake AutoBlock` 只在目标存在且主手持剑时置位，并通过已存在的 `HandsView` 动画入口显示。

## TpAuraPlus

`TpAuraPlus` 保留与 `TpAura` 相同的目标追踪、预测、上抬寻路、逐点落地判断和十点淡出缓存，但攻击包与移动包先进入 `pendingPackets`，只通过无事件路径批量释放。六格内直接攻击；远距离攻击位置必须满足视线、玩家碰撞箱安全以及视点到目标 AABB 不超过 `3.5` 格。

收到位置修正时，在周期内或上个周期结束后 `2000ms` 保护窗口中取消原包并静默确认 teleport ID。周期内清空待发队列，从服务端修正位置向真实客户端位置构建恢复路径；距离不足一格则直接进入暂停。保护窗口外按普通回弹处理。参考实现不会在正常周期结束时直接改写本地玩家位置，因此 Graven 也只完成包路径并复位状态机。

## TpauraRise

`TeleportAura` 模式保留 CPS/1.9 冷却选择、攻击键和使用键互斥、`Single`/`Multiple` 调度以及每个路径点后紧跟 `ServerboundClientTickEndPacket` 的序列。`Multiple` 循环会多次覆盖同一组 `path` 与 `attackEntity`，最终只执行最后一次路径；这是参考源码的真实行为，未擅自修复。

`Watchdog` 模式在收到位置修正后开始 Blink。下一次 motion 首先跳过；随后把坐标和旋转改为目标，保存待攻击目标，并缓存所有后续出站包。更新阶段先静默攻击，Blink 超过一个 tick 后按原顺序静默释放全部包。修正包不被取消。禁用和离开世界都会先结束 Blink，再清理目标、路径和队列。Watchdog 中央目标文本与 TeleportAura 青色路径立柱均保留。

## 其他模块

- `AntiVoid`：保留 Blink、Flag、GhostBlock 三模式。Blink 回到最后安全点并丢弃危险阶段包；Flag 支持静默改写 motion；GhostBlock 只在判定虚空坠落时替换救援高度以下的空碰撞形状。
- `TargetStrafe`：保留 Adaptive/Behind、按键反向、撞墙或前方虚空自动反向、空间键条件和第三人称视角恢复。
- `Regen`：保留 Normal 固定包数与 AntiCheat 随机包数、延迟、位置抖动、反应等待、按键抖动和随机暂停状态。
- `ItemPhysics`：保留地面物品平躺、旋转和堆叠姿态；Mixin 只在模块启用时接管渲染状态。
- `ClickTP`：保留准星方块选择、距离限制、按 `Step Distance` 分段的本地推进和目标框。
- `Derp`：保留旋转速度、俯仰角和 Priority 设置；全局移动包 yaw `+720` 依赖也一并迁移。
- `CubeCraftDisbaler`：保留取消 `START_SPRINTING`、KeepAlive/Pong 延迟队列、`100..299ms` 队首释放、默认 `25s` 整体释放、ForceGround 和原始移动判定公式。
- `AutoGapple`：以真正上游实现为准，只识别副手普通金苹果；低血量触发使用，持续 `32` tick，取消减速并阻止 `RELEASE_USE_ITEM`。

## CubeCraft 原始边界

以下三点是参考实现的组成部分，未静默重写：

1. `distance <= 64 - speed - 0.15` 使用的是参考源码原公式，即使其量纲不一致。
2. `C0FPacketConfirmTransaction` 在 26.1.2 中不存在；字符串匹配分支保留，因此实际为死分支。
3. 入站 KeepAlive 在新协议中不能作为出站包编码。Graven 保存包方向并在客户端监听器重放入站包；这只修正协议方向，不改变延迟与顺序。

## 验证矩阵

| 层级 | 验证项 | 通过标准 |
|---|---|---|
| 静态 | i18n JSON 解析 | `en_us.json`、`zh_cn.json` 均为合法对象且新模块无空叶节点。 |
| Common | `:common:compileJava` | 所有共享 Minecraft API、事件和 Mixin 回调代码通过 Java 25 编译。 |
| Fabric | `:fabric:compileJava` | Fabric 平台复用共享源码并通过编译。 |
| NeoForge | `:neoforge:compileJava` | NeoForge 平台复用共享源码并通过编译。 |
| 发布 | `buildRelease` | Mixin、资源、双平台 remap 和发布产物全部成功。 |
| 差异 | `git diff --check` | 无空白错误；`AGENTS.md` 无本轮改动。 |

## 兼容性报告

- **保留：** 模块公开名称、设置默认值和范围、目标排序、阶段状态机、包顺序、延迟/缓存语义、异常回退、禁用与离开世界清理、视觉效果以及参考源码中可观察的缺陷。
- **更改：** Yarn 1.21.11 类名映射为 Mojmap 26.1.2；渲染改用 26.1.2 的提取/提交 API；入站 KeepAlive 使用方向感知重放；CubeCraft 模式按要求独立命名为 `CubeCraftDisbaler`。
- **移除：** 无功能分支被移除。
- **未实现：** `TpAura` 的 `Glow` 高亮没有渲染逻辑，因为参考源码该选项同样没有实现体。
