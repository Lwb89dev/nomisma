package com.nomisma.app.nostr

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AmberSigner(
    private val activity: ComponentActivity
) : Signer, DefaultLifecycleObserver {
    private val preferences = activity.getSharedPreferences("nomisma_signer", Activity.MODE_PRIVATE)
    private val pending = ConcurrentHashMap<String, CancellableContinuation<ActivityResult>>()
    private var identity: SignerIdentity? = preferences.getString(KEY_PUBKEY, null)
        ?.trim()
        ?.lowercase()
        ?.takeIf(::isValidPubkey)
        ?.let { pubkey -> SignerIdentity(pubkey, preferences.getString(KEY_PACKAGE, "").orEmpty()) }
    private val launcher = activity.activityResultRegistry.register(
        "nomisma-amber-${UUID.randomUUID()}",
        activity,
        ActivityResultContracts.StartActivityForResult(),
        ActivityResultCallback { result ->
            val id = result.data?.getStringExtra("id")
            if (id != null) pending.remove(id)?.resume(result)
        }
    )

    init {
        activity.lifecycle.addObserver(this)
    }

    override suspend fun getIdentity(): SignerIdentity {
        identity?.let { return it }
        return requestIdentity()
    }

    override suspend fun refreshIdentity(): SignerIdentity {
        val previousIdentity = identity
        identity = null
        return runCatching { requestIdentity() }
            .onFailure { identity = previousIdentity }
            .getOrThrow()
    }

    private suspend fun requestIdentity(): SignerIdentity {
        val response = request(
            type = "get_public_key",
            payload = "",
            packageName = null
        )
        check(response.resultCode == Activity.RESULT_OK) { "Amber failed to return a public key" }
        check(response.data?.getBooleanExtra("rejected", false) != true) { "Amber public-key request rejected" }
        val pubkey = response.data?.getStringExtra("result")?.lowercase()
            ?: error("Amber did not return a public key")
        check(isValidPubkey(pubkey)) {
            "Amber ha restituito una chiave pubblica non valida"
        }
        val packageName = response.data?.getStringExtra("package").orEmpty()
        return SignerIdentity(pubkey, packageName).also {
            identity = it
            preferences.edit()
                .putString(KEY_PUBKEY, it.pubkey)
                .putString(KEY_PACKAGE, it.packageName)
                .apply()
        }
    }

    override suspend fun signEvent(event: UnsignedNostrEvent): NostrEvent {
        val signerIdentity = getIdentity()
        val response = request(
            type = "sign_event",
            payload = event.copy(pubkey = signerIdentity.pubkey).toSignerJson(),
            packageName = signerIdentity.packageName,
            currentUser = signerIdentity.pubkey
        )
        check(response.resultCode == Activity.RESULT_OK) { "Amber failed to sign event" }
        check(response.data?.getBooleanExtra("rejected", false) != true) { "Amber signing request rejected" }

        val returnedEventJson = response.data?.getStringExtra("event")
            ?: response.data?.getStringExtra("result")?.takeIf { it.trimStart().startsWith("{") }
        if (returnedEventJson != null) {
            return SignerResponseValidator.requireMatchesRequest(
                requested = event,
                expectedPubkey = signerIdentity.pubkey,
                returned = NostrEvent.fromJson(returnedEventJson)
            )
        }

        val signature = response.data?.getStringExtra("result")
            ?: error("Amber did not return a signature or event")
        val signedEvent = event.copy(pubkey = signerIdentity.pubkey)
        return NostrEvent(
            id = NostrHash.sha256Hex(signedEvent.canonicalSerialization(signerIdentity.pubkey)),
            pubkey = signerIdentity.pubkey,
            createdAt = signedEvent.createdAt,
            kind = signedEvent.kind,
            tags = signedEvent.tags,
            content = signedEvent.content,
            sig = signature
        )
    }

    override suspend fun nip44Encrypt(plaintext: String, recipientPubkey: String): String {
        val signerIdentity = getIdentity()
        val response = request("nip44_encrypt", plaintext, signerIdentity.packageName, recipientPubkey, signerIdentity.pubkey)
        check(response.resultCode == Activity.RESULT_OK) { "Amber failed to encrypt" }
        check(response.data?.getBooleanExtra("rejected", false) != true) { "Amber encryption request rejected" }
        return response.data?.getStringExtra("result") ?: error("Amber did not return ciphertext")
    }

    override suspend fun nip44Decrypt(ciphertext: String, senderPubkey: String): String {
        val signerIdentity = getIdentity()
        val response = request("nip44_decrypt", ciphertext, signerIdentity.packageName, senderPubkey, signerIdentity.pubkey)
        check(response.resultCode == Activity.RESULT_OK) { "Amber failed to decrypt" }
        check(response.data?.getBooleanExtra("rejected", false) != true) { "Amber decryption request rejected" }
        return response.data?.getStringExtra("result") ?: error("Amber did not return plaintext")
    }

    private suspend fun request(
        type: String,
        payload: String,
        packageName: String?,
        otherPubkey: String? = null,
        currentUser: String? = null
    ): ActivityResult = suspendCancellableCoroutine { continuation ->
        val id = UUID.randomUUID().toString()
        pending[id] = continuation
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:${Uri.encode(payload)}")).apply {
            packageName?.takeIf(String::isNotBlank)?.let { `package` = it }
            putExtra("type", type)
            putExtra("id", id)
            otherPubkey?.let { putExtra("pubkey", it) }
            currentUser?.let { putExtra("current_user", it) }
        }
        continuation.invokeOnCancellation { pending.remove(id) }
        runCatching { launcher.launch(intent) }
            .onFailure { failure ->
                pending.remove(id)
                continuation.resumeWithException(
                    IllegalStateException("Amber non è disponibile o non può gestire la richiesta", failure)
                )
            }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        pending.values.forEach { it.resumeWithException(IllegalStateException("Amber signer owner destroyed")) }
        pending.clear()
        launcher.unregister()
        activity.lifecycle.removeObserver(this)
    }

    private companion object {
        const val KEY_PUBKEY = "pubkey"
        const val KEY_PACKAGE = "package"

        fun isValidPubkey(value: String): Boolean =
            value.length == 64 && value.all { it in "0123456789abcdef" }
    }
}
