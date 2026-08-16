package tech.hakuri.graven.mixins;

import tech.hakuri.graven.modules.impl.render.maseffects.MasEffects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {

    @Inject(method = "levelEvent", at = @At("HEAD"))
    private void onLevelEvent(@Nullable Entity source, int type, BlockPos pos, int data, CallbackInfo ci) {
        MasEffects.INSTANCE.onLevelEvent(type, Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0));
    }

}
