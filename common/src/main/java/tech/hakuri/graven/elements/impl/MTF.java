package tech.hakuri.graven.elements.impl;

import tech.hakuri.graven.elements.HudModule;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import tech.hakuri.graven.settings.impl.ColorSetting;
import tech.hakuri.graven.settings.impl.DoubleSetting;
import net.minecraft.client.DeltaTracker;

import java.awt.*;

public class MTF extends HudModule {

    public static final MTF INSTANCE = new MTF();

    private MTF() {
        super("MTF", 0f, 0f, 36f, 36f);
    }

    private final DoubleSetting size = doubleSetting("Size", 32.0, 12.0, 96.0, 1.0);
    private final DoubleSetting speed = doubleSetting("Speed", 160.0, -720.0, 720.0, 5.0);
    private final ColorSetting color = colorSetting("Color", new Color(255, 255, 255, 255), true);

    @Override
    public void render(DeltaTracker deltaTracker) {
        float boxSize = size.getValue().floatValue();
        setBounds(boxSize, boxSize);

        float originX = this.x + boxSize / 2.0f;
        float originY = this.y + boxSize / 2.0f;
        float rotation = (System.currentTimeMillis() % 3_600_000L) / 1000.0f * speed.getValue().floatValue();

        // UiTree texture nodes resolve resource-pack textures. Dynamic glyph atlases are TextureManager-only.
        renderScope().rotatedTexture("minecraft:textures/item/cake.png", new UiRect(this.x, this.y, boxSize, boxSize),
                0.0f, 0.0f, 1.0f, 1.0f,
                lumin(color.getValue()), originX, originY, rotation);
    }

}
