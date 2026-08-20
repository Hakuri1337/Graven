package tech.hakuri.graven.mixins;

import tech.hakuri.graven.events.bus.EventBus;
import tech.hakuri.graven.events.impl.AttackSlowDownEvent;
import tech.hakuri.graven.events.impl.AttackYawEvent;
import tech.hakuri.graven.events.impl.TravelEvent;
import tech.hakuri.graven.modules.impl.movement.KeepSprint;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static tech.hakuri.graven.Constants.mc;

@Mixin(Player.class)
public class MixinPlayer {

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void onTravelPre(Vec3 input, CallbackInfo ci) {
        if ((Player) (Object) this == mc.player) {
            TravelEvent event = EventBus.INSTANCE.post(new TravelEvent());
            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }

    @ModifyExpressionValue(method = {"causeExtraKnockback", "doSweepAttack"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"))
    private float modifyAttackYaw(float original) {
        AttackYawEvent event = EventBus.INSTANCE.post(new AttackYawEvent(original));
        return event.getYaw();
    }

    @Inject(method = "causeExtraKnockback", at = @At("HEAD"), cancellable = true)
    private void onCauseExtraKnockback(Entity entity, float knockbackAmount, Vec3 oldMovement, CallbackInfo ci) {
        AttackSlowDownEvent event = EventBus.INSTANCE.post(new AttackSlowDownEvent(entity, knockbackAmount));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "causeExtraKnockback",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V",
                    shift = Shift.AFTER
            )
    )
    private void applyCubeCraftKeepSprint(Entity entity, float knockbackAmount, Vec3 oldMovement, CallbackInfo ci) {
        KeepSprint keepSprint = KeepSprint.INSTANCE;
        if ((Player) (Object) this != mc.player || !keepSprint.isEnabled() || !keepSprint.isCubeCraft()) return;

        double multiplier = 0.6 + 0.4 * keepSprint.motion.getValue();
        Vec3 movement = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(
                movement.x / 0.6 * multiplier,
                movement.y,
                movement.z / 0.6 * multiplier
        );
        mc.player.setSprinting(true);
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void applyLegacyKeepSprint(Entity entity, CallbackInfo ci) {
        KeepSprint keepSprint = KeepSprint.INSTANCE;
        if ((Player) (Object) this != mc.player || !keepSprint.isEnabled() || keepSprint.isCubeCraft() || !keepSprint.shouldKeepSprint()) return;

        if (!mc.player.isSprinting()) {
            mc.player.setSprinting(true);
        }
        double slowdownPercent = keepSprint.slowdown.getValue().doubleValue() / 100.0;
        if (slowdownPercent > 0.0) {
            double customFactor = 0.6 + 0.4 * (1.0 - slowdownPercent);
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().multiply(customFactor, 1.0, customFactor));
        }
    }

}
