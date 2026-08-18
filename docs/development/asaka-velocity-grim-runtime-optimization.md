# Velocity Grim 历史优化记录

本文记录此前 Graven 对 Asaka Grim Velocity 做过但现已撤销的行为偏离。

历史版本曾加入：

- 首次击退目标锁定；
- 准星切换时中止攻击轮次；
- `PerTick` 仅在实际发送攻击包后递减；
- `OneTime` 在攻击槽被其他模块占用时保留计数。

这些处理可以降低部分环境中的 Hitbox 或空耗计数，但不属于当前 Asaka 源码。完整重迁移已恢复 Asaka 的动态目标和原始计数规则。Graven 与 Asaka 的真正架构差异改由以下依赖适配解决：

- 使用 `Managers.ROTATION.getHitResult()` 映射 Asaka 在 `mc.pick()` 后得到的逻辑目标；
- 恢复 Asaka KeepSprint 的 `Vanilla/Prediction`、攻击减速事件和热栏切换；
- 继续使用 `FightManager` 协调普通攻击与纯发包攻击。

Grim 模式已按下游 Asaka 源码重新迁移。本文列出的优化仍未启用；当前生产行为以
[Asaka GrimVelocity 完整重迁移](asaka-velocity-grim-migration.md) 为准。
