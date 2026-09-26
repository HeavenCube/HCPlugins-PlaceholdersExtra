package fr.noltox.hcplugins.placeholdersextra.api;

import org.bukkit.plugin.Plugin;

import java.util.Objects;

/** Locates the HCPlaceholdersExtra registry required by contributor plugins. */
public final class HCPlaceholders {

    public static final String PLUGIN_NAME = "HCPlaceholdersExtra";

    private HCPlaceholders() {
    }

    public static PlaceholderProviderRegistry require(Plugin contributor) {
        Objects.requireNonNull(contributor, "contributor");
        PlaceholderProviderRegistry registry = contributor.getServer()
                .getServicesManager()
                .load(PlaceholderProviderRegistry.class);
        if (registry == null) {
            throw new IllegalStateException(PLUGIN_NAME
                    + " est requis et doit être activé avant "
                    + contributor.getName() + ".");
        }
        return registry;
    }
}
