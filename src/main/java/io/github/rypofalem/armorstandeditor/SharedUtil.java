package io.github.rypofalem.armorstandeditor;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SharedUtil {
    private static final ArmorStandEditorPlugin plugin = ArmorStandEditorPlugin.instance();
    private static final Set<UUID> IN_USE = ConcurrentHashMap.newKeySet();
    private static final NamespacedKey LOCK_KEY = new NamespacedKey(plugin, "locked");

    public static boolean isLocked(ArmorStand stand) {
        Boolean lock = stand.getPersistentDataContainer().get(LOCK_KEY, PersistentDataType.BOOLEAN);

        return lock != null && lock;
    }

    public static void setLocked(ArmorStand stand, boolean value) {
        PersistentDataContainer pdc = stand.getPersistentDataContainer();
        pdc.set(LOCK_KEY, PersistentDataType.BOOLEAN, value);
    }

    public static boolean isInUse(UUID stand) {
        return IN_USE.contains(stand);
    }

    public static void setInUse(UUID stand, boolean value) {
        if (value) {
            IN_USE.add(stand);
        } else {
            IN_USE.remove(stand);
        }
    }
}
