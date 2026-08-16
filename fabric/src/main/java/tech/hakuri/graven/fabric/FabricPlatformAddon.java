package tech.hakuri.graven.fabric;

import tech.hakuri.graven.Constants;
import tech.hakuri.graven.addon.GravenAddon;

import java.util.List;

/**
 * Built-in Fabric addon for Fabric-only features.
 */
public class FabricPlatformAddon extends GravenAddon {

    public FabricPlatformAddon() {
        super("graven_fabric");
    }

    @Override
    public void onSetup() {
        Constants.LOGGER.info("Fabric platform addon initialized.");
    }

    @Override
    public String getDisplayName() {
        return "Fabric Platform";
    }

    @Override
    public String getDescription() {
        return "Built-in addon for Fabric-specific integrations.";
    }

    @Override
    public String getVersion() {
        return Constants.VERSION;
    }

    @Override
    public List<String> getAuthors() {
        return List.of("slmpc", "06789");
    }

}
