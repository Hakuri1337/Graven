package tech.hakuri.graven.modules.impl.combat;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.MoveEvent;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.modules.impl.movement.Scaffold;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.DoubleSetting;
import tech.hakuri.graven.settings.impl.EnumSetting;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class TargetStrafe extends Module {

    public static final TargetStrafe INSTANCE = new TargetStrafe();

    private enum Mode { Adaptive, Behind }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Adaptive);
    private final DoubleSetting distance = doubleSetting("Distance", 2, 0.5, 4.5, 0.1);
    private final DoubleSetting points = doubleSetting("Points", 12, 3, 16, 1);
    private final BoolSetting space = boolSetting("Require space key", false);
    private final BoolSetting auto3rdPerson = boolSetting("Auto 3rd Person", false);
    private CameraType perspective = CameraType.FIRST_PERSON;
    private boolean changedPerspective;
    private int direction = 1;

    private TargetStrafe() {
        super("TargetStrafe", Category.COMBAT);
    }

    @Override
    protected void onDisable() {
        resetPerspective();
    }

    @EventHandler
    private void onMove(MoveEvent event) {
        if (nullCheck() || Scaffold.INSTANCE.isEnabled() || space.getValue() && !mc.options.keyJump.isDown()) return;
        Entity target = KillAura.INSTANCE.isEnabled() ? KillAura.INSTANCE.target : null;
        if (target == null) {
            resetPerspective();
            return;
        }
        if (auto3rdPerson.getValue() && !changedPerspective && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            perspective = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            changedPerspective = true;
        }
        if (mc.options.keyLeft.isDown()) direction = 1;
        else if (mc.options.keyRight.isDown()) direction = -1;
        if (mc.player.horizontalCollision) direction = -direction;

        Vec3 goal = getGoal(target);
        double diffX = goal.x - mc.player.getX();
        double diffZ = goal.z - mc.player.getZ();
        Vec3 movement = mc.player.getDeltaMovement();
        double speed = Math.hypot(movement.x, movement.z);
        double yaw = Math.atan2(diffZ, diffX);
        double motionX = speed * Math.cos(yaw);
        double motionZ = speed * Math.sin(yaw);
        if (isOverVoid(mc.player.getX() + motionX, mc.player.getY(), mc.player.getZ() + motionZ)) {
            direction = -direction;
            return;
        }
        event.setX(motionX);
        event.setZ(motionZ);
    }

    private Vec3 getGoal(Entity target) {
        double dist = Math.max(0.1, distance.getValue());
        if (mode.is(Mode.Behind)) {
            double yaw = Math.toRadians(target.getYRot() + 180.0F);
            return new Vec3(target.getX() - Math.sin(yaw) * dist, target.getY(), target.getZ() + Math.cos(yaw) * dist);
        }
        double currentAngle = Math.atan2(mc.player.getZ() - target.getZ(), mc.player.getX() - target.getX());
        double nextAngle = currentAngle + direction * (Math.PI * 2.0 / points.getValue().intValue());
        return new Vec3(target.getX() + Math.cos(nextAngle) * dist, target.getY(), target.getZ() + Math.sin(nextAngle) * dist);
    }

    private boolean isOverVoid(double x, double y, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        for (int blockY = pos.getY(); blockY >= mc.level.getMinY(); blockY--) {
            pos.setY(blockY);
            if (!mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty()) return false;
        }
        return true;
    }

    private void resetPerspective() {
        if (changedPerspective) {
            mc.options.setCameraType(perspective);
            changedPerspective = false;
        }
    }
}
