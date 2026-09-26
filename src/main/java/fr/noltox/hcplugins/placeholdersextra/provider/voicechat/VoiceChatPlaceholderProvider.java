package fr.noltox.hcplugins.placeholdersextra.provider.voicechat;

import fr.noltox.hcplugins.placeholdersextra.api.PlaceholderProvider;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Relational provider exposing Simple Voice Chat activity icons.
 */
final class VoiceChatPlaceholderProvider implements PlaceholderProvider {

    private final VoiceChatActivityState activityState;

    VoiceChatPlaceholderProvider(VoiceChatActivityState activityState) {
        this.activityState = Objects.requireNonNull(activityState, "activityState");
    }

    @Override
    public String resolve(Player viewer, Player target, String placeholder) {
        if (!"icon".equalsIgnoreCase(placeholder)) {
            return null;
        }
        if (viewer == null || target == null) {
            return "";
        }
        return activityState.resolveIcon(viewer.getUniqueId(), target.getUniqueId());
    }
}
