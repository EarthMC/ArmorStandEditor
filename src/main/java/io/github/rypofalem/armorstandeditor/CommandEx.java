/*
 * ArmorStandEditor: Bukkit plugin to allow editing armor stand attributes
 * Copyright (C) 2016-2023  RypoFalem
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package io.github.rypofalem.armorstandeditor;

import io.github.rypofalem.armorstandeditor.modes.AdjustmentMode;
import io.github.rypofalem.armorstandeditor.modes.Axis;
import io.github.rypofalem.armorstandeditor.modes.EditMode;
import io.github.rypofalem.armorstandeditor.utils.MinecraftVersion;
import io.github.rypofalem.armorstandeditor.utils.Util;
import io.github.rypofalem.armorstandeditor.utils.VersionUtil;

import net.kyori.adventure.text.Component;

import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.EulerAngle;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class CommandEx implements CommandExecutor {
    private final ArmorStandEditorPlugin plugin;
    private final Component listMode = Component.text("/ase mode <" + Util.getEnumList(EditMode.class) + ">", NamedTextColor.YELLOW);
    private final Component listAxis = Component.text("/ase axis <" + Util.getEnumList(Axis.class) + ">", NamedTextColor.YELLOW);
    private final Component listAdjustment = Component.text("/ase adj <" + Util.getEnumList(AdjustmentMode.class) + ">", NamedTextColor.YELLOW);
    private final Component resetWithinRange = Component.text("/ase resetWithinRange <range>", NamedTextColor.YELLOW);
    private final Component give = Component.text("/ase give", NamedTextColor.YELLOW);
    private final Component listSlot = Component.text("/ase slot <1-9>", NamedTextColor.YELLOW);
    private final Component help = Component.text("/ase help or /ase ?", NamedTextColor.YELLOW);
    private final Component version = Component.text("/ase version", NamedTextColor.YELLOW);
    private final Component update = Component.text("/ase update", NamedTextColor.YELLOW);
    private final Component reload = Component.text("/ase reload", NamedTextColor.YELLOW);
    private final Component givePlayerHead = Component.text("/ase playerhead", NamedTextColor.YELLOW);
    private final Component getArmorStats = Component.text("/ase stats", NamedTextColor.YELLOW);

    public CommandEx(ArmorStandEditorPlugin armorStandEditorPlugin) {
        this.plugin = armorStandEditorPlugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull[] args) {

        if (!(sender instanceof Player player)) { //Fix to Support #267
            Debug.log("Sender is CONSOLE!");
            if (args.length == 0) {
                sender.sendMessage(version);
                sender.sendMessage(help);
                sender.sendMessage(reload);
            } else {
                switch (args[0].toLowerCase()) {
                    case "reload" -> commandReloadConsole(sender);
                    case "help", "?" -> commandHelpConsole(sender);
                    case "version" -> commandVersionConsole(sender);
                    default -> sender.sendMessage(plugin.getLang().getMessage("noconsolecom", "warn"));
                }
            }
            return true;
        }

        if (!getPermissionBasic(player)) {
            Debug.log("Sender is Player but asedit.basic is" + getPermissionBasic(player));
            sender.sendMessage(plugin.getLang().getMessage("nopermoption", "warn", "basic"));
        } else {
            Debug.log("Sender is Player and asedit.basic is " + getPermissionBasic(player));
            if (args.length == 0) {
                player.sendMessage(listMode);
                player.sendMessage(listAxis);
                player.sendMessage(listSlot);
                player.sendMessage(listAdjustment);
                player.sendMessage(version);
                player.sendMessage(update);
                player.sendMessage(help);
                player.sendMessage(reload);
                player.sendMessage(givePlayerHead);
                player.sendMessage(give);
                player.sendMessage(getArmorStats);
                return true;
            }
            switch (args[0].toLowerCase()) {
                case "mode" -> commandMode(player, args);
                case "axis" -> commandAxis(player, args);
                case "adj" -> commandAdj(player, args);
                case "slot" -> commandSlot(player, args);
                case "help", "?" -> commandHelp(player);
                case "version" -> commandVersion(player);
                case "update" -> commandUpdate(player);
                case "playerhead" -> commandGivePlayerHead(player);
                case "give" -> commandGive(player);
                case "reload" -> commandReload(player);
                case "stats" -> commandStats(player);
                case "resetwithinrange" -> commandResetWithinRange(player, args);
                default -> {
                    player.sendMessage(listMode);
                    player.sendMessage(listAxis);
                    player.sendMessage(listSlot);
                    player.sendMessage(listAdjustment);
                    player.sendMessage(version);
                    player.sendMessage(update);
                    player.sendMessage(help);
                    player.sendMessage(reload);
                    player.sendMessage(givePlayerHead);
                    player.sendMessage(give);
                    player.sendMessage(getArmorStats);
                }
            }
        }
        return true;
    }


    // Implemented to fix:
    // https://github.com/Wolfieheart/ArmorStandEditor-Issues/issues/35 &
    // https://github.com/Wolfieheart/ArmorStandEditor-Issues/issues/30 - See Remarks OTHER
    @SuppressWarnings("UnstableApiUsage")
    private void commandGive(Player player) {
        if (player.hasPermission("asedit.give")) {
            ItemStack stack = new ItemStack(plugin.getEditTool());
            ItemMeta meta = stack.getItemMeta();

            CustomModelDataComponent dC = meta.getCustomModelDataComponent();
            dC.setFloats(List.of((float) plugin.getCustomModelDataValue()));
            meta.setCustomModelDataComponent(dC);

            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            stack.setItemMeta(meta);
            player.getInventory().addItem(stack);
            player.sendMessage(plugin.getLang().getMessage("give", "info"));
        } else {
            player.sendMessage(plugin.getLang().getMessage("nogive", "warn"));
        }
    }


    private void commandResetWithinRange(Player player, String[] args) {
        if (args.length == 1) {
            player.sendMessage(resetWithinRange);
            return;
        }

        if (player.hasPermission("asedit.reset.withinRange")) {
            Debug.log(" Player '" + player.getName() + "' is resetting armor stands within range.");
            double range = Double.parseDouble(args[1]);
            Debug.log(" Range Chosen: " + range);

            if (range > plugin.getMaxResetRange()) {
                player.sendMessage(plugin.getLang().getMessage("resetwithinrangeexceed", "warn"));
                return;
            }

            Location playerLoc = player.getLocation();
            plugin.editorManager.getPlayerEditor(player.getUniqueId()).resetArmorStandsWithinRange(playerLoc, range);
            player.sendMessage(plugin.getLang().getMessage("resetwithinrange", "info"));
        } else {
            player.sendMessage(plugin.getLang().getMessage("nopermoption", "warn", "resetwithinrange"));
        }
    }

    private void commandGivePlayerHead(Player player) {
        if (player.hasPermission("asedit.head") && plugin.getAllowedToRetrieveOwnPlayerHead()) {
            Debug.log("Creating a player head for the Player '" + player.getName() + "'");
            ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            meta.setOwningPlayer(player);
            item.setItemMeta(meta);
            player.getInventory().addItem(item);
            player.sendMessage(plugin.getLang().getMessage("playerhead", "info"));
        } else {
            player.sendMessage(plugin.getLang().getMessage("playerheaderror", "warn"));
        }
    }

    private void commandSlot(Player player, String[] args) {

        if (args.length <= 1) {
            player.sendMessage(plugin.getLang().getMessage("noslotnumcom", "warn"));
            player.sendMessage(listSlot);
        }

        if (args.length > 1) {
            try {
                byte slot = (byte) (Byte.parseByte(args[1]) - 0b1);
                if (slot >= 0 && slot < 9) {
                    Debug.log("Player has chosen slot: " + slot);
                    plugin.editorManager.getPlayerEditor(player.getUniqueId()).setCopySlot(slot);
                } else {
                    player.sendMessage(listSlot);
                }

            } catch (NumberFormatException _) {
                player.sendMessage(listSlot);
            }
        }
    }

    private void commandAdj(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(plugin.getLang().getMessage("noadjcom", "warn"));
            player.sendMessage(listAdjustment);
        }

        if (args.length > 1) {
            for (AdjustmentMode adj : AdjustmentMode.values()) {
                if (adj.toString().toLowerCase().contentEquals(args[1].toLowerCase())) {
                    plugin.editorManager.getPlayerEditor(player.getUniqueId()).setAdjMode(adj);
                    return;
                }
            }
            player.sendMessage(listAdjustment);
        }
    }

    private void commandAxis(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(plugin.getLang().getMessage("noaxiscom", "warn"));
            player.sendMessage(listAxis);
        }

        if (args.length > 1) {
            for (Axis axis : Axis.values()) {
                if (axis.toString().toLowerCase().contentEquals(args[1].toLowerCase())) {
                    Debug.log("Player '" + player.getName() + "' sets the axis to " + axis);
                    plugin.editorManager.getPlayerEditor(player.getUniqueId()).setAxis(axis);
                    return;
                }
            }
            player.sendMessage(listAxis);
        }
    }

    private void commandMode(Player player, String[] args) {
        if (args.length == 1) {
            player.sendMessage(plugin.getLang().getMessage("nomodecom", "warn"));
            player.sendMessage(listMode);
            return; // early return lets us drop the second `if` entirely
        }

        EditMode matched = findMatchingMode(args[1]);
        if (matched == null) return;

        if (!isVisibilityAllowed(player, args[1])) return;

        plugin.editorManager.getPlayerEditor(player.getUniqueId()).setMode(matched);
        Debug.log("Player '" + player.getName() + "' chose the mode: " + matched);
    }

    private void commandHelp(Player player) {
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        player.sendMessage(plugin.getLang().getMessage("help", "info", plugin.editTool.name()));
        player.sendMessage(Component.empty());
        player.sendMessage(plugin.getLang().getMessage("helptips", "info"));
        player.sendMessage(Component.empty());
        player.sendMessage(plugin.getLang().getMessage("helpurl", ""));
        player.sendMessage(plugin.getLang().getMessage("helpdiscord", ""));
    }

    private void commandHelpConsole(CommandSender sender) {
        sender.sendMessage(plugin.getLang().getMessage("help", "info", plugin.editTool.name()));
        sender.sendMessage(Component.empty());
        sender.sendMessage(plugin.getLang().getMessage("helptips", "info"));
        sender.sendMessage(Component.empty());
        sender.sendMessage(plugin.getLang().getMessage("helpurl", "info"));
        sender.sendMessage(plugin.getLang().getMessage("helpdiscord", "info"));
    }

    private void commandUpdate(Player player) {
        if (!(checkPermission(player, "update", true))) return;

        Debug.log("Current ArmorStandEditor Version is: " + ArmorStandEditorPlugin.ASE_VERSION);

        if (plugin.getRunTheUpdateChecker()) {
            Debug.log("Plugin is on Server: Paper/Spigot or a fork thereof.");
            new UpdateChecker(plugin).checkForUpdatesAndNotify(player);
        } else {
            player.sendMessage(Component.text("[ArmorStandEditor] Update Checker is not enabled on this server.", NamedTextColor.YELLOW));
        }
    }

    private void commandVersion(Player player) {
        Debug.log("Player '" + player.getName() + "' permission check for asedit.update: " + getPermissionUpdate(player));
        if (!getPermissionUpdate(player)) return;
        String verString = plugin.getASEVersion();
        player.sendMessage(Component.text("[ArmorStandEditor] Version: " + verString, NamedTextColor.YELLOW));
    }

    private void commandVersionConsole(CommandSender sender) {
        String verString = plugin.getASEVersion();
        sender.sendMessage(Component.text("[ArmorStandEditor] Version: " + verString, NamedTextColor.YELLOW));
    }

    private void commandReload(Player player) {
        Debug.log("Player '" + player.getName() + "' permission check for asedit.reload: " + getPermissionReload(player));

        if (!(getPermissionReload(player))) return;
        Debug.log("Performing reload of config.yml");
        plugin.performReload();
        player.sendMessage(plugin.getLang().getMessage("reloaded", ""));
    }

    private void commandReloadConsole(CommandSender sender) {
        Debug.log("Console has decided to reload the plugin....");
        plugin.performReload();
        sender.sendMessage(plugin.getLang().getMessage("reloaded", "info"));
    }

    private void commandStats(Player player) {
        Debug.log("Player '" + player.getName() + "' permission check for asedit.stats: " + getPermissionStats(player));

        if (getPermissionStats(player)) {
            for (Entity e : player.getNearbyEntities(1, 1, 1)) {
                if (e instanceof ArmorStand as) {
                    sendArmorStandStats(player, as);
                }
            }
        } else {
            player.sendMessage(plugin.getLang().getMessage("norangeforstats", "warn"));
        }
    }


    private boolean checkPermission(Player player, String permName, boolean sendMessageOnInvalidation) {
        if (permName.equalsIgnoreCase("paste")) {
            permName = "copy";
        }
        if (player.hasPermission("asedit." + permName.toLowerCase())) {
            return true;
        } else {
            if (sendMessageOnInvalidation) {
                player.sendMessage(plugin.getLang().getMessage("noperm", "warn"));
            }
            return false;
        }
    }

    private boolean getPermissionBasic(Player player) {
        return checkPermission(player, "basic", false);
    }

    private boolean getPermissionUpdate(Player player) {
        return checkPermission(player, "update", false);
    }

    private boolean getPermissionReload(Player player) {
        return checkPermission(player, "reload", false);
    }

    private boolean getPermissionStats(Player player) {
        return checkPermission(player, "stats", false);
    }

    /*
     * Helper Functions for Stats
     */
    private Component label(String label, Object value) {
        return Component.text(label + ": ", NamedTextColor.YELLOW)
            .append(Component.text(String.valueOf(value), NamedTextColor.AQUA));
    }

    private void sendArmorStandStats(Player player, ArmorStand as) {
        PoseData pose = PoseData.from(as);
        Location loc = as.getLocation();

        player.sendMessage(Component.text("----------- Armor Stand Statistics -----------", NamedTextColor.YELLOW));
        player.sendMessage(plugin.getLang().getMessage("stats"));

        sendPose(player, "Head", pose.head());
        sendPose(player, "Body", pose.body());
        sendPose(player, "Right Arm", pose.rightArm());
        sendPose(player, "Left Arm", pose.leftArm());
        sendPose(player, "Right Leg", pose.rightLeg());
        sendPose(player, "Left Leg", pose.leftLeg());

        sendCoordinates(player, loc);
        sendVisibility(player, as);
        sendPhysics(player, as);
        sendSizeInfo(player, as);

        player.sendMessage(Component.text("----------------------------------------------", NamedTextColor.YELLOW));
    }

    private void sendPose(Player player, String name, EulerAngle angle) {
        player.sendMessage(
                Component.text(name + ": ", NamedTextColor.YELLOW)
                .append(Component.text(
                    round(angle.getX()) + " / " +
                        round(angle.getY()) + " / " +
                        round(angle.getZ()),
                    NamedTextColor.AQUA))
        );
    }

    private double round(double radians) {
        return Math.rint(Math.toDegrees(radians));
    }

    private void sendSizeInfo(Player player, ArmorStand as) {
        TextComponent.Builder builder = Component.text();

        if (isScaleSupported()) {
            AttributeInstance attributeInstance = as.getAttribute(Attribute.SCALE);
            double scale = attributeInstance != null ? attributeInstance.getBaseValue() : 0;
            player.sendMessage(
                Component.text("Size: ", NamedTextColor.YELLOW)
                    .append(Component.text(scale + "/" + plugin.getMaxScaleValue(), NamedTextColor.AQUA))
                    .append(Component.text(". ", NamedTextColor.YELLOW)));
        } else {
            builder.append(label("Is Small", as.isSmall()));
        }

        builder.append(label("Is Glowing", as.isGlowing()))
                .append(Component.text(". ", NamedTextColor.YELLOW))
                .append(label("Is Locked", SharedUtil.isLocked(as)))
                .append(Component.text(". ", NamedTextColor.YELLOW))
                .append(label("Is InUse", SharedUtil.isInUse(as.getUniqueId())));
        player.sendMessage(builder.build());
    }

    private boolean isScaleSupported() {
        return VersionUtil.fromString(plugin.getNmsVersion())
            .isNewerThanOrEquals(MinecraftVersion.MINECRAFT_1_20_4);
    }

    private void sendCoordinates(Player player, Location loc) {
        player.sendMessage(Component.text("Coordinates: ", NamedTextColor.YELLOW)
                .append(Component.text(
                    "X: " + loc.getX() +
                        " / Y: " + loc.getY() +
                        " / Z: " + loc.getZ(),
                        NamedTextColor.AQUA))
        );
    }

    private void sendVisibility(Player player, ArmorStand as) {
        player.sendMessage(
            label("Is Visible", as.isVisible())
                .append(Component.text(". ", NamedTextColor.YELLOW))
                .append(label("Arms Visible", as.hasArms()))
                .append(Component.text(". ", NamedTextColor.YELLOW))
                .append(label("Base Plate Visible", as.hasBasePlate()))
        );
    }

    private void sendPhysics(Player player, ArmorStand as) {
        player.sendMessage(
            label("Is Vulnerable", as.isInvulnerable())
                .append(Component.text(". ", NamedTextColor.YELLOW))
                .append(label("Affected by Gravity", as.hasGravity()))
        );
    }

    private record PoseData(
            EulerAngle head,
            EulerAngle body,
            EulerAngle rightArm,
            EulerAngle leftArm,
            EulerAngle rightLeg,
            EulerAngle leftLeg
    ) {
        static PoseData from(ArmorStand as) {
            return new PoseData(
                as.getHeadPose(),
                as.getBodyPose(),
                as.getRightArmPose(),
                as.getLeftArmPose(),
                as.getRightLegPose(),
                as.getLeftLegPose()
            );
        }
    }

    /**
     * Returns the EditMode whose name matches the given argument (case-insensitive),
     * or null if none matches.
     */
    private EditMode findMatchingMode(String arg) {
        for (EditMode mode : EditMode.values()) {
            if (mode.toString().equalsIgnoreCase(arg)) return mode;
        }
        return null;
    }

    /**
     * Returns false if the requested mode is a restricted visibility toggle
     * that the player lacks permission to use and the feature is disabled globally.
     */
    private boolean isVisibilityAllowed(Player player, String arg) {
        if (arg.equals("invisible"))
            return checkPermission(player, "togglearmorstandvisibility", true) && plugin.getArmorStandVisibility();
        if (arg.equals("itemframe"))
            return checkPermission(player, "toggleitemframevisibility", true) && plugin.getItemFrameVisibility();
        return true;
    }

}
