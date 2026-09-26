package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

final class MatchAccumulator {

    private int total;

    void add(int amount) {
        total = Math.addExact(total, amount);
    }

    int total() {
        return total;
    }

    boolean satisfies(int requested, boolean strict) {
        return strict ? total == requested : total >= requested;
    }
}
