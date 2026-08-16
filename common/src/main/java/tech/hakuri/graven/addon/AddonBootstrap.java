package tech.hakuri.graven.addon;

import tech.hakuri.graven.holders.AddonHolder;

/**
 * Shared addon bootstrap utility used by multiple loaders.
 */
public class AddonBootstrap {

    private AddonBootstrap() {
    }

    public static void registerAddons(GravenAddonSetupEvent addonEvent) {
        if (addonEvent != null) {
            registerAddons(addonEvent.getAddons());
        }
    }

    public static void registerAddons(Iterable<GravenAddon> addons) {
        AddonHolder.INSTANCE.registerAddons(addons);
    }

    public static void setupAddons(GravenAddonSetupEvent addonEvent) {
        registerAddons(addonEvent);
        AddonHolder.INSTANCE.setupAddons();
    }

    public static void setupAddons(Iterable<GravenAddon> addons) {
        registerAddons(addons);
        AddonHolder.INSTANCE.setupAddons();
    }

}
