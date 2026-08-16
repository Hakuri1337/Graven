package tech.hakuri.graven.utils.rotation.model;

import tech.hakuri.graven.utils.rotation.Rot2f;
import net.minecraft.util.Mth;

public final class LinearRotationModel implements RotationModel {

    private final double speed;

    public LinearRotationModel(double speed) {
        this.speed = speed;
    }

    @Override
    public Rot2f tick(Rot2f from, Rot2f to, float timeDelta) {
        float deltaYaw = Mth.wrapDegrees(to.getYaw() - from.getYaw()) * timeDelta;
        float deltaPitch = (to.getPitch() - from.getPitch()) * timeDelta;
        double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        if (distance == 0.0D) {
            return new Rot2f(from.getYaw() + deltaYaw, from.getPitch() + deltaPitch);
        }

        double distributionYaw = Math.abs(deltaYaw / distance);
        double distributionPitch = Math.abs(deltaPitch / distance);
        double maxYaw = speed * distributionYaw;
        double maxPitch = speed * distributionPitch;
        float moveYaw = (float) Mth.clamp(deltaYaw, -maxYaw, maxYaw);
        float movePitch = (float) Mth.clamp(deltaPitch, -maxPitch, maxPitch);
        return new Rot2f(from.getYaw() + moveYaw, from.getPitch() + movePitch);
    }
}
