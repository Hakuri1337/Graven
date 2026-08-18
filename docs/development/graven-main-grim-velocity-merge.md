# Graven-main Grim Velocity 合并记录

> 本文记录旧附件合并，已被
> [Asaka GrimVelocity 完整重迁移](asaka-velocity-grim-migration.md) 取代。当前实现直接读取
> `mc.hitResult`，不再采用本文的 `Managers.ROTATION.getHitResult()` 适配。

## 参考版本

- 附件：`Graven-main.zip`
- 附件 SHA-256：`DAFEF0DC145EB918C1FF72C43A02463E522C5DED5CBC704CA503D07B279CAA65`
- 对比基线：公开提交 `5ad5ebd`
- 游戏版本：Minecraft `26.1.2`

## 映射表

| 参考实现 | 本项目对应项 | 合并方式 |
|---|---|---|
| `Velocity.Mode.Grim` | `Velocity.Mode.Grim` | 保留枚举与模式切换分支 |
| `Velocity.GrimMode` | `Velocity.GrimMode` | 保留 `PerTick`、`OneTime` |
| `grimPacketQueue` | `Velocity` 私有并发队列 | 保留队列所有权与释放时机 |
| `FightManager` | `utils.combat.FightManager` | 直接复用既有逐 tick 攻击锁 |
| `mc.hitResult` | `Managers.ROTATION.getHitResult()` | 适配本项目逻辑命中结果，保持静默旋转与攻击目标一致 |
| `ClientboundPingPacket` 延迟 | 原包类型与手动 `handle` 回放 | 保留参考实现的包范围和回放顺序 |
| Grim 设置与枚举文案 | Graven i18n JSON | 补齐中英文叶节点 |
| `local-maven` | 仓库根目录 `local-maven/` | 原样导入附件离线仓库 |
| 阿里云、腾讯云镜像 | Gradle repositories、Wrapper | 合并现有阿里云镜像并采用腾讯 Wrapper |

## 状态机

1. 收到本玩家速度包后，根据逻辑准星目标、地面状态和近距离玩家决定是否激活 Grim。
2. 激活期间缓存并取消后续玩家速度包与客户端 Ping 包。
3. `PlayerTickEvent.Pre` 按设置执行逐 tick 或一次性攻击计数。
4. 攻击计数耗尽且玩家落地后回放缓存包，并开始下一轮攻击计数。
5. 服务端位置修正、模块禁用或离开游戏时释放或清理对应状态。

## 兼容性报告

- **保留：** Grim 模式设置、攻击计数、跳跃重置、日志、受击包状态机、Ping 延迟、位置修正处理、手动包回放、Legit 输入向量判断。
- **更改：** 目标读取从摄像机 `mc.hitResult` 改为 `Managers.ROTATION.getHitResult()`，原因是本项目已将服务端旋转逻辑命中结果与屏幕准星分离。
- **移除：** 无。
- **未实现：** 无。
