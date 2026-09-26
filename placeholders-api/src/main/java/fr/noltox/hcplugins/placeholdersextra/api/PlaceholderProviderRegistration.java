package fr.noltox.hcplugins.placeholdersextra.api;

/** Idempotent registration handle owned by the contributing plugin. */
public interface PlaceholderProviderRegistration extends AutoCloseable {

    boolean isRegistered();

    @Override
    void close();
}
