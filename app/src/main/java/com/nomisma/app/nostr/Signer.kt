package com.nomisma.app.nostr

interface Signer {
    suspend fun getIdentity(): SignerIdentity
    suspend fun refreshIdentity(): SignerIdentity = getIdentity()
    suspend fun signEvent(event: UnsignedNostrEvent): NostrEvent
    suspend fun nip44Encrypt(plaintext: String, recipientPubkey: String): String
    suspend fun nip44Decrypt(ciphertext: String, senderPubkey: String): String
}

data class SignerIdentity(
    val pubkey: String,
    val packageName: String
)

object SignerResponseValidator {
    fun requireMatchesRequest(
        requested: UnsignedNostrEvent,
        expectedPubkey: String,
        returned: NostrEvent
    ): NostrEvent {
        require(returned.pubkey.equals(expectedPubkey, ignoreCase = true)) {
            "Amber ha firmato con un'identità diversa da quella selezionata"
        }
        require(returned.createdAt == requested.createdAt) { "Amber ha modificato il timestamp dell'evento" }
        require(returned.kind == requested.kind) { "Amber ha modificato il kind dell'evento" }
        require(returned.tags == requested.tags) { "Amber ha modificato i tag dell'evento" }
        require(returned.content == requested.content) { "Amber ha modificato il contenuto dell'evento" }
        return returned
    }
}
