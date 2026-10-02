package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import org.bukkit.NamespacedKey;

import java.util.Objects;

record PdcCriterion(
        NamespacedKey key,
        PdcType type,
        Object expectedValue
) {

    PdcCriterion {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
    }

    boolean isPresenceOnly() {
        return expectedValue == null;
    }

    Object expected() {
        return expectedValue;
    }

    String path() {
        return key.toString();
    }

    enum PdcType {
        ANY,
        STRING,
        INTEGER,
        BYTE,
        DOUBLE,
        BOOLEAN
    }
}
