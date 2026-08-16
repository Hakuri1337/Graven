package tech.hakuri.graven.mixins;

import tech.hakuri.graven.graven;
import tech.hakuri.graven.modules.impl.ClientSetting;
import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Mixin(Window.class)
public class MixinWindow {

    @Redirect(method = "setIcon", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/IconSet;getStandardIcons(Lnet/minecraft/server/packs/PackResources;)Ljava/util/List;"))
    private List<IoSupplier<InputStream>> onSetIcon(IconSet instance, PackResources resources) throws IOException {
        final InputStream graven_16x16 = graven.class.getResourceAsStream("/assets/graven/textures/icons/icon_16x16.png");
        final InputStream graven_32x32 = graven.class.getResourceAsStream("/assets/graven/textures/icons/icon_32x32.png");
        final InputStream table_16x16 = graven.class.getResourceAsStream("/assets/graven/textures/icons/table_16x16.png");
        final InputStream table_32x32 = graven.class.getResourceAsStream("/assets/graven/textures/icons/table_32x32.png");

        if (ClientSetting.INSTANCE.customIcon.is(ClientSetting.IconMode.Graven)) {
            if (graven_16x16 != null && graven_32x32 != null) {
                return List.of(() -> graven_16x16, () -> graven_32x32);
            }
        } else if (ClientSetting.INSTANCE.customIcon.is(ClientSetting.IconMode.Minecraft_1_8_9)) {
            if (table_16x16 != null && table_32x32 != null) {
                return List.of(() -> table_16x16, () -> table_32x32);
            }
        }

        return instance.getStandardIcons(resources);
    }

}
