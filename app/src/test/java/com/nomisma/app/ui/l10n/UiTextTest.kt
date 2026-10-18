package com.nomisma.app.ui.l10n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiTextTest {
    private val languages = listOf(
        "en", "de", "fr", "es", "pt", "nl", "pl",
        "sv", "da", "fi", "cs", "sk", "sl", "hr",
        "bg", "el", "hu", "ro", "et", "lv", "lt"
    )

    @Test
    fun italianAndUnknownLanguagesStayOnTheSource() {
        val source = "Sicuro di voler uscire?"
        assertEquals(source, UiText.translate(source, "it"))
        assertEquals(source, UiText.translate(source, "mt"))
        assertEquals(source, UiText.translate(source, "ga"))
    }

    @Test
    fun everyEuLanguageTranslatesTheExitPromptAndKeepsBrandTokens() {
        val exit = "Sicuro di voler uscire?"
        val donation = "Dona a Nomisma tramite donazioni Lightning"
        languages.forEach { language ->
            assertNotEquals(language, exit, UiText.translate(exit, language))
            val translatedDonation = UiText.translate(donation, language)
            assertTrue(language, translatedDonation.contains("Nomisma"))
            assertTrue(language, translatedDonation.contains("Lightning"))
        }
    }
}
