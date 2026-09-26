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
