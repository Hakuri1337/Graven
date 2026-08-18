package tech.hakuri.graven.managers.impl.music;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import tech.hakuri.graven.Constants;
import tech.hakuri.graven.assets.config.ProjectPaths;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;

/** 音乐播放队列与音频生命周期管理器。所有 GUI 状态通过不可变快照读取。 */
public final class MusicPlayerManager implements AutoCloseable {
    public static final MusicPlayerManager INSTANCE = new MusicPlayerManager();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> { Thread thread = new Thread(runnable, "Graven-Music"); thread.setDaemon(true); return thread; });
    private final WyApiClient api = new WyApiClient(executor);
    private final AtomicReference<MusicPlayerSnapshot> snapshot = new AtomicReference<>(MusicPlayerSnapshot.empty());
    private final AtomicLong playbackRequestId = new AtomicLong();
    private final Path cacheDirectory = ProjectPaths.configDirectory().resolve("music").resolve("cache");
    private volatile SoundInstance activeSound;
    private volatile Path activePath;
    private volatile Path activeCoverPath;
    private volatile long playbackStartedAtMs;
    private volatile boolean closed;

    private MusicPlayerManager() {}

    public MusicPlayerSnapshot snapshot() {
        MusicPlayerSnapshot current = snapshot.get();
        if (current.state() != MusicPlaybackState.PLAYING || playbackStartedAtMs <= 0L) return current;
        long position = Math.max(0L, System.currentTimeMillis() - playbackStartedAtMs);
        if (current.durationMs() > 0L && position >= current.durationMs()) { advanceAfterEnd(); return snapshot.get(); }
        return copy(current, current.state(), position, current.error());
    }

    public Path currentCoverPath() { return activeCoverPath; }

    public void search(String query) {
        String value = query == null ? "" : query.trim();
        MusicPlayerSnapshot before = snapshot.get();
        snapshot.set(new MusicPlayerSnapshot(MusicPlaybackState.FETCHING_INFO, value, List.of(), before.current(), before.queue(), before.currentIndex(), before.playMode(), before.lyrics(), null, before.positionMs(), before.durationMs(), before.volume()));
        api.search(value, 50, 0).whenCompleteAsync((result, error) -> {
            if (error != null) { setError(error); return; }
            MusicPlayerSnapshot now = snapshot.get();
            snapshot.set(new MusicPlayerSnapshot(MusicPlaybackState.READY, value, result.songs(), now.current(), now.queue(), now.currentIndex(), now.playMode(), now.lyrics(), null, now.positionMs(), now.durationMs(), now.volume()));
        }, executor);
    }

    public void importPlaylist(String playlistId) {
        long id;
        try { id = Long.parseLong(playlistId == null ? "" : playlistId.trim()); }
        catch (NumberFormatException exception) { setError(new IllegalArgumentException("Invalid playlist ID")); return; }
        MusicPlayerSnapshot before = snapshot.get();
        snapshot.set(copy(before, MusicPlaybackState.FETCHING_INFO, before.positionMs(), null));
        api.importPlaylist(id).whenCompleteAsync((playlist, error) -> {
            if (error != null) { setError(error); return; }
            MusicPlayerSnapshot now = snapshot.get();
            snapshot.set(new MusicPlayerSnapshot(MusicPlaybackState.READY, now.query(), playlist.songs(), now.current(), playlist.songs(), -1, now.playMode(), now.lyrics(), null, 0L, 0L, now.volume()));
        }, executor);
    }

    public void play(MusicModels.Song song) {
        if (closed || song == null) return;
        MusicPlayerSnapshot before = snapshot.get();
        List<MusicModels.Song> queue = before.queue().isEmpty() ? (before.results().isEmpty() ? List.of(song) : before.results()) : before.queue();
        int index = queue.indexOf(song);
        if (index < 0) { queue = List.of(song); index = 0; }
        snapshot.set(new MusicPlayerSnapshot(MusicPlaybackState.FETCHING_INFO, before.query(), before.results(), song, queue, index, before.playMode(), MusicModels.Lyrics.empty(), null, 0L, parseDuration(song.duration()), before.volume()));
        stopActiveSound();
        long requestId = playbackRequestId.incrementAndGet();
        prepareAndPlay(song, 0L, requestId);
    }

    public void previous() { move(-1); }
    public void next() { move(1); }
    private void move(int delta) {
        MusicPlayerSnapshot current = snapshot.get();
        if (current.queue().isEmpty()) return;
        int index = current.playMode() == MusicPlayMode.SHUFFLE ? (int) (Math.random() * current.queue().size()) : Math.floorMod((current.currentIndex() < 0 ? 0 : current.currentIndex()) + delta, current.queue().size());
        play(current.queue().get(index));
    }
    private void advanceAfterEnd() {
        MusicPlayerSnapshot current = snapshot.get();
        if (current.queue().isEmpty()) { stopActiveSound(); snapshot.set(copy(current, MusicPlaybackState.READY, current.durationMs(), null)); return; }
        if (current.playMode() == MusicPlayMode.SINGLE_LOOP) play(current.current()); else move(1);
    }

    public void cyclePlayMode() {
        MusicPlayerSnapshot current = snapshot.get();
        MusicPlayMode next = switch (current.playMode()) { case LIST_LOOP -> MusicPlayMode.SINGLE_LOOP; case SINGLE_LOOP -> MusicPlayMode.SHUFFLE; case SHUFFLE -> MusicPlayMode.LIST_LOOP; };
        snapshot.set(new MusicPlayerSnapshot(current.state(), current.query(), current.results(), current.current(), current.queue(), current.currentIndex(), next, current.lyrics(), current.error(), current.positionMs(), current.durationMs(), current.volume()));
    }

    public void seek(long positionMs) {
        MusicPlayerSnapshot current = snapshot();
        Path path = activePath;
        if (current.current() == null || path == null || current.durationMs() <= 0L) return;
        long target = Math.max(0L, Math.min(current.durationMs(), positionMs));
        boolean paused = current.state() == MusicPlaybackState.PAUSED;
        stopActiveSound();
        activePath = path;
        snapshot.set(copy(current, paused ? MusicPlaybackState.PAUSED : MusicPlaybackState.SEEKING, target, null));
        if (!paused) Constants.mc.execute(() -> startSound(current.current(), path, target));
    }

    public void togglePause() {
        MusicPlayerSnapshot current = snapshot();
        if (current.state() == MusicPlaybackState.PLAYING) {
            stopActiveSound();
            snapshot.set(copy(current, MusicPlaybackState.PAUSED, current.positionMs(), null));
        } else if (current.state() == MusicPlaybackState.PAUSED && activePath != null) {
            snapshot.set(copy(current, MusicPlaybackState.SEEKING, current.positionMs(), null));
            startSound(current.current(), activePath, current.positionMs());
        }
    }

    public void setVolume(float volume) {
        MusicPlayerSnapshot c = snapshot.get();
        float value = Math.max(0.0f, Math.min(1.0f, volume));
        snapshot.set(new MusicPlayerSnapshot(c.state(), c.query(), c.results(), c.current(), c.queue(), c.currentIndex(), c.playMode(), c.lyrics(), c.error(), c.positionMs(), c.durationMs(), value));
    }

    private void prepareAndPlay(MusicModels.Song song, long startMs, long requestId) {
        api.getSongInfo(song.id()).thenCompose(info -> api.getSongUrl(info.id(), "standard").thenCompose(url -> api.downloadAudio(url, cacheDirectory).thenApply(path -> new PreparedSong(info, path))))
                .whenCompleteAsync((prepared, error) -> {
                    if (requestId != playbackRequestId.get()) return;
                    if (error != null || prepared == null) { setError(error == null ? new IllegalStateException("Audio cache path is empty") : error); return; }
                    activePath = prepared.path();
                    api.downloadCover(prepared.song().coverUrl(), prepared.song().id(), cacheDirectory).whenCompleteAsync((cover, ignoredCover) -> {
                        if (requestId == playbackRequestId.get()) activeCoverPath = cover;
                    }, executor);
                    api.getLyrics(prepared.song().id()).whenCompleteAsync((lyrics, ignored) -> {
                        if (requestId != playbackRequestId.get()) return;
                        MusicPlayerSnapshot c = snapshot.get();
                        snapshot.set(new MusicPlayerSnapshot(c.state(), c.query(), c.results(), prepared.song(), c.queue(), c.currentIndex(), c.playMode(), lyrics == null ? MusicModels.Lyrics.empty() : lyrics, c.error(), c.positionMs(), parseDuration(prepared.song().duration()), c.volume()));
                    }, executor);
                    Constants.mc.execute(() -> {
                        if (requestId == playbackRequestId.get()) startSound(prepared.song(), prepared.path(), startMs);
                    });
                }, executor);
    }

    private void startSound(MusicModels.Song song, Path path, long startMs) {
        if (closed) return;
        RemoteMusicSource.set(path, startMs);
        SoundInstance instance = new SimpleSoundInstance(RemoteMusicSource.SOUND_EVENT, SoundSource.RECORDS, snapshot.get().volume(), 1.0f, SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        var result = Constants.mc.getSoundManager().play(instance);
        if (result == net.minecraft.client.sounds.SoundEngine.PlayResult.NOT_STARTED) {
            setError(new IllegalStateException("Minecraft sound engine rejected the music stream"));
            return;
        }
        activeSound = instance; activePath = path; playbackStartedAtMs = System.currentTimeMillis() - startMs;
        MusicPlayerSnapshot c = snapshot.get();
        snapshot.set(new MusicPlayerSnapshot(MusicPlaybackState.PLAYING, c.query(), c.results(), song, c.queue(), c.currentIndex(), c.playMode(), c.lyrics(), null, startMs, c.durationMs(), c.volume()));
    }

    private void stopActiveSound() { SoundInstance old = activeSound; activeSound = null; playbackStartedAtMs = 0L; if (old != null) Constants.mc.execute(() -> Constants.mc.getSoundManager().stop(old)); RemoteMusicSource.clear(); }
    private void setError(Throwable error) { Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error; Constants.LOGGER.error("MusicPlayer request failed", cause); MusicPlayerSnapshot c = snapshot.get(); snapshot.set(copy(c, MusicPlaybackState.ERROR, c.positionMs(), cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage())); }
    private static MusicPlayerSnapshot copy(MusicPlayerSnapshot c, MusicPlaybackState state, long position, String error) { return new MusicPlayerSnapshot(state, c.query(), c.results(), c.current(), c.queue(), c.currentIndex(), c.playMode(), c.lyrics(), error, position, c.durationMs(), c.volume()); }
    private static long parseDuration(String value) { if (value == null || value.isBlank()) return 0L; try { long s = 0L; for (String p : value.split(":")) s = s * 60L + Long.parseLong(p.trim()); return s * 1000L; } catch (NumberFormatException ignored) { return 0L; } }
    private record PreparedSong(MusicModels.Song song, Path path) {}
    @Override public void close() { if (closed) return; closed = true; stopActiveSound(); executor.shutdownNow(); }
}
