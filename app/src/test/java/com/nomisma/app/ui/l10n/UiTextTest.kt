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

    @Test
    fun splitToggleAndTagChartLabelsAreTranslated() {
        val keys = listOf(
            "Da dividere 50/50",
            "L'importo è il totale. A credito va la metà.",
            "L'importo è già la quota. A credito va per intero.",
            "Quota già suddivisa",
            "Andamento per tag",
            "Ogni linea è un tag nel periodo selezionato",
            "I tag con più spesa nel periodo"
        )
        languages.forEach { language ->
            keys.forEach { key ->
                assertNotEquals("$language $key", key, UiText.translate(key, language))
            }
        }
        assertEquals("Da dividere 50/50", UiText.translate("Da dividere 50/50", "it"))
    }
}
