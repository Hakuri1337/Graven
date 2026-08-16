# EdOpal AutoThrow 严格迁移分析

## 1. 范围与结论

本文分析 `SkidProjects/EdOpal` 的 `AutoThrowModule`，并记录其迁移到 Graven 的完整行为映射、实现结果和验证结论。

参考基线：

- EdOpal 主实现：`SkidProjects/EdOpal/src/client/java/wtf/oraculus/client/feature/module/impl/combat/AutoThrowModule.java`
- EdOpal 内部依赖：`TeamsModule`、`AntiBotsModule`、`RotationMouseHandler`、`ClientRotationHandler`、`LinearRotationModel`、`PlayerUtility`、`RotationUtility`、`Stopwatch`
- EdOpal 注册入口：`SkidProjects/EdOpal/src/editions/beta/java/wtf/oraculus/client/edition/EditionModuleCatalog.java`
- Graven：Minecraft 26.1.2、Java 25、Fabric/NeoForge 多加载器架构
- Minecraft 26.1.2 参考源码：`reference/vanilla-26.1.2`

结论：AutoThrow 不是一个可独立复制的“自动右键”模块。严格迁移必须同时保留以下合同：

1. 毫秒计时、投掷计划和最多四个计划 tick 的状态机。
2. 副手、当前主手槽、其余快捷栏的固定物品选择优先级。
3. AutoThrow 私有的目标收集、排序和锁定后复验规则。
4. 基于蛋/雪球速度与重力常量的低弹道解。
5. EdOpal 确定性二维线性转向及鼠标灵敏度量化。
6. 使用物品后继续保持临时槽位，直到本 tick 移动包阶段结束后再恢复。
7. `Teams`、`AntiBots`、好友、Scaffold/Stuck/Blink 的协作行为。

Graven 现有 `TargetManager` 和 `RotationManager` 均不能直接作为等价替换。前者改变距离定义和目标优先级，后者在平滑过程中加入随机速度与微扰，并受全局 SILENT/SNAP 模式影响。直接调用这两个 Manager 会产生可观察的目标选择、转向轨迹和包角度差异。

## 2. EdOpal 设置与状态

### 2.1 设置

| 设置 | 默认值 | 范围 | 步长 | 语义 |
|---|---:|---:|---:|---|
| `Min Distance` | 5.0 | 3.0..30.0 | 1.0 | 目标水平距离下限 |
| `Max Distance` | 10.0 | 3.0..30.0 | 1.0 | 目标水平距离上限 |
| `Delay` | 500 ms | 50..2000 ms | 50 ms | 从启用或上次创建计划起计算的实际时间间隔 |
| `FOV` | 90 | 15..180 | 5 | 单侧水平角阈值，不是 Graven 当前工具中的总视野角 |
| `Turn Speed` | 35 deg/tick | 10..90 | 5 | 二维 yaw/pitch 向量每次线性步进的最大长度 |
| `Target.Player` | true | - | - | 允许玩家 |
| `Target.Invisible` | true | - | - | 初次选目标时允许对本地玩家隐身的实体 |
| `Target.Animals` | false | - | - | 允许 `AnimalEntity` |
| `Target.Mobs` | false | - | - | 允许动物以外的 `MobEntity`，不仅是敌对生物 |

实现时分别映射为 Graven 的 `DoubleSetting`、`IntSetting` 和四个 `BoolSetting`。不能把四个目标开关压缩成实体列表，因为这会改变配置 schema、GUI 结构和默认行为。

### 2.2 常量与运行时状态

```text
PROJECTILE_SPEED   = 1.5
PROJECTILE_GRAVITY = 0.03
MAX_TURN_TICKS     = 4

stopwatch
pendingPlan(hand, slot, targetId)
pendingRotation
rotationTicks
restoreSlot = -1
```

`ThrowPlan` 必须继续只保存手、快捷栏槽和实体 ID。保存实体对象会改变换世界、实体卸载和 ID 失效时的行为。

## 3. 完整状态机

### 3.1 启用与禁用

- 启用：清除计划，恢复尚未恢复的槽位，重置毫秒计时器。
- 禁用：先恢复槽位，再清除计划；EdOpal 不在禁用时重置计时器。
- `clearPlan()` 只清空 `pendingPlan`、`pendingRotation` 和 `rotationTicks`，不会恢复槽位，也不会重置计时器。

### 3.2 每次 `PreGameTickEvent`

EdOpal 的事件发布在 Minecraft 客户端 `tick()` HEAD。Graven 的精确对应项是 `ClientTickEvent.Pre`，不是位置更晚的 `PlayerTickEvent.Pre`。

处理顺序必须保持：

1. 玩家、世界或交互管理器不存在时清除计划并返回。
2. Screen/overlay 打开，或 Scaffold、SsngScaffold、Stuck、Blink 任一启用时，清除计划并返回。
3. 已有计划时只执行 `updatePendingAim()`，不检查 Delay，也不重新选择投掷物或其他目标。
4. 无计划时以 `System.currentTimeMillis()` 检查 Delay。EdOpal 的 `Stopwatch` 使用严格的 `elapsed > delay`，不是 `>=`。
5. 先寻找投掷物计划，再寻找目标。即使没有投掷物，目标查询仍会执行。
6. 计划或目标缺失，或者玩家此时正在使用物品时，保持计时器不变并返回。
7. 计算弹道旋转；无解时不创建计划，也不重置计时器。
8. 保存带目标实体 ID 的计划，按当前角度差计算 `rotationTicks`，然后立即重置 Stopwatch。

Delay 在“创建计划”时重置，不在真正投掷时重置。因此总投掷周期是 Delay 与计划转向时间的组合，而不是固定的两次投掷间隔。

### 3.3 已锁定计划

每个计划 tick：

1. 通过实体 ID 从当前世界重新解析目标。
2. 重新验证计划槽中的物品仍是蛋或雪球。
3. 重新验证目标存活、非观察者、非自身、可见、类型允许、非 bot、非队友、非好友、水平距离仍在范围内。
4. 重新计算指向目标当前 AABB 最近点的弹道旋转。
5. 向线性旋转控制器提交最新目标旋转。
6. 执行前置递减 `--rotationTicks`；结果小于等于零时在同一 tick 投掷。

锁定后有意不再复验以下初选条件：

- `Invisible` 设置；
- FOV；
- 初次查询使用的三维膨胀 AABB。
- 玩家是否正在使用其他物品。

因此目标锁定后可以离开 FOV、改变隐身状态或在垂直方向离开初始搜索盒，只要其他复验条件仍成立，计划就会继续。迁移时不能把初选谓词统一复用到锁定复验中。

### 3.4 投掷与槽位恢复

投掷前再次验证计划物品：

- 副手计划要求当前副手仍为蛋或雪球。
- 主手计划要求槽位仍为 0..8 且该槽仍为蛋或雪球。

投掷物查找优先级固定为：

1. 副手；
2. 当前选中快捷栏槽；
3. 槽位 0 到 8 的第一个蛋或雪球。

主手计划且计划槽不是当前槽时，模块保存旧槽并调用 `Inventory#setSelectedSlot`。随后调用 `MultiPlayerGameMode#useItem` 和本地 `swing`，最后只清除计划。

26.1.2 的 `MultiPlayerGameMode#useItem` 已确认先执行 `ensureHasSentCarriedItem()`，再构造 `ServerboundUseItemPacket(hand, sequence, playerYaw, playerPitch)`。因此切槽后的网络顺序保持为：

```text
ServerboundSetCarriedItemPacket(计划槽)
ServerboundUseItemPacket(手, sequence, yaw, pitch)
ServerboundSwingPacket(手)
本 tick 移动包阶段
本地恢复旧槽
```

恢复旧槽不立即强制发送第二个 carried-item 包；它会在后续需要同步槽位的交互中由原版 `ensureHasSentCarriedItem()` 发送。这一点必须保留。

## 4. 目标选择的精确语义

### 4.1 初次收集

EdOpal 使用：

```text
world.getNonSpectatingEntities(
    LivingEntity,
    player.boundingBox.expand(maxDistance)
)
```

26.1.2 的对应 API 是：

```text
level.getEntitiesOfClass(
    LivingEntity.class,
    player.getBoundingBox().inflate(maxDistance)
)
```

该无谓词重载已经使用 `EntitySelector.NO_SPECTATORS`。不能改成 `entitiesForRendering()`，否则候选集合的空间边界和加载语义会变化。

完整初选谓词顺序：

1. 排除本地玩家，要求存活且非观察者。
2. `AntiBotsModule.shouldFilter(entity)` 为 false。
3. `TeamsModule.isTeammate(entity)` 为 false。
4. 好友名单不包含实体名称的大写形式。
5. 目标没有对本地玩家隐身，或者 `Invisible=true`。
6. 类型开关允许。
7. `player.canSee/hasLineOfSight(target)` 为 true。
8. 水平 FOV 通过。
9. 水平距离位于排序后的 min/max 闭区间。
10. 按玩家到实体的三维平方距离取最小值。

Min 和 Max 始终先经 `Math.min/Math.max` 排序，所以用户配置反转时仍正常工作。

### 4.2 FOV 差异

EdOpal 的 FOV 判定为：

```text
if fov >= 180: true
angle = wrapDegrees(clientYaw - yawToEntityPosition)
abs(angle) < fov
```

所以默认 90 表示左右各 90 度。Graven 当前 `RotationUtils.isInFov(entity, fov)` 使用 `abs(diff) <= fov / 2`，默认 90 只表示左右各 45 度，且边界比较不同。AutoThrow 必须保留自己的 FOV helper，不能调用现有方法。

### 4.3 类型差异

EdOpal 先判断 Player，再判断 Animal，最后把所有其余 `MobEntity` 归入 Mobs。26.1.2 应映射为：

```text
Player -> Player 开关
Animal -> Animals 开关
其余 Mob -> Mobs 开关
其他 LivingEntity -> false
```

只使用 Graven `TargetRequest.mob()` 当前对应的 `Monster` 会漏掉村民、环境生物和其他非动物 Mob。

### 4.4 AntiBot 与好友

Graven 的 `AntiBot` 已迁移自同一 EdOpal 逻辑，并保留确认 bot、新玩家超时和异常实体 ID 判定。AutoThrow 应直接调用：

```text
AntiBot.INSTANCE.isBot(entity)
AntiBot.INSTANCE.isBedWarsBot(entity)
```

这些方法内部已经包含模块启用检查。

EdOpal 好友比较通过名称大写实现不区分大小写；Graven `FriendManager.isFriend` 当前是精确大小写比较。为保持 AutoThrow 行为，迁移模块应对 `Managers.FRIEND.getFriends()` 做 `equalsIgnoreCase` 比较，不能静默改变全局 FriendManager 的合同。

### 4.5 Teams 是必需的内部依赖

Graven 当前没有 Teams 模块。严格迁移应连带迁移独立 `Teams` 模块，而不是把团队判断塞进 AutoThrow。EdOpal Teams 只有启用时生效，包含：

- `COLOR`：比较实体与本地玩家的 team color。
- `SCOREBOARD`：从网络玩家信息取得 scoreboard team name，再用 `Objects.equals` 比较。

Teams 只对 `Player` 生效；动物和其他 Mob 永远不会被 Teams 过滤。

需要保留两个看似反直觉的边缘行为：

- COLOR 模式下，两个无队伍实体通常都返回默认白色，因此会被视为同队。
- SCOREBOARD 模式下，双方 team name 都为 null 时，`Objects.equals(null, null)` 为 true，也会被视为同队。

EdOpal 把 Teams 放在 WORLD 分类，而 Graven 没有 WORLD 分类。迁移时只能放入最接近用途的 `Category.COMBAT`；这是 UI 分类适配，不改变判断逻辑。

## 5. 弹道与旋转

### 5.1 目标点

目标点不是实体中心或眼睛，而是玩家眼睛到以下 AABB 的最近点：

```text
target.boundingBox.expand(target.targetingMargin)
```

在 26.1.2 中 `Entity#getPickRadius()` 是对应的命中边距。最近点逐轴 clamp：

```text
x = clamp(eye.x, box.minX, box.maxX)
y = clamp(eye.y, box.minY, box.maxY)
z = clamp(eye.z, box.minZ, box.maxZ)
```

Graven 当前 `getEyeDistanceToEntity` 没有扩大 pick radius，不能直接复用为弹道目标点。

### 5.2 低弹道公式

设 `difference = closestPoint - eye`，`d = hypot(dx, dz)`：

```text
v2 = 1.5 * 1.5
g  = 0.03

discriminant = v2 * v2 - g * (g * d * d + 2 * dy * v2)
tangent      = (v2 - sqrt(discriminant)) / (g * d)

yaw   = degrees(-atan2(dx, dz))
pitch = -degrees(atan(tangent))
```

使用减号根，即低弹道。`d < 1e-4`、判别式小于零或 yaw/pitch 为 NaN 时放弃本次计划/计划更新。pitch 最后限制到 `-90..90`。

### 5.3 灵敏度量化不能复用现有实现

EdOpal `getVanillaRotation` 的基准是玩家当前实际 yaw/pitch，并依次执行 `patchConstantRotation`、发送旋转量化和 yaw 近值环绕。Graven `RotationUtils.applySensitivityPatch` 的基准是 `Managers.ROTATION.lastRotations`，还会给 sensitivity 加随机扰动。两者不等价。

迁移时应把 EdOpal 的确定性量化算法作为 AutoThrow 旋转控制器的一部分原样适配到 `Rot2f`，不能调用当前随机化 helper。

### 5.4 线性步进与计划 tick

EdOpal `LinearRotationModel` 在 yaw/pitch 二维空间按欧氏长度分配速度：

```text
deltaYaw   = wrapDegrees(targetYaw - currentYaw)
deltaPitch = targetPitch - currentPitch
distance   = hypot(deltaYaw, deltaPitch)

maxYaw   = speed * abs(deltaYaw / distance)
maxPitch = speed * abs(deltaPitch / distance)

nextYaw   = currentYaw + clamp(deltaYaw, -maxYaw, maxYaw)
nextPitch = currentPitch + clamp(deltaPitch, -maxPitch, maxPitch)
```

计划创建时：

```text
difference = hypot(abs(wrapDegrees(targetYaw-currentYaw)),
                   abs(targetPitch-currentPitch))
rotationTicks = clamp(ceil(difference / turnSpeed), 1, 4)
```

目标移动时每 tick 重新计算目标角，但不重算 `rotationTicks`。

EdOpal 的 `RotationMouseHandler` 还在高优先级 PreGameTick 中推进上一请求，然后 AutoThrow 在同一事件中提交并立即推进新请求。Graven 保留 AutoThrow 的计划 tick 计数和目标角度重算，并把每次请求交给既有 RotationManager；这样网络旋转继续遵循共享平滑/优先级生命周期，同时不再驱动屏幕视角。

## 6. 旋转子系统迁移方案

严格方案保留 EdOpal 的线性旋转和投掷时序，但在 Graven 中通过既有 `RotationManager` 提交旋转请求，使 AutoThrow 使用服务端旋转而不改变屏幕视角。

| EdOpal | Graven 落点 | 要求 |
|---|---|---|
| `IRotationModel` | `utils.rotation.model.RotationModel` | 保留 `tick(from,to,timeDelta)` 合同 |
| `LinearRotationModel` | `utils.rotation.model.LinearRotationModel` | 原样保留二维速度分配 |
| `ClientRotationHandler` | `managers.impl.rotations.ClientRotationTracker` | 跟踪用户真实鼠标旋转，供 FOV 和回转使用 |
| `RotationMouseHandler` | `managers.impl.rotations.RotationManager` | 复用既有静默/快照旋转生命周期，不直接调用 `LocalPlayer#turn` |
| `MouseUpdateEvent` | 现有 `UseItemEvent` 与 `SendPositionEvent` | 由 RotationManager 写入投掷包和移动包角度 |

AutoThrow 不再接管 `MouseHandler#turnPlayer`，因此鼠标输入、屏幕视角和客户端旋转跟踪保持原样；RotationManager 负责在移动包阶段输出服务端角度。

仅使用以下代码属于近似实现，不满足严格迁移：

```text
Managers.ROTATION.setRotations(target, turnSpeed, Priority.Medium)
```

原因包括随机 `rotationSpeed + Math.random()`、FPS 次数相关微扰、不同量化基准、SILENT/SNAP 分支及不同的每 tick 推进次数。

作为包级兜底，AutoThrow 在投掷 tick 从 `Managers.ROTATION.getRotation()` 处理 `UseItemEvent`，确保 26.1.2 `ServerboundUseItemPacket` 的 yaw/pitch 与当次静默旋转一致。

## 7. 移动包结束事件

EdOpal 在 `sendMovementPackets` TAIL 发布 `PostMovementPacketEvent`，AutoThrow 在该事件中恢复槽位。迁移前 Graven 只有 `SendPositionEvent` HEAD，没有等价的 post 事件。

26.1.2 `LocalPlayer#tick` 有两个分支：

- 非乘客调用 `sendPosition()`；
- 乘客直接发送旋转/载具/疾跑相关包，不调用 `sendPosition()`。

因此只在 `sendPosition` TAIL 新增事件会导致乘坐载具时临时槽位永不恢复。当前实现已在 `LocalPlayer#tick` 的乘客/非乘客移动包分支汇合后、ambient sound handler 循环前发布 `PostMovementPacketEvent`。

还要保留取消语义：EdOpal 在 movement packet HEAD 被取消时不会到达 TAIL。Graven 应记录当次 `SendPositionEvent` 是否取消；非乘客分支被取消时跳过 post 事件，槽位保持到下一次真正完成的移动包阶段或模块禁用时恢复。乘客分支没有 `SendPositionEvent`，正常发布 post 事件。

事件只属于 Minecraft 公共逻辑，放在 `common/` 的共享 Mixin 和事件包中，不引入 Fabric/NeoForge API。新增 Mixin 不需要新的 JSON 条目，因为继续使用现有 `MixinLocalPlayer`。

## 8. 模块与依赖映射表

| 参考模块/类/函数 | Graven 对应项 | 迁移处理 |
|---|---|---|
| `AutoThrowModule` | `modules.impl.combat.AutoThrow` | 新增单例，保留状态机与私有筛选器 |
| `PreGameTickEvent` | `ClientTickEvent.Pre` | 精确对应 Minecraft tick HEAD |
| `PostMovementPacketEvent` | 新增同语义事件 | 在 26.1.2 移动分支汇合点发布 |
| `Stopwatch` | AutoThrow 私有 Stopwatch 或严格比较字段 | 必须使用 `elapsed > delay` |
| `ThrowPlan` | AutoThrow 私有 record | 保留 hand/slot/targetId |
| `Vec2f` | `Rot2f` | 仅数据类型适配 |
| `RotationUtility.getVanillaRotation` | 新控制器的确定性量化 | 不复用随机 Graven helper |
| `LinearRotationModel` | 新共享 rotation model | 原样迁移 |
| `RotationMouseHandler` | 既有 `RotationManager` | 复用共享旋转优先级、平滑和生命周期 |
| `PlayerUtility.getClosestVectorToBoundingBox` | 新的精确 helper | 使用 26.1.2 AABB + pick radius |
| `AntiBotsModule.shouldFilter` | `AntiBot.INSTANCE.isBot/isBedWarsBot` | 现有迁移逻辑可复用 |
| `LocalDataWatch.friendList` | `Managers.FRIEND.getFriends()` | AutoThrow 内不区分大小写比较 |
| `TeamsModule` | 新增 `modules.impl.combat.Teams` | 独立模块，保留 Color/Scoreboard |
| `ScaffoldModule` | `Scaffold.INSTANCE` | 直接映射 |
| `SsngScaffoldModule` | Graven `Scaffold.INSTANCE` | Ssng/Telly 能力已并入现有 Scaffold，不重复注册暂停条件 |
| `StuckModule` | `Stuck.INSTANCE` | 直接映射 |
| `BlinkModule` | `Blink.INSTANCE` | 直接映射 |
| `interactItem` | `mc.gameMode.useItem` | 26.1.2 已核验 |
| `swingHand` | `mc.player.swing` | 26.1.2 已核验 |
| Beta edition catalog | `ModuleHolder.initModules()` | 注册 AutoThrow 与 Teams |

EdOpal `getSuffix()` 返回排序后的 `minDistance + " - " + maxDistance`。Graven 应通过 `getInfo()` 保留该模块列表信息，不能返回原始未排序设置值。

## 9. 外部依赖

AutoThrow 没有额外 Maven、native、Fabric 或 NeoForge 专有依赖。其“特有依赖”全部是 EdOpal 内部子系统：Teams、AntiBots、好友存储、鼠标旋转控制、事件总线和若干暂停模块。

迁移后：

- `common/` 只依赖 Minecraft API 和 Graven 自身公共层。
- Fabric/NeoForge 不新增加载器专有代码。
- 不新增第三方库。
- 需要新增公共事件/Mixin、旋转模型/控制器、Teams、AutoThrow、ModuleHolder 注册和 i18n 项。

## 10. 实现顺序

1. 新增并单测确定性的 `LinearRotationModel`、灵敏度量化和 AABB 最近点 helper。
2. 使用既有 RotationManager 的 SILENT/SNAP 事件链，AutoThrow 不接管 MouseHandler，也不修改客户端视角。
3. 新增严格 post movement 事件并验证乘客、非乘客、取消三个分支。
4. 迁移独立 Teams 模块及 Color/Scoreboard 两种模式。
5. 实现 AutoThrow 设置、状态机、筛选器、弹道、计划和槽位恢复。
6. 注册 AutoThrow/Teams，生成并填写 `en_us.json`、`zh_cn.json` 的模块、设置和枚举翻译。
7. 更新事件、模块和旋转文档。
8. 编译 common、Fabric、NeoForge；Mixin/启动路径变化后运行完整 `buildRelease`。

## 11. 验证矩阵

### 11.1 纯逻辑

- Min/Max 正序、反序和相等。
- Delay 的 `>` 边界、启用重置、计划创建重置、清除计划不重置。
- 副手、当前槽、其余 hotbar 的固定选择优先级。
- 蛋、雪球与其他物品的计划创建/失效。
- 弹道水平距离接近零、负判别式、NaN、低弹道根。
- yaw 跨 `-180/180`，pitch 边界及灵敏度量化。
- rotationTicks 为 1、2、3、4，以及大角差被限制为 4。

### 11.2 目标行为

- Player/Animal/Mob 四类设置组合。
- 初选 FOV 边界使用严格 `<`，180 直接放行。
- 初选隐身过滤，锁定后改变隐身不取消。
- 锁定后离开 FOV不取消。
- 锁定后失去视线、死亡、变观察者、超距、变好友/队友/bot 时取消。
- 同时存在多个候选时按三维平方距离选最近，不受 sharedTarget 影响。
- 大垂直差目标不进入初选盒；锁定后垂直离开盒但水平距离仍有效。

### 11.3 包与生命周期

- 主手换槽包、UseItem、Swing 的顺序。
- UseItem 包 yaw/pitch 与控制器当次旋转一致。
- 非乘客 movement post 后恢复槽位。
- 乘客 movement post 后恢复槽位。
- SendPosition 被取消时不发布 post，下一次完成或禁用时恢复。
- Screen、overlay、Scaffold、Stuck、Blink 启用时清计划。
- 世界退出、玩家为空、禁用时无残留计划或临时槽位。

### 11.4 构建与运行

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot'
.\gradlew.bat :common:compileJava :fabric:compileJava :neoforge:compileJava
.\gradlew.bat buildRelease
```

运行验证应同时记录客户端包序列和服务器观察结果，至少覆盖静止目标、移动目标、目标突然失效、背包槽变化、乘坐载具及移动包取消。

## 12. 当前兼容性报告

- **保留：** 全部设置、默认值、状态字段、`elapsed > delay` 边界、状态机顺序、投掷物优先级、目标初选和锁定复验差异、低弹道公式、最多四 tick 的线性转向、鼠标灵敏度量化、投掷包角度、槽位恢复时机、暂停条件以及 Teams 的 Color/Scoreboard 判定均已实现。AutoThrow 和 Teams 已注册到 `ModuleHolder`，双语 i18n 已补齐。
- **更改：** Yarn 名称适配到 Mojmap 26.1.2；SsngScaffold 的能力已并入 Graven Scaffold，因此只检查现有 Scaffold；Teams UI 分类从 Graven 不存在的 WORLD 适配为 COMBAT；AutoThrow 的线性请求改由既有 `RotationManager` 输出服务端角度，移除对 `MouseRotationController`/`LocalPlayer#turn` 的直接调用，使投掷过程不改变屏幕视角；`UseItemEvent` 读取当前 RotationManager 角度，保证投掷包与静默旋转一致。这些变更是用户要求的视觉行为修正，不改变目标筛选、弹道、计划 tick 或槽位恢复合同。
- **移除：** 无。
- **未实现：** 无源码项缺失。真实多人服务器仍需按 11.3 的矩阵采集客户端与服务端包序列，作为环境级验证；本地开发启动已验证 Mixin 应用、模块注册和 i18n 生成。
