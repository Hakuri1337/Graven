package tech.hakuri.graven.managers.impl.music;

import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** SoundBufferLibrary Mixin 与 MusicPlayerManager 之间的一次性远程音频来源。 */
public final class RemoteMusicSource {
    public static final Identifier SOUND_EVENT = Identifier.fromNamespaceAndPath("graven", "music_remote");
    public static final Identifier STREAM_RESOURCE = Identifier.fromNamespaceAndPath("graven", "sounds/music/remote.ogg");

    private static final AtomicReference<PlaybackRequest> CURRENT = new AtomicReference<>();

    private RemoteMusicSource() {
    }

    public static void set(Path path) {
        set(path, 0L);
    }

    public static void set(Path path, long startMs) {
        CURRENT.set(path == null ? null : new PlaybackRequest(path.toAbsolutePath().normalize(), Math.max(0L, startMs)));
    }

    public static Path current() {
        PlaybackRequest request = CURRENT.get();
        return request == null ? null : request.path();
    }

    public static PlaybackRequest request() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.set(null);
    }

    public record PlaybackRequest(Path path, long startMs) {}
}
