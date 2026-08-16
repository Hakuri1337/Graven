# AutoMLG Grim AimModulo360 优化记录

## 参考源码

本次分析使用 `SkidProjects/GrimAC` 的官方仓库浅克隆，远端为
`https://github.com/GrimAnticheat/Grim.git`，源码固定在提交
`0a18c770c0dd20e4d2aaf4253adabd97841b5bf1`。

Grim 的 `AimModulo360` 位于
`common/src/main/java/ac/grim/grimac/checks/impl/aim/AimModulo360.java`，判定条件是：

```java
player.yaw < 360 && player.yaw > -360
    && Math.abs(rotationUpdate.getDeltaXRot()) > 320
    && Math.abs(lastDeltaYaw) < 30
```

它针对的是把连续旋转角度强制模 360 后，在 `179 -> -179` 或 `-179 -> 179`
边界发送约 `358` 度单包跳变的客户端行为。Grim 在该判定中使用原始包 yaw 差值，
不会把这类差值再次包络到 `[-180, 180]`。

## AutoMLG 根因

AutoMLG 的落水方向由 `rotationFromDeltas` 计算，原实现使用
`Mth.wrapDegrees(yaw)`。射线方向在数学上正确，但当玩家上一发送 yaw 位于边界另一侧
时，等价方向会被编码为相反的 `[-180, 180]` 表示。随后 RotationManager 将此值用于
Silent/Snap 发包，产生超过 `320` 度的单包差值，满足 Grim 的检测条件。

## 修改范围

只在 `AutoMLG` 增加 `keepYawContinuous` 表示层适配：

1. 以 `Managers.ROTATION.getLastRotation().getYaw()` 作为上一发送 yaw；无效时回退到
   `mc.player.getYRot()`。
2. 用 `referenceYaw + Mth.wrapDegrees(targetYaw - referenceYaw)` 选择与目标方向等价、
   且距离上一发送 yaw 最近的表示。
3. 在 `setTargetRotation` 和 `useItem` 两个发包入口使用同一表示，保证旋转包和物品使用包
   的 yaw 一致。
4. pitch 仍限制在 `[-90, 90]`；射线、触发距离、落地预测、物品栏切换、恢复状态机、
   冷却计数和原有优先级均未改变。

## 行为验证

| 场景 | 修复前编码 | 修复后编码 | 实际视线 |
|---|---:|---:|---|
| 上一 yaw `179`，目标 `-179` | `-179`，差值 `-358` | `181`，差值 `+2` | 相同 |
| 上一 yaw `-179`，目标 `179` | `179`，差值 `+358` | `-181`，差值 `-2` | 相同 |
| 普通目标方向 | 原始等价角 | 最近等价角 | 不变 |

## 兼容性报告

- **保留：** AutoMLG 原有状态机、设置名和默认值、触发条件、预测模型、射线检查、
  水桶放置/回收、栏位恢复、RotationManager 优先级和 Silent/Snap 支持。
- **更改：** 仅更改目标 yaw 的连续数值表示，避免 Grim AimModulo360 的模 360 边界误报。
- **移除：** 无。
- **未实现：** 无。Grim 源码已克隆并用于逐条件核对。

