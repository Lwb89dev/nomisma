package com.nomisma.app.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrProfilesTest {
    private fun key(seed: Char) = seed.toString().repeat(64)

    private fun profile(
        seed: Char,
        name: String? = null,
        display: String? = null,
        nip05: String? = null,
        createdAt: Long = 1L,
        verified: Boolean = false
    ) = NostrProfile(key(seed), name, display, nip05, createdAt, verified)

    private fun event(content: String, kind: Int = 0) =
        NostrEvent("0".repeat(64), key('a'), 7L, kind, emptyList(), content, "0".repeat(128))

    @Test
    fun parserReadsNamesAndNip05AndStripsInvisibleCharacters() {
        val parsed = NostrProfileParser.parse(
            event("""{"name":"lw‮b","display_name":"  L​WB\n ","nip05":"LWB@Cosanostr.com","picture":"x"}""")
        )!!
        assertEquals("lwb", parsed.name)
        assertEquals("LWB", parsed.displayName)
        assertEquals("lwb@cosanostr.com", parsed.nip05)
        assertEquals("LWB", parsed.label)
        assertEquals(key('a'), parsed.pubkey)
    }

    @Test
    fun parserRejectsOtherKindsGarbageAndEmptyProfiles() {
        assertNull(NostrProfileParser.parse(event("""{"name":"x"}""", kind = 1)))
        assertNull(NostrProfileParser.parse(event("not json")))
        assertNull(NostrProfileParser.parse(event("""{"about":"only a bio"}""")))
        assertNull(NostrProfileParser.parse(event("""{"name":123,"nip05":"not an id"}""")))
        assertNotNull(NostrProfileParser.parse(event("""{"name":"ok"}""")))
    }

    @Test
    fun parserCapsAbsurdlyLongNames() {
        val parsed = NostrProfileParser.parse(event("""{"name":"${"n".repeat(500)}"}"""))!!
        assertEquals(48, parsed.name!!.length)
    }

    @Test
    fun rankingKeepsOnlyRealNameMatchesAndOrdersByExactness() {
        val ranked = ProfileRanking.rank(
            "LWB",
            listOf(
                profile('1', name = "andre-lwb"),
                profile('2', name = "LWB", display = "LWB"),
                profile('3', name = "lwbeoin"),
                profile('4', name = "Galactus"),
                profile('5', name = "someone", nip05 = "someone@example.com")
            )
        )
        assertEquals(listOf(key('2'), key('3'), key('1')), ranked.map { it.pubkey })
    }

    @Test
    fun rankingPutsVerifiedIdentitiesBeforeLookalikes() {
        val ranked = ProfileRanking.rank(
            "lwb",
            listOf(
                profile('1', name = "LWB", createdAt = 99L),
                profile('2', name = "LWB", nip05 = "lwb@cosanostr.com", createdAt = 1L, verified = true),
                profile('3', name = "LWB", nip05 = "lwb@fake.example", createdAt = 50L)
            )
        )
        assertEquals(listOf(key('2'), key('3'), key('1')), ranked.map { it.pubkey })
    }

    @Test
    fun rankingKeepsNewestProfilePerKeyAndHonoursExclusions() {
        val ranked = ProfileRanking.rank(
            "ere",
            listOf(
                profile('1', name = "Ere old", createdAt = 1L),
                profile('1', name = "Ere new", createdAt = 9L),
                profile('2', name = "Ere mine", createdAt = 5L)
            ),
            exclude = setOf(key('2'))
        )
        assertEquals(listOf("Ere new"), ranked.map { it.name })
    }

    @Test
    fun rankingMatchesAcrossPunctuationAndMultipleWords() {
        assertEquals(1, ProfileRanking.rank("ere tk", listOf(profile('1', name = "Ere.TK"))).size)
        assertEquals(1, ProfileRanking.rank("luigi antona", listOf(profile('1', display = "Luigi_Antona"))).size)
        assertTrue(ProfileRanking.rank("  ", listOf(profile('1', name = "x"))).isEmpty())
    }

    @Test
    fun nip05ParsingAcceptsIdentifiersAndRejectsUnsafeHosts() {
        assertEquals("lwb@cosanostr.com", Nip05.parse(" LWB@CosaNostr.com ")?.toString())
        assertEquals("_@fiatjaf.com", Nip05.parse("fiatjaf.com")?.toString())
        assertNull(Nip05.parse("a@localhost"))
        assertNull(Nip05.parse("a@192.168.1.10"))
        assertNull(Nip05.parse("a@evil.com/../x"))
        assertNull(Nip05.parse("a b@example.com"))
    }

    @Test
    fun nip05ResponseMustMapTheNameToAHexKey() {
        val json = """{"names":{"lwb":"${key('b').uppercase()}","bad":"zz"}}"""
        assertEquals(key('b'), Nip05.extractPubkey(json, "lwb"))
        assertNull(Nip05.extractPubkey(json, "bad"))
        assertNull(Nip05.extractPubkey(json, "missing"))
        assertNull(Nip05.extractPubkey("[]", "lwb"))
    }
}
