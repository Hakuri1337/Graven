package tech.hakuri.graven.utils.player;

import tech.hakuri.graven.utils.rotation.Rot2f;
import tech.hakuri.graven.utils.rotation.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static tech.hakuri.graven.Constants.mc;

public final class TeleportAuraUtils {

    private TeleportAuraUtils() {
    }

    public static List<Vec3> buildPath(Vec3 from, Vec3 to, double stepSize, int limit) {
        return buildOutboundPath(from, to, stepSize, 8.0, limit);
    }

    public static List<Vec3> buildOutboundPath(Vec3 from, Vec3 to, double stepSize, double maxUp, int limit) {
        if (hasLineOfSight(from.add(0.0, 1.0, 0.0), to.add(0.0, 1.0, 0.0))) {
            return buildDirectPath(stepSize, limit, from, to);
        }

        Vec3 aboveFrom = findSafeAbove(from, maxUp);
        Vec3 aboveTo = findSafeAbove(to, maxUp);
        if (aboveFrom == null || aboveTo == null
                || !hasLineOfSight(aboveFrom.add(0.0, 1.0, 0.0), aboveTo.add(0.0, 1.0, 0.0))) {
            return buildDirectPath(stepSize, limit, from, to);
        }
        return buildDirectPath(stepSize, limit, from, aboveFrom, aboveTo, to);
    }

    public static List<Vec3> buildDirectPath(double stepSize, int limit, Vec3... waypoints) {
        List<Vec3> points = new ArrayList<>();
        double step = Math.max(0.5, stepSize);
        for (int i = 0; i < waypoints.length - 1; i++) {
            Vec3 from = waypoints[i];
            Vec3 to = waypoints[i + 1];
            if (from == null || to == null) continue;
            double distance = from.distanceTo(to);
            if (distance < 0.05) continue;
            int steps = Math.max(1, (int) Math.ceil(distance / step));
            for (int point = 1; point <= steps; point++) {
                points.add(from.lerp(to, point / (double) steps));
            }
        }

        List<Vec3> result = new ArrayList<>();
        Vec3 last = null;
        for (Vec3 point : points) {
            if (last == null || last.distanceToSqr(point) > 1.0E-4) {
                result.add(point);
                last = point;
            }
        }
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    public static Vec3 findSafeAbove(Vec3 from, double maxUp) {
        for (double dy = 2.0; dy <= maxUp; dy += 0.5) {
            Vec3 candidate = from.add(0.0, dy, 0.0);
            if (!isInvalidPosition(candidate)) return candidate;
        }
        return null;
    }

    public static boolean hasLineOfSight(Vec3 from, Vec3 to) {
        return mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }

    public static boolean isInvalidPosition(Vec3 pos) {
        AABB box = mc.player.getBoundingBox().move(pos.subtract(mc.player.position()));
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos blockPos : BlockPos.betweenClosed(min, max)) {
            var state = mc.level.getBlockState(blockPos);
            if (!state.getCollisionShape(mc.level, blockPos).isEmpty() || state.is(Blocks.LAVA)) return true;
        }
        return false;
    }

    public static Vec3 findNearestSafePosition(Vec3 desired) {
        for (int dy = 0; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Vec3 test = desired.add(dx, dy, dz);
                    if (!isInvalidPosition(test)) return test;
                }
            }
        }
        return null;
    }

    public static boolean isOnGroundAt(Vec3 pos) {
        BlockPos below = BlockPos.containing(pos.x, pos.y - 0.1, pos.z);
        return !mc.level.getBlockState(below).getCollisionShape(mc.level, below).isEmpty();
    }

    public static Vec3 predictedPosition(LivingEntity entity, double ticks) {
        Vec3 current = entity.position();
        Vec3 last = current.subtract(entity.getX() - entity.xOld,
                entity.getY() - entity.yOld, entity.getZ() - entity.zOld);
        return last.lerp(current, Math.clamp(ticks, 0.0, 1.0));
    }

    public static Rot2f rotations(Vec3 attackPosition, LivingEntity target) {
        return RotationUtils.calculate(attackPosition.add(0.0, mc.player.getEyeHeight(), 0.0),
                target.getBoundingBox().getCenter());
    }

    public static double distanceToBox(Vec3 point, AABB box) {
        double x = Math.max(box.minX - point.x, Math.max(0.0, point.x - box.maxX));
        double y = Math.max(box.minY - point.y, Math.max(0.0, point.y - box.maxY));
        double z = Math.max(box.minZ - point.z, Math.max(0.0, point.z - box.maxZ));
        return Math.sqrt(x * x + y * y + z * z);
    }
}
