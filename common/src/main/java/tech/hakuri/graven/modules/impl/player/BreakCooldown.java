package tech.hakuri.graven.modules.impl.player;

import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.IntSetting;

public class BreakCooldown extends Module {

    public static final BreakCooldown INSTANCE = new BreakCooldown();

    private BreakCooldown() {
        super("Break Cooldown", Category.PLAYER);
    }

    public final IntSetting cooldown = intSetting("Cooldown", 0, 0, 5, 1);

}
