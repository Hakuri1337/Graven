# EdOpal NoSlow NoC0F 对比与 Graven 优化

## 参考实现

参考文件：

- `SkidProjects/EdOpal/src/client/java/wtf/oraculus/client/feature/module/impl/movement/noslow/NoSlowModule.java`
- `SkidProjects/EdOpal/src/client/java/wtf/oraculus/client/feature/module/impl/movement/noslow/impl/NoC0FNoSlow.java`

EdOpal 把 NoSlow 拆成模式模块，NoC0F 使用 `NONE -> CANCEL_PONG -> SWAP_HANDS -> USING`
状态机。它缓存 Pong，静默发送交换副手包，等待 `ScreenHandlerSlotUpdate` 或完整库存同步，
再强制恢复使用键；释放、受击、切换副手、背包管理器动作、S08 拉回和模块关闭都会回滚。

Graven 当前 NoSlow 使用同一状态机形状，但原实现存在四个差异：只监听单槽位同步包；
交换和缓存 Pong 通过普通连接发送，容易再次进入本地事件处理；没有交换超时、受击/拉回回滚；
没有固定目标手的 `UseItem` 包；强制 `keyUse` 后缺少原始按键状态恢复。

## Graven 映射

| EdOpal | Graven |
|---|---|
| `CommonPongC2SPacket` | `ServerboundPongPacket` |
| `PlayerActionC2SPacket.SWAP_ITEM_WITH_OFFHAND` | `ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND` |
| `ScreenHandlerSlotUpdateS2CPacket` | `ClientboundContainerSetSlotPacket` |
| `InventoryS2CPacket` | `ClientboundContainerSetContentPacket` |
| `sendPacketSilent` | `PacketUtils.sendSilently` |
| `PlayerPositionLookS2CPacket` | `ClientboundPlayerPositionPacket` / `ClientboundPlayerRotationPacket` |

## 本次优化

- 保留 Vanilla、Jump、Grim1_2、Grim1_3 的原有路径，仅增强 GrimC0F。
- 新增交换等待计数，20 tick 未收到库存同步就回滚并恢复原手。
- `SetSlot` 和 `SetContent` 都能确认交换完成。
- Pong 缓存、交换、关闭容器和恢复包全部走 `PacketUtils.sendSilently`，避免状态机自己拦截
  自己发出的恢复包。
- USING 阶段把 `ServerboundUseItemPacket` 的手修正到交换后的目标手，保持客户端和服务端
  的使用手一致。
- 受击、S08 位置/旋转修正、离开世界、切换模式和禁用模块都会释放队列、可选地反向换手并
  恢复 `keyUse` 原值。
- 使用停止后 10 tick 自动回滚，避免旧状态残留；双手同时持有可食用/饮用物时继续跳过。

## 兼容性报告

- **保留：** NoSlow 的设置、模式枚举、C0F 触发条件、Pong 队列、交换顺序和其他模式。
- **更改：** GrimC0F 增加完整边界回滚、同步包覆盖、目标手修正和静默网络路径。
- **移除：** 无。
- **未实现：** EdOpal 的跨版本按键反射、主手视觉翻转和外部 InventoryManager 查询未移入，
  因为 Graven 26.1.2 的 KeyMapping API 与本项目模块边界已直接提供等价能力，且这些部分
  不属于 C0F 网络状态机本身。

