# EdOpal HUD 与 ESP 视觉迁移

## 目标与边界

本阶段继续 Graven 的 Opal 视觉预设，将 `SkidProjects/EdOpal` 中 Module List、Notifications、
Target Info 和 ESP 的默认视觉与交互状态映射到 Graven。迁移保留 Graven 的 `HudModule`、`UiTree`、
`UiScene`、`Render2DEvent`、配置和 i18n 架构，不引入 EdOpal 的 NanoVG、OverlayModule、Bloom pass
或 LiquidGlassV2 renderer。后者的可见结果使用 Minecraft 26.1.2 圆角 blur、半透明表面、描边和阴影复现。

Opal 分支由 `ClientSetting.ThemePreset.Opal` 控制；非 Opal 预设继续使用现有渲染路径和配置行为。

## 实现状态

ModuleList、Notifications、TargetHUD 与 ESP 的 Opal 分支均已落地。Notifications 已隔离 Legacy 与
Island 消费路径；TargetHUD 保留目标缓存和退场状态机；ESP 保留 Graven 的实体分类筛选，并补齐
EdOpal 的状态图标、分段标签、装备与短附魔名。所有新增设置均已进入 `en_us` 与 `zh_cn` i18n。

## 源码映射

| EdOpal 参考实现 | Graven 对应项 | 保留内容 | 适配内容 |
|---|---|---|---|
| `ToggledSettings` | `ModuleList` 的 Opal 设置 | 小写、后缀、分类过滤、BarMode、缩放、背景与圆角 | `Visible categories` 展开为四个 BoolSetting；LiquidGlassV2 映射为 blur 表面 |
| `ToggledModulesElement` | `ModuleList.renderOpal` | 长度降序、禁用保留到出场结束、总高度随动画变化 | HUD anchor 决定列表展开方向和左右对齐 |
| `ModuleElement` | `ModuleList` 的 Opal 行布局 | 12 px 行高、400 ms 横向 EASE_OUT_EXPO、600 ms 纵向 EASE_OUT_EXPO、200 ms 高度动画、1 px Bar、主题往返渐变 | Product Sans 映射为已安装的 `graven-opal-medium` |
| `NotificationSettings` | `Notifications` 的 Opal 模式设置 | Legacy/Island、彩色图标区、通知持续时间 | Island 状态由现有 `DynamicIsland` 消费，不复制 EdOpal trigger registry |
| `NotificationsElement.renderLegacy` | `Notifications.renderOpalLegacy` | 右下堆叠、100 px 最小宽度、21 px 高度、3 px 间距、400 ms 横向 EASE_OUT_EXPO、底部剩余时间条 | Material 图标映射为 `graven-icons`；LiquidGlassV2 映射为 blur |
| `NotificationsElement.renderIsland` | `DynamicIsland.renderNotifications` | 28 px 条目、22 px 图标槽、标题/说明、剩余时间条、通知优先级 | 继续使用 Graven Notification 对象和替换语义 |
| `TargetInfoSettings` 默认 `PANEL` | `TargetHUD.renderOpal` | 31.5 px 面板、3 px padding、22.5 px 头像偏移、主题渐变血条、心形与数值、五格装备区、受伤头像染色、200/1000 ms 动画 | 目标来源映射为 `KillAura.INSTANCE.target`；编辑器预览映射为本地玩家 |
| `ESPSettings` | `ESP2D` 的 Box/Health/NameTag 设置 | Box 与黑色 stroke、左侧血条、Name/Health/Distance/Equipment、Sneaking/Strength/Invisible/Blocking 指示器 | EdOpal TargetProperty 映射为 Graven 已有实体分类开关和好友判断 |
| `ESPModule.renderBoxIn2D` | `ESP2D.onRender2D` | 实体 AABB 投影、0.5 px Box、队伍色、血条、分段 NameTag 背景、装备层 | 投影继续使用已核验的 8 顶点 `WorldToScreen`；不重复除 GUI scale |

## 生命周期与绘制顺序

1. HUD 元素仍由 `HudElementHolder` 在同一 `UiScene` 中构建，不创建每元素 renderer。
2. ModuleList、Notifications 和 TargetHUD 的 Opal 背景先提交 blur region，再向当前 HUD scope 提交节点。
3. TargetHUD 与 ESP 的物品图标继续由 `GuiGraphicsExtractor.item` 提交，坐标通过 `UiCoordinateMapper`
   从 Graven projection 转换到 Minecraft GUI 坐标。
4. ESP 的 Lumin 节点先作为 Level 层 `UiScene` 提交，物品层随后加入同一个 GUI 提取帧。
5. 资源重载和模块禁用继续关闭 ESP 自有 `UiScene`；HUD 全局场景由 `HudElementHolder` 管理。

## 设置与兼容性

- 现有设置名称与默认值保持不变；新增 Opal 设置使用独立键，旧配置缺失时采用源码默认值。
- ModuleList 非 Opal 的 Compact/Open 行为不变。
- Notifications 非 Opal 的两阶段进出动画不变；Opal Legacy 使用 EdOpal 的滑入滑出。
- TargetHUD 非 Opal 的当前可配置面板不变；Opal 仅替换可见布局，不改变目标保持和延迟血条状态。
- ESP 的实体分类和颜色设置继续生效；Opal 分支补充 NameTag 与状态元素，不改变目标枚举范围。

## 验证矩阵

| 场景 | 预期 |
|---|---|
| 模块快速开关 | 行按宽度排序，横向和高度动画不中断，禁用行在出场完成后移除 |
| 通知新增、刷新、过期 | Legacy 从右侧滑入并在右侧退出；Island 只由 DynamicIsland 显示一次 |
| Target 切换、受伤、消失 | 目标缩放连续，血条缓动，头像受伤染色，出场结束后释放缓存目标 |
| ESP 多实体、屏外实体 | 仅有效投影实体生成节点；方框、血条和 NameTag 对齐同一屏幕 AABB |
| 第一人称本地玩家 | 不渲染本地玩家 ESP |
| Fabric/NeoForge | `compileJava` 均成功，资源处理包含 Opal 字体与透明 Logo |

## 兼容性报告

- **保留：** 参考实现的尺寸、字体角色、动画曲线与时长、排序、颜色插值、进出场、血条、状态指示器和装备层语义。
- **更改：** NanoVG/LiquidGlassV2/Bloom 映射为 Graven 的 LuminGraphics、Minecraft blur 和共享 scene；目标/实体来源映射为 Graven Managers 与 Module API。
- **移除：** EdOpal OverlayModule 注册器、独立 Bloom pass、NanoVG GL handle 和 shader 生命周期；这些属于已明确不迁移的视觉系统。
- **未实现：** 无视觉分支缺口；EdOpal TargetInfo 的 Compact/Gay/Rvn/LiquidGlass/DynamicIsland 额外模式不属于本次默认 Panel 视觉范围。
