package tech.hakuri.graven.fabric.addon;

import tech.hakuri.graven.addon.GravenAddonSetupEvent;

/**
 * Custom Fabric entrypoint contract for Graven addons.
 */
public interface FabricGravenAddonEntrypoint {

    void registerAddon(GravenAddonSetupEvent event);

}

