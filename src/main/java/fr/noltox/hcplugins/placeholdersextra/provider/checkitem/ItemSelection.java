package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

record ItemSelection(Kind kind, int slot) {

    static final ItemSelection INVENTORY = new ItemSelection(Kind.INVENTORY, -1);
    static final ItemSelection HANDS = new ItemSelection(Kind.HANDS, -1);
    static final ItemSelection MAIN_HAND = new ItemSelection(Kind.MAIN_HAND, -1);
    static final ItemSelection OFF_HAND = new ItemSelection(Kind.OFF_HAND, -1);

    static ItemSelection slot(int slot) {
        return new ItemSelection(Kind.SLOT, slot);
    }

    enum Kind {
        INVENTORY,
        HANDS,
        MAIN_HAND,
        OFF_HAND,
        SLOT
    }
}
