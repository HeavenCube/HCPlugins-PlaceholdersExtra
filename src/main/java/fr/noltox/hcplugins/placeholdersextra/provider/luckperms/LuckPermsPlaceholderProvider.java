package fr.noltox.hcplugins.placeholdersextra.provider.luckperms;

import fr.noltox.hcplugins.placeholdersextra.api.PlaceholderProvider;
import net.luckperms.api.LuckPerms;
import org.bukkit.OfflinePlayer;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Non-blocking LuckPerms permission count provider for the {@code luckperms} domain.
 */
final class LuckPermsPlaceholderProvider implements PlaceholderProvider, AutoCloseable {

    private static final String COUNT_PREFIX = "count_";
    private static final String LOADING_VALUE = "Calcul de la statistique…";
    private static final Duration CACHE_TIME_TO_LIVE = Duration.ofSeconds(30);
    private static final int MAXIMUM_CACHE_SIZE = 1_024;
    private static final int MAXIMUM_CONCURRENT_REFRESHES = 4;
    private static final ThreadFactory WORKER_THREAD_FACTORY =
            Thread.ofVirtual().name("hc-placeholders-luckperms-worker-", 0).factory();

    private final Logger logger;
    private final ExecutorService workerExecutor;
    private final AsyncPermissionCountCache countCache;

    LuckPermsPlaceholderProvider(LuckPerms luckPerms, Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
        workerExecutor = Executors.newThreadPerTaskExecutor(WORKER_THREAD_FACTORY);
        LuckPermsPermissionCounter permissionCounter = new LuckPermsPermissionCounter(
                Objects.requireNonNull(luckPerms, "luckPerms"),
                workerExecutor
        );
        try {
            countCache = new AsyncPermissionCountCache(
                    CACHE_TIME_TO_LIVE,
                    MAXIMUM_CACHE_SIZE,
                    MAXIMUM_CONCURRENT_REFRESHES,
                    permissionCounter::count,
                    this::logFailure
            );
        } catch (RuntimeException exception) {
            workerExecutor.shutdownNow();
            throw exception;
        }
    }

    @Override
    public String resolve(OfflinePlayer ignoredPlayer, String placeholder) {
        if (!placeholder.regionMatches(true, 0, COUNT_PREFIX, 0, COUNT_PREFIX.length())) {
            return null;
        }

        PermissionQuery query = PermissionQuery.parse(placeholder.substring(COUNT_PREFIX.length()))
                .orElse(null);
        if (query == null) {
            return null;
        }

        Integer count = countCache.get(query);
        return count == null ? LOADING_VALUE : Integer.toString(count);
    }

    @Override
    public void close() {
        try {
            countCache.close();
        } finally {
            workerExecutor.shutdownNow();
        }
    }

    private void logFailure(PermissionQuery query, Throwable failure) {
        logger.log(Level.SEVERE, failure,
                () -> "Impossible de compter la permission '" + query.permission() + "'.");
    }
}
