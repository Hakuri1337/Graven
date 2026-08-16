# OpenZen NoSlow 与 Velocity 深入分析

## 范围与结论

参考源码位于 `SkidProjects/OpenZen`。其构建文件表明参考版本是 Forge 1.20.1（Forge 47.4.20），而 Graven 使用 Minecraft 26.1.2 的多加载器 `common` 层。OpenZen 源码树中没有 `Velocity.java`；击退处理由 `combat/AntiKB.java` 及 `combat/antikb/` 下的三个策略类承担。因此，“OpenZen Velocity”在结构上应解释为 AntiKB 击退策略，而不是一个可直接复制的类。

本次代码变更只移除了 Graven 当前 `NoSlow` 的 `GrimC0F` 模式和 `Velocity` 的 `Grim` 模式。普通 NoSlow 模式、Legit/Cancel Velocity、爆炸处理、推力排除和配置框架均保留。

## OpenZen NoSlow 架构

入口是 `shit.zen.modules.impl.movement.NoSlow`，模块内部保留两个顶层模式：`Grim V3` 与 `NoSlow`。默认模式是 `Grim V3`，Bow 默认关闭，食物默认开启，药水和盾牌路径各自有独立开关。状态机将手部交换和临时入站延迟拆开：

```text
SlowdownEvent
  ├─ Grim V3 + Bow=false -> WAITING -> SWAPPING -> USING
  │                          Pong 暂存 -> SWAP_ITEM_WITH_OFFHAND
  │                          装备变化包 -> 恢复 keyUse -> 允许移动
  └─ Grim V3 + Bow=true  -> UseItem 包拦截/延迟
                             2 tick blink -> RELEASE_USE_ITEM
                             交换后恢复原手
```

### 普通 NoSlow 路径

`isNoSlowMode()` 分支直接调用 `handleGrimSlowdown`。弓和弩按 `tickCount % 3` 周期放行减速；可食用物品和药水根据剩余使用刻数及 3 刻周期决定是否减速；`keepSprinting` 强制维持冲刺。这一分支不交换物品栏，也不延迟入站包。

### Grim V3 的食物/药水路径

`handleOffhandSlowdown` 在主手使用食物或药水且副手没有另一个可使用动画时，把状态从 `IDLE` 推到 `WAITING`，保存当前快捷栏槽位并临时释放 `keyUse`。发送 Pong 时，`handleOffhandPacket` 取消原 Pong 并入 `pongQueue`，随后进入 `SWAPPING` 并发送 `SWAP_ITEM_WITH_OFFHAND`。收到副手装备变化（容器槽或 `ClientboundSetEquipmentPacket`）后进入 `USING`，恢复 `keyUse`，并取消减速。

`USING` 连续 5 tick 没有检测到继续使用就自动清理。释放使用包、受击运动包和模块禁用都会走 `resetOffhandState`：先发送暂存 Pong，再反向交换副手，清空队列并从真实鼠标/键盘状态恢复 `keyUse`。

### Grim V3 的弓/弩路径

弓/弩路径以 `didSwapHand`、`pendingUseHand`、`releaseTicksRemaining` 和 `isBlinking` 协同工作。使用包在手部交换窗口被取消；食物/药水使用会排队到下一 tick，并使用 `sendPredictiveDirect` 生成预测序列。交换后启动 1～2 tick blink，blink 期间只缓存可安全延迟的入站包：KeepAlive、Ping、玩家自身的运动包、主容器/副手槽更新和副手装备更新。登录、重生、拉回等边界包会立即结束 blink 并清理队列。blink 结束后发送 `RELEASE_USE_ITEM`，按 `useItemTicks` 保持若干 tick 的释放状态，再恢复真实 `keyUse`。

### OpenZen NoSlow 的依赖与风险

| 组件 | 作用 | 迁移时的关键差异 |
|---|---|---|
| `PacketUtil.sendQueued` | 发送带 bypass 标记的包，使事件回调不重复拦截 | Graven 的 `PacketUtils.sendSilently` 没有 OpenZen 的全局 `queuedPackets` 语义 |
| `sendPredictiveDirect` | 通过 `BlockStatePredictionHandler` 生成序列号 | Graven 26.1.2 的预测 API 和包构造签名必须按当前源码重新核对 |
| `PacketEvent` | 同时承载入站/出站方向 | OpenZen 的 `isIncoming()` 实现返回 `!incoming`，命名与字段语义相反，直接移植会反转方向判断 |
| GLFW/InputConstants | 恢复真实按键状态 | Graven 应集中使用现有输入工具，不能长期覆盖用户按键状态 |
| 入站 blink 队列 | 延后状态包并在连接线程处理 | 必须限定白名单，拉回、登录、重生、断线时必须清空并恢复状态 |

源码还存在几个需要保留并修正的边界：部分设置条件使用了交叉的 `crossbowNoSlow/foodNoSlow` 判断；队列使用原始泛型；包 `handle` 失败时只清空剩余队列；`isBlocking` 直接读取 `mc.hitResult`，没有逻辑旋转命中结果隔离。这些都是行为差异点，不能在迁移时当作无关细节删除。

## OpenZen 的击退实现（对应“Velocity”）

### `AntiKB` 分发层

`AntiKB` 只保存设置和静态旋转目标，把所有事件转发给当前 `AntiKBMode`。它在每个事件入口重复查找策略，并在 `FireballBlink` 或 `HighJump` 启用时暂停策略。这是一个策略对象架构，不是单个 Velocity 状态机。

### `NoXZMode`

`NoXZMode` 是最接近 Grim Velocity 的策略：

1. 收到自身 `ClientboundSetEntityMotionPacket` 后，仅处理水平或正向 Y 击退。空中或没有合法冲刺目标时进入 `isSuspending`，取消击退包并保存 `knockbackPacket`。
2. suspension 期间缓存除聊天、时间、生命等白名单之外的入站包，同时缓存出站移动包；最多等待 12 tick 或落地。
3. 落地且目标仍在 3.7 格 AABB 距离内、玩家仍冲刺时，先发送缓存的移动包，再回放原击退包，然后执行配置次数的攻击。攻击前暂时关闭冲刺，攻击后把水平速度乘以 0.6。
4. `instantAttack` 会把客户端 `serverTickRate` 临时改为 0.5/4.0，形成更短的攻击窗口；失败、拉回、断线和禁用都恢复为 1.0。
5. 收到 `ClientboundPlayerPositionPacket` 视为 flag：释放所有挂起包、清空状态并设置 2 tick 冷却。

这个策略不是简单取消速度，而是“延迟击退、回放移动、在落地窗口攻击、再回放服务器状态”的时序控制。它使用 `mc.hitResult` 或 KillAura 目标作为攻击目标，和 Graven 已采用的 `RotationManager.getHitResult()` 逻辑命中结果仍有语义差异。

### `JumpResetMode`

该模式在收到自身击退后保存包并取消处理：空中延迟最多 20 tick、地面延迟 10 tick。它根据击退向量计算 `atan2(x, -z)` 的方向旋转，可选地锁定旋转并把移动强制转为前进；地面阶段在延迟结束后回放击退包并压入一次跳跃。NoFall/Backtrack 活跃时会立即释放并清除旋转，断线时清空所有状态。

### `MixMode`

该模式把击退处理、方向旋转、KillAura 双攻击、移动键覆盖和实体移动包回放合并。它在非水/非蜘蛛网场景取消自身击退，缓存击退、移动、Ping 和传送包；根据击退方向相对玩家 yaw 设置前后左右键，约 1～3 tick 强制方向，随后读取真实按键恢复。蜘蛛网、液体和断线是显式排除条件。

## 与 Graven 的映射

| OpenZen 参考 | Graven 对应 | 兼容性结论 |
|---|---|---|
| `NoSlow` `ModeSetting` | `NoSlow` `EnumSetting<Mode>` | 可映射设置 DSL，但值名、默认值和 i18n key 不同 |
| `SlowdownEvent` | `SlowdownEvent` | 事件职责相同，注册方式不同 |
| `PacketEvent` 入/出站 | `PacketEvent.Send/Receive` | Graven 已拆分方向，不能复制 OpenZen 的方向布尔逻辑 |
| `PacketUtil.sendQueued` | `PacketUtils.sendSilently` | 仅能映射“绕过本模块回调”，不能假设队列语义相同 |
| `AntiKB.NoXZMode` | `Velocity` | 不是一对一；NoXZ 还依赖 KillAura、serverTickRate、移动包回放和 AABB 目标选择 |
| `AntiKB.JumpResetMode` | `Velocity`/RotationManager | 需要把旋转生命周期接入 Graven RotationManager，不能直接写客户端视角 |
| `AntiKB.MixMode` | `Velocity` + 输入/战斗模块 | 需要额外的按键恢复和模块互斥协议 |

## 本次删除记录

保留：`NoSlow` 的 `Vanilla`、`Jump`、`Grim1_2`、`Grim1_3`；`Velocity` 的 `Cancel`、`Legit`、爆炸处理、Spear Lunge/Wind Charge 排除和输入跳跃重置。

移除：`NoSlow.GrimC0F` 的 C0F 交换状态机、Pong/容器包队列、强制 `keyUse` 生命周期；`Velocity.Grim` 的逻辑命中攻击队列、击退包回放、Grim 日志和攻击/跳跃设置；对应英文和中文 i18n 条目。

未实现：OpenZen 的 NoSlow 或 AntiKB 迁移。本次请求只要求深入分析并删除现有两个模式，后续若要迁移，应先按上述映射逐项核对 26.1.2 的包签名、线程和 RotationManager 生命周期。
