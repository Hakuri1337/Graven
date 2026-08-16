package tech.hakuri.graven.modules.impl.combat.antikb;

import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import tech.hakuri.graven.Constants;
import tech.hakuri.graven.events.impl.KeyboardInputEvent;
import tech.hakuri.graven.events.impl.PacketEvent;
import tech.hakuri.graven.events.impl.PostMovementPacketEvent;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.events.impl.StrafeEvent;
import tech.hakuri.graven.managers.Managers;
import tech.hakuri.graven.modules.impl.combat.AntiKB;
import tech.hakuri.graven.modules.impl.combat.KillAura;
import tech.hakuri.graven.utils.network.PacketUtils;

import java.util.ArrayDeque;
import java.util.Queue;

/** AntiKB 各模式共享的生命周期、目标和包回放基础。 */
public abstract class AntiKBMode {
    protected final AntiKB owner;
    protected final Queue<Packet<?>> incoming = new ArrayDeque<>();
    protected final Queue<Packet<?>> outgoing = new ArrayDeque<>();
    private boolean flushing;
    private boolean replayAfterMovement;

    protected AntiKBMode(AntiKB owner) {
        this.owner = owner;
    }

    public void enable() {
        clear();
    }

    public void disable() {
        release();
    }

    public boolean isSuspending() {
        return false;
    }

    protected final boolean isFlushing() {
        return flushing;
    }

    public void tick(PlayerTickEvent.Pre event) {
    }

    public void receive(PacketEvent.Receive event) {
    }

    public void send(PacketEvent.Send event) {
    }

    public void input(KeyboardInputEvent event) {
    }

    public void strafe(StrafeEvent event) {
    }

    public void postMovement(PostMovementPacketEvent event) {
        if (replayAfterMovement) {
            replayAfterMovement = false;
            flushIncoming();
        }
    }

    protected final boolean nullCheck() {
        return Constants.mc.player == null || Constants.mc.level == null || Constants.mc.getConnection() == null;
    }

    protected final Entity target() {
        if (KillAura.INSTANCE.target != null) return KillAura.INSTANCE.target;
        HitResult result = Managers.ROTATION.getHitResult();
        if (result instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living
                && living != Constants.mc.player && living.isAlive()) return living;
        return null;
    }

    protected final boolean inReach(Entity entity) {
        if (entity == null || !entity.isAlive() || Constants.mc.player == null) return false;
        Vec3 eye = Constants.mc.player.getEyePosition(1.0f);
        var box = entity.getBoundingBox();
        double x = Math.max(box.minX, Math.min(eye.x, box.maxX));
        double y = Math.max(box.minY, Math.min(eye.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(eye.z, box.maxZ));
        return eye.distanceTo(new Vec3(x, y, z)) <= 3.7D;
    }

    protected final boolean attack(Entity entity) {
        if (!inReach(entity) || Constants.mc.gameMode == null) return false;
        if (owner.sprintStateCheck.getValue() && !Constants.mc.player.isSprinting()) return false;
        boolean sprinting = Constants.mc.player.isSprinting();
        if (sprinting) Constants.mc.player.setSprinting(false);
        Constants.mc.gameMode.attack(Constants.mc.player, entity);
        Constants.mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (sprinting) {
            Vec3 velocity = Constants.mc.player.getDeltaMovement();
            Constants.mc.player.setDeltaMovement(velocity.x * 0.6D, velocity.y, velocity.z * 0.6D);
        }
        return true;
    }

    protected final void scheduleIncomingReplay() {
        replayAfterMovement = true;
    }

    protected final void clearReplaySchedule() {
        replayAfterMovement = false;
    }

    protected final void handleIncomingNow(Packet<?> packet) {
        if (packet == null || Constants.mc.getConnection() == null) return;
        PacketListener listener = Constants.mc.getConnection().getConnection().getPacketListener();
        flushing = true;
        try {
            @SuppressWarnings("rawtypes") Packet raw = packet;
            raw.handle(listener);
        } catch (RuntimeException exception) {
            Constants.LOGGER.error("AntiKB 入站包立即回放失败: {}", packet.type(), exception);
        } finally {
            flushing = false;
        }
    }

    protected final void flushIncoming() {
        if (Constants.mc.getConnection() == null) {
            incoming.clear();
            return;
        }
        PacketListener listener = Constants.mc.getConnection().getConnection().getPacketListener();
        flushing = true;
        try {
            while (!incoming.isEmpty()) {
                Packet<?> packet = incoming.poll();
                try {
                    @SuppressWarnings("rawtypes") Packet raw = packet;
                    raw.handle(listener);
                } catch (RuntimeException exception) {
                    Constants.LOGGER.error("AntiKB 入站包回放失败: {}", packet.type(), exception);
                    incoming.clear();
                    return;
                }
            }
        } finally {
            flushing = false;
        }
    }

    protected final void flushOutgoing() {
        flushing = true;
        try {
            while (!outgoing.isEmpty()) PacketUtils.sendSilently(outgoing.poll());
        } finally {
            flushing = false;
        }
    }

    protected void release() {
        clearReplaySchedule();
        flushOutgoing();
        flushIncoming();
        clear();
    }

    protected final void clear() {
        incoming.clear();
        outgoing.clear();
        replayAfterMovement = false;
    }

}
