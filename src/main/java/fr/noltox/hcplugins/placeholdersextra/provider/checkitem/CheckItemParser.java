package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import org.bukkit.Material;
import org.bukkit.potion.PotionType;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

final class CheckItemParser {

    private static final int LAST_PLAYER_INVENTORY_SLOT = 40;
    private static final Pattern KEY_PATTERN = Pattern.compile("(?:[a-z0-9._-]+:)?[a-z0-9._/-]+");
    private static final Pattern MATERIAL_PATTERN = Pattern.compile("[A-Z0-9_]+");

    private final Predicate<String> materialValidator;
    private final Predicate<String> potionValidator;

    CheckItemParser() {
        this(
                value -> {
                    Material material = Material.matchMaterial(value);
                    return material != null && material.isItem() && !material.isAir();
                },
                value -> {
                    try {
                        PotionType.valueOf(value);
                        return true;
                    } catch (IllegalArgumentException exception) {
                        return false;
                    }
                }
        );
    }

    CheckItemParser(Predicate<String> materialValidator, Predicate<String> potionValidator) {
        this.materialValidator = materialValidator;
        this.potionValidator = potionValidator;
    }

    private static ItemSelection parseInfoSelection(String value) throws CheckItemParseException {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "main", "mainhand" -> ItemSelection.MAIN_HAND;
            case "off", "offhand" -> ItemSelection.OFF_HAND;
            default -> ItemSelection.slot(parseSlot(value));
        };
    }

    private static boolean requiresInfoArgument(InfoRequest.Kind kind) {
        return switch (kind) {
            case CUSTOM_DATA_STRING, CUSTOM_DATA_INTEGER, COMPONENT_STRING, COMPONENT_INTEGER -> true;
            default -> false;
        };
    }

    private static ItemSelection parseHand(String value) throws CheckItemParseException {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "" -> ItemSelection.HANDS;
            case "main", "mainhand" -> ItemSelection.MAIN_HAND;
            case "off", "offhand" -> ItemSelection.OFF_HAND;
            default -> throw new CheckItemParseException("Main invalide : " + value);
        };
    }

    private static int parseSlot(String value) throws CheckItemParseException {
        int slot = parseNonNegativeInteger("slot", value);
        if (slot > LAST_PLAYER_INVENTORY_SLOT) {
            throw new CheckItemParseException("Le slot doit être compris entre 0 et " + LAST_PLAYER_INVENTORY_SLOT + ".");
        }
        return slot;
    }

    private static int parsePositiveInteger(String name, String value) throws CheckItemParseException {
        int parsed = parseInteger(name, value);
        if (parsed < 1) {
            throw new CheckItemParseException(name + " doit être strictement positif.");
        }
        return parsed;
    }

    private static int parseNonNegativeInteger(String name, String value) throws CheckItemParseException {
        int parsed = parseInteger(name, value);
        if (parsed < 0) {
            throw new CheckItemParseException(name + " doit être positif ou nul.");
        }
        return parsed;
    }

    private static int parseInteger(String name, String value) throws CheckItemParseException {
        try {
            return Integer.parseInt(requireValue(name, value));
        } catch (NumberFormatException exception) {
            throw new CheckItemParseException(name + " n'est pas un entier valide.", exception);
        }
    }

    private static boolean parseBoolean(String name, String value) throws CheckItemParseException {
        return switch (requireValue(name, value).toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new CheckItemParseException(name + " doit valoir true ou false.");
        };
    }

    private static boolean requireFlag(String name, String value) throws CheckItemParseException {
        return value.isEmpty() || parseBoolean(name, value);
    }

    private static boolean requireEnabledFlag(String name, String value) throws CheckItemParseException {
        if (!requireFlag(name, value)) {
            throw new CheckItemParseException(name + " ne peut pas être désactivé ; omettez le modificateur.");
        }
        return true;
    }

    private static String parsePotionType(
            String value,
            Predicate<String> potionValidator
    ) throws CheckItemParseException {
        String normalized = requireValue("potiontype", value).toUpperCase(Locale.ROOT);
        if (!potionValidator.test(normalized)) {
            throw new CheckItemParseException("Type de potion inconnu : " + value);
        }
        return normalized;
    }

    private static List<EnchantmentCriterion> parseEnchantments(
            String value,
            UnaryOperator<String> nestedResolver
    ) throws CheckItemParseException {
        List<EnchantmentCriterion> result = new ArrayList<>();
        for (String entry : EscapedText.split(requireValue("enchantments", value), ';')) {
            String[] pair = EscapedText.splitFirst(entry, '=');
            String key = nestedResolver.apply(pair[0]).toLowerCase(Locale.ROOT);
            if (!KEY_PATTERN.matcher(key).matches()) {
                throw new CheckItemParseException("Clé d'enchantement invalide : " + key);
            }
            Integer level = pair[1].isEmpty() ? null : parsePositiveInteger("niveau d'enchantement", nestedResolver.apply(pair[1]));
            result.add(new EnchantmentCriterion(key, level));
        }
        return result;
    }

    private static List<RawDataCriterion> parseRawData(
            RawDataCriterion.Scope scope,
            RawDataCriterion.ValueType type,
            String value,
            UnaryOperator<String> nestedResolver
    ) throws CheckItemParseException {
        List<RawDataCriterion> result = new ArrayList<>();
        for (String entry : EscapedText.split(requireValue("données NBT", value), ';')) {
            String[] pair = EscapedText.splitFirst(entry, '=');
            String path = validateRawPath("données NBT", nestedResolver.apply(pair[0]));
            String rawExpected = nestedResolver.apply(pair[1]);
            if (rawExpected.isEmpty()) {
                throw new CheckItemParseException("Une donnée NBT doit respecter chemin=valeur.");
            }
            Object expected = type == RawDataCriterion.ValueType.INTEGER
                    ? parseInteger("valeur NBT", rawExpected)
                    : rawExpected;
            result.add(new RawDataCriterion(scope, type, path, expected));
        }
        return result;
    }

    private static String validateRawPath(String name, String path) throws CheckItemParseException {
        String required = requireValue(name, path);
        if (List.of(required.split("\\.\\.", -1)).stream().anyMatch(String::isBlank)) {
            throw new CheckItemParseException("Chemin de donnée brute invalide : " + path);
        }
        return required;
    }

    private static List<String> resolveAll(List<String> values, UnaryOperator<String> resolver) {
        return values.stream().map(resolver).toList();
    }

    private static String requireValue(String name, String value) throws CheckItemParseException {
        if (value == null || value.isBlank()) {
            throw new CheckItemParseException("Valeur absente pour " + name + ".");
        }
        return value;
    }

    CheckItemQuery parse(String input, UnaryOperator<String> nestedResolver)
            throws CheckItemParseException {
        if (input == null || input.isBlank()) {
            throw new CheckItemParseException("La requête CheckItem est vide.");
        }

        ParsedOperation parsedOperation = parseOperation(input);
        Builder builder = new Builder(parsedOperation.operation(), parsedOperation.selection());
        if (builder.operation == CheckItemOperation.GET_INFO) {
            parseInfoRequests(parsedOperation.modifiers(), nestedResolver, builder);
        } else {
            parseCriteria(parsedOperation.modifiers(), nestedResolver, builder);
        }
        builder.reportAmount = parsedOperation.reportAmount();
        return builder.build();
    }

    private ParsedOperation parseOperation(String input) throws CheckItemParseException {
        if (input.startsWith("getinfo:")) {
            int separator = input.indexOf('_');
            String slot = separator < 0 ? input.substring("getinfo:".length())
                    : input.substring("getinfo:".length(), separator);
            return new ParsedOperation(
                    CheckItemOperation.GET_INFO,
                    parseInfoSelection(slot),
                    separator < 0 ? "" : input.substring(separator + 1),
                    false
            );
        }
        if (input.startsWith("amount_remove_")) {
            return new ParsedOperation(
                    CheckItemOperation.REMOVE,
                    ItemSelection.INVENTORY,
                    input.substring("amount_remove_".length()),
                    true
            );
        }
        if (input.startsWith("amount_")) {
            return new ParsedOperation(
                    CheckItemOperation.AMOUNT,
                    ItemSelection.INVENTORY,
                    input.substring("amount_".length()),
                    false
            );
        }
        if (input.startsWith("give_")) {
            return new ParsedOperation(
                    CheckItemOperation.GIVE,
                    ItemSelection.INVENTORY,
                    input.substring("give_".length()),
                    false
            );
        }
        if (input.startsWith("remove_")) {
            return new ParsedOperation(
                    CheckItemOperation.REMOVE,
                    ItemSelection.INVENTORY,
                    input.substring("remove_".length()),
                    false
            );
        }
        return new ParsedOperation(CheckItemOperation.CHECK, ItemSelection.INVENTORY, input, false);
    }

    private void parseCriteria(
            String modifiers,
            UnaryOperator<String> nestedResolver,
            Builder builder
    ) throws CheckItemParseException {
        if (modifiers.isBlank()) {
            throw new CheckItemParseException("Aucun modificateur CheckItem n'est défini.");
        }
        for (String token : EscapedText.split(modifiers, ',')) {
            if (token.isBlank()) {
                throw new CheckItemParseException("Un modificateur CheckItem est vide.");
            }
            String[] pair = EscapedText.splitFirst(token, ':');
            String name = pair[0].toLowerCase(Locale.ROOT);
            String value = nestedResolver.apply(pair[1]);
            switch (name) {
                case "mat" -> builder.material(value, materialValidator);
                case "matcontains" -> builder.materialContains(requireValue(name, value));
                case "amt" -> builder.amount(parsePositiveInteger(name, value));
                case "namecontains" -> builder.name(ItemCriteria.TextCriterion.Mode.CONTAINS, value);
                case "namestartswith" -> builder.name(ItemCriteria.TextCriterion.Mode.STARTS_WITH, value);
                case "nameequals" -> builder.name(ItemCriteria.TextCriterion.Mode.EQUALS, value);
                case "lorecontains" ->
                        builder.lore(ItemCriteria.TextCriterion.Mode.CONTAINS, List.of(requireValue(name, value)));
                case "loreequals" -> builder.lore(
                        ItemCriteria.TextCriterion.Mode.EQUALS,
                        resolveAll(EscapedText.split(requireValue(name, value), '|'), nestedResolver)
                );
                case "custommodeldata" -> builder.customModelData(parseNonNegativeInteger(name, value));
                case "enchantments" -> builder.enchantments(parseEnchantments(value, nestedResolver));
                case "enchanted" -> builder.enchanted(requireEnabledFlag(name, value));
                case "potiontype" -> builder.potionType(parsePotionType(value, potionValidator));
                case "potionextended" -> builder.potionExtended(parseBoolean(name, value));
                case "potionupgraded" -> builder.potionUpgraded(parseBoolean(name, value));
                case "nexo" -> builder.nexoId(requireValue(name, value));
                case "nbtstrings", "customdatastrings" -> builder.rawData(
                        parseRawData(RawDataCriterion.Scope.CUSTOM_DATA, RawDataCriterion.ValueType.STRING, value, nestedResolver)
                );
                case "nbtints", "customdataints" -> builder.rawData(
                        parseRawData(RawDataCriterion.Scope.CUSTOM_DATA, RawDataCriterion.ValueType.INTEGER, value, nestedResolver)
                );
                case "componentstrings" -> builder.rawData(
                        parseRawData(RawDataCriterion.Scope.COMPONENTS, RawDataCriterion.ValueType.STRING, value, nestedResolver)
                );
                case "componentints" -> builder.rawData(
                        parseRawData(RawDataCriterion.Scope.COMPONENTS, RawDataCriterion.ValueType.INTEGER, value, nestedResolver)
                );
                case "inhand" -> builder.selection(parseHand(value));
                case "inslot" -> builder.selection(ItemSelection.slot(parseSlot(value)));
                case "strict" -> builder.strict(requireFlag(name, value));
                default -> throw new CheckItemParseException("Modificateur CheckItem inconnu : " + name);
            }
        }
    }

    private void parseInfoRequests(
            String modifiers,
            UnaryOperator<String> nestedResolver,
            Builder builder
    ) throws CheckItemParseException {
        if (modifiers.isBlank()) {
            builder.infoRequests.addAll(List.of(
                    new InfoRequest(InfoRequest.Kind.MATERIAL, ""),
                    new InfoRequest(InfoRequest.Kind.AMOUNT, ""),
                    new InfoRequest(InfoRequest.Kind.NAME, ""),
                    new InfoRequest(InfoRequest.Kind.LORE, ""),
                    new InfoRequest(InfoRequest.Kind.CUSTOM_MODEL_DATA, ""),
                    new InfoRequest(InfoRequest.Kind.ENCHANTMENTS, ""),
                    new InfoRequest(InfoRequest.Kind.POTION_TYPE, ""),
                    new InfoRequest(InfoRequest.Kind.NEXO, "")
            ));
            return;
        }
        for (String token : EscapedText.split(modifiers, ',')) {
            String[] pair = EscapedText.splitFirst(token, ':');
            String name = pair[0].toLowerCase(Locale.ROOT);
            String argument = nestedResolver.apply(pair[1]);
            InfoRequest.Kind kind = switch (name) {
                case "mat", "matcontains" -> InfoRequest.Kind.MATERIAL;
                case "amt" -> InfoRequest.Kind.AMOUNT;
                case "namecontains", "namestartswith", "nameequals" -> InfoRequest.Kind.NAME;
                case "lorecontains", "loreequals" -> InfoRequest.Kind.LORE;
                case "custommodeldata" -> InfoRequest.Kind.CUSTOM_MODEL_DATA;
                case "enchantments", "enchanted" -> InfoRequest.Kind.ENCHANTMENTS;
                case "potiontype", "potionextended", "potionupgraded" -> InfoRequest.Kind.POTION_TYPE;
                case "nexo" -> InfoRequest.Kind.NEXO;
                case "nbtstrings", "customdatastrings" -> InfoRequest.Kind.CUSTOM_DATA_STRING;
                case "nbtints", "customdataints" -> InfoRequest.Kind.CUSTOM_DATA_INTEGER;
                case "componentstrings" -> InfoRequest.Kind.COMPONENT_STRING;
                case "componentints" -> InfoRequest.Kind.COMPONENT_INTEGER;
                default -> throw new CheckItemParseException("Champ getinfo inconnu : " + name);
            };
            if (requiresInfoArgument(kind)) {
                argument = validateRawPath(name, argument);
            }
            builder.infoRequests.add(new InfoRequest(kind, argument));
        }
    }

    private record ParsedOperation(
            CheckItemOperation operation,
            ItemSelection selection,
            String modifiers,
            boolean reportAmount
    ) {
    }

    private static final class Builder {

        private final CheckItemOperation operation;
        private final Set<String> unique = new HashSet<>();
        private final List<EnchantmentCriterion> enchantments = new ArrayList<>();
        private final List<RawDataCriterion> rawData = new ArrayList<>();
        private final List<InfoRequest> infoRequests = new ArrayList<>();
        private ItemSelection selection;
        private String material;
        private String materialContains;
        private Integer amount;
        private ItemCriteria.TextCriterion name;
        private ItemCriteria.TextCriterion lore;
        private Integer customModelData;
        private boolean enchanted;
        private String potionType;
        private Boolean potionExtended;
        private Boolean potionUpgraded;
        private String nexoId;
        private boolean strict;
        private boolean reportAmount;
        private int substantiveCriteria;

        private Builder(CheckItemOperation operation, ItemSelection selection) {
            this.operation = operation;
            this.selection = selection;
        }

        private static ItemCriteria emptyCriteria() {
            return new ItemCriteria(null, null, null, null, null, null, List.of(), false, null, null, List.of(), false);
        }

        private void material(
                String value,
                Predicate<String> materialValidator
        ) throws CheckItemParseException {
            unique("material");
            String parsed = requireValue("mat", value).toUpperCase(Locale.ROOT);
            if (!MATERIAL_PATTERN.matcher(parsed).matches() || !materialValidator.test(parsed)) {
                throw new CheckItemParseException("Matériau d'item inconnu : " + value);
            }
            material = parsed;
            substantiveCriteria++;
        }

        private void materialContains(String value) throws CheckItemParseException {
            unique("material");
            materialContains = value.toUpperCase(Locale.ROOT);
            substantiveCriteria++;
        }

        private void amount(int value) throws CheckItemParseException {
            unique("amount");
            amount = value;
        }

        private void name(ItemCriteria.TextCriterion.Mode mode, String value) throws CheckItemParseException {
            unique("name");
            name = new ItemCriteria.TextCriterion(mode, List.of(requireValue("nom", value)));
            substantiveCriteria++;
        }

        private void lore(ItemCriteria.TextCriterion.Mode mode, List<String> values) throws CheckItemParseException {
            unique("lore");
            if (values.isEmpty() || values.stream().anyMatch(String::isEmpty)) {
                throw new CheckItemParseException("Le lore attendu est vide.");
            }
            lore = new ItemCriteria.TextCriterion(mode, values);
            substantiveCriteria++;
        }

        private void customModelData(int value) throws CheckItemParseException {
            unique("custommodeldata");
            customModelData = value;
            substantiveCriteria++;
        }

        private void enchantments(List<EnchantmentCriterion> values) throws CheckItemParseException {
            unique("enchantments");
            enchantments.addAll(values);
            substantiveCriteria++;
        }

        private void enchanted(boolean value) throws CheckItemParseException {
            unique("enchanted");
            enchanted = value;
            substantiveCriteria++;
        }

        private void potionType(String value) throws CheckItemParseException {
            unique("potiontype");
            potionType = value;
            substantiveCriteria++;
        }

        private void potionExtended(boolean value) throws CheckItemParseException {
            unique("potionextended");
            potionExtended = value;
            substantiveCriteria++;
        }

        private void potionUpgraded(boolean value) throws CheckItemParseException {
            unique("potionupgraded");
            potionUpgraded = value;
            substantiveCriteria++;
        }

        private void nexoId(String value) throws CheckItemParseException {
            unique("nexo");
            nexoId = value;
            substantiveCriteria++;
        }

        private void rawData(List<RawDataCriterion> values) {
            rawData.addAll(values);
            substantiveCriteria += values.size();
        }

        private void selection(ItemSelection value) throws CheckItemParseException {
            unique("selection");
            selection = value;
        }

        private void strict(boolean value) throws CheckItemParseException {
            unique("strict");
            strict = value;
        }

        private CheckItemQuery build() throws CheckItemParseException {
            if (operation == CheckItemOperation.GET_INFO) {
                return new CheckItemQuery(operation, selection, emptyCriteria(), infoRequests, false);
            }
            if (substantiveCriteria == 0) {
                throw new CheckItemParseException("La requête ne contient aucun critère d'item.");
            }
            if (material != null && nexoId != null) {
                throw new CheckItemParseException("mat et nexo ne peuvent pas être combinés.");
            }
            if (Boolean.TRUE.equals(potionExtended) && Boolean.TRUE.equals(potionUpgraded)) {
                throw new CheckItemParseException("Une potion ne peut pas être longue et renforcée simultanément.");
            }
            if (operation == CheckItemOperation.GIVE) {
                validateGive();
            }
            var potion = potionType == null && potionExtended == null && potionUpgraded == null
                    ? null
                    : new ItemCriteria.PotionCriterion(potionType, potionExtended, potionUpgraded);
            var criteria = new ItemCriteria(
                    material,
                    materialContains,
                    amount,
                    name,
                    lore,
                    customModelData,
                    enchantments,
                    enchanted,
                    potion,
                    nexoId,
                    rawData,
                    strict
            );
            return new CheckItemQuery(operation, selection, criteria, List.of(), reportAmount);
        }

        private void validateGive() throws CheckItemParseException {
            if (material == null && nexoId == null) {
                throw new CheckItemParseException("give exige un modificateur mat ou nexo.");
            }
            if (selection != ItemSelection.INVENTORY || materialContains != null
                    || name != null && name.mode() != ItemCriteria.TextCriterion.Mode.EQUALS
                    || lore != null && lore.mode() != ItemCriteria.TextCriterion.Mode.EQUALS
                    || enchanted || strict) {
                throw new CheckItemParseException("Un modificateur de recherche ne peut pas construire un item à donner.");
            }
            if ((potionExtended != null || potionUpgraded != null) && potionType == null) {
                throw new CheckItemParseException("give exige potiontype avec potionextended ou potionupgraded.");
            }
        }

        private void unique(String category) throws CheckItemParseException {
            if (!unique.add(category)) {
                throw new CheckItemParseException("Modificateur dupliqué : " + category);
            }
        }
    }
}
