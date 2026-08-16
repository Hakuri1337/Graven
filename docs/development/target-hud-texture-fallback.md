# Target HUD 动态皮肤回退

Target HUD 的玩家皮肤由 Minecraft `TextureManager` 以动态纹理提供。Lumin 的 Minecraft 资源桥在构建
`UiTree` 纹理节点时还要求该标识可从 `ResourceManager` 读取；动态皮肤标识（例如
`minecraft:skins/<hash>`）不满足这一条件。

Target HUD 在提交纹理节点前检查 `Identifier` 及 `ResourceManager` 资源。检查通过时保持原有头部和帽子
UV 绘制；检查失败时绘制同尺寸、同圆角和同动画透明度的占位头像。这样不会改变 HUD 布局、血条或目标
状态机，也不会让 `UiResourceNotFoundException` 传播到 Render thread。
