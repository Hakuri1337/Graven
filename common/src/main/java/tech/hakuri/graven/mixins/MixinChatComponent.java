package tech.hakuri.graven.mixins;

import tech.hakuri.graven.managers.Managers;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = {
        "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess",
        "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess"
})
public class MixinChatComponent {

    @ModifyVariable(method = "handleMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private FormattedCharSequence sakura$animateClientPrefix(FormattedCharSequence message) {
        return tech.hakuri.graven.utils.asaka.grimvelocity.ChatUtils.applyAnimatedPrefix(
                Managers.NOTIFICATION.applyAnimatedPrefix(message)
        );
    }

}
