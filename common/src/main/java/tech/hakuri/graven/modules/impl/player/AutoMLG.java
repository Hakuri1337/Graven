package tech.hakuri.graven.modules.impl.player;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.bus.EventPriority;
import tech.hakuri.graven.events.impl.ClientTickEvent;
import tech.hakuri.graven.events.impl.UseItemEvent;
import tech.hakuri.graven.managers.Managers;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.DoubleSetting;
import tech.hakuri.graven.utils.rotation.Priority;
import tech.hakuri.graven.utils.rotation.Rot2f;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public final class AutoMLG extends Module {

    public static final AutoMLG INSTANCE = new AutoMLG();

    private static final Random RANDOM = new Random();

    private final DoubleSetting triggerDistanceSetting = doubleSetting("Fall distance", 3.0, 1.0, 10.0, 0.1);
    private final DoubleSetting predictTicksSetting = doubleSetting("Predict Ticks", 2.0, 1.0, 5.0, 1.0);
    private final BoolSetting solidCheckSetting = boolSetting("Solid check", true);
    private final BoolSetting recoverySetting = boolSetting("Recorvey", true);

    public Rot2f targetRotation;
    private float accumulatedFall;
    private double lastY;
    private Integer slotToRestore;
    private boolean waterPlaced;
    private boolean recoveryActive;
    private int recoveryDelay;
    private int recoveryCountdown;
    private Integer waterBucketSlot;
    private BlockPos placedWaterPos;
    private boolean readyToPlace;
    private int postPlaceCooldown;
    private int postActionCooldown;
    private int extraCooldown;
    private boolean usingItemRotation;
    private Rot2f actionRotation;

    private AutoMLG() {
        super("AutoMLG", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        resetState(false);
        lastY = mc.player != null ? mc.player.getY() : 0.0;
    }

    @Override
    protected void onDisable() {
        resetState(true);
    }

    public boolean isInCooldown() {
        return postPlaceCooldown > 0;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onTick(ClientTickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.isFallFlying()) return;

        if (mc.player.onGround()
                || mc.player.getAbilities().flying
                || isInWaterRainOrBubble()
                || mc.player.isInLava()) {
            accumulatedFall = 0.0F;
        } else {
            double deltaY = mc.player.getY() - lastY;
            if (deltaY < 0.0) {
                accumulatedFall -= (float) deltaY;
            }
        }

        lastY = mc.player.getY();

        if (postPlaceCooldown > 0) --postPlaceCooldown;
        if (postActionCooldown > 0) --postActionCooldown;
        if (extraCooldown > 0) --extraCooldown;

        restoreSlot();

        if (mc.player.onGround() || accumulatedFall <= 0.0F) {
            waterPlaced = false;
            readyToPlace = false;
        }

        if (recoveryActive) {
            handleRecovery();
            return;
        }

        int slot;
        BlockPos bucketPos;
        Rot2f rotation;
        BlockHitResult hit;
        if (!waterPlaced
                && !recoveryActive
                && placedWaterPos == null
                && postPlaceCooldown == 0
                && postActionCooldown == 0
                && accumulatedFall <= 0.5F
                && findHotbarItem(Items.WATER_BUCKET) < 0
                && (slot = findHotbarItem(Items.BUCKET)) >= 0
                && (bucketPos = findBucketPos()) != null
                && (hit = raycastFluid(rotation = rotationToBlock(bucketPos), 4.5)).getType() != HitResult.Type.MISS
                && hit.getBlockPos().equals(bucketPos)) {
            setTargetRotation(rotation);
            selectSlot(slot);
            useItem(rotation);
            postActionCooldown = 8;
            postPlaceCooldown = Math.max(postPlaceCooldown, 1);
            return;
        }

        if (waterPlaced && !readyToPlace && mc.player.getDeltaMovement().y < 0.0) {
            double distance = distanceToGround(2.5);
            if (distance > 0.0 && distance <= 1.05) {
                readyToPlace = true;
            }
        }

        if (waterPlaced) return;
        if (accumulatedFall < triggerDistanceSetting.getValue().floatValue()) return;

        slot = findHotbarItem(Items.WATER_BUCKET);
        if (slot < 0) return;

        int ticksLeft = ticksUntilGround();
        if (ticksLeft <= predictTicksSetting.getValue().intValue()) {
            if (solidCheckSetting.getValue()
                    && !hasSolidBelow(BlockPos.containing(mc.player.getX(), mc.player.getY(), mc.player.getZ()))) {
                return;
            }

            rotation = new Rot2f(mc.player.getYRot(), 90.0F);
            hit = raycastSolid(rotation, 5.0);
            if (hit.getType() == HitResult.Type.MISS) return;
            placeWaterBucket(slot, true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onUseItem(UseItemEvent event) {
        if (!usingItemRotation || actionRotation == null) return;
        event.setYaw(actionRotation.getYaw());
        event.setPitch(actionRotation.getPitch());
    }

    private void handleRecovery() {
        if (recoveryDelay > 0) {
            --recoveryDelay;
            return;
        }

        if (recoveryCountdown-- <= 0) {
            recoveryActive = false;
            return;
        }

        if (waterBucketSlot == null) {
            waterBucketSlot = findHotbarItem(Items.BUCKET);
            if (waterBucketSlot < 0) {
                clearRecovery();
                return;
            }
        }

        if (mc.player.getInventory().getItem(waterBucketSlot).is(Items.WATER_BUCKET)) {
            clearRecovery();
            postPlaceCooldown = Math.max(postPlaceCooldown, 1);
            return;
        }

        if (placedWaterPos == null || !isWaterSource(placedWaterPos)) {
            clearRecovery();
            return;
        }

        Rot2f recoveryRotation = rotationToBlock(placedWaterPos);
        BlockHitResult recoveryHit = raycastFluid(recoveryRotation, 4.5);
        if (recoveryHit.getType() == HitResult.Type.MISS
                || !recoveryHit.getBlockPos().equals(placedWaterPos)) {
            clearRecovery();
            return;
        }

        setTargetRotation(recoveryRotation);
        selectSlot(waterBucketSlot);
        useItem(recoveryRotation);
    }

    private int ticksUntilGround() {
        if (mc.player.getDeltaMovement().y >= 0.0) return 999;

        double distance = distanceToGround(30.0);
        if (distance == Double.POSITIVE_INFINITY) return 999;

        double simulatedDrop = 0.0;
        double simulatedVelocity = mc.player.getDeltaMovement().y;
        for (int tick = 1; tick <= 20; ++tick) {
            simulatedDrop += simulatedVelocity;
            simulatedVelocity = (simulatedVelocity - 0.08) * 0.98;
            if (Math.abs(simulatedDrop) >= distance) return tick;
        }
        return 999;
    }

    private void useItem(Rot2f rotation) {
        if (mc.gameMode == null || mc.player == null) return;

        Rot2f continuousRotation = keepYawContinuous(rotation);
        float originalPitch = mc.player.getXRot();
        float originalYaw = mc.player.getYRot();
        if (continuousRotation != null) {
            mc.player.setXRot(continuousRotation.getPitch());
            mc.player.setYRot(continuousRotation.getYaw());
        }

        actionRotation = continuousRotation;
        usingItemRotation = continuousRotation != null;
        try {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            mc.player.swing(InteractionHand.MAIN_HAND);
        } finally {
            usingItemRotation = false;
            actionRotation = null;
            if (continuousRotation != null) {
                mc.player.setXRot(originalPitch);
                mc.player.setYRot(originalYaw);
            }
        }
    }

    private BlockPos findBucketPos() {
        BlockPos playerPos = BlockPos.containing(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        BlockPos closestPos = null;
        double closestDistSq = Double.POSITIVE_INFINITY;
        int radius = 4;

        for (int dy = -1; dy <= 1; ++dy) {
            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    BlockPos candidatePos = playerPos.offset(dx, dy, dz);
                    if (!isWaterSource(candidatePos)) continue;

                    double distSq = mc.player.position().distanceToSqr(
                            candidatePos.getX() + 0.5,
                            candidatePos.getY() + 0.5,
                            candidatePos.getZ() + 0.5
                    );
                    if (distSq >= closestDistSq) continue;

                    Rot2f rotation = rotationToBlock(candidatePos);
                    BlockHitResult hit = raycastFluid(rotation, 4.5);
                    if (hit.getType() == HitResult.Type.MISS || !hit.getBlockPos().equals(candidatePos)) continue;

                    closestPos = candidatePos;
                    closestDistSq = distSq;
                }
            }
        }
        return closestPos;
    }

    private Rot2f rotationToBlock(BlockPos blockPos) {
        Vec3 predictedPos = new Vec3(
                mc.player.getX(),
                mc.player.getY() + mc.player.getEyeHeight(),
                mc.player.getZ()
        );
        double dx = blockPos.getX() - predictedPos.x + 0.5;
        double dy = blockPos.getY() - predictedPos.y + 0.5;
        double dz = blockPos.getZ() - predictedPos.z + 0.5;
        return rotationFromDeltas(addNoise(dx), addNoise(dy), addNoise(dz));
    }

    private Rot2f rotationFromDeltas(double dx, double dy, double dz) {
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));
        return new Rot2f(Mth.wrapDegrees(yaw), Mth.wrapDegrees(pitch));
    }

    private double addNoise(double value) {
        return value + randomDouble(0.05, 0.08) * (randomDouble(0.0, 1.0) * 2.0 - 1.0);
    }

    private double randomDouble(double min, double max) {
        return min >= max ? min : RANDOM.nextDouble() * (max - min) + min;
    }

    private void setTargetRotation(Rot2f rotation) {
        targetRotation = keepYawContinuous(rotation);
        Managers.ROTATION.setRotations(targetRotation, 180, Priority.Highest);
        targetRotation = null;
    }

    /**
     * 将等价 yaw 表示保持在上一发送旋转附近，避免 -180/180 边界产生 360 度单包跳变。
     * Grim 的 AimModulo360 会将这种模 360 编码识别为异常，但该处理不改变实际视线方向。
     */
    private Rot2f keepYawContinuous(Rot2f rotation) {
        if (rotation == null || mc.player == null) return rotation;

        Rot2f lastRotation = Managers.ROTATION.getLastRotation();
        float referenceYaw = lastRotation != null ? lastRotation.getYaw() : mc.player.getYRot();
        if (!Float.isFinite(referenceYaw)) referenceYaw = mc.player.getYRot();

        float yaw = referenceYaw + Mth.wrapDegrees(rotation.getYaw() - referenceYaw);
        float pitch = Mth.clamp(rotation.getPitch(), -90.0F, 90.0F);
        return new Rot2f(yaw, pitch);
    }

    private void selectSlot(int slot) {
        slotToRestore = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
    }

    private void restoreSlot() {
        if (slotToRestore == null) return;
        if (mc.player != null && slotToRestore >= 0 && slotToRestore < 9) {
            mc.player.getInventory().setSelectedSlot(slotToRestore);
        }
        slotToRestore = null;
    }

    private void placeWaterBucket(int slot, boolean markPlaced) {
        Rot2f rotation = new Rot2f(mc.player.getYRot(), 90.0F);
        setTargetRotation(rotation);
        selectSlot(slot);
        useItem(rotation);
        if (markPlaced) waterPlaced = true;

        recoveryActive = recoverySetting.getValue();
        recoveryDelay = 3;
        recoveryCountdown = recoveryActive ? 2 : 0;
        waterBucketSlot = null;
        placedWaterPos = getPlacementBlockPos(rotation);
    }

    private BlockPos getPlacementBlockPos(Rot2f rotation) {
        BlockHitResult hit = raycastSolid(rotation, 4.5);
        if (hit.getType() == HitResult.Type.MISS) return null;
        return hit.getBlockPos().relative(hit.getDirection());
    }

    private BlockHitResult raycastSolid(Rot2f rotation, double range) {
        Vec3 eyePos = mc.player.getEyePosition(1.0F);
        Vec3 direction = Vec3.directionFromRotation(rotation.getPitch(), rotation.getYaw());
        Vec3 endPos = eyePos.add(direction.scale(range));
        return mc.level.clip(new ClipContext(
                eyePos,
                endPos,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player
        ));
    }

    private BlockHitResult raycastFluid(Rot2f rotation, double range) {
        Vec3 eyePos = mc.player.getEyePosition(1.0F);
        Vec3 direction = Vec3.directionFromRotation(rotation.getPitch(), rotation.getYaw());
        Vec3 endPos = eyePos.add(direction.scale(range));
        return mc.level.clip(new ClipContext(
                eyePos,
                endPos,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.SOURCE_ONLY,
                mc.player
        ));
    }

    private boolean isWaterSource(BlockPos blockPos) {
        FluidState fluidState = mc.level.getFluidState(blockPos);
        return fluidState.getType() == Fluids.WATER && fluidState.isSource();
    }

    private boolean hasSolidBelow(BlockPos blockPos) {
        return isSolidNonMenu(blockPos.below()) || isSolidNonMenu(blockPos.below(2));
    }

    private boolean isSolidNonMenu(BlockPos blockPos) {
        BlockState blockState = mc.level.getBlockState(blockPos);
        boolean hasCollision = !blockState.getCollisionShape(mc.level, blockPos).isEmpty();
        boolean noMenu = blockState.getMenuProvider(mc.level, blockPos) == null;
        return hasCollision && noMenu;
    }

    private double distanceToGround(double maxDist) {
        Vec3 startPos = new Vec3(mc.player.getX(), mc.player.getBoundingBox().minY, mc.player.getZ());
        Vec3 endPos = startPos.add(0.0, -maxDist, 0.0);
        BlockHitResult hit = mc.level.clip(new ClipContext(
                startPos,
                endPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        if (hit.getType() == HitResult.Type.MISS) return Double.POSITIVE_INFINITY;
        return startPos.y - hit.getLocation().y;
    }

    private int findHotbarItem(Item item) {
        for (int slot = 0; slot < 9; ++slot) {
            if (mc.player.getInventory().getItem(slot).is(item)) return slot;
        }
        return -1;
    }

    private boolean isInWaterRainOrBubble() {
        return mc.player.isInWaterOrRain()
                || mc.level.getBlockState(mc.player.blockPosition()).is(Blocks.BUBBLE_COLUMN);
    }

    private void clearRecovery() {
        recoveryActive = false;
        waterBucketSlot = null;
        placedWaterPos = null;
    }

    private void resetState(boolean restoreSelectedSlot) {
        if (restoreSelectedSlot) restoreSlot();
        else slotToRestore = null;

        waterPlaced = false;
        recoveryActive = false;
        recoveryDelay = 0;
        recoveryCountdown = 0;
        waterBucketSlot = null;
        placedWaterPos = null;
        readyToPlace = false;
        postPlaceCooldown = 0;
        postActionCooldown = 0;
        extraCooldown = 0;
        accumulatedFall = 0.0F;
        targetRotation = null;
        usingItemRotation = false;
        actionRotation = null;
    }
}
