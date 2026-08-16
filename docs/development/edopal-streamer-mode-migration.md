# EdOpal Streamer Mode 严格迁移

## 1. 参考范围

本迁移以以下 EdOpal 源码为唯一行为基线：

- `StreamerModeModule`：设置、用户名过滤和 Hypixel 传送消息处理。
- `TextVisitFactoryMixin`：所有格式化文本访问前的全局字符串过滤。
- `MessageHandlerMixin` 与 `ChatReceivedEvent`：玩家、伪装、系统及 overlay 消息的可取消事件。
- `InGameHudMixin#renderScoreboardSidebar`：Hypixel 计分板首行 Server ID 混淆。
- `KnownServerManager`、`ProxyServer` 与 `HypixelServer.SERVER_BRAND_PATTERN`：Hypixel 直连和代理识别。

目标环境为 Minecraft 26.1.2、Mojmap、Java 25，并继续放在 `common/`，不引入 Fabric 或 NeoForge 专有调用。

## 2. 设置与公开行为

| EdOpal 设置 | 默认值 | Graven 映射 | 行为 |
|---|---:|---|---|
| `Hide server ID` | `true` | `BoolSetting` | 仅在识别为 Hypixel 时处理传送消息和计分板首行 |
| `Hide username` | `true` | `BoolSetting` | 在格式化文本访问入口替换本地会话用户名 |
| `Custom username` | `"You"` | `StringSetting` | 仅在隐藏用户名时显示；读取时执行 `trim()` |

模块名保留为 `Streamer Mode`。EdOpal 的 `VISUAL` 分类映射到 Graven 的 `Category.RENDER`。

用户名过滤合同：

1. 只有模块启用且 `Hide username=true` 时处理。
2. 自定义用户名先去除首尾空白。
3. 去除空白后为空时保持原文本。
4. 使用大小写不敏感、非正则的全量替换。
5. 搜索串是 `Minecraft#getUser().getName()`，不使用实体显示名或配置中的旧用户名。

## 3. 文本访问映射

EdOpal 修改 `TextVisitFactory.visitFormatted(String, int, Style, Style, CharacterVisitor)` 的第一个参数。26.1.2 的对应类与方法是：

```text
TextVisitFactory                         -> StringDecomposer
visitFormatted(String,int,Style,Style,*) -> iterateFormatted(String,int,Style,Style,FormattedCharSink)
```

迁移使用 `@ModifyVariable(argsOnly = true, ordinal = 0)` 修改该重载的 `String` 参数。该位置同时覆盖聊天、计分板、名称、Tab、标题、GUI 文本的测量和绘制，并保留原组件 Style；不能只在某个 HUD 或 Chat 绘制函数中替换。

## 4. ChatReceivedEvent 映射

26.1.2 的 `ChatListener` 对应旧版 `MessageHandler`。事件映射如下：

| EdOpal 路径 | 26.1.2 注入点 | 取消结果 |
|---|---|---|
| 签名玩家消息 | `showMessageToPlayer` 的两个 `addPlayerMessage` 调用前 | 返回 `false` |
| 伪装/无 Profile 消息 | `lambda$handleDisguisedChatMessage$0` HEAD | 返回 `false` |
| 系统消息 | `handleSystemMessage` HEAD | 取消方法 |
| Action bar/overlay | `handleOverlay` HEAD | 取消方法 |

签名玩家消息保留 EdOpal 的两个显示分支：未过滤分支发布 `decoratedMessage`，过滤分支重新取得过滤后的正文并应用 `ChatType.Bound` 装饰。注入点位于原版安全性、屏蔽名单、完全过滤和接收能力检查之后；取消会从显示方法返回 `false`，不会继续旁白、聊天日志和消息时间戳更新。伪装消息在延迟队列真正执行的合成方法中发布，因此不会提前绕过原版消息延迟。

事件继续携带只读 `Component text` 和可修改 `overlay` 标志，并继承 Graven `Cancellable`。新事件加入 Lua 事件注册表。

## 5. Hypixel 识别

保留 EdOpal 的端口、域名和 Brand 规则：

- 端口必须为 `25565`。
- 直连域名允许自身和任意子域：`hypixel.net`、`hypixel.io`、`technoblade.club`。
- 代理域名允许自身和任意子域：`liquidproxy.net`、`nyap.buzz`。
- Brand 正则为 `Hypixel BungeeCord \(.+\) <- .+`。
- 直连在 Brand 尚未收到时仍视为 Hypixel；收到不匹配 Brand 后不再视为 Hypixel。
- 代理只有在收到匹配 Brand 后才视为 Hypixel。

这里不迁移 Hypixel Mod API：Streamer Mode 只读取“当前是否为 Hypixel”，不读取游戏类型、地图、机器人或床色；地址和 Brand 已完整覆盖该模块实际依赖的识别合同。

## 6. Server ID 处理

### 6.1 传送消息

当模块启用、`Hide server ID=true` 且当前服务器为 Hypixel 时：

1. 读取 `event.text.getString()`。
2. 仅匹配以 `Sending you to ` 开头的消息。
3. 取消原消息。
4. 通过全量 `replace("Sending you to ", "").replace("!", "")` 提取 Server ID。
5. 本地显示 `§aSending you to §k<serverId>§r§a!`，不添加 Graven 前缀。

### 6.2 计分板

只处理 `Gui#displayScoreboardSidebar` 中排序、截断后的第一个条目。若其纯文本同时包含 `/` 和两个连续空格，则按正则 `" {2}"` 分割；当结果至少有两段时替换为：

```text
§7<parts[0]>  §8§k<parts[1]>
```

其余条目、计分板标题和分数保持原样。该转换发生在文本提交前，宽度计算仍沿用 EdOpal 的顺序，即先按原始文本计算布局，再替换显示内容。

## 7. 映射表

| 参考模块/类/函数 | Graven 对应项 |
|---|---|
| `StreamerModeModule` | `modules.impl.render.StreamerMode` |
| `filter(String)` | 同名方法，保留 trim 与 replaceIgnoreCase |
| `ChatReceivedEvent` | `events.impl.ChatReceivedEvent` |
| `MessageHandlerMixin` | `MixinChatListener` |
| `TextVisitFactoryMixin` | `MixinStringDecomposer` |
| `InGameHudMixin` 首行处理 | `MixinGui` 的 scoreboard name 调用包装 |
| `KnownServerManager` Streamer 子集 | `StreamerMode#isOnHypixel()` |
| `ChatUtility.display` | `ChatUtils.addChatMessage(false, Component)` |
| Beta 模块目录 | `ModuleHolder.initModules()` |

## 8. 外部依赖

本迁移不增加 Maven、native 或加载器依赖。`StringUtils.replaceIgnoreCase` 来自 Minecraft 运行时已有的 Apache Commons Lang；Minecraft、MixinExtras 和 Graven EventBus/Setting DSL 已在现有构建中提供。

## 9. 验证矩阵

- 模块禁用、各设置开关和空白 Custom username。
- 用户名大小写变体、重复出现、子串和带 Style 的组件。
- 直连 Hypixel、三个备用域、两个代理域、非 25565 端口、假 Hypixel Brand。
- 普通聊天、签名聊天、伪装聊天、系统消息和 overlay 的事件取消。
- `Sending you to ` 精确前缀、多个 `!`、空 Server ID 和非 Hypixel 消息。
- 计分板首行匹配/不匹配、非首行匹配、多个双空格及少于两段的 split 结果。
- common、Fabric、NeoForge 编译，完整 `buildRelease` 和 Fabric 开发启动 Mixin 检查。

## 10. 兼容性报告

- **保留：** 设置、默认值、依赖可见性、全局过滤位置、大小写不敏感替换、聊天取消与本地重发、Hypixel 判断、计分板首行条件和混淆格式。
- **更改：** Yarn 类名和旧 HUD 渲染方法适配到 Mojmap 26.1.2 的提取式 GUI；EdOpal KnownServer 状态机收敛为只包含 Streamer Mode 实际读取结果的等价判定。
- **移除：** 无 Streamer Mode 行为被移除。
- **未实现：** 无。
