package fr.noltox.hcplugins.placeholdersextra.provider.luckperms;

import java.time.Duration;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Cache asynchrone non bloquant avec stratégie « stale-while-revalidate ».
 */
final class AsyncPermissionCountCache implements AutoCloseable {

    private final ConcurrentMap<PermissionQuery, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentMap<PermissionQuery, Boolean> refreshes = new ConcurrentHashMap<>();
    private final ConcurrentMap<PermissionQuery, CompletableFuture<Integer>> runningRefreshes =
            new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<PermissionQuery> refreshQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean draining = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Semaphore refreshPermits;
    private final long timeToLiveNanos;
    private final int maximumSize;
    private final Loader loader;
    private final FailureHandler failureHandler;
    private final LongSupplier nanoTime;

    AsyncPermissionCountCache(
            Duration timeToLive,
            int maximumSize,
            int maximumConcurrentRefreshes,
            Loader loader,
            FailureHandler failureHandler
    ) {
        this(
                timeToLive,
                maximumSize,
                maximumConcurrentRefreshes,
                loader,
                failureHandler,
                System::nanoTime
        );
    }

    AsyncPermissionCountCache(
            Duration timeToLive,
            int maximumSize,
            int maximumConcurrentRefreshes,
            Loader loader,
            FailureHandler failureHandler,
            LongSupplier nanoTime
    ) {
        Objects.requireNonNull(timeToLive, "timeToLive");
        if (timeToLive.isZero() || timeToLive.isNegative()) {
            throw new IllegalArgumentException("timeToLive must be positive");
        }
        if (maximumSize < 1) {
            throw new IllegalArgumentException("maximumSize must be positive");
        }
        if (maximumConcurrentRefreshes < 1) {
            throw new IllegalArgumentException("maximumConcurrentRefreshes must be positive");
        }

        this.timeToLiveNanos = timeToLive.toNanos();
        this.maximumSize = maximumSize;
        this.refreshPermits = new Semaphore(maximumConcurrentRefreshes);
        this.loader = Objects.requireNonNull(loader, "loader");
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    private static Throwable unwrap(Throwable failure) {
        var current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * Renvoie immédiatement la dernière valeur publiée et tente de programmer
     * un rafraîchissement si nécessaire. Une file saturée reporte la tentative
     * au prochain appel.
     *
     * @param query requête normalisée
     * @return valeur publiée, ou {@code null} tant que le premier calcul n'a pas abouti
     */
    Integer get(PermissionQuery query) {
        Objects.requireNonNull(query, "query");

        if (closed.get()) {
            return null;
        }

        var now = nanoTime.getAsLong();
        var snapshot = snapshots.get(query);
        if (snapshot == null || snapshot.shouldRefresh(now)) {
            refresh(query);
        }

        return snapshot != null && snapshot.hasValue() ? snapshot.count() : null;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        refreshQueue.clear();
        refreshes.clear();
        runningRefreshes.values().forEach(refresh -> refresh.cancel(true));
        runningRefreshes.clear();
        snapshots.clear();
    }

    private void refresh(PermissionQuery query) {
        if (closed.get()) {
            return;
        }
        if (refreshes.putIfAbsent(query, Boolean.TRUE) != null) {
            return;
        }
        if (closed.get()) {
            refreshes.remove(query);
            return;
        }
        if (!snapshots.containsKey(query) && refreshes.size() > maximumSize) {
            refreshes.remove(query);
            return;
        }

        refreshQueue.add(query);
        if (closed.get()) {
            refreshQueue.remove(query);
            refreshes.remove(query);
            return;
        }
        drainRefreshQueue();
    }

    private void drainRefreshQueue() {
        if (closed.get()) {
            return;
        }
        if (!draining.compareAndSet(false, true)) {
            return;
        }

        while (true) {
            try {
                while (!closed.get() && refreshPermits.tryAcquire()) {
                    var query = refreshQueue.poll();
                    if (query == null) {
                        refreshPermits.release();
                        break;
                    }
                    startRefresh(query);
                }
            } finally {
                draining.set(false);
            }

            if (closed.get()
                    || refreshQueue.isEmpty()
                    || refreshPermits.availablePermits() == 0
                    || !draining.compareAndSet(false, true)) {
                return;
            }
        }
    }

    private void startRefresh(PermissionQuery query) {
        if (closed.get()) {
            finishRefresh(query);
            return;
        }

        CompletableFuture<Integer> refresh;
        try {
            CompletionStage<Integer> stage =
                    Objects.requireNonNull(loader.load(query), "loader result");
            refresh = stage.toCompletableFuture();
        } catch (RuntimeException failure) {
            completeFailure(query, failure);
            return;
        }

        runningRefreshes.put(query, refresh);
        if (closed.get()) {
            refresh.cancel(true);
        }
        refresh.whenComplete((count, failure) -> {
            if (failure == null) {
                completeSuccess(query, count);
            } else {
                completeFailure(query, failure);
            }
        });
    }

    private void completeSuccess(PermissionQuery query, Integer count) {
        try {
            if (closed.get()) {
                return;
            }
            if (count == null || count < 0) {
                throw new IllegalArgumentException("The permission count must be non-negative");
            }

            store(query, Snapshot.withValue(count, nextRefreshTime()));
        } catch (RuntimeException failure) {
            if (!closed.get()) {
                recordFailure(query, failure);
            }
        } finally {
            finishRefresh(query);
        }
    }

    private void completeFailure(PermissionQuery query, Throwable failure) {
        try {
            if (!closed.get()) {
                recordFailure(query, failure);
            }
        } finally {
            finishRefresh(query);
        }
    }

    private void recordFailure(PermissionQuery query, Throwable failure) {
        if (closed.get()) {
            return;
        }

        var previous = snapshots.get(query);
        var retryAt = nextRefreshTime();
        store(
                query,
                previous == null
                        ? Snapshot.withoutValue(retryAt)
                        : previous.retryAt(retryAt)
        );

        if (closed.get()) {
            return;
        }

        try {
            failureHandler.onFailure(query, unwrap(failure));
        } catch (RuntimeException ignored) {
            // Une défaillance du journal ne doit pas bloquer les rafraîchissements suivants.
        }
    }

    private void finishRefresh(PermissionQuery query) {
        runningRefreshes.remove(query);
        refreshes.remove(query);
        refreshPermits.release();
        drainRefreshQueue();
    }

    private void store(PermissionQuery query, Snapshot snapshot) {
        if (closed.get()) {
            return;
        }

        snapshots.put(query, snapshot);
        if (closed.get()) {
            snapshots.remove(query, snapshot);
            return;
        }

        while (snapshots.size() > maximumSize) {
            var oldest = snapshots.entrySet().stream()
                    .min(Comparator.comparingLong(entry -> entry.getValue().refreshAtNanos()))
                    .orElse(null);
            if (oldest == null) {
                return;
            }
            snapshots.remove(oldest.getKey(), oldest.getValue());
        }
    }

    private long nextRefreshTime() {
        return nanoTime.getAsLong() + timeToLiveNanos;
    }

    @FunctionalInterface
    interface Loader {

        CompletionStage<Integer> load(PermissionQuery query);
    }

    @FunctionalInterface
    interface FailureHandler {

        void onFailure(PermissionQuery query, Throwable failure);
    }

    private record Snapshot(int count, boolean hasValue, long refreshAtNanos) {

        static Snapshot withValue(int count, long refreshAtNanos) {
            return new Snapshot(count, true, refreshAtNanos);
        }

        static Snapshot withoutValue(long refreshAtNanos) {
            return new Snapshot(0, false, refreshAtNanos);
        }

        boolean shouldRefresh(long now) {
            return now - refreshAtNanos >= 0;
        }

        Snapshot retryAt(long retryAtNanos) {
            return new Snapshot(count, hasValue, retryAtNanos);
        }
    }
}
