package tech.hakuri.graven.fabric;

import tech.hakuri.graven.Constants;
import tech.hakuri.graven.graven;
import tech.hakuri.graven.addon.AddonBootstrap;
import tech.hakuri.graven.addon.GravenAddonSetupEvent;
import tech.hakuri.graven.assets.i18n.LanguageReloadListener;
import tech.hakuri.graven.assets.resources.ResourceLocationUtils;
import tech.hakuri.graven.fabric.addon.FabricGravenAddonEntrypoint;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.server.packs.PackType;

public class GravenFabric implements ClientModInitializer {

    public static final String ADDON_ENTRYPOINT_KEY = "graven:addon";

    @Override
    public void onInitializeClient() {
        GravenAddonSetupEvent addonEvent = new GravenAddonSetupEvent();
        for (EntrypointContainer<FabricGravenAddonEntrypoint> container : FabricLoader.getInstance().getEntrypointContainers(ADDON_ENTRYPOINT_KEY, FabricGravenAddonEntrypoint.class)) {
            String providerId = container.getProvider().getMetadata().getId();
            try {
                FabricGravenAddonEntrypoint entrypoint = container.getEntrypoint();
                entrypoint.registerAddon(addonEvent);
            } catch (Throwable t) {
                Constants.LOGGER.error("Failed to register addon entrypoint from mod: {}", providerId, t);
            }
        }
        AddonBootstrap.registerAddons(addonEvent);

        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                ResourceLocationUtils.getIdentifier("objects/reload_listener"),
                new LanguageReloadListener()
        );
        // 必须在客户端第一次资源重载前注册；否则 graven.init() 读取到空资源管理器，
        // 初次进入 GUI 时所有自定义翻译都会退回到原始 key。
        graven.init();
    }

}
