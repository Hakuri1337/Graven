package tech.hakuri.graven.modules.impl.player;

import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.IntSetting;

public class UseCooldown extends Module {

    public static final UseCooldown INSTANCE = new UseCooldown();

    private UseCooldown() {
        super("Use Cooldown", Category.PLAYER);
    }

    public final IntSetting cooldown = intSetting("Cooldown", 0, 0, 4, 1);

}
