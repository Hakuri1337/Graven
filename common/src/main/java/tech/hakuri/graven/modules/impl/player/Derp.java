package tech.hakuri.graven.modules.impl.player;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.managers.Managers;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.DoubleSetting;
import tech.hakuri.graven.utils.rotation.Priority;
import tech.hakuri.graven.utils.rotation.Rot2f;

public final class Derp extends Module {

    public static final Derp INSTANCE = new Derp();

    private final DoubleSetting spinSpeed = doubleSetting("Spin Speed", 30.0, -40.0, 40.0, 1.0);
    private final DoubleSetting pitch = doubleSetting("Pitch", 30.0, -40.0, 40.0, 1.0);
    private final BoolSetting priority = boolSetting("Priority", false);
    private float spinYaw;
    private Rot2f rotations;

    private Derp() {
        super("Derp", Category.PLAYER);
    }

    @Override
    protected void onDisable() {
        spinYaw = 0.0F;
        rotations = null;
        if (Managers.ROTATION != null) Managers.ROTATION.setActive(false);
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) {
            rotations = null;
            return;
        }
        if (spinYaw >= 360.0F || spinYaw <= -360.0F) spinYaw = 0.0F;
        spinYaw += spinSpeed.getValue().floatValue();
        rotations = new Rot2f(spinYaw, pitch.getValue().floatValue());
        Managers.ROTATION.setRotations(rotations, 180.0,
                priority.getValue() ? Priority.Highest : Priority.Lowest);
    }
}
