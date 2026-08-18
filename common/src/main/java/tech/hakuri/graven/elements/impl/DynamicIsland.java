package tech.hakuri.graven.elements.impl;

import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.text.icon.IconChars;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.text.UiTextMetrics;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import net.minecraft.client.DeltaTracker;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.Mth;
import tech.hakuri.graven.Constants;
import tech.hakuri.graven.elements.HudModule;
import tech.hakuri.graven.elements.impl.notification.Notification;
import tech.hakuri.graven.elements.impl.notification.Notifications;
import tech.hakuri.graven.gui.dropdown.DropdownScreen;
import tech.hakuri.graven.gui.theme.OpalIslandStyle;
import tech.hakuri.graven.managers.Managers;
import tech.hakuri.graven.managers.impl.music.MusicPlaybackState;
import tech.hakuri.graven.managers.impl.music.MusicPlayerSnapshot;
import tech.hakuri.graven.modules.impl.player.MusicPlayer;
import tech.hakuri.graven.utils.render.animation.Animation;
import tech.hakuri.graven.utils.render.animation.Easing;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.nio.file.Files;

/** 顶部动态岛；内容状态遵循搜索、通知、默认信息的优先级。 */
public final class DynamicIsland extends HudModule {

    public static final DynamicIsland INSTANCE = new DynamicIsland();

    private static final float DEFAULT_HEIGHT = OpalIslandStyle.HEIGHT;
    private static final float DEFAULT_TEXT_SCALE = 0.72f;
    private static final float SECONDARY_TEXT_SCALE = 0.48f;
    private static final float FOOTER_TEXT_SCALE = 0.42f;
    private static final float NOTIFICATION_TITLE_SCALE = 0.54f;
    private static final float NOTIFICATION_BODY_SCALE = 0.46f;
    private static final float NOTIFICATION_HORIZONTAL_PADDING = 7.0f;
    private static final float NOTIFICATION_VERTICAL_PADDING = 4.0f;
    private static final float NOTIFICATION_ITEM_HEIGHT = 28.0f;
    private static final float NOTIFICATION_ITEM_GAP = 3.0f;
    private static final float NOTIFICATION_ICON_SIZE = 22.0f;
    private static final float NOTIFICATION_ICON_GAP = 5.0f;
    private static final float MAX_NOTIFICATION_WIDTH = 280.0f;

    private final Animation widthAnimation = new Animation(Easing.DYNAMIC_ISLAND, OpalIslandStyle.ANIMATION_DURATION);
    private final Animation heightAnimation = new Animation(Easing.DYNAMIC_ISLAND, OpalIslandStyle.ANIMATION_DURATION);
    private boolean positioned;
    private Identifier musicCoverId;
    private DynamicTexture musicCoverTexture;
    private java.nio.file.Path failedMusicCoverPath;

    private DynamicIsland() {
        super("Dynamic Island", 0.0f, OpalIslandStyle.TOP, OpalIslandStyle.DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setAnchorState(HorizontalAnchor.Center, VerticalAnchor.Top, 0.0f, OpalIslandStyle.TOP);
        setDefaultHidden(false);
        setDefaultEnabled(true);
    }

    @Override
    protected void resetCustomState() {
        setAnchorState(HorizontalAnchor.Center, VerticalAnchor.Top, 0.0f, OpalIslandStyle.TOP);
        positioned = false;
        if (musicCoverId != null) {
            mc.getTextureManager().release(musicCoverId);
            musicCoverTexture = null;
            musicCoverId = null;
        }
        failedMusicCoverPath = null;
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        if (nullCheck() || mc.screen instanceof DropdownScreen) return;

        if (Managers.NOTIFICATION != null) {
            Managers.NOTIFICATION.update();
        }

        MinecraftUiRuntime2612 runtime = MinecraftUiRuntime2612.current();
        UiTextMetrics metrics = runtime.textMetrics();
        List<Notification> notifications = activeNotifications();
        IslandLayout target = notifications.isEmpty()
                ? defaultLayout(metrics)
                : notificationLayout(metrics, notifications);

        animateBounds(target.width(), target.height());
        float animatedWidth = Math.max(2.0f, widthAnimation.getValue());
        float animatedHeight = Math.max(2.0f, heightAnimation.getValue());
        setBounds(animatedWidth, animatedHeight);

        UiTree.Scope scope = renderScope();
        OpalIslandStyle.applyBlur(x, y, animatedWidth, animatedHeight);
        OpalIslandStyle.drawSurface(scope, x, y, animatedWidth, animatedHeight);

        float contentProgress = Mth.clamp(heightAnimation.getProgress(), 0.0f, 1.0f);
        scope.scissor(new UiRect(x, y, animatedWidth, animatedHeight), clipped -> {
            if (notifications.isEmpty()) {
                renderDefault(clipped, target, contentProgress);
            } else {
                renderNotifications(clipped, metrics, notifications, target, contentProgress);
            }
        });
    }

    private void animateBounds(float targetWidth, float targetHeight) {
        if (!positioned) {
            widthAnimation.setValue(targetWidth);
            heightAnimation.setValue(targetHeight);
            widthAnimation.run(targetWidth);
            heightAnimation.run(targetHeight);
            positioned = true;
            return;
        }
        widthAnimation.run(targetWidth);
        heightAnimation.run(targetHeight);
    }

    private IslandLayout defaultLayout(UiTextMetrics metrics) {
        MusicPlayerSnapshot music = Managers.MUSIC == null ? MusicPlayerSnapshot.empty() : Managers.MUSIC.snapshot();
        if (MusicPlayer.INSTANCE.showOnDynamicIsland.getValue()
                && (music.state() == MusicPlaybackState.PLAYING || music.state() == MusicPlaybackState.PAUSED)
                && music.current() != null) {
            String title = music.current().displayName();
            String artist = music.current().displayArtist();
            String lyric = MusicPlayer.INSTANCE.showLyrics.getValue() ? currentMusicLyric(music) : "";
            float contentWidth = Math.max(metrics.textWidth(title, 0.58f, OpalIslandStyle.TITLE_FONT),
                    metrics.textWidth(artist, 0.43f, OpalIslandStyle.BODY_FONT));
            if (!lyric.isBlank()) contentWidth = Math.max(contentWidth,
                    metrics.textWidth(lyric, 0.42f, OpalIslandStyle.BODY_FONT));
            float islandHeight = lyric.isBlank() ? 42.0f : 54.0f;
            return new IslandLayout(Math.min(360.0f, Math.max(190.0f, 52.0f + contentWidth)), islandHeight,
                    title, artist, "", "", "");
        }
        String brand = Constants.NAME;
        String releaseType = translated("release type", "CLIENT");
        String version = Constants.VERSION;
        ServerSnapshot server = serverSnapshot();
        float releaseWidth = Math.max(
                metrics.textWidth(releaseType, SECONDARY_TEXT_SCALE, OpalIslandStyle.TITLE_FONT),
                metrics.textWidth(version, FOOTER_TEXT_SCALE, OpalIslandStyle.BODY_FONT));
        float width = 14.0f
                + metrics.textWidth(brand, DEFAULT_TEXT_SCALE, OpalIslandStyle.TITLE_FONT)
                + releaseWidth
                + metrics.textWidth(server.address(), SECONDARY_TEXT_SCALE, OpalIslandStyle.TITLE_FONT)
                + 35.0f;
        return new IslandLayout(Math.max(OpalIslandStyle.DEFAULT_WIDTH, width), DEFAULT_HEIGHT,
                brand, releaseType, version, server.address(), server.ping());
    }

    private IslandLayout notificationLayout(UiTextMetrics metrics, List<Notification> notifications) {
        float contentWidth = 1.0f;
        for (Notification notification : notifications) {
            contentWidth = Math.max(contentWidth, Math.max(
                    metrics.textWidth(safe(notification.getTitle(), translated("notification", "Notification")),
                            NOTIFICATION_TITLE_SCALE, OpalIslandStyle.TITLE_FONT),
                    metrics.textWidth(safe(notification.getSubTitle(), ""),
                            NOTIFICATION_BODY_SCALE, OpalIslandStyle.BODY_FONT)));
        }
        float width = NOTIFICATION_HORIZONTAL_PADDING * 2.0f + NOTIFICATION_ICON_SIZE
                + NOTIFICATION_ICON_GAP + contentWidth;
        float height = NOTIFICATION_VERTICAL_PADDING * 2.0f
                + notifications.size() * NOTIFICATION_ITEM_HEIGHT
                + Math.max(0, notifications.size() - 1) * NOTIFICATION_ITEM_GAP;
        return new IslandLayout(Math.min(MAX_NOTIFICATION_WIDTH, Math.max(96.0f, width)), height,
                "", "", "", "", "");
    }

    private void renderDefault(UiTree.Scope scope, IslandLayout layout, float progress) {
        MusicPlayerSnapshot music = Managers.MUSIC == null ? MusicPlayerSnapshot.empty() : Managers.MUSIC.snapshot();
        if (MusicPlayer.INSTANCE.showOnDynamicIsland.getValue()
                && (music.state() == MusicPlaybackState.PLAYING || music.state() == MusicPlaybackState.PAUSED)
                && music.current() != null) {
            renderMusic(scope, layout, music, progress);
            return;
        }
        Color text = alpha(OpalIslandStyle.TEXT, progress);
        Color muted = alpha(OpalIslandStyle.MUTED_TEXT, progress);
        Color accentStart = alpha(OpalIslandStyle.ACCENT_START, progress);
        Color accentEnd = alpha(OpalIslandStyle.ACCENT_END, progress);

        float logoWidth = 17.0f;
        float logoHeight = 15.0f;
        float iconX = x + 6.0f;
        float iconY = y + 6.5f;
        scope.texture("graven:textures/icons/client_band.png", new UiRect(iconX, iconY, logoWidth, logoHeight),
                4.0f, 4.0f, 4.0f, 4.0f, 0.0f, 0.0f, 1.0f, 1.0f, alpha(Color.WHITE, progress));

        float brandX = x + 27.0f;
        float brandY = y + 8.0f;
        float brandWidth = textWidth(layout.brand(), DEFAULT_TEXT_SCALE, OpalIslandStyle.TITLE_FONT);
        scope.text(layout.brand(), brandX, brandY, DEFAULT_TEXT_SCALE, accentStart, OpalIslandStyle.TITLE_FONT);
        scope.roundRect(brandX + brandWidth * 0.52f, y + 20.2f,
                Math.max(1.0f, brandWidth * 0.48f), 1.0f, 0.5f, accentEnd);

        float releaseX = brandX + brandWidth + 4.3f;
        scope.rect(releaseX, y + 8.0f, 0.75f, 10.0f, muted);
        float releaseTextX = releaseX + 3.5f;
        scope.text(layout.releaseType(), releaseTextX, y + 6.5f,
                SECONDARY_TEXT_SCALE, text, OpalIslandStyle.TITLE_FONT);
        scope.text(layout.version(), releaseTextX, y + 15.5f,
                FOOTER_TEXT_SCALE, muted, OpalIslandStyle.BODY_FONT);

        float releaseWidth = Math.max(
                textWidth(layout.releaseType(), SECONDARY_TEXT_SCALE, OpalIslandStyle.TITLE_FONT),
                textWidth(layout.version(), FOOTER_TEXT_SCALE, OpalIslandStyle.BODY_FONT));
        float serverDividerX = releaseTextX + releaseWidth + 4.3f;
        scope.rect(serverDividerX, y + 8.0f, 0.75f, 10.0f, muted);
        float serverX = serverDividerX + 3.5f;
        scope.text(layout.serverAddress(), serverX, y + 6.5f,
                SECONDARY_TEXT_SCALE, text, OpalIslandStyle.TITLE_FONT);
        scope.text(layout.serverPing(), serverX, y + 15.5f,
                FOOTER_TEXT_SCALE, muted, OpalIslandStyle.BODY_FONT);
    }

    private void renderMusic(UiTree.Scope scope, IslandLayout layout, MusicPlayerSnapshot music, float progress) {
        ensureMusicCover();
        UiTextMetrics metrics = MinecraftUiRuntime2612.current().textMetrics();
        float iconSize = 30.0f;
        if (MusicPlayer.INSTANCE.showCover.getValue() && musicCoverId != null) {
            scope.texture(musicCoverId.toString(), new UiRect(x + 6.0f, y + 6.0f, iconSize, iconSize), 6.0f, 6.0f, 6.0f, 6.0f, 0, 0, 1, 1, alpha(Color.WHITE, progress));
        } else {
            scope.roundRect(x + 6.0f, y + 6.0f, iconSize, iconSize, 6.0f, alpha(OpalIslandStyle.ACCENT_START, progress));
            float iconScale = 0.82f;
            float iconWidth = metrics.textWidth(IconChars.MUSIC_NOTE, iconScale, OpalIslandStyle.ICON_FONT);
            float iconHeight = metrics.textHeight(iconScale, OpalIslandStyle.ICON_FONT);
            scope.text(IconChars.MUSIC_NOTE, x + 6.0f + (iconSize - iconWidth) / 2.0f,
                    y + 6.0f + (iconSize - iconHeight) / 2.0f - 1.0f, iconScale,
                    alpha(Color.WHITE, progress), OpalIslandStyle.ICON_FONT);
        }
        float textX = x + 42.0f;
        float textWidth = layout.width() - 48.0f;
        scope.text(fit(metrics, layout.brand(), 0.58f, OpalIslandStyle.TITLE_FONT, textWidth), textX, y + 6.0f,
                0.58f, alpha(OpalIslandStyle.TEXT, progress), OpalIslandStyle.TITLE_FONT);
        scope.text(fit(metrics, layout.releaseType(), 0.43f, OpalIslandStyle.BODY_FONT, textWidth), textX, y + 17.0f,
                0.43f, alpha(OpalIslandStyle.MUTED_TEXT, progress), OpalIslandStyle.BODY_FONT);
        String lyric = MusicPlayer.INSTANCE.showLyrics.getValue() ? currentMusicLyric(music) : "";
        if (!lyric.isBlank()) scope.text(fit(metrics, lyric, 0.42f, OpalIslandStyle.BODY_FONT, textWidth), textX, y + 31.0f,
                0.42f, alpha(OpalIslandStyle.MUTED_TEXT, progress), OpalIslandStyle.BODY_FONT);
        float ratio = music.durationMs() <= 0L ? 0.0f
                : Mth.clamp(music.positionMs() / (float) music.durationMs(), 0.0f, 1.0f);
        float barWidth = Math.max(1.0f, layout.width() - 48.0f);
        float barY = y + layout.height() - 5.0f;
        scope.roundRect(textX, barY, barWidth, 1.5f, 0.75f,
                alpha(OpalIslandStyle.MUTED_TEXT, 0.30f * progress));
        scope.roundRect(textX, barY, barWidth * ratio, 1.5f, 0.75f,
                alpha(OpalIslandStyle.ACCENT_START, progress));
    }

    private void ensureMusicCover() {
        if (Managers.MUSIC == null || !MusicPlayer.INSTANCE.showCover.getValue()) return;
        java.nio.file.Path path = Managers.MUSIC.currentCoverPath();
        if (path == null || !Files.isRegularFile(path)) return;
        if (path.equals(failedMusicCoverPath)) return;
        Identifier id = Identifier.fromNamespaceAndPath("graven", "music_cover/" + path.getFileName().toString().replace('.', '_'));
        if (id.equals(musicCoverId)) return;
        try (var input = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(input);
            DynamicTexture texture = new DynamicTexture(() -> "Graven music cover", image);
            if (musicCoverId != null) mc.getTextureManager().release(musicCoverId);
            mc.getTextureManager().register(id, texture);
            musicCoverTexture = texture;
            musicCoverId = id;
            failedMusicCoverPath = null;
        } catch (Exception exception) {
            failedMusicCoverPath = path;
            Constants.LOGGER.warn("Failed to load music cover {}", path, exception);
        }
    }

    private static String currentMusicLyric(MusicPlayerSnapshot music) {
        String lyric = "";
        for (var line : music.lyrics().lines()) {
            if (line.timeMs() > music.positionMs()) break;
            lyric = line.translation().isBlank() ? line.text() : line.text() + "  " + line.translation();
        }
        return lyric;
    }

    private void renderNotifications(UiTree.Scope scope, UiTextMetrics metrics, List<Notification> notifications,
                                     IslandLayout layout, float progress) {
        for (int index = 0; index < notifications.size(); index++) {
            Notification notification = notifications.get(index);
            float itemY = y + NOTIFICATION_VERTICAL_PADDING
                    + index * (NOTIFICATION_ITEM_HEIGHT + NOTIFICATION_ITEM_GAP);
            float iconX = x + NOTIFICATION_HORIZONTAL_PADDING;
            float iconY = itemY + (NOTIFICATION_ITEM_HEIGHT - NOTIFICATION_ICON_SIZE) / 2.0f;
            Color modeColor = alpha(notification.getMode().getColor(), progress);
            if (Notifications.INSTANCE.showIslandIconBackground()) {
                scope.roundRect(iconX, iconY, NOTIFICATION_ICON_SIZE, NOTIFICATION_ICON_SIZE,
                        7.0f, alpha(modeColor, 0.22f));
            }

            String icon = switch (notification.getMode()) {
                case Success -> IconChars.CHECK;
                case Info -> IconChars.INFO;
                case Error -> IconChars.ERROR;
            };
            float iconScale = 0.70f;
            float iconWidth = metrics.textWidth(icon, iconScale, OpalIslandStyle.ICON_FONT);
            float iconHeight = metrics.textHeight(iconScale, OpalIslandStyle.ICON_FONT);
            scope.text(icon, iconX + (NOTIFICATION_ICON_SIZE - iconWidth) / 2.0f,
                    iconY + (NOTIFICATION_ICON_SIZE - iconHeight) / 2.0f - 1.0f,
                    iconScale, modeColor, OpalIslandStyle.ICON_FONT);

            float contentX = iconX + NOTIFICATION_ICON_SIZE + NOTIFICATION_ICON_GAP;
            float contentWidth = Math.max(1.0f, layout.width() - (contentX - x) - NOTIFICATION_HORIZONTAL_PADDING);
            String title = fit(metrics, safe(notification.getTitle(), translated("notification", "Notification")),
                    NOTIFICATION_TITLE_SCALE, OpalIslandStyle.TITLE_FONT, contentWidth);
            String subtitle = fit(metrics, safe(notification.getSubTitle(), ""),
                    NOTIFICATION_BODY_SCALE, OpalIslandStyle.BODY_FONT, contentWidth);
            scope.text(title, contentX, itemY + 5.0f, NOTIFICATION_TITLE_SCALE,
                    alpha(OpalIslandStyle.TEXT, progress), OpalIslandStyle.TITLE_FONT);
            scope.text(subtitle, contentX, itemY + 13.5f, NOTIFICATION_BODY_SCALE,
                    alpha(OpalIslandStyle.MUTED_TEXT, progress), OpalIslandStyle.BODY_FONT);

            float remaining = Mth.clamp(1.0f - notification.getElapsedTime()
                    / (float) Math.max(1, notification.getDisplayTime()), 0.0f, 1.0f);
            float barWidth = Math.min(contentWidth,
                    metrics.textWidth(title, NOTIFICATION_TITLE_SCALE, OpalIslandStyle.TITLE_FONT));
            float barY = itemY + 22.0f;
            scope.roundRect(contentX, barY, barWidth, 2.0f, 1.0f,
                    alpha(new Color(0, 0, 0, 102), progress));
            if (remaining > 0.0f) {
                scope.roundRect(contentX, barY, barWidth * remaining, 2.0f, 1.0f, modeColor);
            }
        }
    }

    private ServerSnapshot serverSnapshot() {
        ServerData server = mc.getCurrentServer();
        if (server == null) {
            return new ServerSnapshot(translated("singleplayer", "singleplayer"), "0 ms");
        }

        String address = server.ip == null || server.ip.isBlank() ? server.name : server.ip;
        address = safe(address, translated("unknown server", "unknown"));
        address = address.toLowerCase(Locale.ROOT);
        if (address.length() > 20) {
            address = address.substring(0, 17) + "...";
        }

        long latency = 0L;
        ClientPacketListener connection = mc.getConnection();
        if (connection != null && mc.player != null) {
            PlayerInfo info = connection.getPlayerInfo(mc.player.getUUID());
            if (info != null) latency = info.getLatency();
        }
        if (latency < 2L) latency = Math.max(0L, server.ping);
        return new ServerSnapshot(address, latency + " ms");
    }

    private List<Notification> activeNotifications() {
        if (!Notifications.INSTANCE.usesIslandNotifications()) return List.of();
        if (Managers.NOTIFICATION == null || Managers.NOTIFICATION.isEmpty()) return List.of();
        List<Notification> result = new ArrayList<>();
        for (Notification notification : Managers.NOTIFICATION.getNotifications()) {
            if (!notification.isExpired()) result.add(notification);
        }
        return result;
    }

    private String translated(String key, String fallback) {
        if (translateComponent == null) return fallback;
        String value = translateComponent.createChild(key).getTranslatedName();
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Color alpha(Color color, float alphaScale) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Mth.clamp(Math.round(color.getAlpha() * Mth.clamp(alphaScale, 0.0f, 1.0f)), 0, 255));
    }

    private static String fit(UiTextMetrics metrics, String text, float scale, String font, float maxWidth) {
        if (metrics.textWidth(text, scale, font) <= maxWidth) return text;
        String ellipsis = "...";
        int low = 0;
        int high = text.length();
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (metrics.textWidth(text.substring(0, middle) + ellipsis, scale, font) <= maxWidth) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return text.substring(0, low) + ellipsis;
    }

    private record IslandLayout(float width, float height, String brand, String releaseType, String version,
                                String serverAddress, String serverPing) {
    }

    private record ServerSnapshot(String address, String ping) {
    }
}
