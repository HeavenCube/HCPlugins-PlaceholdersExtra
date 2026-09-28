package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import fr.noltox.hcplugins.core.api.message.MiniMessages;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
            public String idFromItem(ItemStack item) {
                return id;
            }

            @Override
            public ItemStack build(String itemId) {
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
    void delegatesPdcCriteriaWithoutExternalDependencies() throws Exception {
        ItemFacts stone = facts("STONE", 1);
        ItemCriteria criteria = criteria("mat:STONE,pdcints:level=2");

        ItemMatcher matcher = new ItemMatcher(new FakeItemDataBridge(true), customItems(null));
        assertTrue(matcher.matches(stone, criteria, null, true));
        assertFalse(matcher.matches(stone, criteria, null, false));
    }

    @Test
    void preservesCompatibilityWithNbtAliases() throws Exception {
        ItemFacts stone = facts("STONE", 1);
        ItemCriteria criteria = criteria("mat:STONE,nbtints:level=2");

        ItemMatcher matcher = new ItemMatcher(new FakeItemDataBridge(true), customItems(null));
        assertTrue(matcher.matches(stone, criteria, null, true));
        assertFalse(matcher.matches(stone, criteria, null, false));
    }

    @Test
    void matchesComponentTextWithPlainMiniMessageAndLegacy() {
        Component sword = MiniMessages.parse("<red>Épée Magique</red>");

        // Plain text matching
        assertTrue(ItemMatcher.matchesComponentText(
                sword,
                new ItemCriteria.TextCriterion(ItemCriteria.TextCriterion.Mode.EQUALS, List.of("Épée Magique"))
        ));
        assertTrue(ItemMatcher.matchesComponentText(
                sword,
                new ItemCriteria.TextCriterion(ItemCriteria.TextCriterion.Mode.CONTAINS, List.of("Magique"))
        ));
        assertTrue(ItemMatcher.matchesComponentText(
                sword,
                new ItemCriteria.TextCriterion(ItemCriteria.TextCriterion.Mode.STARTS_WITH, List.of("Épée"))
        ));

        // MiniMessage matching
        assertTrue(ItemMatcher.matchesComponentText(
                sword,
                new ItemCriteria.TextCriterion(ItemCriteria.TextCriterion.Mode.EQUALS, List.of("<red>Épée Magique</red>"))
        ));
        assertTrue(ItemMatcher.matchesComponentText(
                sword,
                new ItemCriteria.TextCriterion(ItemCriteria.TextCriterion.Mode.CONTAINS, List.of("<red>"))
        ));

        // Negative check
        assertFalse(ItemMatcher.matchesComponentText(
                sword,
                new ItemCriteria.TextCriterion(ItemCriteria.TextCriterion.Mode.EQUALS, List.of("Autre Arme"))
        ));
    }

    @Test
    void nullAndAirSafelyHandledInMatcher() {
        ItemMatcher matcher = new ItemMatcher(new FakeItemDataBridge(true), customItems(null));

        assertFalse(matcher.matches((ItemStack) null, ItemCriteria.TextCriterion.Mode.EQUALS != null ? null : null));
        assertTrue(ItemMatcher.checkDisplayName(null, null));
        assertFalse(ItemMatcher.checkDisplayName(null, new ItemCriteria.TextCriterion(ItemCriteria.TextCriterion.Mode.EQUALS, List.of("Nom"))));
        assertTrue(ItemMatcher.checkLore(null, null));
        assertFalse(ItemMatcher.checkLore(null, new ItemCriteria.TextCriterion(ItemCriteria.TextCriterion.Mode.EQUALS, List.of("Lore"))));
        assertTrue(ItemMatcher.checkCustomModelData(null, null));
        assertFalse(ItemMatcher.checkCustomModelData(null, 123));
        assertTrue(ItemMatcher.checkPdc(null, List.of()));
        assertFalse(ItemMatcher.checkPdc(null, List.of(new PdcCriterion(org.bukkit.NamespacedKey.minecraft("test"), PdcCriterion.PdcType.ANY, null))));
    }

    private ItemCriteria criteria(String value) throws CheckItemParseException {
        return parser.parse(value, UnaryOperator.identity()).criteria();
    }

    private record FakeItemDataBridge(boolean result) implements ItemDataBridge {
        @Override
        public boolean matches(ItemStack item, List<PdcCriterion> criteria) {
            return criteria.isEmpty() || result;
        }

        @Override
        public boolean matches(ItemMeta meta, List<PdcCriterion> criteria) {
            return criteria.isEmpty() || result;
        }

        @Override
        public void apply(ItemMeta meta, List<PdcCriterion> criteria) {
            // No mutation is needed for matching tests.
        }

        @Override
        public String read(ItemStack item, ItemMeta meta, InfoRequest request) {
            return "";
        }
    }
}
