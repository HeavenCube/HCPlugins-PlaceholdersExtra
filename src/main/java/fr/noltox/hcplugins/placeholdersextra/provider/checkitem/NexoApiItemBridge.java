package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import com.nexomc.nexo.api.NexoItems;
import org.bukkit.inventory.ItemStack;

final class NexoApiItemBridge implements CustomItemBridge {

    @Override
    public String idFromItem(ItemStack item) {
        return NexoItems.idFromItem(item);
    }

    @Override
    public ItemStack build(String itemId) {
        return NexoItems.optionalItemFromId(itemId)
                .map(builder -> builder.build())
                .orElse(null);
    }
}
