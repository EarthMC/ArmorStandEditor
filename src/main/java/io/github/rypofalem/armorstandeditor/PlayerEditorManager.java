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

import io.github.rypofalem.armorstandeditor.menu.ASEHolder;
import io.github.rypofalem.armorstandeditor.protections.*;
import io.github.rypofalem.armorstandeditor.utils.Util;

import io.papermc.lib.PaperLib;
import lombok.Getter;
import net.kyori.adventure.text.Component;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

//Manages PlayerEditors and Player Events related to editing armorstands
public class PlayerEditorManager implements Listener {
    private final ArmorStandEditorPlugin plugin;
    private final HashMap<UUID, PlayerEditor> players;
    private final Scheduler scheduler;

    @Getter
    private ASEHolder menuHolder = new ASEHolder(); //Inventory holder that owns the main ase menu inventories for the plugin
    @Getter
    private ASEHolder equipmentHolder = new ASEHolder(); //Inventory holder that owns the equipment menu
    @Getter
    private ASEHolder presetHolder = new ASEHolder(); //Inventory Holder that owns the PresetArmorStand Post Menu
    @Getter
    private ASEHolder sizeMenuHolder = new ASEHolder(); //Inventory Holder that owns the PresetArmorStand Post Menu

    double coarseAdj;
    double fineAdj;
    double coarseMov;
    double fineMov;
    private final TickCounter counter;

    // Instantiate protections used to determine whether a player may edit an armor stand or item frame
    private final List<Protection> protections = List.of(
        new TownyProtection(),
        new WorldGuardProtection()
    );

    PlayerEditorManager(ArmorStandEditorPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = plugin.getScheduler();

        players = new HashMap<>();
        coarseAdj = Util.FULL_CIRCLE / plugin.coarseRot;
        fineAdj = Util.FULL_CIRCLE / plugin.fineRot;
        coarseMov = 1;
        fineMov = .03125; // 1/32
        counter = new TickCounter();
        scheduler.runTaskTimer(counter, 1, 1);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onArmorStandSpawn(EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ArmorStand armorStand)) return;
        Debug.log("Entity being spawned is an ArmorStand");

        Player player = event.getPlayer();
        if (player == null) return;
        Location location = player.getLocation();

        Debug.log("Player " + player.getName()
            + " is placing an ArmorStand at (approx) X: " + Math.round(location.getX())
            + ", Y: " + Math.round(location.getY())
            + ", Z: " + Math.round(location.getZ())
        );

        armorStand.setGravity(plugin.getDefaultGravity());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onArmorStandDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!plugin.isEditTool(player.getInventory().getItemInMainHand())) return;
        if (!((event.getEntity() instanceof ArmorStand) || event.getEntity() instanceof ItemFrame)) {
            event.setCancelled(true);
            Debug.log("Open Menu Called for Player: " + player.getName());
            getPlayerEditor(player.getUniqueId()).openMenu();
            return;
        }
        if (event.getEntity() instanceof ArmorStand armorStand) {
            Debug.log("Player '" + player.getName() + "' has left clicked the ArmorStand");
            getPlayerEditor(player.getUniqueId()).cancelOpenMenu();
            event.setCancelled(true);
            if (canEdit(player, armorStand)) {
                applyLeftTool(player, armorStand);
            }
        } else if (event.getEntity() instanceof ItemFrame itemf) {
            Debug.log(" Player '" + player.getName() + "' has right clicked on an ItemFrame");
            getPlayerEditor(player.getUniqueId()).cancelOpenMenu();
            event.setCancelled(true);
            if (canEdit(player, itemf)) {
                applyLeftTool(player, itemf);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onArmorStandInteract(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!((event.getRightClicked() instanceof ArmorStand) || event.getRightClicked() instanceof ItemFrame)) return;

        if (event.getRightClicked() instanceof ArmorStand as) {
            Debug.log("Player '" + player.getName() + "' has right clicked on an ArmorStand");
            if (!canEdit(player, as)) return;
            if (plugin.isEditTool(player.getInventory().getItemInMainHand())) {
                getPlayerEditor(player.getUniqueId()).cancelOpenMenu();
                event.setCancelled(true);
                applyRightTool(player, as);
                return;
            }


            //Attempt rename
            if (player.getInventory().getItemInMainHand().getType() == Material.NAME_TAG && player.hasPermission("asedit.rename")) {
                ItemStack nameTag = player.getInventory().getItemInMainHand();
                Component name;
                ItemMeta meta = nameTag.getItemMeta();
                if (meta != null && meta.hasCustomName()) {
                    Component displayName = meta.customName();
                    if (!player.hasPermission("asedit.rename.color") && displayName != null) {
                        name = Component.text(PlainTextComponentSerializer.plainText().serialize(displayName));
                    } else {
                        name = displayName;
                    }
                } else {
                    name = null;
                }


                if (name == null) {
                    as.customName(null);
                    as.setCustomNameVisible(false);
                    event.setCancelled(true);
                } else {
                    event.setCancelled(true);
                    if (player.getGameMode() != GameMode.CREATIVE) {
                        nameTag.subtract(1);
                    }
                    // Minecraft will set the name after this event even if the event is cancelled.
                    // change it 1 tick later to apply formatting without it being overwritten
                    scheduler.runForEntity(as, () -> {
                        as.customName(name);
                        as.setCustomNameVisible(true);
                    });
                }
            }
        } else if (event.getRightClicked() instanceof ItemFrame itemFrame) {
            if (!canEdit(player, itemFrame)) return;
            if (plugin.isEditTool(player.getInventory().getItemInMainHand())) {
                getPlayerEditor(player.getUniqueId()).cancelOpenMenu();
                if (!itemFrame.getItem().getType().equals(Material.AIR)) {
                    event.setCancelled(true);
                }
                applyRightTool(player, itemFrame);
                return;
            }

            // TODO: Check dupe
            if (player.getInventory().getItemInMainHand().getType().equals(Material.GLOW_INK_SAC) //attempt glowing
                && player.hasPermission("asedit.basic")
                && plugin.glowItemFrames && player.isSneaking()
            ) {

                ItemStack glowSacs = player.getInventory().getItemInMainHand();
                ItemStack contents = null;
                Rotation rotation = null;
                if (itemFrame.getItem().getType() != Material.AIR) {
                    contents = itemFrame.getItem(); //save item
                    rotation = itemFrame.getRotation(); // save item rotation
                }
                Location itemFrameLocation = itemFrame.getLocation();
                BlockFace facing = itemFrame.getFacing();

                if (player.getGameMode() != GameMode.CREATIVE) {
                    glowSacs.subtract(1);
                }

                itemFrame.remove();
                GlowItemFrame glowFrame = (GlowItemFrame) player.getWorld().spawnEntity(itemFrameLocation, EntityType.GLOW_ITEM_FRAME);
                glowFrame.setFacingDirection(facing);
                if (contents != null) {
                    glowFrame.setItem(contents);
                    glowFrame.setRotation(rotation);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStandBreak(EntityDamageByEntityEvent event) { // Fixes issue #309
        if (!(event.getDamager() instanceof Player)) return; // If the damager is not a player, ignore.
        if (!(event.getEntity() instanceof ArmorStand stand)) return; // If the damaged entity is not an ArmorStand, ignore.

        // Check if the ArmorStand is invulnerable and if the damager is a player.
        if (stand.isInvulnerable() && event.getDamager() instanceof Player player) {
            // Check if the player is in Creative mode.
            if (player.getGameMode() == GameMode.CREATIVE) {
                // If the player is in Creative mode and the ArmorStand is invulnerable,
                // cancel the event to prevent breaking the ArmorStand.
                player.sendMessage(plugin.getLang().getMessage("unabledestroycreative"));
                event.setCancelled(true);
            }
        }


        if (stand.isDead()) {
            stand.customName(null);
            stand.setCustomNameVisible(false);
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwitchHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        Debug.log("PlayerSwapHandItemsEvent trigger for Player: " + player.getName());

        // Ignore if the off-hand item is not the edit tool
        if (!plugin.isEditTool(event.getOffHandItem())) return;
        event.setCancelled(true);

        // Get targets
        ArrayList<ArmorStand> asTargets = getTargets(player);       // Closest ArmorStands
        ArrayList<ItemFrame> frameTargets = getFrameTargets(player); // Closest ItemFrames

        PlayerEditor editor = getPlayerEditor(player.getUniqueId());

        // Handle double target
        if (!asTargets.isEmpty() && !frameTargets.isEmpty()) {
            editor.sendMessage("doubletarget", "warn");
            return;
        }

        // Handle single target: ArmorStand
        if (!asTargets.isEmpty()) {
            editor.setTarget(asTargets);
            return;
        }

        // Handle single target: ItemFrame
        if (!frameTargets.isEmpty()) {
            editor.setFrameTarget(frameTargets);
            return;
        }

        // No target found
        editor.sendMessage("nodoubletarget", "warn");
    }

    private @NotNull ArrayList<ArmorStand> getTargets(Player player) {
        Location eyeLaser = player.getEyeLocation();
        Vector direction = player.getLocation().getDirection();
        ArrayList<ArmorStand> armorStands = new ArrayList<>();

        double STEPSIZE = .5;
        Vector STEP = direction.multiply(STEPSIZE);
        double RANGE = 10;
        double LASERRADIUS = .3;
        List<Entity> nearbyEntities = player.getNearbyEntities(RANGE, RANGE, RANGE);
        if (nearbyEntities.isEmpty()) return armorStands;

        for (double i = 0; i < RANGE; i += STEPSIZE) {
            List<Entity> nearby = (List<Entity>) player.getWorld().getNearbyEntities(eyeLaser, LASERRADIUS, LASERRADIUS, LASERRADIUS);
            if (!nearby.isEmpty()) {
                boolean endLaser = false;
                for (Entity e : nearby) {
                    if (e instanceof ArmorStand stand) {
                        armorStands.add(stand);
                        endLaser = true;
                    }
                }

                if (endLaser) break;
            }
            if (eyeLaser.getBlock().getType().isSolid()) break;
            eyeLaser.add(STEP);
        }
        return armorStands;
    }

    private @NotNull ArrayList<ItemFrame> getFrameTargets(Player player) {
        Location eyeLaser = player.getEyeLocation();
        Vector direction = player.getLocation().getDirection();
        ArrayList<ItemFrame> itemFrames = new ArrayList<>();

        double STEPSIZE = .5;
        Vector STEP = direction.multiply(STEPSIZE);
        double RANGE = 10;
        double LASERRADIUS = .3;

        List<Entity> nearbyEntities = player.getNearbyEntities(RANGE, RANGE, RANGE);
        if (nearbyEntities.isEmpty()) return itemFrames;

        for (double i = 0; i < RANGE; i += STEPSIZE) {
            List<Entity> nearby = (List<Entity>) player.getWorld().getNearbyEntities(eyeLaser, LASERRADIUS, LASERRADIUS, LASERRADIUS);
            if (!nearby.isEmpty()) {
                boolean endLaser = false;
                for (Entity e : nearby) {
                    if (e instanceof ItemFrame frame) {
                        itemFrames.add(frame);
                        endLaser = true;
                    }
                }

                if (endLaser) break;
            }
            if (eyeLaser.getBlock().getType().isSolid()) break;
            eyeLaser.add(STEP);
        }

        return itemFrames;
    }


    boolean canEdit(Player player, Entity entity) {
        // Check if the entity has a blocked name
        Component component = entity.customName();
        if (component != null && plugin.enableBlockedNames) {
            String name = PlainTextComponentSerializer.plainText().serialize(component);
            if (plugin.blockedNames.stream().anyMatch(name::equalsIgnoreCase)) {
                return false;
            }
        }

        // Check if all protections allow this edit, if one fails, don't allow edit
        return protections.stream().allMatch(protection -> protection.checkPermission(entity, player));
    }

    private void applyLeftTool(Player player, ArmorStand as) {
        Debug.log("Applying Left Tool on ArmorStand for Player: " + player.getName());
        getPlayerEditor(player.getUniqueId()).cancelOpenMenu();
        getPlayerEditor(player.getUniqueId()).editArmorStand(as);
    }

    private void applyLeftTool(Player player, ItemFrame itemf) {
        Debug.log("Applying Left Tool on ItemFrame for Player: " + player.getName());
        getPlayerEditor(player.getUniqueId()).cancelOpenMenu();
        getPlayerEditor(player.getUniqueId()).editItemFrame(itemf);
    }

    private void applyRightTool(Player player, ItemFrame itemf) {
        Debug.log("Applying Right Tool on ItemFrame for Player: " + player.getName());
        getPlayerEditor(player.getUniqueId()).cancelOpenMenu();
        getPlayerEditor(player.getUniqueId()).editItemFrame(itemf);
    }

    private void applyRightTool(Player player, ArmorStand as) {
        Debug.log("Applying Right Tool on ArmorStand for Player: " + player.getName());
        getPlayerEditor(player.getUniqueId()).cancelOpenMenu();
        getPlayerEditor(player.getUniqueId()).reverseEditArmorStand(as);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRightClickTool(PlayerInteractEvent event) {
        if (!(event.getAction().isLeftClick() || event.getAction().isRightClick())) {
            return;
        }

        Debug.log("Ran on Right Click Tool Event.");
        Player player = event.getPlayer();

        if (!plugin.isEditTool(player.getInventory().getItemInMainHand())) return;
        if (plugin.requireSneaking && !player.isSneaking()) return;
        if (!player.hasPermission("asedit.basic")) return;
        if (plugin.enablePerWorld && (!plugin.allowedWorldList.contains(player.getWorld().getName()))) {
            //Implementation for Per World ASE
            getPlayerEditor(player.getUniqueId()).sendMessage("notincorrectworld", "warn");
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        Debug.log("Open Menu Called for Player: " + player.getName());
        getPlayerEditor(player.getUniqueId()).openMenu();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onScrollNCrouch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        if (!plugin.isEditTool(player.getInventory().getItem(event.getPreviousSlot()))) return;

        event.setCancelled(true);
        if (event.getNewSlot() == event.getPreviousSlot() + 1 || (event.getNewSlot() == 0 && event.getPreviousSlot() == 8)) {
            getPlayerEditor(player.getUniqueId()).cycleAxis(1);
        } else if (event.getNewSlot() == event.getPreviousSlot() - 1 || (event.getNewSlot() == 8 && event.getPreviousSlot() == 0)) {
            getPlayerEditor(player.getUniqueId()).cycleAxis(-1);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMenuSelect(InventoryClickEvent event) {
        final InventoryHolder holder = PaperLib.getHolder(event.getInventory(), false).getHolder();

        if (!(holder instanceof ASEHolder)) return;

        if (holder == menuHolder) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item != null && item.hasItemMeta()) {
                Player player = (Player) event.getWhoClicked();
                String command = item.getItemMeta().getPersistentDataContainer().get(plugin.getIconKey(), PersistentDataType.STRING);
                if (command == null || command.equals("ase ")) { // Therefore user has clicked a black pane
                    getPlayerEditor(player.getUniqueId()).sendMessage("blackGlassClick", "");
                    return;
                } else {
                    player.performCommand(command);
                    scheduler.runForEntity(player, player::closeInventory);
                    return;
                }
            }
        }
        if (holder == equipmentHolder) {
            ItemStack item = event.getCurrentItem();
            if (item == null) return;
            if (item.getItemMeta() == null) return;
            if (item.getItemMeta().getPersistentDataContainer().has(plugin.getIconKey(), PersistentDataType.STRING)) {
                event.setCancelled(true);
            }
        }

        if (holder == presetHolder) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item != null && item.hasItemMeta()) {
                Player player = (Player) event.getWhoClicked();
                String itemName = item.getPersistentDataContainer().get(plugin.getIconKey(), PersistentDataType.STRING);
                PlayerEditor pe = players.get(player.getUniqueId());
                pe.presetPoseMenu.handlePresetPose(itemName, player);
                scheduler.runForEntity(player, player::closeInventory);
            }
        }

        if (holder == sizeMenuHolder) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item != null && item.hasItemMeta()) {
                Player player = (Player) event.getWhoClicked();
                String itemName = item.getPersistentDataContainer().get(plugin.getIconKey(), PersistentDataType.STRING);
                PlayerEditor pe = players.get(player.getUniqueId());
                pe.sizeModificationMenu.handleAttributeScaling(itemName, player);
                scheduler.runForEntity(player, player::closeInventory);
            }
        }
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMenuClose(InventoryCloseEvent event) {
        final InventoryHolder holder = PaperLib.getHolder(event.getInventory(), false).getHolder();

        if (!(holder instanceof ASEHolder)) return;
        if (holder == equipmentHolder) {
            PlayerEditor pe = players.get(event.getPlayer().getUniqueId());
            pe.equipMenu.equipArmorstand();

            // Remove the In Use Lock
            SharedUtil.setInUse(pe.armorStandInUseId, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLogOut(PlayerQuitEvent event) {
        removePlayerEditor(event.getPlayer().getUniqueId());
    }

    public PlayerEditor getPlayerEditor(UUID uuid) {
        return players.containsKey(uuid) ? players.get(uuid) : addPlayerEditor(uuid);
    }

    private PlayerEditor addPlayerEditor(UUID uuid) {
        PlayerEditor pe = new PlayerEditor(uuid, plugin);
        players.put(uuid, pe);
        return pe;
    }

    private void removePlayerEditor(UUID uuid) {
        players.remove(uuid);
    }

    long getTime() {
        return counter.ticks;
    }


    static class TickCounter implements Runnable {
        long ticks = 0; //I am optimistic

        @Override
        public void run() {
            ticks++;
        }

        public long getTime() {
            return ticks;
        }
    }
}
