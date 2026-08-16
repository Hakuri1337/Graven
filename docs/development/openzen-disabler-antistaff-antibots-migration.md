# OpenZen Disabler、AntiStaff、AntiBots 迁移分析

## 1. 文档范围与结论

本文是 `SkidProjects/OpenZen` 到 Graven 的迁移分析、实现记录与验证方案。三个模块已经按本文映射接入 Graven，参考实现和 26.1.2 本地源码仍是兼容性核验依据：

- OpenZen：`SkidProjects/OpenZen`，Minecraft 1.20.1、Forge 47.4.20、Java 17。
- Graven：Minecraft 26.1.2、Fabric 0.150.0+26.1.2、NeoForge 26.1.2.76、Java 25。
- Minecraft 26.1.2 参考源码：`reference/vanilla-26.1.2`。

三个模块不能通过复制 Java 文件完成迁移。OpenZen 的包事件、Forge/ASM Patch、反射访问和模块注册均与 Graven 不同；Graven 已经提供了跨 Fabric/NeoForge 的 `PacketEvent.Send`/`Receive`、严格按运行时 class 分发的 EventBus、`MixinConnection` 和 `ModuleHolder`。正确迁移路径是保留模块的行为状态机，把平台接入改写为 Graven 的共享事件与 Mixin 层，并针对 26.1.2 的包模型重新核验字段和生命周期。

实现落点：`Disabler` 扩展现有 Player 模块并保留原有 Sprinting/Input 容器分支；`AntiStaff` 新增为 Player 模块并注册到 `ModuleHolder`；OpenZen `AntiBots` 合并到现有 `AntiBot.INSTANCE`，同时让 `TargetManager` 使用确认 bot 与新玩家超时两类查询。

## 2. OpenZen 架构与调用链

### 2.1 启动和模块生命周期

OpenZen 通过 `ModuleManager.initModules()` 逐个 `new` 模块，`register()` 再反射扫描字段并收集 `Setting`。模块实例保存 `INSTANCE` 静态引用；启用时向 OpenZen EventBus 注册，禁用时注销。`ModulesConfig` 只持久化 `name:key:enabled`，Setting 的持久化由各模块配置系统完成。

Graven 则在 `ModuleHolder.initModules()` 注册单例，`Module.setEnabled()` 负责 EventBus 订阅、通知和 `onEnable/onDisable`，Setting 通过 `SettingHost` DSL 在实例字段上注册，并由配置系统保存到用户目录 `.graven/`。迁移模块必须使用 Graven 的单例和注册流程，不能保留 OpenZen 的反射注册或 Forge 生命周期回调。

### 2.2 事件与网络包

OpenZen 有两种包入口：

1. Forge/ASM Patch 在网络处理链上生成 `ReceivePacketEvent` 和 `PacketSendEvent`，事件对象只携带包，取消由 Patch 代码决定是否继续调用 `packet.handle` 或底层发送。
2. `PacketEvent` 同时携带 incoming 标志，模块自行判断方向。`EventBus` 只按事件的精确运行时 class 分发，并按 `EventTarget` 优先级排序；可取消事件在首次取消后停止继续分发。

Graven 的 `MixinConnection` 已在 `Connection.channelRead0` 和 `Connection.send` 处发布独立的 `PacketEvent.Receive`/`PacketEvent.Send`。修改包使用 `event.setPacket()`，丢弃使用 `cancel()`；绕过模块事件发送使用 `PacketUtils.sendSilently()`。Graven 的 Manager（`Managers.S2CPACKET`、`Managers.C2SPACKET`）在模块事件之前运行，迁移必须确认不会与包缓存或 Blink 冲突。

### 2.3 OpenZen 特有外部依赖

| 依赖/机制 | OpenZen 用途 | Graven 对应情况 | 迁移结论 |
|---|---|---|---|
| Forge 47.4.20、ForgeGradle | 1.20.1 加载和重映射 | Fabric Loom + NeoForge ModDev | 模块放入 `common`，不导入 Forge API；仅在平台 Mixin 中处理差异 |
| ASM 9.6（compile/runtime） | `PatchAgent`、网络 Patch、反射辅助 | ASM 9.8 仅作为项目现有依赖 | 不迁移 PatchAgent 或 Java Agent；使用现有 Mixin 入口 |
| `PatchAgent`、`-javaagent` | 类加载前安装和重转换 Patch | Graven 没有 Agent 启动合同 | 不新增 Agent；否则会破坏 Fabric/NeoForge 启动和发布结构 |
| DevAuth-forge 1.2.2 | 开发环境认证 | Graven 无此运行时依赖 | 不属于三个模块的运行依赖，不迁移 |
| Lombok 1.18.34 | OpenZen getter/setter/generated 构造器 | Graven 源码不依赖 Lombok | 迁移时写出显式访问器或使用现有字段 API |
| Unsafe/反射字段访问 | 修改 `ServerboundMovePlayerPacket` yaw/pitch | 26.1.2 参考源码中 `yRot/xRot` 为 public | 只在源码核验后直接访问字段；不复制 Unsafe/映射反射链 |
| native DLL、CMake、vcpkg、Qt、UPX | OpenZen Loader 与发布 | Graven 无 native Loader | 与三个模块无关，明确排除 |

## 3. Disabler 分析

源码：`SkidProjects/OpenZen/src/main/java/shit/zen/modules/impl/exploit/Disabler.java`。

### 3.1 Setting 与状态

| OpenZen Setting | 默认值 | 作用 |
|---|---:|---|
| `Grim Bad PacketsA` | true | 取消连续相同的 `ServerboundSetCarriedItemPacket`（排除 -1） |
| `Grim Duplicate RotPlace` | true | 记录旋转包到放置包之间的 yaw/pitch 差值，重复差值时加入 0.001～0.010 的随机扰动 |
| `ACA Fast Switch` | true | 两个非相邻、非 0↔8 环绕的快捷栏切换之间补发中间槽位包 |
| `ACA Inventory Frequency` | false | 容器打开后 150ms 内关闭时取消并延迟释放关闭包 |
| `ACA Aim Step` | true | 当 yaw 或 pitch 一轴几乎不变而另一轴变化超过 1 度时加入高斯微扰 |
| `ACA Perfect Rotation` | true | 对 5.625 度步长集合和 360 度倍数的旋转差加入高斯微扰 |
| `Themis Blink` | true | 统计移动 StatusOnly/Pong 包，每 200ms 在计数为 0 时补发 id=0 的 Pong |
| `Only Remote Server` | false | 单人世界中跳过全部处理 |
| `Logging` | false | 通过聊天输出每次修改、取消、延迟和补发 |

状态字段包括：上次槽位、容器打开时间/标志、关闭包和释放延迟、Themis 计时与计数、上次发送 yaw/pitch、当前旋转与上一旋转差、上一放置差、旋转 pending 标志、随机数源。`onEnable` 与 `onDisable` 都调用 `resetState()`；收到 `ClientboundLoginPacket`、玩家无效、观察者、死亡或 `ProgressScreen` 时也会清理状态。

### 3.2 精确事件顺序

OpenZen 的 `onPacket(PacketEvent)` 没有方向过滤，因此同一方法接收入站和出站包，顺序如下：

1. 检查玩家、单人世界限制；收到登录包则清理并返回。
2. 玩家状态无效时清理并返回。
3. 若延迟关闭包已到期，先通过 `PacketUtil.sendQueued` 发送并清空 pending。
4. 看到 `ClientboundOpenScreenPacket` 时记录容器打开时间。
5. 处理快捷栏包：重复槽位取消；需要时补发中间槽位；最后更新 `lastSentSlot`。
6. 处理容器关闭频率：打开后不超过 150ms 时取消原包、保存包、设置剩余延迟并清除打开标志；否则放行并清除标志。
7. Themis 计数和 200ms 补发逻辑运行，移动 StatusOnly 与 Pong 都计数。
8. Grim Duplicate RotPlace 读取旋转包；放置包到达时把本次旋转差保存为下一次比较基准。
9. ACA Aim Step 和 Perfect Rotation 依次修改同一个移动包，最后更新 `lastYaw/lastPitch`。

取消和修改必须保留该顺序。特别是中间槽位和延迟关闭包必须绕过当前 Disabler 的发送监听，否则会递归处理或重复计数。

### 3.3 26.1.2 API 断点

- `ServerboundMovePlayerPacket` 仍有 `Pos`、`PosRot`、`Rot`、`StatusOnly`，但 26.1.2 参考源码直接公开 `yRot`、`xRot`，同时提供 `getYRot(fallback)`/`getXRot(fallback)`。不能复用 OpenZen 的 `ReflectionUtil` 字段名和 Unsafe 缓存。
- `ServerboundPongPacket` 从 `net.minecraft.network.protocol.game` 移到 `net.minecraft.network.protocol.common`，监听器类型也从 `ServerGamePacketListener` 变为 `ServerCommonPacketListener`。
- `ServerboundUseItemOnPacket` 的构造和字段仍然存在，但加入 `InteractionHand`、`BlockHitResult`、sequence 的 26.1.2 形式；Disabler 只需识别类型，不构造该包。
- `ServerboundSetCarriedItemPacket`、`ServerboundContainerClosePacket`、`ClientboundOpenScreenPacket`、`ClientboundLoginPacket` 均存在并保留相应 getter。
- Graven 已有 `PacketEvent.Send`/`Receive`。Disabler 应拆成方向明确的两个处理器：入站只接收登录/打开屏幕，出站只处理快捷栏、关闭、Pong、移动和放置包；不能继续依赖 OpenZen 的 incoming 反转语义。
- 原实现的 `PacketUtil.sendQueued` 会标记包以绕过下一次发送事件。Graven 对应应使用 `PacketUtils.sendSilently`，并在验证中确认 `MixinConnection` 的 `bypassedPackets` 在异常或断线时不会残留。

### 3.4 迁移边界

保留所有 Setting、默认值、阈值、随机扰动范围、槽位环绕判断、pending 清理和日志分支。`ReflectionUtil`、ASM Patch、Forge 注解、OpenZen Timer 和 ChatUtil 不迁移；分别映射到 Graven Setting DSL、Mixin 已发布字段、`System.nanoTime/currentTimeMillis` 计时或现有时间工具、`ChatUtils`。新增模块放在 `common/modules/impl/player/Disabler`，通过 `ModuleHolder` 注册，并补齐 `en_us.json`、`zh_cn.json`。

## 4. AntiStaff 分析

源码：`SkidProjects/OpenZen/src/main/java/shit/zen/modules/impl/world/AntiStaff.java`。

模块没有 Setting，构造时解码固定 Base64 字符串为 UTF-8 名称列表，并对每个包重新解码和 `split(",")`。入站 `ClientboundPlayerInfoUpdatePacket` 含 `ADD_PLAYER` 时比较 profile name 和 display name；命中立即打印 `Staff detected!`，再通过 `mc.player.connection.sendCommand("hub")` 发送服务器命令。1.20.1 的 `ClientboundAddPlayerPacket` 到达时，模块从 `mc.level` 按 entity id 查实体并比较实体名称。

### 4.1 26.1.2 API 断点与正确映射

- 26.1.2 没有 `ClientboundAddPlayerPacket`，使用通用的 `ClientboundAddEntityPacket`。该包提供 `getId()`、`getUUID()`、`getType()`；玩家实体添加时需按 `getType() == EntityType.PLAYER` 且 UUID 与待检测 profile 对应。
- `ClientboundPlayerInfoUpdatePacket.Entry` 在 26.1.2 提供 `profileId()`、可空 `profile()`、可空 `displayName()`。只在 `ADD_PLAYER` action 下读取 profile name；其他 action 的 profile 可能为 null。
- 发送 `/hub` 应走 Graven 的客户端命令发送 API，并确认命令发送不会被 `PacketEvent.Send` 或其他模块拦截。不得直接调用过时的 Forge connection 方法。
- Graven 的包事件在 Netty/连接处理链进入游戏处理前触发；命中后应只执行一次退出动作，建议保存当前连接或命中 UUID 作为 pending，避免同一批次多个 Entry 重复发送 `/hub`。

### 4.2 固定名单与配置

严格行为迁移要求保留 OpenZen 的 Base64 内容、UTF-8 解码、逗号分隔和大小写敏感的 `List.contains` 语义。实现阶段可以在 `onEnable` 解码为不可变集合以避免每个包重复解码，但必须用单元测试证明名称集合和空字符串行为一致；不要擅自改成模糊匹配、大小写不敏感匹配或网络下载名单。若将名单暴露为 Setting，默认值必须由原 Base64 解码得到，且配置缺省时仍保持同一名单。

## 5. AntiBots 分析与 Graven AntiBot 对比

OpenZen 源码：`SkidProjects/OpenZen/src/main/java/shit/zen/modules/impl/combat/AntiBots.java`。Graven 现有实现：`common/src/main/java/tech/hakuri/graven/modules/impl/combat/AntiBot.java`。

### 5.1 OpenZen 行为

OpenZen 提供两个静态查询：

- `isBot(Entity)`：只要 entity id 在 `confirmedBotIds` 中即为 bot。
- `isBedWarsBot(Entity)`：entity id >= 1,000,000,000 或 <= -1、名称为空、scoreboard 名为空直接判定；否则在 `playerAddTimes` 中存在且加入时间小于 `Respawn Time`（默认 2500ms）时判定。

网络状态机分为三张表：

1. 收到带 `ADD_PLAYER` 的玩家信息包时记录 profile UUID 的加入时间。
2. 收到显示名无 siblings 且 game mode 不是 SURVIVAL 的玩家信息 Entry 时写入 `suspectJoinTimes`/`suspectNames`；随后收到对应 UUID 的玩家实体添加包即确认 bot，记录 entity id 和名称。
3. 收到 swing 主手 `ClientboundAnimatePacket(action=0)` 时移除该实体的临时加入时间；收到 `ClientboundRemoveEntitiesPacket` 时移除确认 id。

世界切换清空 suspect、confirmed id 和名称；每个 post motion 检查 suspect 超过 500ms 的条目并输出调试信息。`KillAura` 在目标筛选中调用 `isBot` 和 `isBedWarsBot`，因此 AntiBots 状态直接影响战斗模块。

### 5.2 已知实现缺陷（迁移前必须决定）

- `confirmedBotIds` 使用 `HashSet`，其余表使用 `ConcurrentHashMap`；包事件、Motion 和目标线程交错时存在并发修改风险。
- `confirmedBotNames` 在实体移除时只读取不删除，会长期积累名称。
- `suspectNames` 只在 Entry 满足 displayName/gameMode 条件时写入；调试输出中的 `Sky_Yuanxiao` 分支是硬编码诊断，不是识别规则。
- `ClientboundAddPlayerPacket` 在 26.1.2 不存在，且在旧版本中通过 `mc.level.getEntity` 查实体可能早于实体真正加入，直接复制会出现漏判。
- `isBedWarsBot` 在 `INSTANCE == null` 或模块未注册时没有保护；Graven 的 TargetManager 可能在模块初始化早期调用，因此迁移实现必须提供空连接/空实例保护。
- `newPlayerTimeout` 使用 wall-clock 毫秒，暂停、系统时间回拨和跨世界残留都需要在生命周期测试中覆盖。

这些是参考实现事实，不应在文档中隐藏。严格迁移时保留可观察识别规则，但必须修复会导致崩溃或跨平台不确定性的集合并发和生命周期问题，并在兼容性报告中列为更改。

### 5.3 Graven AntiBot 的差异

Graven `AntiBot.isBot` 只有一条规则：模块启用且实体 UUID 不在 `mc.getConnection().getOnlinePlayerIds()` 中即判定 bot。它没有加入时间、玩家信息与实体添加关联、游戏模式/displayName 条件、确认 id 集合、世界切换清理或调试输出。

因此 OpenZen AntiBots 不是对现有 AntiBot 的局部补丁，而是一次行为扩展。建议保留 Graven `AntiBot.INSTANCE` 和 `TargetManager` 的公共查询入口，在其内部增加 OpenZen 的状态机；不要再创建第二个 `AntiBots.INSTANCE`，否则 KillAura、TargetManager 和外部 Addon 会出现两个不一致的 bot 判定源。

### 5.4 26.1.2 映射

| OpenZen 1.20.1 | Graven 26.1.2 |
|---|---|
| `ClientboundPlayerInfoUpdatePacket.Entry.profile().getId()` | `Entry.profileId()` |
| `GameProfile.getName()` | `GameProfile.name()` |
| `ClientboundAddPlayerPacket.getPlayerId()` | `ClientboundAddEntityPacket.getUUID()` |
| `ClientboundAddPlayerPacket.getEntityId()` | `ClientboundAddEntityPacket.getId()` |
| `ClientboundRemoveEntitiesPacket.getEntityIds()` | 仍为 `IntList`，按 `for (int id : packet.getEntityIds())` 清理 |
| `WorldChangeEvent` | `GameJoinedEvent`、`GameLeftEvent` 或 `LevelUpdateEvent`，按实际生命周期选择；至少在 `GameLeftEvent` 清空所有表 |
| `MotionEvent.isPost()` | `PlayerTickEvent.Post`；清理 suspect 时保证在客户端线程执行 |
| `AntiBots.isBot` | 扩展 `AntiBot.isBot`，保持 TargetManager 调用不变 |

对玩家实体添加包，优先按 `getType() == EntityType.PLAYER`、UUID 和待确认 profile 关联；如果服务端先发实体后发 Tab 信息，则保留 `pendingEntityByUuid`，在后续 `ADD_PLAYER` 到达时完成确认，不能只依赖单一包顺序。

## 6. 参考模块到 Graven 的映射表

| 参考对象 | Graven 对应项 | 处理方式 |
|---|---|---|
| `shit.zen.modules.impl.exploit.Disabler` | `tech.hakuri.graven.modules.impl.player.Disabler` | 保留单例、Setting、状态机；拆分 Send/Receive 处理器 |
| `shit.zen.modules.impl.world.AntiStaff` | `tech.hakuri.graven.modules.impl.player.AntiStaff` 或 `world` 分类 | 分类按现有 UI 约定决定；行为只依赖网络包和客户端命令 |
| `shit.zen.modules.impl.combat.AntiBots` | `tech.hakuri.graven.modules.impl.combat.AntiBot` | 扩展现有公共查询，不新建第二套 bot 服务 |
| `EventTarget`/OpenZen EventBus | `@EventHandler`/Graven EventBus | 方法返回 void、单一对象参数；依赖精确事件 class 分发 |
| `PacketUtil.sendQueued` | `PacketUtils.sendSilently` | 所有内部补发包绕过当前事件；验证 bypass 集合清理 |
| `ChatUtil.print` | `ChatUtils.addChatMessage` | 所有可见文案进入 i18n，调试日志不吞异常 |
| `ModuleManager.register` | `ModuleHolder.initModules` | 只在 Holder 注册，不保留反射 Setting 收集 |
| `Timer` | 模块实例字段 + Graven 时间工具/毫秒时间 | 禁止 static 全局计时器；disable/join/left 清理 |

## 7. 实施记录与后续验证

1. 已完成 Graven 包事件接入：Disabler 使用 `PacketEvent.Send`/`Receive`，内部补发使用 `PacketUtils.sendSilently`。
2. 已完成 Disabler 状态与数学逻辑：槽位插值、0↔8 环绕、角度 wrap、已知步长、150ms/200ms 边界和 enable/disable/world reset。
3. 已完成 Fabric/NeoForge common 编译，两个平台共享 `MixinConnection` 发送链。
4. 已完成 AntiStaff 名单解码、一次性 hub pending、profile/display/entity 三路检测和断线清理。
5. 已完成 AntiBot UUID/实体关联、Tab 先到/实体先到、移除包、世界清理和并发集合修复，并接入 `TargetManager`。
6. 已完成 `en_us.json`、`zh_cn.json` 模块与消息 key；剩余工作是按验证矩阵进行真实服务器包序列回放。

## 8. 验证矩阵

| 维度 | 必测场景 | 通过条件 |
|---|---|---|
| Disabler 包方向 | 入站 Login/OpenScreen、出站移动/槽位/关闭/Pong/放置 | 只有参考方向的处理器生效，其他包不被误取消 |
| Disabler 状态 | enable、disable、login、disconnect、死亡、ProgressScreen | 所有字段回到初始值，pending 包不再发送 |
| Disabler 边界 | 相同槽位、相邻槽位、0↔8、150ms、200ms、负/NaN 角度 | 与 OpenZen 阈值和分支一致 |
| AntiStaff | profile name、display name、空 profile、重复 Entry、多包同帧 | 命中只发送一次 `/hub`，非命中无副作用 |
| AntiBots | Tab 先到/实体先到、非生存模式、500ms 超时、移除、世界切换 | bot 查询、名称和 UUID 表在每个生命周期正确清理 |
| 目标集成 | KillAura/TargetManager 读取 AntiBot | bot 不进入候选列表，普通玩家不被误过滤 |
| 平台 | Fabric client、NeoForge client | `common` 无平台 import，两个平台编译和启动链均通过 |
| 构建 | `build`、`buildRelease`、`git diff --check` | 无 Mixin 未命中、资源缺失、i18n 非字符串或警告升级为错误 |

## 9. 兼容性报告（分析阶段）

- **保留：** Disabler 的九个开关、默认值、包处理顺序、阈值、随机扰动、pending 状态、AntiStaff 的精确名单与命令、AntiBots 的加入/确认/移除/超时状态机和 TargetManager 查询语义。
- **更改：** Forge/ASM Patch 改为 Graven `MixinConnection` + `PacketEvent`；1.20.1 包类型映射到 26.1.2；OpenZen `AntiBots` 合并到现有 `AntiBot.INSTANCE`；并发集合和断线清理按 Graven 生命周期修复。原因是平台 API、包协议和现有调用者不同，直接复制会导致编译失败、递归发送或状态泄漏。
- **移除：** Java Agent、PatchAgent、Forge 注册、DevAuth、native DLL/Loader、Lombok 生成依赖、OpenZen 反射映射缓存。原因是它们不是三个模块的行为依赖，且与 Graven 的双平台架构冲突。
- **未实现：** 真实服务器环境下的包序列回放和反作弊行为确认尚未执行；代码、平台编译、名单一致性和静态验证已完成。
