package io.github.rypofalem.armorstandeditor;


import java.util.logging.Level;

public class Debug {
    private static final ArmorStandEditorPlugin plugin = ArmorStandEditorPlugin.instance();

    public static void log(String msg) {
        if (plugin.isDebug()) {
            plugin.getLogger().log(Level.INFO, "[ArmorStandEditor-Debug] {0}", msg);
        }
    }
}
