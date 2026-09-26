package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

record RawDataCriterion(Scope scope, ValueType type, String path, Object expected) {

    enum Scope {
        CUSTOM_DATA,
        COMPONENTS
    }

    enum ValueType {
        STRING,
        INTEGER
    }
}
