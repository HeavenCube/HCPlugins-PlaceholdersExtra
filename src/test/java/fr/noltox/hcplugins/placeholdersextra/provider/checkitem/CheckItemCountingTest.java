package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

class CheckItemCountingTest {

    private final CheckItemParser parser = new CheckItemParser(value -> true, value -> true);
    private final CheckItemService service = new CheckItemService(new ItemDataBridge() {
        public boolean matches(ItemStack item, List<PdcCriterion> criteria) { throw new AssertionError(); }
        public boolean matches(ItemMeta meta, List<PdcCriterion> criteria) { throw new AssertionError(); }
        public void apply(ItemMeta meta, List<PdcCriterion> criteria) { throw new AssertionError(); }
        public String read(ItemStack item, ItemMeta meta, InfoRequest request) { throw new AssertionError(); }
    }, new CustomItemBridge() {
        public String idFromItem(ItemStack item) { throw new AssertionError(); }
        public ItemStack build(String id) { throw new AssertionError(); }
    });

    @Test
    void nonStrictCheckStopsMatchingWhenEnoughItemsWereFound() throws Exception {
        var reads = new AtomicInteger();
        var inventory = inventory(new Stone(3, reads), new Stone(2, reads), new Stone(64, reads));
        assertEquals("true", resolve(inventory, "mat:STONE,amt:5"));
        assertEquals(2, reads.get());
    }

    @Test
    void strictCheckAndAmountStillCountEveryMatchingStack() throws Exception {
        var reads = new AtomicInteger();
        var inventory = inventory(null, new Stone(3, reads), new Stone(2, reads));
        // Strict matching additionally queries metadata but no Bukkit registries.
        assertEquals("false", resolve(inventory, "mat:STONE,amt:3,strict"));
        assertEquals(2, reads.get());
        reads.set(0);
        assertEquals("5", resolve(inventory, "amount_mat:STONE"));
        assertEquals(2, reads.get());
    }

    @Test
    void insufficientAndEmptyInventoriesKeepTheirResults() throws Exception {
        assertEquals("false", resolve(inventory(new Stone(1, new AtomicInteger())), "mat:STONE,amt:2"));
        assertEquals("false", resolve(inventory(), "mat:STONE"));
        assertEquals("0", resolve(inventory(), "amount_mat:STONE"));
    }

    private String resolve(PlayerInventory inventory, String text) throws CheckItemParseException {
        return service.execute(inventory, parser.parse(text, UnaryOperator.identity()));
    }

    private static PlayerInventory inventory(ItemStack... contents) {
        return (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
                new Class<?>[]{PlayerInventory.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getContents")) {
                        return contents.clone();
                    }
                    throw new AssertionError("Unexpected inventory call: " + method.getName());
                });
    }

    private static final class Stone extends ItemStack {
        private final int amount;
        private final AtomicInteger reads;
        Stone(int amount, AtomicInteger reads) { this.amount = amount; this.reads = reads; }
        @Override public boolean isEmpty() { return false; }
        @Override public Material getType() { reads.incrementAndGet(); return Material.STONE; }
        @Override public int getAmount() { return amount; }
        @Override public boolean hasItemMeta() { return false; }
    }
}
