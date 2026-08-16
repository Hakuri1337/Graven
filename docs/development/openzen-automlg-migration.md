# OpenZen AutoMLG 严格迁移分析

## 1. 参考基线

本分析以 `SkidProjects/OpenZen` 当前源码为唯一行为基线：

- OpenZen 提交：`9f094b4aa8e0db7ebf2761749ea523d3517d7b0e`
- `AutoMLG.java` SHA-256：`7C947C023DE40670BBF1CA44BA114BECB19238D761E1F5D4C521A336B77A9D13`
- 参考环境：Minecraft 1.20.1、Forge 47.4.20、official mappings
- 目标环境：Minecraft 26.1.2、Java 25、Graven `common/`

直接参考文件：

- `modules.impl.player.AutoMLG`
- `utils.rotation.RotationHandler`
- `utils.game.RotationUtil#rotationToBlock`
- `utils.game.ItemUtil#findItemInRange`
- `utils.math.MathUtil#randomDouble`
- `patch.MinecraftPatch#onTick`

`MotionSimulator`、`RayTraceUtil` 虽然存在于 OpenZen，但 AutoMLG 没有调用它们，不属于迁移依赖。

## 2. 模块实际职责

AutoMLG 包含三个相互衔接的动作，而不只是落地前右键水桶：

1. **落地缓冲**：累计玩家真实下降距离，预测到地面的剩余 tick，在阈值内向正下方放水。
2. **延迟收水**：放水后等待 3 tick，最多执行 2 次空桶收水尝试。
3. **地面补水**：玩家没有水桶但持有空桶时，扫描附近可见水源并主动装水。

模块只搜索主快捷栏 `0..8`，不使用副手，不从背包换入物品。

## 3. 设置合同

| OpenZen 名称 | 类型 | 默认值 | 范围 / 步长 | Graven 映射 |
|---|---|---:|---|---|
| `Fall distance` | `NumberSetting` | `3.0` | `1.0..10.0 / 0.1` | `DoubleSetting` |
| `Predict Ticks` | `NumberSetting` | `2.0` | `1.0..5.0 / 1.0` | `DoubleSetting`，读取时 `intValue()` |
| `Solid check` | `BooleanSetting` | `true` | - | `BoolSetting` |
| `Recorvey` | `BooleanSetting` | `true` | - | `BoolSetting` |

`Recorvey` 是参考实现的公开设置名和配置键拼写，迁移时必须原样保留。不得静默改成 `Recovery`。模块名保留为 `AutoMLG`，分类映射到 `Category.PLAYER`。

## 4. 状态字段与生命周期

| 字段 | 含义 | 初始/清理值 |
|---|---|---|
| `accumulatedFall` | 非落地状态下累计的负向 Y 位移绝对值 | `0` |
| `lastY` | 上一轮模块 tick 的玩家 Y | 启用时为当前 Y |
| `slotToRestore` | 下一 tick 恢复的原快捷栏槽位 | `null` |
| `waterPlaced` | 本轮坠落是否已经触发放水 | `false` |
| `recoveryActive` | 是否处于延迟收水阶段 | `false` |
| `recoveryDelay` | 收水前固定延迟 | `0`，放水后为 `3` |
| `recoveryCountdown` | 延迟结束后的最大收水尝试窗口 | `0`，启用恢复时为 `2` |
| `waterBucketSlot` | 收水阶段缓存的空桶槽位 | `null` |
| `placedWaterPos` | 放水后根据射线计算的水方块位置 | `null` |
| `readyToPlace` | 下降且离地不超过 `1.05` 时置位 | `false` |
| `postPlaceCooldown` | `isInCooldown()` 暴露的短冷却 | `0` |
| `postActionCooldown` | 地面装水分支的 8 tick 冷却 | `0` |
| `extraCooldown` | 参考实现仅递减，从未赋正值 | `0` |
| `targetRotation` | 交给全局 RotationHandler 的单次旋转请求 | `null` |

`readyToPlace` 和 `extraCooldown` 在当前源码中没有影响最终动作，但属于参考状态机，严格迁移时保留，不擅自删除。

启用和禁用均重置上述状态。参考实现禁用时直接丢弃 `slotToRestore`，不会恢复已切换槽位；这与 Graven“模块禁用必须恢复物品栏外部状态”的约束冲突，见第 10 节。

## 5. 每 tick 的严格执行顺序

OpenZen 的 `TickEvent` 在 `Minecraft#tick` 入口发布。AutoMLG 使用默认优先级 `NORMAL(2)`；`RotationHandler#onTickHigh` 实际注册为 `LOWEST(4)`，因此 AutoMLG 先产生 `targetRotation`，RotationHandler 在同一 tick 末尾消费它，并把 AutoMLG 放在所有旋转模块的第一优先位。

AutoMLG 的顺序为：

1. 玩家或世界为空时直接返回。
2. 玩家正在鞘翅飞行时直接返回；此时 `lastY`、冷却和待恢复槽位都不更新。
3. 落地、能力飞行、处于水/雨/气泡柱或熔岩时把 `accumulatedFall` 清零；否则仅累计负向 Y 差。
4. 更新 `lastY`。
5. 依次递减 `postPlaceCooldown`、`postActionCooldown`、`extraCooldown`。
6. 若 `slotToRestore != null`，恢复快捷栏并清空该字段。
7. 落地或累计下降不大于零时，清除 `waterPlaced` 与 `readyToPlace`。
8. `recoveryActive` 时只执行收水状态机并立即返回。
9. 满足地面补水全部条件时装水、设置冷却并立即返回。
10. 已放水且仍下降、离地 `0..1.05` 时设置 `readyToPlace=true`。
11. `waterPlaced=true` 时返回，防止同一次坠落重复放水。
12. 累计下降达到阈值、快捷栏存在水桶、预计落地 tick 小于等于设置值时执行放水。

该顺序决定了槽位恢复、恢复延迟和补水是否能在同一 tick 发生，不得合并或重排。

## 6. 算法与边缘行为

### 6.1 累计坠落距离

模块不读取 `player.fallDistance`。它按 tick 计算：

```text
deltaY = currentY - lastY
if deltaY < 0: accumulatedFall -= deltaY
```

上升不会减少累计值；只有落地、飞行、水/雨/气泡柱或熔岩会清零。

### 6.2 到地面距离

从 `(player.x, player.boundingBox.minY, player.z)` 向下发射 `COLLIDER + Fluid.NONE` 射线：

- 预测使用最大距离 `30.0`。
- `readyToPlace` 检测使用最大距离 `2.5`。
- MISS 返回正无穷。
- 命中返回起点 Y 减命中位置 Y。

### 6.3 剩余 tick 预测

仅模拟竖直速度，不模拟水平落点：

```text
simulatedDrop = 0
simulatedVelocity = player.deltaMovement.y
for tick = 1..20:
    simulatedDrop += simulatedVelocity
    simulatedVelocity = (simulatedVelocity - 0.08) * 0.98
    if abs(simulatedDrop) >= distanceToGround(30): return tick
return 999
```

初始竖直速度非负或 30 格内无碰撞面也返回 `999`。

### 6.4 Solid check

`Solid check` 检查的是玩家当前整数方块位置下方 1 格或 2 格，而不是预测的水平落点。候选方块必须同时：

- 碰撞形状非空；
- `BlockState#getMenuProvider(level, pos) == null`。

随后仍需用当前 yaw、pitch `90`、范围 `5.0` 的 `OUTLINE + Fluid.NONE` 射线命中，MISS 时不放水。

### 6.5 放水和槽位恢复

动作顺序严格为：

1. 保存当前快捷栏槽位。
2. 切换到水桶槽位。
3. 临时把玩家 yaw/pitch 改为目标值。
4. `gameMode.useItem(MAIN_HAND)`。
5. `player.swing(MAIN_HAND)`。
6. 立即恢复玩家客户端 yaw/pitch。
7. 下一模块 tick 才恢复快捷栏槽位。

放水目标固定为当前 yaw、pitch `90`。放水后再次用 `OUTLINE + Fluid.NONE`、范围 `4.5` 射线命中支撑面，并把 `hit.blockPos.relative(hit.direction)` 保存为水位置。

### 6.6 延迟收水

放水后：`recoveryDelay=3`、`recoveryCountdown=2`。延迟递减期间整个模块直接返回。延迟结束后：

- 搜索空桶并缓存槽位；
- 若缓存槽位已经变回水桶，认为收水成功并结束；
- 若目标位置不再是水源，结束；
- 对目标水源中心计算带噪声的旋转；
- `SOURCE_ONLY` 流体射线必须准确命中目标位置；
- 切换空桶、使用并挥手；下一 tick 恢复槽位。

这形成最多两次动作尝试的窗口，成功通常在下一 tick 通过“槽位已是水桶”确认。

### 6.7 地面补水

同时满足以下条件才扫描水源：

- 未放水、未恢复、`placedWaterPos == null`；
- 两个动作冷却均为零；
- `accumulatedFall <= 0.5`；
- 快捷栏没有水桶但有空桶。

扫描以玩家整数方块位置为中心，`dx/dz=-4..4`、`dy=-1..1`，选择欧氏距离平方最近且在 `4.5` 格 `SOURCE_ONLY` 射线内可见的水源。候选旋转瞄准方块中心，并给 X/Y/Z 差值分别加入随机噪声：幅度在 `[0.05, 0.08)`，符号因子在 `[-1, 1)`。

装水后设置 `postActionCooldown=8` 和至少 1 tick 的 `postPlaceCooldown`。`postActionCooldown` 只阻止再次地面补水，不阻止随后使用新水桶执行 MLG。

## 7. 依赖与架构映射

| OpenZen 项 | Graven 对应项 | 迁移要求 |
|---|---|---|
| `AutoMLG extends Module` | `modules.impl.player.AutoMLG` | `INSTANCE` + 私有构造，`Category.PLAYER` |
| `TickEvent` | `ClientTickEvent.Pre` | 同为 `Minecraft#tick` HEAD；监听器需晚于普通模块运行 |
| `NumberSetting` | `DoubleSetting` | 保留名称、默认值、范围、步长和 `intValue()` 行为 |
| `BooleanSetting` | `BoolSetting` | 保留 `Recorvey` 拼写 |
| `ItemUtil.findItemInRange(0,9,item)` | 模块私有快捷栏查找或精确范围 helper | 只查 `0..8`，返回首个槽位或 `-1` |
| `Rotation` | `Rot2f` | yaw/pitch 顺序不变 |
| `RotationUtil.rotationToBlock` | AutoMLG 私有 `rotationToBlock` | 不可直接用无噪声的 `RotationUtils.calculate(BlockPos)` |
| `RotationHandler` 第一优先项 | `Managers.ROTATION.setRotations(..., Priority.Highest)` | 每次从 `Managers.ROTATION` 读取；不能缓存 Manager |
| 临时玩家旋转 + UseItem packet | `UseItemEvent` + 临时 yaw/pitch | 模块动作期间以 `LOWEST` 覆盖包内 yaw/pitch，保持精确动作视角 |
| `Inventory.selected` | `getSelectedSlot()/setSelectedSlot()` | 26.1.2 字段为 private，setter 会校验 `0..8` |
| `isInWaterRainOrBubble()` | `isInWaterOrRain()` + 26.1.2 气泡柱状态核验 | 旧 API 已移除，需保留三项重置语义 |
| `ClipContext` | 同名 Mojmap API | `COLLIDER/OUTLINE` 与 `NONE/SOURCE_ONLY` 原样保留 |
| `gameMode.useItem` | 同名 API | 26.1.2 会先同步手持槽位，再发送带 yaw/pitch 的预测包 |

实现不需要新增 Maven、native、Fabric 或 NeoForge 依赖，全部逻辑应放在 `common/`。

## 8. 实现文件与职责

正式实现已修改：

- 新增 `common/src/main/java/tech/hakuri/graven/modules/impl/player/AutoMLG.java`
- 修改 `common/src/main/java/tech/hakuri/graven/holders/ModuleHolder.java`
- 修改 `common/src/main/resources/assets/graven/i18n/en_us.json`
- 修改 `common/src/main/resources/assets/graven/i18n/zh_cn.json`
- 更新本文件的最终兼容性报告

不新增 Mixin。现有 `MixinMultiPlayerGameMode` 已发布 `UseItemEvent`，现有 RotationManager 已覆盖移动包、使用物品射线和服务端使用包视角。

实现结构保留一个模块类中的原始状态机与私有 helper，没有把三个动作拆成互不共享状态的模块，也没有把完整流程压缩成一次 `fallDistance` 判断。

## 9. 旋转与事件时序适配

Graven EventBus 数值越大越先执行；OpenZen 则按 `0..4` 从高到低执行。建议映射：

- AutoMLG 的 `ClientTickEvent.Pre` 使用 `EventPriority.LOWEST`，使普通 ClientTick 模块先提交旋转。
- AutoMLG 动作旋转使用 `Priority.Highest`，复现 OpenZen RotationHandler 中 AutoMLG 的第一选择权。
- `Managers.ROTATION.setRotations(target, 180, Priority.Highest)` 后立即执行动作。
- 动作期间设置 `usingItemRotation`，在 `UseItemEvent` 的 `EventPriority.LOWEST` 监听器中把包 yaw/pitch 改为本次精确旋转，避免 Graven 平滑/灵敏度修正改变 OpenZen 的临时实体视角语义。
- 动作结束后清除标志，raytrace 回调不执行任何状态修改。

由于 Graven 允许其他模块在稍后的 `PlayerTickEvent.Pre` 再提交同级 `Highest` 旋转，正式实现验证必须覆盖与 Scaffold、AutoMend、AutoThrow 同时启用的冲突场景；如发生同级覆盖，只能通过现有事件优先级调整，不能新建第二套全局 RotationManager。

## 10. 已确认差异与审批点

### 10.1 必须适配且不改变结果

- `Inventory.selected` 改为 getter/setter。
- `TickEvent` 改为 `ClientTickEvent.Pre`。
- `Rotation` 改为 `Rot2f`。
- 1.20.1 `isInWaterRainOrBubble()` 在 26.1.2 已移除；需以当前水/雨 API和气泡柱状态复现重置条件。

### 10.2 参考实现的确定性故障

恢复分支把返回 `int` 的 `findItemInRange` 赋给 `Integer waterBucketSlot`，随后只判断 `null`。当空桶在 3 tick 延迟期间消失时，返回值 `-1` 会被装箱，代码继续执行 `inventory.items.get(-1)`，产生越界异常。已用 OpenZen 编译产物字节码确认该路径存在。

生产实现应把条件改为 `slot < 0` 时终止恢复并清理状态。该变更只改变异常失败模式，不改变正常输入行为，但属于明确的兼容性差异，正式实现报告必须记录，不能静默修改。

### 10.3 Graven 生命周期约束

OpenZen 禁用时不会实际恢复 `slotToRestore`。Graven 强制要求模块禁用恢复物品栏，因此目标实现应在 `onDisable()` 中先恢复有效槽位，再清理状态。该差异只影响“动作后、下一 tick 前禁用模块”的边缘情况。

除上述两项外，不修正 `readyToPlace`、`extraCooldown`、鞘翅提前返回导致计时冻结等参考行为。

## 11. 验证矩阵

### 11.1 算法测试

- 上升、下降、落地、能力飞行、鞘翅、水、雨、气泡柱和熔岩下的累计距离。
- 初速度非负、30 格内无地面、1..20 tick 命中边界及 `Predict Ticks` 端点。
- Solid check 对空气、普通方块、有菜单方块、1 格和 2 格下方的结果。
- `COLLIDER/OUTLINE` 与 `NONE/SOURCE_ONLY` 四种射线组合。
- 81×3 扫描范围、最近水源选择、遮挡和 4.5 格边界。

### 11.2 状态机测试

- 放水只触发一次，落地后允许下一轮。
- 槽位在下一 tick 恢复，不在同 tick 恢复。
- 恢复固定等待 3 tick，并最多尝试 2 次。
- 收水成功、水源消失、空桶消失、射线偏离各自清理状态。
- 无水桶 + 空桶时自动补水，8 tick 内不重复补水。
- 禁用发生在待恢复槽位期间时仍恢复原槽位。

### 11.3 集成验证

- Fabric 与 NeoForge 编译和 `buildRelease`。
- 两个平台真实客户端启动，确认 Module/i18n/配置注册。
- 录制并比较手持槽位包、使用物品包、移动旋转包和挥手包的顺序。
- 与 Scaffold、NoFall、AutoMend、AutoThrow、Rotation Mode `SILENT/SNAP` 的组合测试。
- 生存模式从不同高度实测放水、收水、自动补水和落地伤害结果。

## 12. 当前兼容性报告

- **保留：** 模块名、分类、四个设置及拼写、默认值/范围/步长、所有状态字段、tick 执行顺序、累计距离算法、20 tick 竖直预测、射线模式与范围、主快捷栏限制、延迟恢复、两次尝试、地面补水、随机瞄准噪声、槽位下一 tick 恢复、挥手行为和公开 `isInCooldown()` 语义。
- **更改：** 按第 7 节完成 26.1.2 API 映射；按第 10 节加入 `-1` 槽位崩溃防护和禁用时恢复槽位两项必要生产修正。使用物品动作通过临时实体旋转与 `UseItemEvent` 同时固定本地交互和发包视角，动作结束后恢复客户端视角。
- **移除：** 无。`MotionSimulator` 与 `RayTraceUtil` 从未被 AutoMLG 调用，不属于移除项。
- **未实现：** 无。

## 13. 实现验证记录

- `:common:compileJava`：基线与迁移后均为 `BUILD SUCCESSFUL`。
- Fabric 开发客户端：完成 Loader、Mixin、Graven 初始化、资源加载与 `graven-empty-i18n.json` 生成；日志包含 `Graven has loaded successfully.`。
- i18n：Fabric 模板包含 `graven.modules.automlg` 及四个设置键；`en_us.json`、`zh_cn.json` dry-run 均无新增 key。模板报告的 4 个 Dynamic Island 多余 key 是迁移前既有内容，未在本任务中删除。
- `buildRelease`：`common`、Fabric、NeoForge 的编译、检查与打包均通过，结果为 `BUILD SUCCESSFUL`。
- 尚需人工联机验证矩阵：不同高度的放水/收水/补水结果，以及与 Scaffold、NoFall、AutoMend、AutoThrow 和两种 Rotation Mode 的组合包序列。该项属于运行环境行为验证，不是缺失实现。
