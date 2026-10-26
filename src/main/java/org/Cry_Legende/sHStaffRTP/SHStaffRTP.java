package org.Cry_Legende.sHStaffRTP;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.chat.*;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.ChatColor;


import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class SHStaffRTP extends JavaPlugin implements Listener {

    // UUID et non Player : un objet Player gardé en mémoire survivrait à la déconnexion
    private final Set<UUID> previouslyTeleportedPlayers = new HashSet<>();
    private FileConfiguration config;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        config = this.getConfig();
        this.getCommand("staffrtp").setExecutor(new StaffRTPCommand());
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Un joueur qui revient peut de nouveau être tiré au sort
        previouslyTeleportedPlayers.remove(event.getPlayer().getUniqueId());
    }

    private class StaffRTPCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player staff)) {
                // La console n'est pas un Player : le cast d'avant levait une ClassCastException
                sendMessage(sender, null, "messages.onlyPlayers");
                return true;
            }
            if (!staff.hasPermission("shstaffrtp.use")) {
                sendMessage(staff, null, "messages.noPermission");
                return true;
            }

            List<Player> possibleTargets = new LinkedList<>(Bukkit.getServer().getOnlinePlayers());
            possibleTargets.removeIf(p -> p.equals(staff) || p.hasPermission("shstaffrtp.bypass"));
            possibleTargets.removeIf(p -> previouslyTeleportedPlayers.contains(p.getUniqueId()));

            if (possibleTargets.isEmpty()) {
                previouslyTeleportedPlayers.clear();
                sendMessage(staff, null, "messages.allPlayersVisited");
                return true;
            }

            Collections.shuffle(possibleTargets);
            Player target = possibleTargets.get(0);
            previouslyTeleportedPlayers.add(target.getUniqueId());

            staff.teleport(target.getLocation());
            sendMessage(staff, target, "messages.teleportedTo");
            return true;
        }

        private void sendMessage(CommandSender staff, Player target, String path) {
            String message = config.getString(path);
            if (message == null) return;
            if (target != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                message = PlaceholderAPI.setPlaceholders(target, message);
            }

            if (path.equals("messages.teleportedTo")) {
                // Traitement spécifique pour le message 'teleportedTo'
                message = ChatColor.translateAlternateColorCodes('&', message);
                BaseComponent[] components = parseJsonMessage(message);
                staff.spigot().sendMessage(components);
            } else {
                // Traitement standard pour les autres messages
                staff.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            }
        }

        private BaseComponent[] parseJsonMessage(String jsonString) {
            try {
                return ComponentSerializer.parse(jsonString);
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "messages.teleportedTo n'est pas un JSON valide", e);
                return new BaseComponent[]{};
            }
        }


    }
}



