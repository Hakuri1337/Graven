# 配置与旋转

## 配置目录

`ConfigHolder` 的根目录位于用户目录下的 `.graven/`：

```text
~/.graven/
├── active-config.txt
├── client-settings.json
├── configs/<name>/
├── imports/
└── exports/
```

- 模块、HUD、Addon Setting 和好友按当前活动配置保存。
- `client-settings.json` 保存标记为 root 的客户端设置。
- 配置支持新建、切换、删除、另存、重载、Zip 导入和导出。
- `saveNow()` 已由 JVM shutdown hook 调用。

安全解压、迁移和主动保存约束见 [`AGENTS.md`](../../AGENTS.md)。

## RotationManager

`RotationManager` 是抽象基类，当前实例只能通过 `Managers.ROTATION` 获取。模式由 `ClientSetting.rotationMode` 选择：

- `SilentRotationManager`：静默发包旋转。
- `SnapRotationManager`：直接修改并恢复玩家旋转。

主要 API：

```java
Managers.ROTATION.setRotations(rotation, speed);
Managers.ROTATION.setRotations(rotation, speed, Priority.High);
Managers.ROTATION.setRotations(rotation, speed, raytrace);
Managers.ROTATION.setRotations(rotation, speed, raytrace, Priority.High);

Rot2f current = Managers.ROTATION.getRotation();
Rot2f previous = Managers.ROTATION.getLastRotation();
HitResult hitResult = Managers.ROTATION.getHitResult();
boolean active = Managers.ROTATION.isActive();
```

旋转值类型为 `tech.hakuri.graven.utils.rotation.Rot2f`。`getHitResult()` 返回按当前托管旋转计算的逻辑命中结果；没有活动旋转时返回原版 `mc.hitResult`。

Rotation priority 与 EventBus priority 是两套系统：

| Priority | 数值 |
|---|---:|
| `Lowest` | 0 |
| `Low` | 10 |
| `Medium` | 50 |
| `High` | 100 |
| `Highest` | 1000 |

仅当新 priority 不低于当前活动 priority 时才覆盖请求。

运行时行为：

- `Function<Rot2f, Boolean>` raytrace 会在平滑随机偏移校验中多次调用。
- 每次平滑后通过 `LocalPlayer.raycastHitResult(1.0f, mc.player)` 更新逻辑命中结果，计算期间由 `RaytraceEvent` 临时应用托管旋转。
- `ClientSetting.modifyCrosshair` 只控制是否把托管旋转应用到视觉准星。关闭后，模块仍可通过 `getHitResult()` 使用托管旋转进行命中检测；FreeCamera 的视觉准星继续使用自由相机结果。
- 物品使用射线仍通过独立的 `UseItemRaytraceEvent` 处理。
- 服务端位置/旋转包会设置 S08 重置标记；下一次请求先同步真实视角。
- 旋转接近玩家真实角度时自动结束，没有 callback 或 `isDone()`。
- 需要等待命中后攻击/放置时，模块保存 pending 状态，每 tick 继续请求旋转，并用当前 `getRotation()` 做 raytrace 后执行一次性动作。
- 切换旋转模式会调用 `copyStateFrom()` 并替换实例。

## MouseRotationController

`ClientSetting.modifyCrosshair` 默认关闭。关闭时托管旋转仍由 `RotationManager` 保存并
通过 `getHitResult()` 提供给逻辑模块，但不会调用 `mc.pick()` 覆盖屏幕准星；需要视觉准星
跟随托管旋转时再显式开启该设置。

`MouseRotationController.INSTANCE` 是从 EdOpal 迁移的确定性客户端鼠标旋转控制器，服务于要求真实客户端转向轨迹的模块。它与 `Managers.ROTATION` 的随机平滑、SILENT/SNAP 模式相互独立。

```java
MouseRotationController.INSTANCE.rotate(target, new LinearRotationModel(speed));
```

- `ClientTickEvent.Pre` 以最高优先级推进上一请求、切换回用户旋转目标并记录本 tick 起始角度。
- 模块在同一事件中调用 `rotate` 时立即推进最新请求，保留 EdOpal 的两阶段 tick 节奏。
- `ClientRotationTracker` 在自动转向期间继续积累用户真实鼠标输入，控制器反向阶段回到该角度。
- `LinearRotationModel` 按 yaw/pitch 二维欧氏长度分配确定性角速度，不加入随机速度或微扰。
- 该控制器直接改变玩家客户端角度；需要静默旋转的普通模块继续使用 `Managers.ROTATION`。
