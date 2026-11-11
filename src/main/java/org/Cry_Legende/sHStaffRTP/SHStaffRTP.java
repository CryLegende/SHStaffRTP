package org.Cry_Legende.sHStaffRTP;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.Cry_Legende.sHStaffRTP.network.ShNetworkStaffRtp;
import org.Cry_Legende.sHStaffRTP.network.StaffNetwork;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.chat.*;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.ChatColor;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.io.IOException;

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
    private volatile Messages messages;
    /** Pont vers SHNetwork, ou null quand le mode réseau est dormant. */
    private StaffNetwork network;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        // Crée messages.yml au premier démarrage, avec les textes de l'ancienne section messages: de config.yml
        Messages.prepare(this);
        try {
            messages = Messages.load(this);
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().warning("messages.yml illisible, textes du jar utilisés : " + e.getMessage());
            messages = Messages.defaults(this);
        }
        this.getCommand("staffrtp").setExecutor(new StaffRTPCommand());
        AdminCommand admin = new AdminCommand();
        this.getCommand("shstaffrtp").setExecutor(admin);
        this.getCommand("shstaffrtp").setTabCompleter(admin);
        getServer().getPluginManager().registerEvents(this, this);
        enableNetwork();
    }

    /**
     * Passe en mode réseau si SHNetwork est installé et 'reseau.actif' est vrai. Les classes qui touchent à SHNetwork
     * ne sont résolues qu'ici : sans lui, rien n'est chargé et /staffrtp garde son fonctionnement d'origine.
     */
    private void enableNetwork() {
        if (!getConfig().getBoolean("reseau.actif", true)) return;
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
            }, getConfig().getLong("reseau.attente-reponse-ticks", 40L), getConfig().getLong("reseau.delai-arrivee-ticks", 30L));
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
            sendMessage(staff, null, "allPlayersVisited");
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
        sendMessage(staff, target, "teleportedTo");
    }

    private class StaffRTPCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player staff)) {
                // La console n'est pas un Player : le cast d'avant levait une ClassCastException
                sendMessage(sender, null, "onlyPlayers");
                return true;
            }
            if (!staff.hasPermission("shstaffrtp.use")) {
                sendMessage(staff, null, "noPermission");
                return true;
            }

            if (network != null) {
                visitAnywhere(staff);
                return true;
            }

            List<Player> possibleTargets = localTargets(staff);

            if (possibleTargets.isEmpty()) {
                previouslyTeleportedPlayers.clear();
                sendMessage(staff, null, "allPlayersVisited");
                return true;
            }

            Collections.shuffle(possibleTargets);
            Player target = possibleTargets.get(0);
            previouslyTeleportedPlayers.add(target.getUniqueId());

            teleportTo(staff, target);
            return true;
        }
    }

    /** /shstaffrtp reload et reset. */
    private class AdminCommand implements CommandExecutor, TabCompleter {

        private static final List<String> SUBS = List.of("reload", "reset");

        private List<String> allowed(CommandSender sender) {
            List<String> allowed = new ArrayList<>();
            for (String sub : SUBS) {
                if (sender.hasPermission("shstaffrtp.admin." + sub)) {
                    allowed.add(sub);
                }
            }
            return allowed;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            List<String> allowed = allowed(sender);
            if (allowed.isEmpty()) {
                sendMessage(sender, null, "noPermission");
                return true;
            }
            String sub = args.length == 0 ? "" : args[0].toLowerCase(java.util.Locale.ROOT);
            if (!allowed.contains(sub)) {
                send(sender, "usage", "commands", String.join("|", allowed));
            } else if (sub.equals("reload")) {
                reload(sender);
            } else {
                previouslyTeleportedPlayers.clear();
                send(sender, "reset-done");
            }
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            List<String> completions = new ArrayList<>();
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], allowed(sender), completions);
            }
            return completions;
        }
    }

    /**
     * Relit config.yml et messages.yml. Les deux sont lus à part et ne remplacent l'état en cours que s'ils sont
     * valides ; sinon l'ancienne configuration reste active et le message donne le fichier et l'erreur.
     * La liste des joueurs déjà visités n'est pas touchée.
     */
    private void reload(CommandSender sender) {
        String file = "config.yml";
        try {
            new YamlConfiguration().load(new File(getDataFolder(), file));
            file = "messages.yml";
            Messages loaded = Messages.load(this);
            String json = loaded.get("teleportedTo");
            if (json != null) {
                ComponentSerializer.parse(ChatColor.translateAlternateColorCodes('&', json));
            }
            reloadConfig();
            messages = loaded;
            send(sender, "reloaded");
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {
            String error = e.getMessage() == null ? e.toString() : e.getMessage().lines().findFirst().orElse("");
            getLogger().warning(file + " illisible, l'ancienne configuration reste active : " + error);
            send(sender, "reload-failed", "file", file, "error", error);
        }
    }

    private void send(CommandSender sender, String key, String... pairs) {
        String text = messages.get(key, pairs);
        if (text != null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', text));
        }
    }

    private void sendMessage(CommandSender staff, Player target, String path) {
        String message = messages.get(path);
        if (message == null) return;
        if (target != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            message = PlaceholderAPI.setPlaceholders(target, message);
        }

        if (path.equals("teleportedTo")) {
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
            getLogger().log(Level.WARNING, "teleportedTo de messages.yml n'est pas un JSON valide", e);
            return new BaseComponent[]{};
        }
    }
}
