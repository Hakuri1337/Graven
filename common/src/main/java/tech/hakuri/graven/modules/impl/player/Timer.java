package tech.hakuri.graven.modules.impl.player;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.managers.Managers;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.DoubleSetting;

public class Timer extends Module {

    public static final Timer INSTANCE = new Timer();

    private Timer() {
        super("Timer", Category.PLAYER);
    }

    public final DoubleSetting multiplier = doubleSetting("Multiplier", 1.0, 0.1, 5.0, 0.1);

    @Override
    protected void onEnable() {
        Managers.TIMER.reset();
    }

    @Override
    protected void onDisable() {
        Managers.TIMER.reset();
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        Managers.TIMER.tryReset();
    }

}
