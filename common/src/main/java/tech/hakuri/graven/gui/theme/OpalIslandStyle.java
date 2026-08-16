package tech.hakuri.graven.gui.theme;

import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftBlurRegion2612;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;

import java.awt.Color;

/** Opal 动态岛与 Dropdown 搜索框共享的视觉令牌。 */
public final class OpalIslandStyle {

    public static final String BODY_FONT = "graven-opal-medium";
    public static final String TITLE_FONT = "graven-opal-bold";
    public static final String ICON_FONT = "graven-icons";

    public static final float HEIGHT = 28.0f;
    public static final float RADIUS = 13.0f;
    public static final float TOP = 10.0f;
    public static final float DEFAULT_WIDTH = 146.0f;
    public static final float SEARCH_WIDTH = 210.0f;
    public static final float SEARCH_MARGIN = 12.0f;
    public static final float SEARCH_ICON_SIZE = 0.68f;
    public static final float SEARCH_GAP = 9.0f;
    public static final long ANIMATION_DURATION = 250L;
    public static final float BLUR_STRENGTH = 8.0f;

    public static final Color BACKGROUND = new Color(16, 17, 19, 178);
    public static final Color OUTLINE = new Color(255, 255, 255, 32);
    public static final Color SHADOW = new Color(0, 0, 0, 98);
    public static final Color TEXT = new Color(255, 255, 255, 255);
    public static final Color MUTED_TEXT = new Color(181, 181, 183, 255);
    public static final Color ACCENT_START = new Color(78, 178, 224, 255);
    public static final Color ACCENT_END = new Color(66, 148, 186, 255);

    private OpalIslandStyle() {
    }

    public static void applyBlur(float x, float y, float width, float height) {
        if (width <= 0.0f || height <= 0.0f) return;
        MinecraftUiRuntime2612.current().applyBlur(MinecraftBlurRegion2612.rounded(
                new UiRect(x, y, width, height), Math.min(RADIUS, height / 2.0f), BLUR_STRENGTH));
    }

    public static void drawSurface(UiTree.Scope scope, float x, float y, float width, float height) {
        float radius = Math.min(RADIUS, height / 2.0f);
        scope.shadow(x, y, width, height, radius, 12.0f, SHADOW);
        scope.roundRect(x, y, width, height, radius, BACKGROUND);
        scope.outline(x, y, width, height, radius, 0.65f, OUTLINE);
    }
}
