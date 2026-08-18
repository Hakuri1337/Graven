package tech.hakuri.graven.mixins;

import tech.hakuri.graven.modules.impl.render.ItemPhysics;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class MixinItemEntityRenderer extends EntityRenderer<ItemEntity, ItemEntityRenderState> {

    @Shadow @Final private RandomSource random;

    protected MixinItemEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void graven$submitItemPhysics(ItemEntityRenderState state, PoseStack poseStack,
                                          SubmitNodeCollector submitNodeCollector,
                                          CameraRenderState camera, CallbackInfo ci) {
        if (!ItemPhysics.INSTANCE.isEnabled()) return;
        ci.cancel();
        if (state.item.isEmpty()) return;

        poseStack.pushPose();
        random.setSeed(state.seed);
        boolean block = state.item.usesBlockLight();
        poseStack.last().pose().setRowColumn(3, 1, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.translate(0.0F, block ? -0.2F : 0.0F, block ? -0.01F : -0.05F);

        for (int i = 0; i < state.count; i++) {
            poseStack.pushPose();
            if (i > 0 && block) {
                poseStack.translate(
                        (random.nextFloat() * 2.0F - 1.0F) * 0.15F,
                        (random.nextFloat() * 2.0F - 1.0F) * 0.15F,
                        (random.nextFloat() * 2.0F - 1.0F) * 0.15F
                );
            }
            state.item.submit(poseStack, submitNodeCollector, state.lightCoords,
                    OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
            if (!block) poseStack.translate(0.0F, 0.0F, 0.09375F);
        }

        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }
}
