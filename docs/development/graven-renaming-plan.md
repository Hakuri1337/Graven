# Graven 重命名实施规划

## 1. 目标与边界

本规划针对当前双加载器项目（Minecraft `26.1.2`、Fabric、NeoForge），目标是：

| 项目维度 | 当前值 | 目标值 | 处理方式 |
|---|---|---|---|
| 显示名称 | `Epsilon` | `Graven` | 构建属性、Fabric/NeoForge 元数据、日志和文档统一更新 |
| Mod ID / 资源命名空间 | `epsilon` | `graven` | `mod_id`、`assets/`、Mixin 文件名、Fabric 自定义 entrypoint key 同步更新 |
| Maven group / Java 根包 | `com.github.epsilon` | `tech.hakuri.graven` | 迁移三个源码集的目录、package 声明和全部 import |
| 共享主类 | `EpsilonCommon` | `graven` | 严格按要求使用小写 Java 类名 `tech.hakuri.graven.graven`，保留原初始化顺序 |
| Fabric 入口 | `EpsilonFabric` | `GravenFabric` | 保留 `ClientModInitializer` 生命周期，元数据入口改为新全限定名 |
| NeoForge 入口 | `EpsilonNeoForge` | `GravenNeoForge` | 保留 `@EventBusSubscriber` 和 `init()` 生命周期 |
| 配置根目录 | `%USERPROFILE%\\.epsilon` | `%USERPROFILE%\\.graven` | 首次启动迁移旧目录，保留旧目录回退读取 |

本规划记录改名前基线与目标；实施过程中以当前源码、构建配置和本文件的兼容策略为准。

## 2. 当前架构基线

当前初始化链路不能改变：

```text
Fabric/NeoForge 平台入口
  -> 平台 Addon 收集
  -> tech.hakuri.graven.graven.init()（改名后的共享入口）
     -> ModuleHolder.initModules()
     -> HudElementHolder.initElements()
     -> AddonHolder.setupAddons()
     -> ConfigHolder.initConfig()
     -> 语言选择与重载
     -> Managers.initManagers()
     -> Render3DScheduler.init()
     -> LuaScriptManager.init()
     -> 生成空 i18n 模板
```

必须保持模块注册、Addon 收集、配置加载、Managers 初始化和 Lua 初始化的先后关系；改名不能引入新的静态初始化依赖，也不能把平台 API 放进 `common/`。

## 3. Java 包和类映射

### 3.1 包路径

对 `common/src/main/java/com/github/epsilon`、`fabric/src/main/java/com/github/epsilon`、`neoforge/src/main/java/com/github/epsilon` 执行结构化目录迁移：

```text
com.github.epsilon              -> tech.hakuri.graven
com.github.epsilon.<subpackage> -> tech.hakuri.graven.<subpackage>
```

迁移必须同时更新 package 声明、import、Mixin 配置中的 package、BuildConfig 生成包、Lua 代码生成输入/输出和文档示例。不得只替换 import 而保留旧目录。

### 3.2 类名

| 当前类 | 目标类 | 说明 |
|---|---|---|
| `EpsilonCommon` | `graven` | 共享主类，名称大小写按用户要求固定为小写；保留 `init()` 签名和初始化顺序 |
| `EpsilonFabric` | `GravenFabric` | Fabric 客户端入口，避免平台入口与小写主类混淆 |
| `EpsilonNeoForge` | `GravenNeoForge` | NeoForge 静态事件入口 |
| `EpsilonAddon` | `GravenAddon` | Addon 基类及其所有引用同步迁移 |
| `EpsilonAddonSetupEvent` | `GravenAddonSetupEvent` | common/NeoForge 对应事件分别迁移 |
| `FabricEpsilonAddonEntrypoint` | `FabricGravenAddonEntrypoint` | Fabric 自定义 Addon entrypoint 合约 |
| `EpsilonLanguage` | `GravenLanguage` | 语言枚举 |
| `EpsilonLanguageManager` | `GravenLanguageManager` | 语言加载器 |
| `EpsilonTranslateComponent` | `GravenTranslateComponent` | 内置翻译 key 工厂 |
| `EpsilonTranslations` | `GravenTranslations` | 内置 UI 文案注册表 |
| `EpsilonUiTheme` | `GravenUiTheme` | Lua/UI 主题入口 |

其余不含 `Graven` 的类名保持不变，仅迁移包名。所有类文件名必须与新的 public 类型名一致。`graven` 的构造函数仍应保持不可外部实例化（若实现为工具式静态入口，则保留现有静态生命周期）。

### 3.3 事件总线

`graven.init()` 中的 Lambda factory 注册前缀必须改为 `tech.hakuri.graven`。平台外部 Addon 的 factory 规则同步更新文档：外部包仍需为自己的包前缀注册 factory，不能依赖根包继承分发。

## 4. Gradle、构建产物和 Mod 元数据

1. `settings.gradle.kts`：`rootProject.name = "Graven"`。
2. `gradle.properties`：
   - `group=tech.hakuri.graven`
   - `mod_id=graven`
   - `mod_name=Graven`
3. `common/build.gradle.kts`：BuildConfig `packageName("tech.hakuri.graven")`；生成的 `BuildConfig` 不手工编辑。
4. `buildSrc`：继续从 `mod_id`、`mod_name`、`group` 读取属性，不新增硬编码版本；归档名将自然变为 `graven-<loader>-<minecraft>`。
5. Fabric：`fabric.mod.json` 的 id、name、入口全限定名、自定义 key、Mixin 和 Access Widener 占位符均使用 `${mod_id}` 或目标全限定名。
6. NeoForge：`neoforge.mods.toml` 的 `modId`、`displayName`、Mixin 配置和依赖块继续使用 `${mod_id}`；`@EventBusSubscriber(modid = Constants.MOD_ID)` 保持由 BuildConfig 提供。
7. 生成目录（`common/build`、`fabric/build`、`neoforge/build`）不是源文件，实施时先清理后重新生成，避免旧包名残留造成误判。

## 5. 资源、Mixin 和访问权限

### 5.1 资源命名空间

将 `common/src/main/resources/assets/epsilon/` 整体迁移为 `assets/graven/`，包括字体、shader、声音、粒子、HUD 纹理和语言文件。`ResourceLocationUtils.getIdentifier()` 的命名空间改为 `graven`；字体注册、shader pipeline、纹理和声音引用不得散落硬编码旧 namespace。

语言文件内容中的内置 owner key 也应从 `epsilon.*` 迁移到 `graven.*`，并同步 `GravenTranslateComponent`、`I18NFileGenerator` 和 `GravenTranslations`。

### 5.2 Mixin 文件

| 当前文件 | 目标文件 | 配置内变化 |
|---|---|---|
| `epsilon.mixins.json` | `graven.mixins.json` | `package` 改为 `tech.hakuri.graven.mixins` |
| `epsilon.fabric.mixins.json` | `graven.fabric.mixins.json` | package/plugin 改为 `tech.hakuri.graven.fabric.mixins...` |
| `epsilon.neoforge.mixins.json` | `graven.neoforge.mixins.json` | package/plugin 改为 `tech.hakuri.graven.neoforge.mixins...` |
| `epsilon.accesswidener` | `graven.accesswidener` | 内容中的 Minecraft 类不变，Fabric 元数据和 Loom 路径更新 |

Mixin 类的目标、注入点、`defaultRequire = 1`、Sodium 条件门控和平台拆分全部保持不变。只改包路径和配置文件名；不得借重命名机会修订注入逻辑。

NeoForge 的 `META-INF/accesstransformer.cfg` 不含 Graven 命名空间，保持内容不变，只验证打包后仍被加载。

## 6. 配置、脚本和序列化兼容策略

### 6.1 配置目录迁移

当前 `ConfigHolder` 和 `LuaScriptManager` 各自硬编码 `%USERPROFILE%\\.epsilon`，实施时应先抽出同一份项目路径常量/解析器，避免两个系统迁移到不同目录。

启动时按以下顺序处理：

1. 使用 `.graven` 作为新写入目录，并创建 `configs`、`imports`、`exports`、`scripts` 等既有子目录。
2. `.graven` 不存在且 `.epsilon` 存在时，执行一次递归迁移；迁移前校验源/目标均为用户目录下的真实目录，迁移失败保留源目录并记录完整异常。
3. 两者同时存在时，新目录优先；对缺失的文件可从旧目录按文件级回退读取，但不能用旧目录覆盖新目录。
4. 成功迁移后写入版本/迁移标记，旧目录保留为只读备份或由用户确认后删除，避免重复迁移。
5. 现有 Zip 安全解压、路径穿越校验、活动配置、Addon 设置和 Lua 包状态逻辑保持不变。

### 6.2 配置 schema 与 owner ID

- 配置 JSON 的字段名（`enabled`、`keyBind`、`settings`、`state`、HUD anchor 字段等）和 `CONFIG_VERSION` 不因改名改变。
- 内置模块的 owner 从默认 `epsilon` 改为 `graven` 时，读取阶段同时接受旧 owner 目录/旧 key，写入只使用新 owner；必须提供明确的冲突优先级。
- 外部 Addon ID 不自动改写；Addon ID 是第三方公共标识，除非 Addon 自己发布迁移，否则保持原值。
- `epsilon-empty-i18n.json` 改为 `graven-empty-i18n.json`；如果需要兼容旧自动化脚本，首个版本可同时生成旧文件别名，但默认日志和文档只使用新名称。

### 6.3 Lua API

Lua 运行时目录迁移到 `.graven/scripts`。以下内容必须成套更新并重新生成：

- `scripts/lua_codegen/epsilon_api.json` -> `graven_api.json`
- `scripts/generate_epsilon_lib.py` -> `generate_graven_lib.py`
- `docs/examples/lua/epsilon_lib.lua` -> `graven_lib.lua`
- `LuaUtilRegistry` 的生成注释、扫描根包和全限定工具类名
- Lua host 类型名、全局包表和文档中的 `graven` 示例

为已有脚本保留过渡期：运行时可同时注入 `graven` 和只读别名 `epsilon`，新脚本和生成类型库只宣传 `graven`。Java `luajava.bindClass("com.github.epsilon...")` 的旧全限定名无法在包迁移后自然成立；如要保持二进制/脚本兼容，必须额外提供显式旧名映射表，不能靠字符串替换猜测。

## 7. Logo 资源方案

参考文件 `C:\Users\27881\Pictures\Graven.png` 已核验为 `1254 x 1254`、24-bit RGB PNG。实施时：

1. 保留原 PNG 作为设计源，不直接覆盖源文件。
2. 生成适合 Mod 元数据的方形副本（建议 `128 x 128`，必要时同时提供 `256 x 256`），保持透明/背景策略与原图一致，并校验 PNG 可读性。
3. 将副本放在构建系统实际读取的资源位置（Fabric `icon` 字段所需路径，以及 NeoForge 发布元数据要求的 icon 路径）；若双加载器共享同一副本，使用共用资源目录并在两端元数据引用同一路径。
4. GUI 内部图标若继续使用 `assets/graven/textures/icons/`，只更新 namespace，不替换既有功能纹理，除非确认用户提供的 Logo 是新的 UI 图标。

## 8. 文档、脚本、测试和非源目录

- `README.md`、`README_zh.md`、`NOTICE.md`、`AGENTS.md` 和 `docs/` 中的产品名、包名、Mod ID、配置路径和 Lua 示例同步更新；`AGENTS.md` 的约束文本必须与实际新路径一致。
- `scripts/tests` 的测试夹具和期望字符串同步更新；测试类中用于验证包扫描的样例可以保留独立的 `tech.hakuri.graven.utils.SampleUtils`，但必须明确它是测试输入而非产品标识。
- `pyproject.toml`/`uv.lock` 的开发工具发行名是否改为 `graven-dev-tools` 应单独确认；它不影响 Mod 运行时，若改名必须同步锁文件和虚拟环境重建。
- `analysis_artifacts/`、`reference/`、`SkidProjects/` 和已有运行日志是历史证据或外部参考，不做批量重命名；只在新文档中注明它们不是发布源。
- 构建输出中的旧包名在清理前属于缓存，不作为源码残留判断依据。

## 9. 分阶段实施顺序

| 阶段 | 内容 | 验收条件 |
|---|---|---|
| 0 | 保存当前工作树快照，记录源码/资源清单和配置迁移测试样本 | 不覆盖既有改动；能恢复到改名前状态 |
| 1 | 迁移 Java 目录、package/import、Epsilon 前缀类名和共享主类 | `rg` 不再发现生产源码中的旧包名；初始化顺序一致 |
| 2 | 更新 Gradle 属性、BuildConfig、双加载器入口和元数据 | Fabric/NeoForge 解析到 `graven`，入口类可加载 |
| 3 | 迁移资源目录、Mixin JSON、Access Widener、硬编码 Identifier | 资源、shader、字体、语言和 Mixin 均指向 `graven` |
| 4 | 实现 `.epsilon` -> `.graven` 迁移和旧目录/owner 回退 | 新安装、旧安装、双目录冲突、迁移失败四类场景结果确定 |
| 5 | 更新 Lua API 生成器、类型库、脚本路径和兼容别名 | 生成器 `--check` 通过；旧脚本按兼容策略运行 |
| 6 | 安装 Logo 副本、更新文档和清理生成目录 | 两加载器最终包不含意外旧资源/包路径 |
| 7 | 完整验证和发布前审计 | 见下一节矩阵；确认回滚路径有效 |

## 10. 验证矩阵

### 静态验证

- `rg` 检查生产源码、资源和元数据中的旧包、旧 Mod ID、旧 namespace、旧 Mixin 文件名。
- `git diff --check`（仓库若已重新初始化）或等价空白检查；检查新增/移动文件的编码和换行。
- 校验所有 `package` 声明与目录路径一致，所有 Mixin JSON 类名与编译产物一致。
- 运行 Lua 生成器和脚本单元测试，确认生成文件与输入一致。

### 构建与运行验证

- Fabric：`assemble`/`remapJar`、客户端启动、资源重载、Mixin/Sodium 条件分支。
- NeoForge：`jar`、客户端启动、资源重载、Mixin/Access Transformer、GameTest namespace。
- 配置：全新 `.graven`、仅 `.epsilon`、两目录并存、损坏迁移源、Zip 导入路径穿越样本。
- 功能回归：模块/HUD/Addon 注册、按键、语言切换、Lua 开关、渲染 shader、声音和 Logo 显示。
- 日志：启动日志应显示 `Graven`，不得因旧 namespace 资源缺失产生异常。

## 11. 回滚与兼容性报告基线

实施前应保留改名前源码/资源快照和配置样本。回滚只恢复包路径、类名、元数据和资源 namespace，不删除用户的 `.graven` 或迁移备份；必要时由配置迁移器反向复制到 `.epsilon`。

**保留：** 模块边界、Addon 生命周期、EventBus 分发、Mixin 注入点、配置 schema、Zip 安全校验、渲染调度和双加载器依赖关系。

**更改：** 产品名、Mod ID、Java 根包、主类/平台入口类名、资源 namespace、Mixin 文件名、配置根目录、Lua 新 API 名称和 Logo 资源。

**移除：** 发布包中的旧 `com.github.epsilon` 类、旧 `epsilon` 资源 namespace 和旧 Mod ID；源码迁移期间不删除 `.epsilon` 用户数据。

**暂留兼容：** `.epsilon` 目录只读回退/迁移、Lua 全局 `epsilon` 别名、旧 owner 配置读取、迁移备份文件、历史文档/分析工件中的旧名称。每一项都应有迁移版本和最终移除条件，不能无限期让新写入继续使用旧标识。

## 12. 待确认项

1. `graven` 小写主类是否需要同时作为 Fabric/NeoForge 的直接入口；本规划将其定义为 common 共享初始化类，平台入口保持独立类名。
2. `.epsilon` 旧目录是保留只读回退，还是迁移成功后由用户手动删除；默认采用保留备份策略。
3. Lua 旧全限定类名是否需要正式兼容映射；若需要，应先确定允许映射的公开类清单。
4. `pyproject.toml` 中开发工具包名是否一并改为 `graven-dev-tools`；默认与 Mod 重命名解耦。
5. Logo 是否替换现有 `textures/icons/icon*.png`，还是只作为 Mod icon；默认只新增 Mod icon，不改变现有 UI 纹理。
