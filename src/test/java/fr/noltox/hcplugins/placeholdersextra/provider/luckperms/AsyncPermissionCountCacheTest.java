package fr.noltox.hcplugins.placeholdersextra.provider.luckperms;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("java:S5960")
class AsyncPermissionCountCacheTest {

    @Test
    void cachedStaleQueriesCannotBypassPendingRefreshLimit() {
        var clock = new AtomicLong();
        var loads = new ArrayList<CompletableFuture<Integer>>();
        PermissionQuery third = new PermissionQuery("test.third", List.of());
        try (var cache = cache(clock, loads)) {
            cache.get(FIRST);
            loads.getFirst().complete(1);
            clock.set(1);
            cache.get(SECOND);
            loads.getLast().complete(2);
            clock.set(11);
            cache.get(third); // Running.
            cache.get(FIRST); // Queued: the two pending slots are now reserved.
            cache.get(SECOND); // Stale value remains available, refresh must wait.
            assertEquals(3, loads.size());
            loads.getLast().complete(3);
            assertEquals(4, loads.size());
            loads.getLast().complete(4);
            assertEquals(4, loads.size());
            cache.get(SECOND); // A caller retries once there is capacity.
            assertEquals(5, loads.size());
        }
    }

    @Test
    void simultaneousMissesStartOnlyOneRefresh() throws Exception {
        var loads = new java.util.concurrent.atomic.AtomicInteger();
        var storage = new CompletableFuture<Integer>();
        var go = new java.util.concurrent.CountDownLatch(1);
        try (var cache = new AsyncPermissionCountCache(Duration.ofSeconds(30), 2, 1,
                query -> {
                    loads.incrementAndGet();
                    return storage;
                }, (query, failure) -> fail(failure));
             var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var calls = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 64; i++) {
                calls.add(executor.submit(() -> {
                    go.await();
                    cache.get(FIRST);
                    return null;
                }));
            }
            go.countDown();
            for (var call : calls) {
                call.get(3, java.util.concurrent.TimeUnit.SECONDS);
            }
            assertEquals(1, loads.get());
            storage.complete(7);
            assertEquals(7, cache.get(FIRST));
        }
    }

    @Test
    void closeWhileLoaderReturnsCannotPublishOrLeaveRunningWork() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var storage = new CompletableFuture<Integer>();
        var cache = new AsyncPermissionCountCache(Duration.ofSeconds(30), 2, 1, query -> {
            entered.countDown();
            try {
                assertTrue(release.await(3, java.util.concurrent.TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                throw new IllegalStateException(exception);
            }
            return storage;
        }, (query, failure) -> fail(failure));
        Thread request = Thread.ofVirtual().start(() -> cache.get(FIRST));
        try {
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS));
            cache.close();
            release.countDown();
            assertTrue(request.join(Duration.ofSeconds(3)));
            assertTrue(storage.isCancelled());
            assertNull(cache.get(FIRST));
        } finally {
            release.countDown();
            request.interrupt();
            cache.close();
        }
    }

    private static final PermissionQuery FIRST = new PermissionQuery("test.first", List.of());
    private static final PermissionQuery SECOND = new PermissionQuery("test.second", List.of());

    private static AsyncPermissionCountCache cache(
            AtomicLong clock, List<CompletableFuture<Integer>> loads
    ) {
        return new AsyncPermissionCountCache(Duration.ofNanos(10), 2, 1, query -> {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            loads.add(future);
            return future;
        }, (query, failure) -> {
            // Failures are deliberately injected to verify stale-value retention and retry timing.
        }, clock::get);
    }

    @Test
    void coalescesRequestsAndKeepsStaleValueUntilRefreshCompletes() {
        AtomicLong clock = new AtomicLong();
        List<CompletableFuture<Integer>> loads = new ArrayList<>();
        try (AsyncPermissionCountCache cache = cache(clock, loads)) {
            assertNull(cache.get(FIRST));
            assertNull(cache.get(FIRST));
            assertEquals(1, loads.size());
            loads.getFirst().complete(4);
            assertEquals(4, cache.get(FIRST));

            clock.set(10);
            assertEquals(4, cache.get(FIRST));
            assertEquals(4, cache.get(FIRST));
            assertEquals(2, loads.size());
            loads.getLast().complete(7);
            assertEquals(7, cache.get(FIRST));
        }
    }

    @Test
    void failedRefreshRetainsValueAndDefersRetry() {
        AtomicLong clock = new AtomicLong();
        List<CompletableFuture<Integer>> loads = new ArrayList<>();
        try (AsyncPermissionCountCache cache = cache(clock, loads)) {
            assertNull(cache.get(FIRST));
            loads.getFirst().complete(4);
            clock.set(10);
            assertEquals(4, cache.get(FIRST));
            loads.getLast().completeExceptionally(new IllegalStateException("Storage unavailable"));
            clock.set(19);
            assertEquals(4, cache.get(FIRST));
            assertEquals(2, loads.size());
            clock.set(20);
            assertEquals(4, cache.get(FIRST));
            assertEquals(3, loads.size());
        }
    }

    @Test
    void queuesRefreshesWithinConcurrencyLimit() {
        List<CompletableFuture<Integer>> loads = new ArrayList<>();
        try (AsyncPermissionCountCache cache = cache(new AtomicLong(), loads)) {
            assertNull(cache.get(FIRST));
            assertNull(cache.get(SECOND));
            assertEquals(1, loads.size());
            loads.getFirst().complete(4);
            assertEquals(2, loads.size());
            loads.getLast().complete(7);
            assertEquals(4, cache.get(FIRST));
            assertEquals(7, cache.get(SECOND));
        }
    }

    @Test
    void closeCancelsRunningRefreshAndDiscardsQueuedWork() {
        List<CompletableFuture<Integer>> loads = new ArrayList<>();
        AsyncPermissionCountCache cache = cache(new AtomicLong(), loads);
        try (cache) {
            assertNull(cache.get(FIRST));
            assertNull(cache.get(SECOND));
        }
        assertTrue(loads.getFirst().isCancelled());
        assertNull(cache.get(FIRST));
        assertNull(cache.get(SECOND));
        assertEquals(1, loads.size());
    }
}
