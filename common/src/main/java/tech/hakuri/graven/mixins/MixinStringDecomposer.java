package tech.hakuri.graven.mixins;

import tech.hakuri.graven.modules.impl.render.StreamerMode;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(StringDecomposer.class)
public final class MixinStringDecomposer {

    @ModifyVariable(
            method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private static String modifyIterateFormatted(String text) {
        StreamerMode streamerMode = StreamerMode.INSTANCE;
        return streamerMode.isEnabled() ? streamerMode.filter(text) : text;
    }
}
