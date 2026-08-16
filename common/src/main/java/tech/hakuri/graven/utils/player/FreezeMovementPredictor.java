package tech.hakuri.graven.utils.player;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 在未加入世界实体列表的玩家副本上执行原版移动积分，用于 Freeze BalanceWarp 路径预览。
 */
public final class FreezeMovementPredictor {

    private FreezeMovementPredictor() {
    }

    public static List<Vec3> predict(Minecraft mc, int ticks, float yaw, float pitch) {
        LocalPlayer source = mc.player;
        if (source == null || mc.level == null || ticks <= 0) return List.of();

        SimulationPlayer simulated = new SimulationPlayer(mc.level,
                new GameProfile(UUID.randomUUID(), "Graven Freeze Prediction"));
        copyState(source, simulated, yaw, pitch);

        Vec2 movement = movementInput(mc);
        boolean jump = mc.options.keyJump.isDown();
        boolean shift = mc.options.keyShift.isDown();
        boolean sprint = mc.options.keySprint.isDown() || source.isSprinting();

        List<Vec3> positions = new ArrayList<>(ticks + 1);
        positions.add(simulated.position());
        for (int i = 0; i < ticks; i++) {
            simulated.simulateStep(movement, jump, shift, sprint);
            positions.add(simulated.position());
        }
        return positions;
    }

    private static Vec2 movementInput(Minecraft mc) {
        float forward = impulse(mc.options.keyUp.isDown(), mc.options.keyDown.isDown());
        float left = impulse(mc.options.keyLeft.isDown(), mc.options.keyRight.isDown());
        return new Vec2(left, forward).normalized();
    }

    private static float impulse(boolean positive, boolean negative) {
        if (positive == negative) return 0.0F;
        return positive ? 1.0F : -1.0F;
    }

    private static void copyState(LocalPlayer source, SimulationPlayer simulated, float yaw, float pitch) {
        simulated.noPhysics = false;
        simulated.setPos(source.position());
        simulated.setBoundingBox(source.getBoundingBox());
        simulated.setDeltaMovement(source.getDeltaMovement());
        simulated.setYRot(yaw);
        simulated.setXRot(pitch);
        simulated.yRotO = yaw;
        simulated.xRotO = pitch;
        simulated.setPose(source.getPose());
        simulated.setShiftKeyDown(source.isShiftKeyDown());
        simulated.setSwimming(source.isSwimming());
        simulated.setSprinting(source.isSprinting());
        simulated.setOnGround(source.onGround());
        simulated.horizontalCollision = source.horizontalCollision;
        simulated.verticalCollision = source.verticalCollision;
        simulated.fallDistance = source.fallDistance;
        simulated.getInventory().replaceWith(source.getInventory());
        simulated.getAbilities().apply(source.getAbilities().pack());
        for (MobEffectInstance effect : source.getActiveEffects()) {
            simulated.addEffect(new MobEffectInstance(effect));
        }
        if (simulated.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            simulated.getAttribute(Attributes.MOVEMENT_SPEED)
                    .setBaseValue(source.getAttributeBaseValue(Attributes.MOVEMENT_SPEED));
        }
    }

    private static final class SimulationPlayer extends RemotePlayer {

        private SimulationPlayer(net.minecraft.client.multiplayer.ClientLevel level, GameProfile profile) {
            super(level, profile);
        }

        private void simulateStep(Vec2 movement, boolean jump, boolean shift, boolean sprint) {
            setShiftKeyDown(shift);
            setSprinting(sprint);
            Abilities abilities = getAbilities();

            if (jump) {
                if (abilities.flying) {
                    addDeltaMovement(new Vec3(0.0, abilities.getFlyingSpeed() * 3.0F, 0.0));
                } else if (onGround()) {
                    jumpFromGround();
                }
            } else if (shift && abilities.flying) {
                addDeltaMovement(new Vec3(0.0, -abilities.getFlyingSpeed() * 3.0F, 0.0));
            }

            float crouchMultiplier = shift && !abilities.flying ? 0.3F : 1.0F;
            travel(new Vec3(movement.x * crouchMultiplier, 0.0, movement.y * crouchMultiplier));
            setPose(shift && !abilities.flying ? Pose.CROUCHING : Pose.STANDING);
            updateSwimming();
        }

        @Override
        public void push(net.minecraft.world.entity.Entity entity) {
        }
    }
}
