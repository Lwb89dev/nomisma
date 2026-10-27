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

    @Test
    fun inviteAndRecipientSearchLabelsAreTranslatedEverywhere() {
        val keys = listOf(
            "Nessun codice invito Nomisma trovato nel testo",
            "Invito Nomisma per",
            "Per accettare: copia questo messaggio e apri Nomisma, il codice viene riconosciuto da solo.",
            "Destinatario",
            "Profilo sconosciuto",
            "Cambia",
            "Cerca il destinatario",
            "Nome profilo, nome@dominio o npub",
            "La ricerca interroga relay pubblici. Puoi anche incollare la npub.",
            "Ricerca in corso…",
            "Ricerca non riuscita. Controlla la connessione e riprova.",
            "Questa è la tua chiave: scegli l'altro partecipante.",
            "Nessun profilo trovato. Prova con nome@dominio oppure incolla la npub.",
            "Più profili possono avere lo stesso nome: scegli quello verificato e confronta la npub con l'altra persona.",
            "verificato",
            "non verificato",
            "Relay pubblico che consegna i messaggi cifrati al destinatario.",
            "Relay pubblico generalista, compatibile con i messaggi cifrati.",
            "Saldi verificati con l'altro dispositivo",
            "Verifica dei saldi in corso",
            "Riallineamento: operazioni mancanti reinviate",
            "I saldi non coincidono con l'altro dispositivo",
            "La verifica dei saldi parte quando entrambi i dispositivi sono connessi",
            "Impronta del ledger"
        )
        // Spanish spells "recipient" exactly like Italian.
        val sameAsItalian = setOf("es" to "Destinatario")
        languages.forEach { language ->
            keys.forEach { key ->
                val translated = UiText.translate(key, language)
                assertTrue("$language $key", translated.isNotBlank())
                if (language to key !in sameAsItalian) assertNotEquals("$language $key", key, translated)
            }
        }
    }
}
