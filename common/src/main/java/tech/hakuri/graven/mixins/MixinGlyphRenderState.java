package tech.hakuri.graven.mixins;

import com.github.slmpc.lumingraphics.mc.v2612.text.TextRenderableAdapter;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlyphRenderState.class)
public class MixinGlyphRenderState {

    @Inject(method = "textureSetup", at = @At("HEAD"), cancellable = true)
    private void onTextureSetup(CallbackInfoReturnable<TextureSetup> cir) {
        TextRenderable renderable = ((GlyphRenderState) (Object) this).renderable();
        if (renderable instanceof TextRenderableAdapter adapter) {
            try {
                cir.setReturnValue(TextureSetup.singleTexture(renderable.textureView(), adapter.sampler()));
            } catch (RuntimeException failure) {
                String failureType = failure.getClass().getName();
                if (failureType.endsWith("FontClosedException")
                        || failureType.endsWith("UiResourceNotFoundException")) {
                    // Resource reload owns font teardown. This render state may belong to its preceding frame.
                    cir.setReturnValue(TextureSetup.noTexture());
                } else {
                    throw failure;
                }
            }
        }
    }
}
