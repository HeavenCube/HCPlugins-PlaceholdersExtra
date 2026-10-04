package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import fr.noltox.hcplugins.core.api.message.MiniMessages;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

final class ItemMatcher {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMP = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private final ItemDataBridge itemData;
    private final CustomItemBridge customItems;

    ItemMatcher(ItemDataBridge itemData, CustomItemBridge customItems) {
        this.itemData = Objects.requireNonNull(itemData, "itemData");
        this.customItems = Objects.requireNonNull(customItems, "customItems");
    }

    static boolean checkDisplayName(ItemMeta meta, ItemCriteria.TextCriterion criterion) {
        if (criterion == null) {
            return true;
        }
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        Component displayName = meta.displayName();
        if (displayName == null) {
            return false;
        }
        return matchesComponentText(displayName, criterion);
    }

    static boolean checkLore(ItemMeta meta, ItemCriteria.TextCriterion criterion) {
        if (criterion == null) {
            return true;
        }
        if (meta == null || !meta.hasLore()) {
            return false;
        }
        List<Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) {
            return false;
        }
        return switch (criterion.mode()) {
            case CONTAINS -> {
                var single = new ItemCriteria.TextCriterion(
                        ItemCriteria.TextCriterion.Mode.CONTAINS,
                        List.of(criterion.values().getFirst())
                );
                yield lore.stream().anyMatch(line -> matchesComponentText(line, single));
            }
            case EQUALS -> {
                if (lore.size() != criterion.values().size()) {
                    yield false;
                }
                for (int i = 0; i < lore.size(); i++) {
                    var single = new ItemCriteria.TextCriterion(
                            ItemCriteria.TextCriterion.Mode.EQUALS,
                            List.of(criterion.values().get(i))
                    );
                    if (!matchesComponentText(lore.get(i), single)) {
                        yield false;
                    }
                }
                yield true;
            }
            case STARTS_WITH -> false;
        };
    }

    static boolean matchesComponentText(Component component, ItemCriteria.TextCriterion criterion) {
        if (criterion == null) {
            return true;
        }
        String expected = criterion.values().getFirst();
        String plain = PLAIN.serialize(component);
        String mini = MINI_MESSAGE.serialize(component);
        String legacyAmp = LEGACY_AMP.serialize(component);
        String legacySec = LEGACY_SECTION.serialize(component);

        String expectedPlain = expected.contains("<")
                ? PLAIN.serialize(MiniMessages.parse(expected))
                : (expected.contains("&") || expected.contains("§")
                ? PLAIN.serialize(LEGACY_AMP.deserialize(expected))
                : expected);

        return switch (criterion.mode()) {
            case CONTAINS -> plain.contains(expected)
                    || plain.contains(expectedPlain)
                    || mini.contains(expected)
                    || legacyAmp.contains(expected)
                    || legacySec.contains(expected)
                    || legacySec.contains(expected.replace('&', '§'));
            case STARTS_WITH -> plain.startsWith(expected)
                    || plain.startsWith(expectedPlain)
                    || mini.startsWith(expected)
                    || legacyAmp.startsWith(expected)
                    || legacySec.startsWith(expected)
                    || legacySec.startsWith(expected.replace('&', '§'));
            case EQUALS -> plain.equals(expected)
                    || plain.equals(expectedPlain)
                    || mini.equals(expected)
                    || legacyAmp.equals(expected)
                    || legacySec.equals(expected)
                    || legacySec.equals(expected.replace('&', '§'));
        };
    }

    static boolean checkCustomModelData(ItemMeta meta, Integer expected) {
        if (expected == null) {
            return true;
        }
        if (meta == null) {
            return false;
        }
        if (meta.hasCustomModelDataComponent()) {
            var comp = meta.getCustomModelDataComponent();
            if (comp != null && !comp.getFloats().isEmpty()) {
                float firstFloat = comp.getFloats().getFirst();
                int rounded = Math.round(firstFloat);
                if (Float.compare(firstFloat, rounded) == 0) {
                    return rounded == expected;
                }
            }
        }
        return false;
    }

    static Integer extractCustomModelData(ItemMeta meta) {
        if (meta == null) {
            return null;
        }
        if (meta.hasCustomModelDataComponent()) {
            var comp = meta.getCustomModelDataComponent();
            if (comp != null && !comp.getFloats().isEmpty()) {
                float firstFloat = comp.getFloats().getFirst();
                int rounded = Math.round(firstFloat);
                if (Float.compare(firstFloat, rounded) == 0) {
                    return rounded;
                }
            }
        }
        return null;
    }

    static boolean checkEnchantments(
            ItemStack item,
            ItemMeta meta,
            List<EnchantmentCriterion> criteria,
            boolean mustBeEnchanted
    ) {
        if (!mustBeEnchanted && criteria.isEmpty()) {
            return true;
        }
        boolean hasAny = (meta != null && meta.hasEnchants())
                || (item != null && !item.getEnchantments().isEmpty())
                || (meta instanceof EnchantmentStorageMeta storageMeta && storageMeta.hasStoredEnchants());
        if (mustBeEnchanted && !hasAny) {
            return false;
        }
        if (criteria.isEmpty()) {
            return true;
        }
        for (EnchantmentCriterion criterion : criteria) {
            Enchantment enchantment = enchantment(criterion.key());
            if (enchantment == null) {
                return false;
            }
            int level = 0;
            if (meta != null && meta.hasEnchant(enchantment)) {
                level = meta.getEnchantLevel(enchantment);
            } else if (meta instanceof EnchantmentStorageMeta storageMeta && storageMeta.hasStoredEnchant(enchantment)) {
                level = storageMeta.getStoredEnchantLevel(enchantment);
            } else if (item != null) {
                level = item.getEnchantmentLevel(enchantment);
            }
            if (level <= 0) {
                return false;
            }
            if (criterion.level() != null && !criterion.level().equals(level)) {
                return false;
            }
        }
        return true;
    }

    static boolean checkPotion(ItemMeta meta, ItemCriteria.PotionCriterion criterion) {
        if (criterion == null) {
            return true;
        }
        if (meta == null || !(meta instanceof PotionMeta potionMeta) || !potionMeta.hasBasePotionType()) {
            return false;
        }
        PotionType actualType = potionMeta.getBasePotionType();
        if (actualType == null) {
            return false;
        }
        String actualName = actualType.name();
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

    static boolean checkPdc(ItemMeta meta, List<PdcCriterion> criteria) {
        if (criteria.isEmpty()) {
            return true;
        }
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (PdcCriterion criterion : criteria) {
            if (!matchesPdcCriterion(pdc, criterion)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesPdcCriterion(PersistentDataContainer pdc, PdcCriterion criterion) {
        NamespacedKey key = criterion.key();
        if (criterion.isPresenceOnly()) {
            return pdc.has(key);
        }
        return switch (criterion.type()) {
            case STRING -> Objects.equals(pdc.get(key, PersistentDataType.STRING), criterion.expectedValue());
            case INTEGER -> Objects.equals(pdc.get(key, PersistentDataType.INTEGER), criterion.expectedValue());
            case BYTE -> Objects.equals(pdc.get(key, PersistentDataType.BYTE), criterion.expectedValue());
            case DOUBLE -> {
                Double actual = pdc.get(key, PersistentDataType.DOUBLE);
                yield actual != null && Double.compare(actual, (Double) criterion.expectedValue()) == 0;
            }
            case BOOLEAN -> Objects.equals(pdc.get(key, PersistentDataType.BOOLEAN), criterion.expectedValue());
            case ANY -> checkPdcAny(pdc, key, criterion.expectedValue());
        };
    }

    private static boolean checkPdcAny(PersistentDataContainer pdc, NamespacedKey key, Object expected) {
        if (!pdc.has(key)) {
            return false;
        }
        String expectedStr = expected.toString();
        if (pdc.has(key, PersistentDataType.STRING)) {
            return Objects.equals(pdc.get(key, PersistentDataType.STRING), expectedStr);
        }
        if (pdc.has(key, PersistentDataType.INTEGER)) {
            try {
                int exp = Integer.parseInt(expectedStr);
                return Objects.equals(pdc.get(key, PersistentDataType.INTEGER), exp);
            } catch (NumberFormatException ignored) {
            }
        }
        if (pdc.has(key, PersistentDataType.BOOLEAN)) {
            if ("true".equalsIgnoreCase(expectedStr) || "false".equalsIgnoreCase(expectedStr)) {
                boolean exp = Boolean.parseBoolean(expectedStr);
                return Objects.equals(pdc.get(key, PersistentDataType.BOOLEAN), exp);
            }
        }
        if (pdc.has(key, PersistentDataType.DOUBLE)) {
            try {
                double exp = Double.parseDouble(expectedStr);
                Double actual = pdc.get(key, PersistentDataType.DOUBLE);
                return actual != null && Double.compare(actual, exp) == 0;
            } catch (NumberFormatException ignored) {
            }
        }
        if (pdc.has(key, PersistentDataType.BYTE)) {
            try {
                byte exp = Byte.parseByte(expectedStr);
                return Objects.equals(pdc.get(key, PersistentDataType.BYTE), exp);
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    static void applyPdc(ItemMeta meta, List<PdcCriterion> criteria) {
        if (meta == null || criteria.isEmpty()) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (PdcCriterion criterion : criteria) {
            NamespacedKey key = criterion.key();
            switch (criterion.type()) {
                case STRING, ANY -> pdc.set(key, PersistentDataType.STRING,
                        criterion.expectedValue() != null ? criterion.expectedValue().toString() : "");
                case INTEGER -> pdc.set(key, PersistentDataType.INTEGER,
                        criterion.expectedValue() != null ? (Integer) criterion.expectedValue() : 1);
                case BYTE -> pdc.set(key, PersistentDataType.BYTE,
                        criterion.expectedValue() != null ? (Byte) criterion.expectedValue() : (byte) 1);
                case DOUBLE -> pdc.set(key, PersistentDataType.DOUBLE,
                        criterion.expectedValue() != null ? (Double) criterion.expectedValue() : 1.0);
                case BOOLEAN -> pdc.set(key, PersistentDataType.BOOLEAN,
                        criterion.expectedValue() != null ? (Boolean) criterion.expectedValue() : true);
            }
        }
    }

    static String readPdc(ItemMeta meta, NamespacedKey key, PdcCriterion.PdcType type) {
        if (meta == null) {
            return "";
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(key)) {
            return "";
        }
        return switch (type) {
            case STRING -> Objects.toString(pdc.get(key, PersistentDataType.STRING), "");
            case INTEGER -> Objects.toString(pdc.get(key, PersistentDataType.INTEGER), "");
            case BYTE -> Objects.toString(pdc.get(key, PersistentDataType.BYTE), "");
            case DOUBLE -> Objects.toString(pdc.get(key, PersistentDataType.DOUBLE), "");
            case BOOLEAN -> Objects.toString(pdc.get(key, PersistentDataType.BOOLEAN), "");
            case ANY -> {
                if (pdc.has(key, PersistentDataType.STRING)) {
                    yield Objects.toString(pdc.get(key, PersistentDataType.STRING), "");
                }
                if (pdc.has(key, PersistentDataType.INTEGER)) {
                    yield Objects.toString(pdc.get(key, PersistentDataType.INTEGER), "");
                }
                if (pdc.has(key, PersistentDataType.BOOLEAN)) {
                    yield Objects.toString(pdc.get(key, PersistentDataType.BOOLEAN), "");
                }
                if (pdc.has(key, PersistentDataType.DOUBLE)) {
                    yield Objects.toString(pdc.get(key, PersistentDataType.DOUBLE), "");
                }
                if (pdc.has(key, PersistentDataType.BYTE)) {
                    yield Objects.toString(pdc.get(key, PersistentDataType.BYTE), "");
                }
                yield "";
            }
        };
    }

    static boolean checkStrict(ItemStack item, ItemMeta meta, ItemCriteria criteria) {
        if (meta == null) {
            return true;
        }
        if (criteria.name() == null && meta.hasDisplayName()) {
            return false;
        }
        if (criteria.lore() == null && meta.hasLore() && meta.lore() != null && !meta.lore().isEmpty()) {
            return false;
        }
        if (criteria.customModelData() == null && meta.hasCustomModelDataComponent()) {
            return false;
        }
        boolean hasEnchants = meta.hasEnchants() || (item != null && !item.getEnchantments().isEmpty())
                || (meta instanceof EnchantmentStorageMeta storageMeta && storageMeta.hasStoredEnchants());
        if ((criteria.enchantments().isEmpty() && !criteria.enchanted()) && hasEnchants) {
            return false;
        }
        return !criteria.pdc().isEmpty() || meta.getPersistentDataContainer().isEmpty();
    }

    static Enchantment enchantment(String key) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(
                key.indexOf(':') < 0 ? "minecraft:" + key : key
        );
        return namespacedKey == null ? null : RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT)
                .get(namespacedKey);
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

    boolean matches(ItemStack item, ItemCriteria criteria) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        if (criteria.material() != null && !criteria.material().equals(item.getType().name())) {
            return false;
        }
        if (criteria.materialContains() != null && !item.getType().name().contains(criteria.materialContains())) {
            return false;
        }
        if (criteria.nexoId() != null && !criteria.nexoId().equals(customItems.idFromItem(item))) {
            return false;
        }

        boolean needsMeta = criteria.name() != null
                || criteria.lore() != null
                || criteria.customModelData() != null
                || criteria.potion() != null
                || !criteria.pdc().isEmpty()
                || criteria.enchanted()
                || !criteria.enchantments().isEmpty()
                || criteria.strict();

        if (!needsMeta) {
            return true;
        }

        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;

        if (!checkDisplayName(meta, criteria.name())
                || !checkLore(meta, criteria.lore())
                || !checkCustomModelData(meta, criteria.customModelData())
                || !checkEnchantments(item, meta, criteria.enchantments(), criteria.enchanted())
                || !checkPotion(meta, criteria.potion())
                || !checkPdc(meta, criteria.pdc())) {
            return false;
        }

        return !criteria.strict() || checkStrict(item, meta, criteria);
    }

    boolean matches(
            ItemFacts item,
            ItemCriteria criteria,
            String customItemId,
            boolean pdcMatches
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
        if (!matchesFactsText(item.customName(), criteria.name())
                || !matchesFactsLore(item.lore(), criteria.lore())
                || criteria.customModelData() != null
                && !criteria.customModelData().equals(item.customModelData())
                || !matchesFactsEnchantments(item.enchantments(), criteria)
                || !matchesFactsPotion(item.potionType(), criteria.potion())
                || !pdcMatches) {
            return false;
        }
        return !criteria.strict() || matchesFactsStrict(item, criteria);
    }

    private static boolean matchesFactsText(String actual, ItemCriteria.TextCriterion criterion) {
        if (criterion == null) {
            return true;
        }
        String expected = criterion.values().getFirst();
        return switch (criterion.mode()) {
            case CONTAINS -> actual.contains(expected);
            case STARTS_WITH -> actual.startsWith(expected);
            case EQUALS -> actual.equals(expected);
        };
    }

    private static boolean matchesFactsLore(List<String> actual, ItemCriteria.TextCriterion criterion) {
        if (criterion == null) {
            return true;
        }
        List<String> expected = criterion.values();
        return switch (criterion.mode()) {
            case CONTAINS -> actual.stream().anyMatch(line -> line.contains(expected.getFirst()));
            case EQUALS -> actual.equals(expected);
            case STARTS_WITH -> false;
        };
    }

    private static boolean matchesFactsEnchantments(
            Map<String, Integer> enchantments,
            ItemCriteria criteria
    ) {
        if (criteria.enchanted() && enchantments.isEmpty()) {
            return false;
        }
        for (EnchantmentCriterion criterion : criteria.enchantments()) {
            String key = criterion.key().indexOf(':') < 0 ? "minecraft:" + criterion.key() : criterion.key();
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

    private static boolean matchesFactsPotion(String actualName, ItemCriteria.PotionCriterion criterion) {
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

    private static boolean matchesFactsStrict(ItemFacts item, ItemCriteria criteria) {
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
}
