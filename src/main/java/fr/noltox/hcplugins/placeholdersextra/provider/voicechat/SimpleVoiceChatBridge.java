package fr.noltox.hcplugins.placeholdersextra.provider.voicechat;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.*;

import java.util.Objects;

/**
 * Adaptateur enregistré dans le service Bukkit de Simple Voice Chat.
 */
final class SimpleVoiceChatBridge implements VoicechatPlugin {

    private static final String PLUGIN_ID = "hcextra";

    private final VoiceChatActivityState activityState;
    // Serialize the short state updates with close so an in-flight callback cannot
    // repopulate the snapshots after they have been cleared at shutdown.
    private boolean active;

    SimpleVoiceChatBridge(VoiceChatActivityState activityState) {
        this.activityState = Objects.requireNonNull(activityState, "activityState");
        active = true;
    }

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(
                MicrophonePacketEvent.class,
                this::onMicrophonePacket
        );
        registration.registerEvent(
                PlayerConnectedEvent.class,
                this::onPlayerConnected
        );
        registration.registerEvent(
                PlayerDisconnectedEvent.class,
                this::onPlayerDisconnected
        );
        registration.registerEvent(
                PlayerStateChangedEvent.class,
                this::onPlayerStateChanged
        );
        registration.registerEvent(
                VoicechatServerStoppedEvent.class,
                ignoredEvent -> clearState()
        );
    }

    synchronized void close() {
        active = false;
        activityState.clear();
    }

    private synchronized void onMicrophonePacket(MicrophonePacketEvent event) {
        if (!active) {
            return;
        }

        var sender = event.getSenderConnection();
        if (sender != null) {
            activityState.recordPacket(
                    sender,
                    event.getPacket().isWhispering()
            );
        }
    }

    private synchronized void onPlayerConnected(PlayerConnectedEvent event) {
        if (active) {
            activityState.updateConnection(event.getConnection());
        }
    }

    private synchronized void onPlayerDisconnected(PlayerDisconnectedEvent event) {
        if (active) {
            activityState.markDisconnected(event.getPlayerUuid());
        }
    }

    private synchronized void onPlayerStateChanged(PlayerStateChangedEvent event) {
        if (active) {
            activityState.updateState(event);
        }
    }

    private synchronized void clearState() {
        if (active) {
            activityState.clear();
        }
    }
}
