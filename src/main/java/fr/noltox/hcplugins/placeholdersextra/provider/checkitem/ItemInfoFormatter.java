package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

final class ItemInfoFormatter {

    private static final String SEPARATOR = " &r";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ItemDataBridge itemData;
    private final CustomItemBridge customItems;

    ItemInfoFormatter(ItemDataBridge itemData, CustomItemBridge customItems) {
        this.itemData = Objects.requireNonNull(itemData, "itemData");
        this.customItems = Objects.requireNonNull(customItems, "customItems");
    }

    private static String potionType(ItemMeta meta) {
        if (!(meta instanceof PotionMeta potionMeta) || !potionMeta.hasBasePotionType()) {
            return "";
        }
        var type = potionMeta.getBasePotionType();
        return type == null ? "" : type.name();
    }

    private static String customModelData(ItemMeta meta) {
        Integer cmd = ItemMatcher.extractCustomModelData(meta);
        return cmd == null ? "" : cmd.toString();
    }

    String format(ItemStack item, List<InfoRequest> requests) {
        if (item == null || item.isEmpty() || item.getType().isAir()) {
            return "";
        }
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        return requests.stream()
                .map(request -> formatField(item, meta, request))
                .collect(Collectors.joining(SEPARATOR));
    }

    private String formatField(ItemStack item, ItemMeta meta, InfoRequest request) {
        return switch (request.kind()) {
            case MATERIAL -> "MATERIAL:" + item.getType().name();
            case AMOUNT -> "AMOUNT:" + item.getAmount();
            case NAME -> "NAME:" + (meta != null && meta.hasDisplayName() && meta.displayName() != null
                    ? LEGACY.serialize(meta.displayName()) : "");
            case LORE -> "LORE:" + (meta != null && meta.hasLore() && meta.lore() != null
                    ? meta.lore().stream()
                    .map(LEGACY::serialize).collect(Collectors.joining("|"))
                    : "");
            case CUSTOM_MODEL_DATA -> "CUSTOMMODELDATA:" + customModelData(meta);
            case ENCHANTMENTS -> "ENCHANTMENTS:" + item.getEnchantments().entrySet().stream()
                    .map(entry -> entry.getKey().getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(";"));
            case POTION_TYPE -> "POTIONTYPE:" + potionType(meta);
            case NEXO -> "NEXO:" + Objects.toString(customItems.idFromItem(item), "");
            case PDC_ANY, PDC_STRING, PDC_INTEGER, PDC_BYTE, PDC_DOUBLE, PDC_BOOLEAN,
                 CUSTOM_DATA_STRING, CUSTOM_DATA_INTEGER, COMPONENT_STRING, COMPONENT_INTEGER ->
                    request.kind().name() + ":" + request.argument() + "=" + itemData.read(item, meta, request);
        };
    }
}
