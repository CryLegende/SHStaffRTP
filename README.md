<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHStaffRTP

*/staffrtp : téléporte un membre du staff vers un joueur connecté qu'il n'a pas encore visité.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

/staffrtp : téléporte un membre du staff vers un joueur connecté qu'il n'a pas encore visité.

Utilisé sur : **Opale**.

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/staffrtp` | — | Téléporte le staff à un joueur aléatoire | — |

## Plusieurs serveurs (SHNetwork)

Avec SHNetwork installé et `reseau.actif: true` (valeur par défaut), `/staffrtp` tire parmi les joueurs de tous les serveurs, pas seulement ceux du serveur du staff.

- Un joueur tiré sur un autre serveur est d'abord proposé à son serveur, qui refuse ceux qui ont `shstaffrtp.bypass` ; un autre joueur est alors tiré.
- Si le serveur accepte, le membre du staff y est envoyé puis téléporté au joueur dès son arrivée, avec le même message qu'en local.
- Le tour des joueurs déjà visités est tenu par chaque serveur.

Canaux du bus : `staffrtp.demande` (serveur du staff vers tous) et `staffrtp.reponse` (serveur du joueur vers le staff). Rien n'est écrit en base.

Sans SHNetwork, ou avec `reseau.actif: false`, rien ne change : le tirage reste local.

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHStaffRTP/
└── <a href="src/main/resources/config.yml">config.yml</a>
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
