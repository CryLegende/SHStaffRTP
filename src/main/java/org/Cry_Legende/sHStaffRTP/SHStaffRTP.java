package org.Cry_Legende.sHStaffRTP;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.Cry_Legende.sHStaffRTP.network.ShNetworkStaffRtp;
import org.Cry_Legende.sHStaffRTP.network.StaffNetwork;
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


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class SHStaffRTP extends JavaPlugin implements Listener {

    // UUID et non Player : un objet Player gardé en mémoire survivrait à la déconnexion
    private final Set<UUID> previouslyTeleportedPlayers = new HashSet<>();
    private FileConfiguration config;
    /** Pont vers SHNetwork, ou null quand le mode réseau est dormant. */
    private StaffNetwork network;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        config = this.getConfig();
        this.getCommand("staffrtp").setExecutor(new StaffRTPCommand());
        getServer().getPluginManager().registerEvents(this, this);
        enableNetwork();
    }

    /**
     * Passe en mode réseau si SHNetwork est installé et 'reseau.actif' est vrai. Les classes qui touchent à SHNetwork
     * ne sont résolues qu'ici : sans lui, rien n'est chargé et /staffrtp garde son fonctionnement d'origine.
     */
    private void enableNetwork() {
        if (!config.getBoolean("reseau.actif", true)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("SHNetwork")) return;
        try {
            network = ShNetworkStaffRtp.open(this, new StaffNetwork.Host() {
                @Override
                public boolean accepts(Player target) {
                    return !target.hasPermission("shstaffrtp.bypass");
                }

                @Override
                public void arrive(Player staff, Player target) {
                    teleportTo(staff, target);
                }
            }, config.getLong("reseau.attente-reponse-ticks", 40L), config.getLong("reseau.delai-arrivee-ticks", 30L));
            getLogger().info("SHNetwork détecté : /staffrtp tire parmi les joueurs de tout le réseau.");
        } catch (RuntimeException | LinkageError e) {
            network = null;
            getLogger().log(Level.SEVERE, "SHNetwork présent mais inutilisable, /staffrtp local à ce serveur : " + e);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Un joueur qui revient peut de nouveau être tiré au sort
        previouslyTeleportedPlayers.remove(event.getPlayer().getUniqueId());
    }

    /** Joueurs de ce serveur que le membre du staff peut encore visiter. */
    private List<Player> localTargets(Player staff) {
        List<Player> possibleTargets = new LinkedList<>(Bukkit.getServer().getOnlinePlayers());
        possibleTargets.removeIf(p -> p.equals(staff) || p.hasPermission("shstaffrtp.bypass"));
        possibleTargets.removeIf(p -> previouslyTeleportedPlayers.contains(p.getUniqueId()));
        return possibleTargets;
    }

    /** Joueurs des autres serveurs que le membre du staff n'a pas encore visités. */
    private List<StaffNetwork.Remote> remoteTargets(Player staff) {
        List<StaffNetwork.Remote> targets = new ArrayList<>(network.remotePlayers());
        targets.removeIf(r -> r.uuid().equals(staff.getUniqueId()) || previouslyTeleportedPlayers.contains(r.uuid())
            || Bukkit.getPlayer(r.uuid()) != null);
        return targets;
    }

    /**
     * Mode réseau : le tirage porte sur les joueurs de tout le réseau. Un joueur d'un autre serveur est d'abord
     * proposé à son serveur (qui refuse les joueurs protégés par shstaffrtp.bypass) ; s'il refuse ou ne répond pas,
     * il est écarté du tour et un autre est tiré.
     */
    private void visitAnywhere(Player staff) {
        List<Player> local = localTargets(staff);
        List<StaffNetwork.Remote> remote = remoteTargets(staff);
        int size = local.size() + remote.size();
        if (size == 0) {
            previouslyTeleportedPlayers.clear();
            sendMessage(staff, null, "messages.allPlayersVisited");
            return;
        }
        int index = ThreadLocalRandom.current().nextInt(size);
        if (index < local.size()) {
            Player target = local.get(index);
            previouslyTeleportedPlayers.add(target.getUniqueId());
            teleportTo(staff, target);
            return;
        }
        StaffNetwork.Remote target = remote.get(index - local.size());
        previouslyTeleportedPlayers.add(target.uuid());
        network.ask(staff, target, accepted -> {
            if (!staff.isOnline()) return;
            if (accepted) {
                network.send(staff, target);
            } else {
                visitAnywhere(staff);
            }
        });
    }

    private void teleportTo(Player staff, Player target) {
        staff.teleport(target.getLocation());
        sendMessage(staff, target, "messages.teleportedTo");
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

            if (network != null) {
                visitAnywhere(staff);
                return true;
            }

            List<Player> possibleTargets = localTargets(staff);

            if (possibleTargets.isEmpty()) {
                previouslyTeleportedPlayers.clear();
                sendMessage(staff, null, "messages.allPlayersVisited");
                return true;
            }

            Collections.shuffle(possibleTargets);
            Player target = possibleTargets.get(0);
            previouslyTeleportedPlayers.add(target.getUniqueId());

            teleportTo(staff, target);
            return true;
        }
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
