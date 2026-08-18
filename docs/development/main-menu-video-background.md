# MainMenu `New` 动态背景

`Client Setting -> MainMenu Background -> New` 使用 `assets/graven/video/new.mp4`。
资源保持原片的 `3840x2160`、`60 FPS`、无音轨 H.264 参数，不在构建时降低分辨率或帧率。

## 渲染与生命周期

- `New` 仅在 Windows 运行。Linux、macOS、Android 或 Windows 解码器启动失败时，`MainMenuScreen.extractBackground` 继续使用 Planet 着色器回退。
- Windows 使用随模组打包的 `graven/native/windows-x86_64/ffmpeg.exe`。首次使用时，它被异步提取到 `~/.graven/native/windows-x86_64/ffmpeg-8.1.2.exe`，并校验 SHA-256 `ad8f211bc894755e0061c55ab280ae00e8d3d4f15a8cc4372b24cfa247b5942e`；不读取用户 `PATH` 中的 FFmpeg。
- `MainMenuVideoBackground` 通过 `ffmpeg -hwaccel auto` 连续解码原片，输出 `3840x2160`、`60 FPS` 的 RGBA 原始帧；视频结束由 FFmpeg 的 `-stream_loop -1` 无缝回绕。
- 解码线程使用三个固定大小的直接内存缓冲区。渲染线程仅将已完成的一帧复制到复用的 `NativeImage` 并上传，避免 JCodec 的 YUV/RGB/BGR 多次 CPU 颜色转换和每帧 Java 数组分配。
- 视频纹理使用线性采样，窗口显示按视频宽高比裁剪填充，避免拉伸；纹理坐标按正常 `v0..v1` 顺序提交，避免上下翻转。
- 离开主菜单时终止 FFmpeg 进程、停止解码线程、释放直接内存和纹理，并删除临时 MP4。

## 依赖

Fabric 与 NeoForge 成品都从共享资源中携带 Windows x64 的 FFmpeg 8.1.2 可执行文件。该二进制采用 GPLv3-or-later，详见仓库根目录 `NOTICE.md`。

`org.jcodec:jcodec` 与 `org.jcodec:jcodec-javase` 仍由音乐播放功能使用；New 视频背景不再调用 JCodec。
