package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

interface ItemDataBridge {

    boolean matches(ItemStack item, List<PdcCriterion> criteria);

    boolean matches(ItemMeta meta, List<PdcCriterion> criteria);

    void apply(ItemMeta meta, List<PdcCriterion> criteria);

    String read(ItemStack item, ItemMeta meta, InfoRequest request);
}
