package org.Cry_Legende.sHStaffRTP.network;

import com.google.gson.JsonObject;
import org.Cry_Legende.sHNetwork.api.NetMessage;
import org.Cry_Legende.sHNetwork.api.NetPlayer;
import org.Cry_Legende.sHNetwork.api.SHNetworkAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Pont vers SHNetwork. Cette classe n'est chargée que si SHNetwork est installé.
 *
 * <pre>
 * staffrtp.demande   serveur du staff -> tous    {id, staff, cible, serveur}
 * staffrtp.reponse   serveur du joueur -> staff  {id, vers, ok}
 * </pre>
 *
 * Le serveur du joueur visité vérifie qu'il peut l'être, retient la visite attendue, puis répond ; le serveur du
 * staff envoie alors le membre du staff, qui est téléporté au joueur à son arrivée.
 */
public final class ShNetworkStaffRtp implements StaffNetwork, Listener {

    private static final String ASK = "staffrtp.demande";
    private static final String ANSWER = "staffrtp.reponse";
    private static final long EXPECTED_MS = 30_000L;

    private record Expected(UUID target, long until) {
    }

    private final JavaPlugin plugin;
    private final SHNetworkAPI api;
    private final Host host;
    private final long answerTicks;
    private final long arrivalTicks;
    /** Demandes en attente de réponse. */
    private final Map<String, Consumer<Boolean>> waiting = new ConcurrentHashMap<>();
    /** Visites acceptées ici, attendues à l'arrivée du membre du staff. */
    private final Map<UUID, Expected> expected = new ConcurrentHashMap<>();

    private ShNetworkStaffRtp(JavaPlugin plugin, Host host, long answerTicks, long arrivalTicks) {
        this.plugin = plugin;
        this.api = SHNetworkAPI.get();
        this.host = host;
        this.answerTicks = answerTicks;
        this.arrivalTicks = arrivalTicks;
        api.subscribe(ASK, this::onAsk, true);
        api.subscribe(ANSWER, this::onAnswer, true);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Point d'entrée : le plugin ne résout cette classe qu'à cet appel, donc jamais sans SHNetwork. */
    public static StaffNetwork open(JavaPlugin plugin, Host host, long answerTicks, long arrivalTicks) {
        return new ShNetworkStaffRtp(plugin, host, answerTicks, arrivalTicks);
    }

    @Override
    public List<Remote> remotePlayers() {
        List<Remote> out = new ArrayList<>();
        for (NetPlayer p : api.online()) {
            if (!p.server().equalsIgnoreCase(api.serverName())) {
                out.add(new Remote(p.uuid(), p.name(), p.server()));
            }
        }
        return out;
    }

    @Override
    public void ask(Player staff, Remote target, Consumer<Boolean> answer) {
        String id = UUID.randomUUID().toString();
        waiting.put(id, answer);
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("staff", staff.getUniqueId().toString());
        o.addProperty("cible", target.uuid().toString());
        o.addProperty("serveur", target.server());
        api.publish(ASK, o.toString());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Consumer<Boolean> late = waiting.remove(id);
            if (late != null) {
                late.accept(false);
            }
        }, answerTicks);
    }

    @Override
    public void send(Player staff, Remote target) {
        api.sendTo(staff, target.server());
    }

    /** Fil principal : un membre du staff demande à visiter un joueur. */
    private void onAsk(NetMessage message) {
        JsonObject o = message.object();
        if (!o.get("serveur").getAsString().equalsIgnoreCase(api.serverName())) {
            return;
        }
        long now = System.currentTimeMillis();
        expected.values().removeIf(e -> e.until() < now);
        UUID staff = UUID.fromString(o.get("staff").getAsString());
        UUID targetId = UUID.fromString(o.get("cible").getAsString());
        Player target = Bukkit.getPlayer(targetId);
        boolean ok = target != null && host.accepts(target);
        if (ok) {
            expected.put(staff, new Expected(targetId, now + EXPECTED_MS));
        }
        JsonObject answer = new JsonObject();
        answer.addProperty("id", o.get("id").getAsString());
        answer.addProperty("vers", message.origin());
        answer.addProperty("ok", ok);
        api.publish(ANSWER, answer.toString());
    }

    /** Fil principal : réponse du serveur du joueur visité. */
    private void onAnswer(NetMessage message) {
        JsonObject o = message.object();
        if (!o.get("vers").getAsString().equalsIgnoreCase(api.serverName())) {
            return;
        }
        Consumer<Boolean> callback = waiting.remove(o.get("id").getAsString());
        if (callback != null) {
            callback.accept(o.get("ok").getAsBoolean());
        }
    }

    /** Arrivée d'un membre du staff attendu : une fois sa sauvegarde reprise, il est téléporté au joueur. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID staffId = event.getPlayer().getUniqueId();
        Expected visit = expected.remove(staffId);
        if (visit == null || visit.until() < System.currentTimeMillis()) {
            return;
        }
        api.awaitSaved(staffId, Duration.ofSeconds(10)).thenAccept(saved -> api.sync(() ->
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player staff = Bukkit.getPlayer(staffId);
                Player target = Bukkit.getPlayer(visit.target());
                if (staff != null && target != null) {
                    host.arrive(staff, target);
                }
            }, arrivalTicks)));
    }
}
