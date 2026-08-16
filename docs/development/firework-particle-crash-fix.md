# 烟花粒子崩溃修复

## 根因

当 `NoRender.fireworks` 开启时，旧的 `MixinParticleManager` 在
`ParticleEngine.createParticle` 入口把 `ParticleTypes.FIREWORK` 返回为 `null`。
26.1.2 原版 `FireworkParticles.Starter.createParticle` 会将该返回值转换为
`SparkParticle`，随后调用 `setTrail`、`setTwinkle`、`setAlpha` 和 `setColor`，因此在烟花爆炸的第一个粒子 tick 抛出 `NullPointerException`。

## 修复

- `MixinParticleManager` 继续取消 `FLASH`，但不再取消 `FIREWORK` 的创建。
- 新增 `MixinFireworkSparkParticle`，在 `SparkParticle.extract` 阶段根据
  `NoRender.fireworks` 跳过绘制。这样原版 Starter 始终得到有效 SparkParticle，
  同时烟花仍然不可见。
- 新 Mixin 已加入 `common/src/main/resources/graven.mixins.json`，不引入 Fabric 或 NeoForge 专有 API。

## 验证

基线和修改后均通过 `:common:compileJava --no-daemon --console=plain`。回滚副本恢复后的 SHA-256 与基线一致；详细命令、输入、输出和状态见 `migration-artifacts/firework-particle-crash-VERIFICATION.txt`。

## 兼容性报告

- **保留：** NoRender 的烟花隐藏、Flash 隐藏、其他粒子过滤和原版烟花粒子生命周期。
- **更改：** FIREWORK 从创建入口过滤改为 SparkParticle 提取阶段过滤。
- **移除：** 会向原版 Starter 返回 `null` 的 FIREWORK 过滤路径。
- **未实现：** 无。
