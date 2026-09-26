package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

final class NexoBridgeState implements CustomItemBridge {

    private static final CustomItemBridge UNAVAILABLE = new CustomItemBridge() {
        @Override
        public String idFromItem(ItemStack item) {
            return null;
        }

        @Override
        public ItemStack build(String itemId) {
            return null;
        }
    };

    private volatile CustomItemBridge delegate = UNAVAILABLE;

    void activate(CustomItemBridge bridge) {
        delegate = Objects.requireNonNull(bridge, "bridge");
    }

    void deactivate() {
        delegate = UNAVAILABLE;
    }

    @Override
    public String idFromItem(ItemStack item) {
        return delegate.idFromItem(item);
    }

    @Override
    public ItemStack build(String itemId) {
        return delegate.build(itemId);
    }
}
