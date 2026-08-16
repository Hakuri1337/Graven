package tech.hakuri.graven.managers.impl.rotations;

import tech.hakuri.graven.events.bus.EventBus;
import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.bus.EventPriority;
import tech.hakuri.graven.events.impl.ClientTickEvent;
import tech.hakuri.graven.events.impl.MouseTurnEvent;
import tech.hakuri.graven.utils.rotation.Rot2f;
import tech.hakuri.graven.utils.rotation.model.RotationModel;
import net.minecraft.util.Mth;

import static tech.hakuri.graven.Constants.mc;

public final class MouseRotationController {

    public static final MouseRotationController INSTANCE = new MouseRotationController();

    private static final double TURN_MULTIPLIER = 0.15D;

    private final ClientRotationTracker clientRotation = ClientRotationTracker.INSTANCE;
    private RotationModel rotationModel;
    private Rot2f targetRotation;
    private Rot2f originalRotation;
    private Rot2f tickRotation;
    private boolean active;
    private boolean forward;

    private MouseRotationController() {
        EventBus.INSTANCE.subscribe(this);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPreTick(ClientTickEvent.Pre event) {
        if (mc.player == null) return;
        forceTick();
        reverse();
        tickRotation = currentRotation();
    }

    @EventHandler
    private void onMouseTurn(MouseTurnEvent event) {
        if (tickRotation == null || targetRotation == null || mc.player == null || !active) return;

        if (originalRotation != null) {
            originalRotation = new Rot2f(
                    originalRotation.getYaw() + (float) event.getInputX() * 0.15F,
                    Mth.clamp(originalRotation.getPitch() + (float) event.getInputY() * 0.15F, -90.0F, 90.0F)
            );
        }

        if (!forward) {
            resetToClient();
            if (targetRotation == null) return;
        }

        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Rot2f tickedRotation = rotationModel.tick(tickRotation, targetRotation, tickDelta);
        event.setX((tickedRotation.getYaw() - mc.player.getYRot()) / TURN_MULTIPLIER);
        event.setY((tickedRotation.getPitch() - mc.player.getXRot()) / TURN_MULTIPLIER);

        if (!forward && rotationDifference(tickedRotation, targetRotation) == 0.0F) {
            deactivate();
        }
    }

    public void rotate(Rot2f targetRotation, RotationModel rotationModel) {
        if (targetRotation == null || rotationModel == null || mc.player == null) return;
        if (!active) {
            originalRotation = currentRotation();
            clientRotation.initialize(originalRotation.getYaw(), originalRotation.getPitch());
        }
        this.targetRotation = targetRotation;
        this.rotationModel = rotationModel;
        this.forward = true;
        this.active = true;
        forceTick();
    }

    public void reset() {
        if (active && originalRotation != null && mc.player != null) {
            mc.player.setYRot(originalRotation.getYaw());
            mc.player.setXRot(originalRotation.getPitch());
            mc.player.setYBodyRot(originalRotation.getYaw());
            mc.player.setYHeadRot(originalRotation.getYaw());
        }
        deactivate();
        originalRotation = null;
        tickRotation = null;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isForward() {
        return forward;
    }

    private void forceTick() {
        if (!active || rotationModel == null || tickRotation == null || targetRotation == null || mc.player == null) return;
        Rot2f tickedRotation = rotationModel.tick(tickRotation, targetRotation, 1.0F);
        applyRotation(tickedRotation);
        if (!forward && rotationDifference(tickedRotation, targetRotation) == 0.0F) {
            deactivate();
        }
    }

    private void applyRotation(Rot2f rotation) {
        double yawDelta = rotation.getYaw() - mc.player.getYRot();
        double pitchDelta = rotation.getPitch() - mc.player.getXRot();
        mc.player.turn(yawDelta / TURN_MULTIPLIER, pitchDelta / TURN_MULTIPLIER);
    }

    private void reverse() {
        if (!forward) return;
        resetToClient();
        forward = false;
    }

    private void resetToClient() {
        targetRotation = clientRotation.getRotation();
    }

    private void deactivate() {
        active = false;
        forward = false;
        targetRotation = null;
        rotationModel = null;
    }

    private Rot2f currentRotation() {
        return new Rot2f(mc.player.getYRot(), mc.player.getXRot());
    }

    private static float rotationDifference(Rot2f a, Rot2f b) {
        return Mth.degreesDifferenceAbs(a.getYaw(), b.getYaw()) + Math.abs(a.getPitch() - b.getPitch());
    }
}
