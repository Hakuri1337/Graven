package tech.hakuri.graven.modules.impl.movement;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;

public final class FlightDelayTrigger extends Module {

    public static final FlightDelayTrigger INSTANCE = new FlightDelayTrigger();

    private boolean prevFlying;
    private boolean triggerArmed;
    private int tickCount;

    private FlightDelayTrigger() {
        super("Flight Delay Trigger", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        prevFlying = isPlayerFlying();
        triggerArmed = false;
        tickCount = 0;
    }

    @Override
    protected void onDisable() {
        triggerArmed = false;
        tickCount = 0;
    }

    @Override
    protected void resetCustomState() {
        prevFlying = false;
        triggerArmed = false;
        tickCount = 0;
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;

        boolean flying = isPlayerFlying();
        if (flying && !prevFlying) {
            triggerArmed = true;
            tickCount = 0;
        }

        if (!flying && prevFlying) {
            triggerArmed = false;
            tickCount = 0;
        }

        if (triggerArmed) {
            tickCount++;
            if (tickCount == 2) {
                Flight.INSTANCE.setEnabled(true);
                Clip.INSTANCE.setEnabled(true);
            }
            if (tickCount == 3) {
                Freeze.INSTANCE.setEnabled(true);
                triggerArmed = false;
            }
        }

        prevFlying = flying;
    }

    private boolean isPlayerFlying() {
        return mc.player != null && mc.player.getAbilities().flying;
    }
}
