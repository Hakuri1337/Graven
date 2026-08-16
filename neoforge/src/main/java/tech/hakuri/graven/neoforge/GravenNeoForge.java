package tech.hakuri.graven.neoforge;

import tech.hakuri.graven.Constants;
import tech.hakuri.graven.graven;
import tech.hakuri.graven.addon.AddonBootstrap;
import tech.hakuri.graven.assets.i18n.LanguageReloadListener;
import tech.hakuri.graven.assets.resources.ResourceLocationUtils;
import tech.hakuri.graven.neoforge.addon.GravenAddonSetupEvent;
import tech.hakuri.graven.neoforge.addon.NeoForgeSelfAddonRegistrar;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public class GravenNeoForge {

    public static void init() {
        NeoForgeSelfAddonRegistrar.register();

        GravenAddonSetupEvent addonEvent = NeoForge.EVENT_BUS.post(new GravenAddonSetupEvent());
        AddonBootstrap.registerAddons(addonEvent.getAddons());

        graven.init();
    }

    @SubscribeEvent
    private static void onResourcesReload(AddClientReloadListenersEvent event) {
        event.addListener(ResourceLocationUtils.getIdentifier("objects/reload_listener"), new LanguageReloadListener());
    }

}
