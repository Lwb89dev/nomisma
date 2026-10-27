package com.nomisma.app.nostr

import fr.acinq.secp256k1.Secp256k1
import org.bouncycastle.crypto.engines.ChaCha7539Engine
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Minimal NIP-44 v2 implementation used only for locally-created gift wraps. */
object Nip44V2 {
    private const val VERSION: Byte = 2
    private const val MAX_PLAINTEXT_BYTES = 1_048_576
    private val secureRandom = SecureRandom()

    fun encrypt(plaintext: String, privateKey: ByteArray, recipientPubkeyHex: String): String {
        require(privateKey.size == 32) { "Private key must be 32 bytes" }
        require(Secp256k1.secKeyVerify(privateKey)) { "Invalid private key" }
        val conversationKey = conversationKey(privateKey, recipientPubkeyHex)
        return try {
            encryptWithConversationKey(plaintext, conversationKey, randomBytes(32))
        } finally {
            conversationKey.fill(0)
        }
    }

    internal fun conversationKey(privateKey: ByteArray, recipientPubkeyHex: String): ByteArray {
        val recipientPubkey = Hex.decode(recipientPubkeyHex)
        require(recipientPubkey.size == 32) { "Recipient pubkey must be 32 bytes" }
        // NIP-44 hashes the raw x coordinate of the shared point. libsecp256k1's ecdh() returns
        // SHA-256 of the point instead, which the recipient's signer would never reproduce.
        val sharedPoint = Secp256k1.pubKeyTweakMul(byteArrayOf(2) + recipientPubkey, privateKey)
        val sharedX = sharedPoint.copyOfRange(1, 33)
        sharedPoint.fill(0)
        return try {
            hkdfExtract(sharedX, NIP44_SALT)
        } finally {
            sharedX.fill(0)
        }
    }

    internal fun encryptWithConversationKey(
        plaintext: String,
        conversationKey: ByteArray,
        nonce: ByteArray
    ): String {
        val padded = pad(plaintext)
        require(conversationKey.size == 32) { "Conversation key must be 32 bytes" }
        require(nonce.size == 32) { "Nonce must be 32 bytes" }
        val keys = messageKeys(conversationKey, nonce)
        try {
            val ciphertext = chacha20(keys.chachaKey, keys.chachaNonce, padded)
            val mac = hmacSha256(keys.hmacKey, nonce + ciphertext)
            return Base64.getEncoder().encodeToString(byteArrayOf(VERSION) + nonce + ciphertext + mac)
        } finally {
            padded.fill(0)
            keys.wipe()
        }
    }

    internal fun decryptWithConversationKey(payload: String, conversationKey: ByteArray): String {
        val decoded = Base64.getDecoder().decode(payload)
        require(decoded.size >= 99) { "NIP-44 payload is too short" }
        require(decoded[0] == VERSION) { "Unsupported NIP-44 version" }
        val nonce = decoded.copyOfRange(1, 33)
        val ciphertext = decoded.copyOfRange(33, decoded.size - 32)
        val mac = decoded.copyOfRange(decoded.size - 32, decoded.size)
        val keys = messageKeys(conversationKey, nonce)
        try {
            val expectedMac = hmacSha256(keys.hmacKey, nonce + ciphertext)
            require(MessageDigest.isEqual(mac, expectedMac)) { "Invalid NIP-44 MAC" }
            val plainBytes = chacha20(keys.chachaKey, keys.chachaNonce, ciphertext)
            try {
                return unpad(plainBytes)
            } finally {
                plainBytes.fill(0)
            }
        } finally {
            keys.wipe()
        }
    }

    private fun pad(plaintext: String): ByteArray {
        val content = plaintext.toByteArray(Charsets.UTF_8)
        require(content.isNotEmpty()) { "Plaintext cannot be empty" }
        require(content.size <= MAX_PLAINTEXT_BYTES) { "Plaintext is too large" }
        val paddedLength = paddedLength(content.size)
        val prefix = if (content.size < 65_536) {
            ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(content.size.toShort()).array()
        } else {
            ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN).putShort(0).putInt(content.size).array()
        }
        return prefix + content + ByteArray(paddedLength - content.size)
    }

    private fun unpad(padded: ByteArray): String {
        require(padded.size >= 34) { "Invalid NIP-44 padding" }
        val firstTwo = ByteBuffer.wrap(padded, 0, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xffff
        val prefixLength: Int
        val contentLength: Long
        if (firstTwo == 0) {
            require(padded.size >= 6) { "Invalid extended NIP-44 padding" }
            contentLength = ByteBuffer.wrap(padded, 2, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xffff_ffffL
            require(contentLength >= 65_536L) { "Invalid extended NIP-44 length" }
            prefixLength = 6
        } else {
            contentLength = firstTwo.toLong()
            prefixLength = 2
        }
        require(contentLength in 1..MAX_PLAINTEXT_BYTES.toLong()) { "Invalid NIP-44 plaintext length" }
        require(padded.size == prefixLength + paddedLength(contentLength.toInt())) { "Invalid NIP-44 padding size" }
        val end = prefixLength + contentLength.toInt()
        return padded.copyOfRange(prefixLength, end).toString(Charsets.UTF_8)
    }

    private fun paddedLength(length: Int): Int {
        if (length <= 32) return 32
        val nextPower = 1L shl (64 - java.lang.Long.numberOfLeadingZeros((length - 1).toLong()))
        val chunk = if (nextPower <= 256L) 32L else nextPower / 8L
        return (chunk * (((length - 1) / chunk) + 1L)).toInt()
    }

    private fun messageKeys(conversationKey: ByteArray, nonce: ByteArray): MessageKeys {
        val expanded = hkdfExpand(conversationKey, nonce, 76)
        try {
            return MessageKeys(
                chachaKey = expanded.copyOfRange(0, 32),
                chachaNonce = expanded.copyOfRange(32, 44),
                hmacKey = expanded.copyOfRange(44, 76)
            )
        } finally {
            expanded.fill(0)
        }
    }

    private fun chacha20(key: ByteArray, nonce: ByteArray, input: ByteArray): ByteArray {
        val engine = ChaCha7539Engine()
        engine.init(true, ParametersWithIV(KeyParameter(key), nonce))
        return ByteArray(input.size).also { output ->
            engine.processBytes(input, 0, input.size, output, 0)
        }
    }

    private fun hkdfExtract(inputKeyMaterial: ByteArray, salt: ByteArray): ByteArray =
        hmacSha256(salt, inputKeyMaterial)

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..8_160) { "Invalid HKDF output length" }
        val result = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            val next = mac.doFinal()
            previous.fill(0)
            previous = next
            val copyLength = minOf(previous.size, length - offset)
            previous.copyInto(result, offset, 0, copyLength)
            offset += copyLength
            counter += 1
        }
        previous.fill(0)
        return result
    }

    private fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(message)
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

    private data class MessageKeys(
        val chachaKey: ByteArray,
        val chachaNonce: ByteArray,
        val hmacKey: ByteArray
    ) {
        fun wipe() {
            chachaKey.fill(0)
            chachaNonce.fill(0)
            hmacKey.fill(0)
        }
    }

    private object Hex {
        fun decode(value: String): ByteArray {
            require(value.length % 2 == 0) { "Invalid hex length" }
            return ByteArray(value.length / 2) { index ->
                val high = value[index * 2].digitToIntOrNull(16) ?: error("Invalid hex")
                val low = value[index * 2 + 1].digitToIntOrNull(16) ?: error("Invalid hex")
                ((high shl 4) or low).toByte()
            }
        }
    }

    private val NIP44_SALT = "nip44-v2".toByteArray(Charsets.UTF_8)
}
