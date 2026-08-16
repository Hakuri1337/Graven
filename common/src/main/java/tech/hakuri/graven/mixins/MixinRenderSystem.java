package tech.hakuri.graven.mixins;

import tech.hakuri.graven.graphics.LuminRenderSystem;
import tech.hakuri.graven.graphics.immediate.LuminImmediateRenderer;
import com.mojang.blaze3d.TracyFrameCapture;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public class MixinRenderSystem {

    @Inject(method = "flipFrame", at = @At("RETURN"))
    private static void onFlipFrame(@Nullable TracyFrameCapture tracyFrameCapture, CallbackInfo ci) {
        LuminRenderSystem.endDynamicUniformFrame();
        LuminImmediateRenderer.endFrame();
        LuminRenderSystem.beginRenderFrame();
    }

}
