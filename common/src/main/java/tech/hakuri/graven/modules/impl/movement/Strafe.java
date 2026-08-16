package tech.hakuri.graven.modules.impl.movement;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.KeyboardInputEvent;
import tech.hakuri.graven.events.impl.MoveEvent;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.utils.player.MoveUtils;
import tech.hakuri.graven.utils.player.PlayerUtils;
import net.minecraft.world.effect.MobEffects;

public class Strafe extends Module {

    public static final Strafe INSTANCE = new Strafe();

    private Strafe() {
        super("Strafe", Category.MOVEMENT);
    }

    private final BoolSetting airStop = boolSetting("Air Stop", true);
    private final BoolSetting autoJump = boolSetting("Auto Jump", true);

    @EventHandler
    private void onMove(MoveEvent event) {
        if (
                mc.player.isCrouching() || mc.player.isFallFlying() || mc.player.isInLava()
                        || PlayerUtils.isInBlock() || mc.player.isInWater() || mc.player.getAbilities().flying
                        || Flight.INSTANCE.isEnabled() || Speed.INSTANCE.isEnabled()
        ) {
            return;
        }

        if (airStop.getValue() && !mc.player.isMoving()) {
            mc.player.setDeltaMovement(0.0, mc.player.getDeltaMovement().y, 0.0);
            return;
        }

        double speed = 0.2873;

        if (mc.player.hasEffect(MobEffects.SPEED) && !mc.player.hasEffect(MobEffects.SLOWNESS)) {
            speed *= 1.0 + 0.2 * (mc.player.getEffect(MobEffects.SPEED).getAmplifier() + 1.0);
        }

        double[] strafe = MoveUtils.forward(speed);
        event.setX(strafe[0]);
        event.setZ(strafe[1]);
        event.cancel();
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (autoJump.getValue() && mc.player.isMoving()) event.setJump(true);
    }

}
