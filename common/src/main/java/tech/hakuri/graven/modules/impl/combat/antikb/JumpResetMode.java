package tech.hakuri.graven.modules.impl.combat.antikb;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import tech.hakuri.graven.events.impl.KeyboardInputEvent;
import tech.hakuri.graven.events.impl.PacketEvent;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.events.impl.StrafeEvent;
import tech.hakuri.graven.managers.Managers;
import tech.hakuri.graven.modules.impl.combat.AntiKB;
import tech.hakuri.graven.modules.impl.movement.Scaffold;
import tech.hakuri.graven.utils.rotation.Priority;
import tech.hakuri.graven.utils.rotation.Rot2f;

/** OpenZen Jump Reset 的 AIR/GROUND 阶段、旋转和跳跃键生命周期。 */
public final class JumpResetMode extends AntiKBMode {
    private enum Phase { IDLE, AIR, GROUND }

    private int delayTicks;
    private int rotationHeldTicks;
    private int jumpTicks;
    private boolean suspending;
    private Phase phase = Phase.IDLE;
    private ClientboundSetEntityMotionPacket knockback;
    private Rot2f targetRotation;

    public JumpResetMode(AntiKB owner) {
        super(owner);
    }

    @Override
    public boolean isSuspending() {
        return suspending;
    }

    @Override
    public void enable() {
        resetState();
        restoreMovementKeys();
    }

    @Override
    public void disable() {
        release();
        resetState();
        restoreMovementKeys();
    }

    @Override
    public void receive(PacketEvent.Receive event) {
        if (isFlushing() || nullCheck()) return;
        Packet<?> packet = event.getPacket();

        if (packet instanceof ClientboundPlayerPositionPacket || packet instanceof ClientboundPlayerRotationPacket) {
            release();
            Managers.ROTATION.setActive(false);
            resetState();
            return;
        }

        if (suspending) {
            if (!(packet instanceof ClientboundSystemChatPacket) && !(packet instanceof ClientboundSetTimePacket)) {
                incoming.add(packet);
                event.cancel();
            }
            return;
        }

        if (!(packet instanceof ClientboundSetEntityMotionPacket motion)
                || motion.id() != mc().player.getId()) return;

        knockback = motion;
        boolean wantRotate = owner.rotate.getValue() || owner.followDirection.getValue();
        Rot2f rotation = wantRotate
                ? new Rot2f((float) Math.toDegrees(Math.atan2(motion.movement().x, -motion.movement().z)), mc().player.getXRot())
                : null;
        if (!mc().player.onGround()) {
            phase = Phase.AIR;
            delayTicks = 20;
            if (rotation != null) setRotation(rotation);
        } else {
            phase = Phase.GROUND;
            delayTicks = 10;
            targetRotation = rotation;
        }
        suspending = true;
        jumpTicks = 0;
        incoming.add(packet);
        event.cancel();
    }

    @Override
    public void send(PacketEvent.Send event) {
        if (!isFlushing() && suspending && event.getPacket() instanceof ServerboundMovePlayerPacket) {
            outgoing.add(event.getPacket());
            event.cancel();
        }
    }

    @Override
    public void tick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;
        if (suspending) {
            if (phase == Phase.AIR) {
                if (mc().player.onGround() || --delayTicks <= 0) finishAir();
            } else if (phase == Phase.GROUND && --delayTicks <= 0) {
                finishGround();
            }
        }

        if (Managers.ROTATION.isActive()) rotationHeldTicks++;
        boolean clearRotation = mc().player.hurtTime == 0
                || rotationHeldTicks > owner.rotateTicks.getValue()
                || (!owner.rotate.getValue() && !owner.followDirection.getValue());
        if (clearRotation) {
            Managers.ROTATION.setActive(false);
            rotationHeldTicks = 0;
            targetRotation = null;
        }
    }

    @Override
    public void input(KeyboardInputEvent event) {
        if (phase == Phase.GROUND && suspending && !Scaffold.INSTANCE.isEnabled()) {
            event.setJump(isKeyDown(mc().options.keyJump));
        }
        if (jumpTicks > 0 && !Scaffold.INSTANCE.isEnabled()) {
            event.setJump(true);
            jumpTicks--;
        }
    }

    @Override
    public void strafe(StrafeEvent event) {
        if (suspending && owner.followDirection.getValue() && targetRotation != null) {
            event.setYaw(targetRotation.getYaw());
        }
    }

    private void finishAir() {
        release();
        resetState();
    }

    private void finishGround() {
        release();
        if (targetRotation != null) {
            setRotation(targetRotation);
            targetRotation = null;
        }
        resetState();
        jumpTicks = 1;
    }

    private void setRotation(Rot2f rotation) {
        Managers.ROTATION.setRotations(rotation, owner.rotateTicks.getValue(), Priority.High);
        rotationHeldTicks = 0;
    }

    private void resetState() {
        clear();
        delayTicks = 0;
        rotationHeldTicks = 0;
        jumpTicks = 0;
        suspending = false;
        phase = Phase.IDLE;
        knockback = null;
        targetRotation = null;
    }

    private void restoreMovementKeys() {
        if (mc().getWindow() == null) return;
        restore(mc().options.keyJump);
    }

    private boolean isKeyDown(KeyMapping mapping) {
        return InputConstants.isKeyDown(mc().getWindow(), mapping.getDefaultKey().getValue());
    }

    private void restore(KeyMapping mapping) {
        InputConstants.Key key = InputConstants.getKey(mapping.saveString());
        mapping.setDown(key.getType() == InputConstants.Type.MOUSE
                ? org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc().getWindow().handle(), key.getValue()) == 1
                : InputConstants.isKeyDown(mc().getWindow(), key.getValue()));
    }

    private net.minecraft.client.Minecraft mc() {
        return net.minecraft.client.Minecraft.getInstance();
    }
}
