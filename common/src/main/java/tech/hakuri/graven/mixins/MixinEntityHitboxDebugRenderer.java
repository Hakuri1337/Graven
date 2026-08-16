package tech.hakuri.graven.mixins;

import tech.hakuri.graven.modules.impl.render.maseffects.MasEffects;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityHitboxDebugRenderer.class)
public abstract class MixinEntityHitboxDebugRenderer {

    @Inject(method = "showHitboxes", at = @At("HEAD"), cancellable = true)
    private void onShowHitboxes(Entity entity, float partialTicks, boolean serverEntity, CallbackInfo ci) {
        if (MasEffects.INSTANCE.renderCustomHitbox(entity, partialTicks)) {
            ci.cancel();
        }
    }

}
