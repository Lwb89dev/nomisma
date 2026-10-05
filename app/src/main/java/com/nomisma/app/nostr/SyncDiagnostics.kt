package com.nomisma.app.nostr

import java.net.URI

enum class SyncHealthLevel {
    LOCAL_ONLY,
    SETUP_REQUIRED,
    READY,
    WORKING,
    HEALTHY,
    ATTENTION
}

data class SyncHealthSummary(
    val level: SyncHealthLevel,
    val title: String,
    val detail: String
)

object SyncHealthEvaluator {
    fun evaluate(state: NostrSessionState, readiness: PairingReadiness): SyncHealthSummary {
        val stats = state.operationStats
        if (state.error != null || stats.rejected > 0) {
            return SyncHealthSummary(
                SyncHealthLevel.ATTENTION,
                "Sincronizzazione da controllare",
                state.error?.cleanDiagnosticLine()
                    ?: "${stats.rejected} operazioni remote sono state rifiutate"
            )
        }
        return when (state.status) {
            SyncConnectionStatus.CONNECTED -> if (state.backlogComplete) {
                SyncHealthSummary(
                    SyncHealthLevel.HEALTHY,
                    "Ledger allineato",
                    if (stats.pending == 0) "Storico ricevuto e outbox vuota"
                    else "Storico ricevuto · ${stats.pending} operazioni locali da pubblicare"
                )
            } else {
                SyncHealthSummary(SyncHealthLevel.WORKING, "Recupero dello storico", "Il ledger locale resta utilizzabile")
            }
            SyncConnectionStatus.CONNECTING ->
                SyncHealthSummary(SyncHealthLevel.WORKING, "Connessione in corso", "Apertura della sessione relay")
            SyncConnectionStatus.BACKOFF -> SyncHealthSummary(
                SyncHealthLevel.WORKING,
                "Riconnessione programmata",
                "Tentativo ${state.reconnectAttempt} · i dati locali restano disponibili"
            )
            SyncConnectionStatus.DISCONNECTED -> when {
                readiness.isReady -> SyncHealthSummary(
                    SyncHealthLevel.READY,
                    "Pronto a connettersi",
                    if (stats.pending == 0) "Pairing completo"
                    else "${stats.pending} operazioni attendono la pubblicazione"
                )
                readiness.identityReady || readiness.recipientReady -> SyncHealthSummary(
                    SyncHealthLevel.SETUP_REQUIRED,
                    "Configurazione incompleta",
                    "Completa i controlli del pairing prima di connetterti"
                )
                else -> SyncHealthSummary(
                    SyncHealthLevel.LOCAL_ONLY,
                    "Modalità locale",
                    "Le spese restano sul dispositivo finché non configuri la sincronizzazione"
                )
            }
        }
    }
}

object SyncDiagnosticsFormatter {
    fun format(
        state: NostrSessionState,
        readiness: PairingReadiness,
        relayUrl: String,
        recipientPubkey: String
    ): String = buildString {
        appendLine("Nomisma · diagnostica sincronizzazione")
        appendLine("stato=${state.status.name.lowercase()}")
        appendLine("storico_allineato=${state.backlogComplete}")
        appendLine("relay=${safeRelayEndpoint(relayUrl)}")
        appendLine("trasporto=${if (RelayTransportPolicy.isCleartext(relayUrl)) "lan_ws" else "tls_wss"}")
        appendLine("identita=${pubkeyFingerprint(state.identity?.pubkey)}")
        appendLine("destinatario=${pubkeyFingerprint(recipientPubkey)}")
        appendLine("pairing_pronto=${readiness.isReady}")
        appendLine("outbox=${state.operationStats.pending}")
        appendLine("pubblicate=${state.operationStats.published}")
        appendLine("ricevute=${state.operationStats.applied}")
        appendLine("differite=${state.operationStats.deferred}")
        appendLine("rifiutate=${state.operationStats.rejected}")
        append("errore=${if (state.error == null) "nessuno" else "presente"}")
    }

    private fun safeRelayEndpoint(raw: String): String = runCatching {
        val uri = URI(raw.trim())
        buildString {
            append(uri.scheme?.lowercase() ?: "?")
            append("://")
            append(uri.host ?: "non-valido")
            if (uri.port >= 0) append(":${uri.port}")
        }
    }.getOrDefault("non-valido")
}

fun pubkeyFingerprint(pubkey: String?): String {
    val normalized = pubkey?.trim()?.lowercase().orEmpty()
    return if (normalized.length == 64 && normalized.all { it in "0123456789abcdef" }) {
        "${normalized.take(8)}…${normalized.takeLast(8)}"
    } else {
        "non-configurata"
    }
}

private fun String.cleanDiagnosticLine(): String =
    replace(Regex("[\\r\\n\\t]+"), " ").trim().take(240)
