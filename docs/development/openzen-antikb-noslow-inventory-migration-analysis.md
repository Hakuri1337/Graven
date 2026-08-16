# OpenZen AntiKB、NoSlow 与 InventoryManager 迁移分析

## 结论

OpenZen 的三个功能不是只依赖一个模块类。它们依赖一套完整的事件、包拦截、状态队列、输入恢复、旋转和目标选择协作层。Graven 当前的 `PacketUtils`、`PacketQueueManager`、`RotationManager`、`InvHelper` 和 `InvManager` 只能提供相似能力，不能在不改变行为的情况下直接替代。

本次只完成调查和迁移设计，不修改 OpenZen 源码，也不把功能实现进 Graven。目标迁移应保留 OpenZen 的模块边界、状态机、包顺序、失败回滚和外部依赖；依赖优先整体迁入，再做 Graven 26.1.2 包签名适配。

## Grim MultiActionsC/D 的真实判定

当前 GrimAC 源码中：

- `MultiActionsC` 只处理 `CLICK_WINDOW`。如果本 tick 没有服务器打开容器，并且 `isVerboseSprinting`、`isVerboseSneaking` 或 `isVerboseInput` 任一为真，就会记录/取消点击。
- `MultiActionsD` 只处理 `CLOSE_WINDOW`，使用同样的 sprint/sneak/input 条件；它不会取消关闭包，但会 flag。
- `serverOpenedInventoryThisTick` 只由服务器发送 `OPEN_WINDOW` 后、经过事务延迟标记；玩家自己的 InventoryMenu 不会自动满足该条件。
- `serverOpenedInventoryThisTick` 在收到移动包或 `CLIENT_TICK_END` 时清除，所以“刚打开背包”不能长期豁免点击。

因此，`OnlyInventory=false` 本身不会绕过 Grim。OpenZen 不被持续检测的主要原因是包流被重排：

```text
handleInventoryMouseClick
  -> ConnectionPatch.onPacketSend
  -> InventoryManager.onPacket 取消 CLICK_WINDOW
  -> pendingPackets
  -> 等待 Sprint/输入状态稳定
  -> PacketUtil.sendQueued(click) 绕过本地拦截但仍发送到服务器
  -> 后续再发送 CLOSE_WINDOW
```

`PacketUtil.sendQueued` 只绕过 OpenZen 自己的 `ConnectionPatch` 回调，不会绕过服务器 Grim。它的作用是避免队列包再次被本地模块取消。OpenZen 同时在 `onSprint` 中把玩家冲刺状态压低，并在 `isPerformingAction && ServerboundPlayerInputPacket` 时取消带冲刺输入、发送不带冲刺的替代输入。对于普通 GuiMove 场景，Grim 看到的 `knownInput.moving()` 与客户端本地移动并不总是同一 tick 的值，且点击包被移到后续包窗口，所以通常不会连续命中 C。

但 OpenZen 当前时序有一个边界缺陷：`onMotionManage` 在 Post Motion 中调用 `handleInventoryMouseClick` 后，若事件监听顺序先后允许 `onMotion` 继续处理，`pendingPackets` 可能在同一客户端 tick 被释放，随后立即发送 `CLOSE_WINDOW`。这会让 Grim 在初始整理瞬间同时看到 CLICK_WINDOW/CLOSE_WINDOW 与移动状态，产生你观察到的 MultiActionsC/D。

## OpenZen NoSlow 的外部依赖

入口：`SkidProjects/OpenZen/src/main/java/shit/zen/modules/impl/movement/NoSlow.java`。

| 依赖 | OpenZen 职责 | Graven 差异 |
|---|---|---|
| `PacketUtil` | `sendQueued`、预测序列、绕过发送回调 | Graven `PacketUtils.sendSilently` 只有 bypass 集合，没有 OpenZen 的“队列后发送”契约 |
| `Timer` | 弓/食物释放窗口和 blink tick | Graven `TimerUtils` 以毫秒为主，需要额外的 tick 边界计时 |
| `GuiMove` | 屏幕打开时产生移动输入 | Graven 没有等价的完整 GuiMove/InventoryManager 互锁层 |
| `MovementUtil` | 判断输入活动和速度 | Graven 的移动判断不能直接表示服务器已收到的 input 状态 |
| `InputConstants/GLFW` | 恢复真实 keyUse 状态 | Graven 的按键生命周期由当前输入事件/Screen 流程管理 |
| `BlockStatePredictionHandler` | 生成预测序列发送 UseItem | 26.1.2 的预测 API、包构造参数和线程时机不同 |
| `ClientboundContainerSetSlot/SetEquipment` | 判定副手交换完成 | 26.1.2 包字段与装备槽映射必须重新核对 |
| `Minecraft.hitResult` | `isBlocking` 判断 | Graven 已有逻辑命中结果隔离，直接读取会重新引入准星/服务端旋转错位 |

NoSlow 的完整行为包括两个相互独立的状态机：食物/药水的 `IDLE -> WAITING -> SWAPPING -> USING`，以及弓/弩的 `didSwapHand/isBlinking/releaseTicksRemaining`。迁移不能只复制 `SlowdownEvent` 分支；必须同时迁入 Pong 暂存、装备包触发、入站 blink 白名单、拉回/重生/断线清理、键盘状态恢复和预测 UseItem 序列。

## OpenZen AntiKB 的外部依赖

入口：`AntiKB.java`，策略位于 `combat/antikb/AntiKBMode.java`、`NoXZMode.java`、`JumpResetMode.java`、`MixMode.java`。

### 分发和状态

`AntiKB` 是策略分发器，不直接处理击退。它保存设置，按事件查找当前 `AntiKBMode`，并在 `FireballBlink` 或 `HighJump` 活跃时暂停策略。模式对象自行保存击退包、挂起阶段、旋转、攻击目标和队列。

### NoXZMode 依赖

- `KillAura.target` 和目标 AABB 距离计算，用于落地攻击窗口。
- `Stuck`、水/岩浆、着火、攀爬、睡眠、蜘蛛网和飞行状态，用于 `shouldIgnore`。
- `Scaffold`、`NoFall`、`Backtrack` 等模块的状态会改变释放时机。
- `PacketUtil` 负责移动包队列、击退包回放和 bypass。
- `ZenClient.serverTickRate` 负责 `instantAttack` 的临时时间倍率。
- `ClientboundSetEntityMotionPacket`、`ClientboundPlayerPositionPacket` 等包决定挂起、flag 和恢复。

NoXZ 的行为不是取消击退：它缓存自身击退和移动包，最多挂起 12 tick；落地且目标合法、玩家冲刺时先回放移动包，再回放击退包，随后按次数攻击，并在攻击后衰减水平速度。flag、断线、禁用和无效环境都会释放所有队列并恢复 tick rate。

### JumpResetMode 依赖

它依赖 `Rotation`/`RotationHandler`、`NoFall`、`Backtrack`、`Scaffold` 和 GLFW 按键读取。空中击退最多延迟 20 tick，地面延迟 10 tick；根据击退向量 `atan2(x, -z)` 生成目标 yaw，必要时锁定旋转，落地释放后补一次跳跃。旋转和按键必须在断线、拉回和依赖模块激活时恢复。

### MixMode 依赖

它额外依赖 `KillAura`、`Scaffold`、`StuckInBlockEvent` 和完整移动键快照。模式会缓存击退、移动、Ping、传送包；在 1～3 tick 内按击退方向覆盖前后左右键，并在结束后读取真实键盘状态恢复。液体和蜘蛛网是明确排除条件。

## Graven 对应层的差异

| OpenZen 层 | Graven 当前层 | 不能直接复用的原因 |
|---|---|---|
| `EventTarget` + 多事件回调 | `EventBus` 精确运行时类型分发 | Graven 没有 OpenZen 的同一事件优先级和生命周期 |
| `PacketEvent(packet, incoming)` | `PacketEvent.Send/Receive` | OpenZen 的 `isIncoming()`/`isIncomingRaw()` 语义容易反转；Graven 已拆方向 |
| `PacketUtil.queuedPackets` | `PacketUtils.bypassedPackets` | OpenZen 是发送队列标记，Graven 是 bypass 集合；重放和并发保证不同 |
| `Timer` | `TimerUtils` | OpenZen 同时按 tick 和毫秒驱动，Graven 工具没有状态机 tick 语义 |
| `RotationHandler` | `Managers.ROTATION` | Graven RotationManager 可被替换，必须每次读取，不能长期缓存 OpenZen handler |
| `KillAura.target` | `FightManager`/TargetManager | 目标锁定、攻击次数和 attack lock 协议不同 |
| `GuiMove`/`Sprint` | 无完整等价组合 | InventoryManager 需要新增输入门控和恢复协议 |
| `ItemUtil`/`BlockUtil` | `InvHelper`/`ClickSlotUtils` | 26.1.2 物品组件、容器输入枚举和槽位语义已变化 |
| `ZenClient.serverTickRate` | 无等价公共时钟 | 必须迁入独立 tick-rate 控制器，不能写入普通 TimerManager |

## 完整迁移方案

### 第一组：NoSlow 依赖整体迁入

应整体迁入以下职责，而不是把 OpenZen NoSlow 简化成现有 `NoSlow` 的几个 if：

1. `OpenZenPacketBypass`：保存待 bypass 包的身份集合，保证队列包只绕过一次本地发送拦截，并在连接/世界切换时清空。
2. `OpenZenInboundBlinkQueue`：按 OpenZen 白名单缓存入站包，提供登录、重生、拉回、断线的强制 flush。
3. `OpenZenUsePrediction`：迁入预测序列生成和 `UseItem` 构造，不复用旧版签名。
4. `OpenZenKeyState`：保存并恢复 keyUse、keySprint 和输入消费状态。
5. `OpenZenNoSlow` 状态机：保留食物/药水 offhand 状态、弓/弩 blink 状态、Pong 队列和装备包触发。

这些类应放在 Graven 的 common 层，通过 26.1.2 包适配器连接 Fabric/NeoForge，而不是复制两份 loader 实现。

### 第二组：AntiKB 依赖整体迁入

应保留 `AntiKBMode` 策略抽象和三个模式类，同时迁入：

- 击退包/移动包双队列与回放器；
- 输入键快照与临时覆盖器；
- OpenZen Rotation/RotationHandler 的方向计算和保持 tick；
- 目标选择、AABB 距离、攻击计数和 attack lock 适配器；
- 独立 `ServerTickRateController`；
- `Stuck`、`Scaffold`、`NoFall`、`Backtrack`、`FireballBlink`、`HighJump` 的活跃状态接口。

这些接口应先以 OpenZen 语义落地，再由 Graven 模块适配；直接调用 Graven 现有 `Stuck`、`FightManager` 或 `PacketQueueManager` 会改变释放顺序和状态清理，不能作为第一阶段替代。

### 第三组：InventoryManager 移动整理修正

OpenZen 的整理算法、物品评分和槽位优先级可以保留，但移动整理发送层必须做以下最小改动：

```text
IDLE
  -> CLICK_QUEUED       本 tick 只取消并缓存 CLICK_WINDOW
  -> INPUT_NEUTRAL      至少跨过一个完整客户端 tick，GuiMove 输出零输入
  -> CLICK_RELEASED     单独发送 CLICK_WINDOW
  -> CLOSE_QUEUED       不与点击同 tick 发送 CLOSE_WINDOW
  -> CLOSE_RELEASED     下一安全 tick 单独发送 CLOSE_WINDOW
  -> IDLE               恢复真实按键和 GuiMove
```

具体约束：

- `performInventoryAction()` 产生点击后记录 `actionTick`，禁止同一 tick 的 `onMotion` flush。
- `onMotion` 释放点击前，必须确认已完成一个 `INPUT_NEUTRAL` tick；不能只检查 `isSprinting`。
- `CLICK_WINDOW` 和 `CLOSE_WINDOW` 至少间隔一个客户端 tick，且关闭包不能紧跟点击包在同一 MotionEvent 中发送。
- `GuiMove` 在 `INPUT_NEUTRAL` 阶段返回零前进/横移并暂停 Sprint；下一 tick 读取真实按键恢复。这样 Grim 的 `knownInput.moving()`、sprint 状态和包顺序都处于稳定窗口。
- 若收到拉回、断线、世界切换或服务器容器包，先释放/清空队列，再恢复输入；不能留下 `inventoryOpen` 或 `isPerformingAction`。
- 整理和丢弃继续使用独立计时器；每次只发一个点击动作，避免 MultiActionsC、PacketOrderA 和容器状态竞争。

这项修改只改变 OpenZen 移动整理的发送时序，不改变物品评分、槽位分配、offhand 偏好和垃圾判断，因此能保留原始整理结果，同时消除首次动作的 C/D 窗口。

## 迁移顺序与验证门槛

1. 先迁入包 bypass、输入快照、tick-rate 和双队列基础设施。
2. 再迁入 NoSlow 状态机并对照每个包类型、每个退出路径和按键恢复。
3. 迁入 AntiKB 基类及 NoXZ、JumpReset、Mix，接入独立的 OpenZen 依赖状态接口。
4. 最后迁入 InventoryManager 算法和上述五阶段移动整理发送器。
5. 对每个模块分别验证：开关、断线、拉回、世界切换、受击、屏幕切换、移动输入、冲刺、容器点击顺序和队列 flush。

## 兼容性报告

- 保留：OpenZen NoSlow 的完整状态机设计、AntiKB 三策略架构、InventoryManager 物品排序/评分/槽位行为。
- 更改：InventoryManager 移动整理增加中性输入 tick，点击和关闭包分离一个 tick；原因是修复首次动作的 MultiActionsC/D。
- 不复用：Graven 现有 PacketQueueManager、FightManager、Stuck、RotationManager 不能作为 OpenZen 行为的直接替代；它们可在适配层提供桥接，但不应取代参考实现的状态和依赖。
- 未实现：本次未开始代码迁移，未修改 Graven 功能模块；下一阶段应按上述顺序逐组实现并逐包核验。
