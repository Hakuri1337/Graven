# AntiKB 与初始语言加载修复

## 语言加载

Fabric 在 `graven.init()` 前注册 `LanguageReloadListener`。首次资源重载会因此填充 `GravenLanguageManager`，Dropdown、Panel 和主菜单不再显示 `graven.*` 原始 key。

## AntiKB

- `AntiKBMode` 的入站/出站回放增加重入保护，回放期间不会再次进入自身包拦截器。
- `NoXZMode` 对地面击退保留原版运动；仅在空中受击时缓存击退与移动包，落地或超时后按 OpenZen 顺序恢复。
- AntiKB 挂起期间 KillAura 暂停交互包，避免 Grim `PacketOrderF` 看到 sprint 状态与交互包处于同一预测窗口。
- 飞行、液体、着火、攀爬、睡眠、蜘蛛网和 Stuck 状态会释放队列并恢复 tick rate。

## 验证

使用 Microsoft JDK 25 执行：

```text
gradlew.bat :common:compileJava --no-daemon
```

结果：`BUILD SUCCESSFUL`。
