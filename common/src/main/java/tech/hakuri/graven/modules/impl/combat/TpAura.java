package tech.hakuri.graven.modules.impl.combat;

import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.ui.scene.UiLayer;
import com.github.slmpc.lumingraphics.ui.scene.UiScene;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.PacketEvent;
import tech.hakuri.graven.events.impl.PlayerTickEvent;
import tech.hakuri.graven.events.impl.Render2DEvent;
import tech.hakuri.graven.events.impl.Render3DEvent;
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
import tech.hakuri.graven.utils.player.RemixEntityUtils;
import tech.hakuri.graven.utils.player.TeleportAuraUtils;
import tech.hakuri.graven.utils.render.WorldToScreen;
import tech.hakuri.graven.utils.rotation.Rot2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public final class TpAura extends Module {

    public static final TpAura INSTANCE = new TpAura();
    private static final long VISUAL_FADE_MS = 1200L;
    private static final long LAGBACK_PAUSE_MS = 1200L;
    private static final int MAX_PATH_POINTS = 80;

    private enum Phase { IDLE, OUTBOUND, AIM, HOLD, RETURNING, PAUSED }
    private enum Mode { Cubecraft, Vanilla, Paper }
    private enum PriorityMode { Distance, Health, LivingTime, Armor }
    private enum Highlight { Off, TwoD, Glow }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Cubecraft);
    private final EnumSetting<PriorityMode> priority = enumSetting("Priority", PriorityMode.Distance);
    private final DoubleSetting range = doubleSetting("Range", 20.0, 3.0, 128.0, 1.0);
    private final DoubleSetting stepSize = doubleSetting("Step Size", 1.0, 0.5, 8.0, 0.1);
    private final IntSetting packetsPerTick = intSetting("Packets/Tick", 2, 1, 20, 1);
    private final DoubleSetting vClip = doubleSetting("VClip", 8.0, 2.0, 64.0, 1.0);
    private final DoubleSetting prev = doubleSetting("Prev", 0.0, 0.0, 5.0, 0.1);
    private final BoolSetting prewarmPackets = boolSetting("Prewarm Packets", false);
    private final IntSetting prewarmPacketCount = intSetting("Prewarm Count", 4, 1, 20, 1, prewarmPackets::getValue);
    private final BoolSetting randomOffset = boolSetting("Random Offset", true);
    private final DoubleSetting offsetXZ = doubleSetting("Offset XZ", 0.05, 0.0, 0.3, 0.01, randomOffset::getValue);
    private final DoubleSetting offsetY = doubleSetting("Offset Y", 0.01, 0.0, 0.2, 0.01, randomOffset::getValue);
    private final BoolSetting safeTargetFallback = boolSetting("Safe Target Fallback", true);
    private final BoolSetting rotate = boolSetting("Rotate", true);
    private final BoolSetting swing = boolSetting("Swing", true);
    private final BoolSetting fakeAutoBlock = boolSetting("Fake AutoBlock", false);
    private final BoolSetting useMace = boolSetting("Use Mace", false);
    private final BoolSetting useCooldown = boolSetting("Use Cooldown", true);
    private final DoubleSetting cooldownBase = doubleSetting("Cooldown Base", 0.75, 0.1, 1.0, 0.05, useCooldown::getValue);
    private final IntSetting attackDelay = intSetting("Attack Delay", 50, 1, 2000, 25, () -> !useCooldown.getValue());
    private final IntSetting attackTimes = intSetting("Attack Times", 1, 1, 50, 1);
    private final IntSetting preAttackDelay = intSetting("Pre-Attack Delay", 2, 0, 10, 1);
    private final IntSetting returnDelay = intSetting("Return Delay Ticks", 15, 0, 100, 1);
    private final BoolSetting tryMissTotem = boolSetting("Try Miss Totem", false);
    private final BoolSetting cancelPingPackets = boolSetting("Cancel Ping Packets", false);
    private final BoolSetting lagbackDisable = boolSetting("Disable on Lagback", false);
    private final DoubleSetting renderPosScale = doubleSetting("Render Pos Scale", 0.6, 0.2, 1.0, 0.01);
    private final EnumSetting<Highlight> highlightTarget = enumSetting("Highlight Target", Highlight.TwoD);
    private final BoolSetting renderPos = boolSetting("Render Pos", true);
    private final ColorSetting renderPosColor = colorSetting("Render Pos Color", new Color(255, 165, 0), renderPos::getValue);
    private final BoolSetting renderTrail = boolSetting("Render Trail", true);
    private final ColorSetting renderTrailColor = colorSetting("Render Trail Color", Color.PINK, renderTrail::getValue);

    private final List<VisualPoint> visualPoints = new ArrayList<>();
    private final List<Vec3> path = new ArrayList<>();
    private Phase phase = Phase.IDLE;
    private int pathIndex;
    private int holdTicksLeft;
    private int preAttackTicksLeft;
    private long lastAttack;
    private long pauseUntil;
    private Vec3 originalPos;
    private Vec3 attackPos;
    private Vec3 returnTo;
    private LivingEntity target;
    private LivingEntity chaseTarget;
    private LivingEntity attackEntity;
    private boolean renderBlock;
    private UiScene highlightScene;
    private MinecraftUiRuntime2612 highlightRuntime;

    private TpAura() {
        super("TpAura", Category.COMBAT);
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

    public boolean isRenderBlock() {
        return isEnabled() && renderBlock;
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;
        KillAura aura = KillAura.INSTANCE;
        if (aura.isEnabled() && aura.target != null) {
            resetCycle();
            chaseTarget = aura.target;
            target = null;
            renderBlock = false;
            return;
        }
        if (phase != Phase.IDLE && !isAliveAttackEntity(attackEntity)) resetCycle();
        updateTarget();
        renderBlock = fakeAutoBlock.getValue() && target != null && mc.player.getMainHandItem().is(ItemTags.SWORDS);
        switch (phase) {
            case IDLE -> startCycle();
            case OUTBOUND -> tickOutbound();
            case AIM -> tickAim();
            case HOLD -> tickHold();
            case RETURNING -> tickReturning();
            case PAUSED -> {
                if (System.currentTimeMillis() >= pauseUntil) phase = Phase.IDLE;
            }
        }
    }

    @EventHandler
    private void onMotion(SendPositionEvent event) {
        if (phase == Phase.OUTBOUND || phase == Phase.AIM || phase == Phase.HOLD || phase == Phase.RETURNING) event.cancel();
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (cancelPingPackets.getValue() && (event.getPacket() instanceof ServerboundPlayerAbilitiesPacket
                || event.getPacket() instanceof ServerboundPongPacket)) event.cancel();
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;
        if (event.getPacket() instanceof ClientboundEntityEventPacket status && tryMissTotem.getValue()
                && status.getEventId() == 35 && target != null && status.getEntity(mc.level) == target) {
            if (phase == Phase.IDLE) {
                originalPos = mc.player.position();
                attackPos = TeleportAuraUtils.predictedPosition(target, 0.0);
                attackEntity = target;
                path.clear();
                path.addAll(TeleportAuraUtils.buildDirectPath(stepSize.getValue(), MAX_PATH_POINTS, originalPos, attackPos));
                pathIndex = 0;
                phase = Phase.OUTBOUND;
            }
            return;
        }
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket
                || event.getPacket() instanceof ClientboundPlayerRotationPacket) handleLagback();
    }

    @EventHandler(priority = -900)
    private void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;
        pruneVisuals();
        renderVisuals();
    }

    @EventHandler
    private void onRender2D(Render2DEvent.Level event) {
        if (nullCheck() || !highlightTarget.is(Highlight.TwoD) || target == null) return;
        float[] bounds = projectBounds(target.getBoundingBox());
        if (bounds == null) return;
        UiTree tree = UiTree.build(scope -> drawOutline(scope, bounds[0] - 2.0f, bounds[1] - 2.0f,
                bounds[2] + 2.0f, bounds[3] + 2.0f, 2.5f, Color.RED));
        MinecraftUiRuntime2612 runtime = MinecraftUiRuntime2612.current();
        runtime.render(highlightScene(runtime), UiLayer.CONTENT, tree);
    }

    private void startCycle() {
        if (target == null || !ready() || mc.player.distanceTo(target) > range.getValue()) return;
        if (mc.player.distanceTo(target) <= 3.0) {
            attackEntity = target;
            attackNow();
            lastAttack = System.currentTimeMillis();
            return;
        }
        Vec3 playerPos = mc.player.position();
        Vec3 targetPos = TeleportAuraUtils.predictedPosition(target, prev.getValue());
        Vec3 attack = safeTargetFallback.getValue() ? findVisibleAttackPos(targetPos) : targetPos;
        if (attack == null) return;
        originalPos = playerPos;
        attackPos = attack;
        attackEntity = target;
        path.clear();
        path.addAll(TeleportAuraUtils.buildOutboundPath(playerPos, attack, stepSize.getValue(), vClip.getValue(), MAX_PATH_POINTS));
        if (path.isEmpty()) return;
        pathIndex = 0;
        phase = Phase.OUTBOUND;
        sendPrewarmPackets();
    }

    private void tickOutbound() {
        int sent = 0;
        Vec3 lastSent = null;
        while (pathIndex < path.size() && sent < Math.max(1, packetsPerTick.getValue())) {
            Vec3 point = path.get(pathIndex);
            sendMove(point, pathIndex == path.size() - 1 ? attackRotations() : null, TeleportAuraUtils.isOnGroundAt(point));
            recordPoint(point);
            pathIndex++;
            sent++;
            lastSent = point;
        }
        if (pathIndex < path.size() && lastSent != null && !TeleportAuraUtils.isOnGroundAt(lastSent)) sendMove(lastSent, null, true);
        if (pathIndex < path.size()) return;
        preAttackTicksLeft = Math.max(0, preAttackDelay.getValue());
        if (preAttackTicksLeft > 0) phase = Phase.AIM;
        else {
            attackNow();
            beginReturnDelay();
        }
    }

    private void tickAim() {
        pinPosition();
        if (preAttackTicksLeft > 0) preAttackTicksLeft--;
        if (preAttackTicksLeft <= 0) {
            attackNow();
            beginReturnDelay();
        }
    }

    private void beginReturnDelay() {
        holdTicksLeft = returnDelay.getValue();
        phase = holdTicksLeft > 0 ? Phase.HOLD : Phase.RETURNING;
        if (phase == Phase.RETURNING) buildReturnPath();
    }

    private void tickHold() {
        pinPosition();
        if (holdTicksLeft > 0) holdTicksLeft--;
        if (holdTicksLeft <= 0) {
            buildReturnPath();
            phase = Phase.RETURNING;
        }
    }

    private void tickReturning() {
        int sent = 0;
        while (pathIndex < path.size() && sent < Math.max(1, packetsPerTick.getValue())) {
            Vec3 point = path.get(pathIndex++);
            sendMove(point, null, TeleportAuraUtils.isOnGroundAt(point));
            recordPoint(point);
            sent++;
        }
        if (pathIndex >= path.size()) {
            syncClientPosition(returnTo != null ? returnTo : originalPos);
            resetCycle();
            lastAttack = System.currentTimeMillis();
        }
    }

    private void buildReturnPath() {
        if (originalPos == null || attackPos == null) {
            resetCycle();
            return;
        }
        returnTo = mc.player.position();
        path.clear();
        path.addAll(TeleportAuraUtils.buildOutboundPath(attackPos, returnTo, stepSize.getValue(), vClip.getValue(), MAX_PATH_POINTS));
        pathIndex = 0;
    }

    private void attackNow() {
        if (attackEntity == null) {
            resetCycle();
            return;
        }
        if (useMace.getValue() && !(mc.player.getMainHandItem().getItem() instanceof MaceItem)) {
            resetCycle();
            return;
        }
        int times = mode.is(Mode.Cubecraft) ? 1 : Math.max(1, attackTimes.getValue());
        for (int i = 0; i < times; i++) mc.getConnection().send(new ServerboundAttackPacket(attackEntity.getId()));
        if (swing.getValue()) mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private Vec3 findVisibleAttackPos(Vec3 desired) {
        Vec3 body = desired.add(0.0, 1.0, 0.0);
        if (TeleportAuraUtils.hasLineOfSight(desired.add(0.0, 1.0, 0.0), body)) return desired;
        for (int dy = 0; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            Vec3 test = desired.add(dx, dy, dz);
            if (test.distanceToSqr(desired) > 9.0) continue;
            if (!TeleportAuraUtils.isInvalidPosition(test)
                    && TeleportAuraUtils.hasLineOfSight(test.add(0.0, 1.0, 0.0), body)) return test;
        }
        return safeTargetFallback.getValue() ? TeleportAuraUtils.findNearestSafePosition(desired) : desired;
    }

    private void sendMove(Vec3 pos, Rot2f rotation, boolean onGround) {
        if (rotation == null) mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(pos, onGround, mc.player.horizontalCollision));
        else mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(pos, rotation.getYaw(), rotation.getPitch(), onGround, mc.player.horizontalCollision));
    }

    private Rot2f attackRotations() {
        return rotate.getValue() && attackPos != null && attackEntity != null ? TeleportAuraUtils.rotations(attackPos, attackEntity) : null;
    }

    private void pinPosition() {
        if (attackPos != null) sendMove(attackPos, null, true);
    }

    private void sendPrewarmPackets() {
        if (!prewarmPackets.getValue() || mode.is(Mode.Cubecraft)) return;
        int count = prewarmPacketCount.getValue();
        if (mode.is(Mode.Vanilla)) count = Math.min(count, 4);
        for (int i = 0; i < count; i++) mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(false, mc.player.horizontalCollision));
    }

    private void syncClientPosition(Vec3 base) {
        if (base == null) return;
        Vec3 syncPos = randomOffset.getValue() ? randomOffset(base) : base;
        if (!syncPos.equals(base)) {
            sendMove(syncPos, null, TeleportAuraUtils.isOnGroundAt(syncPos));
            recordPoint(syncPos);
        }
        mc.player.setPos(syncPos);
    }

    private Vec3 randomOffset(Vec3 base) {
        double xz = offsetXZ.getValue();
        double y = offsetY.getValue();
        if (xz <= 0.0 && y <= 0.0) return base;
        List<Vec3> offsets = new ArrayList<>(List.of(
                base.add(xz, y, 0.0), base.add(-xz, y, 0.0), base.add(0.0, y, xz), base.add(0.0, y, -xz),
                base.add(xz, y, xz), base.add(-xz, y, -xz), base.add(xz, y, -xz), base.add(-xz, y, xz)));
        Collections.shuffle(offsets);
        for (Vec3 offset : offsets) if (!TeleportAuraUtils.isInvalidPosition(offset)) return offset;
        Vec3 vertical = base.add(0.0, y, 0.0);
        return TeleportAuraUtils.isInvalidPosition(vertical) ? base : vertical;
    }

    private void handleLagback() {
        if (phase == Phase.IDLE) return;
        resetCycle();
        if (lagbackDisable.getValue()) setEnabled(false);
        else {
            phase = Phase.PAUSED;
            pauseUntil = System.currentTimeMillis() + LAGBACK_PAUSE_MS;
        }
    }

    private void updateTarget() {
        if (isValidTarget(chaseTarget)) {
            target = chaseTarget;
            return;
        }
        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) if (entity instanceof LivingEntity living && isValidTarget(living)) candidates.add(living);
        target = candidates.stream().min(comparator()).orElse(null);
        if (target != null) chaseTarget = target;
    }

    private boolean isValidTarget(LivingEntity entity) {
        return entity != null && entity.isAlive() && entity.getHealth() > 0.0F
                && RemixEntityUtils.isSelected(entity) && mc.player.distanceTo(entity) <= range.getValue();
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

    private void recordPoint(Vec3 pos) {
        if (visualPoints.size() >= 10) visualPoints.removeFirst();
        visualPoints.add(new VisualPoint(pos, System.currentTimeMillis()));
    }

    private void pruneVisuals() {
        long now = System.currentTimeMillis();
        Iterator<VisualPoint> iterator = visualPoints.iterator();
        while (iterator.hasNext()) if (now - iterator.next().time > VISUAL_FADE_MS) iterator.remove();
    }

    private void renderVisuals() {
        for (int i = 0; i < visualPoints.size(); i++) {
            VisualPoint point = visualPoints.get(i);
            float alpha = alpha(point.time);
            if (alpha <= 0.01F) continue;
            if (renderPos.getValue()) {
                float distanceAlpha = mc.player.position().distanceTo(point.pos) < 6.0 ? 0.4F : 1.0F;
                Render3DScheduler.INSTANCE.addOutlineBox(pointBox(point.pos), withAlpha(renderPosColor.getValue(), alpha * distanceAlpha), 2.0F);
            }
            if (renderTrail.getValue() && i > 0) {
                VisualPoint previous = visualPoints.get(i - 1);
                float lineAlpha = Math.min(alpha, alpha(previous.time));
                int steps = Math.max(2, (int) Math.ceil(point.pos.distanceTo(previous.pos) / 0.35));
                for (int step = 0; step <= steps; step++) {
                    Vec3 mid = previous.pos.lerp(point.pos, step / (double) steps);
                    Render3DScheduler.INSTANCE.addFilledBox(new AABB(mid.subtract(0.035, 0.035, 0.035), mid.add(0.035, 0.035, 0.035)), withAlpha(renderTrailColor.getValue(), lineAlpha));
                }
            }
        }
    }

    private AABB pointBox(Vec3 pos) {
        double half = renderPosScale.getValue() / 2.0;
        return new AABB(pos.x - half, pos.y, pos.z - half, pos.x + half, pos.y + 1.8, pos.z + half);
    }

    private float[] projectBounds(AABB box) {
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

    private void drawOutline(UiTree.Scope scope, float x, float y, float endX, float endY, float thickness, Color color) {
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

    private int withAlpha(Color color, float alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.clamp(Math.round(255.0F * alpha), 0, 255)).getRGB();
    }

    private float alpha(long time) {
        float progress = Math.max(0.0F, 1.0F - (System.currentTimeMillis() - time) / (float) VISUAL_FADE_MS);
        return progress * progress;
    }

    private void resetCycle() {
        phase = Phase.IDLE;
        path.clear();
        pathIndex = 0;
        holdTicksLeft = 0;
        preAttackTicksLeft = 0;
        originalPos = null;
        attackPos = null;
        returnTo = null;
        attackEntity = null;
    }

    private void resetState() {
        resetCycle();
        visualPoints.clear();
        target = null;
        chaseTarget = null;
        renderBlock = false;
        lastAttack = 0L;
        pauseUntil = 0L;
    }

    private record VisualPoint(Vec3 pos, long time) { }
}
