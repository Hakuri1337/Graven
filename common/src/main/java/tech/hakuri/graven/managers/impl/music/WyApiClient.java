package tech.hakuri.graven.managers.impl.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import tech.hakuri.graven.Constants;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** wyapi.toubiec.cn 的正式后端适配器。所有请求都带 timestamp 和缓存的客户端 IP。 */
public final class WyApiClient {
    public static final String API_ROOT = "https://nextmusic.toubiec.cn";

    private final HttpClient httpClient;
    private final Executor executor;
    private volatile String clientIp;
    private volatile CompletableFuture<Void> ipBootstrap;
    private static final long MAX_AUDIO_BYTES = 160L * 1024L * 1024L;

    public WyApiClient(Executor executor) {
        this.executor = executor;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .executor(executor)
                .build();
    }

    public CompletableFuture<MusicModels.SearchResult> search(String keyword, int limit, int offset) {
        String value = keyword == null ? "" : keyword.trim();
        if (value.isBlank()) return CompletableFuture.completedFuture(new MusicModels.SearchResult(List.of(), 0));
        JsonObject body = new JsonObject();
        body.addProperty("keyword", value);
        body.addProperty("type", 1);
        body.addProperty("limit", Math.max(1, Math.min(100, limit)));
        body.addProperty("offset", Math.max(0, offset));
        return post("/api/search", body).thenApply(this::parseSearchResult);
    }

    public CompletableFuture<MusicModels.Song> getSongInfo(long id) {
        JsonObject body = new JsonObject();
        body.addProperty("id", Long.toString(id));
        return post("/api/getSongInfo", body).thenApply(response -> parseSong(response.getAsJsonObject("data")));
    }

    public CompletableFuture<MusicModels.Playlist> importPlaylist(long id) {
        return CompletableFuture.supplyAsync(() -> {
            List<MusicModels.Song> songs = new ArrayList<>();
            String name = "Playlist " + id;
            String cover = "";
            String description = "";
            int offset = 0;
            while (true) {
                JsonObject body = new JsonObject();
                body.addProperty("id", Long.toString(id));
                body.addProperty("limit", 500);
                body.addProperty("offset", offset);
                JsonObject data = object(post("/api/playlist_trackall", body).join(), "data");
                name = string(data, "name").isBlank() ? name : string(data, "name");
                cover = string(data, "coverImage");
                description = string(data, "description");
                JsonArray page = data.has("songs") && data.get("songs").isJsonArray() ? data.getAsJsonArray("songs") : new JsonArray();
                for (JsonElement element : page) if (element.isJsonObject()) songs.add(parseSong(element.getAsJsonObject()));
                if (page.size() < 500) break;
                offset += page.size();
                if (offset > 10000) break;
            }
            return new MusicModels.Playlist(id, name, cover, description, songs);
        }, executor);
    }

    public CompletableFuture<MusicModels.Lyrics> getLyrics(long id) {
        JsonObject body = new JsonObject();
        body.addProperty("id", Long.toString(id));
        return post("/api/getSongLyric", body).thenApply(response -> parseLyrics(object(response, "data")));
    }

    public CompletableFuture<MusicModels.SongUrl> getSongUrl(long id, String level) {
        JsonObject body = new JsonObject();
        body.addProperty("id", Long.toString(id));
        body.addProperty("level", level == null || level.isBlank() ? "standard" : level);
        return post("/api/getSongUrl", body).thenApply(response -> {
            JsonObject data = object(response, "data");
            return new MusicModels.SongUrl(number(data, "id", id), string(data, "url"),
                    string(data, "level"), number(data, "size", 0L), string(data, "md5"));
        });
    }

    public CompletableFuture<Path> downloadAudio(MusicModels.SongUrl songUrl, Path cacheDirectory) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(cacheDirectory);
                Path target = cacheDirectory.resolve(songUrl.id() + "-" + songUrl.level() + ".mp3");
                if (Files.isRegularFile(target) && Files.size(target) > 0L) {
                    Files.setLastModifiedTime(target, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
                    return target;
                }
                try (var files = Files.list(cacheDirectory)) {
                    Path compatible = files.filter(path -> path.getFileName().toString().startsWith(songUrl.id() + "-" + songUrl.level() + "-"))
                            .filter(path -> path.getFileName().toString().endsWith(".mp3"))
                            .filter(path -> {
                                try { return Files.size(path) > 0L; }
                                catch (IOException ignored) { return false; }
                            }).findFirst().orElse(null);
                    if (compatible != null) return compatible;
                }
                Path temporary = cacheDirectory.resolve(target.getFileName() + ".part");
                HttpRequest request = HttpRequest.newBuilder(URI.create(songUrl.url()))
                        .timeout(Duration.ofSeconds(90))
                        .header("User-Agent", "Graven-MusicPlayer/1")
                        .GET()
                        .build();
                HttpResponse<java.io.InputStream> response = null;
                Exception lastFailure = null;
                for (int attempt = 1; attempt <= 3; attempt++) {
                    try {
                        response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                        break;
                    } catch (IOException | InterruptedException exception) {
                        if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                        lastFailure = exception;
                        if (attempt < 3 && !Thread.currentThread().isInterrupted()) Thread.sleep(300L * attempt);
                    }
                }
                if (response == null) throw new IOException("Audio CDN failed after 3 attempts", lastFailure);
                if (response.statusCode() / 100 != 2) {
                    response.body().close();
                    throw new IOException("Audio CDN HTTP " + response.statusCode());
                }
                long expected = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                if (expected > MAX_AUDIO_BYTES) {
                    response.body().close();
                    throw new IOException("Audio file exceeds 160 MiB limit");
                }
                long written = 0L;
                byte[] buffer = new byte[64 * 1024];
                try (java.io.InputStream input = response.body();
                     java.io.OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.CREATE,
                             StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        written += read;
                        if (written > MAX_AUDIO_BYTES) throw new IOException("Audio file exceeds 160 MiB limit");
                        output.write(buffer, 0, read);
                    }
                } catch (Exception exception) {
                    Files.deleteIfExists(temporary);
                    throw exception;
                }
                if (written == 0L) {
                    Files.deleteIfExists(temporary);
                    throw new IOException("Audio CDN returned an empty file");
                }
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
                trimCache(cacheDirectory, 512L * 1024L * 1024L, target);
                return target;
            } catch (Exception exception) {
                throw new java.util.concurrent.CompletionException(exception);
            }
        }, executor);
    }

    public CompletableFuture<Path> downloadCover(String url, long songId, Path cacheDirectory) {
        if (url == null || url.isBlank()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(cacheDirectory);
                Path target = cacheDirectory.resolve("cover-" + songId + ".png");
                if (Files.isRegularFile(target) && Files.size(target) > 0L) return target;
                HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header("User-Agent", "Graven-MusicPlayer/1").GET().build();
                HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() / 100 != 2) { response.body().close(); throw new IOException("Cover HTTP " + response.statusCode()); }
                BufferedImage cover;
                try (var input = new java.io.BufferedInputStream(response.body())) {
                    cover = ImageIO.read(input);
                }
                if (cover == null) throw new IOException("Unsupported cover image format");
                Path temporary = target.resolveSibling(target.getFileName() + ".part");
                if (!ImageIO.write(cover, "PNG", temporary.toFile())) throw new IOException("PNG cover encoder unavailable");
                if (Files.size(temporary) > 8L * 1024L * 1024L) { Files.deleteIfExists(temporary); throw new IOException("Cover exceeds 8 MiB"); }
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                return target;
            } catch (Exception exception) { throw new java.util.concurrent.CompletionException(exception); }
        }, executor);
    }

    private static void trimCache(Path directory, long maxBytes, Path protectedFile) throws IOException {
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> !path.equals(protectedFile) && !path.getFileName().toString().endsWith(".part"))
                    .sorted(java.util.Comparator.comparingLong(path -> {
                        try { return Files.getLastModifiedTime(path).toMillis(); }
                        catch (IOException ignored) { return Long.MIN_VALUE; }
                    }))
                    .toList();
        }
        long total;
        try (var stream = Files.list(directory)) {
            total = stream.filter(Files::isRegularFile).mapToLong(path -> {
                try { return Files.size(path); }
                catch (IOException ignored) { return 0L; }
            }).sum();
        }
        for (Path path : files) {
            if (total <= maxBytes) break;
            long size = Files.size(path);
            if (Files.deleteIfExists(path)) total -= size;
        }
    }

    private CompletableFuture<JsonObject> post(String path, JsonObject payload) {
        return ensureIp().thenCompose(ignored -> {
            payload.addProperty("timestamp", System.currentTimeMillis());
            if (clientIp != null && !clientIp.isBlank()) payload.addProperty("ip", clientIp);
            HttpRequest request = HttpRequest.newBuilder(URI.create(API_ROOT + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApplyAsync(response -> parseResponse(path, response), executor);
        });
    }

    private CompletableFuture<Void> ensureIp() {
        if (clientIp != null) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> current = ipBootstrap;
        if (current != null) return current;
        synchronized (this) {
            if (clientIp != null) return CompletableFuture.completedFuture(null);
            if (ipBootstrap != null) return ipBootstrap;
            JsonObject body = new JsonObject();
            body.addProperty("timestamp", System.currentTimeMillis());
            HttpRequest request = HttpRequest.newBuilder(URI.create(API_ROOT + "/api/ip"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            CompletableFuture<Void> bootstrap = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApplyAsync(response -> {
                        JsonObject root = parseResponse("/api/ip", response);
                        String resolved = string(object(root, "data"), "ip");
                        if (resolved.isBlank()) throw new MusicApiException("/api/ip", response.statusCode(), 200, "API returned an empty IP");
                        clientIp = resolved;
                        return (Void) null;
                    }, executor);
            ipBootstrap = bootstrap;
            bootstrap.whenComplete((ignored, error) -> {
                if (error == null) return;
                synchronized (WyApiClient.this) {
                    if (ipBootstrap == bootstrap) ipBootstrap = null;
                }
            });
            return bootstrap;
        }
    }

    private JsonObject parseResponse(String path, HttpResponse<String> response) {
        try {
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            int code = root.has("code") ? root.get("code").getAsInt() : response.statusCode();
            if (response.statusCode() / 100 != 2 || code != 200) {
                String message = root.has("message") ? root.get("message").getAsString() : "HTTP " + response.statusCode();
                throw new MusicApiException(path, response.statusCode(), code, message);
            }
            return root;
        } catch (MusicApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new MusicApiException(path, response.statusCode(), response.statusCode(), "Invalid API response", ex);
        }
    }

    private MusicModels.SearchResult parseSearchResult(JsonObject root) {
        JsonObject data = object(root, "data");
        JsonArray songs = data.has("songs") && data.get("songs").isJsonArray() ? data.getAsJsonArray("songs") : new JsonArray();
        List<MusicModels.Song> result = new ArrayList<>();
        for (JsonElement element : songs) {
            if (element.isJsonObject()) result.add(parseSong(element.getAsJsonObject()));
        }
        int total = data.has("songCount") ? data.get("songCount").getAsInt() : result.size();
        return new MusicModels.SearchResult(result, total);
    }

    private MusicModels.Song parseSong(JsonObject data) {
        return new MusicModels.Song(number(data, "id", 0L), string(data, "name"), string(data, "album"),
                string(data, "singer"), string(data, "picimg"), string(data, "duration"),
                !data.has("free") || data.get("free").getAsBoolean());
    }

    private MusicModels.Lyrics parseLyrics(JsonObject data) {
        String lrc = string(data, "lrc");
        String translation = string(data, "tlyric");
        List<MusicModels.LyricLine> lines = new ArrayList<>();
        java.util.Map<Long, String> translations = parseLrc(translation);
        for (var entry : parseLrc(lrc).entrySet()) lines.add(new MusicModels.LyricLine(entry.getKey(), entry.getValue(), translations.getOrDefault(entry.getKey(), "")));
        lines.sort(java.util.Comparator.comparingLong(MusicModels.LyricLine::timeMs));
        return new MusicModels.Lyrics(lines);
    }

    private static java.util.Map<Long, String> parseLrc(String value) {
        java.util.Map<Long, String> result = new java.util.TreeMap<>();
        if (value == null) return result;
        for (String line : value.split("\\R")) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:\\.(\\d{1,3}))?\\](.*)").matcher(line);
            if (!matcher.matches()) continue;
            long millis = Long.parseLong(matcher.group(1)) * 60000L + Long.parseLong(matcher.group(2)) * 1000L;
            if (matcher.group(3) != null) millis += Integer.parseInt((matcher.group(3) + "000").substring(0, 3));
            String text = matcher.group(4).trim();
            if (!text.isBlank()) result.put(millis, text);
        }
        return result;
    }

    private static JsonObject object(JsonObject root, String name) {
        return root.has(name) && root.get(name).isJsonObject() ? root.getAsJsonObject(name) : new JsonObject();
    }

    private static String string(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : "";
    }

    private static long number(JsonObject object, String name, long fallback) {
        try { return object.has(name) ? object.get(name).getAsLong() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    public static final class MusicApiException extends RuntimeException {
        private final String path;
        private final int httpCode;
        private final int apiCode;

        public MusicApiException(String path, int httpCode, int apiCode, String message) {
            super(message);
            this.path = path;
            this.httpCode = httpCode;
            this.apiCode = apiCode;
        }

        public MusicApiException(String path, int httpCode, int apiCode, String message, Throwable cause) {
            super(message, cause);
            this.path = path;
            this.httpCode = httpCode;
            this.apiCode = apiCode;
        }

        public String path() { return path; }
        public int httpCode() { return httpCode; }
        public int apiCode() { return apiCode; }
    }
}
