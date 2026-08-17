# Graven 开发文档

本目录保存 Graven 本体仓库的架构和具体项目说明。强制开发约束仍集中在仓库根目录的 [`AGENTS.md`](../AGENTS.md)，避免约束与背景资料混杂。

## 目录

```text
docs/
├── README.md
├── architecture/
│   ├── overview.md
│   └── lifecycle-and-components.md
├── development/
│   ├── build-and-versioning.md
│   ├── modules-and-addons.md
│   ├── events-and-mixins.md
│   ├── configuration-and-rotation.md
│   ├── rendering.md
│   └── internationalization.md
├── lua-scripting/
│   ├── README.md
│   ├── getting-started.md
│   ├── packages-and-modules.md
│   ├── settings-and-storage.md
│   ├── events-and-java.md
│   ├── util-code-completion.md
│   ├── rendering.md
│   ├── internationalization.md
│   ├── management-and-lifecycle.md
│   ├── architecture.md
│   └── testing-and-troubleshooting.md
└── examples/
    └── lua/
        ├── graven_lib.lua
        └── example-suite/
```

## 阅读路径

| 主题 | 文档 |
|---|---|
| 项目定位、仓库结构、分层与 `common` 包 | [架构总览](architecture/overview.md) |
| 启动顺序、Holders、Managers | [生命周期与核心组件](architecture/lifecycle-and-components.md) |
| 版本来源、Gradle 约定、构建命令 | [构建与版本](development/build-and-versioning.md) |
| Module、Setting DSL、Addon | [模块与 Addon](development/modules-and-addons.md) |
| EventBus、事件目录、Mixin | [事件与 Mixin](development/events-and-mixins.md) |
| BMW Freeze 三模式、包队列、Tick 余额与 FlightDelayTrigger 替换 | [BMW Freeze 严格迁移](development/bmw-freeze-migration.md) |
| EdOpal Streamer Mode 行为、API 映射与严格迁移 | [EdOpal Streamer Mode 迁移](development/edopal-streamer-mode-migration.md) |
| OpenZen AutoMLG 状态机、落地预测、旋转/物品时序与严格迁移 | [OpenZen AutoMLG 迁移](development/openzen-automlg-migration.md) |
| AutoMLG 针对 Grim AimModulo360 的连续 yaw 优化与行为保持 | [AutoMLG Grim AimModulo360 优化](development/automlg-grim-aimmodulo360-optimization.md) |
| InvManager 整理/丢弃独立计时与 Grim MultiActionsC 防护 | [InvManager Grim MultiActionsC 优化](development/invmanager-grim-multiactionsc-optimization.md) |
| EdOpal NoSlow NoC0F 状态机对比、包映射与 Graven C0F 优化 | [EdOpal NoSlow NoC0F 对比](development/edopal-noslow-noc0f-comparison.md) |
| OpenZen Disabler、AntiStaff、AntiBots 分析、API 映射与迁移方案 | [OpenZen 模块迁移分析](development/openzen-disabler-antistaff-antibots-migration.md) |
| OpenZen AntiKB、NoSlow、InventoryManager 依赖差异、Grim MultiActionsC/D 时序与迁移设计 | [OpenZen AntiKB/NoSlow/InventoryManager 迁移分析](development/openzen-antikb-noslow-inventory-migration-analysis.md) |
| OpenZen AntiKB、NoSlow 与 InventoryManager 实际实现、包时序与兼容性报告 | [OpenZen AntiKB/NoSlow/InventoryManager 实现](development/openzen-antikb-noslow-inventory-migration-implementation.md) |
| AntiKB 包回放与初始语言加载修复 | [AntiKB 与初始语言加载修复](development/antikb-and-i18n-hotfix-20260816.md) |
| Render 模块翻译键修复 | [Render 翻译键修复](development/i18n-render-key-fix-20260816.md) |
| NameTags 文本指标崩溃修复 | [NameTags 文本指标崩溃修复](development/nametags-text-metric-crash-20260817.md) |
| 配置目录、持久化、RotationManager | [配置与旋转](development/configuration-and-rotation.md) |
| Lumin、GUI/HUD、2D/3D 渲染 | [渲染](development/rendering.md) |
| Target HUD 动态皮肤资源检查与回退 | [Target HUD 纹理回退](development/target-hud-texture-fallback.md) |
| Graven 项目重命名、包名迁移、入口类、资源与配置兼容规划 | [Graven 重命名实施规划](development/graven-renaming-plan.md) |
| Lua 脚本包、Module、Java 调用、Setting、事件、渲染和管理 | [Lua 脚本教程](lua-scripting/) |
| Lua 代码补全库与可直接安装的多模块示例包 | [Lua 示例](examples/lua/) |
| key、JSON 格式和同步流程 | [国际化](development/internationalization.md) |

## 维护原则

烟花粒子 NoRender 崩溃修复见 [`development/firework-particle-crash-fix.md`](development/firework-particle-crash-fix.md)。

OpenZen 边走边丢的迁移记录见 [OpenZen 边走边丢实现](development/openzen-inventory-moving-drop-implementation.md)。

OpenZen AntiKB 完整等效迁移计划见 [OpenZen AntiKB 完整等效迁移计划](development/openzen-antikb-exact-parity-plan-20260816.md)。

- 文档与源码冲突时，以当前源码、`gradle/libs.versions.toml` 和本地 Minecraft 参考源码为准。
- 修改架构、公共 API、配置/资源格式、注册或构建流程时，在同一次修改中更新对应主题文档。
- 本目录描述“项目是什么、如何工作、API 如何使用”；强制限制只在 `AGENTS.md` 维护。
