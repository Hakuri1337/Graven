# MusicPlayer 实现记录

## 模块入口

`MusicPlayer` 注册在 `ModuleHolder.initModules()` 的 Player 类别。启用模块会打开唯一的 `MusicPlayerScreen`，关闭模块会关闭该 Screen；模块按键、Dropdown/Panel 中的模块入口都复用同一生命周期。

## 状态与线程

`MusicPlayerManager` 使用不可变 `MusicPlayerSnapshot` 暴露状态。`WyApiClient` 通过 Java `HttpClient` 调用 `https://nextmusic.toubiec.cn`，先缓存 `/api/ip` 的 IP，再为业务请求添加 `timestamp` 和 `ip`。搜索、歌曲信息、音频地址请求均在后台线程执行，GUI 线程不进行网络或 JSON 解析。

## 当前 UI

`MusicPlayerScreen` 复用 `UiScene`、`UiTree`、`MinecraftUiRuntime2612` 和 `MD3Theme`，提供搜索与歌单 ID 双输入、剪贴板粘贴、结果/队列列表、选曲、上一首/下一首、播放/暂停、三种播放模式、进度拖动和歌词区域。歌单通过 `/api/playlist_trackall` 以 `limit=500` 分页导入，普通 UGC 歌单的业务错误会沿 API 错误状态显示。GUI 每帧读取快照，因此请求失败、限流和加载状态不会阻塞渲染线程。

## 播放链路

播放器固定向 API 请求 `standard` 音质，将 MP3 原子写入 `.graven/music/cache/`，单文件上限为 160 MiB、缓存总量上限为 512 MiB。下载按歌曲 ID 与音质复用已有缓存，CDN 超时最多重试三次。`Mp3AudioStream` 使用项目已有的 JCodec 逐帧解码为 16-bit little-endian PCM；`MixinSoundBufferLibrary` 只拦截 `graven:sounds/music/remote.ogg` 占位资源，并把 PCM 流交给 Minecraft `SoundEngine`。播放器使用 `SoundSource.RECORDS`，避免用户静音原版背景音乐后客户端播放器也完全无声；暂停和恢复通过停止并从保存位置重建本声道实现，不修改其他游戏声音。

`lossless`、`hires` 等可能返回 FLAC/MP4 的音质当前没有暴露到 UI；在对应解码器加入并完成跨加载器打包验证前，管理器不会请求这些格式。

## 队列、歌词与 DynamicIsland

`MusicPlayerSnapshot` 保存结果、队列、当前索引、`MusicPlayMode`、播放位置和歌词。列表循环在末尾回到首首，单曲循环重新打开当前文件，随机模式选择队列中的随机索引。seek 会重建 `RemoteMusicSource.PlaybackRequest`，`Mp3AudioStream` 依据 MP3 packet 时间戳跳过目标位置后再交给 SoundEngine，因此拖动进度不会只改变显示数值。

`MusicPlayer` 提供 `Dynamic Island`、`Island Lyrics`、`Island Cover` 设置；通知仍然拥有最高显示优先级。音乐状态显示歌名、歌手、播放进度和可选当前歌词，关闭 Dynamic Island 后不提交音乐内容。

封面下载后统一转码为 PNG，再交给 `NativeImage` 注册动态纹理。加载失败的同一路径在当前会话内熔断，避免 HUD 每帧重复读取并刷日志。音乐岛使用固定的封面、标题、歌手、歌词和底部进度层级；宽度按三类文本的最大测量宽度计算，歌词不会与歌手或进度条重叠。
