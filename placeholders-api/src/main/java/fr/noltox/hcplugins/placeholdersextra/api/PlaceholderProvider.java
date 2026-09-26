package fr.noltox.hcplugins.placeholdersextra.api;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** Supplies one HeavenCube placeholder domain to HCPlaceholdersExtra. */
public interface PlaceholderProvider {

    /** Resolves a non-relational placeholder, or {@code null} when it is unknown. */
    default @Nullable String resolve(@Nullable OfflinePlayer player, String placeholder) {
        return null;
    }

    /** Resolves a relational placeholder, or {@code null} when it is unknown. */
    default @Nullable String resolve(@Nullable Player viewer, @Nullable Player target, String placeholder) {
        return null;
    }
}
