# UI 稳定性与品牌资源

## 品牌资源

- `assets/graven/icon.png` 与 NeoForge `logo.png` 使用 Graven 原始 PNG。
- 窗口图标使用同一图像生成的 16x16 与 32x32 版本。
- HUD Watermark 使用 `textures/icons/client_band.png`。该文件保留图形内容并将白色背景转换为 alpha，不影响窗口图标的原始白底。

## UI 进度边界

Lumin 的 `Switch` 要求 progress 位于 `[0, 1]`。`EASE_OUT_ELASTIC` 会产生回弹越界值，因此所有动态开关在 `PanelElements.buildSwitch` 进入 Lumin 前统一进行有限值检查和闭区间收敛。

## 字体图集恢复

Lumin runtime 已注册 Minecraft 资源重载回调。Graven 在资源包重载完成后使 `ClientSetting` 的字体注册缓存失效；下一帧重新注册 Graven 字体。HUD 不得主动调用 `runtime.onResourceReload()`：该方法会销毁所有字体 atlas，只允许由 Minecraft 的资源包重载监听器调用。运行时 glyph atlas 也不得作为 `UiTree.texture`/`rotatedTexture` 的 ID 提交；这些节点只能解析资源包纹理。
