package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

record InfoRequest(Kind kind, String argument) {

    enum Kind {
        MATERIAL,
        AMOUNT,
        NAME,
        LORE,
        CUSTOM_MODEL_DATA,
        ENCHANTMENTS,
        POTION_TYPE,
        NEXO,
        CUSTOM_DATA_STRING,
        CUSTOM_DATA_INTEGER,
        COMPONENT_STRING,
        COMPONENT_INTEGER
    }
}
