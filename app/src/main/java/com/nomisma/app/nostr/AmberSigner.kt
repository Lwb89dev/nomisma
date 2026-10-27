package com.nomisma.app.nostr

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.nomisma.app.security.securePreferences
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * NIP-55 signer backed by Amber. Requests first go through Amber's content provider, which
 * answers in the background once the user has allowed an action; only when it declines does
 * Nomisma open Amber's screen. Screen requests run one at a time, so a result that comes back
 * without its id (Amber closed or cancelled) still reaches the request that is waiting for it.
 */
class AmberSigner(
    private val activity: ComponentActivity
) : Signer, DefaultLifecycleObserver {
    private val preferences = activity.securePreferences("nomisma_signer")
    private val screenRequests = Mutex()
    private var inFlight: CancellableContinuation<ActivityResult>? = null
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
            val waiting = inFlight
            inFlight = null
            waiting?.resume(result)
        }
    )

    init {
        activity.lifecycle.addObserver(this)
    }

    /** Last identity Amber returned, without opening Amber. */
    val knownPubkey: String? get() = identity?.pubkey

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
        val payload = event.copy(pubkey = signerIdentity.pubkey).toSignerJson()
        val background = queryProvider(
            signerIdentity,
            "SIGN_EVENT",
            arrayOf(payload, "", signerIdentity.pubkey),
            listOf("event", "result")
        )
        if (background != null) {
            return signedEvent(event, signerIdentity, background["event"], background["result"])
        }

        val response = request(
            type = "sign_event",
            payload = payload,
            packageName = signerIdentity.packageName,
            currentUser = signerIdentity.pubkey
        )
        check(response.resultCode == Activity.RESULT_OK) { "Amber failed to sign event" }
        check(response.data?.getBooleanExtra("rejected", false) != true) { "Amber signing request rejected" }
        return signedEvent(
            event,
            signerIdentity,
            response.data?.getStringExtra("event"),
            response.data?.getStringExtra("result")
        )
    }

    private fun signedEvent(
        event: UnsignedNostrEvent,
        signerIdentity: SignerIdentity,
        returnedEvent: String?,
        result: String?
    ): NostrEvent {
        val returnedEventJson = returnedEvent ?: result?.takeIf { it.trimStart().startsWith("{") }
        if (returnedEventJson != null) {
            return SignerResponseValidator.requireMatchesRequest(
                requested = event,
                expectedPubkey = signerIdentity.pubkey,
                returned = NostrEvent.fromJson(returnedEventJson)
            )
        }

        val signature = result ?: error("Amber did not return a signature or event")
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
        queryProvider(signerIdentity, "NIP44_ENCRYPT", arrayOf(plaintext, recipientPubkey, signerIdentity.pubkey), listOf("result"))
            ?.get("result")
            ?.let { return it }
        val response = request("nip44_encrypt", plaintext, signerIdentity.packageName, recipientPubkey, signerIdentity.pubkey)
        check(response.resultCode == Activity.RESULT_OK) { "Amber failed to encrypt" }
        check(response.data?.getBooleanExtra("rejected", false) != true) { "Amber encryption request rejected" }
        return response.data?.getStringExtra("result") ?: error("Amber did not return ciphertext")
    }

    override suspend fun nip44Decrypt(ciphertext: String, senderPubkey: String): String {
        val signerIdentity = getIdentity()
        queryProvider(signerIdentity, "NIP44_DECRYPT", arrayOf(ciphertext, senderPubkey, signerIdentity.pubkey), listOf("result"))
            ?.get("result")
            ?.let { return it }
        val response = request("nip44_decrypt", ciphertext, signerIdentity.packageName, senderPubkey, signerIdentity.pubkey)
        check(response.resultCode == Activity.RESULT_OK) { "Amber failed to decrypt" }
        check(response.data?.getBooleanExtra("rejected", false) != true) { "Amber decryption request rejected" }
        return response.data?.getStringExtra("result") ?: error("Amber did not return plaintext")
    }

    /**
     * NIP-55 background path. Returns null whenever Amber does not answer silently (no remembered
     * permission, rejected, old Amber), so the caller falls back to Amber's screen.
     */
    private suspend fun queryProvider(
        signerIdentity: SignerIdentity,
        action: String,
        arguments: Array<String>,
        columns: List<String>
    ): Map<String, String>? {
        val packageName = signerIdentity.packageName.takeIf(String::isNotBlank) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                activity.contentResolver
                    .query(Uri.parse("content://$packageName.$action"), arguments, null, null, null)
                    ?.use { cursor ->
                        if (!cursor.moveToFirst() || cursor.getColumnIndex("rejected") >= 0) return@use null
                        columns.mapNotNull { column ->
                            cursor.getColumnIndex(column).takeIf { it >= 0 }
                                ?.let(cursor::getString)
                                ?.let { column to it }
                        }.toMap().takeIf { it.isNotEmpty() }
                    }
            }.getOrNull()
        }
    }

    private suspend fun request(
        type: String,
        payload: String,
        packageName: String?,
        otherPubkey: String? = null,
        currentUser: String? = null
    ): ActivityResult = screenRequests.withLock {
        suspendCancellableCoroutine { continuation ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:${Uri.encode(payload)}")).apply {
                packageName?.takeIf(String::isNotBlank)?.let { `package` = it }
                putExtra("type", type)
                putExtra("id", UUID.randomUUID().toString())
                otherPubkey?.let { putExtra("pubkey", it) }
                currentUser?.let { putExtra("current_user", it) }
            }
            inFlight = continuation
            continuation.invokeOnCancellation { if (inFlight === continuation) inFlight = null }
            runCatching { launcher.launch(intent) }
                .onFailure { failure ->
                    inFlight = null
                    continuation.resumeWithException(
                        IllegalStateException("Amber non è disponibile o non può gestire la richiesta", failure)
                    )
                }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        inFlight?.resumeWithException(IllegalStateException("Amber signer owner destroyed"))
        inFlight = null
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
