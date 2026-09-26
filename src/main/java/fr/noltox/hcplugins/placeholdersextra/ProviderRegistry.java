package fr.noltox.hcplugins.placeholdersextra;

import fr.noltox.hcplugins.placeholdersextra.api.PlaceholderProvider;
import fr.noltox.hcplugins.placeholdersextra.api.PlaceholderProviderRegistration;
import fr.noltox.hcplugins.placeholdersextra.api.PlaceholderProviderRegistry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Thread-safe registry with immutable snapshots for PlaceholderAPI reads.
 */
final class ProviderRegistry implements PlaceholderProviderRegistry, AutoCloseable {

    private static final Pattern DOMAIN_PATTERN = Pattern.compile("[a-z][a-z0-9-]*");

    private final Logger logger;
    private final Map<String, Registration> registrations = new HashMap<>();
    private volatile Map<String, Registration> snapshot = Map.of();
    private boolean closed;

    ProviderRegistry(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    private static void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Les providers de placeholders doivent être modifiés sur le thread serveur.");
        }
    }

    @Override
    public synchronized PlaceholderProviderRegistration register(
            Plugin owner,
            String domain,
            PlaceholderProvider provider
    ) {
        requirePrimaryThread();
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(provider, "provider");
        if (closed) {
            throw new IllegalStateException("Le registre de placeholders est fermé.");
        }

        String normalizedDomain = domain.toLowerCase(Locale.ROOT);
        if (!domain.equals(normalizedDomain) || !DOMAIN_PATTERN.matcher(domain).matches()) {
            throw new IllegalArgumentException(
                    "Le domaine de placeholder doit respecter [a-z][a-z0-9-]* : " + domain
            );
        }
        if (registrations.containsKey(domain)) {
            throw new IllegalStateException("Le domaine de placeholder '" + domain + "' est déjà enregistré.");
        }

        Registration registration = new Registration(this, owner, domain, provider);
        registrations.put(domain, registration);
        publishSnapshot();
        return registration;
    }

    String resolve(OfflinePlayer player, String domain, String placeholder) {
        Registration registration = snapshot.get(domain);
        if (registration == null) {
            return null;
        }
        try {
            return registration.provider().resolve(player, placeholder);
        } catch (RuntimeException exception) {
            logProviderFailure(registration, placeholder, exception);
            return null;
        }
    }

    String resolve(Player viewer, Player target, String domain, String placeholder) {
        Registration registration = snapshot.get(domain);
        if (registration == null) {
            return null;
        }
        try {
            return registration.provider().resolve(viewer, target, placeholder);
        } catch (RuntimeException exception) {
            logProviderFailure(registration, placeholder, exception);
            return null;
        }
    }

    synchronized void unregisterAll(Plugin owner) {
        requirePrimaryThread();
        boolean changed = registrations.values().removeIf(registration -> {
            if (registration.owner() != owner) {
                return false;
            }
            registration.markClosed();
            return true;
        });
        if (changed) {
            publishSnapshot();
        }
    }

    @Override
    public synchronized void close() {
        requirePrimaryThread();
        if (closed) {
            return;
        }
        closed = true;
        registrations.values().forEach(Registration::markClosed);
        registrations.clear();
        snapshot = Map.of();
    }

    private synchronized void unregister(Registration registration) {
        requirePrimaryThread();
        if (registrations.remove(registration.domain(), registration)) {
            publishSnapshot();
        }
        registration.markClosed();
    }

    private void publishSnapshot() {
        snapshot = Map.copyOf(registrations);
    }

    private void logProviderFailure(
            Registration registration,
            String placeholder,
            RuntimeException exception
    ) {
        logger.log(Level.WARNING, exception,
                () -> "Le provider '" + registration.domain() + "' de "
                        + registration.owner().getName()
                        + " a échoué pour le placeholder '" + placeholder + "'.");
    }

    private static final class Registration implements PlaceholderProviderRegistration {

        private final ProviderRegistry registry;
        private final Plugin owner;
        private final String domain;
        private final PlaceholderProvider provider;
        private volatile boolean registered = true;

        private Registration(
                ProviderRegistry registry,
                Plugin owner,
                String domain,
                PlaceholderProvider provider
        ) {
            this.registry = registry;
            this.owner = owner;
            this.domain = domain;
            this.provider = provider;
        }

        @Override
        public boolean isRegistered() {
            return registered;
        }

        @Override
        public void close() {
            registry.unregister(this);
        }

        private Plugin owner() {
            return owner;
        }

        private String domain() {
            return domain;
        }

        private PlaceholderProvider provider() {
            return provider;
        }

        private void markClosed() {
            registered = false;
        }
    }
}
