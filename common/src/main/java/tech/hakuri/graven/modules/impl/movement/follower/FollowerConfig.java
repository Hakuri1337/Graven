package tech.hakuri.graven.modules.impl.movement.follower;

public record FollowerConfig(
        double stopDistance,
        double verticalDeadzone,
        int searchRadius,
        int maxNodes
) {
}
