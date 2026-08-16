# BMWClient Freeze 严格迁移

## 参考边界

本迁移以 `SkidProjects/BMWClient/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/ModuleFreeze.kt`、`PacketQueueManager.kt`、`NetworkEvents.kt` 和当前 Minecraft 26.1.2 参考源码为权威来源。目标是替换 `FlightDelayTrigger` 对 `Stuck` 的近似映射，并保留 Freeze 的模式、包流、Tick 余额、修正处理、交互时序和退出清理。

## 架构与 API 映射

| BMWClient | Graven | 适配 |
|---|---|---|
| `ModuleFreeze` | `movement.Freeze` | 独立 Module，保留单例、三模式和全部默认值。 |
| `ChoiceConfigurable` | `EnumSetting<Mode>` + 条件 Setting | Graven 使用扁平 Setting 模型，模式依赖由延迟 lambda 控制。 |
| `PlayerTickEvent` | `PlayerTickEvent.Pre` | 两者都在本地玩家父类 Tick 前取消。 |
| `PacketEvent(origin, packet)` | `PacketEvent.Send/Receive` | 方向由事件类型映射为 `TransferOrigin`。 |
| `QueuePacketEvent` | `QueuePacketEvent` | 保留 `FLUSH(0) / PASS(1) / QUEUE(2)` 单调优先级仲裁。 |
| `PacketQueueManager` | `Managers.PACKET_QUEUE` | 保留统一快照、双向队列、最终优先级截获、冲刷和入站重放。 |
| `sendPacketSilently` | `PacketUtils.sendSilently` | 重放和 Stationary 替换包绕过 Graven 发送事件。 |
| `PlayerMoveC2SPacket` | `ServerboundMovePlayerPacket` | 取消所有位置、旋转和状态子类型。 |
| `CommonPongC2SPacket` | `ServerboundPongPacket` | `CancelC0B` 的当前协议映射。 |
| `PlayerInteractItemC2SPacket` | `ServerboundUseItemPacket` | 保留 hand、sequence，并重写 yaw/pitch。 |
| `PlayerInteractEntityC2SPacket` | `ServerboundInteractPacket` | 旋转包后静默重发原交互包。 |
| `PlayerInteractBlockC2SPacket` | `ServerboundUseItemOnPacket` | 旋转包后静默重发原交互包。 |
| `PlayerPositionLookS2CPacket` | `ClientboundPlayerPositionPacket` / `ClientboundPlayerRotationPacket` | 26.1.2 将位置与纯旋转修正拆为两个包。 |
| `interaction.sendSequencedPacket` | `BlockStatePredictionHandler.startPredicting()` | 使用原版 prediction sequence 生命周期。 |
| `SimulatedPlayerCache` | `FreezeMovementPredictor` | 在未加入世界实体表的 `RemotePlayer` 副本上调用 26.1.2 原版 `Player.travel` 积分。 |

## 模式与数据流

### Queue

`Freeze` 通过 `QueuePacketEvent` 请求选定方向进入 `QUEUE`。`PacketQueueManager` 在所有普通 Packet 监听器之后运行，已经取消的包不再入队；握手、状态查询、Ping 和聊天包直接通过；服务端位置/旋转修正、断线和死亡包先冲刷对应方向。出站快照由 `PacketUtils.sendSilently` 重放，入站快照在客户端 Tick 线程调用当前 packet listener。没有模块继续声明队列请求时，下一次客户端 Tick 自动冲刷。队列中的移动包持续提交折线路径，并在第三人称使用 Graven 共享 `WireframeEntityRenderer` 绘制首个延迟位置的玩家模型。

### Cancel

按 `Cancel Origin` 中的 Incoming/Outgoing 方向取消 `PacketEvent.Receive` 或 `PacketEvent.Send`。该模式不保存包，因此禁用后不存在重放动作。

### Stationary

所有 `ServerboundMovePlayerPacket` 均取消。可选的 `CancelC0B` 取消 `ServerboundPongPacket`。使用物品、实体交互和方块交互会取消原包，先静默发送仅旋转包，再静默发送交互包；随机 yaw/pitch 偏移位于 `[0.002, 0.01)`，相邻偏移差至少为 `1E-6`，对应 BMW 对 Grim duplicate rotation 的处理。

## Tick 余额与 negative timer

Freeze 运行时取消每个 `PlayerTickEvent.Pre` 并累计 `missedOutTick`。禁用且 `BalanceWarp=true` 时，模块先进入 `warpInProgress`，同步补跑累计数量的 `LocalPlayer.tick()`，最后清零计数。`BalanceWarp=false` 时直接丢弃累计 Tick。路径预览使用当前按键、摄像机旋转、速度、碰撞箱、姿态、能力、物品栏、药水效果和移动速度初始化隔离玩家副本，并通过原版 `Player.travel` 逐 Tick 生成折线路径。

`BypassNegativeTimer=true` 时，禁用后优先使用副手；若副手为珍珠、TNT、火焰弹、风弹或 Eat/Drink/Bow/Crossbow 动作，则搜索热栏。临时换槽会显式发送 carried-item 包，预测 sequence 内发送 `ServerboundUseItemPacket`，随后恢复原槽位。

## 生命周期

- 启用：清零 missed Tick 与 warp 标记。
- 收到位置或旋转修正：清零 missed Tick；按 `DisableOnFlag` 关闭，可选发送本地化通知。
- 禁用：按配置补跑 Tick、清零状态并执行 negative timer 交互。
- 离开世界：自动关闭；队列管理器清空剩余快照。
- `AutoThrow`：Freeze 开启时暂停其目标搜索与投掷状态机，避免与冻结包流冲突。

## 配置适配

BMW 的 `multiEnumChoice("Origin")` 在 Graven 中映射为 `Queue Origin` 和 `Cancel Origin` 两个 `StringListSetting`，序列化仍为字符串数组并支持同时选择 Incoming/Outgoing。默认值均为仅 Outgoing。列表中非 `Incoming`/`Outgoing` 的字符串不会改变包流。

## 依赖与性能

迁移未引入新的外部依赖。并发队列使用 JDK `ConcurrentLinkedQueue`，协议类型、隔离玩家和 prediction sequence 均来自当前 Minecraft 26.1.2；渲染复用 Graven 的 `Render3DScheduler` 与 `WireframeEntityRenderer`。常态 Stationary/Cancel 不分配预测实体；只有 `BalanceWarp` 开启且存在 missed Tick 时按渲染帧构建隔离预测状态，队列路径遍历与排队包数量线性相关。

## 兼容性报告

- **保留：** Queue/Cancel/Stationary、全部布尔默认值、双向 Origin、Tick 取消与余额回放、修正清零/关闭、通知、negative timer 交互、排除物品与动作、临时换槽、prediction sequence、随机旋转偏移、C0B 选项、退出关闭、包队列仲裁与重放。
- **更改：** BMW 嵌套 Choice 配置映射为 Graven 条件 Setting；Origin 使用字符串列表；位置修正适配为 26.1.2 的两个包类；预测器使用目标版本原版 `Player.travel` 的隔离实体，而非移植旧映射下的 1000 行物理代码。
- **移除：** 无。
- **未实现：** 无运行时 Freeze 行为缺项。
