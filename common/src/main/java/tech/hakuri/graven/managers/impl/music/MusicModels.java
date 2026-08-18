package tech.hakuri.graven.managers.impl.music;

import java.util.List;

/** MusicPlayer 使用的不可变数据模型。 */
public final class MusicModels {
    private MusicModels() {
    }

    public record Song(long id, String name, String album, String singer, String coverUrl,
                       String duration, boolean free) {
        public String displayName() {
            return name == null || name.isBlank() ? String.valueOf(id) : name;
        }

        public String displayArtist() {
            return singer == null || singer.isBlank() ? "Unknown artist" : singer;
        }
    }

    public record SongUrl(long id, String url, String level, long size, String md5) {
    }

    public record SearchResult(List<Song> songs, int total) {
        public SearchResult {
            songs = List.copyOf(songs == null ? List.of() : songs);
        }
    }

    public record Playlist(long id, String name, String coverUrl, String description, List<Song> songs) {
        public Playlist {
            name = name == null ? "" : name;
            coverUrl = coverUrl == null ? "" : coverUrl;
            description = description == null ? "" : description;
            songs = List.copyOf(songs == null ? List.of() : songs);
        }
    }

    public record LyricLine(long timeMs, String text, String translation) {
    }

    public record Lyrics(List<LyricLine> lines) {
        public Lyrics {
            lines = List.copyOf(lines == null ? List.of() : lines);
        }
        public static Lyrics empty() { return new Lyrics(List.of()); }
    }
}
