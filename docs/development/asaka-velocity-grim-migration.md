# Asaka GrimVelocity 完整重迁移

## 状态与基准

本次迁移以
`SkidProjects/Asaka-26.2x/common/src/main/java/asaka/lol/client/modules/impl/combat/Velocity.java`
为唯一行为基准。目标环境为 Minecraft 26.1.2、Java 25；参考环境为 Minecraft 26.2。

权威源文件 SHA-256 为
`29283A2DAE8238321D84D740AF79A9832D57F42F92527F7FEB22329D9D268E4D`。
运行 Jar 中同名混淆类及用户目录下其他版本配置不参与本次迁移定义，避免把不同分支的
Alink/Prediction 状态机混入这份源码的 GrimReduce 实现。

仓库提供 `scripts/verify_asaka_velocity_port.ps1`。该脚本会将权威源码执行确定性的根包替换，
并对 `Velocity` 及五个重新实现的专用依赖逐字比较；任何控制流、默认值、异常处理、注释或
调用顺序漂移都会返回非零状态。

当前 `Velocity` 已恢复 Asaka 的 `Cancel`、`Legit`、`Grim` 三种模式。Grim 的字段、设置、包选择、状态转换、攻击计数、回放时机、异常处理和清理策略均按参考源码保留。

## 依赖映射

| Asaka 源对象 | Graven 迁移对象 | 处理 |
|---|---|---|
| `modules.impl.combat.Velocity` | `modules.impl.combat.Velocity` | 保留 Combat 模块边界；Grim 主体逐分支迁移 |
| `utils.combat.FightManager` | `utils.asaka.grimvelocity.FightManager` | 新增完整专用副本，不复用结构不同的通用类 |
| `utils.player.ChatUtils` | `utils.asaka.grimvelocity.ChatUtils` | 新增完整专用副本；仅适配 26.1.2 GUI getter 和 Graven Accessor 名称 |
| `TimerUtils` | `utils.asaka.grimvelocity.TimerUtils` | 新增完整专用副本，仅替换根包名 |
| `PlayerUtils` | `utils.asaka.grimvelocity.PlayerUtils` | 新增完整专用副本，仅替换根包名和 `Constants` 引用 |
| `EnchantmentUtils` | `utils.asaka.grimvelocity.EnchantmentUtils` | 新增完整专用副本，仅替换根包名 |
| `PacketEvent`、`PlayerTickEvent`、`KeyboardInputEvent`、`GameLeftEvent` | Graven 同名事件 | 类型、取消模型和分发时机相同 |
| `Module`、Setting DSL、EventBus | Graven 同名基础设施 | Velocity 使用到的接口和优先级排序相同 |
| `MixinConnection` | Graven 同名 Mixin | 收发事件触发点和取消/替换路径相同 |
| `MixinMultiPlayerGameMode` | Graven 同名 Mixin | 普通攻击成功进入原版路径前标记专用 `FightManager` |
| `MixinEntity`、`MixinFlowingFluid`、`MixinLocalPlayer` | Graven 同名 Mixin | 三个推力取消注入点及条件保持相同 |
| Asaka 聊天前缀渲染链 | Graven `MixinChatComponent` | 在 Graven 前缀处理后继续执行专用 `ChatUtils.applyAnimatedPrefix()` |
| `mc.hitResult` | `mc.hitResult` | 严格保留直接读取，不使用 `RotationManager.getHitResult()` |

没有新增 Maven、Fabric 或 NeoForge 外部依赖。

## Grim 状态机

### 收到本玩家速度包

1. `grimLag == true` 时复位该标记、调用 `clearGrim()`，并放行当前速度包。
2. `grimAlink == true` 时把当前速度包加入 FIFO 并取消原处理。
3. 当前 `mc.hitResult` 命中任意 `Player` 时，将 `grimAttackQueue` 设为 `Attacks`，启用 `grimAlink`，放行首个速度包。
4. 未命中玩家、当前不在地面且两格内没有其他存活玩家时，启用 `grimAlink`，随后缓存并取消当前速度包。
5. 其他情况放行速度包且不进入 Grim 阶段。

`grimAlink` 期间只缓存本玩家 `ClientboundSetEntityMotionPacket` 和 `ClientboundPingPacket`。其他实体的速度包会在实体 ID 检查处直接返回。

### 每 tick 攻击

- `Jump Reset` 原样比较 `mc.player.tickCount == Jump Tick`。它不是“受击后经过 N tick”的计时器。
- `grimAttackQueue <= 0` 且 FIFO 非空时，空中继续等待；落地后同步回放全部包、重新装填攻击计数，并在当前事件中返回。
- 攻击阶段每 tick 重新读取 `mc.hitResult`，不锁定首次目标。
- 未命中存活玩家时不递减计数，持续等待目标。
- `PerTick` 每 tick 尝试占用一次攻击槽；无论槽位是否已被普通攻击占用，计数都会递减。
- `OneTime` 在同一 tick 循环消耗全部计数；`FightManager` 锁使循环中最多只有一次真实纯发包攻击。
- 纯发包攻击顺序固定为 `ServerboundAttackPacket` 后 `ServerboundSwingPacket`。
- 成功纯发包攻击后，本地水平速度乘以 `0.6`，垂直速度不变。

### 修正、禁用与离开世界

- 收到 `ClientboundPlayerPositionPacket` 时先同步回放 FIFO，再设置 `grimLag = true`；位置修正包本身继续处理。
- 模块禁用调用 `clearGrim()`：回放 FIFO、关闭 `grimAlink`，但不清零 `grimAttackQueue` 或 `grimLag`。
- 模块重新启用调用 `resetGrim()`，此时才清空队列、计数和 `grimLag`。
- `GameLeftEvent` 调用 `resetGrim()`，直接丢弃尚未回放的包。
- 单个回放包抛出 `Exception` 时打印堆栈并继续回放后续包。

## 26.1.2 机械适配

参考 `ChatUtils` 使用 26.2 的 `mc.gui.hud.getChat()` 和 `asaka$...` Accessor。Graven 26.1.2 对应为 `mc.gui.getChat()` 和 `graven$...`。这两处仅是目标版本与 Mixin 前缀适配，消息构建、线程切换、哈希替换和动画前缀逻辑没有改写。

`ClientboundExplodePacket`、`ClientboundSetEntityMotionPacket`、`ClientboundPingPacket`、`ClientboundPlayerPositionPacket`、`ServerboundAttackPacket` 和 `ServerboundSwingPacket` 已直接核对 `reference/vanilla-26.1.2/`。

## 验证矩阵

| 场景 | 参考结果 |
|---|---|
| 首个速度包且准星命中玩家 | 放行包，装填攻击计数并进入 `grimAlink` |
| 空中、无准星玩家且两格内无人 | 取消并缓存首个速度包 |
| `grimAlink` 中收到 Ping | 取消并按 FIFO 缓存 |
| 攻击计数为零且仍在空中 | 保留 FIFO，等待落地 |
| 落地且 FIFO 非空 | 同步回放，重新装填计数，当前 tick 不攻击 |
| 普通攻击先占用当前 tick | Grim 不发攻击，但 `PerTick` 仍消耗一次计数 |
| `OneTime` 且 `Attacks > 1` | 只真实攻击一次，循环耗尽全部计数 |
| 收到位置修正 | 回放 FIFO，下一次本玩家速度包仅清除 `grimLag` 并放行 |
| 禁用 | 回放 FIFO，不主动清零攻击计数和 `grimLag` |
| 离开世界 | 丢弃 FIFO 并清零全部 Grim 状态 |

## 兼容性报告

- **保留：**Asaka 的全部 Grim Setting、四个状态字段、FIFO 类型、包范围、条件顺序、直接准星读取、攻击锁、发包顺序、`0.6` 减速、位置修正处理和生命周期清理。
- **更改：**仅根包名、Minecraft 26.1.2 聊天 GUI getter 和 Mixin Accessor 前缀。
- **移除：**旧迁移的 `RotationManager.getHitResult()` 映射、目标锁定、命中变化中止、成功攻击后才递减等非 Asaka 行为。
- **未实现：**无。
