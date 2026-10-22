package com.autoalert.app

object Config {
    // Adresse de ton site WordPress (https, sans « / » à la fin)
    const val SERVER = "https://jarti.app"

    const val POLL_MS = 10_000L            // le récepteur interroge le serveur toutes les 10 s
    const val POS_EVERY_MS = 15_000L       // la voiture envoie sa position toutes les 15 s
    const val TRACK_MS = 15 * 60_000L      // durée de suivi GPS après une alerte
    const val CMD_EVERY_MS = 20_000L       // la voiture vérifie toutes les 20 s si on l'interroge
    const val ALERT_COOLDOWN_MS = 60_000L  // délai minimum entre 2 alertes
}
