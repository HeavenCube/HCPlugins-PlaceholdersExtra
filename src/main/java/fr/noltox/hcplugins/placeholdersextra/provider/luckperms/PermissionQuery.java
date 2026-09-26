package fr.noltox.hcplugins.placeholdersextra.provider.luckperms;

import java.util.*;

/**
 * Requête de comptage normalisée issue des paramètres d'un placeholder.
 *
 * @param permission nœud de permission en minuscules
 * @param contexts   contextes triés et dédupliqués
 */
record PermissionQuery(String permission, List<ContextEntry> contexts) {

    static final int MAX_PARAMETER_LENGTH = 512;
    static final int MAX_PERMISSION_LENGTH = 256;
    static final int MAX_CONTEXT_COUNT = 16;

    private static final Comparator<ContextEntry> CONTEXT_ORDER =
            Comparator.comparing(ContextEntry::key).thenComparing(ContextEntry::value);

    PermissionQuery {
        Objects.requireNonNull(permission, "permission");
        contexts = List.copyOf(contexts);
    }

    /**
     * Analyse {@code permission[:clé=valeur,...]}.
     *
     * @param parameters paramètres transmis par PlaceholderAPI
     * @return la requête normalisée, ou un résultat vide si la syntaxe est invalide
     */
    static Optional<PermissionQuery> parse(String parameters) {
        if (parameters == null || parameters.isBlank() || parameters.length() > MAX_PARAMETER_LENGTH) {
            return Optional.empty();
        }

        var input = parameters.strip();
        var separatorIndex = input.indexOf(':');
        var rawPermission = separatorIndex < 0 ? input : input.substring(0, separatorIndex);
        var permission = rawPermission.strip().toLowerCase(Locale.ROOT);

        if (!isValidPermission(permission)) {
            return Optional.empty();
        }

        if (separatorIndex < 0) {
            return Optional.of(new PermissionQuery(permission, List.of()));
        }

        var rawContexts = input.substring(separatorIndex + 1);
        if (rawContexts.isBlank()) {
            return Optional.empty();
        }

        var pairs = rawContexts.split(",", -1);
        if (pairs.length > MAX_CONTEXT_COUNT) {
            return Optional.empty();
        }

        var contexts = new TreeSet<>(CONTEXT_ORDER);
        for (var pair : pairs) {
            var separator = pair.indexOf('=');
            if (separator <= 0 || separator == pair.length() - 1) {
                return Optional.empty();
            }

            var key = pair.substring(0, separator).strip().toLowerCase(Locale.ROOT);
            var value = pair.substring(separator + 1).strip().toLowerCase(Locale.ROOT);
            if (!isValidContextPart(key) || !isValidContextPart(value)) {
                return Optional.empty();
            }

            contexts.add(new ContextEntry(key, value));
        }

        if (contexts.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new PermissionQuery(permission, new ArrayList<>(contexts)));
    }

    private static boolean isValidPermission(String permission) {
        return !permission.isEmpty()
                && permission.length() <= MAX_PERMISSION_LENGTH
                && permission.codePoints().noneMatch(character ->
                Character.isWhitespace(character) || Character.isISOControl(character)
        );
    }

    private static boolean isValidContextPart(String part) {
        return !part.isEmpty() && part.codePoints().noneMatch(Character::isISOControl);
    }

    boolean isContextual() {
        return !contexts.isEmpty();
    }

    /**
     * Paire clé/valeur d'un contexte LuckPerms.
     *
     * @param key   clé normalisée en minuscules
     * @param value valeur normalisée en minuscules
     */
    record ContextEntry(String key, String value) {

        ContextEntry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
        }
    }
}
