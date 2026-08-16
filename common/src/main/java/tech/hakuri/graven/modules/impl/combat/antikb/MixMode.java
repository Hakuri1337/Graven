package tech.hakuri.graven.modules.impl.combat.antikb;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import tech.hakuri.graven.events.impl.KeyboardInputEvent;
import tech.hakuri.graven.events.impl.PacketEvent;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.modules.impl.combat.AntiKB;
import tech.hakuri.graven.modules.impl.combat.KillAura;
import tech.hakuri.graven.modules.impl.movement.Scaffold;
import tech.hakuri.graven.utils.openzen.OpenZenInputGate;

/** OpenZen Mix 的受击方向、短暂挂起、双攻击和键位恢复时序。 */
public final class MixMode extends AntiKBMode {
    private boolean shouldAttack;
    private boolean wasSprinting;
    private boolean suspending;
    private int lastTickCount;
    private int webHitCount;
    private int airTicks;
    private int sprintTick = -1;
    private int movementState;
    private ClientboundSetEntityMotionPacket knockback;

    public MixMode(AntiKB owner) {
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
        resetState();
    }

    @Override
    public void receive(PacketEvent.Receive event) {
        if (isFlushing() || nullCheck()) return;
        if (lastTickCount < mc().player.tickCount) webHitCount = 0;
        Packet<?> packet = event.getPacket();

        boolean bypass = webHitCount > 0 && !mc().player.isInWater() && !mc().player.isUnderWater();
        if (bypass) {
            resetState();
            return;
        }

        if (packet instanceof ClientboundSetEntityMotionPacket motion) {
            if (motion.id() != mc().player.getId()) return;
            knockback = null;
            if (motion.movement().y > 0.0D) {
                sprintTick = 0;
                if (KillAura.INSTANCE.isEnabled() && KillAura.INSTANCE.target != null && owner.tryAttack.getValue()) {
                    shouldAttack = true;
                    wasSprinting = mc().player.isSprinting();
                }
                event.cancel();
                incoming.add(packet);
                suspending = true;
                knockback = motion;
                return;
            }
        }

        if (suspending && (packet instanceof ClientboundMoveEntityPacket
                || packet instanceof ClientboundPingPacket
                || packet instanceof ClientboundTeleportEntityPacket)) {
            incoming.add(packet);
            event.cancel();
            return;
        }
        if (suspending && packet instanceof ClientboundPlayerPositionPacket) resetState();
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
        if (lastTickCount < mc().player.tickCount) {
            webHitCount = isCobweb() ? webHitCount + 1 : 0;
            lastTickCount = mc().player.tickCount;
        }
        airTicks = mc().player.onGround() ? 0 : airTicks + 1;
        if (knockback != null && !owner.movementOverride.getValue() && !Scaffold.INSTANCE.isEnabled()
                && mc().player.hurtTime > 6 && !mc().options.keyJump.isDown()) {
            mc().options.keyJump.setDown(true);
        }
        if (suspending && (mc().player.onGround() || airTicks >= 24)) {
            release();
            suspending = false;
            shouldAttack = false;
            knockback = null;
            sprintTick = -1;
        }
    }

    @Override
    public void input(KeyboardInputEvent event) {
        if (knockback == null) return;

        if (shouldAttack && mc().player.hurtTime > 0 && KillAura.INSTANCE.target != null) {
            shouldAttack = false;
            for (int i = 0; i < 2; i++) attackTarget(KillAura.INSTANCE.target);
        }

        if (sprintTick >= 0) sprintTick++;
        if (owner.movementOverride.getValue()) {
            if (sprintTick >= 1 && sprintTick <= 3) {
                if (sprintTick <= 2 && mc().player.onGround()) event.setJump(true);
                applyKBDirection(event);
                movementState = 1;
            }
            if (sprintTick >= 4 && sprintTick <= 10) {
                if (movementState == 1) movementState = 0;
            }
            if (sprintTick >= 10) sprintTick = -1;
        }
        if (!owner.movementOverride.getValue()) {
            OpenZenInputGate.restore(mc().options.keyJump);
        }
    }

    private void applyKBDirection(KeyboardInputEvent event) {
        float dx = (float) knockback.movement().x;
        float dz = (float) knockback.movement().z;
        float kbYaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        float yawDelta = Mth.wrapDegrees(kbYaw - mc().player.getYRot());
        double yawRad = Math.toRadians(yawDelta);
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);
        event.setForward(cosYaw > 0.5 ? 1.0f : cosYaw < -0.5 ? -1.0f : 0.0f);
        event.setStrafe(sinYaw > 0.5 ? -1.0f : sinYaw < -0.5 ? 1.0f : 0.0f);
        event.setJump(false);
        event.setSprint(false);
    }

    private void attackTarget(LivingEntity target) {
        if (!target.isAlive() || mc().gameMode == null) return;
        boolean sprinting = mc().player.isSprinting();
        mc().player.setSprinting(false);
        mc().gameMode.attack(mc().player, target);
        mc().player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (sprinting && wasSprinting) {
            mc().player.setDeltaMovement(mc().player.getDeltaMovement().multiply(0.6, 1.0, 0.6));
        }
    }

    private boolean isCobweb() {
        return mc().level.getBlockState(mc().player.blockPosition()).is(Blocks.COBWEB);
    }

    private void resetState() {
        clearReplaySchedule();
        clear();
        shouldAttack = false;
        wasSprinting = false;
        suspending = false;
        lastTickCount = 0;
        webHitCount = 0;
        airTicks = 0;
        sprintTick = -1;
        movementState = 0;
        knockback = null;
        OpenZenInputGate.restoreAll();
    }

    private net.minecraft.client.Minecraft mc() {
        return net.minecraft.client.Minecraft.getInstance();
    }
}
