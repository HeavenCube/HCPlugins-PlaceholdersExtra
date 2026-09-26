package fr.noltox.hcplugins.placeholdersextra.provider.voicechat;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.events.PlayerStateChangedEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Instantanés immuables de l'état vocal, lisibles depuis n'importe quel thread.
 */
final class VoiceChatActivityState {

    private static final long SPEAKING_TIMEOUT_MILLIS = 250L;
    private static final long SPEAKING_TIMEOUT_NANOS =
            TimeUnit.MILLISECONDS.toNanos(SPEAKING_TIMEOUT_MILLIS);

    private static final String SPEAKING_ICON =
            " <sprite:\"minecraft:gui\":\"voicechat:icons/speaker\">";
    private static final String WHISPERING_ICON =
            " <sprite:\"minecraft:gui\":\"voicechat:icons/speaker_whisper\">";
    private static final String DISABLED_ICON =
            " <sprite:\"minecraft:gui\":\"voicechat:icons/speaker_off\">";
    private static final String DISCONNECTED_ICON =
            " <sprite:\"minecraft:gui\":\"voicechat:icons/disconnected\">";

    private final ConcurrentMap<UUID, ConnectionState> connections;
    private final ConcurrentMap<UUID, SpeakingState> speakingPlayers;

    VoiceChatActivityState() {
        connections = new ConcurrentHashMap<>();
        speakingPlayers = new ConcurrentHashMap<>();
    }

    void recordPacket(VoicechatConnection connection, boolean whispering) {
        Objects.requireNonNull(connection, "connection");

        UUID playerUuid = connection.getPlayer().getUuid();
        ConnectionState connectionState = ConnectionState.from(connection);
        connections.put(playerUuid, connectionState);

        if (!connectionState.installed()
                || !connectionState.connected()
                || connectionState.disabled()) {
            speakingPlayers.remove(playerUuid);
            return;
        }

        speakingPlayers.put(
                playerUuid,
                new SpeakingState(System.nanoTime(), whispering)
        );
    }

    void updateConnection(VoicechatConnection connection) {
        Objects.requireNonNull(connection, "connection");

        UUID playerUuid = connection.getPlayer().getUuid();
        ConnectionState connectionState = ConnectionState.from(connection);
        connections.put(playerUuid, connectionState);
        clearSpeakingIfUnavailable(playerUuid, connectionState);
    }

    void updateState(PlayerStateChangedEvent event) {
        Objects.requireNonNull(event, "event");

        UUID playerUuid = event.getPlayerUuid();
        var connection = event.getConnection();
        ConnectionState previousState = connections.get(playerUuid);
        boolean installed = connection != null
                ? connection.isInstalled()
                : previousState != null && previousState.installed();
        var connectionState = new ConnectionState(
                installed,
                !event.isDisconnected(),
                event.isDisabled()
        );

        connections.put(playerUuid, connectionState);
        clearSpeakingIfUnavailable(playerUuid, connectionState);
    }

    void markDisconnected(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");

        connections.compute(playerUuid, (ignoredUuid, previousState) -> {
            if (previousState == null) {
                return new ConnectionState(false, false, false);
            }
            return new ConnectionState(
                    previousState.installed(),
                    false,
                    previousState.disabled()
            );
        });
        speakingPlayers.remove(playerUuid);
    }

    String resolveIcon(UUID viewerUuid, UUID targetUuid) {
        Objects.requireNonNull(viewerUuid, "viewerUuid");
        Objects.requireNonNull(targetUuid, "targetUuid");

        ConnectionState viewerState = connections.get(viewerUuid);
        if (viewerState == null || !viewerState.installed()) {
            return "";
        }

        ConnectionState targetState = connections.get(targetUuid);
        if (targetState == null
                || !targetState.installed()
                || !targetState.connected()) {
            return DISCONNECTED_ICON;
        }
        if (targetState.disabled()) {
            return DISABLED_ICON;
        }

        SpeakingState speakingState = speakingPlayers.get(targetUuid);
        if (speakingState == null) {
            return "";
        }

        long elapsedNanos = System.nanoTime() - speakingState.lastPacketNanos();
        if (elapsedNanos < 0L || elapsedNanos > SPEAKING_TIMEOUT_NANOS) {
            speakingPlayers.remove(targetUuid, speakingState);
            return "";
        }

        return speakingState.whispering() ? WHISPERING_ICON : SPEAKING_ICON;
    }

    void remove(UUID playerUuid) {
        connections.remove(playerUuid);
        speakingPlayers.remove(playerUuid);
    }

    void clear() {
        connections.clear();
        speakingPlayers.clear();
    }

    private void clearSpeakingIfUnavailable(
            UUID playerUuid,
            ConnectionState connectionState
    ) {
        if (!connectionState.installed()
                || !connectionState.connected()
                || connectionState.disabled()) {
            speakingPlayers.remove(playerUuid);
        }
    }

    private record ConnectionState(
            boolean installed,
            boolean connected,
            boolean disabled
    ) {

        private static ConnectionState from(VoicechatConnection connection) {
            return new ConnectionState(
                    connection.isInstalled(),
                    connection.isConnected(),
                    connection.isDisabled()
            );
        }
    }

    private record SpeakingState(long lastPacketNanos, boolean whispering) {
    }
}
