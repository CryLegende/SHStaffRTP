package org.Cry_Legende.sHStaffRTP;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.chat.*;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.ChatColor;


import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class SHStaffRTP extends JavaPlugin {

    private final List<Player> previouslyTeleportedPlayers = new LinkedList<>();
    private FileConfiguration config;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        config = this.getConfig();
        this.getCommand("staffrtp").setExecutor(new StaffRTPCommand());
    }

    private class StaffRTPCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sendMessage((Player) sender, null, "messages.onlyPlayers");
                return true;
            }
            Player staff = (Player) sender;
            if (!staff.hasPermission("shstaffrtp.use")) {
                sendMessage(staff, null, "messages.noPermission");
                return true;
            }

            List<Player> possibleTargets = new LinkedList<>(Bukkit.getServer().getOnlinePlayers());
            possibleTargets.removeIf(p -> p.equals(staff) || p.hasPermission("shstaffrtp.bypass"));
            possibleTargets.removeAll(previouslyTeleportedPlayers);

            if (possibleTargets.isEmpty()) {
                previouslyTeleportedPlayers.clear();
                sendMessage(staff, null, "messages.allPlayersVisited");
                return true;
            }

            Collections.shuffle(possibleTargets);
            Player target = possibleTargets.get(0);
            previouslyTeleportedPlayers.add(target);

            staff.teleport(target.getLocation());
            sendMessage(staff, target, "messages.teleportedTo");
            return true;
        }

        private void sendMessage(Player staff, Player target, String path) {
            String message = config.getString(path);
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
                // Log des erreurs
                e.printStackTrace();
                return new BaseComponent[]{};
            }
        }


    }
}



