package tech.hakuri.graven.mixins;

import tech.hakuri.graven.gui.screen.MainMenuScreen;
import tech.hakuri.graven.gui.screen.WelcomeScreen;
import tech.hakuri.graven.modules.impl.ClientSetting;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static tech.hakuri.graven.Constants.mc;

@Mixin(TitleScreen.class)
public class MixinTitleScreen {

    @Unique
    private static boolean graven$welcomeHandled;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void onInit(CallbackInfo ci) {
        if (!graven$welcomeHandled) {
            graven$welcomeHandled = true;
            if (ClientSetting.INSTANCE.showWelcomeScreen.getValue()) {
                ci.cancel();
                mc.setScreen(WelcomeScreen.INSTANCE);
                return;
            }
        }

        if (ClientSetting.INSTANCE.useMainMenu.getValue()) {
            ci.cancel();
            mc.setScreen(MainMenuScreen.INSTANCE);
        }
    }

}
