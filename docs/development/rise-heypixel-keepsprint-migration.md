# Rise Heypixel KeepSprint 迁移

## 目标

本次迁移将 Rise 6.9.5 `KillAura` 的 `New Universal Keep Sprint` 嵌入 Graven 的
`KillAura`，设置显示名固定为 `Heypixel KeepSprint`。迁移保留 Rise 的攻击前疾跑
抑制、下一更新周期攻击和跳跃取消时序，不创建独立替代模块。

## 参考状态机

Rise 在一次可攻击尝试中按以下顺序执行：

1. `newUniversalKeepSprint` 开启、存在目标、未命中 `shouldSkipKeepSprint()` 且处于
   `isWithinAttackCooldown()` 时调用 `stopSprinting()`。
2. `groundTicks == 1` 时直接返回 `true`，使本次攻击延后一更新周期。
3. 其他情况下若玩家仍在疾跑，清除玩家疾跑、清除疾跑按键并设置
   `sprintCancelled`，同样延后本次攻击。
4. `stopSprinting()` 返回 `false` 的下一周期执行实际攻击。Rise 的攻击预算/计时器不
   因抑制尝试而消费。
5. 当 `sprintCancelled` 且玩家未疾跑时取消跳跃；玩家恢复疾跑或目标消失时清理该状态。

Graven 当前没有 Rise 的攻击计时器，因此 1.8 模式的 `attacks` CPS 累积值承担攻击
预算，抑制结果不会扣除该值；1.9+ 模式保留原有攻击冷却检查。

## API 与架构映射

| Rise | Graven | 适配 |
|---|---|---|
| `newUniversalKeepSprint` | `KillAura.heypixelKeepSprint` | BoolSetting，显示名 `Heypixel KeepSprint` |
| `sprintCancelled` | `KillAura.sprintCancelled` | KillAura 生命周期内维护 |
| `cqL` 连续落地 tick | `groundTicks` | `PlayerTickEvent.Pre` 中按 `onGround()` 累积 |
| `stopSprinting()` | `stopHeypixelSprinting()` | 清理玩家疾跑及 `keySprint`，返回是否抑制 |
| `shouldSkipKeepSprint()` | `shouldSkipHeypixelKeepSprint()` | 保留 `hurtTime < 8`；本项目没有 Rise 的 GrimSpeed/fastFall 依赖 |
| `isWithinAttackCooldown()` | `isWithinHeypixelAttackCooldown()` | 使用当前目标范围校验，攻击预算/原版冷却仍由 Graven 原流程控制 |
| Rise Jump event | `JumpEvent` + `MixinLivingEntity` | 事件改为可取消；Mixin 在起跳入口发布并在取消时跳过整个跳跃 |

## 冲突协调

- `MixinPlayer` 的独立 `KeepSprint` 攻击后恢复逻辑在 Heypixel 过渡期间跳过，避免
  把 Rise 的“下一 tick 再攻击”变成立即恢复疾跑。
- `AutoSprint` 和 `NoSlow` 在过渡期间不重新写入疾跑状态或按键。
- `SilentRotationManager` 仍可通过 `JumpEvent` 修改跳跃 yaw；取消只影响玩家的
  `jumpFromGround()`，其他生物保持原版逻辑。
- `TargetMode.Multiple` 先收集全部可攻击目标，再执行一次疾跑门控，避免部分攻击
  后才停止疾跑。

## 行为检查

- 1.8 CPS：`SPRINT_SUPPRESSED` 不扣除 `attacks`，下一 tick 继续同一次攻击预算。
- 1.9+：只有原有 `getAttackStrengthScale()` 满足时才尝试攻击，抑制不会伪造攻击。
- `groundTicks == 1`、`hurtTime < 8`、目标超出范围和无有效命中结果均按对应分支处理。
- KillAura 禁用时清理目标、切换索引、疾跑过渡和落地 tick 状态。

## 兼容性报告

**保留：** Rise 的 New Universal Keep Sprint 设置语义、攻击前停止疾跑、疾跑按键
清除、`sprintCancelled` 跳跃取消、下一周期执行攻击、Multiple 原子门控及状态清理。

**更改：** Rise 的 `attackTimer/swingTimer` 映射到 Graven 现有 CPS 累积和 1.9 原版
攻击冷却；Rise 的 `GrimSpeed.fastFall` 在 Graven 中不存在，因此 `shouldSkip` 仅
保留可观测的 `hurtTime < 8` 条件。上述差异是 API/模块不存在导致的适配，不改变
KeepSprint 的抑制顺序。

**移除：** 无。没有删除 Graven 原有独立 `Keep Sprint` 模块。

**未实现：** Rise KillAura 的其他攻击模式、自动格挡和 Watchdog 专属逻辑不属于本次
KeepSprint 功能，未被带入。
