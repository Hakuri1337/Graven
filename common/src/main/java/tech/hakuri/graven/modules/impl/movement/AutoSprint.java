package tech.hakuri.graven.modules.impl.movement;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.modules.impl.combat.KillAura;

public class AutoSprint extends Module {

    public static final AutoSprint INSTANCE = new AutoSprint();

    private AutoSprint() {
        super("Auto Sprint", Category.MOVEMENT);
    }

    @Override
    protected void onDisable() {
        if (mc.options.keySprint.isDown()) mc.options.keySprint.setDown(false);
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (KillAura.INSTANCE.isHeypixelKeepSprintTransition()) return;
        mc.options.keySprint.setDown(true);
    }

}
