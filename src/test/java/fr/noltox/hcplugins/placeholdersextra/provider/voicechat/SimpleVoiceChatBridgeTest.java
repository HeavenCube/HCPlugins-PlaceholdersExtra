package fr.noltox.hcplugins.placeholdersextra.provider.voicechat;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.events.Event;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.PlayerStateChangedEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleVoiceChatBridgeTest {

    @Test
    void closeWaitsForInFlightUpdateAndRejectsLateCallbacks() throws Exception {
        var state = new VoiceChatActivityState();
        var bridge = new SimpleVoiceChatBridge(state);
        var callback = new AtomicReference<Consumer<PlayerStateChangedEvent>>();
        bridge.registerEvents(new EventRegistration() {
            @Override
            public <T extends Event> void registerEvent(Class<T> type, Consumer<T> handler, int priority) {
                if (type == PlayerStateChangedEvent.class) {
                    callback.set(event -> handler.accept(type.cast(event)));
                }
            }
        });

        UUID player = UUID.randomUUID();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        VoicechatConnection connection = (VoicechatConnection) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{VoicechatConnection.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("isInstalled")) {
                        return true;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        PlayerStateChangedEvent event = (PlayerStateChangedEvent) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{PlayerStateChangedEvent.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getPlayerUuid" -> player;
                    case "isDisconnected" -> false;
                    case "isDisabled" -> true;
                    case "getConnection" -> {
                        entered.countDown();
                        if (!release.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Le callback n'a pas été libéré.");
                        }
                        yield connection;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var update = executor.submit(() -> callback.get().accept(event));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var closing = new CountDownLatch(1);
                var close = executor.submit(() -> {
                    closing.countDown();
                    bridge.close();
                });
                assertTrue(closing.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> close.get(100, TimeUnit.MILLISECONDS));
                release.countDown();
                update.get(5, TimeUnit.SECONDS);
                close.get(5, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
        assertEquals("", state.resolveIcon(player, player));
        callback.get().accept(event);
        assertEquals("", state.resolveIcon(player, player));
    }
}
