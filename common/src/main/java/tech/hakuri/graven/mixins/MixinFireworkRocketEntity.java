package tech.hakuri.graven.mixins;

import tech.hakuri.graven.events.bus.EventBus;
import tech.hakuri.graven.events.impl.FireworkRotationEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static tech.hakuri.graven.Constants.mc;

@Mixin(FireworkRocketEntity.class)
public class MixinFireworkRocketEntity {

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 redirectMovement(LivingEntity instance, Operation<Vec3> original) {
        if (instance == mc.player) {
            FireworkRotationEvent event = EventBus.INSTANCE.post(new FireworkRotationEvent(instance.getYRot(), instance.getXRot()));
            return instance.calculateViewVector(event.getPitch(), event.getYaw());
        }
        return original.call(instance);
    }

}
