package tech.hakuri.graven.mixins;

import tech.hakuri.graven.events.bus.EventBus;
import tech.hakuri.graven.events.impl.ChatReceivedEvent;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;

@Mixin(ChatListener.class)
public final class MixinChatListener {

    @Inject(
            method = "showMessageToPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;addPlayerMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
                    ordinal = 0
            ),
            cancellable = true
    )
    private void onUnfilteredPlayerChat(ChatType.Bound boundChatType, PlayerChatMessage message,
                                        Component decoratedMessage, GameProfile sender, boolean onlyShowSecure,
                                        Instant received, CallbackInfoReturnable<Boolean> cir) {
        if (post(decoratedMessage, false)) cir.setReturnValue(false);
    }

    @Inject(
            method = "showMessageToPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;addPlayerMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
                    ordinal = 1
            ),
            cancellable = true
    )
    private void onFilteredPlayerChat(ChatType.Bound boundChatType, PlayerChatMessage message,
                                      Component decoratedMessage, GameProfile sender, boolean onlyShowSecure,
                                      Instant received, CallbackInfoReturnable<Boolean> cir) {
        Component filtered = message.filterMask().applyWithFormatting(message.signedContent());
        if (filtered != null && post(boundChatType.decorate(filtered), false)) cir.setReturnValue(false);
    }

    @Inject(method = "lambda$handleDisguisedChatMessage$0", at = @At("HEAD"), cancellable = true)
    private void onDisguisedChat(ChatType.Bound boundChatType, Component message, Instant received,
                                 CallbackInfoReturnable<Boolean> cir) {
        if (post(boundChatType.decorate(message), false)) cir.setReturnValue(false);
    }

    @Inject(method = "handleSystemMessage", at = @At("HEAD"), cancellable = true)
    private void onSystemChat(Component message, boolean remote, CallbackInfo ci) {
        if (post(message, false)) ci.cancel();
    }

    @Inject(method = "handleOverlay", at = @At("HEAD"), cancellable = true)
    private void onOverlay(Component message, CallbackInfo ci) {
        if (post(message, true)) ci.cancel();
    }

    private static boolean post(Component message, boolean overlay) {
        return EventBus.INSTANCE.post(new ChatReceivedEvent(message, overlay)).isCancelled();
    }
}
