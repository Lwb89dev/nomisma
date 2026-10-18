package com.nomisma.app.nostr

/** NIP-19 `npub` encoding (bech32, 32-byte public keys only). */
object Nip19 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val HRP = "npub"
    private val generator = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    /** Accepts an npub (optionally prefixed by `nostr:`) and returns the lowercase hex key, or null. */
    fun npubToHex(raw: String): String? {
        val text = raw.trim().removePrefix("nostr:").lowercase()
        if (!text.startsWith("${HRP}1") || text.length != 63) return null
        val values = text.drop(HRP.length + 1).map { CHARSET.indexOf(it).also { v -> if (v < 0) return null } }
        if (polymod(expandHrp() + values) != 1) return null
        val bytes = convertBits(values.dropLast(6), from = 5, to = 8, pad = false) ?: return null
        return bytes.takeIf { it.size == 32 }?.let { NostrHex.encode(it.map(Int::toByte).toByteArray()) }
    }

    fun hexToNpub(hex: String): String {
        require(hex.length == 64) { "A public key has 64 hex characters" }
        val data = convertBits(NostrHex.decode(hex).map { it.toInt() and 0xff }, from = 8, to = 5, pad = true)!!
        val checksumInput = expandHrp() + data + List(6) { 0 }
        val mod = polymod(checksumInput) xor 1
        val checksum = List(6) { (mod shr (5 * (5 - it))) and 31 }
        return HRP + "1" + (data + checksum).joinToString("") { CHARSET[it].toString() }
    }

    private fun expandHrp(): List<Int> = HRP.map { it.code shr 5 } + 0 + HRP.map { it.code and 31 }

    private fun polymod(values: List<Int>): Int {
        var checksum = 1
        for (value in values) {
            val top = checksum ushr 25
            checksum = ((checksum and 0x1ffffff) shl 5) xor value
            for (i in 0 until 5) if ((top shr i) and 1 == 1) checksum = checksum xor generator[i]
        }
        return checksum
    }

    private fun convertBits(data: List<Int>, from: Int, to: Int, pad: Boolean): List<Int>? {
        var accumulator = 0
        var bits = 0
        val maxValue = (1 shl to) - 1
        val result = ArrayList<Int>()
        for (value in data) {
            if (value shr from != 0) return null
            accumulator = (accumulator shl from) or value
            bits += from
            while (bits >= to) {
                bits -= to
                result += (accumulator shr bits) and maxValue
            }
            accumulator = accumulator and ((1 shl bits) - 1)
        }
        if (pad) {
            if (bits > 0) result += (accumulator shl (to - bits)) and maxValue
        } else if (bits >= from || ((accumulator shl (to - bits)) and maxValue) != 0) {
            return null
        }
        return result
    }
}
