package tech.hakuri.graven.modules.impl.combat.antikb;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import tech.hakuri.graven.events.impl.KeyboardInputEvent;
import tech.hakuri.graven.events.impl.PacketEvent;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.managers.Managers;
import tech.hakuri.graven.modules.impl.combat.AntiKB;

/** OpenZen NoXZ 的完整击退延迟、回放和攻击时序。 */
public final class NoXZMode extends AntiKBMode {
    private ClientboundSetEntityMotionPacket knockback;
    private int suspendTicks;
    private int attackRemaining;
    private int flagCooldown;
    private int hitCounter;
    private int sprintBoostCounter;
    private boolean shouldJump;
    private boolean suspending;
    private float instantProgress;
    private boolean instantAttacking;
    private Entity attackTarget;

    public NoXZMode(AntiKB owner) {
        super(owner);
    }

    @Override
    public boolean isSuspending() {
        return suspending;
    }

    @Override
    public void enable() {
        resetState();
    }

    @Override
    public void disable() {
        releaseNoXZ(false);
        resetState();
    }

    @Override
    public void receive(PacketEvent.Receive event) {
        if (isFlushing() || shouldIgnore()) return;
        Packet<?> packet = event.getPacket();

        if (packet instanceof ClientboundPlayerPositionPacket || packet instanceof ClientboundPlayerRotationPacket) {
            if (suspending) releaseNoXZ(false);
            resetState();
            flagCooldown = 2;
            return;
        }

        if (suspending) {
            if (!allowed(packet)) {
                incoming.add(packet);
                event.cancel();
            }
            return;
        }

        if (flagCooldown > 0 || !(packet instanceof ClientboundSetEntityMotionPacket motion)
                || motion.id() != mc().player.getId()) return;

        double dx = -motion.movement().x;
        double dz = -motion.movement().z;
        if (Math.abs(dx) > 0.01D || Math.abs(dz) > 0.01D) hitCounter = 1;
        if (motion.movement().y <= 0.0D) return;

        sprintBoostCounter = sprintBoostCounter % 100 + 100;
        if (sprintBoostCounter >= 100) shouldJump = true;

        Entity target = target();
        boolean canAttackOnGround = mc().player.onGround()
                && mc().player.isSprinting()
                && target != null
                && inReach(target);
        if (canAttackOnGround) {
            attackTarget = target;
            attackRemaining = owner.attackAmount.getValue();
            return;
        }

        beginSuspension(motion, event);
    }

    @Override
    public void send(PacketEvent.Send event) {
        if (isFlushing() || !suspending) return;
        if (event.getPacket() instanceof ServerboundMovePlayerPacket) {
            outgoing.add(event.getPacket());
            event.cancel();
        }
    }

    @Override
    public void input(KeyboardInputEvent event) {
        if (hitCounter > 0) event.setForward(1.0f);
        if (shouldJump && mc().player != null && mc().player.onGround() && !shouldIgnore()) {
            event.setJump(true);
            shouldJump = false;
        }
    }

    @Override
    public void tick(PlayerTickEvent.Pre event) {
        if (shouldIgnore()) {
            if (suspending) releaseNoXZ(false);
            resetState();
            return;
        }

        if (flagCooldown > 0) flagCooldown--;
        if (hitCounter > 0 && ++hitCounter > 2) hitCounter = 0;

        if (!suspending) {
            if (attackRemaining > 0 && attackTarget != null && inReach(attackTarget)) {
                attack(attackTarget);
                attackRemaining--;
            } else if (attackTarget == null || !inReach(attackTarget)) {
                attackTarget = null;
                attackRemaining = 0;
            }
            if (instantAttacking && attackRemaining <= 0) {
                instantAttacking = false;
                instantProgress = 0.0f;
                if (Managers.OPENZEN_TICK_RATE != null) Managers.OPENZEN_TICK_RATE.reset();
            }
            return;
        }

        boolean instantAttackEnabled = owner.instantAttack.getValue();
        if (instantAttackEnabled && instantProgress < 3.0f && Managers.OPENZEN_TICK_RATE != null) {
            Managers.OPENZEN_TICK_RATE.set(0.5f);
            instantProgress = Math.min(3.0f, instantProgress + 0.5f);
        }

        boolean onGround = mc().player.onGround();
        boolean timeout = ++suspendTicks >= 12;
        if (!onGround && !timeout) return;

        Entity target = target();
        boolean sprinting = mc().player.isSprinting();
        releaseNoXZ(true);
        if (onGround && target != null && inReach(target) && sprinting) {
            attackTarget = target;
            attackRemaining = instantAttackEnabled && instantProgress > 0.0f
                    ? (int) instantProgress : owner.attackAmount.getValue();
            if (instantAttackEnabled) {
                instantAttacking = true;
                if (Managers.OPENZEN_TICK_RATE != null) Managers.OPENZEN_TICK_RATE.set(4.0f);
            }
        } else {
            attackTarget = null;
            attackRemaining = 0;
            instantProgress = 0.0f;
            if (onGround && sprinting) mc().player.setSprinting(false);
        }
    }

    private void beginSuspension(ClientboundSetEntityMotionPacket motion, PacketEvent.Receive event) {
        knockback = motion;
        suspending = true;
        suspendTicks = 0;
        event.cancel();
    }

    private void releaseNoXZ(boolean deferIncoming) {
        if (!suspending && knockback == null && outgoing.isEmpty() && incoming.isEmpty()) return;
        flushOutgoing();
        if (knockback != null) handleIncomingNow(knockback);
        knockback = null;
        suspending = false;
        suspendTicks = 0;
        if (deferIncoming) {
            scheduleIncomingReplay();
        } else {
            clearReplaySchedule();
            flushIncoming();
        }
        outgoing.clear();
    }

    private boolean shouldIgnore() {
        if (nullCheck()) return true;
        if (mc().player.isDeadOrDying() || !mc().player.isAlive() || mc().player.getHealth() <= 0.0f) return true;
        if (mc().player.isSpectator() || mc().player.getAbilities().flying) return true;
        if (mc().player.isInLava() || mc().player.isOnFire() || mc().player.isInWater()
                || mc().player.onClimbable() || mc().player.isSleeping()) return true;
        if (mc().level.getBlockState(mc().player.blockPosition()).is(Blocks.COBWEB)) return true;
        return tech.hakuri.graven.modules.impl.movement.Stuck.INSTANCE != null
                && tech.hakuri.graven.modules.impl.movement.Stuck.INSTANCE.isEnabled();
    }

    private boolean allowed(Packet<?> packet) {
        return packet instanceof ClientboundSetEntityMotionPacket
                || packet instanceof ClientboundSetHealthPacket
                || packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundPlayerRotationPacket
                || packet instanceof ClientboundSoundPacket
                || packet instanceof ClientboundPlayerChatPacket
                || packet instanceof ClientboundPlayerCombatKillPacket
                || packet instanceof ClientboundContainerClosePacket
                || packet instanceof ClientboundHurtAnimationPacket
                || packet instanceof ClientboundSetTitleTextPacket
                || packet instanceof ClientboundSetPlayerTeamPacket
                || packet instanceof ClientboundSystemChatPacket
                || packet instanceof ClientboundDisconnectPacket
                || packet instanceof ClientboundAnimatePacket animate && animate.getId() != mc().player.getId();
    }

    private void resetState() {
        clearReplaySchedule();
        clear();
        knockback = null;
        suspendTicks = 0;
        attackRemaining = 0;
        flagCooldown = 0;
        hitCounter = 0;
        sprintBoostCounter = 0;
        shouldJump = false;
        suspending = false;
        instantProgress = 0.0f;
        instantAttacking = false;
        attackTarget = null;
        if (Managers.OPENZEN_TICK_RATE != null) Managers.OPENZEN_TICK_RATE.reset();
    }

    private net.minecraft.client.Minecraft mc() {
        return net.minecraft.client.Minecraft.getInstance();
    }
}
