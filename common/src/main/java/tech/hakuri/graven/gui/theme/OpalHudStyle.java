package tech.hakuri.graven.gui.theme;

import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftBlurRegion2612;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import net.minecraft.util.Mth;

import java.awt.Color;
import java.util.List;

/** EdOpal HUD 组件共享的颜色、字体和圆角表面令牌。 */
public final class OpalHudStyle {

    public static final String MEDIUM_FONT = OpalIslandStyle.BODY_FONT;
    public static final String BOLD_FONT = OpalIslandStyle.TITLE_FONT;
    public static final String ICON_FONT = OpalIslandStyle.ICON_FONT;

    public static final Color BACKGROUND = new Color(9, 9, 9, 128);
    public static final Color BACKGROUND_SOFT = new Color(9, 9, 9, 102);
    public static final Color TEXT = Color.WHITE;
    public static final Color MUTED_TEXT = new Color(170, 170, 170);
    public static final Color ACCENT_START = OpalIslandStyle.ACCENT_START;
    public static final Color ACCENT_END = OpalIslandStyle.ACCENT_END;
    public static final Color OUTLINE = new Color(0, 0, 0, 220);

    private OpalHudStyle() {
    }

    public static boolean active() {
        return MD3Theme.isOpalTheme();
    }

    public static Color accent(int index) {
        long raw = Math.floorMod(System.currentTimeMillis() / 6L - index * 20L, 360L);
        float phase = (raw >= 180L ? 360L - raw : raw) * 2.0f / 360.0f;
        return interpolate(ACCENT_START, ACCENT_END, phase);
    }

    public static Color interpolate(Color start, Color end, float progress) {
        float value = Mth.clamp(progress, 0.0f, 1.0f);
        return new Color(
                Math.round(Mth.lerp(value, start.getRed(), end.getRed())),
                Math.round(Mth.lerp(value, start.getGreen(), end.getGreen())),
                Math.round(Mth.lerp(value, start.getBlue(), end.getBlue())),
                Math.round(Mth.lerp(value, start.getAlpha(), end.getAlpha())));
    }

    public static Color withAlpha(Color color, float alphaScale) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Mth.clamp(Math.round(color.getAlpha() * Mth.clamp(alphaScale, 0.0f, 1.0f)), 0, 255));
    }

    public static Color darker(Color color, float amount, float alphaScale) {
        float factor = 1.0f - Mth.clamp(amount, 0.0f, 1.0f);
        return new Color(
                Math.round(color.getRed() * factor),
                Math.round(color.getGreen() * factor),
                Math.round(color.getBlue() * factor),
                Mth.clamp(Math.round(255.0f * alphaScale), 0, 255));
    }

    public static void applyBlur(float x, float y, float width, float height,
                                 float topLeft, float topRight, float bottomRight, float bottomLeft) {
        if (width <= 0.0f || height <= 0.0f) return;
        MinecraftUiRuntime2612.current().applyBlur(new MinecraftBlurRegion2612(
                new UiRect(x, y, width, height),
                new MinecraftBlurRegion2612.CornerRadii(topLeft, topRight, bottomRight, bottomLeft),
                OpalIslandStyle.BLUR_STRENGTH,
                List.of()));
    }

    public static void drawSurface(UiTree.Scope scope, float x, float y, float width, float height,
                                   float topLeft, float topRight, float bottomRight, float bottomLeft,
                                   float alpha) {
        scope.roundRect(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft,
                withAlpha(BACKGROUND, alpha));
    }
}
