package fr.noltox.hcplugins.placeholdersextra.provider.voicechat;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import fr.noltox.hcplugins.placeholdersextra.api.PlaceholderProviderRegistration;
import fr.noltox.hcplugins.placeholdersextra.api.PlaceholderProviderRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * Owns the optional Simple Voice Chat bridge, state and provider lifecycle.
 */
public final class VoiceChatIntegration implements Listener, AutoCloseable {

    private final VoiceChatActivityState activityState;
    private final SimpleVoiceChatBridge bridge;
    private final PlaceholderProviderRegistration registration;

    private VoiceChatIntegration(
            VoiceChatActivityState activityState,
            SimpleVoiceChatBridge bridge,
            PlaceholderProviderRegistration registration
    ) {
        this.activityState = activityState;
        this.bridge = bridge;
        this.registration = registration;
    }

    public static VoiceChatIntegration start(
            JavaPlugin plugin,
            PlaceholderProviderRegistry registry
    ) {
        BukkitVoicechatService service = Objects.requireNonNull(
                plugin.getServer().getServicesManager().load(BukkitVoicechatService.class),
                "Le service serveur de Simple Voice Chat est indisponible."
        );
        VoiceChatActivityState activityState = new VoiceChatActivityState();
        SimpleVoiceChatBridge bridge = new SimpleVoiceChatBridge(activityState);
        PlaceholderProviderRegistration registration = registry.register(
                plugin,
                "voicechat",
                new VoiceChatPlaceholderProvider(activityState)
        );
        VoiceChatIntegration integration = new VoiceChatIntegration(
                activityState,
                bridge,
                registration
        );
        try {
            plugin.getServer().getPluginManager().registerEvents(integration, plugin);
            service.registerPlugin(bridge);
            return integration;
        } catch (RuntimeException exception) {
            try {
                integration.close();
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    private static RuntimeException cleanup(RuntimeException failure, Runnable action) {
        try {
            action.run();
            return failure;
        } catch (RuntimeException exception) {
            if (failure == null) {
                return exception;
            }
            failure.addSuppressed(exception);
            return failure;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        activityState.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        RuntimeException failure = cleanup(null, () -> HandlerList.unregisterAll(this));
        failure = cleanup(failure, registration::close);
        failure = cleanup(failure, bridge::close);
        if (failure != null) {
            throw failure;
        }
    }
}
