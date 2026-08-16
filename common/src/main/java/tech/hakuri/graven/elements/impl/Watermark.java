package tech.hakuri.graven.elements.impl;

import tech.hakuri.graven.assets.resources.ResourceLocationUtils;
import tech.hakuri.graven.elements.HudModule;
import tech.hakuri.graven.settings.impl.ColorSetting;
import tech.hakuri.graven.settings.impl.DoubleSetting;
import net.minecraft.client.DeltaTracker;

import java.awt.*;

public class Watermark extends HudModule {

    public static final Watermark INSTANCE = new Watermark();
    private static final String CLIENT_BAND_TEXTURE =
            ResourceLocationUtils.getIdentifier("textures/icons/client_band.png").toString();

    private Watermark() {
        super("Watermark", 0f, 0f, 28f, 28f);
    }

    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.0, 0.1);
    private final ColorSetting textColor = colorSetting("Text Color", new Color(255, 255, 255, 235));

    @Override
    public void render(DeltaTracker deltaTracker) {
        float height = scale.getValue().floatValue() * 28.0f;
        float width = height * (144.0f / 128.0f);
        renderScope().texture(CLIENT_BAND_TEXTURE, this.x, this.y, width, height, lumin(textColor.getValue()));
        setBounds(width, height);
    }

}
