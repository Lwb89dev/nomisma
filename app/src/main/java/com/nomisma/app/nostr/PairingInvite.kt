package com.nomisma.app.nostr

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.Base64

data class PairingInvite(
    val ledgerId: String,
    val relayUrl: String,
    val senderPubkey: String,
    val senderParticipantId: String,
    val receiverParticipantId: String
) {
    fun normalized(): PairingInvite {
        val normalizedRelay = relayUrl.trim()
        val normalizedPubkey = senderPubkey.trim().lowercase()
        val normalizedLedger = ledgerId.trim()
        val sender = senderParticipantId.trim()
        val receiver = receiverParticipantId.trim()
        require(normalizedLedger.isNotBlank() && normalizedLedger.length <= 120) { "Ledger pairing non valido" }
        require(RelayTransportPolicy.isValidWebSocketUrl(normalizedRelay)) {
            "Il relay del pairing deve usare ws:// o wss://"
        }
        require(normalizedPubkey.length == 64 && normalizedPubkey.all { it in "0123456789abcdef" }) {
            "Chiave pubblica del pairing non valida"
        }
        require(
            sender.isNotBlank() && receiver.isNotBlank() && sender != receiver &&
                sender.length <= 120 && receiver.length <= 120
        ) {
            "Associazione partecipanti del pairing non valida"
        }
        return copy(
            ledgerId = normalizedLedger,
            relayUrl = normalizedRelay,
            senderPubkey = normalizedPubkey,
            senderParticipantId = sender,
            receiverParticipantId = receiver
        )
    }
}

object PairingInviteCodec {
    private const val PREFIX = "nomisma1:"
    private const val CHECKSUM_BYTES = 8

    fun encode(invite: PairingInvite): String {
        val normalized = invite.normalized()
        val body = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeByte(1)
                output.writeUTF(normalized.ledgerId)
                output.writeUTF(normalized.relayUrl)
                output.writeUTF(normalized.senderPubkey)
                output.writeUTF(normalized.senderParticipantId)
                output.writeUTF(normalized.receiverParticipantId)
            }
            bytes.toByteArray()
        }
        val payload = body + checksum(body)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
    }

    fun decode(raw: String): PairingInvite {
        val prefixIndex = raw.indexOf(PREFIX)
        require(prefixIndex >= 0) { "Codice pairing Nomisma non riconosciuto" }
        val encoded = raw.substring(prefixIndex + PREFIX.length)
            .takeWhile { !it.isWhitespace() }
            .trim()
        require(encoded.isNotBlank()) { "Codice pairing vuoto" }
        val bytes = runCatching { Base64.getUrlDecoder().decode(encoded) }
            .getOrElse { throw IllegalArgumentException("Codice pairing danneggiato", it) }
        require(bytes.size > CHECKSUM_BYTES) { "Codice pairing incompleto" }
        val body = bytes.copyOfRange(0, bytes.size - CHECKSUM_BYTES)
        val suppliedChecksum = bytes.copyOfRange(bytes.size - CHECKSUM_BYTES, bytes.size)
        require(MessageDigest.isEqual(checksum(body), suppliedChecksum)) {
            "Il controllo integrità del pairing non corrisponde"
        }
        return runCatching {
            DataInputStream(ByteArrayInputStream(body)).use { input ->
                require(input.readUnsignedByte() == 1) { "Versione pairing non supportata" }
                val invite = PairingInvite(
                    ledgerId = input.readUTF(),
                    relayUrl = input.readUTF(),
                    senderPubkey = input.readUTF(),
                    senderParticipantId = input.readUTF(),
                    receiverParticipantId = input.readUTF()
                )
                require(input.read() == -1) { "Dati aggiuntivi nel codice pairing" }
                invite.normalized()
            }
        }.getOrElse {
            if (it is IllegalArgumentException) throw it
            throw IllegalArgumentException("Codice pairing danneggiato", it)
        }
    }

    private fun checksum(payload: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(payload).copyOf(CHECKSUM_BYTES)
}

/** Finds a valid pairing code inside free text such as a chat message or a share. */
object PairingInviteText {
    private val candidate = Regex("nomisma1:[A-Za-z0-9_-]{16,}")

    fun find(text: CharSequence?): String? =
        text?.let { candidate.findAll(it).map { match -> match.value }.firstOrNull(::isValid) }

    fun isValid(code: String): Boolean = runCatching { PairingInviteCodec.decode(code) }.isSuccess
}

data class PairingReadiness(
    val relayReady: Boolean,
    val identityReady: Boolean,
    val recipientReady: Boolean,
    val participantsReady: Boolean,
    val keysAreDistinct: Boolean
) {
    val isReady: Boolean
        get() = relayReady && identityReady && recipientReady && participantsReady && keysAreDistinct
}

object PairingReadinessEvaluator {
    fun evaluate(
        relayUrl: String,
        localPubkey: String?,
        recipientPubkey: String,
        localParticipantId: String,
        remoteParticipantId: String
    ): PairingReadiness {
        val recipient = recipientPubkey.trim().lowercase()
        val local = localPubkey?.trim()?.lowercase()
        val relayReady = RelayTransportPolicy.isSupported(relayUrl)
        val identityReady = local?.let(::isHexPubkey) == true
        val recipientReady = isHexPubkey(recipient)
        return PairingReadiness(
            relayReady = relayReady,
            identityReady = identityReady,
            recipientReady = recipientReady,
            participantsReady = localParticipantId.isNotBlank() && remoteParticipantId.isNotBlank() &&
                localParticipantId != remoteParticipantId,
            keysAreDistinct = local == null || !recipientReady || local != recipient
        )
    }

    private fun isHexPubkey(value: String): Boolean =
        value.length == 64 && value.all { it in "0123456789abcdef" }
}

object RelayTransportPolicy {
    const val ALLOWED_CLEARTEXT_HOST = "192.168.1.10"

    fun isValidWebSocketUrl(raw: String): Boolean = parse(raw) != null

    fun isSupported(raw: String): Boolean {
        val uri = parse(raw) ?: return false
        return uri.scheme.equals("wss", ignoreCase = true) || uri.host == ALLOWED_CLEARTEXT_HOST
    }

    fun isCleartext(raw: String): Boolean =
        parse(raw)?.scheme.equals("ws", ignoreCase = true)

    private fun parse(raw: String): URI? = runCatching { URI(raw.trim()) }.getOrNull()?.takeIf { uri ->
        uri.scheme?.lowercase() in setOf("ws", "wss") &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.fragment == null &&
            raw.trim().length <= 2_048
    }

}
