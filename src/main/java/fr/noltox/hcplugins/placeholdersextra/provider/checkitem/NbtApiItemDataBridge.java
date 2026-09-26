package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.NBTType;
import de.tr7zw.nbtapi.iface.ReadWriteItemNBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.nbtapi.iface.ReadableItemNBT;
import de.tr7zw.nbtapi.iface.ReadableNBT;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

final class NbtApiItemDataBridge implements ItemDataBridge {

    private static boolean matchesAll(ReadableNBT root, List<RawDataCriterion> criteria) {
        return criteria.stream().allMatch(criterion -> {
            Object actual = readValue(root, criterion.path(), criterion.type());
            return criterion.expected().equals(actual);
        });
    }

    private static void applyAll(ReadWriteNBT root, List<RawDataCriterion> criteria) {
        for (RawDataCriterion criterion : criteria) {
            PathTarget target = writableTarget(root, criterion.path());
            switch (criterion.type()) {
                case STRING -> target.compound().setString(target.key(), (String) criterion.expected());
                case INTEGER -> target.compound().setInteger(target.key(), (Integer) criterion.expected());
            }
        }
    }

    private static Object readValue(
            ReadableNBT root,
            String path,
            RawDataCriterion.ValueType type
    ) {
        String[] parts = path.split("\\.\\.", -1);
        ReadableNBT current = root;
        for (int index = 0; index < parts.length - 1; index++) {
            current = current.getCompound(parts[index]);
            if (current == null) {
                return null;
            }
        }
        String key = parts[parts.length - 1];
        NBTType expectedType = type == RawDataCriterion.ValueType.STRING
                ? NBTType.NBTTagString
                : NBTType.NBTTagInt;
        if (!current.hasTag(key, expectedType)) {
            return null;
        }
        return switch (type) {
            case STRING -> current.getString(key);
            case INTEGER -> current.getInteger(key);
        };
    }

    private static PathTarget writableTarget(ReadWriteNBT root, String path) {
        String[] parts = path.split("\\.\\.", -1);
        ReadWriteNBT current = root;
        for (int index = 0; index < parts.length - 1; index++) {
            current = current.getOrCreateCompound(parts[index]);
        }
        return new PathTarget(current, parts[parts.length - 1]);
    }

    @Override
    public boolean matches(ItemStack item, List<RawDataCriterion> criteria) {
        List<RawDataCriterion> customData = criteria.stream()
                .filter(criterion -> criterion.scope() == RawDataCriterion.Scope.CUSTOM_DATA)
                .toList();
        List<RawDataCriterion> components = criteria.stream()
                .filter(criterion -> criterion.scope() == RawDataCriterion.Scope.COMPONENTS)
                .toList();
        return (customData.isEmpty() || NBT.get(
                item,
                (Function<ReadableItemNBT, Boolean>) nbt -> matchesAll(nbt, customData)
        )) && (components.isEmpty() || NBT.getComponents(
                item,
                (Function<ReadableNBT, Boolean>) nbt -> matchesAll(nbt, components)
        ));
    }

    @Override
    public void apply(ItemStack item, List<RawDataCriterion> criteria) {
        List<RawDataCriterion> customData = criteria.stream()
                .filter(criterion -> criterion.scope() == RawDataCriterion.Scope.CUSTOM_DATA)
                .toList();
        List<RawDataCriterion> components = criteria.stream()
                .filter(criterion -> criterion.scope() == RawDataCriterion.Scope.COMPONENTS)
                .toList();
        if (!customData.isEmpty()) {
            NBT.modify(item, (Consumer<ReadWriteItemNBT>) nbt -> applyAll(nbt, customData));
        }
        if (!components.isEmpty()) {
            NBT.modifyComponents(item, (Consumer<ReadWriteNBT>) nbt -> applyAll(nbt, components));
        }
    }

    @Override
    public String read(ItemStack item, InfoRequest request) {
        RawDataCriterion.Scope scope = switch (request.kind()) {
            case CUSTOM_DATA_STRING, CUSTOM_DATA_INTEGER -> RawDataCriterion.Scope.CUSTOM_DATA;
            case COMPONENT_STRING, COMPONENT_INTEGER -> RawDataCriterion.Scope.COMPONENTS;
            default -> throw new IllegalArgumentException("Ce champ n'est pas une donnée brute : " + request.kind());
        };
        RawDataCriterion.ValueType type = switch (request.kind()) {
            case CUSTOM_DATA_STRING, COMPONENT_STRING -> RawDataCriterion.ValueType.STRING;
            case CUSTOM_DATA_INTEGER, COMPONENT_INTEGER -> RawDataCriterion.ValueType.INTEGER;
            default -> throw new IllegalArgumentException("Ce champ n'est pas une donnée brute : " + request.kind());
        };
        Object value = scope == RawDataCriterion.Scope.CUSTOM_DATA
                ? NBT.get(item, (Function<ReadableItemNBT, Object>)
                nbt -> readValue(nbt, request.argument(), type))
                : NBT.getComponents(item, (Function<ReadableNBT, Object>)
                nbt -> readValue(nbt, request.argument(), type));
        return value == null ? "" : value.toString();
    }

    private record PathTarget(ReadWriteNBT compound, String key) {
    }
}
