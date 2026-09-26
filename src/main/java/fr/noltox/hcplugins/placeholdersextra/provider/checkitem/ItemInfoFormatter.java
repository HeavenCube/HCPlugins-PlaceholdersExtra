package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import io.papermc.paper.datacomponent.DataComponentTypes;
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

    private static String customModelData(ItemStack item) {
        var data = item.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (data == null || data.floats().isEmpty()) {
            return "";
        }
        float value = data.floats().getFirst();
        int rounded = Math.round(value);
        return Float.compare(value, rounded) == 0
                ? Integer.toString(rounded)
                : Float.toString(value);
    }

    String format(ItemStack item, List<InfoRequest> requests) {
        if (item == null || item.isEmpty()) {
            return "";
        }
        return requests.stream()
                .map(request -> formatField(item, request))
                .collect(Collectors.joining(SEPARATOR));
    }

    private String formatField(ItemStack item, InfoRequest request) {
        ItemMeta meta = item.getItemMeta();
        return switch (request.kind()) {
            case MATERIAL -> "MATERIAL:" + item.getType().name();
            case AMOUNT -> "AMOUNT:" + item.getAmount();
            case NAME -> "NAME:" + (meta != null && meta.hasCustomName()
                    ? LEGACY.serialize(Objects.requireNonNull(meta.customName())) : "");
            case LORE -> "LORE:" + (meta != null && meta.hasLore()
                    ? Objects.requireNonNull(meta.lore()).stream()
                    .map(LEGACY::serialize).collect(Collectors.joining("|"))
                    : "");
            case CUSTOM_MODEL_DATA -> "CUSTOMMODELDATA:" + customModelData(item);
            case ENCHANTMENTS -> "ENCHANTMENTS:" + item.getEnchantments().entrySet().stream()
                    .map(entry -> entry.getKey().getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(";"));
            case POTION_TYPE -> "POTIONTYPE:" + potionType(meta);
            case NEXO -> "NEXO:" + Objects.toString(customItems.idFromItem(item), "");
            case CUSTOM_DATA_STRING, CUSTOM_DATA_INTEGER, COMPONENT_STRING, COMPONENT_INTEGER ->
                    request.kind().name() + ":" + request.argument() + "=" + itemData.read(item, request);
        };
    }
}
