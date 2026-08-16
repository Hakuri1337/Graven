# 2026-08-15 崩溃分析

## 致命异常

最终导致退出的是：

```text
java.lang.IllegalArgumentException: UI progress must be in 0..1
at ...UiNodes.unit
at ...SwitchElement.<init>
at tech.hakuri.graven.gui.panel.component.ModuleRow.buildUi(ModuleRow.java:104)
```

`ModuleListPanel` 的模块开关使用 `EASE_OUT_ELASTIC`。该 easing 在回弹阶段有意产生大于 `1` 或小于 `0` 的值，而 Lumin `Switch` 要求 progress 严格位于 `[0, 1]`。现在所有动态开关统一通过 `PanelElements.unitProgress` 收敛有限值后再提交。

## 次级异常

HUD 日志中的 `Missing UI texture: lumin_graphics_mc:glyph-atlas/203` 的直接来源是动态 glyph atlas 被作为 `UiTree.rotatedTexture` 的纹理 ID 提交。该节点通过资源包管理器解析纹理，而 glyph atlas 只注册在 `TextureManager`，因此每帧都会失败。失败后由 Graven 主动调用 `runtime.onResourceReload()` 会销毁字体 atlas，并在下一帧再次触发相同问题，最终让 Minecraft 文本回退为方框。现在 MTF 使用资源包可解析的静态 Cake 纹理，HUD 不再主动调用该 reload API；真正的资源包重载仍由 Lumin 的监听器处理，随后 Graven 只失效自身字体注册缓存。

HUD 的次级异常不会再掩盖模块面板的致命异常；两条路径分别处理。
