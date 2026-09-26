package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import fr.noltox.hcplugins.placeholdersextra.api.PlaceholderProvider;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Objects;

final class CheckItemProvider implements PlaceholderProvider {

    private final CheckItemParser parser;
    private final CheckItemService service;

    CheckItemProvider(
            CheckItemParser parser,
            CheckItemService service
    ) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public String resolve(OfflinePlayer offlinePlayer, String placeholder) {
        if (!Bukkit.isPrimaryThread()) {
            return null;
        }
        if (!(offlinePlayer instanceof Player player) || !player.isOnline()) {
            return null;
        }
        try {
            CheckItemQuery query = parser.parse(
                    placeholder,
                    value -> PlaceholderAPI.setBracketPlaceholders(player, value)
            );
            return service.execute(player.getInventory(), query);
        } catch (CheckItemParseException exception) {
            return null;
        }
    }
}
