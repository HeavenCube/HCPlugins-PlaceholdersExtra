package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import java.util.List;

record ItemCriteria(
        String material,
        String materialContains,
        Integer requiredAmount,
        TextCriterion name,
        TextCriterion lore,
        Integer customModelData,
        List<EnchantmentCriterion> enchantments,
        boolean enchanted,
        PotionCriterion potion,
        String nexoId,
        List<RawDataCriterion> rawData,
        boolean strict
) {

    ItemCriteria {
        enchantments = List.copyOf(enchantments);
        rawData = List.copyOf(rawData);
    }

    int requestedAmount() {
        return requiredAmount == null ? 1 : requiredAmount;
    }

    record TextCriterion(Mode mode, List<String> values) {
        TextCriterion {
            values = List.copyOf(values);
        }

        enum Mode {
            CONTAINS,
            STARTS_WITH,
            EQUALS
        }
    }

    record PotionCriterion(String type, Boolean extended, Boolean upgraded) {
    }
}
