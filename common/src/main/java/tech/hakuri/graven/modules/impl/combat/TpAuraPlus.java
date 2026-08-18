package tech.hakuri.graven.modules.impl.combat;

import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.ui.scene.UiLayer;
import com.github.slmpc.lumingraphics.ui.scene.UiScene;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.PacketEvent;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.events.impl.Render3DEvent;
import tech.hakuri.graven.events.impl.Render2DEvent;
import tech.hakuri.graven.events.impl.SendPositionEvent;
import tech.hakuri.graven.graphics.schedulers.render3d.Render3DScheduler;
import tech.hakuri.graven.gui.theme.GravenUiTheme;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.ColorSetting;
import tech.hakuri.graven.settings.impl.DoubleSetting;
import tech.hakuri.graven.settings.impl.EnumSetting;
import tech.hakuri.graven.settings.impl.IntSetting;
import tech.hakuri.graven.utils.network.PacketUtils;
import tech.hakuri.graven.utils.player.RemixEntityUtils;
import tech.hakuri.graven.utils.player.TeleportAuraUtils;
import tech.hakuri.graven.utils.render.WorldToScreen;
import tech.hakuri.graven.utils.rotation.Rot2f;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class TpAuraPlus extends Module {

    public static final TpAuraPlus INSTANCE = new TpAuraPlus();
    private static final int MAX_PATH_POINTS = 80;
    private static final long PROTECTION_WINDOW_MS = 2000L;
    private static final long PAUSE_MS = 1200L;
    private static final long VISUAL_FADE_MS = 1200L;

    private enum Phase { IDLE, OUTBOUND, AIM, HOLD, RETURNING, PAUSED }
    private enum PriorityMode { Distance, Health, LivingTime, Armor }

    private final EnumSetting<PriorityMode> priority = enumSetting("Priority", PriorityMode.Distance);
    private final DoubleSetting range = doubleSetting("Range", 12.0, 3.0, 64.0, 1.0);
    private final DoubleSetting stepSize = doubleSetting("Step Size", 1.0, 0.5, 8.0, 0.1);
    private final IntSetting packetsPerTick = intSetting("Packets/Tick", 2, 1, 20, 1);
    private final DoubleSetting vClip = doubleSetting("VClip", 8.0, 2.0, 64.0, 1.0);
    private final DoubleSetting prev = doubleSetting("Prev", 0.0, 0.0, 5.0, 0.1);
    private final BoolSetting safeTargetFallback = boolSetting("Safe Target Fallback", true);
    private final BoolSetting randomOffset = boolSetting("Random Offset", true);
    private final DoubleSetting offsetXZ = doubleSetting("Offset XZ", 0.05, 0.0, 0.3, 0.01, randomOffset::getValue);
    private final DoubleSetting offsetY = doubleSetting("Offset Y", 0.01, 0.0, 0.2, 0.01, randomOffset::getValue);
    private final BoolSetting rotate = boolSetting("Rotate", true);
    private final BoolSetting swing = boolSetting("Swing", true);
    private final BoolSetting useMace = boolSetting("Use Mace", false);
    private final BoolSetting useCooldown = boolSetting("Use Cooldown", true);
    private final DoubleSetting cooldownBase = doubleSetting("Cooldown Base", 0.75, 0.1, 1.0, 0.05, useCooldown::getValue);
    private final IntSetting attackDelay = intSetting("Attack Delay", 50, 1, 2000, 25, () -> !useCooldown.getValue());
    private final IntSetting attackTimes = intSetting("Attack Times", 1, 1, 50, 1);
    private final IntSetting preAttackDelay = intSetting("Pre-Attack Delay", 1, 0, 10, 1);
    private final IntSetting returnDelay = intSetting("Return Delay Ticks", 10, 0, 100, 1);
    private final BoolSetting tryMissTotem = boolSetting("Try Miss Totem", false);
    private final BoolSetting lagbackDisable = boolSetting("Disable on Lagback", false);
    private final BoolSetting antiCorrection = boolSetting("Anti Teleport", true);
    private final BoolSetting renderTrail = boolSetting("Render Trail", true);
    private final ColorSetting renderTrailColor = colorSetting("Render Trail Color", Color.CYAN, renderTrail::getValue);

    private final List<Vec3> path = new ArrayList<>();
    private final List<VisualPoint> visualPoints = new ArrayList<>();
    private final Deque<Packet<?>> pendingPackets = new ArrayDeque<>();
    private Phase phase = Phase.IDLE;
    private int pathIndex;
    private int preAttackTicksLeft;
    private int holdTicksLeft;
    private long lastAttack;
    private long pauseUntil;
    private long lastCycleEndTime;
    private boolean recovering;
    private Vec3 attackPosition;
    private LivingEntity target;
    private LivingEntity chaseTarget;
    private LivingEntity attackEntity;
    private UiScene highlightScene;
    private MinecraftUiRuntime2612 highlightRuntime;

    private TpAuraPlus() {
        super("TpAuraPlus", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        resetState();
        releaseHighlightScene();
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;
        if (KillAura.INSTANCE.isEnabled() && KillAura.INSTANCE.target != null) {
            resetCycle();
            chaseTarget = KillAura.INSTANCE.target;
            target = null;
            return;
        }
        if (phase != Phase.IDLE && !isAliveAttackEntity(attackEntity)) resetCycle();
        updateTarget();
        if (phase == Phase.PAUSED) {
            if (System.currentTimeMillis() >= pauseUntil) phase = Phase.IDLE;
            return;
        }
        switch (phase) {
            case IDLE -> startCycle();
            case OUTBOUND -> tickOutbound();
            case AIM -> tickAim();
            case HOLD -> tickHold();
            case RETURNING -> tickReturning();
            default -> { }
        }
    }

    @EventHandler
    private void onMotion(SendPositionEvent event) {
        if (phase != Phase.IDLE && phase != Phase.PAUSED) event.cancel();
    }

    @EventHandler(priority = 180)
    private void onReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;
        if (event.getPacket() instanceof ClientboundEntityEventPacket status && status.getEventId() == 35
                && tryMissTotem.getValue() && status.getEntity(mc.level) == target) {
            if (phase == Phase.IDLE) {
                attackPosition = TeleportAuraUtils.predictedPosition(target, 0.0);
                attackEntity = target;
                path.clear();
                path.addAll(TeleportAuraUtils.buildDirectPath(stepSize.getValue(), MAX_PATH_POINTS,
                        mc.player.position(), attackPosition));
                pathIndex = 0;
                phase = Phase.OUTBOUND;
            }
            return;
        }
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket position) {
            handleCorrection(event, position);
        } else if (event.getPacket() instanceof ClientboundPlayerRotationPacket) {
            handleLagback();
        }
    }

    @EventHandler(priority = -900)
    private void onRender(Render3DEvent event) {
        pruneVisuals();
        if (!renderTrail.getValue()) return;
        for (int i = 1; i < visualPoints.size(); i++) {
            VisualPoint previous = visualPoints.get(i - 1);
            VisualPoint point = visualPoints.get(i);
            float alpha = Math.min(alpha(previous.time), alpha(point.time));
            Color base = renderTrailColor.getValue();
            Color color = new Color(base.getRed(), base.getGreen(), base.getBlue(),
                    Math.clamp(Math.round(255.0F * alpha), 0, 255));
            Render3DScheduler.INSTANCE.addLine(previous.pos, point.pos, color, 2.0F);
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent.Level event) {
        if (nullCheck() || target == null) return;
        float[] bounds = projectBounds(target.getBoundingBox());
        if (bounds == null) return;
        UiTree tree = UiTree.build(scope -> drawOutline(scope, bounds[0] - 2.0F, bounds[1] - 2.0F,
                bounds[2] + 2.0F, bounds[3] + 2.0F, 2.5F, Color.ORANGE));
        MinecraftUiRuntime2612 runtime = MinecraftUiRuntime2612.current();
        runtime.render(highlightScene(runtime), UiLayer.CONTENT, tree);
    }

    private void startCycle() {
        if (target == null || !ready() || mc.player.distanceTo(target) > range.getValue()) return;
        if (mc.player.distanceTo(target) <= 6.0) {
            attackEntity = target;
            attackNow();
            flushPending();
            lastAttack = System.currentTimeMillis();
            return;
        }

        attackEntity = target;
        Vec3 desired = TeleportAuraUtils.predictedPosition(target, prev.getValue());
        attackPosition = findAttackPosition(desired, target);
        if (attackPosition == null) return;
        path.clear();
        path.addAll(TeleportAuraUtils.buildOutboundPath(mc.player.position(), attackPosition,
                stepSize.getValue(), vClip.getValue(), MAX_PATH_POINTS));
        if (path.isEmpty() && safeTargetFallback.getValue()) {
            path.addAll(TeleportAuraUtils.buildOutboundPath(mc.player.position(),
                    desired.add(0.0, vClip.getValue(), 0.0), stepSize.getValue(), vClip.getValue(), MAX_PATH_POINTS));
        }
        if (path.isEmpty()) return;
        pathIndex = 0;
        phase = Phase.OUTBOUND;
    }

    private Vec3 findAttackPosition(Vec3 desired, LivingEntity entity) {
        if (isValidAttackPosition(desired, entity)) return desired;
        List<Vec3> candidates = new ArrayList<>();
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Vec3 candidate = desired.add(dx, dy, dz);
                    if (isValidAttackPosition(candidate, entity)) candidates.add(candidate);
                }
            }
        }
        return candidates.stream().min(Comparator.comparingDouble(candidate -> candidate.distanceTo(desired))).orElse(null);
    }

    private boolean isValidAttackPosition(Vec3 candidate, LivingEntity entity) {
        if (TeleportAuraUtils.distanceToBox(candidate, entity.getBoundingBox()) > 3.5) return false;
        return mc.level.noCollision(mc.player,
                mc.player.getBoundingBox().move(candidate.subtract(mc.player.position())));
    }

    private void tickOutbound() {
        flushPending();
        int sent = 0;
        while (pathIndex < path.size() && sent++ < packetsPerTick.getValue()) {
            Vec3 point = path.get(pathIndex++);
            Rot2f rotations = rotate.getValue() && pathIndex == path.size()
                    ? TeleportAuraUtils.rotations(point, attackEntity) : null;
            queueMove(point, rotations);
            recordPoint(point);
        }
        if (pathIndex < path.size() && pathIndex > 0) {
            Vec3 lastSent = path.get(pathIndex - 1);
            if (!TeleportAuraUtils.isOnGroundAt(lastSent)) {
                pendingPackets.add(new ServerboundMovePlayerPacket.Pos(lastSent, true, mc.player.horizontalCollision));
            }
        }
        if (pathIndex >= path.size()) {
            preAttackTicksLeft = preAttackDelay.getValue();
            if (preAttackTicksLeft > 0) phase = Phase.AIM;
            else beginHold();
        }
    }

    private void tickAim() {
        flushPending();
        if (--preAttackTicksLeft <= 0) beginHold();
    }

    private void beginHold() {
        attackNow();
        holdTicksLeft = returnDelay.getValue();
        if (holdTicksLeft > 0) phase = Phase.HOLD;
        else buildReturnPath();
    }

    private void tickHold() {
        flushPending();
        pinPosition();
        if (--holdTicksLeft <= 0) buildReturnPath();
    }

    private void buildReturnPath() {
        if (attackPosition == null) {
            resetCycle();
            return;
        }
        path.clear();
        path.addAll(TeleportAuraUtils.buildOutboundPath(attackPosition, mc.player.position(),
                stepSize.getValue(), vClip.getValue(), MAX_PATH_POINTS));
        pathIndex = 0;
        phase = Phase.RETURNING;
    }

    private void tickReturning() {
        flushPending();
        int sent = 0;
        while (pathIndex < path.size() && sent++ < packetsPerTick.getValue()) {
            Vec3 point = path.get(pathIndex++);
            queueMove(point, null);
            recordPoint(point);
        }
        if (pathIndex < path.size()) return;
        flushPending();
        if (recovering) {
            recovering = false;
            resetCycle();
            phase = Phase.PAUSED;
            pauseUntil = System.currentTimeMillis() + PAUSE_MS;
        } else {
            lastCycleEndTime = System.currentTimeMillis();
            resetCycle();
            lastAttack = System.currentTimeMillis();
        }
    }

    private void attackNow() {
        if (attackEntity == null) {
            resetCycle();
            return;
        }
        if (useMace.getValue() && !(mc.player.getMainHandItem().getItem() instanceof net.minecraft.world.item.MaceItem)) {
            resetCycle();
            return;
        }
        for (int i = 0; i < attackTimes.getValue(); i++) pendingPackets.add(new ServerboundAttackPacket(attackEntity.getId()));
        if (swing.getValue()) mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void queueMove(Vec3 point, Rot2f rotations) {
        boolean onGround = TeleportAuraUtils.isOnGroundAt(point);
        if (rotations == null) pendingPackets.add(new ServerboundMovePlayerPacket.Pos(point, onGround, mc.player.horizontalCollision));
        else pendingPackets.add(new ServerboundMovePlayerPacket.PosRot(point, rotations.getYaw(), rotations.getPitch(), onGround, mc.player.horizontalCollision));
    }

    private void flushPending() {
        Packet<?> packet;
        while ((packet = pendingPackets.poll()) != null) PacketUtils.sendSilently(packet);
    }

    private void pinPosition() {
        if (attackPosition != null) queueMove(attackPosition, null);
    }

    private void handleCorrection(PacketEvent.Receive event, ClientboundPlayerPositionPacket packet) {
        boolean inCycle = phase != Phase.IDLE;
        boolean protectedWindow = System.currentTimeMillis() - lastCycleEndTime < PROTECTION_WINDOW_MS;
        if (!antiCorrection.getValue() || (!inCycle && !protectedWindow)) {
            handleLagback();
            return;
        }
        event.cancel();
        PacketUtils.sendSilently(new ServerboundAcceptTeleportationPacket(packet.id()));
        if (!inCycle) {
            resetCycle();
            phase = Phase.PAUSED;
            pauseUntil = System.currentTimeMillis() + PAUSE_MS;
            return;
        }
        PositionMoveRotation absolute = PositionMoveRotation.calculateAbsolute(
                PositionMoveRotation.of(mc.player), packet.change(), packet.relatives());
        Vec3 corrected = absolute.position();
        Vec3 real = mc.player.position();
        pendingPackets.clear();
        resetCycle();
        if (corrected.distanceToSqr(real) > 1.0) {
            attackPosition = corrected;
            path.addAll(TeleportAuraUtils.buildDirectPath(stepSize.getValue(), MAX_PATH_POINTS, corrected, real));
            pathIndex = 0;
            recovering = true;
            phase = Phase.RETURNING;
        } else {
            phase = Phase.PAUSED;
            pauseUntil = System.currentTimeMillis() + PAUSE_MS;
        }
    }

    private void handleLagback() {
        if (phase == Phase.IDLE) return;
        resetCycle();
        if (lagbackDisable.getValue()) setEnabled(false);
        else {
            phase = Phase.PAUSED;
            pauseUntil = System.currentTimeMillis() + PAUSE_MS;
        }
    }

    private void updateTarget() {
        if (isValid(chaseTarget)) {
            target = chaseTarget;
            return;
        }
        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && isValid(living)) candidates.add(living);
        }
        target = candidates.stream().min(comparator()).orElse(null);
        if (target != null) chaseTarget = target;
    }

    private boolean isValid(LivingEntity entity) {
        return entity != null && entity.isAlive() && RemixEntityUtils.isSelected(entity)
                && mc.player.distanceTo(entity) <= range.getValue();
    }

    private boolean isAliveAttackEntity(LivingEntity entity) {
        return entity != null && entity.isAlive() && entity.getHealth() > 0.0F;
    }

    private Comparator<LivingEntity> comparator() {
        return switch (priority.getValue()) {
            case Health -> Comparator.comparingDouble(entity -> entity.getHealth() + entity.getAbsorptionAmount());
            case LivingTime -> Comparator.comparingInt((LivingEntity entity) -> entity.tickCount).reversed();
            case Armor -> Comparator.comparingInt(LivingEntity::getArmorValue);
            default -> Comparator.comparingDouble(mc.player::distanceTo);
        };
    }

    private boolean ready() {
        return useCooldown.getValue() ? mc.player.getAttackStrengthScale(-1.0F) >= cooldownBase.getValue()
                : System.currentTimeMillis() - lastAttack >= attackDelay.getValue();
    }

    private void resetCycle() {
        phase = Phase.IDLE;
        path.clear();
        pendingPackets.clear();
        pathIndex = 0;
        holdTicksLeft = 0;
        preAttackTicksLeft = 0;
        attackPosition = null;
        attackEntity = null;
        recovering = false;
    }

    private void resetState() {
        resetCycle();
        target = null;
        chaseTarget = null;
        visualPoints.clear();
        lastAttack = 0L;
        pauseUntil = 0L;
        // Remix 不重置 lastCycleEndTime，保留跨开关的 2 秒保护窗口。
    }

    private void recordPoint(Vec3 pos) {
        if (visualPoints.size() >= 10) visualPoints.removeFirst();
        visualPoints.add(new VisualPoint(pos, System.currentTimeMillis()));
    }

    private void pruneVisuals() {
        long now = System.currentTimeMillis();
        Iterator<VisualPoint> iterator = visualPoints.iterator();
        while (iterator.hasNext()) if (now - iterator.next().time > VISUAL_FADE_MS) iterator.remove();
    }

    private float alpha(long time) {
        float progress = Math.max(0.0F, 1.0F - (System.currentTimeMillis() - time) / (float) VISUAL_FADE_MS);
        return progress * progress;
    }

    private float[] projectBounds(net.minecraft.world.phys.AABB box) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < 8; vertex++) {
            Vector3f projected = WorldToScreen.calcWorld2Screen(new Vec3((vertex & 1) == 0 ? box.minX : box.maxX,
                    (vertex & 2) == 0 ? box.minY : box.maxY, (vertex & 4) == 0 ? box.minZ : box.maxZ));
            if (projected == null) return null;
            minX = Math.min(minX, projected.x);
            minY = Math.min(minY, projected.y);
            maxX = Math.max(maxX, projected.x);
            maxY = Math.max(maxY, projected.y);
        }
        return maxX > minX && maxY > minY ? new float[]{minX, minY, maxX, maxY} : null;
    }

    private void drawOutline(UiTree.Scope scope, float x, float y, float endX, float endY,
                             float thickness, Color color) {
        scope.rect(x, y, endX - x, thickness, color);
        scope.rect(x, endY - thickness, endX - x, thickness, color);
        scope.rect(x, y + thickness, thickness, endY - y - thickness * 2.0F, color);
        scope.rect(endX - thickness, y + thickness, thickness, endY - y - thickness * 2.0F, color);
    }

    private UiScene highlightScene(MinecraftUiRuntime2612 runtime) {
        if (highlightScene == null || highlightRuntime != runtime) {
            releaseHighlightScene();
            highlightScene = runtime.createScene(GravenUiTheme.lumin());
            highlightRuntime = runtime;
        }
        return highlightScene;
    }

    private void releaseHighlightScene() {
        UiScene previous = highlightScene;
        highlightScene = null;
        highlightRuntime = null;
        if (previous != null) previous.close();
    }

    private record VisualPoint(Vec3 pos, long time) { }
}
