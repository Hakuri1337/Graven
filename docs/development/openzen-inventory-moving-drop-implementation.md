# OpenZen 边走边丢迁移实现

## 目标

当 `InvManager` 的 `Inventory Only` 关闭、`GUI Move` 开启时，允许玩家持续移动，
同时执行整理和垃圾丢弃。物品评分、槽位优先级、随机延迟和丢弃顺序保持 Graven 原实现。

## 参考行为

OpenZen 在移动输入存在时仍调用 `performInventoryAction()`，然后由包处理器取消并暂存
`CLICK_WINDOW`。动作状态依次经过：

```text
IDLE -> INPUT_NEUTRAL -> CLICK_RELEASED -> CLOSE_RELEASED -> IDLE
```

`INPUT_NEUTRAL` 阶段把移动、跳跃和冲刺输入置零；下一 tick 释放点击包；再下一 tick
发送 `CLOSE_WINDOW`；最后恢复真实按键状态。这样本地玩家可以继续走动，而服务器收到
的点击和关闭包不会与移动输入处于同一客户端 tick。

## Graven 实现

- 移除 `InvManager.onTick` 中 `mc.player.isMoving()` 的自动点击早退。
- 保留 `automationCapture` 对 `ServerboundContainerClickPacket` 的捕获。
- 保留 `OpenZenInputGate` 的中性输入和冲刺抑制。
- 保留 `advanceActionState()` 的跨 tick 点击/关闭包发送顺序。
- 保留外部容器、Stealer、Scaffold、配置校验和模块禁用时的清理路径。
- 整理与垃圾丢弃继续使用独立的 `organizeTimer`、`throwTimer`。

## Grim 时序依据

Grim `MultiActionsC` 检查移动输入、冲刺状态和 `CLICK_WINDOW` 的同 tick 关系；
`MultiActionsD` 检查 `CLOSE_WINDOW` 的同 tick 关系。Graven 不通过静默发送绕过服务器，
而是延续 OpenZen 的包重排和输入门控，使两个包分别落在独立 tick。

## 兼容性报告

- **保留：** OpenZen 的动作状态机、输入恢复、包捕获和跨 tick 释放顺序；Graven 原有
  物品整理、评分、延迟和配置结构。
- **更改：** 允许移动输入进入 `onTick`，由状态机负责反作弊时序隔离。
- **移除：** 移动时直接返回的保护分支。
- **未实现：** 无。
