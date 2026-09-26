package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import java.util.List;
import java.util.Map;

record ItemFacts(
        String material,
        int amount,
        String customName,
        List<String> lore,
        Integer customModelData,
        Map<String, Integer> enchantments,
        String potionType
) {

    ItemFacts {
        lore = List.copyOf(lore);
        enchantments = Map.copyOf(enchantments);
    }
}
