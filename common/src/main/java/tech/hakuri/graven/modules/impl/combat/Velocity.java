package tech.hakuri.graven.modules.impl.combat;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.*;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.SettingGroup;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.EnumSetting;
import tech.hakuri.graven.settings.impl.IntSetting;
import tech.hakuri.graven.utils.asaka.grimvelocity.FightManager;
import tech.hakuri.graven.utils.asaka.grimvelocity.ChatUtils;
import tech.hakuri.graven.utils.asaka.grimvelocity.EnchantmentUtils;
import tech.hakuri.graven.utils.asaka.grimvelocity.PlayerUtils;
import tech.hakuri.graven.utils.asaka.grimvelocity.TimerUtils;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.WindChargeItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Velocity extends Module {

    public static final Velocity INSTANCE = new Velocity();

    private Velocity() {
        super("Velocity", Category.COMBAT);
    }

    private enum Mode {
        Cancel,
        Legit,
        Grim,
        CubeCraft,
    }

    private enum GrimMode {
        PerTick,
        OneTime,
    }

    // ========== Existing Cancel Mode Settings ==========
    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Cancel);
    private final BoolSetting serverMotion = boolSetting("Server Motion", true, () -> mode.is(Mode.Cancel));
    private final BoolSetting explosion = boolSetting("Explosion", true, () -> mode.is(Mode.Cancel));
    private final BoolSetting explosionOnlyBlock = boolSetting("Explosion Only Block", false, () -> mode.is(Mode.Cancel) && explosion.getValue());
    public final BoolSetting waterPush = boolSetting("No Water Push", true, () -> mode.is(Mode.Cancel));
    public final BoolSetting entityPush = boolSetting("No Entity Push", true, () -> mode.is(Mode.Cancel));
    public final BoolSetting blockPush = boolSetting("No Block Push", true, () -> mode.is(Mode.Cancel));
    private final IntSetting cubeHorizontal = intSetting("Horizontal", 0, 0, 100, 1, () -> mode.is(Mode.CubeCraft));
    private final IntSetting cubeVertical = intSetting("Vertical", 0, 0, 100, 1, () -> mode.is(Mode.CubeCraft));

    private final SettingGroup sgExclusions = settingGroup("Exclusions");

    private final BoolSetting excludeSpearLunge = boolSetting("Exclude Spear Lunge", false, () -> mode.is(Mode.Cancel)).group(sgExclusions);
    private final BoolSetting excludeWindCharge = boolSetting("Exclude Wind Charge", false, () -> mode.is(Mode.Cancel)).group(sgExclusions);

    // ========== GrimReduce Settings ==========
    private final EnumSetting<GrimMode> grimMode = enumSetting("Grim Mode", GrimMode.PerTick, () -> mode.is(Mode.Grim));
    private final IntSetting grimAttacks = intSetting("Attacks", 2, 1, 5, 1, () -> mode.is(Mode.Grim));
    private final BoolSetting grimJumpReset = boolSetting("Jump Reset", true, () -> mode.is(Mode.Grim));
    private final IntSetting grimJumpTick = intSetting("Jump Tick", 9, 1, 10, 1, () -> mode.is(Mode.Grim) && grimJumpReset.getValue());
    private final BoolSetting grimLogging = boolSetting("Logging", false, () -> mode.is(Mode.Grim));

    // ========== Existing State ==========
    private final TimerUtils windChargeTimer = new TimerUtils();
    private boolean jump;

    // ========== Grim State ==========
    private final Queue<Packet<?>> grimPacketQueue = new ConcurrentLinkedQueue<>();
    private int grimAttackQueue = 0;
    private boolean grimAlink = false;
    private boolean grimLag = false;

    @Override
    protected void onEnable() {
        jump = false;
        windChargeTimer.reset();
        resetGrim();
    }

    @Override
    protected void onDisable() {
        jump = false;
        windChargeTimer.reset();
        clearGrim();
    }

    // ========== Packet Send (Existing only) ==========
    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        // wind charge exclusion
        if (excludeWindCharge.getValue() && event.getPacket() instanceof ServerboundUseItemPacket packet) {
            ItemStack stack = mc.player.getItemInHand(packet.getHand());
            if (stack.getItem() instanceof WindChargeItem) {
                windChargeTimer.reset();
            }
        }
    }

    // ========== Packet Receive ==========
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;

        switch (mode.getValue()) {
            case Cancel -> {
                if (nullCheck()) return;

                if (serverMotion.getValue() && event.getPacket() instanceof ClientboundSetEntityMotionPacket packet && packet.id() == mc.player.getId()) {
                    if (!shouldExcludeMotion(packet)) {
                        event.cancel();
                    }
                    return;
                }

                if (
                        explosion.getValue() && event.getPacket() instanceof ClientboundExplodePacket packet
                                && (!explosionOnlyBlock.getValue() || PlayerUtils.isInBlock())
                ) {
                    if (shouldExcludeExplosion(packet)) {
                        return;
                    }
                    event.setPacket(new ClientboundExplodePacket(
                            packet.center(),
                            packet.radius(),
                            packet.blockCount(),
                            Optional.empty(),
                            packet.explosionParticle(),
                            packet.explosionSound(),
                            packet.blockParticles()
                    ));
                }
            }
            case Legit -> {
                if (event.getPacket() instanceof ClientboundSetEntityMotionPacket packet && packet.id() == mc.player.getId()) {
                    jump = true;
                }
            }
            case Grim -> {
                Packet<?> packet = event.getPacket();

                if (packet instanceof ClientboundSetEntityMotionPacket velocityPacket) {
                    if (velocityPacket.id() != mc.player.getId()) return;

                    // If we're lagged (flag detected), ignore KB
                    if (grimLag) {
                        grimLag = false;
                        clearGrim();
                        return;
                    }

                    // If already in attack phase, queue additional KBs
                    if (grimAlink) {
                        grimPacketQueue.add(packet);
                        event.cancel();
                        grimLog("Additional KB queued");
                        return;
                    }

                    // First KB — check for valid target
                    HitResult hitResult = mc.hitResult;
                    if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
                        Entity entity = ((EntityHitResult) hitResult).getEntity();
                        if (entity instanceof Player) {
                            // Valid player target — enter attack phase
                            grimAttackQueue = grimAttacks.getValue();
                            grimAlink = true;
                            // Don't cancel! Let the KB through — velocity will be
                            // reduced by ×0.6 in onGrimTick
                            grimLog("KB received, attacks: " + grimAttackQueue);
                            return;
                        }
                    }

                    // No valid crosshair target — check if in air and no enemy within 2 blocks
                    if (!mc.player.onGround() && !hasEnemyWithin(2.0)) {
                        grimAlink = true;
                        grimLog("KB queued (air, no nearby enemy)");
                        // falls through to queue below
                    } else {
                        grimLog("KB ignored (on ground or enemy nearby)");
                        return;
                    }
                }

                // While in alink mode, queue both KB and Ping packets
                if (grimAlink) {
                    if (packet instanceof ClientboundSetEntityMotionPacket || packet instanceof ClientboundPingPacket) {
                        grimPacketQueue.add(packet);
                        event.cancel();
                        grimLog("Additional packet queued");
                        return;
                    }
                }

                // Flag detection — clear state
                if (packet instanceof ClientboundPlayerPositionPacket) {
                    grimLog("Flag detected, clearing queue");
                    clearGrim();
                    grimLag = true;
                }
            }
            case CubeCraft -> {
                if (event.getPacket() instanceof ClientboundSetEntityMotionPacket packet
                        && packet.id() == mc.player.getId()) {
                    if (cubeHorizontal.getValue() == 0 && cubeVertical.getValue() == 0) {
                        event.cancel();
                    } else {
                        Vec3 movement = packet.movement();
                        event.setPacket(new ClientboundSetEntityMotionPacket(
                                packet.id(),
                                new Vec3(
                                        movement.x * cubeHorizontal.getValue() / 100.0,
                                        movement.y * cubeVertical.getValue() / 100.0,
                                        movement.z * cubeHorizontal.getValue() / 100.0
                                )
                        ));
                    }
                }
            }
        }
    }

    // ========== Keyboard Input (Legit Jump only) ==========
    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (jump) {
            if (mc.player.onGround() && mc.player.isMoving()) {
                mc.player.input.makeJump();
            }
            jump = false;
        }
    }

    // ========== Grim Tick ==========
    @EventHandler
    private void onGrimTick(PlayerTickEvent.Pre event) {
        if (!mode.is(Mode.Grim) || nullCheck()) return;

        // Jump reset: force jump when hit by knockback
        if (grimJumpReset.getValue() && mc.player.tickCount == grimJumpTick.getValue()) {
            mc.player.input.makeJump();
        }

        // Process attack queue
        if (grimAttackQueue <= 0) {
            // No more attacks pending
            if (!grimPacketQueue.isEmpty()) {
                // If in air mode (no target), wait for landing to release
                if (!mc.player.onGround()) {
                    grimLog("Waiting for landing to release...");
                    return;
                }
                // Replay queued packets (from no-target case or additional KBs).
                // Then start new attack round so attacks reduce the replayed velocity.
                clearGrim();
                grimAttackQueue = grimAttacks.getValue();
                grimLog("New attack round: " + grimAttackQueue);
            } else if (grimAlink) {
                grimAlink = false;  // Reset alink when done
            }
            return;
        }

        // Check if we have a valid target
        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.ENTITY) {
            grimLog("No target for attack");
            return;
        }

        Entity entity = ((EntityHitResult) hitResult).getEntity();
        if (!(entity instanceof Player) || !entity.isAlive()) {
            grimLog("Invalid target");
            return;
        }

        // Attack
        if (grimMode.is(GrimMode.OneTime)) {
            // Attack all at once
            while (grimAttackQueue > 0) {
                if (!FightManager.hasAttackedThisTick() && FightManager.attackAndLock()) {
                    FightManager.attackByPacket(entity);
                    applyGrimSlow();
                    grimLog("OneTime attack: " + grimAttackQueue);
                }
                grimAttackQueue--;
            }
        } else {
            // PerTick: attack one per tick
            if (!FightManager.hasAttackedThisTick() && FightManager.attackAndLock()) {
                FightManager.attackByPacket(entity);
                applyGrimSlow();
                grimLog("PerTick attack: " + grimAttackQueue);
            }
            grimAttackQueue--;
        }
    }

    // ========== Game Left Cleanup ==========
    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        jump = false;
        windChargeTimer.reset();
        resetGrim();
    }

    // ========== Grim Helpers ==========

    private void grimLog(String message) {
        if (grimLogging.getValue()) {
            ChatUtils.addChatMessage("§7[Grim] §f" + message);
        }
    }

    private void applyGrimSlow() {
        Vec3 vel = mc.player.getDeltaMovement();
        if (Math.abs(vel.x) > 0.001 || Math.abs(vel.z) > 0.001) {
            mc.player.setDeltaMovement(vel.x * 0.6, vel.y, vel.z * 0.6);
        }
    }

    /**
     * Replays all queued packets and resets state (but not attackQueue).
     * onGrimTick handles setting attackQueue for the next attack round.
     */
    private void clearGrim() {
        while (!grimPacketQueue.isEmpty()) {
            try {
                Packet<?> packet = grimPacketQueue.poll();
                if (packet == null || mc.getConnection() == null) continue;
                @SuppressWarnings("unchecked")
                Packet<ClientPacketListener> p = (Packet<ClientPacketListener>) packet;
                p.handle(mc.getConnection());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        grimAlink = false;
        grimLog("Queue cleared, packets replayed");
    }

    private void resetGrim() {
        grimPacketQueue.clear();
        grimAlink = false;
        grimAttackQueue = 0;
        grimLag = false;
    }

    // ========== Existing Exclusion Methods ==========

    private boolean shouldExcludeMotion(ClientboundSetEntityMotionPacket packet) {
        return excludeSpearLunge.getValue() && isSpearLungeMotion(packet);
    }

    private boolean shouldExcludeExplosion(ClientboundExplodePacket packet) {
        return excludeWindCharge.getValue() && isWindChargeExplosion(packet);
    }

    private boolean isSpearLungeMotion(ClientboundSetEntityMotionPacket packet) {
        if (!isSpearWithLunge(mc.player.getMainHandItem())) return false;
        if (!mc.options.keyAttack.isDown()) return false;

        Vec3 vel = packet.movement();
        double horiz = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (horiz < 0.15) return false;

        Vec3 look = mc.player.getLookAngle();
        double dot = vel.x * look.x + vel.z * look.z;
        return dot > 0;
    }

    private boolean isWindChargeExplosion(ClientboundExplodePacket packet) {
        if (windChargeTimer.passedMillise(3000)) return false;

        double dist = packet.center().distanceTo(mc.player.position());
        if (dist > 12.0) return false;

        if (packet.radius() > 3.0f) return false;

        return packet.playerKnockback().isPresent();
    }

    private boolean isSpearWithLunge(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.has(DataComponents.PIERCING_WEAPON)
                && EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.LUNGE) > 0;
    }

    private boolean hasEnemyWithin(double range) {
        if (mc.level == null) return false;
        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive()) continue;
            if (mc.player.distanceTo(player) <= range) {
                return true;
            }
        }
        return false;
    }

}
