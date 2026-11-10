package org.Cry_Legende.sHStaffRTP.network;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Ce dont /staffrtp a besoin du réseau. Seule {@link ShNetworkStaffRtp} connaît SHNetwork : sans lui, aucune
 * classe de ce paquet n'est chargée.
 */
public interface StaffNetwork {

    /** Joueur connecté sur un autre serveur du réseau. */
    record Remote(UUID uuid, String name, String server) {
    }

    /** Ce que le serveur du joueur visité décide, côté plugin. */
    interface Host {

        /** Vrai si ce joueur de ce serveur peut être visité (sans {@code shstaffrtp.bypass}). */
        boolean accepts(Player target);

        /** Appelé à l'arrivée du membre du staff : téléportation et message, comme pour un joueur du même serveur. */
        void arrive(Player staff, Player target);
    }

    /** Joueurs connectés sur les autres serveurs du réseau. */
    List<Remote> remotePlayers();

    /**
     * Demande au serveur du joueur s'il peut être visité. La réponse arrive sur le fil principal : vrai si le
     * serveur accepte, faux s'il refuse ou ne répond pas à temps.
     */
    void ask(Player staff, Remote target, Consumer<Boolean> answer);

    /** Envoie le membre du staff sur le serveur du joueur ; il est téléporté à lui dès son arrivée. */
    void send(Player staff, Remote target);
}
