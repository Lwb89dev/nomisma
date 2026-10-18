package com.nomisma.app.nostr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** NIP-05 `name@domain` identifiers. A bare `domain` means `_@domain`. */
object Nip05 {
    private val localPart = Regex("[a-z0-9._-]{1,64}")
    private val domainName = Regex("(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,24}")

    data class Identifier(val local: String, val domain: String) {
        override fun toString() = "$local@$domain"
    }

    fun parse(raw: String): Identifier? {
        val text = raw.trim().lowercase()
        val local = if ('@' in text) text.substringBefore('@') else "_"
        val domain = if ('@' in text) text.substringAfter('@') else text
        if (!localPart.matches(local) || !domainName.matches(domain)) return null
        return Identifier(local, domain)
    }

    fun extractPubkey(json: String, local: String): String? = runCatching {
        JSONObject(json).getJSONObject("names").optString(local).lowercase()
    }.getOrNull()?.takeIf { it.length == 64 && it.all { c -> c in "0123456789abcdef" } }
}

/**
 * Resolves and checks NIP-05 identifiers. Redirects are not followed (NIP-05 forbids it)
 * and the response is capped, because the host comes from an untrusted profile.
 */
class Nip05Verifier(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()
) {
    private val cache = ConcurrentHashMap<String, String?>()

    suspend fun resolve(identifier: String): String? {
        val parsed = Nip05.parse(identifier) ?: return null
        val key = parsed.toString()
        if (cache.containsKey(key)) return cache[key]
        return fetch(parsed).also { if (it != null) cache[key] = it }
    }

    suspend fun verify(identifier: String, pubkey: String): Boolean =
        resolve(identifier) == pubkey.lowercase()

    private suspend fun fetch(identifier: Nip05.Identifier): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host(identifier.domain)
                .addPathSegments(".well-known/nostr.json")
                .addQueryParameter("name", identifier.local)
                .build()
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val source = response.body?.source() ?: return@use null
                source.request(MAX_BODY_BYTES)
                val body = source.buffer.readUtf8(minOf(source.buffer.size, MAX_BODY_BYTES))
                Nip05.extractPubkey(body, identifier.local)
            }
        }.getOrNull()
    }

    private companion object {
        const val MAX_BODY_BYTES = 65_536L
    }
}
