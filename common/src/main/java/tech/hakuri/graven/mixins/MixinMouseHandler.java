package tech.hakuri.graven.mixins;

import tech.hakuri.graven.events.bus.EventBus;
import tech.hakuri.graven.events.impl.MousePressEvent;
import tech.hakuri.graven.events.impl.MouseScrollEvent;
import tech.hakuri.graven.events.impl.MouseTurnEvent;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MixinMouseHandler {

    @ModifyArgs(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void onTurnPlayer(Args args) {
        MouseTurnEvent event = EventBus.INSTANCE.post(new MouseTurnEvent(args.get(0), args.get(1)));
        args.set(0, event.getX());
        args.set(1, event.getY());
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onButton(long handle, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
        MousePressEvent event = EventBus.INSTANCE.post(new MousePressEvent(rawButtonInfo.button(), action, rawButtonInfo.modifiers()));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        MouseScrollEvent event = EventBus.INSTANCE.post(new MouseScrollEvent(yoffset));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

}
