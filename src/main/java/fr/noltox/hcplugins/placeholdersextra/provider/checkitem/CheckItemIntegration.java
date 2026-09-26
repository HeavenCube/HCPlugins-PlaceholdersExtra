package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import fr.noltox.hcplugins.placeholdersextra.api.PlaceholderProviderRegistration;
import fr.noltox.hcplugins.placeholdersextra.api.PlaceholderProviderRegistry;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * Owns the internal CheckItem provider and its optional Nexo bridge.
 */
public final class CheckItemIntegration implements AutoCloseable {

    private final NexoBridgeState nexoBridge;
    private final PlaceholderProviderRegistration registration;

    private CheckItemIntegration(
            NexoBridgeState nexoBridge,
            PlaceholderProviderRegistration registration
    ) {
        this.nexoBridge = nexoBridge;
        this.registration = registration;
    }

    public static CheckItemIntegration start(
            JavaPlugin plugin,
            PlaceholderProviderRegistry registry
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(registry, "registry");
        var nexoBridge = new NexoBridgeState();
        var itemData = new NbtApiItemDataBridge();
        var provider = new CheckItemProvider(
                new CheckItemParser(),
                new CheckItemService(itemData, nexoBridge)
        );
        var registration = registry.register(plugin, "checkitem", provider);
        return new CheckItemIntegration(nexoBridge, registration);
    }

    public void enableNexo() {
        nexoBridge.activate(new NexoApiItemBridge());
    }

    public void disableNexo() {
        nexoBridge.deactivate();
    }

    @Override
    public void close() {
        nexoBridge.deactivate();
        registration.close();
    }
}
