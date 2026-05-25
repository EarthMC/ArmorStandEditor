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
package io.github.rypofalem.armorstandeditor.protections;

import io.github.rypofalem.armorstandeditor.ArmorStandEditorPlugin;

import com.palmergames.bukkit.towny.event.executors.TownyActionEventExecutor;
import com.palmergames.bukkit.towny.TownyAPI;

import io.github.rypofalem.armorstandeditor.Debug;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;

//FIX for https://github.com/Wolfieheart/ArmorStandEditor-Issues/issues/15
public class TownyProtection implements Protection {
    private final boolean tEnabled;
    private final ArmorStandEditorPlugin plugin;


    public TownyProtection() {
        plugin = ArmorStandEditorPlugin.instance();
        tEnabled = Bukkit.getPluginManager().isPluginEnabled("Towny");
    }

    @Override
    public boolean checkPermission(Entity entity, Player player) {

        // Bypasses - Towny is not detected, Player is Op or has Bypass Perms
        if (!tEnabled || player.isOp() || player.hasPermission("asedit.ignoreProtection.towny")) return true;

        TownyAPI towny = TownyAPI.getInstance();
        Location playerLoc = player.getLocation();

        Material material;
        if (entity instanceof ArmorStand) {
            material = Material.ARMOR_STAND;
            Debug.log("Editing ArmorStand: " + entity.getUniqueId());
        } else if (entity instanceof ItemFrame) {
            material = entity instanceof GlowItemFrame ? Material.GLOW_ITEM_FRAME : Material.ITEM_FRAME;
            Debug.log("Editing ItemFrame: " + entity.getUniqueId());
        } else return true;


        // --- wilderness checks ---
        if (towny.isWilderness(playerLoc)) {
            if (player.hasPermission("asedit.townyProtection.canEditInWild")) {
                Debug.log("User '" + player.getName() + "' is in the Wilderness and has the permission asedit.townyProtection.canEditInWild set to TRUE. Edits are allowed!");
                return true;
            } else {
                player.sendMessage(plugin.getLang().getMessage("townyNoWildEdit", "warn"));
                return false;
            }
        }
        return TownyActionEventExecutor.canBuild(player, entity.getLocation(), material);
    }
}

