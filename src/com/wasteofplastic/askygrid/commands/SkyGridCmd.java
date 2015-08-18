/*******************************************************************************
 * This file is part of ASkyGrid.
 *
 *     ASkyGrid is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ASkyGrid is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with ASkyGrid.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package com.wasteofplastic.askygrid.commands;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import com.wasteofplastic.askygrid.ASkyGrid;
import com.wasteofplastic.askygrid.GridManager;
import com.wasteofplastic.askygrid.Settings;
import com.wasteofplastic.askygrid.listeners.PlayerEvents;
import com.wasteofplastic.askygrid.util.Util;
import com.wasteofplastic.askygrid.util.VaultHelper;

public class SkyGridCmd implements CommandExecutor, TabCompleter {
    public boolean levelCalcFreeFlag = true;
    private ASkyGrid plugin;
    /**
     * Constructor
     * 
     * @param plugin
     * @param players
     */
    public SkyGridCmd(ASkyGrid plugin) {
	// Plugin instance
	this.plugin = plugin;
    }

    /**
     * Makes the default island for the player
     * @param player
     */
    public void newIsland(final Player player) {
	//long time = System.nanoTime();
	final UUID playerUUID = player.getUniqueId();
	Util.logger(2,"DEBUG: finding spawn location");
	Random random = new Random();
	Location next = new Location(ASkyGrid.getGridWorld(), (random.nextInt(Settings.spawnDistance*2) - Settings.spawnDistance)
		,Settings.spawnHeight, (random.nextInt(Settings.spawnDistance*2) - Settings.spawnDistance));
	Util.logger(2,"DEBUG: found " + next);
	// Clear any old home locations (they should be clear, but just in case)
	plugin.getPlayers().clearHomeLocations(playerUUID);
	// Set the player's island location to this new spot
	plugin.getPlayers().setHomeLocation(playerUUID, next);
	// Save the player so that if the server is reset weird things won't happen
	plugin.getPlayers().save(playerUUID);
	// Set the biome
	//BiomesPanel.setIslandBiome(next, schematic.getBiome());
	// Teleport to the new home
	plugin.getGrid().homeTeleport(player);
	// Reset any inventory, etc. This is done AFTER the teleport because other plugins may switch out inventory based on world
	plugin.resetPlayer(player);
	// Reset money if required
	if (Settings.resetMoney) {
	    resetMoney(player);
	}
	// Show fancy titles!
	if (!plugin.myLocale(player.getUniqueId()).islandSubTitle.isEmpty()) {
	    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
		    "title " + player.getName() + " subtitle {text:\"" + plugin.myLocale(player.getUniqueId()).islandSubTitle + "\", color:blue}");
	}
	if (!plugin.myLocale(player.getUniqueId()).islandTitle.isEmpty()) {
	    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
		    "title " + player.getName() + " title {text:\"" + plugin.myLocale(player.getUniqueId()).islandTitle + "\", color:gold}");
	}
	if (!plugin.myLocale(player.getUniqueId()).islandDonate.isEmpty() && !plugin.myLocale(player.getUniqueId()).islandURL.isEmpty()) {
	    plugin.getServer().dispatchCommand(
		    plugin.getServer().getConsoleSender(),
		    "tellraw " + player.getName() + " {text:\"" + plugin.myLocale(player.getUniqueId()).islandDonate + "\",color:aqua" + ",clickEvent:{action:open_url,value:\""
			    + plugin.myLocale(player.getUniqueId()).islandURL + "\"}}");
	}
	// Run any commands that need to be run at the start
	/*
	if (firstTime) {
	    runCommands(Settings.startCommands, player.getUniqueId());
	}*/
	// Done - fire event
	//final IslandNewEvent event = new IslandNewEvent(player,schematic, myIsland);
	//plugin.getServer().getPluginManager().callEvent(event);
	//Util.logger(2,"DEBUG: Done! " + (System.nanoTime()- time) * 0.000001);
    }

    private void resetMoney(Player player) {
	if (!Settings.useEconomy) {
	    return;
	}
	// Set player's balance in acid island to the starting balance
	try {
	    // Util.logger(2,"DEBUG: " + player.getName() + " " +
	    // Settings.general_worldName);
	    if (VaultHelper.econ == null) {
		// plugin.getLogger().warning("DEBUG: econ is null!");
		VaultHelper.setupEconomy();
	    }
	    Double playerBalance = VaultHelper.econ.getBalance(player, Settings.worldName);
	    // Util.logger(2,"DEBUG: playerbalance = " +
	    // playerBalance);
	    // Round the balance to 2 decimal places and slightly down to
	    // avoid issues when withdrawing the amount later
	    BigDecimal bd = new BigDecimal(playerBalance);
	    bd = bd.setScale(2, RoundingMode.HALF_DOWN);
	    playerBalance = bd.doubleValue();
	    // Util.logger(2,"DEBUG: playerbalance after rounding = "
	    // + playerBalance);
	    if (playerBalance != Settings.startingMoney) {
		if (playerBalance > Settings.startingMoney) {
		    Double difference = playerBalance - Settings.startingMoney;
		    EconomyResponse response = VaultHelper.econ.withdrawPlayer(player, Settings.worldName, difference);
		    // Util.logger(2,"DEBUG: withdrawn");
		    if (response.transactionSuccess()) {
			Util.logger(2,
				"FYI:" + player.getName() + " had " + VaultHelper.econ.format(playerBalance) + " when they typed /island and it was set to "
					+ Settings.startingMoney);
		    } else {
			plugin.getLogger().warning(
				"Problem trying to withdraw " + playerBalance + " from " + player.getName() + "'s account when they typed /island!");
			plugin.getLogger().warning("Error from economy was: " + response.errorMessage);
		    }
		} else {
		    Double difference = Settings.startingMoney - playerBalance;
		    EconomyResponse response = VaultHelper.econ.depositPlayer(player, Settings.worldName, difference);
		    if (response.transactionSuccess()) {
			Util.logger(2,
				"FYI:" + player.getName() + " had " + VaultHelper.econ.format(playerBalance) + " when they typed /island and it was set to "
					+ Settings.startingMoney);
		    } else {
			plugin.getLogger().warning(
				"Problem trying to deposit " + playerBalance + " from " + player.getName() + "'s account when they typed /island!");
			plugin.getLogger().warning("Error from economy was: " + response.errorMessage);
		    }

		}
	    }
	} catch (final Exception e) {
	    plugin.getLogger().severe("Error trying to zero " + player.getName() + "'s account when they typed /island!");
	    plugin.getLogger().severe(e.getMessage());
	}

    }

    /**
     * One-to-one relationship, you can return the first matched key
     * 
     * @param map
     * @param value
     * @return
     */
    public static <T, E> T getKeyByValue(Map<T, E> map, E value) {
	for (Entry<T, E> entry : map.entrySet()) {
	    if (value.equals(entry.getValue())) {
		return entry.getKey();
	    }
	}
	return null;
    }

    /*
     * (non-Javadoc)
     * @see
     * org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender
     * , org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] split) {
	if (!(sender instanceof Player)) {
	    return false;
	}
	final Player player = (Player) sender;
	// Basic permissions check to even use /island
	if (!VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.create")) {
	    player.sendMessage(ChatColor.RED + plugin.myLocale(player.getUniqueId()).errorYouDoNotHavePermission);
	    return true;
	}
	/*
	 * Grab data for this player - may be null or empty
	 * playerUUID is the unique ID of the player who issued the command
	 */
	final UUID playerUUID = player.getUniqueId();
	// Check if a player has an island or is in a team
	switch (split.length) {
	// /island command by itself
	case 0:
	    // New island
	    if (plugin.getPlayers().getHomeLocation(playerUUID) == null) {
		// Create new island for player
		player.sendMessage(ChatColor.GREEN + plugin.myLocale(player.getUniqueId()).newPlayer);
		newIsland(player);
		if (Settings.removeMobs) {
		    plugin.getGrid().removeMobs(player.getLocation());
		}
		return true;
	    } else {
		if (!player.getWorld().getName().equalsIgnoreCase(Settings.worldName) || Settings.allowTeleportWhenFalling
			|| !PlayerEvents.isFalling(playerUUID) || player.isOp()) {
		    // Teleport home
		    plugin.getGrid().homeTeleport(player);
		    if (Settings.removeMobs) {
			plugin.getGrid().removeMobs(player.getLocation());
		    }
		} else {
		    player.sendMessage(ChatColor.RED + plugin.myLocale(player.getUniqueId()).errorCommandNotReady);
		}
		return true;
	    }
	case 1:
	    if (split[0].equalsIgnoreCase("lang")) {
		if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.lang")) {
		    player.sendMessage("/" + label + " lang <locale>");
		    player.sendMessage("English");
		    /*
		    player.sendMessage("Français");
		    player.sendMessage("Deutsch");
		    player.sendMessage("Español");
		    player.sendMessage("Italiano");
		    player.sendMessage("한국의 / Korean");
		    player.sendMessage("Polski");
		    player.sendMessage("Brasil");
		    player.sendMessage("中国 / SimplifiedChinese");
		    player.sendMessage("Čeština");
		    player.sendMessage("Slovenčina");
		    player.sendMessage("繁體中文 / TraditionalChinese");
		     */
		} else {
		    player.sendMessage(ChatColor.RED + plugin.myLocale(playerUUID).errorNoPermission);
		}
		return true;
	    } else if (split[0].equalsIgnoreCase("go")) {
		if (plugin.getPlayers().getHomeLocation(playerUUID) == null) {
		    // Player has no island
		    player.sendMessage(ChatColor.RED + plugin.myLocale(playerUUID).newPlayer);
		    return true;
		}
		// Teleport home
		plugin.getGrid().homeTeleport(player);
		if (Settings.removeMobs) {
		    plugin.getGrid().removeMobs(player.getLocation());
		}
		return true;
	    } else if (split[0].equalsIgnoreCase("about")) {
		player.sendMessage(ChatColor.GOLD + "This plugin is free software: you can redistribute");
		player.sendMessage(ChatColor.GOLD + "it and/or modify it under the terms of the GNU");
		player.sendMessage(ChatColor.GOLD + "General Public License as published by the Free");
		player.sendMessage(ChatColor.GOLD + "Software Foundation, either version 3 of the License,");
		player.sendMessage(ChatColor.GOLD + "or (at your option) any later version.");
		player.sendMessage(ChatColor.GOLD + "This plugin is distributed in the hope that it");
		player.sendMessage(ChatColor.GOLD + "will be useful, but WITHOUT ANY WARRANTY; without");
		player.sendMessage(ChatColor.GOLD + "even the implied warranty of MERCHANTABILITY or");
		player.sendMessage(ChatColor.GOLD + "FITNESS FOR A PARTICULAR PURPOSE.  See the");
		player.sendMessage(ChatColor.GOLD + "GNU General Public License for more details.");
		player.sendMessage(ChatColor.GOLD + "You should have received a copy of the GNU");
		player.sendMessage(ChatColor.GOLD + "General Public License along with this plugin.");
		player.sendMessage(ChatColor.GOLD + "If not, see <http://www.gnu.org/licenses/>.");
		player.sendMessage(ChatColor.GOLD + "Souce code is available on GitHub.");
		player.sendMessage(ChatColor.GOLD + "(c) 2015 by tastybento");
		return true;
	    } else if (split[0].equalsIgnoreCase("warp")) {
		if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.warp")) {
		    player.sendMessage(ChatColor.YELLOW + "/island warp <player>: " + ChatColor.WHITE + plugin.myLocale(player.getUniqueId()).islandhelpWarp);
		    return true;
		}
	    } else if (split[0].equalsIgnoreCase("warps")) {
		if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.warp")) {
		    // Step through warp table
		    Collection<UUID> warpList = plugin.getWarpSignsListener().listWarps();
		    if (warpList.isEmpty()) {
			player.sendMessage(ChatColor.YELLOW + plugin.myLocale(player.getUniqueId()).warpserrorNoWarpsYet);
			if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.addwarp")) {
			    player.sendMessage(ChatColor.YELLOW + plugin.myLocale().warpswarpTip);
			}
			return true;
		    } else {
			if (Settings.useWarpPanel) {
			    // Try the warp panel
			    player.openInventory(plugin.getWarpPanel().getWarpPanel(0));
			} else {
			    Boolean hasWarp = false;
			    String wlist = "";
			    for (UUID w : warpList) {
				if (wlist.isEmpty()) {
				    wlist = plugin.getPlayers().getName(w);
				} else {
				    wlist += ", " + plugin.getPlayers().getName(w);
				}
				if (w.equals(playerUUID)) {
				    hasWarp = true;
				}
			    }
			    player.sendMessage(ChatColor.YELLOW + plugin.myLocale(player.getUniqueId()).warpswarpsAvailable + ": " + ChatColor.WHITE + wlist);
			    if (!hasWarp && (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.addwarp"))) {
				player.sendMessage(ChatColor.YELLOW + plugin.myLocale().warpswarpTip);
			    }
			}
			return true;
		    }
		}
	    } else if (split[0].equalsIgnoreCase("sethome")) {
		if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.sethome")) {
		    // Check world
		    if (!player.getWorld().equals(ASkyGrid.getGridWorld())) {
			player.sendMessage(ChatColor.RED + plugin.myLocale(player.getUniqueId()).errorWrongWorld);
			return true;
		    }
		    plugin.getGrid().homeSet(player);
		    return true;
		}
		return false;
	    } else if (split[0].equalsIgnoreCase("help")) {
		player.sendMessage(ChatColor.GREEN + plugin.getName() + " " + plugin.getDescription().getVersion() + " help:");

		player.sendMessage(plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + ": " + ChatColor.WHITE + plugin.myLocale(player.getUniqueId()).help);
		// Dynamic home sizes with permissions
		int maxHomes = Settings.maxHomes;
		for (PermissionAttachmentInfo perms : player.getEffectivePermissions()) {
		    if (perms.getPermission().startsWith(Settings.PERMPREFIX + "player.maxhomes.")) {
			maxHomes = Integer.valueOf(perms.getPermission().split(Settings.PERMPREFIX + "player.maxhomes.")[1]);
		    }
		    // Do some sanity checking
		    if (maxHomes < 1) {
			maxHomes = 1;
		    }
		}
		if (maxHomes > 1 && VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.sethome")) {
		    player.sendMessage(plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " go <1 - " + maxHomes + ">: " + ChatColor.WHITE + plugin.myLocale(player.getUniqueId()).islandhelpTeleport);
		} else {
		    player.sendMessage(plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " go: " + ChatColor.WHITE + plugin.myLocale(player.getUniqueId()).islandhelpTeleport);
		}
		if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.warp")) {
		    player.sendMessage(plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " controlpanel or cp: " + ChatColor.WHITE + plugin.myLocale(player.getUniqueId()).helpControlPanel);
		}
		if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.sethome")) {
		    if (maxHomes > 1) {
			player.sendMessage(plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " sethome <1 - " + maxHomes + ">: " + ChatColor.WHITE + plugin.myLocale(player.getUniqueId()).helpSetHome);
		    } else {
			player.sendMessage(plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " sethome: " + ChatColor.WHITE + plugin.myLocale(player.getUniqueId()).helpSetHome);
		    }
		}
		if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.warp")) {
		    player.sendMessage(plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " warps: " + ChatColor.WHITE + plugin.myLocale(player.getUniqueId()).islandhelpWarps);
		    player.sendMessage(plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " warp <player>: " + ChatColor.WHITE + plugin.myLocale(player.getUniqueId()).islandhelpWarp);
		}
		if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.challenges")) {
		    player.sendMessage(plugin.myLocale(player.getUniqueId()).helpColor + plugin.myLocale(player.getUniqueId()).islandHelpChallenges);
		}
		if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.lang")) {
		    player.sendMessage(plugin.myLocale(player.getUniqueId()).helpColor + "/" + label + " lang <locale> - select language");
		}
		return true;
	    } 
	    /*
	     * Commands that have two parameters
	     */
	case 2:
	    if (split[0].equalsIgnoreCase("warps")) {
		if (Settings.useWarpPanel) {
		    if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.warp")) {
			// Step through warp table
			Set<UUID> warpList = plugin.getWarpSignsListener().listWarps();
			if (warpList.isEmpty()) {
			    player.sendMessage(ChatColor.YELLOW + plugin.myLocale(player.getUniqueId()).warpserrorNoWarpsYet);
			    if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.addwarp")) {
				player.sendMessage(ChatColor.YELLOW + plugin.myLocale().warpswarpTip);
			    }
			    return true;
			} else {
			    // Try the warp panel
			    int panelNum = 0;
			    try {
				panelNum = Integer.valueOf(split[1]) - 1;
			    } catch (Exception e) {
				panelNum = 0;
			    }
			    player.openInventory(plugin.getWarpPanel().getWarpPanel(panelNum));
			    return true;
			}
		    } else {
			player.sendMessage(ChatColor.RED + plugin.myLocale(playerUUID).errorNoPermission);
		    }
		} else {
		    return false;
		}
	    } else if (split[0].equalsIgnoreCase("lang")) {
		if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.lang")) {
		    if (split[1].equalsIgnoreCase("english")) {
			plugin.getPlayers().setLocale(playerUUID, "en-US"); 
		    } else if (split[1].equalsIgnoreCase("Français") || split[1].equalsIgnoreCase("Francais")) {
			plugin.getPlayers().setLocale(playerUUID, "fr-FR"); 
		    } else if (split[1].equalsIgnoreCase("Deutsch")) {
			plugin.getPlayers().setLocale(playerUUID, "de-DE");  
		    } else if (split[1].equalsIgnoreCase("español") || split[1].equalsIgnoreCase("espanol")) {
			plugin.getPlayers().setLocale(playerUUID, "es-ES");  
		    } else if (split[1].equalsIgnoreCase("italiano")) {
			plugin.getPlayers().setLocale(playerUUID, "it-IT");  
		    } else if (split[1].equalsIgnoreCase("Korean") || split[1].equalsIgnoreCase("한국의")) {
			plugin.getPlayers().setLocale(playerUUID, "ko-KR");  
		    } else if (split[1].equalsIgnoreCase("polski")) {
			plugin.getPlayers().setLocale(playerUUID, "pl-PL");  
		    } else if (split[1].equalsIgnoreCase("Brasil")) {
			plugin.getPlayers().setLocale(playerUUID, "pt-BR");  
		    } else if (split[1].equalsIgnoreCase("SimplifiedChinese") || split[1].equalsIgnoreCase("中国")) {
			plugin.getPlayers().setLocale(playerUUID, "zh-CN");  
		    } else if (split[1].equalsIgnoreCase("Čeština") || split[1].equalsIgnoreCase("Cestina")) {
			plugin.getPlayers().setLocale(playerUUID, "cs-CS");  
		    } else if (split[1].equalsIgnoreCase("Slovenčina") || split[1].equalsIgnoreCase("Slovencina")) {
			plugin.getPlayers().setLocale(playerUUID, "sk-SK");  
		    } else if (split[1].equalsIgnoreCase("TraditionalChinese") || split[1].equalsIgnoreCase("繁體中文")) {
			plugin.getPlayers().setLocale(playerUUID, "zh-TW");  
		    } else {
			// Typed it in wrong
			player.sendMessage("/" + label + " lang <locale>");
			player.sendMessage("English");
			player.sendMessage("Français");
			player.sendMessage("Deutsch");
			player.sendMessage("Español");
			player.sendMessage("Italiano");
			player.sendMessage("한국의 / Korean");
			player.sendMessage("Polski");
			player.sendMessage("Brasil");
			player.sendMessage("中国 / SimplifiedChinese");
			player.sendMessage("Čeština");
			player.sendMessage("Slovenčina");
			player.sendMessage("繁體中文 / TraditionalChinese");
			return true;
		    }
		    player.sendMessage("OK!");
		    return true;
		} else {
		    player.sendMessage(ChatColor.RED + plugin.myLocale(playerUUID).errorNoPermission);
		    return true;
		}
	    } else 
		// Multi home
		if (split[0].equalsIgnoreCase("go")) {
		    if (plugin.getPlayers().getHomeLocation(playerUUID) == null) {
			// Player has no island
			player.sendMessage(ChatColor.RED + plugin.myLocale(player.getUniqueId()).newPlayer);
			return true;
		    }
		    if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.sethome")) {
			int number = 1;
			try {
			    number = Integer.valueOf(split[1]);
			    //Util.logger(2,"DEBUG: number = " + number);
			    if (number < 1) {
				plugin.getGrid().homeTeleport(player,1);
			    } else {
				int maxHomes = Settings.maxHomes;
				// Dynamic home sizes with permissions
				for (PermissionAttachmentInfo perms : player.getEffectivePermissions()) {
				    if (perms.getPermission().startsWith(Settings.PERMPREFIX + "player.maxhomes.")) {
					maxHomes = Integer.valueOf(perms.getPermission().split(Settings.PERMPREFIX + "player.maxhomes.")[1]);
				    }
				    // Do some sanity checking
				    if (maxHomes < 1) {
					maxHomes = 1;
				    }
				}
				if (number > maxHomes) {
				    if (maxHomes > 1) {
					player.sendMessage(ChatColor.RED + plugin.myLocale(player.getUniqueId()).setHomeerrorNumHomes.replace("[max]",String.valueOf(maxHomes)));
				    } else {
					plugin.getGrid().homeTeleport(player,1);
				    }
				} else {
				    // Teleport home
				    plugin.getGrid().homeTeleport(player,number);
				}
			    }
			} catch (Exception e) {
			    // Teleport home
			    plugin.getGrid().homeTeleport(player,1);
			}
			if (Settings.removeMobs) {
			    plugin.getGrid().removeMobs(player.getLocation());
			}
		    } else {
			player.sendMessage(ChatColor.RED + plugin.myLocale(player.getUniqueId()).errorNoPermission); 
		    }
		    return true;
		} else if (split[0].equalsIgnoreCase("sethome")) {
		    if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.sethome")) {
			if (!player.getWorld().equals(ASkyGrid.getGridWorld())) {
			    // Util.logger(2,"DEBUG: player has no island in grid");
			    // Player has no island in the grid
			    player.sendMessage(ChatColor.RED + plugin.myLocale(player.getUniqueId()).errorWrongWorld);
			    return true;
			}
			int maxHomes = Settings.maxHomes;
			// Dynamic home sizes with permissions
			for (PermissionAttachmentInfo perms : player.getEffectivePermissions()) {
			    if (perms.getPermission().startsWith(Settings.PERMPREFIX + "player.maxhomes.")) {
				maxHomes = Integer.valueOf(perms.getPermission().split(Settings.PERMPREFIX + "player.maxhomes.")[1]);
			    }
			}
			if (maxHomes > 1) {
			    // Check the number given is a number
			    int number = 0;
			    try {
				number = Integer.valueOf(split[1]);
				if (number < 1 || number > maxHomes) {
				    player.sendMessage(ChatColor.RED + plugin.myLocale(player.getUniqueId()).setHomeerrorNumHomes.replace("[max]",String.valueOf(maxHomes)));
				} else {
				    plugin.getGrid().homeSet(player, number);
				}
			    } catch (Exception e) {
				player.sendMessage(ChatColor.RED + plugin.myLocale(player.getUniqueId()).setHomeerrorNumHomes.replace("[max]",String.valueOf(maxHomes)));
			    }
			} else {
			    player.sendMessage(ChatColor.RED + plugin.myLocale(player.getUniqueId()).errorNoPermission);
			}
			return true;
		    }
		    player.sendMessage(ChatColor.RED + plugin.myLocale(player.getUniqueId()).errorNoPermission);
		    return true;
		} else if (split[0].equalsIgnoreCase("warp")) {
		    // Warp somewhere command
		    if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.warp")) {
			final Set<UUID> warpList = plugin.getWarpSignsListener().listWarps();
			if (warpList.isEmpty()) {
			    player.sendMessage(ChatColor.YELLOW + plugin.myLocale(player.getUniqueId()).warpserrorNoWarpsYet);
			    if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.addwarp")) {
				player.sendMessage(ChatColor.YELLOW + plugin.myLocale().warpswarpTip);
			    } else {
				player.sendMessage(ChatColor.RED + plugin.myLocale(player.getUniqueId()).errorNoPermission);
			    }
			    return true;
			} else {
			    // Check if this is part of a name
			    UUID foundWarp = null;
			    for (UUID warp : warpList) {
				if (plugin.getPlayers().getName(warp).toLowerCase().startsWith(split[1].toLowerCase())) {
				    foundWarp = warp;
				    break;
				}
			    }
			    if (foundWarp == null) {
				player.sendMessage(ChatColor.RED + plugin.myLocale(player.getUniqueId()).warpserrorDoesNotExist);
				return true;
			    } else {
				// Warp exists!
				final Location warpSpot = plugin.getWarpSignsListener().getWarp(foundWarp);
				// Check if the warp spot is safe
				if (warpSpot == null) {
				    player.sendMessage(ChatColor.RED + plugin.myLocale(player.getUniqueId()).warpserrorNotReadyYet);
				    plugin.getLogger().warning("Null warp found, owned by " + plugin.getPlayers().getName(foundWarp));
				    return true;
				}
				// Find out which direction the warp is facing
				Block b = warpSpot.getBlock();
				if (b.getType().equals(Material.SIGN_POST) || b.getType().equals(Material.WALL_SIGN)) {
				    Sign sign = (Sign) b.getState();
				    org.bukkit.material.Sign s = (org.bukkit.material.Sign) sign.getData();
				    BlockFace directionFacing = s.getFacing();
				    Location inFront = b.getRelative(directionFacing).getLocation();
				    Location oneDown = b.getRelative(directionFacing).getRelative(BlockFace.DOWN).getLocation();
				    if ((GridManager.isSafeLocation(inFront))) {
					warpPlayer(player, inFront, foundWarp, directionFacing);
					return true;
				    } else if (b.getType().equals(Material.WALL_SIGN) && GridManager.isSafeLocation(oneDown)) {
					// Try one block down if this is a wall sign
					warpPlayer(player, oneDown, foundWarp, directionFacing);
					return true;
				    }
				} else {
				    // Warp has been removed
				    player.sendMessage(ChatColor.RED + plugin.myLocale(player.getUniqueId()).warpserrorDoesNotExist);
				    plugin.getWarpSignsListener().removeWarp(warpSpot);
				    return true;
				}
				if (!(GridManager.isSafeLocation(warpSpot))) {
				    player.sendMessage(ChatColor.RED + plugin.myLocale(player.getUniqueId()).warpserrorNotSafe);
				    // WALL_SIGN's will always be unsafe if the place in front is obscured.
				    if (b.getType().equals(Material.SIGN_POST)) {
					plugin.getLogger().warning(
						"Unsafe warp found at " + warpSpot.toString() + " owned by " + plugin.getPlayers().getName(foundWarp));

				    }
				    return true;
				} else {
				    final Location actualWarp = new Location(warpSpot.getWorld(), warpSpot.getBlockX() + 0.5D, warpSpot.getBlockY(),
					    warpSpot.getBlockZ() + 0.5D);
				    player.teleport(actualWarp);
				    player.getWorld().playSound(player.getLocation(), Sound.BAT_TAKEOFF, 1F, 1F);
				    return true;
				}
			    }
			}
		    } else {
			player.sendMessage(ChatColor.RED + plugin.myLocale(player.getUniqueId()).errorNoPermission);
			return false;
		    }
		} 
	}
	return false;
    }


    /**
     * Warps a player to a spot in front of a sign
     * @param player
     * @param inFront
     * @param foundWarp
     * @param directionFacing
     */
    private void warpPlayer(Player player, Location inFront, UUID foundWarp, BlockFace directionFacing) {
	// convert blockface to angle
	float yaw = Util.blockFaceToFloat(directionFacing);
	final Location actualWarp = new Location(inFront.getWorld(), inFront.getBlockX() + 0.5D, inFront.getBlockY(),
		inFront.getBlockZ() + 0.5D, yaw, 30F);
	player.teleport(actualWarp);
	player.getWorld().playSound(player.getLocation(), Sound.BAT_TAKEOFF, 1F, 1F);
	Player warpOwner = plugin.getServer().getPlayer(foundWarp);
	if (warpOwner != null && !warpOwner.equals(player)) {
	    warpOwner.sendMessage(plugin.myLocale(foundWarp).warpsPlayerWarped.replace("[name]", player.getDisplayName()));
	}
    }


    /**
     * Runs commands 
     * 
     * @param commands
     * @param player
     */
    private void runCommands(List<String> commands, UUID player) {
	// Run any reset commands
	for (String cmd : commands) {
	    // Substitute in any references to player
	    try {
		if (!plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd.replace("[player]", plugin.getPlayers().getName(player)))) {
		    plugin.getLogger().severe("Problem executing commands - skipping!");
		    plugin.getLogger().severe("Command was : " + cmd);
		}
	    } catch (Exception e) {
		plugin.getLogger().severe("Problem executing commands - skipping!");
		plugin.getLogger().severe("Command was : " + cmd);
		plugin.getLogger().severe("Error was: " + e.getMessage());
		e.printStackTrace();
	    }
	}

    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String label, final String[] args) {
	if (!(sender instanceof Player)) {
	    return new ArrayList<String>();
	}
	final Player player = (Player) sender;

	if (!VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.create")) {
	    return new ArrayList<String>();
	}

	final List<String> options = new ArrayList<String>();
	String lastArg = (args.length != 0 ? args[args.length - 1] : "");

	switch (args.length) {
	case 0: 
	case 1: 
	    options.add("help"); //No permission needed.
	    //options.add("make"); //Make is currently a private command never accessible to the player
	    if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.sethome")) {
		options.add("go");
	    }
	    options.add("about"); //No permission needed. :-) Indeed.
	    if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.warp")) {
		options.add("controlpanel");
		options.add("cp");
		options.add("warp");
		options.add("warps");
	    }
	    if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.settings")) {
		options.add("settings");
	    }
	    if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.lang")) {
		options.add("lang");
	    }

	    break;
	case 2: 
	    if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.lang")) {
		if (args[0].equalsIgnoreCase("lang")) {
		    options.add("English");
		    /*
		    options.add("Français");
		    options.add("Deutsch");
		    options.add("Español");
		    options.add("Italiano");
		    options.add("한국의");
		    options.add("Korean");
		    options.add("Polski");
		    options.add("Brasil");
		    options.add("中国");
		    options.add("Chinese");
		    options.add("Čeština");
		    options.add("Slovenčina");*/
		}
	    }
	    if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.sethome")) {
		if (args[0].equalsIgnoreCase("go") || args[0].equalsIgnoreCase("sethome")) {
		    for (int i = 0; i < Settings.maxHomes; i++) {
			options.add(Integer.toString(i));
		    }
		}
	    }
	    if (VaultHelper.checkPerm(player, Settings.PERMPREFIX + "player.warp")
		    && args[0].equalsIgnoreCase("warp")) {
		final Set<UUID> warpList = plugin.getWarpSignsListener().listWarps();

		for (UUID warp : warpList) {
		    options.add(plugin.getPlayers().getName(warp));
		}
	    }
	    break;
	}

	return Util.tabLimit(options, lastArg);
    }
}
