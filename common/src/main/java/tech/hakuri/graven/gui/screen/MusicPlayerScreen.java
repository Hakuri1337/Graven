package tech.hakuri.graven.gui.screen;

import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.scene.UiLayer;
import com.github.slmpc.lumingraphics.ui.scene.UiScene;
import com.github.slmpc.lumingraphics.ui.text.UiTextMetrics;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import net.minecraft.network.chat.Component;
import tech.hakuri.graven.gui.theme.GravenUiTheme;
import tech.hakuri.graven.assets.i18n.GravenTranslations;
import tech.hakuri.graven.gui.theme.MD3Theme;
import tech.hakuri.graven.gui.theme.OpalIslandStyle;
import tech.hakuri.graven.gui.utils.UiCoordinateMapper;
import tech.hakuri.graven.managers.Managers;
import tech.hakuri.graven.managers.impl.music.MusicModels;
import tech.hakuri.graven.managers.impl.music.MusicPlaybackState;
import tech.hakuri.graven.managers.impl.music.MusicPlayerSnapshot;
import tech.hakuri.graven.modules.impl.player.MusicPlayer;
import tech.hakuri.graven.Constants;
import com.github.slmpc.lumingraphics.text.icon.IconChars;

import java.util.List;

/** Material 风格的独立音乐播放器 Screen。 */
public final class MusicPlayerScreen extends Screen {
    public static final MusicPlayerScreen INSTANCE = new MusicPlayerScreen();

    private static final float PANEL_RADIUS = 18.0f;
    private static final float SEARCH_HEIGHT = 30.0f;
    private UiScene scene;
    private MinecraftUiRuntime2612 sceneRuntime;
    private UiTextMetrics textMetrics;
    private String query = "";
    private String playlistId = "";
    private boolean queryFocused;
    private boolean playlistFocused;
    private boolean draggingProgress;
    private long dragPositionMs;
    private int resultTop;
    private int resultRowHeight;
    private int resultScroll;

    private MusicPlayerScreen() {
        super(Component.literal("Music Player"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        MusicPlayerSnapshot snapshot = Managers.MUSIC == null ? MusicPlayerSnapshot.empty() : Managers.MUSIC.snapshot();
        query = snapshot.query();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        MinecraftUiRuntime2612 runtime = MinecraftUiRuntime2612.current();
        tech.hakuri.graven.modules.impl.ClientSetting.INSTANCE.configureMinecraftFonts(runtime);
        if (scene == null || sceneRuntime != runtime) {
            releaseScene();
            sceneRuntime = runtime;
            scene = runtime.createScene(GravenUiTheme.lumin());
            textMetrics = runtime.textMetrics();
        }
        int projectionX = UiCoordinateMapper.toProjectionX(mouseX);
        int projectionY = UiCoordinateMapper.toProjectionY(mouseY);
        runtime.render(scene, active -> draw(active, projectionX, projectionY));
    }

    private void draw(UiScene active, int mouseX, int mouseY) {
        int width = UiCoordinateMapper.getProjectionWidthInt();
        int height = UiCoordinateMapper.getProjectionHeightInt();
        float panelW = Math.min(840.0f, Math.max(320.0f, width - 48.0f));
        float panelH = Math.min(620.0f, Math.max(260.0f, height - 42.0f));
        float x = (width - panelW) * 0.5f;
        float y = (height - panelH) * 0.5f;
        MusicPlayerSnapshot snapshot = Managers.MUSIC == null ? MusicPlayerSnapshot.empty() : Managers.MUSIC.snapshot();
        UiTree tree = UiTree.build(root -> {
            root.rect(0.0f, 0.0f, width, height, MD3Theme.withAlpha(MD3Theme.SURFACE_DIM, 232));
            root.pushAbsolute(x, y, panel -> {
                panel.shadow(0.0f, 3.0f, panelW, panelH, PANEL_RADIUS, 22.0f, MD3Theme.withAlpha(MD3Theme.SHADOW, 130));
                panel.roundRect(0.0f, 0.0f, panelW, panelH, PANEL_RADIUS, MD3Theme.SURFACE);
                panel.text(GravenTranslations.Music.TITLE.getTranslatedName(), 22.0f, 22.0f, 0.78f, MD3Theme.TEXT_PRIMARY);
                panel.text("WyAPI", 22.0f, 37.0f, 0.42f, MD3Theme.TEXT_MUTED);

                float searchX = 20.0f;
                float searchY = 52.0f;
                float searchW = panelW * 0.48f - 30.0f;
                panel.roundRect(searchX, searchY, searchW, SEARCH_HEIGHT, 9.0f,
                        MD3Theme.filledFieldSurface(queryFocused, queryFocused ? 1.0f : 0.0f));
                panel.rect(searchX, searchY + SEARCH_HEIGHT - 1.4f, searchW, 1.4f,
                        MD3Theme.filledFieldIndicator(queryFocused, 0.0f));
                String displayQuery = query.isBlank() ? GravenTranslations.Music.SEARCH_PLACEHOLDER.getTranslatedName() : query;
                panel.text(trim(displayQuery, 0.54f, searchW - 18.0f), searchX + 9.0f, searchY + 9.0f, 0.54f,
                        query.isBlank() ? MD3Theme.TEXT_MUTED : MD3Theme.TEXT_PRIMARY);
                panel.roundRect(panelW * 0.48f - 1.0f, searchY, 42.0f, SEARCH_HEIGHT, 9.0f, MD3Theme.PRIMARY_CONTAINER);
                panel.text(IconChars.SEARCH, panelW * 0.48f + 11.0f, searchY + 8.0f, 0.68f,
                        MD3Theme.ON_PRIMARY_CONTAINER, OpalIslandStyle.ICON_FONT);

                float playlistX = panelW * 0.48f + 48.0f;
                float playlistW = panelW - playlistX - 78.0f;
                panel.roundRect(playlistX, searchY, playlistW, SEARCH_HEIGHT, 9.0f, MD3Theme.filledFieldSurface(playlistFocused, playlistFocused ? 1.0f : 0.0f));
                String playlistText = playlistId.isBlank() ? GravenTranslations.Music.PLAYLIST_ID.getTranslatedName() : playlistId;
                panel.text(playlistText, playlistX + 9.0f, searchY + 8.0f, 0.52f, playlistId.isBlank() ? MD3Theme.TEXT_MUTED : MD3Theme.TEXT_PRIMARY);
                panel.roundRect(panelW - 72.0f, searchY, 52.0f, SEARCH_HEIGHT, 9.0f, MD3Theme.SECONDARY_CONTAINER);
                panel.text(IconChars.PLAYLIST_ADD, panelW - 59.0f, searchY + 8.0f, 0.68f,
                        MD3Theme.ON_SECONDARY_CONTAINER, OpalIslandStyle.ICON_FONT);

                float contentY = 96.0f;
                float contentH = panelH - 154.0f;
                panel.roundRect(20.0f, contentY, panelW - 40.0f, contentH, 11.0f, MD3Theme.SURFACE_CONTAINER_LOW);
                float sideX = panelW * 0.67f;
                panel.roundRect(sideX, contentY + 8.0f, panelW - sideX - 28.0f, contentH - 16.0f, 10.0f, MD3Theme.SURFACE_CONTAINER);
                panel.text(IconChars.MUSIC_NOTE, sideX + 18.0f, contentY + 20.0f, 1.2f,
                        MD3Theme.PRIMARY, OpalIslandStyle.ICON_FONT);
                if (snapshot.current() != null) {
                    panel.text(trim(snapshot.current().displayName(), 0.62f, panelW - sideX - 52.0f), sideX + 18.0f, contentY + 48.0f, 0.62f, MD3Theme.TEXT_PRIMARY);
                    panel.text(trim(snapshot.current().displayArtist(), 0.48f, panelW - sideX - 52.0f), sideX + 18.0f, contentY + 63.0f, 0.48f, MD3Theme.TEXT_SECONDARY);
                    String lyric = currentLyric(snapshot);
                    panel.text(trim(lyric, 0.46f, panelW - sideX - 52.0f), sideX + 18.0f, contentY + 90.0f, 0.46f, MD3Theme.TEXT_MUTED);
                }
                panel.text(statusText(snapshot), 32.0f, contentY + 14.0f, 0.46f,
                        snapshot.error() == null ? MD3Theme.TEXT_MUTED : MD3Theme.ERROR);
                resultTop = Math.round(y + contentY + 31.0f);
                resultRowHeight = 30;
                List<MusicModels.Song> songs = snapshot.results();
                int max = Math.min(Math.max(0, songs.size() - resultScroll), Math.max(0, (int) ((contentH - 38.0f) / resultRowHeight)));
                for (int row = 0; row < max; row++) {
                    int index = resultScroll + row;
                    MusicModels.Song song = songs.get(index);
                    float rowY = contentY + 32.0f + row * resultRowHeight;
                    boolean hovered = mouseX >= x + 28.0f && mouseX <= x + sideX - 8.0f
                            && mouseY >= y + rowY && mouseY <= y + rowY + 26.0f;
                    panel.roundRect(28.0f, rowY, sideX - 40.0f, 26.0f, 7.0f,
                            hovered ? MD3Theme.SURFACE_CONTAINER_HIGH : MD3Theme.SURFACE_CONTAINER);
                    String title = (index + 1) + ". " + song.displayName();
                    panel.text(trim(title, 0.54f, sideX - 104.0f), 36.0f, rowY + 5.0f, 0.54f, MD3Theme.TEXT_PRIMARY);
                    panel.text(trim(song.displayArtist(), 0.45f, 72.0f), sideX - 82.0f, rowY + 6.0f, 0.45f, MD3Theme.TEXT_SECONDARY);
                }

                float footerY = panelH - 52.0f;
                float progressW = panelW - 40.0f;
                long shownPosition = draggingProgress ? dragPositionMs : snapshot.positionMs();
                float progress = snapshot.durationMs() <= 0L ? 0.0f : Math.min(1.0f, shownPosition / (float) snapshot.durationMs());
                panel.roundRect(20.0f, footerY - 8.0f, progressW, 3.0f, 1.5f, MD3Theme.SURFACE_CONTAINER_HIGH);
                panel.roundRect(20.0f, footerY - 8.0f, progressW * progress, 3.0f, 1.5f, MD3Theme.PRIMARY);
                panel.roundRect(20.0f, footerY, panelW - 40.0f, 30.0f, 9.0f, MD3Theme.SURFACE_CONTAINER_HIGH);
                MusicModels.Song current = snapshot.current();
                String currentText = current == null ? GravenTranslations.Music.NOTHING_PLAYING.getTranslatedName() : current.displayName() + "  -  " + current.displayArtist();
                panel.text(trim(currentText, 0.48f, panelW - 180.0f), 30.0f, footerY + 10.0f, 0.48f, MD3Theme.TEXT_PRIMARY);
                String action = (snapshot.state() == MusicPlaybackState.PLAYING
                        ? GravenTranslations.Music.PAUSE : GravenTranslations.Music.PLAY).getTranslatedName();
                drawIconButton(panel, panelW - 248.0f, footerY + 3.0f, IconChars.SKIP_PREVIOUS, MD3Theme.SURFACE_CONTAINER_HIGH);
                drawIconButton(panel, panelW - 210.0f, footerY + 3.0f, snapshot.state() == MusicPlaybackState.PLAYING ? IconChars.PAUSE : IconChars.PLAY_ARROW, MD3Theme.SECONDARY_CONTAINER);
                drawIconButton(panel, panelW - 172.0f, footerY + 3.0f, IconChars.SKIP_NEXT, MD3Theme.SURFACE_CONTAINER_HIGH);
                drawIconButton(panel, panelW - 134.0f, footerY + 3.0f, modeIcon(snapshot), MD3Theme.SURFACE_CONTAINER_HIGH);
                drawIconButton(panel, panelW - 92.0f, footerY + 3.0f, IconChars.CLOSE, MD3Theme.PRIMARY_CONTAINER);
            });
        });
        active.submit(UiLayer.CHROME, 0, tree);
    }

    private void drawIconButton(UiTree.Scope panel, float x, float y, String icon, java.awt.Color color) {
        panel.roundRect(x, y, 32.0f, 24.0f, 8.0f, color);
        float width = textMetrics.textWidth(icon, 0.68f, OpalIslandStyle.ICON_FONT);
        float height = textMetrics.textHeight(0.68f, OpalIslandStyle.ICON_FONT);
        panel.text(icon, x + (32.0f - width) / 2.0f, y + (24.0f - height) / 2.0f - 1.0f,
                0.68f, MD3Theme.TEXT_PRIMARY, OpalIslandStyle.ICON_FONT);
    }

    private String modeIcon(MusicPlayerSnapshot snapshot) {
        return switch (snapshot.playMode()) { case LIST_LOOP -> IconChars.REPEAT; case SINGLE_LOOP -> IconChars.REPEAT_ONE; case SHUFFLE -> IconChars.SHUFFLE; };
    }

    private String currentLyric(MusicPlayerSnapshot snapshot) {
        String current = "";
        for (MusicModels.LyricLine line : snapshot.lyrics().lines()) { if (line.timeMs() > snapshot.positionMs()) break; current = line.translation().isBlank() ? line.text() : line.text() + "  " + line.translation(); }
        return current.isBlank() ? GravenTranslations.Music.NO_LYRICS.getTranslatedName() : current;
    }

    private String statusText(MusicPlayerSnapshot snapshot) {
        if (snapshot.error() != null) return snapshot.error();
        return switch (snapshot.state()) {
            case FETCHING_INFO, FETCHING_URL, DOWNLOADING -> GravenTranslations.Music.LOADING.getTranslatedName();
            case PLAYING -> GravenTranslations.Music.PLAYING.getTranslatedName();
            case PAUSED -> GravenTranslations.Music.PAUSED.getTranslatedName();
            default -> snapshot.results().isEmpty() ? GravenTranslations.Music.SEARCH_HINT.getTranslatedName()
                    : snapshot.results().size() + " " + GravenTranslations.Music.RESULTS.getTranslatedName();
        };
    }

    private String trim(String value, float scale, float maxWidth) {
        if (textMetrics.textWidth(value, scale, null) <= maxWidth) return value;
        String suffix = "...";
        for (int end = value.length(); end > 0; end--) {
            String candidate = value.substring(0, end) + suffix;
            if (textMetrics.textWidth(candidate, scale, null) <= maxWidth) return candidate;
        }
        return suffix;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        double mouseX = UiCoordinateMapper.toProjectionX(event.x());
        double mouseY = UiCoordinateMapper.toProjectionY(event.y());
        int width = UiCoordinateMapper.getProjectionWidthInt();
        int height = UiCoordinateMapper.getProjectionHeightInt();
        float panelW = Math.min(840.0f, Math.max(320.0f, width - 48.0f));
        float panelH = Math.min(620.0f, Math.max(260.0f, height - 42.0f));
        float x = (width - panelW) * 0.5f;
        float y = (height - panelH) * 0.5f;
        if (event.button() != 0) return super.mouseClicked(event, isDoubleClick);
        queryFocused = mouseX >= x + 20.0f && mouseX <= x + panelW * 0.48f - 10.0f && mouseY >= y + 52.0f && mouseY <= y + 82.0f;
        playlistFocused = mouseX >= x + panelW * 0.48f + 48.0f && mouseX <= x + panelW - 78.0f && mouseY >= y + 52.0f && mouseY <= y + 82.0f;
        if (mouseX >= x + panelW * 0.48f - 1.0f && mouseX <= x + panelW * 0.48f + 41.0f && mouseY >= y + 52.0f && mouseY <= y + 82.0f) {
            search();
            return true;
        }
        if (mouseX >= x + panelW - 72.0f && mouseX <= x + panelW - 20.0f && mouseY >= y + 52.0f && mouseY <= y + 82.0f) { if (Managers.MUSIC != null) Managers.MUSIC.importPlaylist(playlistId); return true; }
        MusicPlayerSnapshot snapshot = Managers.MUSIC == null ? MusicPlayerSnapshot.empty() : Managers.MUSIC.snapshot();
        List<MusicModels.Song> songs = snapshot.results();
        float contentH = panelH - 154.0f;
        int visibleRows = Math.min(Math.max(0, songs.size() - resultScroll), Math.max(0, (int) ((contentH - 38.0f) / resultRowHeight)));
        for (int row = 0; row < visibleRows; row++) {
            int index = resultScroll + row;
            double rowY = resultTop + row * resultRowHeight;
            if (mouseX >= x + 28.0f && mouseX <= x + panelW * 0.67f - 8.0f && mouseY >= rowY && mouseY <= rowY + 26.0f) {
                Managers.MUSIC.play(songs.get(index));
                return true;
            }
        }
        float footerY = y + panelH - 52.0f;
        if (mouseX >= x + 20.0f && mouseX <= x + panelW - 20.0f && mouseY >= footerY - 12.0f && mouseY <= footerY - 2.0f) {
            draggingProgress = true;
            updateDragPosition(mouseX, x, panelW);
            return true;
        }
        if (mouseY >= footerY + 3.0f && mouseY <= footerY + 27.0f && mouseX >= x + panelW - 248.0f && mouseX <= x + panelW - 216.0f) { Managers.MUSIC.previous(); return true; }
        if (mouseY >= footerY + 3.0f && mouseY <= footerY + 27.0f && mouseX >= x + panelW - 210.0f && mouseX <= x + panelW - 178.0f) { Managers.MUSIC.togglePause(); return true; }
        if (mouseY >= footerY + 3.0f && mouseY <= footerY + 27.0f && mouseX >= x + panelW - 172.0f && mouseX <= x + panelW - 140.0f) { Managers.MUSIC.next(); return true; }
        if (mouseY >= footerY + 3.0f && mouseY <= footerY + 27.0f && mouseX >= x + panelW - 134.0f && mouseX <= x + panelW - 102.0f) { Managers.MUSIC.cyclePlayMode(); return true; }
        if (mouseX >= x + panelW - 92.0f && mouseX <= x + panelW - 60.0f && mouseY >= footerY && mouseY <= footerY + 30.0f) {
            onClose();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        MusicPlayerSnapshot snapshot = Managers.MUSIC == null ? MusicPlayerSnapshot.empty() : Managers.MUSIC.snapshot();
        int width = UiCoordinateMapper.getProjectionWidthInt();
        int height = UiCoordinateMapper.getProjectionHeightInt();
        float panelW = Math.min(840.0f, Math.max(320.0f, width - 48.0f));
        float panelH = Math.min(620.0f, Math.max(260.0f, height - 42.0f));
        int visible = Math.max(1, (int) ((panelH - 192.0f) / Math.max(1, resultRowHeight)));
        resultScroll = Math.max(0, Math.min(Math.max(0, snapshot.results().size() - visible), resultScroll - (int) Math.signum(scrollY) * 3));
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (!draggingProgress) return super.mouseDragged(event, mouseX, mouseY);
        int width = UiCoordinateMapper.getProjectionWidthInt();
        float panelW = Math.min(840.0f, Math.max(320.0f, width - 48.0f));
        float x = (width - panelW) * 0.5f;
        updateDragPosition(UiCoordinateMapper.toProjectionX(mouseX), x, panelW);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingProgress) {
            draggingProgress = false;
            if (Managers.MUSIC != null) Managers.MUSIC.seek(dragPositionMs);
            return true;
        }
        return super.mouseReleased(event);
    }

    private void updateDragPosition(double mouseX, float panelX, float panelW) {
        MusicPlayerSnapshot snapshot = Managers.MUSIC == null ? MusicPlayerSnapshot.empty() : Managers.MUSIC.snapshot();
        double ratio = Math.max(0.0, Math.min(1.0, (mouseX - panelX - 20.0) / Math.max(1.0, panelW - 40.0)));
        dragPositionMs = Math.round(snapshot.durationMs() * ratio);
    }

    private void search() {
        if (Managers.MUSIC != null) Managers.MUSIC.search(query);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            onClose();
            return true;
        }
        boolean ctrl = InputConstants.isKeyDown(Constants.mc.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(Constants.mc.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
        if ((queryFocused || playlistFocused) && ctrl && event.key() == GLFW.GLFW_KEY_V) {
            String clipboard = Constants.mc.keyboardHandler.getClipboard();
            if (queryFocused) query = clipboard == null ? "" : clipboard.substring(0, Math.min(80, clipboard.length()));
            else playlistId = clipboard == null ? "" : clipboard.replaceAll("\\D", "").substring(0, Math.min(20, clipboard.replaceAll("\\D", "").length()));
            return true;
        }
        if (queryFocused && event.key() == 257) {
            search();
            return true;
        }
        if (queryFocused && event.key() == 259) {
            if (!query.isEmpty()) query = query.substring(0, query.offsetByCodePoints(query.length(), -1));
            return true;
        }
        if (playlistFocused && event.key() == 259) { if (!playlistId.isEmpty()) playlistId = playlistId.substring(0, playlistId.length() - 1); return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (queryFocused && event.isAllowedChatCharacter()) {
            String typed = event.codepointAsString();
            if (query.length() < 80) query += typed;
            return true;
        }
        if (playlistFocused && event.isAllowedChatCharacter() && Character.isDigit(event.codepoint())) { if (playlistId.length() < 20) playlistId += event.codepointAsString(); return true; }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        if (MusicPlayer.INSTANCE.isEnabled()) {
            MusicPlayer.INSTANCE.setEnabled(false);
        } else {
            super.onClose();
        }
    }

    @Override
    public void removed() {
        releaseScene();
        if (MusicPlayer.INSTANCE.isEnabled()) MusicPlayer.INSTANCE.setEnabled(false);
        super.removed();
    }

    private void releaseScene() {
        UiScene previous = scene;
        scene = null;
        sceneRuntime = null;
        textMetrics = null;
        if (previous != null) previous.close();
    }
}
