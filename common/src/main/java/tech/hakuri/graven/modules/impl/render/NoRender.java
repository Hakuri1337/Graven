package tech.hakuri.graven.modules.impl.render;

import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.BoolSetting;

public class NoRender extends Module {

    public static final NoRender INSTANCE = new NoRender();

    private NoRender() {
        super("No Render", Category.RENDER);
    }

    public final BoolSetting potionEffects = boolSetting("Potion Effects", true);
    public final BoolSetting blockOverlay = boolSetting("Block Overlay", true);
    public final BoolSetting explosions = boolSetting("Explosions", true);
    public final BoolSetting totems = boolSetting("Totems", true);
    public final BoolSetting totemAnimation = boolSetting("Totem Animation", true);
    public final BoolSetting portal = boolSetting("Portal", true);
    public final BoolSetting fireworks = boolSetting("Fireworks", true);
    public final BoolSetting fireOverlay = boolSetting("Fire Overlay", true);
    public final BoolSetting negativeEffects = boolSetting("Negative Effects", true);
    public final BoolSetting potionParticles = boolSetting("Potion Particles", true);

}
