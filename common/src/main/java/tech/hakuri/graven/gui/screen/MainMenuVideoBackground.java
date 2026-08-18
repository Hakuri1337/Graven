package tech.hakuri.graven.gui.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.SamplerCache;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.AddressMode;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;
import tech.hakuri.graven.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static tech.hakuri.graven.Constants.mc;

/** 主菜单 New 视频背景的 Windows FFmpeg 解码与 GPU 纹理生命周期。 */
final class MainMenuVideoBackground implements AutoCloseable {

    private static final String RESOURCE = "/assets/graven/video/new.mp4";
    private static final String FFMPEG_RESOURCE = "/graven/native/windows-x86_64/ffmpeg.exe";
    private static final String FFMPEG_VERSION = "8.1.2";
    private static final String FFMPEG_SHA256 = "ad8f211bc894755e0061c55ab280ae00e8d3d4f15a8cc4372b24cfa247b5942e";
    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("graven", "main_menu_new");
    private static final int BUFFER_COUNT = 3;
    private static final int VIDEO_WIDTH = 3840;
    private static final int VIDEO_HEIGHT = 2160;
    private static final double VIDEO_FRAME_RATE = 60.0;
    private static final long FRAME_INTERVAL_NANOS = Math.round(1_000_000_000.0 / VIDEO_FRAME_RATE);
    private static final int FRAME_BYTES = VIDEO_WIDTH * VIDEO_HEIGHT * 4;

    private final AtomicReference<FrameBuffer> pendingFrame = new AtomicReference<>();
    private final ArrayBlockingQueue<FrameBuffer> availableBuffers = new ArrayBlockingQueue<>(BUFFER_COUNT);
    private volatile boolean running;
    private volatile boolean failed;
    private Thread decoderThread;
    private Path temporaryVideo;
    private Path ffmpegExecutable;
    private volatile Process ffmpegProcess;
    private VideoTexture texture;
    private FrameBuffer lastUploaded;
    private int width = VIDEO_WIDTH;
    private int height = VIDEO_HEIGHT;
    private double frameRate = VIDEO_FRAME_RATE;

    void start() {
        if (running || failed) return;
        if (!isWindows()) {
            failed = true;
            Constants.LOGGER.info("New main-menu video is disabled on non-Windows platform; using GLSL fallback");
            return;
        }
        try {
            temporaryVideo = Files.createTempFile("graven-main-menu-", ".mp4");
            try (InputStream input = MainMenuVideoBackground.class.getResourceAsStream(RESOURCE)) {
                if (input == null) throw new IOException("Missing video resource " + RESOURCE);
                Files.copy(input, temporaryVideo, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            failed = true;
            Constants.LOGGER.error("Failed to stage New main-menu video", exception);
            return;
        }

        running = true;
        decoderThread = new Thread(this::decodeLoop, "Graven-New-Video-Decoder");
        decoderThread.setDaemon(true);
        decoderThread.start();
    }

    void uploadLatestFrame() {
        if (!running) return;
        FrameBuffer frame = pendingFrame.getAndSet(null);
        if (frame == null) return;

        if (texture == null) {
            width = frame.width;
            height = frame.height;
            texture = new VideoTexture(width, height);
            mc.getTextureManager().register(TEXTURE_ID, texture);
        }

        if (frame.width != width || frame.height != height) {
            availableBuffers.offer(frame);
            return;
        }

        NativeImage pixels = texture.getPixels();
        MemoryUtil.memCopy(MemoryUtil.memAddress(frame.pixels), pixels.getPointer(), FRAME_BYTES);
        texture.upload();

        if (lastUploaded != null) availableBuffers.offer(lastUploaded);
        lastUploaded = frame;
    }

    boolean isReady() {
        return texture != null;
    }

    Identifier textureId() {
        return TEXTURE_ID;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    double frameRate() {
        return frameRate;
    }

    private void decodeLoop() {
        try {
            ffmpegExecutable = stageFfmpeg();
            for (int index = 0; index < BUFFER_COUNT; index++) {
                availableBuffers.offer(new FrameBuffer(VIDEO_WIDTH, VIDEO_HEIGHT));
            }
            ProcessBuilder builder = new ProcessBuilder(
                    ffmpegExecutable.toString(),
                    "-hide_banner",
                    "-loglevel", "error",
                    "-hwaccel", "auto",
                    "-stream_loop", "-1",
                    "-i", temporaryVideo.toString(),
                    "-an",
                    "-vsync", "0",
                    "-f", "rawvideo",
                    "-pix_fmt", "rgba",
                    "pipe:1"
            );
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            ffmpegProcess = builder.start();
            try (ReadableByteChannel output = Channels.newChannel(ffmpegProcess.getInputStream())) {
                while (running) {
                    long frameStart = System.nanoTime();
                    FrameBuffer target = availableBuffers.take();
                    target.pixels.clear();
                    if (!readFully(output, target.pixels)) {
                        availableBuffers.offer(target);
                        break;
                    }
                    target.pixels.flip();
                    FrameBuffer replaced = pendingFrame.getAndSet(target);
                    if (replaced != null) availableBuffers.offer(replaced);
                    long sleep = FRAME_INTERVAL_NANOS - (System.nanoTime() - frameStart);
                    if (sleep > 0L) sleepNanos(sleep);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            if (running) {
                failed = true;
                running = false;
                Constants.LOGGER.error("Failed to decode New main-menu video with bundled FFmpeg", exception);
            }
        } finally {
            Process process = ffmpegProcess;
            ffmpegProcess = null;
            if (process != null) process.destroyForcibly();
        }
    }

    private static boolean readFully(ReadableByteChannel channel, ByteBuffer destination) throws IOException {
        while (destination.hasRemaining()) {
            int read = channel.read(destination);
            if (read < 0) return false;
            if (read == 0) Thread.onSpinWait();
        }
        return true;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static Path stageFfmpeg() throws IOException {
        Path directory = Path.of(System.getProperty("user.home"), ".graven", "native", "windows-x86_64");
        Files.createDirectories(directory);
        Path executable = directory.resolve("ffmpeg-" + FFMPEG_VERSION + ".exe");
        if (Files.isRegularFile(executable) && FFMPEG_SHA256.equals(sha256(executable))) return executable;
        Path temporary = Files.createTempFile(directory, "ffmpeg-", ".tmp");
        try (InputStream input = MainMenuVideoBackground.class.getResourceAsStream(FFMPEG_RESOURCE)) {
            if (input == null) throw new IOException("Missing bundled FFmpeg resource " + FFMPEG_RESOURCE);
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temporary, executable, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, executable, StandardCopyOption.REPLACE_EXISTING);
        }
        return executable;
    }

    private static String sha256(Path path) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] chunk = new byte[8192];
            for (int read; (read = input.read(chunk)) != -1; ) {
                digest.update(chunk, 0, read);
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static void sleepNanos(long nanos) {
        try {
            long millis = nanos / 1_000_000L;
            int extraNanos = (int) (nanos % 1_000_000L);
            Thread.sleep(millis, extraNanos);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        running = false;
        Process process = ffmpegProcess;
        ffmpegProcess = null;
        if (process != null) process.destroyForcibly();
        if (decoderThread != null) {
            decoderThread.interrupt();
            try {
                decoderThread.join(1000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            decoderThread = null;
        }
        FrameBuffer pending = pendingFrame.getAndSet(null);
        if (pending != null) availableBuffers.offer(pending);
        FrameBuffer uploaded = lastUploaded;
        lastUploaded = null;
        FrameBuffer buffer;
        while ((buffer = availableBuffers.poll()) != null) buffer.close();
        if (uploaded != null) uploaded.close();
        if (texture != null) {
            mc.getTextureManager().release(TEXTURE_ID);
            texture = null;
        }
        if (temporaryVideo != null) {
            try {
                Files.deleteIfExists(temporaryVideo);
            } catch (IOException exception) {
                Constants.LOGGER.warn("Failed to delete staged New main-menu video {}", temporaryVideo, exception);
            }
            temporaryVideo = null;
        }
    }

    private static final class FrameBuffer {
        private final int width;
        private final int height;
        private final ByteBuffer pixels;

        private FrameBuffer(int width, int height) {
            this.width = width;
            this.height = height;
            this.pixels = MemoryUtil.memAlloc(width * height * 4);
        }

        private void close() {
            MemoryUtil.memFree(pixels);
        }
    }

    private static final class VideoTexture extends DynamicTexture {
        private VideoTexture(int width, int height) {
            super(() -> "Graven New main-menu video", width, height, false);
            SamplerCache samplers = RenderSystem.getSamplerCache();
            this.sampler = samplers.getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, false);
        }
    }
}
