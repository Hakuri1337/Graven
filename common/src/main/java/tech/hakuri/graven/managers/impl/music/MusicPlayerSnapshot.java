package tech.hakuri.graven.managers.impl.music;

import java.util.List;

/** GUI 线程读取的完整音乐状态快照。 */
public record MusicPlayerSnapshot(
        MusicPlaybackState state,
        String query,
        List<MusicModels.Song> results,
        MusicModels.Song current,
        List<MusicModels.Song> queue,
        int currentIndex,
        MusicPlayMode playMode,
        MusicModels.Lyrics lyrics,
        String error,
        long positionMs,
        long durationMs,
        float volume
) {
    public MusicPlayerSnapshot {
        state = state == null ? MusicPlaybackState.IDLE : state;
        query = query == null ? "" : query;
        results = List.copyOf(results == null ? List.of() : results);
        queue = List.copyOf(queue == null ? List.of() : queue);
        currentIndex = Math.max(-1, currentIndex);
        playMode = playMode == null ? MusicPlayMode.LIST_LOOP : playMode;
        lyrics = lyrics == null ? MusicModels.Lyrics.empty() : lyrics;
        volume = Math.max(0.0f, Math.min(1.0f, volume));
    }

    public static MusicPlayerSnapshot empty() {
        return new MusicPlayerSnapshot(MusicPlaybackState.IDLE, "", List.of(), null, List.of(), -1,
                MusicPlayMode.LIST_LOOP, MusicModels.Lyrics.empty(), null, 0L, 0L, 1.0f);
    }
}
