package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class CheckItemService {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ItemDataBridge itemData;
    private final CustomItemBridge customItems;
    private final ItemMatcher matcher;
    private final ItemInfoFormatter infoFormatter;

    CheckItemService(ItemDataBridge itemData, CustomItemBridge customItems) {
        this.itemData = Objects.requireNonNull(itemData, "itemData");
        this.customItems = Objects.requireNonNull(customItems, "customItems");
        matcher = new ItemMatcher(itemData, customItems);
        infoFormatter = new ItemInfoFormatter(itemData, customItems);
    }

    private static List<InventoryItem> selectedItems(
            PlayerInventory inventory,
            ItemSelection selection
    ) {
        return switch (selection.kind()) {
            case INVENTORY -> {
                ItemStack[] contents = Objects.requireNonNull(
                        inventory.getContents(),
                        "inventory contents"
                );
                List<InventoryItem> items = new ArrayList<>(contents.length);
                for (int slot = 0; slot < contents.length; slot++) {
                    addIfPresent(items, slot, contents[slot]);
                }
                yield items;
            }
            case HANDS -> selectedHands(inventory);
            case MAIN_HAND -> selectedItem(inventory.getHeldItemSlot(), inventory.getItemInMainHand());
            case OFF_HAND -> selectedItem(40, inventory.getItemInOffHand());
            case SLOT -> selectedItem(selection.slot(), inventory.getItem(selection.slot()));
        };
    }

    private static List<InventoryItem> selectedHands(PlayerInventory inventory) {
        List<InventoryItem> items = new ArrayList<>(2);
        addIfPresent(items, inventory.getHeldItemSlot(), inventory.getItemInMainHand());
        addIfPresent(items, 40, inventory.getItemInOffHand());
        return items;
    }

    private static List<InventoryItem> selectedItem(int slot, ItemStack item) {
        return item == null || item.isEmpty() ? List.of() : List.of(new InventoryItem(slot, item));
    }

    private static void addIfPresent(List<InventoryItem> items, int slot, ItemStack item) {
        if (item != null && !item.isEmpty()) {
            items.add(new InventoryItem(slot, item));
        }
    }

    private static PotionType constructedPotionType(ItemCriteria.PotionCriterion criterion) {
        if (criterion.type() == null) {
            return null;
        }
        String type = ItemMatcher.basePotionName(criterion.type());
        if (Boolean.TRUE.equals(criterion.extended())) {
            type = "LONG_" + type;
        } else if (Boolean.TRUE.equals(criterion.upgraded())) {
            type = "STRONG_" + type;
        } else if (criterion.type().startsWith("LONG_") || criterion.type().startsWith("STRONG_")) {
            type = criterion.type();
        }
        try {
            return PotionType.valueOf(type);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    String execute(
            PlayerInventory inventory,
            CheckItemQuery query
    ) {
        return switch (query.operation()) {
            case CHECK -> Boolean.toString(check(inventory, query));
            case AMOUNT -> Integer.toString(scan(inventory, query).amount().total());
            case GET_INFO -> {
                List<InventoryItem> selected = selectedItems(inventory, query.selection());
                yield infoFormatter.format(selected.isEmpty() ? null : selected.getFirst().item(), query.infoRequests());
            }
            case GIVE -> give(inventory, query);
            case REMOVE -> remove(inventory, query);
        };
    }

    private boolean check(PlayerInventory inventory, CheckItemQuery query) {
        MatchScan scan = scan(inventory, query);
        int requested = query.criteria().requestedAmount();
        return scan.amount().satisfies(requested, query.criteria().strict());
    }

    private String give(
            PlayerInventory inventory,
            CheckItemQuery query
    ) {
        ItemStack template = createItem(query.criteria());
        if (template == null) {
            return "false";
        }

        int remaining = query.criteria().requestedAmount();
        int maxStackSize = template.getMaxStackSize();
        while (remaining > 0) {
            int batchSize = Math.min(maxStackSize, remaining);
            ItemStack batch = template.asQuantity(batchSize);
            Map<Integer, ItemStack> leftovers = inventory.addItem(batch);
            int notAdded = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            remaining -= batchSize - notAdded;
            if (notAdded > 0) {
                return Integer.toString(remaining);
            }
        }
        return "true";
    }

    private String remove(
            PlayerInventory inventory,
            CheckItemQuery query
    ) {
        MatchScan scan = scan(inventory, query);
        int requested = query.criteria().requiredAmount() == null
                ? scan.amount().total()
                : query.criteria().requiredAmount();
        if (!scan.amount().satisfies(requested, false)) {
            return query.reportAmount() ? "0" : "false";
        }

        int remaining = requested;
        for (InventoryItem matched : scan.matches()) {
            int removed = Math.min(remaining, matched.item().getAmount());
            int newAmount = matched.item().getAmount() - removed;
            if (newAmount == 0) {
                inventory.setItem(matched.slot(), null);
            } else {
                matched.item().setAmount(newAmount);
            }
            remaining -= removed;
            if (remaining == 0) {
                break;
            }
        }
        return query.reportAmount() ? Integer.toString(requested) : "true";
    }

    private MatchScan scan(PlayerInventory inventory, CheckItemQuery query) {
        List<InventoryItem> matches = new ArrayList<>();
        MatchAccumulator amount = new MatchAccumulator();
        for (InventoryItem candidate : selectedItems(inventory, query.selection())) {
            if (matcher.matches(candidate.item(), query.criteria())) {
                matches.add(candidate);
                amount.add(candidate.item().getAmount());
            }
        }
        return new MatchScan(List.copyOf(matches), amount);
    }

    private ItemStack createItem(ItemCriteria criteria) {
        Material material = criteria.material() == null
                ? null
                : Material.matchMaterial(criteria.material());
        ItemStack item = criteria.nexoId() == null
                ? (material == null ? null : ItemStack.of(material))
                : customItems.build(criteria.nexoId());
        if (item == null || item.isEmpty()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (criteria.name() != null) {
            meta.customName(LEGACY.deserialize(criteria.name().values().getFirst()));
        }
        if (criteria.lore() != null) {
            meta.lore(criteria.lore().values().stream().map(LEGACY::deserialize).toList());
        }
        for (EnchantmentCriterion criterion : criteria.enchantments()) {
            var enchantment = ItemMatcher.enchantment(criterion.key());
            if (enchantment == null) {
                return null;
            }
            meta.addEnchant(enchantment, Objects.requireNonNullElse(criterion.level(), 1), true);
        }
        if (criteria.potion() != null) {
            if (!(meta instanceof PotionMeta potionMeta)) {
                return null;
            }
            PotionType potionType = constructedPotionType(criteria.potion());
            if (potionType == null) {
                return null;
            }
            potionMeta.setBasePotionType(potionType);
        }
        if (!item.setItemMeta(meta)) {
            return null;
        }
        if (criteria.customModelData() != null) {
            item.setData(
                    DataComponentTypes.CUSTOM_MODEL_DATA,
                    CustomModelData.customModelData().addFloat(criteria.customModelData().floatValue())
            );
        }
        itemData.apply(item, criteria.rawData());
        return item.asOne();
    }

    private record InventoryItem(int slot, ItemStack item) {
    }

    private record MatchScan(List<InventoryItem> matches, MatchAccumulator amount) {
    }
}
