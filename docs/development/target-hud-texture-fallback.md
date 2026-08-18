# Target HUD 动态皮肤回退

Target HUD 的玩家皮肤由 Minecraft `TextureManager` 以动态纹理提供。Lumin 的 Minecraft 资源桥在构建
`UiTree` 纹理节点时还要求该标识可从 `ResourceManager` 读取；动态皮肤标识（例如
`minecraft:skins/<hash>`）不满足这一条件。

Target HUD 的玩家头像不再提交给 Lumin 的纹理节点。Lumin 的默认纹理过滤为 `LINEAR`，直接放大皮肤
8x8 面部区域会产生模糊；现在 overlay 阶段从 `TextureManager` 获取已加载的 GPU 视图，并通过原生 GUI
纹理管线使用 `NEAREST` 采样绘制面部和帽子 UV。纹理尺寸不足时跳过头像绘制，避免把缺失纹理放大到 HUD。
面板、动画、血条和目标状态机仍由原有 Lumin 流程负责。
