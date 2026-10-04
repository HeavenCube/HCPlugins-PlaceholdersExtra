package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CustomModelDataTest {

    @Test
    void appliesTheEditedSnapshotAndPreservesOtherModelChannels() {
        var floats = new AtomicReference<>(List.of(1.0F));
        var applied = new AtomicReference<CustomModelDataComponent>();
        var component = (CustomModelDataComponent) Proxy.newProxyInstance(
                CustomModelDataComponent.class.getClassLoader(), new Class<?>[]{CustomModelDataComponent.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getFloats" -> floats.get();
                    case "setFloats" -> {
                        @SuppressWarnings("unchecked")
                        List<Float> replacement = (List<Float>) arguments[0];
                        floats.set(List.copyOf(replacement));
                        yield null;
                    }
                    case "getStrings" -> List.of("custom-model");
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var meta = (ItemMeta) Proxy.newProxyInstance(ItemMeta.class.getClassLoader(), new Class<?>[]{ItemMeta.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getCustomModelDataComponent" -> component;
                    case "setCustomModelDataComponent" -> {
                        applied.set((CustomModelDataComponent) arguments[0]);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });

        CheckItemService.applyCustomModelData(meta, 123);
        assertSame(component, applied.get());
        assertEquals(List.of(123.0F), applied.get().getFloats());
        assertEquals(List.of("custom-model"), applied.get().getStrings());
    }
}
