# Asaka Velocity Grim 完整迁移

## 状态

本迁移已经重新启用。生产实现位于
`common/src/main/java/tech/hakuri/graven/modules/impl/movement/Velocity.java`，参考实现位于
`SkidProjects/Asaka-26.2x/common/src/main/java/asaka/lol/client/modules/impl/combat/Velocity.java`。

迁移目标是保留 Asaka 的包选择、状态转换、攻击计数、包顺序和错误恢复，同时补齐其依赖的攻击减速与逻辑射线语义。此前 Graven 增加的目标锁定和“仅实际攻击后递减计数”并非参考行为，本次没有继续保留。

## 版本与边界

| 项目 | 版本 | 用途 |
|---|---:|---|
| Asaka | Minecraft 26.2 | 唯一行为参考 |
| Graven | Minecraft 26.1.2 / NeoForm 26.1.2-1 | 目标运行环境 |
| Java | 25 | 两侧共同运行版本 |

当前实现只使用 Minecraft、Java 标准库和 Graven 本地 API，不新增 Maven、Fabric 或 NeoForge 依赖。共享实现继续位于 `common/`。

## 依赖映射

| Asaka 对象 | Graven 对应项 | 迁移结果 |
|---|---|---|
| `Velocity.Mode.Grim` | 现有 `Velocity.Mode` | 恢复 `Grim`，保留 `Cancel`、`Legit` 和默认值 |
| `GrimMode.PerTick/OneTime` | `EnumSetting<GrimMode>` | 名称、默认值和分支顺序一致 |
| 五个 Grim Setting | Graven Setting DSL | 名称、范围、步长和显示条件一致 |
| `grimPacketQueue` | `ConcurrentLinkedQueue<Packet<?>>` | 保留入站击退/Ping FIFO 回放 |
| `FightManager` | `tech.hakuri.graven.utils.combat.FightManager` | 已存在；攻击槽、纯发包攻击和普通攻击标记一致 |
| `MixinMultiPlayerGameMode` | Graven 同名 Mixin | 已存在；未取消攻击时调用 `markVanillaAttack()` |
| `KeepSprint.Mode` | Graven `KeepSprint` | 恢复 `Vanilla/Prediction` 和模式信息显示 |
| `AttackSlowDownEvent` | Graven 同名事件与 `MixinPlayer` | 恢复 Asaka 对原版攻击减速的取消/预测处理 |
| 攻击前热栏切换 | `PacketUtils.sendSilently` | 恢复随机临时槽位和原槽位两次切换 |
| Asaka `mc.pick()` 后的 `mc.hitResult` | `Managers.ROTATION.getHitResult()` | 适配 Graven 独立逻辑射线架构，保持服务端旋转目标语义 |
| 入站包事件 | `PacketEvent.Receive` | Velocity 以普通优先级先取消包，统一 PacketQueueManager 在 `-1000` 不会重复收包 |
| 包回放 | `Packet.handle(ClientPacketListener)` | 绕过 PacketEvent 再入，保持参考 FIFO 和处理顺序 |
| `ChatUtils`/`TimerUtils`/排除工具 | Graven 同名实现 | 直接复用 |

## Grim 状态机

### 首个本地击退

1. `grimLag` 为真时清除标记并放行当前击退。
2. `grimAlink` 已启用时缓存并取消后续击退。
3. 逻辑射线命中存活玩家时设置攻击计数并放行首个击退。
4. 未命中玩家、玩家在空中且两格内没有其他存活玩家时，进入缓存状态并取消当前击退。
5. 其他情况不修改击退。

### 缓存与回放

`grimAlink` 期间缓存本地 `ClientboundSetEntityMotionPacket` 和 `ClientboundPingPacket`。Ping 的处理会产生 Pong，因此缓存 Ping 会让服务端的交易确认与延迟击退保持相同顺序。

攻击计数耗尽后：

- 队列为空时退出 `grimAlink`；
- 队列非空且仍在空中时继续等待；
- 落地后依次回放全部包，再建立一轮新的攻击计数。

收到 `ClientboundPlayerPositionPacket` 时先回放队列，再设置 `grimLag`。下一次本地击退按参考实现放行。

### 攻击与速度

`FightManager.attackByPacket()` 按以下顺序发送：

1. `ServerboundAttackPacket`
2. `ServerboundSwingPacket`

纯发包攻击不会调用客户端 `Player.attack()`，因此 Velocity 在成功占用攻击槽后手动把本地水平速度乘以 `0.6`。普通攻击则由 `MixinMultiPlayerGameMode` 标记，Velocity 不重复应用减速。

`PerTick` 每个客户端 Tick 最多尝试一次攻击。`OneTime` 保留参考实现的循环与攻击锁行为：首次成功攻击后锁定当前 Tick，但剩余计数仍在同一循环中消耗。

## 旋转兼容

Asaka 的 `RotationManager.smooth()` 总会调用 `mc.pick(1.0f)`，所以 Velocity 读取的 `mc.hitResult` 已包含托管旋转。Graven 为避免静默旋转污染可见准星，将结果保存为 `rotationHitResult`，并通过 `getHitResult()` 暴露。

因此 Graven 的 Grim 分支必须读取 `Managers.ROTATION.getHitResult()`。这不是目标锁定或算法替换，而是两个旋转架构之间的等价 API 映射；直接读取 `mc.hitResult` 会把摄像机目标当成服务端旋转目标，可能触发 Hitbox。

## KeepSprint 兼容

Asaka Grim 的攻击槽逻辑默认假设普通攻击是否已经应用 `0.6` 由 KeepSprint 控制。为避免 Graven 原有简化 KeepSprint 改变 Simulation 输入，本次恢复：

- `Vanilla`：取消 `causeExtraKnockback`，攻击前静默切换随机热栏槽并立即切回；
- `Prediction`：不取消原版攻击减速，只在减速事件中恢复疾跑；
- `slowdown`、`groundOnly`、`prediction`、`reachOnly` 的原有后处理公式与条件；
- `PlayerTickEvent.Pre/Post` 的预测窗口。

Graven 26.1.2 的 `Player.causeExtraKnockback` 方法签名与 Asaka 26.2 不同，Mixin 使用当前参考源码中的三个参数签名，事件语义保持一致。

## 明确保留的参考行为

以下行为虽然看起来可以改进，但属于 Asaka 当前源码，迁移中不得静默改变：

- `Jump Reset` 使用 `player.tickCount == Jump Tick`；
- `PerTick` 在攻击槽已占用时仍递减计数；
- `OneTime` 在同一 Tick 消耗全部计数，但攻击锁只允许首次纯发包攻击；
- 攻击阶段每 Tick重新读取逻辑射线目标，不锁定首次目标；
- `clearGrim()` 回放队列但不清零攻击计数或 `grimLag`；
- 两格检查把所有其他存活玩家视为敌人；
- No Water/Entity/Block Push 的值在 Grim 模式仍由既有 Mixin 使用。

若后续要修正这些行为，需要单独建立偏离 Asaka 的优化任务，并逐项记录兼容性影响。

## 配置与 i18n

- 模块 ID 仍为 `Velocity`，旧 Graven 配置中的 `Cancel/Legit` 可继续读取。
- 历史配置中的 `Grim` 值恢复为可识别枚举。
- KeepSprint 新增 `Mode` 时默认 `Vanilla`，已有减速和条件设置不改名。
- `en_us.json` 与 `zh_cn.json` 包含模块、Setting 和枚举值的完整翻译。

## 验证矩阵

| 场景 | 预期结果 |
|---|---|
| 首个击退且逻辑射线命中玩家 | 放行击退，建立攻击计数 |
| 首个击退且空中无附近玩家 | 缓存并取消击退 |
| `grimAlink` 中收到本地击退/Ping | FIFO 缓存并取消原处理 |
| 攻击计数耗尽但未落地 | 不回放 |
| 落地且队列非空 | 回放队列，下一 Tick 开始新攻击轮次 |
| 收到位置修正 | 回放队列并令下一次击退跳过 Grim |
| 同 Tick 已有普通攻击 | Velocity 不发送纯发包攻击 |
| KeepSprint Vanilla | 取消原版攻击减速并执行双热栏切换 |
| KeepSprint Prediction | 保留原版减速并恢复疾跑 |
| Silent/Snap 托管旋转 | Grim 使用逻辑射线目标，而非可见准星目标 |
| 模块禁用/离开世界 | 回放或清空缓存并复位状态 |
| Fabric/NeoForge | 共享实现均能编译和打包 |

## 兼容性报告

- **保留：**Asaka 的包类型、FIFO 顺序、攻击/挥手顺序、状态字段、Setting、计数推进和 `0.6` 水平减速。
- **更改：**目标读取改用 Graven 的逻辑射线 API；异常改用项目 Logger；离开世界额外复位 `FightManager`。这些变化分别用于架构等价、错误可诊断和跨世界清理。
- **移除：**此前 Graven 的目标锁定、目标变化中止和“仅成功攻击后递减”优化。
- **未实现：**无。
