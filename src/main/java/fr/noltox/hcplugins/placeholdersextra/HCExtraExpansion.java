package fr.noltox.hcplugins.placeholdersextra;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The only PlaceholderAPI expansion registered by HeavenCube plugins.
 */
final class HCExtraExpansion extends PlaceholderExpansion implements Relational {

    private static final String IDENTIFIER = "hcextra";
    private static final List<String> PLACEHOLDERS = List.of(
            "%hcextra_luckperms_count_bukkit.command.help%",
            "%hcextra_checkitem_mat:STONE%",
            "%hcextra_checkitem_mat:STONE,amt:2%",
            "%hcextra_checkitem_amount_mat:STONE%",
            "%hcextra_checkitem_getinfo:mainhand_mat:,amt:,nexo:%",
            "%hcextra_checkitem_give_mat:STONE,amt:2%",
            "%hcextra_checkitem_remove_mat:STONE,amt:2%",
            "%hcextra_checkitem_nexo:custom_sword%",
            "%hcextra_checkitem_amount_nexo:custom_sword%",
            "%hcextra_checkitem_nbtstrings:clé=valeur%",
            "%hcextra_checkitem_componentstrings:minecraft:custom_name=valeur%",
            "%hcextra_glow_color%",
            "%rel_hcextra_voicechat_icon%"
    );

    private final Plugin plugin;
    private final ProviderRegistry registry;

    HCExtraExpansion(Plugin plugin, ProviderRegistry registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public @NotNull String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public int hashCode() {
        // PlaceholderExpansion.equals is final and compares these three metadata fields.
        return Objects.hash(getIdentifier(), getAuthor(), getVersion());
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @NotNull List<String> getPlaceholders() {
        return PLACEHOLDERS;
    }

    @Override
    public @Nullable String onRequest(
            @Nullable OfflinePlayer player,
            @NotNull String parameters
    ) {
        Route route = Route.parse(parameters);
        return route == null ? null : registry.resolve(player, route.domain(), route.placeholder());
    }

    @Override
    public @Nullable String onPlaceholderRequest(
            @Nullable Player viewer,
            @Nullable Player target,
            @NotNull String parameters
    ) {
        Route route = Route.parse(parameters);
        return route == null ? null : registry.resolve(viewer, target, route.domain(), route.placeholder());
    }

    private record Route(String domain, String placeholder) {

        private static Route parse(String parameters) {
            int separator = parameters.indexOf('_');
            if (separator < 1 || separator == parameters.length() - 1) {
                return null;
            }
            return new Route(
                    parameters.substring(0, separator).toLowerCase(Locale.ROOT),
                    parameters.substring(separator + 1)
            );
        }
    }
}
