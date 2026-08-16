package tech.hakuri.graven.managers.impl.rotations;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.bus.EventPriority;
import tech.hakuri.graven.events.impl.*;
import tech.hakuri.graven.modules.impl.ClientSetting;
import tech.hakuri.graven.modules.impl.movement.MovementFix;
import tech.hakuri.graven.modules.impl.render.FreeCamera;

import static tech.hakuri.graven.Constants.mc;

public class SilentRotationManager extends RotationManager {

    @Override
    protected void handleSendPosition(SendPositionEvent event) {
        float yaw = rotations.getYaw();
        float pitch = rotations.getPitch();

        if (!Float.isNaN(yaw) && !Float.isNaN(pitch)) {
            event.setYaw(yaw);
            event.setPitch(pitch);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onMoveInput(KeyboardInputEvent event) {
        MovementFix moveFix = MovementFix.INSTANCE;
        if (moveFix.isEnabled() && hasActiveRotation() && !mc.player.isFallFlying()) {
            moveFix.fixMovement(event, rotations.getYaw());
        }
    }

    @Override
    protected boolean shouldModifyCrosshair() {
        return ClientSetting.INSTANCE.modifyCrosshair.getValue() && !FreeCamera.INSTANCE.isEnabled();
    }

    @EventHandler
    private void onItemRaytrace(UseItemRaytraceEvent event) {
        if (hasActiveRotation()) {
            event.setYaw(rotations.getYaw());
            event.setPitch(rotations.getPitch());
        }
    }

    @EventHandler
    private void onStrafe(StrafeEvent event) {
        if (MovementFix.INSTANCE.isEnabled() && hasActiveRotation() && !mc.player.isFallFlying()) {
            event.setYaw(rotations.getYaw());
        }
    }

    @EventHandler
    private void onJump(JumpEvent event) {
        if (MovementFix.INSTANCE.isEnabled() && hasActiveRotation() && !mc.player.isFallFlying()) {
            event.setYaw(rotations.getYaw());
        }
    }

    @EventHandler
    private void onFallFlying(FallFlyingEvent event) {
        if (MovementFix.INSTANCE.isEnabled() && hasActiveRotation()) {
            event.setYaw(rotations.getYaw());
            event.setPitch(rotations.getPitch());
        }
    }

    @EventHandler
    private void onUseItem(UseItemEvent event) {
        if (hasActiveRotation()) {
            event.setYaw(rotations.getYaw());
            event.setPitch(rotations.getPitch());
        }
    }

    @EventHandler
    private void onFireworkUpdate(FireworkRotationEvent event) {
        if (hasActiveRotation()) {
            event.setYaw(rotations.getYaw());
            event.setPitch(rotations.getPitch());
        }
    }

    @EventHandler
    private void onAttack(AttackYawEvent event) {
        if (rotations != null) {
            event.setYaw(rotations.getYaw());
        }
    }

}
