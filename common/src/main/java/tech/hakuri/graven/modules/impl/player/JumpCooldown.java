package tech.hakuri.graven.modules.impl.player;

import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.IntSetting;

public class JumpCooldown extends Module {

    public static final JumpCooldown INSTANCE = new JumpCooldown();

    private JumpCooldown() {
        super("Jump Cooldown", Category.PLAYER);
    }

    public final IntSetting cooldown = intSetting("Cooldown", 0, 0, 9, 1);

}
