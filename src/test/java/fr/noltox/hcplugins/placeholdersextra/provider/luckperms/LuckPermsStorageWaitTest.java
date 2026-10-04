package fr.noltox.hcplugins.placeholdersextra.provider.luckperms;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LuckPermsStorageWaitTest {

    @Test
    void returnsStorageResultAndPreservesFailureCause() {
        assertEquals(7, LuckPermsPermissionCounter.awaitStorage(CompletableFuture.completedFuture(7)));
        var cause = new IllegalStateException("storage offline");
        var failure = assertThrows(CompletionException.class, () -> LuckPermsPermissionCounter.awaitStorage(
                CompletableFuture.failedFuture(cause)));
        assertSame(cause, failure.getCause());
    }

    @Test
    void shutdownInterruptReleasesWorkerWaitingForStalledStorage() throws Exception {
        var started = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        var interrupted = new AtomicBoolean();
        var storage = new CompletableFuture<Integer>();
        Thread worker = Thread.ofVirtual().start(() -> {
            started.countDown();
            try {
                LuckPermsPermissionCounter.awaitStorage(storage);
            } catch (CompletionException exception) {
                failure.set(exception.getCause());
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        try {
            assertTrue(started.await(3, TimeUnit.SECONDS));
            worker.interrupt();
            assertTrue(worker.join(Duration.ofSeconds(3)));
            assertInstanceOf(InterruptedException.class, failure.get());
            assertTrue(interrupted.get());
            // This future belongs to LuckPerms; the plugin does not cancel LP's storage operation.
            assertFalse(storage.isCancelled());
        } finally {
            worker.interrupt();
        }
    }
}
