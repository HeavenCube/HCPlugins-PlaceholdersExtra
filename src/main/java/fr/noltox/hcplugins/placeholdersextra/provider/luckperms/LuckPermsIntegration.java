package fr.noltox.hcplugins.placeholdersextra.provider.luckperms;

import fr.noltox.hcplugins.placeholdersextra.api.PlaceholderProviderRegistration;
import fr.noltox.hcplugins.placeholdersextra.api.PlaceholderProviderRegistry;
import net.luckperms.api.LuckPerms;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * Owns the optional LuckPerms provider lifecycle.
 */
public final class LuckPermsIntegration implements AutoCloseable {

    private final LuckPermsPlaceholderProvider provider;
    private final PlaceholderProviderRegistration registration;

    private LuckPermsIntegration(
            LuckPermsPlaceholderProvider provider,
            PlaceholderProviderRegistration registration
    ) {
        this.provider = provider;
        this.registration = registration;
    }

    public static LuckPermsIntegration start(
            JavaPlugin plugin,
            PlaceholderProviderRegistry registry
    ) {
        LuckPerms luckPerms = Objects.requireNonNull(
                plugin.getServer().getServicesManager().load(LuckPerms.class),
                "Le service LuckPerms est indisponible."
        );
        LuckPermsPlaceholderProvider provider = new LuckPermsPlaceholderProvider(
                luckPerms,
                plugin.getLogger()
        );
        try {
            PlaceholderProviderRegistration registration = registry.register(
                    plugin,
                    "luckperms",
                    provider
            );
            return new LuckPermsIntegration(provider, registration);
        } catch (RuntimeException exception) {
            try {
                provider.close();
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        try {
            registration.close();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            provider.close();
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
