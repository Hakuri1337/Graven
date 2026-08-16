# BMWClient Clip 与 FlightDelayTrigger 迁移

## 迁移范围

本次迁移以 `SkidProjects/BMWClient` 7.6.3-preview 和附件 `bjdphase.js` 为参考来源，在 Graven 中实现：

- BMWClient `ModuleClip` 的 `Fancy` 与 `Old` 两种模式。
- `FlightDelayTrigger` 的飞行能力上升沿检测和 2/3 Tick 模块启用序列。
- BMWClient `Fly` 映射为 Graven `Flight`，BMWClient `Freeze` 映射为完整迁移后的 Graven `Freeze`。

## 映射表

| 参考项 | Graven 对应项 | 适配说明 |
|---|---|---|
| `ModuleClip` | `movement.Clip` | 保留两个模式、输入判定、位置搜索、提示与冷却。 |
| `ModuleClip.Fancy.repeatable` | `ClientTickEvent.Pre` | BMW `tickHandler` 实际监听客户端 Tick 头部；Graven 使用等价事件。 |
| `waitTicks(5)` | `cooldownTicks` | 仅在得到移动方向后暂停后续五个客户端 Tick；提前返回不进入冷却。 |
| `BlockPos.canStandOn()` | `isFaceSturdy(..., Direction.UP)` | 对应参考实现的中心支撑面语义。 |
| `ModuleFly.running` | `Flight.INSTANCE.isEnabled()` | Graven 模块事件仅在世界内使用，启用状态是对应运行门控。 |
| `OverlayRenderEvent` | `Render2DEvent.HUD` | 继续使用 Minecraft 字体绘制 Unicode 上下方向提示。 |
| `Fly.enabled = true` | `Flight.INSTANCE.setEnabled(true)` | 保持先启用飞行，再启用 Clip 的顺序。 |
| `Clip.enabled = true` | `Clip.INSTANCE.setEnabled(true)` | `Old` 立即位移并关闭，`Fancy` 进入持续扫描。 |
| `Freeze.enabled = true` | `Freeze.INSTANCE.setEnabled(true)` | 第 3 Tick 启用独立 Freeze，不再使用行为不等价的 Stuck。 |
| `playerTick` | `PlayerTickEvent.Pre` | 注入位置均位于本地玩家调用父类 Tick 之前。 |

## 行为约束

`FlightDelayTrigger` 启用时以当前 `abilities.flying` 初始化 `prevFlying`。只有后续 `false -> true` 上升沿会布防；触发 Tick 自身计为第 1 Tick，第 2 Tick 开启 `Flight` 和 `Clip`，第 3 Tick 开启 `Freeze` 并解除布防。飞行在序列完成前下降会取消剩余动作，但不会关闭已经启用的模块。禁用触发器也只清理自身状态，不回滚三个目标模块。

`Clip.Fancy` 每次扫描先计算上、下可穿越方向。玩家水平碰撞时根据前后左右键选择水平方向；否则潜行选择向下、跳跃选择向上。搜索必须先经过至少一个不可站立/非双空气位置，再接受最近的双空气目标。`Requires Stand On` 对向上穿越无效，与参考实现一致。

`Clip.Old` 按玩家朝向应用水平偏移和垂直偏移，可选清零速度，并在一次位移后关闭模块。

## 架构差异

BMWClient 通过嵌套 `ChoiceConfigurable` 允许两个模式各自拥有同名 Setting。Graven Setting 为模块级扁平集合，为避免配置序列化键冲突，Old 的两个偏移设置命名为 `Old Horizontal` 和 `Old Vertical`；数值范围、默认值和运行行为保持不变。

BMWClient 提供 `Misc` 分类，Graven 只有 `Combat`、`Player`、`Movement`、`Render`，因此独立触发器归入 `Movement`。该分类变化不影响运行逻辑。

## 兼容性报告

- **保留：** 两种 Clip 模式、全部默认值和范围、墙体后最近空位搜索、中心支撑检查、输入优先级、方向提示、五 Tick 冷却、飞行上升沿状态机、模块启用顺序、下降沿取消和禁用清理语义。
- **更改：** `Fly` 适配为 `Flight`；`Freeze` 由早期的 Stuck 近似映射替换为独立完整模块；Old 设置名称增加前缀；触发器分类改为 `Movement`。这些变化由目标项目现有 API 和分类模型决定。
- **移除：** JavaScript 中针对旧映射和 Forge 的飞行字段异常回退；Graven 26.1.2 已核验 `Player#getAbilities().flying`。
- **未实现：** 无。
