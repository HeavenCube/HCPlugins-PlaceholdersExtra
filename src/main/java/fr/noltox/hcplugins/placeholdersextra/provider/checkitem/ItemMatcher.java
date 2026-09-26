package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

final class ItemMatcher {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final ItemDataBridge itemData;
    private final CustomItemBridge customItems;

    ItemMatcher(ItemDataBridge itemData, CustomItemBridge customItems) {
        this.itemData = Objects.requireNonNull(itemData, "itemData");
        this.customItems = Objects.requireNonNull(customItems, "customItems");
    }

    private static boolean matchesText(String actual, ItemCriteria.TextCriterion criterion) {
        if (criterion == null) {
            return true;
        }
        String expected = legacy(criterion.values().getFirst());
        return switch (criterion.mode()) {
            case CONTAINS -> actual.contains(expected);
            case STARTS_WITH -> actual.startsWith(expected);
            case EQUALS -> actual.equals(expected);
        };
    }

    private static boolean matchesLore(List<String> actual, ItemCriteria.TextCriterion criterion) {
        if (criterion == null) {
            return true;
        }
        List<String> expected = criterion.values().stream().map(ItemMatcher::legacy).toList();
        return switch (criterion.mode()) {
            case CONTAINS -> actual.stream().anyMatch(line -> line.contains(expected.getFirst()));
            case EQUALS -> actual.equals(expected);
            case STARTS_WITH -> false;
        };
    }

    private static boolean matchesEnchantments(
            Map<String, Integer> enchantments,
            ItemCriteria criteria
    ) {
        if (criteria.enchanted() && enchantments.isEmpty()) {
            return false;
        }
        for (EnchantmentCriterion criterion : criteria.enchantments()) {
            String key = normalizeKey(criterion.key());
            Integer level = enchantments.get(key);
            if (level == null) {
                return false;
            }
            if (criterion.level() != null && !criterion.level().equals(level)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesPotion(String actualName, ItemCriteria.PotionCriterion criterion) {
        if (criterion == null) {
            return true;
        }
        if (actualName == null) {
            return false;
        }
        if (criterion.type() != null
                && !basePotionName(actualName).equals(basePotionName(criterion.type()))) {
            return false;
        }
        if (criterion.extended() != null
                && criterion.extended() != actualName.startsWith("LONG_")) {
            return false;
        }
        return criterion.upgraded() == null
                || criterion.upgraded() == actualName.startsWith("STRONG_");
    }

    private static boolean matchesStrict(ItemFacts item, ItemCriteria criteria) {
        if (criteria.name() == null && !item.customName().isEmpty()) {
            return false;
        }
        if (criteria.lore() == null && !item.lore().isEmpty()) {
            return false;
        }
        if (criteria.customModelData() == null && item.customModelData() != null) {
            return false;
        }
        return (!criteria.enchantments().isEmpty() || criteria.enchanted())
                || item.enchantments().isEmpty();
    }

    private static ItemFacts facts(ItemStack item, ItemCriteria criteria) {
        boolean needsMeta = criteria.name() != null
                || criteria.lore() != null
                || criteria.customModelData() != null
                || criteria.potion() != null
                || criteria.strict();
        var meta = needsMeta ? item.getItemMeta() : null;
        Integer legacyCustomModelData = criteria.customModelData() != null || criteria.strict()
                ? customModelData(item)
                : null;
        String potionType = potionType(meta);
        Map<String, Integer> enchantments = !criteria.enchantments().isEmpty()
                || criteria.enchanted() || criteria.strict()
                ? item.getEnchantments().entrySet().stream().collect(Collectors.toUnmodifiableMap(
                entry -> entry.getKey().getKey().toString(),
                Map.Entry::getValue
        ))
                : Map.of();
        return new ItemFacts(
                item.getType().name(),
                item.getAmount(),
                meta != null && meta.hasCustomName()
                        ? LEGACY.serialize(Objects.requireNonNull(meta.customName())) : "",
                meta != null && meta.hasLore()
                        ? Objects.requireNonNull(meta.lore()).stream().map(LEGACY::serialize).toList()
                        : List.of(),
                legacyCustomModelData,
                enchantments,
                potionType
        );
    }

    private static String potionType(org.bukkit.inventory.meta.ItemMeta meta) {
        if (!(meta instanceof PotionMeta potionMeta) || !potionMeta.hasBasePotionType()) {
            return null;
        }
        var type = potionMeta.getBasePotionType();
        return type == null ? null : type.name();
    }

    private static Integer customModelData(ItemStack item) {
        var data = item.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (data == null || data.floats().isEmpty()) {
            return null;
        }
        float value = data.floats().getFirst();
        int rounded = Math.round(value);
        return Float.compare(value, rounded) == 0 ? rounded : null;
    }

    static Enchantment enchantment(String key) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(
                key.indexOf(':') < 0 ? "minecraft:" + key : key
        );
        return namespacedKey == null ? null : RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT)
                .get(namespacedKey);
    }

    private static String normalizeKey(String key) {
        return key.indexOf(':') < 0 ? "minecraft:" + key : key;
    }

    static String basePotionName(String name) {
        if (name.startsWith("LONG_")) {
            return name.substring("LONG_".length());
        }
        if (name.startsWith("STRONG_")) {
            return name.substring("STRONG_".length());
        }
        return name;
    }

    static String legacy(String value) {
        return value.replace('&', '§');
    }

    boolean matches(ItemStack item, ItemCriteria criteria) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        return matches(
                facts(item, criteria),
                criteria,
                criteria.nexoId() == null ? null : customItems.idFromItem(item),
                true
        ) && (criteria.rawData().isEmpty() || itemData.matches(item, criteria.rawData()));
    }

    boolean matches(
            ItemFacts item,
            ItemCriteria criteria,
            String customItemId,
            boolean rawDataMatches
    ) {
        if (criteria.material() != null && !criteria.material().equals(item.material())) {
            return false;
        }
        if (criteria.materialContains() != null
                && !item.material().contains(criteria.materialContains())) {
            return false;
        }
        if (criteria.nexoId() != null && !criteria.nexoId().equals(customItemId)) {
            return false;
        }
        if (!matchesText(item.customName(), criteria.name())
                || !matchesLore(item.lore(), criteria.lore())
                || criteria.customModelData() != null
                && !criteria.customModelData().equals(item.customModelData())
                || !matchesEnchantments(item.enchantments(), criteria)
                || !matchesPotion(item.potionType(), criteria.potion())
                || !rawDataMatches) {
            return false;
        }
        return !criteria.strict() || matchesStrict(item, criteria);
    }
}
