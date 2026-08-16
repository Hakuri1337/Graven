package tech.hakuri.graven.fabric.addon;

import tech.hakuri.graven.addon.GravenAddonSetupEvent;
import tech.hakuri.graven.fabric.FabricPlatformAddon;

/**
 * Registers Graven's built-in Fabric addon through Fabric custom entrypoint.
 */
public class FabricSelfAddonEntrypoint implements FabricGravenAddonEntrypoint {

    @Override
    public void registerAddon(GravenAddonSetupEvent event) {
        event.registerAddon(new FabricPlatformAddon());
    }

}

