# InvManager 整理/丢弃计时与 Grim MultiActionsC 优化

## Grim 判定依据

参考源码位于 `SkidProjects/GrimAC`，固定提交为
`0a18c770c0dd20e4d2aaf4253adabd97841b5bf1`。`MultiActionsC` 在收到
`CLICK_WINDOW` 时检查：服务器未在本 tick 主动打开背包，并且玩家处于冲刺、旧版本潜行
或有移动输入；满足任一条件即记录并在启用修改时取消该点击。

## 原实现问题

Graven `InvManager` 原先用同一个静态 `TimerUtils timer` 处理护甲、快捷栏/副手整理和
垃圾丢弃。`Inventory Only` 打开玩家背包时没有检查 `mc.player.isMoving()`，所以移动输入
持续存在时仍会调用 `ClickSlotUtils`，向服务器发送 `CLICK_WINDOW`，直接命中
`MultiActionsC`。

## 修改

- 新增 `organizeTimer` 与 `throwTimer`，整理动作和垃圾丢弃拥有独立的计时状态。
- 每个 tick 分别生成整理 delay 与丢弃 delay；原设置 `Min Delay`、`Delay`、随机范围和
  动作顺序保持不变。
- 允许移动输入进入自动点击逻辑；`InvManager` 的 `INPUT_NEUTRAL -> CLICK_RELEASED ->
  CLOSE_RELEASED` 状态机负责跨 tick 捕获和释放容器包。
- `swapItem`、`swapOffHand`、护甲和副手整理只使用 `organizeTimer`；`throwItem` 与垃圾
  扫描只使用 `throwTimer`。
- 未修改 `ClickSlotUtils`、背包槽位映射、物品评分、配置键或 Stealer 协作逻辑。

## 兼容性报告

- **保留：** 原 InvManager 的整理优先级、随机 delay 公式、所有设置、槽位和物品判断、
  `inventoryOpen` 生命周期以及非玩家背包容器保护。
- **更改：** 整理/丢弃计时器拆分；移除移动时的自动点击早退，改由输入中性阶段、点击包
  延迟和关闭包延迟隔离 Grim MultiActionsC/D 的检测窗口。
- **移除：** 无。
- **未实现：** 无。
