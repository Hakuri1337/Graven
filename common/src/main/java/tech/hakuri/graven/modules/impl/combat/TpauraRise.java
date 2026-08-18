package tech.hakuri.graven.modules.impl.combat;

import tech.hakuri.graven.events.bus.EventBus;
import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.AttackEntityEvent;
import tech.hakuri.graven.events.impl.GameLeftEvent;
import tech.hakuri.graven.events.impl.PacketEvent;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.events.impl.Render3DEvent;
import tech.hakuri.graven.events.impl.SendPositionEvent;
import tech.hakuri.graven.graphics.schedulers.render3d.Render3DScheduler;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.DoubleSetting;
import tech.hakuri.graven.settings.impl.EnumSetting;
import tech.hakuri.graven.settings.impl.IntSetting;
import tech.hakuri.graven.utils.network.PacketUtils;
import tech.hakuri.graven.utils.player.RemixEntityUtils;
import tech.hakuri.graven.utils.rotation.Rot2f;
import tech.hakuri.graven.utils.rotation.RotationUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class TpauraRise extends Module {

    public static final TpauraRise INSTANCE = new TpauraRise();

    private enum Mode { TeleportAura, Watchdog }
    private enum TargetsMode { Single, Multiple }
    private enum PathPhase { IDLE, OUTBOUND, RETURNING }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.TeleportAura);
    private final EnumSetting<TargetsMode> targets = enumSetting("Targets", TargetsMode.Single);
    private final DoubleSetting range = doubleSetting("Range", 32, 3, 100, 1);
    private final IntSetting minCps = intSetting("Min CPS", 10, 1, 20, 1);
    private final IntSetting maxCps = intSetting("Max CPS", 15, 1, 20, 1);
    private final BoolSetting cooldown19 = boolSetting("1.9 Cooldown", false);
    private final DoubleSetting stepSize = doubleSetting("Step Size", 1.0, 0.5, 4.0, 0.1);
    private final IntSetting packetsPerTick = intSetting("Packets/Tick", 5, 1, 20, 1);
    private final BoolSetting renderPath = boolSetting("Render Path", true);
    private final BoolSetting players = boolSetting("Players", true);
    private final BoolSetting hostile = boolSetting("Hostile", false);
    private final BoolSetting invisibles = boolSetting("Invisibles", true);
    private final BoolSetting teammates = boolSetting("Teammates", false);

    private final List<Vec3> path = new ArrayList<>();
    private final Deque<Packet<?>> heldPackets = new ArrayDeque<>();
    private LivingEntity target;
    private LivingEntity pendingAttackTarget;
    private LivingEntity attackEntity;
    private long lastClick;
    private long nextSwing;
    private boolean blinking;
    private boolean skipNextTick = true;
    private int blinkTicks;
    private PathPhase phase = PathPhase.IDLE;
    private int pathIndex;

    private TpauraRise() {
        super("TpauraRise", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        if (blinking) stopBlink();
        heldPackets.clear();
        resetPath();
    }

    @EventHandler
    private void onUpdate(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;
        if (mode.is(Mode.Watchdog)) onWatchdogUpdate();
        else onTeleportAuraUpdate();
    }

    @EventHandler(priority = 180)
    private void onMotion(SendPositionEvent event) {
        if (nullCheck()) return;
        if (mode.is(Mode.Watchdog)) {
            if (skipNextTick) {
                skipNextTick = false;
                return;
            }
            updateWatchdogTarget();
            if (target == null) return;
            if (blinking) {
                event.setX(target.getX());
                event.setY(target.getY());
                event.setZ(target.getZ());
                pendingAttackTarget = target;
                Rot2f rotations = RotationUtils.getRotationsToEntity(target);
                event.setYaw(rotations.getYaw());
                event.setPitch(rotations.getPitch());
            } else {
                event.setX(mc.player.getX());
                event.setY(mc.player.getY());
                event.setZ(mc.player.getZ());
            }
        } else if (phase != PathPhase.IDLE) {
            event.cancel();
        }
    }

    @EventHandler(priority = 200)
    private void onReceive(PacketEvent.Receive event) {
        if (!mode.is(Mode.Watchdog) || nullCheck()) return;
        if ((event.getPacket() instanceof ClientboundPlayerPositionPacket
                || event.getPacket() instanceof ClientboundPlayerRotationPacket) && !blinking) startBlink();
    }

    @EventHandler(priority = 200)
    private void onSend(PacketEvent.Send event) {
        if (!mode.is(Mode.Watchdog) || nullCheck() || !blinking || event.isCancelled()) return;
        heldPackets.add(event.getPacket());
        event.cancel();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (blinking) stopBlink();
        heldPackets.clear();
        resetPath();
    }

    @EventHandler(priority = -900)
    private void onRender3D(Render3DEvent event) {
        if (!mode.is(Mode.TeleportAura) || !renderPath.getValue() || path.isEmpty()) return;
        for (Vec3 point : path) {
            double pad = 0.05;
            Render3DScheduler.INSTANCE.addFilledBox(new AABB(
                    point.x - pad, point.y - pad, point.z - pad,
                    point.x + pad, point.y + 1.0 + pad, point.z + pad), new Color(0, 255, 255, 120));
        }
    }

    @EventHandler
    private void onRender2D(tech.hakuri.graven.events.impl.Render2DEvent.Level event) {
        if (!mode.is(Mode.Watchdog) || target == null || !blinking) return;
        String text = "Target: " + target.getName().getString() + " ["
                + (int) (target.getHealth() / target.getMaxHealth() * 100.0F) + "%]";
        int x = (mc.getWindow().getGuiScaledWidth() - mc.font.width(text)) / 2;
        int y = mc.getWindow().getGuiScaledHeight() / 2 + 20;
        event.getGuiGraphics().text(mc.font, Component.literal(text), x, y, 0xFFFFFFFF, true);
    }

    private void onTeleportAuraUpdate() {
        if (phase != PathPhase.IDLE) {
            tickPathPhase();
            return;
        }
        List<LivingEntity> livings = getTargets();
        if (livings.isEmpty()) {
            target = null;
            return;
        }
        livings.sort(Comparator.comparingDouble(mc.player::distanceTo));
        target = livings.getFirst();
        if (!mc.player.isDeadOrDying()) doAttack(livings);
    }

    private void doAttack(List<LivingEntity> livings) {
        boolean ready = cooldown19.getValue()
                ? mc.player.getAttackStrengthScale(0.5F) >= 1.0F
                : System.currentTimeMillis() - lastClick >= nextSwing;
        if (!ready || target == null || mc.options.keyAttack.isDown() || mc.options.keyUse.isDown()) return;
        if (!cooldown19.getValue()) {
            int low = Math.min(minCps.getValue(), maxCps.getValue());
            int high = Math.max(minCps.getValue(), maxCps.getValue());
            long cps = ThreadLocalRandom.current().nextLong(low, high + 1L);
            nextSwing = 1000L / Math.max(1L, cps);
        }
        if (targets.is(TargetsMode.Single)) {
            if (mc.player.distanceTo(target) <= range.getValue()) startAttack(target);
        } else {
            livings.removeIf(entity -> mc.player.distanceTo(entity) > range.getValue());
            for (LivingEntity living : livings) startAttack(living);
        }
        lastClick = System.currentTimeMillis();
    }

    private void startAttack(LivingEntity living) {
        path.clear();
        path.addAll(buildPath(mc.player.position(), living.position()));
        if (path.isEmpty()) return;
        attackEntity = living;
        pathIndex = 0;
        phase = PathPhase.OUTBOUND;
    }

    private void tickPathPhase() {
        int sent = 0;
        while (pathIndex < path.size() && sent < Math.max(1, packetsPerTick.getValue())) {
            Vec3 point = path.get(pathIndex++);
            PacketUtils.sendSilently(new ServerboundMovePlayerPacket.Pos(point, true, mc.player.horizontalCollision));
            PacketUtils.sendSilently(ServerboundClientTickEndPacket.INSTANCE);
            sent++;
        }
        if (pathIndex < path.size()) return;
        if (phase == PathPhase.OUTBOUND) {
            if (attackEntity != null) attack(attackEntity, true);
            Collections.reverse(path);
            pathIndex = 0;
            phase = PathPhase.RETURNING;
        } else resetPath();
    }

    private void onWatchdogUpdate() {
        if (pendingAttackTarget != null && blinking) {
            attack(pendingAttackTarget, false);
            pendingAttackTarget = null;
        }
        if (blinking && ++blinkTicks > 1) stopBlink();
    }

    private void attack(LivingEntity entity, boolean swing) {
        AttackEntityEvent event = EventBus.INSTANCE.post(new AttackEntityEvent(mc.player, entity));
        if (event.isCancelled()) return;
        PacketUtils.sendSilently(new ServerboundAttackPacket(entity.getId()));
        if (swing) mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void updateWatchdogTarget() {
        List<LivingEntity> list = getTargets();
        if (list.isEmpty()) {
            target = null;
            return;
        }
        list.sort(Comparator.comparingDouble(entity -> Math.abs(
                RotationUtils.getRotationsToEntity(entity).getYaw() - mc.player.getYRot())));
        target = list.getFirst();
    }

    private List<Vec3> buildPath(Vec3 from, Vec3 to) {
        List<Vec3> points = new ArrayList<>();
        double distance = from.distanceTo(to);
        if (distance < 0.05) return points;
        int steps = Math.max(1, (int) Math.ceil(distance / Math.max(0.5, stepSize.getValue())));
        for (int step = 1; step <= steps && points.size() < 200; step++) {
            points.add(from.lerp(to, step / (double) steps));
        }
        return points;
    }

    private List<LivingEntity> getTargets() {
        List<LivingEntity> list = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !RemixEntityUtils.isSelected(living)) continue;
            if (living instanceof Player player) {
                if (!players.getValue()) continue;
                if (!teammates.getValue() && tech.hakuri.graven.managers.Managers.FRIEND.isFriend(player)) continue;
            } else if (!hostile.getValue()) continue;
            if (!invisibles.getValue() && living.isInvisible()) continue;
            list.add(living);
        }
        return list;
    }

    private void startBlink() {
        blinking = true;
        blinkTicks = 0;
        heldPackets.clear();
    }

    private void stopBlink() {
        blinking = false;
        Packet<?> packet;
        while ((packet = heldPackets.poll()) != null) PacketUtils.sendSilently(packet);
        skipNextTick = false;
    }

    private void resetPath() {
        path.clear();
        pathIndex = 0;
        phase = PathPhase.IDLE;
        attackEntity = null;
    }

    private void resetState() {
        target = null;
        pendingAttackTarget = null;
        heldPackets.clear();
        blinking = false;
        skipNextTick = true;
        blinkTicks = 0;
        lastClick = 0L;
        nextSwing = 0L;
        resetPath();
    }
}
