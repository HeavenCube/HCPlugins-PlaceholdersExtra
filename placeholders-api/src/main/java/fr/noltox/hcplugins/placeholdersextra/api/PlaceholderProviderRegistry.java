package fr.noltox.hcplugins.placeholdersextra.api;

import org.bukkit.plugin.Plugin;

/** Public service exposed by HCPlaceholdersExtra through Paper's ServicesManager. */
@FunctionalInterface
public interface PlaceholderProviderRegistry {

    PlaceholderProviderRegistration register(
            Plugin owner,
            String domain,
            PlaceholderProvider provider
    );
}
