package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import org.bukkit.inventory.ItemStack;

interface CustomItemBridge {

    String idFromItem(ItemStack item);

    ItemStack build(String itemId);
}
