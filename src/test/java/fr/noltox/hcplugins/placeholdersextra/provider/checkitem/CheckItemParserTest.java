package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("java:S5960")
class CheckItemParserTest {

    private final CheckItemParser parser = new CheckItemParser(
            value -> value.equals("STONE") || value.equals("PAPER"),
            value -> value.equals("SWIFTNESS")
    );

    @Test
    void parsesSimpleAndCombinedCriteriaOnce() throws Exception {
        CheckItemQuery query = parse("mat:STONE,amt:2,enchanted,inhand:main");

        assertEquals(CheckItemOperation.CHECK, query.operation());
        assertEquals("STONE", query.criteria().material());
        assertEquals(2, query.criteria().requestedAmount());
        assertTrue(query.criteria().enchanted());
        assertEquals(ItemSelection.MAIN_HAND, query.selection());
    }

    @Test
    void preservesEscapedCommasAndSemicolons() throws Exception {
        CheckItemQuery query = parse(
                "mat:PAPER,nameequals:Bonjour\\, monde,nbtstrings:texte=un\\;deux;rang=admin"
        );

        assertEquals("Bonjour, monde", query.criteria().name().values().getFirst());
        assertEquals(2, query.criteria().rawData().size());
        assertEquals("un;deux", query.criteria().rawData().getFirst().expected());
    }

    @Test
    void parsesPdcPresenceAndTypedCriteria() throws Exception {
        CheckItemQuery query = parse(
                "mat:STONE,pdc:simple_tag,pdcstrings:custom:text=val,pdcints:level=42,pdcbooleans:active=true,pdcbytes:status=1,pdcdoubles:ratio=3.14"
        );

        assertEquals(6, query.criteria().pdc().size());

        // Presence check
        PdcCriterion simpleTag = query.criteria().pdc().get(0);
        assertEquals(NamespacedKey.minecraft("simple_tag"), simpleTag.key());
        assertEquals(PdcCriterion.PdcType.ANY, simpleTag.type());
        assertTrue(simpleTag.isPresenceOnly());
        assertNull(simpleTag.expectedValue());

        // Typed String with custom namespace
        PdcCriterion textTag = query.criteria().pdc().get(1);
        assertEquals(new NamespacedKey("custom", "text"), textTag.key());
        assertEquals(PdcCriterion.PdcType.STRING, textTag.type());
        assertFalse(textTag.isPresenceOnly());
        assertEquals("val", textTag.expectedValue());

        // Typed Integer
        PdcCriterion levelTag = query.criteria().pdc().get(2);
        assertEquals(PdcCriterion.PdcType.INTEGER, levelTag.type());
        assertEquals(42, levelTag.expectedValue());

        // Typed Boolean
        PdcCriterion activeTag = query.criteria().pdc().get(3);
        assertEquals(PdcCriterion.PdcType.BOOLEAN, activeTag.type());
        assertEquals(true, activeTag.expectedValue());

        // Typed Byte
        PdcCriterion statusTag = query.criteria().pdc().get(4);
        assertEquals(PdcCriterion.PdcType.BYTE, statusTag.type());
        assertEquals((byte) 1, statusTag.expectedValue());

        // Typed Double
        PdcCriterion ratioTag = query.criteria().pdc().get(5);
        assertEquals(PdcCriterion.PdcType.DOUBLE, ratioTag.type());
        assertEquals(3.14, ratioTag.expectedValue());
    }

    @Test
    void resolvesNestedPlaceholderValuesAfterStructuralParsing() throws Exception {
        CheckItemQuery query = parser.parse(
                "mat:{material},nameequals:{name}",
                value -> switch (value) {
                    case "{material}" -> "STONE";
                    case "{name}" -> "Nom, avec virgule";
                    default -> value;
                }
        );

        assertEquals("STONE", query.criteria().material());
        assertEquals("Nom, avec virgule", query.criteria().name().values().getFirst());
    }

    @Test
    void parsesOperationsHandsSlotsAndNexo() throws Exception {
        assertEquals(CheckItemOperation.AMOUNT, parse("amount_nexo:custom_sword").operation());
        assertEquals(ItemSelection.OFF_HAND, parse("mat:STONE,inhand:off").selection());
        assertEquals(ItemSelection.slot(12), parse("mat:STONE,inslot:12").selection());
        assertEquals(ItemSelection.MAIN_HAND, parse("getinfo:mainhand_mat:,amt:").selection());
        assertEquals(ItemSelection.MAIN_HAND, parse("getinfo:mainhand_pdc:simple_tag").selection());
    }

    @Test
    void rejectsUnsafeOrInvalidRequests() {
        assertThrows(CheckItemParseException.class, () -> parse(""));
        assertThrows(CheckItemParseException.class, () -> parse("mat:NOT_A_MATERIAL"));
        assertThrows(CheckItemParseException.class, () -> parse("mat:STONE,amt:0"));
        assertThrows(CheckItemParseException.class, () -> parse("mat:STONE,inslot:41"));
        assertThrows(CheckItemParseException.class, () -> parse("remove_amt:2"));
        assertThrows(CheckItemParseException.class, () -> parse("give_matcontains:STONE"));
        assertThrows(CheckItemParseException.class, () -> parse("mat:STONE,enchanted:false"));
        assertThrows(CheckItemParseException.class, () -> parse("give_mat:STONE,potionextended:true"));
        assertThrows(CheckItemParseException.class, () -> parse("getinfo:main_nbtstrings:parent....child"));
        assertThrows(CheckItemParseException.class, () -> parse("mat:STONE,unknown:value"));
    }

    private CheckItemQuery parse(String value) throws CheckItemParseException {
        return parser.parse(value, UnaryOperator.identity());
    }
}
