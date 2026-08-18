# OpenZen AntiKB、NoSlow 与 InventoryManager 实现记录

> 历史状态：`AntiKB` 与其三种策略已于 2026-08-18 从 Graven 移除。本文保留用于追溯此前迁移；其中 NoSlow 和 InventoryManager 的描述不受此模块删除影响。

## 实现范围

本次实现沿用 OpenZen 的模块边界与事件顺序，并针对 Minecraft 26.1.2 的当前包 API 做了适配：

| OpenZen | Graven 实现 |
|---|---|
| `AntiKB` 分发器 | `modules/impl/combat/AntiKB` |
| `AntiKBMode` | `modules/impl/combat/antikb/AntiKBMode` |
| `NoXZMode` | `modules/impl/combat/antikb/NoXZMode` |
| `JumpResetMode` | `modules/impl/combat/antikb/JumpResetMode` |
| `MixMode` | `modules/impl/combat/antikb/MixMode` |
| `PacketUtil.sendQueued` | `utils/openzen/OpenZenPacketBypass` |
| NoSlow 入站 blink | `utils/openzen/OpenZenInboundBlinkQueue` |
| 输入状态恢复 | `utils/openzen/OpenZenInputGate` |
| `serverTickRate` | `managers/impl/OpenZenTickRateController` |

## InventoryManager 时序

整理算法、物品评分、offhand 偏好、槽位优先级和垃圾判定仍由原 `InvManager` 执行。自动点击通过 `automationCapture` 捕获后进入以下状态：

```text
IDLE -> INPUT_NEUTRAL -> CLICK_RELEASED -> CLOSE_RELEASED -> IDLE
```

产生点击的 tick 不 flush；下一完整客户端 tick 发送 `CLICK_WINDOW`；再下一 tick 才发送 `CLOSE_WINDOW`；最后一个 tick 恢复物理按键。`OpenZenInputGate` 在中性阶段把移动和冲刺输入置零，因此不会在同一 Motion 事件同时出现移动、点击和关闭包。

服务器容器打开/内容同步、断线、世界切换和模块禁用都会清空 pending click 并调用统一输入恢复。

## 兼容性报告

- **历史保留：** OpenZen AntiKB 三策略边界；NoSlow 食物/药水与弓/弩状态机；入站包白名单；InventoryManager 原评分与整理顺序。AntiKB 当前已删除。
- **更改：** `ServerboundContainerClickPacket` 与 `ServerboundContainerClosePacket` 强制跨 tick 释放，修复首次整理触发 Grim MultiActionsC/D 的时序缺陷；使用 26.1.2 的 `movement()`、`id()`、`ItemUseAnimation` 和预测序列 API。
- **移除：** 未移除参考行为；仅移除 OpenZen 专属事件类型、日志和加载器调用，改由 Graven `EventBus`/`PacketEvent`/`PlayerTickEvent` 承载。
- **未实现：** OpenZen 中不存在于 Graven 的 Backtrack、FireballBlink、HighJump 专属联动没有伪造依赖；策略在对应状态不可用时按 OpenZen 的清理路径释放队列。

## 验证

`C:\Users\27881\Documents\Graven\gradlew.bat :common:compileJava --no-daemon` 已通过（Java 25，Minecraft 26.1.2）。
