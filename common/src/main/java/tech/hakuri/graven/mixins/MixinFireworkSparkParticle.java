package tech.hakuri.graven.mixins;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.hakuri.graven.modules.impl.render.NoRender;

/** 在保留原版 SparkParticle 实例的前提下隐藏烟花渲染，避免 Starter 收到 null。 */
@Mixin(targets = "net.minecraft.client.particle.FireworkParticles$SparkParticle")
public abstract class MixinFireworkSparkParticle {

    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void graven$hideWhenDisabled(
            QuadParticleRenderState particleTypeRenderState, Camera camera, float partialTickTime, CallbackInfo ci
    ) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.fireworks.getValue()) {
            ci.cancel();
        }
    }
}
