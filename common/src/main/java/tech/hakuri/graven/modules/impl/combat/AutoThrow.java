package tech.hakuri.graven.modules.impl.combat;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.bus.EventPriority;
import tech.hakuri.graven.events.impl.ClientTickEvent;
import tech.hakuri.graven.events.impl.PostMovementPacketEvent;
import tech.hakuri.graven.events.impl.UseItemEvent;
import tech.hakuri.graven.managers.Managers;
import tech.hakuri.graven.managers.impl.rotations.ClientRotationTracker;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.modules.impl.movement.Blink;
import tech.hakuri.graven.modules.impl.movement.Freeze;
import tech.hakuri.graven.modules.impl.movement.Scaffold;
import tech.hakuri.graven.modules.impl.movement.Stuck;
import tech.hakuri.graven.settings.SettingGroup;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.DoubleSetting;
import tech.hakuri.graven.utils.rotation.Priority;
import tech.hakuri.graven.utils.rotation.Rot2f;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Optional;

public final class AutoThrow extends Module {

    public static final AutoThrow INSTANCE = new AutoThrow();

    private static final double PROJECTILE_SPEED = 1.5D;
    private static final double PROJECTILE_GRAVITY = 0.03D;
    private static final int MAX_TURN_TICKS = 4;

    private final SettingGroup sgTarget = settingGroup("Target");

    private final DoubleSetting minDistance = doubleSetting("Min Distance", 5.0D, 3.0D, 30.0D, 1.0D);
    private final DoubleSetting maxDistance = doubleSetting("Max Distance", 10.0D, 3.0D, 30.0D, 1.0D);
    private final DoubleSetting delay = doubleSetting("Delay", 500.0D, 50.0D, 2000.0D, 50.0D);
    private final DoubleSetting fov = doubleSetting("FOV", 90.0D, 15.0D, 180.0D, 5.0D);
    private final DoubleSetting turnSpeed = doubleSetting("Turn Speed", 35.0D, 10.0D, 90.0D, 5.0D);
    private final BoolSetting targetPlayer = boolSetting("Player", true).group(sgTarget);
    private final BoolSetting targetInvisible = boolSetting("Invisible", true).group(sgTarget);
    private final BoolSetting targetAnimals = boolSetting("Animals", false).group(sgTarget);
    private final BoolSetting targetMobs = boolSetting("Mobs", false).group(sgTarget);

    private final Stopwatch stopwatch = new Stopwatch();
    private ThrowPlan pendingPlan;
    private Rot2f pendingRotation;
    private Rot2f submittedRotation;
    private int rotationTicks;
    private int restoreSlot = -1;
    private boolean throwing;

    private AutoThrow() {
        super("AutoThrow", Category.COMBAT);
    }

    @EventHandler
    private void onPreGameTick(ClientTickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            clearPlan();
            return;
        }
        if (shouldPause()) {
            clearPlan();
            return;
        }

        if (pendingPlan != null) {
            updatePendingAim();
            return;
        }
        if (!stopwatch.hasTimeElapsed(delay.getValue().longValue())) return;

        Optional<ThrowPlan> plan = findThrowPlan();
        Optional<LivingEntity> target = findTarget();
        if (plan.isEmpty() || target.isEmpty() || mc.player.isUsingItem()) return;

        pendingRotation = getRotationToEntity(target.get());
        if (pendingRotation == null) return;

        pendingPlan = plan.get().withTarget(target.get().getId());
        rotationTicks = getRequiredRotationTicks(pendingRotation);
        stopwatch.reset();
    }

    @EventHandler
    private void onPostMovementPacket(PostMovementPacketEvent event) {
        restoreSlot();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onUseItem(UseItemEvent event) {
        if (!throwing || mc.player == null || Managers.ROTATION == null) return;
        Rot2f rotation = Managers.ROTATION.getRotation();
        event.setYaw(rotation.getYaw());
        event.setPitch(rotation.getPitch());
    }

    private void throwPending() {
        if (pendingPlan == null || mc.player == null || mc.gameMode == null) {
            clearPlan();
            return;
        }

        ThrowPlan plan = pendingPlan;
        if (!isPlanThrowable(plan)) {
            clearPlan();
            return;
        }
        if (plan.hand == InteractionHand.MAIN_HAND && plan.slot != mc.player.getInventory().getSelectedSlot()) {
            restoreSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(plan.slot);
        }

        throwing = true;
        try {
            mc.gameMode.useItem(mc.player, plan.hand);
        } finally {
            throwing = false;
        }
        mc.player.swing(plan.hand);
        clearPlan();
    }

    private Optional<ThrowPlan> findThrowPlan() {
        if (isThrowable(mc.player.getOffhandItem())) {
            return Optional.of(new ThrowPlan(InteractionHand.OFF_HAND, -1));
        }

        int selected = mc.player.getInventory().getSelectedSlot();
        if (isThrowable(mc.player.getInventory().getItem(selected))) {
            return Optional.of(new ThrowPlan(InteractionHand.MAIN_HAND, selected));
        }

        for (int slot = 0; slot < 9; slot++) {
            if (isThrowable(mc.player.getInventory().getItem(slot))) {
                return Optional.of(new ThrowPlan(InteractionHand.MAIN_HAND, slot));
            }
        }
        return Optional.empty();
    }

    private Optional<LivingEntity> findTarget() {
        double min = Math.min(minDistance.getValue(), maxDistance.getValue());
        double max = Math.max(minDistance.getValue(), maxDistance.getValue());
        return mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(max)).stream()
                .filter(entity -> entity != mc.player && entity.isAlive() && !entity.isSpectator())
                .filter(entity -> !AntiBot.INSTANCE.isBot(entity) && !AntiBot.INSTANCE.isBedWarsBot(entity))
                .filter(entity -> !Teams.isTeammate(entity))
                .filter(entity -> !isFriend(entity))
                .filter(entity -> !entity.isInvisibleTo(mc.player) || targetInvisible.getValue())
                .filter(this::isSelectedTargetType)
                .filter(mc.player::hasLineOfSight)
                .filter(this::isEntityInFov)
                .filter(entity -> {
                    double distance = getHorizontalDistance(entity);
                    return distance >= min && distance <= max;
                })
                .min(Comparator.comparingDouble(entity -> mc.player.distanceToSqr(entity)));
    }

    private boolean isSelectedTargetType(LivingEntity entity) {
        if (entity instanceof Player) return targetPlayer.getValue();
        if (entity instanceof Animal) return targetAnimals.getValue();
        return entity instanceof Mob && targetMobs.getValue();
    }

    private Rot2f getRotationToEntity(LivingEntity target) {
        Vec3 start = mc.player.getEyePosition();
        Vec3 end = getClosestVectorToBoundingBox(start, target);
        Vec3 difference = end.subtract(start);
        double horizontalDistance = Math.hypot(difference.x, difference.z);
        if (horizontalDistance < 1.0E-4D) return null;

        double speedSquared = PROJECTILE_SPEED * PROJECTILE_SPEED;
        double discriminant = speedSquared * speedSquared
                - PROJECTILE_GRAVITY * (PROJECTILE_GRAVITY * horizontalDistance * horizontalDistance
                + 2.0D * difference.y * speedSquared);
        if (discriminant < 0.0D) return null;

        double tangent = (speedSquared - Math.sqrt(discriminant)) / (PROJECTILE_GRAVITY * horizontalDistance);
        float yaw = (float) Math.toDegrees(-Math.atan2(difference.x, difference.z));
        float pitch = (float) -Math.toDegrees(Math.atan(tangent));
        if (Float.isNaN(yaw) || Float.isNaN(pitch)) return null;
        return getVanillaRotation(new Rot2f(yaw, Mth.clamp(pitch, -90.0F, 90.0F)));
    }

    private Vec3 getClosestVectorToBoundingBox(Vec3 from, LivingEntity entity) {
        AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
        return new Vec3(
                Mth.clamp(from.x, box.minX, box.maxX),
                Mth.clamp(from.y, box.minY, box.maxY),
                Mth.clamp(from.z, box.minZ, box.maxZ)
        );
    }

    private Rot2f getVanillaRotation(Rot2f original) {
        Rot2f sentRotation = getSensitivityModifiedRotation(patchConstantRotation(original, currentRotation()));
        float wrappedYaw = mc.player.getYRot() + Mth.wrapDegrees(sentRotation.getYaw() - mc.player.getYRot());
        return new Rot2f(wrappedYaw, sentRotation.getPitch());
    }

    private Rot2f patchConstantRotation(Rot2f rotation, Rot2f previousRotation) {
        double sensitivity = mc.options.sensitivity().get() * 0.6F + 0.2F;
        double multiplier = sensitivity * sensitivity * sensitivity * 8.0D;
        double divisor = multiplier * 0.15F;
        float yawDelta = rotation.getYaw() - previousRotation.getYaw();
        float pitchDelta = rotation.getPitch() - previousRotation.getPitch();
        float yaw = previousRotation.getYaw() + (float) (Math.round(yawDelta / divisor) * divisor);
        float pitch = previousRotation.getPitch() + (float) (Math.round(pitchDelta / divisor) * divisor);
        return new Rot2f(yaw, pitch);
    }

    private Rot2f getSensitivityModifiedRotation(Rot2f rotation) {
        return new Rot2f(
                getSensitivityModifiedRotation(rotation.getYaw()),
                getSensitivityModifiedRotation(rotation.getPitch())
        );
    }

    private float getSensitivityModifiedRotation(double original) {
        double sensitivity = mc.options.sensitivity().get() * 0.6F + 0.2F;
        double multiplier = sensitivity * sensitivity * sensitivity * 8.0D;
        return (float) (getCursorDelta(original, multiplier) * multiplier) * 0.15F;
    }

    private float getCursorDelta(double rotationDelta, double sensitivityMultiplier) {
        return (float) (rotationDelta / sensitivityMultiplier) / 0.15F;
    }

    private boolean isEntityInFov(Entity entity) {
        if (fov.getValue() >= 180.0D) return true;
        Vec3 difference = entity.position().subtract(mc.player.getEyePosition());
        float yaw = (float) Math.toDegrees(-Math.atan2(difference.x, difference.z));
        float clientYaw = ClientRotationTracker.INSTANCE.getYawOr(mc.player.getYRot());
        double yawDifference = (clientYaw - yaw) % 360.0D + 540.0D;
        double angle = yawDifference % 360.0D - 180.0D;
        return Math.abs(angle) < fov.getValue();
    }

    private double getHorizontalDistance(LivingEntity entity) {
        return Math.hypot(entity.getX() - mc.player.getX(), entity.getZ() - mc.player.getZ());
    }

    private boolean isThrowable(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(Items.EGG) || stack.is(Items.SNOWBALL));
    }

    private boolean shouldPause() {
        if (mc.screen != null || mc.getOverlay() != null) return true;
        return Scaffold.INSTANCE.isEnabled()
                || Stuck.INSTANCE.isEnabled()
                || Freeze.INSTANCE.isEnabled()
                || Blink.INSTANCE.isEnabled();
    }

    private void updatePendingAim() {
        LivingEntity target = getPendingTarget();
        if (target == null || !isPlanThrowable(pendingPlan)) {
            clearPlan();
            return;
        }

        pendingRotation = getRotationToEntity(target);
        if (pendingRotation == null) {
            clearPlan();
            return;
        }

        // 使用共享静默旋转管理器提交服务端角度，避免 MouseRotationController 直接改变屏幕视角。
        Managers.ROTATION.setRotations(pendingRotation, turnSpeed.getValue(), Priority.High);
        submittedRotation = pendingRotation;
        if (--rotationTicks <= 0) throwPending();
    }

    private LivingEntity getPendingTarget() {
        if (pendingPlan == null || mc.level == null) return null;
        Entity entity = mc.level.getEntity(pendingPlan.targetId());
        if (!(entity instanceof LivingEntity target)
                || target == mc.player
                || !target.isAlive()
                || target.isSpectator()
                || !mc.player.hasLineOfSight(target)
                || !isSelectedTargetType(target)
                || AntiBot.INSTANCE.isBot(target)
                || AntiBot.INSTANCE.isBedWarsBot(target)
                || Teams.isTeammate(target)
                || isFriend(target)) {
            return null;
        }

        double min = Math.min(minDistance.getValue(), maxDistance.getValue());
        double max = Math.max(minDistance.getValue(), maxDistance.getValue());
        double distance = getHorizontalDistance(target);
        return distance >= min && distance <= max ? target : null;
    }

    private boolean isPlanThrowable(ThrowPlan plan) {
        if (plan == null || mc.player == null) return false;
        if (plan.hand == InteractionHand.OFF_HAND) return isThrowable(mc.player.getOffhandItem());
        return plan.slot >= 0 && plan.slot < 9 && isThrowable(mc.player.getInventory().getItem(plan.slot));
    }

    private int getRequiredRotationTicks(Rot2f rotation) {
        Rot2f currentRotation = currentRotation();
        float yawDifference = Math.abs(Mth.wrapDegrees(rotation.getYaw() - currentRotation.getYaw()));
        float pitchDifference = Math.abs(rotation.getPitch() - currentRotation.getPitch());
        double difference = Math.hypot(yawDifference, pitchDifference);
        int ticks = (int) Math.ceil(difference / turnSpeed.getValue());
        return Math.max(1, Math.min(MAX_TURN_TICKS, ticks));
    }

    private Rot2f currentRotation() {
        return new Rot2f(mc.player.getYRot(), mc.player.getXRot());
    }

    private boolean isFriend(Entity entity) {
        String name = entity.getName().getString();
        return Managers.FRIEND.getFriends().stream().anyMatch(friend -> friend.equalsIgnoreCase(name));
    }

    private void restoreSlot() {
        if (mc.player != null && restoreSlot >= 0 && restoreSlot < 9) {
            mc.player.getInventory().setSelectedSlot(restoreSlot);
        }
        restoreSlot = -1;
    }

    private void clearPlan() {
        releaseRotation();
        pendingPlan = null;
        pendingRotation = null;
        rotationTicks = 0;
    }

    private void releaseRotation() {
        if (submittedRotation != null && Managers.ROTATION != null
                && Managers.ROTATION.isActive()
                && Managers.ROTATION.targetRotations == submittedRotation) {
            Managers.ROTATION.setActive(false);
        }
        submittedRotation = null;
    }

    @Override
    public String getInfo() {
        double min = Math.min(minDistance.getValue(), maxDistance.getValue());
        double max = Math.max(minDistance.getValue(), maxDistance.getValue());
        return min + " - " + max;
    }

    @Override
    protected void onEnable() {
        clearPlan();
        restoreSlot();
        stopwatch.reset();
    }

    @Override
    protected void onDisable() {
        restoreSlot();
        clearPlan();
    }

    private record ThrowPlan(InteractionHand hand, int slot, int targetId) {

        private ThrowPlan(InteractionHand hand, int slot) {
            this(hand, slot, -1);
        }

        private ThrowPlan withTarget(int targetId) {
            return new ThrowPlan(hand, slot, targetId);
        }
    }

    private static final class Stopwatch {

        private long lastMs;

        private Stopwatch() {
            reset();
        }

        private void reset() {
            lastMs = System.currentTimeMillis();
        }

        private boolean hasTimeElapsed(long time) {
            return System.currentTimeMillis() - lastMs > time;
        }
    }
}
