package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import org.bukkit.inventory.ItemStack;

import java.util.List;

interface ItemDataBridge {

    boolean matches(ItemStack item, List<RawDataCriterion> criteria);

    void apply(ItemStack item, List<RawDataCriterion> criteria);

    String read(ItemStack item, InfoRequest request);
}
