# OpenZen AntiKB 完整等效迁移计划

## 目标与边界

本次目标是让 Graven 的 AntiKB 在输入、包流、状态生命周期、目标选择、攻击节奏、
旋转和异常清理方面与 `SkidProjects/OpenZen` 的 AntiKB 一致。Minecraft 26.1.2 的
包类型和 Graven 的 EventBus 只作为 API 适配层；不改变 OpenZen 的策略条件、状态机
边界或执行顺序。

本阶段只完成调查、映射和实施计划，不修改 AntiKB 源码。

## 参考实现映射

| OpenZen | Graven 对应项 | 当前差异 | 计划 |
|---|---|---|---|
| `AntiKB` 分发器 | `modules/impl/combat/AntiKB` | Graven 缺少 FireballBlink/HighJump 门控和完整事件边界 | 保留分发器，增加依赖状态接口和断线/切模式清理 |
| `AntiKBMode` | `modules/impl/combat/antikb/AntiKBMode` | Graven 只有通用队列，目标/攻击/回放语义不完整 | 保留基类，补充 OpenZen 等价的释放和攻击协议 |
| `NoXZMode` | `NoXZMode` | 地面无目标放行；忽略垂直击退；缺少 hitCounter、sprintBoostCounter、shouldJump；入站回放过早 | 按 OpenZen 条件和顺序逐段恢复 |
| `JumpResetMode` | `JumpResetMode` | 缺少 AIR/GROUND 阶段、NoFall/Backtrack/Scaffold 互斥、真实按键恢复和 RotationHandler 生命周期 | 增加完整阶段状态机 |
| `MixMode` | `MixMode` | 缺少蜘蛛网计数、TeleportEntity、双击、Sprint 时间轴和移动键恢复 | 按 OpenZen 的 `sprintTick` 与 `movementState` 恢复 |
| `ReceivePacketEvent` | `PacketEvent.Receive` | Graven 已拆分方向 | 保持 Receive 方向，不复制 OpenZen 的反向命名语义 |
| `PacketUtil` | `PacketUtils`/`AntiKBMode` 队列 | Graven bypass 只绕过本地 Send 监听 | 只用于出站重放；入站必须通过当前 `PacketListener` 回放 |
| `MotionEvent.Pre` | `PlayerTickEvent.Pre` + `PostMovementPacketEvent` | 当前 NoXZ 在 PlayerTick Pre 立即回放入站包 | 增加延迟回放标记，在移动包阶段之后处理 |
| `mc.hitResult` | `Managers.ROTATION.getHitResult()` | 目标时序不同，可能导致地面目标为空 | 保留 Graven 逻辑命中协议，但增加快照时机并核对回退规则 |

## NoXZ 完整状态机

### 接收击退

1. 只处理本地玩家 ID 的 `ClientboundSetEntityMotionPacket`。
2. 记录水平击退到 `hitCounter`，不因 X/Z 小于阈值而丢弃 Y 击退。
3. 当 `motion.y > 0` 时更新 `sprintBoostCounter`，达到 OpenZen 阈值后触发 `shouldJump`。
4. 地面且冲刺、目标合法、目标在 3.7 格内时，建立 `attackTarget/attacksRemaining`，放行原击退。
5. 其他地面情况进入 `isSuspending`，保存并取消击退包。
6. 空中击退进入 `isSuspending`，保存并取消击退包。

### 挂起期间

- 缓存服务器入站包，保留 OpenZen 白名单。
- 缓存全部 `ServerboundMovePlayerPacket`。
- 位置修正、旋转修正、死亡、断线、世界切换和 Stuck/液体/着火等状态立即释放。
- 禁止 KillAura 与 AntiKB 同时产生交互包。

### 落地/超时释放

严格保持以下顺序：

```text
发送缓存移动包
应用原始击退包
设置延迟入站回放标记
在移动包阶段结束后的 Pre 回调回放其余入站包
检查目标、冲刺和攻击设置
执行攻击序列
恢复 tick rate、旋转和输入状态
```

不能在同一个 `PlayerTickEvent.Pre` 中直接完成全部入站回放，否则会改变 OpenZen 的
物理与服务器预测顺序。

### 攻击协议

- `sprintStateCheck=true` 且当前不冲刺时禁止攻击。
- 攻击失败不得递减 `attacksRemaining`。
- 攻击前临时关闭 Sprint，攻击后按 OpenZen 规则将水平速度乘以 `0.6`。
- `InstantAttack` 继续使用 OpenZen 的 0.5 tick-rate 预加载和 4.0 恢复阶段。

## JumpReset 完整状态机

引入 `IDLE/AIR/GROUND` 阶段：

- 空中受击最多延迟 20 tick，落地立即释放。
- 地面受击延迟 10 tick，释放后设置一次跳跃。
- 旋转或跟随方向开启时保存击退方向，并在指定 tick 数后释放 RotationManager 控制。
- NoFall、Backtrack 或 Scaffold 状态激活时清空队列、旋转和跳跃覆盖。
- 所有键位修改必须从 GLFW 读取真实状态恢复，不能只写 `setDown(false)`。

## Mix 完整状态机

恢复 OpenZen 的以下字段与行为：

- `shouldAttack`、`wasSprinting`、`webHitCount`、`airTicks`；
- `sprintTick`、`movementState`；
- `ClientboundTeleportEntityPacket` 入站缓存；
- 液体、水下和蜘蛛网旁路；
- 受击后 1～3 tick 的方向覆盖和 4～10 tick 的按键恢复；
- `Try Attack` 的双攻击以及 KeepSprint 速度衰减；
- `Movement Override=false` 时的跳跃键时间轴。

## Graven 依赖适配

### 已存在且可复用

- `Managers.ROTATION`：承载逻辑旋转；
- `OpenZenTickRateController`：承载 InstantAttack tick rate；
- `OpenZenInputGate`：统一恢复输入；
- `PacketUtils.sendSilently`：出站包重放；
- `PostMovementPacketEvent`：实现延迟入站回放边界；
- `KillAura.target`、`Scaffold.INSTANCE`、`NoFall.INSTANCE`、`Stuck.INSTANCE`。

### 需要补充的加载器无关协议

- `AntiKBDependencyState`：FireballBlink、HighJump、Backtrack、NoFall、Scaffold 的
  可用状态查询；缺失模块以 `false` 返回，不伪造模块实例。
- `AntiKBInputSnapshot`：保存并恢复前后左右、跳跃、冲刺键的真实状态。
- `AntiKBDeferredReplay`：在 `PostMovementPacketEvent` 或等价移动阶段完成入站回放。
- `AntiKBAttackResult`：攻击成功/失败结果，防止失败消耗攻击次数。

## 实施顺序

1. 先修复 `AntiKBMode` 的攻击返回值、输入快照和延迟入站回放协议。
2. 按 OpenZen 原顺序完整恢复 NoXZ，优先验证地面、空中、垂直击退和无目标情况。
3. 恢复 JumpReset 的阶段、旋转、依赖互斥和按键生命周期。
4. 恢复 Mix 的攻击、蜘蛛网、TeleportEntity、方向覆盖和键位恢复。
5. 完善 AntiKB 分发器的事件优先级、模式切换、断线、重生和世界切换清理。
6. 最后再接入 KillAura 的交互包门控，避免在 AntiKB 回放期间改变包顺序。

## 验证矩阵

| 场景 | 观察项 |
|---|---|
| 地面击退、无目标 | 是否取消击退并按时回放 |
| 地面击退、有目标、冲刺 | 放行击退、攻击次数、Sprint 状态 |
| 空中水平击退 | 移动包/击退包/其他入站包顺序 |
| 仅 Y 击退 | 是否进入正确状态机 |
| 位置/旋转修正 | 是否先清队列并恢复状态 |
| InstantAttack | tick rate、攻击阶段和结束清理 |
| JumpReset 地面/空中 | 10/20 tick、跳跃和旋转恢复 |
| Mix 蜘蛛网/液体 | 是否旁路并恢复键位 |
| KillAura 同时启用 | 是否出现 PacketOrderF/Simulation |
| 断线、重生、切世界、禁用 | 队列、旋转、tick rate、按键是否全部恢复 |

## 兼容性报告

- **保留：** OpenZen 的模块边界、事件顺序、状态机、包白名单、攻击条件、延迟和清理路径。
- **更改：** 仅将 OpenZen 的事件、包类、旋转和输入 API 映射到 Graven 26.1.2 API。
- **移除：** 不移除任何 OpenZen 策略行为；不存在的依赖只通过等价状态协议接入。
- **未实现：** 本文阶段尚未修改 AntiKB 源码，完整实现将在计划确认后执行。
