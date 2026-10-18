package com.nomisma.app.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Nip19Test {
    // Reference values computed with an independent Python bech32 implementation.
    private val fiatjafHex = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val fiatjafNpub = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"
    private val otherHex = "97bb9f09d388ff5bebeedad19bba995d41c962d9247c666c6c90f897e8ba6346"
    private val otherNpub = "npub1j7ae7zwn3rl4h6lwmtgehw5et4qujckey37xvmrvjruf0696vdrq55x9qc"

    @Test
    fun encodesKnownKeys() {
        assertEquals(fiatjafNpub, Nip19.hexToNpub(fiatjafHex))
        assertEquals(otherNpub, Nip19.hexToNpub(otherHex))
    }

    @Test
    fun decodesKnownKeysWithPrefixCaseAndWhitespace() {
        assertEquals(fiatjafHex, Nip19.npubToHex(fiatjafNpub))
        assertEquals(otherHex, Nip19.npubToHex("  nostr:${otherNpub.uppercase()}\n"))
    }

    @Test
    fun rejectsCorruptedOrForeignBech32() {
        val flipped = fiatjafNpub.dropLast(1) + if (fiatjafNpub.last() == 'q') 'p' else 'q'
        assertNull(Nip19.npubToHex(flipped))
        assertNull(Nip19.npubToHex(fiatjafNpub.dropLast(1)))
        assertNull(Nip19.npubToHex("nsec1" + fiatjafNpub.drop(5)))
        assertNull(Nip19.npubToHex("npub1" + "b".repeat(58)))
        assertNull(Nip19.npubToHex(fiatjafHex))
        assertNull(Nip19.npubToHex(""))
    }
}
