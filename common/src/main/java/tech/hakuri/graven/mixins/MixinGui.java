package tech.hakuri.graven.mixins;

import tech.hakuri.graven.events.bus.EventBus;
import tech.hakuri.graven.events.impl.Render2DEvent;
import tech.hakuri.graven.modules.impl.render.FreeCamera;
import tech.hakuri.graven.modules.impl.render.GameAnimation;
import tech.hakuri.graven.modules.impl.render.NoRender;
import tech.hakuri.graven.modules.impl.render.StreamerMode;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class MixinGui {

    @Unique
    private int graven$scoreboardEntryIndex;

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void onExtractEffects(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        NoRender noRender = NoRender.INSTANCE;
        if (noRender.isEnabled() && noRender.potionEffects.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        EventBus.INSTANCE.post(new Render2DEvent.HUD(graphics));
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"))
    private void resetScoreboardEntryIndex(GuiGraphicsExtractor graphics, net.minecraft.world.scores.Objective objective,
                                           CallbackInfo ci) {
        graven$scoreboardEntryIndex = 0;
    }

    @WrapOperation(
            method = "displayScoreboardSidebar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
                    ordinal = 1
            )
    )
    private void filterScoreboardEntry(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y,
                                       int color, boolean dropShadow, Operation<Void> original) {
        Component filtered = StreamerMode.INSTANCE.filterScoreboardEntry(text, graven$scoreboardEntryIndex++);
        original.call(graphics, font, filtered, x, y, color, dropShadow);
    }

    @ModifyArg(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 1), index = 2)
    private int modifyHotbarSelectionX(int x) {
        return GameAnimation.INSTANCE.getHotbarSelectionX(x);
    }

    @ModifyExpressionValue(method = "extractCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
    private boolean alwaysRenderCrosshairInFreecam(boolean firstPerson) {
        return FreeCamera.INSTANCE.isEnabled() || firstPerson;
    }

}
