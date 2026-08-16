package tech.hakuri.graven.managers.impl.rotations;

import tech.hakuri.graven.events.bus.EventBus;
import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.bus.EventPriority;
import tech.hakuri.graven.events.impl.ClientTickEvent;
import tech.hakuri.graven.events.impl.GameJoinedEvent;
import tech.hakuri.graven.events.impl.GameLeftEvent;
import tech.hakuri.graven.events.impl.MouseTurnEvent;
import tech.hakuri.graven.utils.rotation.Rot2f;
import net.minecraft.util.Mth;

import static tech.hakuri.graven.Constants.mc;

public final class ClientRotationTracker {

    public static final ClientRotationTracker INSTANCE = new ClientRotationTracker();

    private Rot2f rotation;

    private ClientRotationTracker() {
        EventBus.INSTANCE.subscribe(this);
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onMouseTurn(MouseTurnEvent event) {
        if (mc.player == null) return;
        initialize(mc.player.getYRot(), mc.player.getXRot());
        rotation = new Rot2f(
                rotation.getYaw() + (float) event.getInputX() * 0.15F,
                Mth.clamp(rotation.getPitch() + (float) event.getInputY() * 0.15F, -90.0F, 90.0F)
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onClientTick(ClientTickEvent.Pre event) {
        if (mc.player == null) {
            rotation = null;
        } else if (!MouseRotationController.INSTANCE.isActive()) {
            rotation = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        }
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        rotation = null;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        rotation = null;
    }

    public void initialize(float yaw, float pitch) {
        if (rotation == null) rotation = new Rot2f(yaw, pitch);
    }

    public Rot2f getRotation() {
        return rotation == null ? null : new Rot2f(rotation.getYaw(), rotation.getPitch());
    }

    public float getYawOr(float fallback) {
        return rotation == null ? fallback : rotation.getYaw();
    }
}
