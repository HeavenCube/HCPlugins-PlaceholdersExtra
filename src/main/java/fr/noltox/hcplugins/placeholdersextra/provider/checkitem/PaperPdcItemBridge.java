package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;

final class PaperPdcItemBridge implements ItemDataBridge {

    private final Plugin plugin;

    PaperPdcItemBridge(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean matches(ItemStack item, List<PdcCriterion> criteria) {
        if (criteria.isEmpty()) {
            return true;
        }
        if (item == null || item.isEmpty() || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return matches(item.getItemMeta(), criteria);
    }

    @Override
    public boolean matches(ItemMeta meta, List<PdcCriterion> criteria) {
        return ItemMatcher.checkPdc(meta, criteria);
    }

    @Override
    public void apply(ItemMeta meta, List<PdcCriterion> criteria) {
        ItemMatcher.applyPdc(meta, criteria);
    }

    @Override
    public String read(ItemStack item, ItemMeta meta, InfoRequest request) {
        if (meta == null) {
            return "";
        }
        NamespacedKey key = parseKey(request.argument());
        if (key == null) {
            return "";
        }
        PdcCriterion.PdcType type = switch (request.kind()) {
            case PDC_STRING, CUSTOM_DATA_STRING, COMPONENT_STRING -> PdcCriterion.PdcType.STRING;
            case PDC_INTEGER, CUSTOM_DATA_INTEGER, COMPONENT_INTEGER -> PdcCriterion.PdcType.INTEGER;
            case PDC_BYTE -> PdcCriterion.PdcType.BYTE;
            case PDC_DOUBLE -> PdcCriterion.PdcType.DOUBLE;
            case PDC_BOOLEAN -> PdcCriterion.PdcType.BOOLEAN;
            default -> PdcCriterion.PdcType.ANY;
        };
        return ItemMatcher.readPdc(meta, key, type);
    }

    private NamespacedKey parseKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return plugin != null
                ? NamespacedKey.fromString(key.toLowerCase(Locale.ROOT), plugin)
                : NamespacedKey.fromString(key.toLowerCase(Locale.ROOT));
    }
}
