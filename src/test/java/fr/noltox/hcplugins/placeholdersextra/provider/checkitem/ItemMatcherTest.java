package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("java:S5960")
class ItemMatcherTest {

    private final CheckItemParser parser = new CheckItemParser(
            value -> value.equals("STONE") || value.equals("DIRT"),
            value -> true
    );

    private static ItemFacts facts(String material, int amount) {
        return new ItemFacts(material, amount, "", List.of(), null, Map.of(), null);
    }

    private static CustomItemBridge customItems(String id) {
        return new CustomItemBridge() {
            @Override
            public String idFromItem(org.bukkit.inventory.ItemStack item) {
                return id;
            }

            @Override
            public org.bukkit.inventory.ItemStack build(String itemId) {
                return null;
            }
        };
    }

    @Test
    void matchesMaterialAndCombinedCriteria() throws Exception {
        ItemMatcher matcher = new ItemMatcher(new FakeItemDataBridge(true), customItems("custom_sword"));

        ItemFacts stone = facts("STONE", 3);
        assertTrue(matcher.matches(stone, criteria("mat:STONE,amt:2"), null, true));
        assertFalse(matcher.matches(stone, criteria("mat:DIRT"), null, true));
        assertTrue(matcher.matches(stone, criteria("nexo:custom_sword"), "custom_sword", true));
        assertFalse(matcher.matches(stone, criteria("nexo:other"), "custom_sword", true));

        MatchAccumulator amount = new MatchAccumulator();
        amount.add(stone.amount());
        amount.add(2);
        assertTrue(amount.satisfies(criteria("mat:STONE,amt:5").requestedAmount(), false));
        assertFalse(amount.satisfies(criteria("mat:STONE,amt:6").requestedAmount(), false));
    }

    @Test
    void delegatesNbtCriteriaWithoutNeedingNbtApiInTheTest() throws Exception {
        ItemFacts stone = facts("STONE", 1);
        ItemCriteria criteria = criteria("mat:STONE,nbtints:level=2");

        ItemMatcher matcher = new ItemMatcher(new FakeItemDataBridge(true), customItems(null));
        assertTrue(matcher.matches(stone, criteria, null, true));
        assertFalse(matcher.matches(stone, criteria, null, false));
    }

    private ItemCriteria criteria(String value) throws CheckItemParseException {
        return parser.parse(value, UnaryOperator.identity()).criteria();
    }

    private record FakeItemDataBridge(boolean result) implements ItemDataBridge {
        @Override
        public boolean matches(org.bukkit.inventory.ItemStack item, List<RawDataCriterion> criteria) {
            return criteria.isEmpty() || result;
        }

        @Override
        public void apply(org.bukkit.inventory.ItemStack item, List<RawDataCriterion> criteria) {
            // No mutation is needed for matching tests.
        }

        @Override
        public String read(org.bukkit.inventory.ItemStack item, InfoRequest request) {
            return "";
        }
    }
}
