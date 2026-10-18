package com.nomisma.app.ui.l10n

internal object UiText {
    private val tables = mapOf(
        "en" to uiTextEn,
        "de" to uiTextDe,
        "fr" to uiTextFr,
        "es" to uiTextEs,
        "pt" to uiTextPt,
        "nl" to uiTextNl,
        "pl" to uiTextPl,
        "sv" to uiTextSv,
        "da" to uiTextDa,
        "fi" to uiTextFi,
        "cs" to uiTextCs,
        "sk" to uiTextSk,
        "sl" to uiTextSl,
        "hr" to uiTextHr,
        "bg" to uiTextBg,
        "el" to uiTextEl,
        "hu" to uiTextHu,
        "ro" to uiTextRo,
        "et" to uiTextEt,
        "lv" to uiTextLv,
        "lt" to uiTextLt
    )

    fun translate(source: String, language: String): String {
        if (language == "it") return source
        val table = tables[language] ?: return source
        return table[source] ?: shortLabels[language]?.get(source) ?: source
    }

    private val shortLabels = mapOf(
        "en" to mapOf("pronto" to "ready", "da completare" to "to complete", "Colore" to "Color", "la tua identità" to "your identity", "gestita dall'altro partecipante" to "managed by the other participant", "Visualizzazione" to "View", "ciclo al" to "cycle at", "per cento" to "percent", "soglia superata" to "threshold exceeded"),
        "de" to mapOf("pronto" to "bereit", "da completare" to "offen", "Colore" to "Farbe", "la tua identità" to "deine Identität", "gestita dall'altro partecipante" to "vom anderen Teilnehmer verwaltet", "Visualizzazione" to "Ansicht", "ciclo al" to "Zyklus bei", "per cento" to "Prozent", "soglia superata" to "Schwelle überschritten"),
        "fr" to mapOf("pronto" to "prêt", "da completare" to "à compléter", "Colore" to "Couleur", "la tua identità" to "ton identité", "gestita dall'altro partecipante" to "gérée par l'autre participant", "Visualizzazione" to "Affichage", "ciclo al" to "cycle à", "per cento" to "pour cent", "soglia superata" to "seuil dépassé"),
        "es" to mapOf("pronto" to "listo", "da completare" to "pendiente", "Colore" to "Color", "la tua identità" to "tu identidad", "gestita dall'altro partecipante" to "gestionada por el otro participante", "Visualizzazione" to "Vista", "ciclo al" to "ciclo al", "per cento" to "por ciento", "soglia superata" to "umbral superado"),
        "pt" to mapOf("pronto" to "pronto", "da completare" to "por concluir", "Colore" to "Cor", "la tua identità" to "a tua identidade", "gestita dall'altro partecipante" to "gerida pelo outro participante", "Visualizzazione" to "Vista", "ciclo al" to "ciclo a", "per cento" to "por cento", "soglia superata" to "limiar ultrapassado"),
        "nl" to mapOf("pronto" to "gereed", "da completare" to "nog te voltooien", "Colore" to "Kleur", "la tua identità" to "jouw identiteit", "gestita dall'altro partecipante" to "beheerd door de andere deelnemer", "Visualizzazione" to "Weergave", "ciclo al" to "cyclus op", "per cento" to "procent", "soglia superata" to "drempel overschreden"),
        "pl" to mapOf("pronto" to "gotowe", "da completare" to "do uzupełnienia", "Colore" to "Kolor", "la tua identità" to "twoja tożsamość", "gestita dall'altro partecipante" to "zarządzana przez drugiego uczestnika", "Visualizzazione" to "Widok", "ciclo al" to "cykl na", "per cento" to "procent", "soglia superata" to "próg przekroczony"),
        "sv" to mapOf("pronto" to "klart", "da completare" to "att slutföra", "Colore" to "Färg", "la tua identità" to "din identitet", "gestita dall'altro partecipante" to "hanteras av den andra deltagaren", "Visualizzazione" to "Visning", "ciclo al" to "cykel på", "per cento" to "procent", "soglia superata" to "tröskel överskriden"),
        "da" to mapOf("pronto" to "klar", "da completare" to "skal udfyldes", "Colore" to "Farve", "la tua identità" to "din identitet", "gestita dall'altro partecipante" to "styres af den anden deltager", "Visualizzazione" to "Visning", "ciclo al" to "cyklus på", "per cento" to "procent", "soglia superata" to "tærskel overskredet"),
        "fi" to mapOf("pronto" to "valmis", "da completare" to "kesken", "Colore" to "Väri", "la tua identità" to "sinun identiteettisi", "gestita dall'altro partecipante" to "toisen osallistujan hallinnassa", "Visualizzazione" to "Näkymä", "ciclo al" to "jakso", "per cento" to "prosenttia", "soglia superata" to "kynnys ylittynyt"),
        "cs" to mapOf("pronto" to "hotovo", "da completare" to "k dokončení", "Colore" to "Barva", "la tua identità" to "tvoje identita", "gestita dall'altro partecipante" to "spravuje druhý účastník", "Visualizzazione" to "Zobrazení", "ciclo al" to "cyklus na", "per cento" to "procent", "soglia superata" to "práh překročen"),
        "sk" to mapOf("pronto" to "hotovo", "da completare" to "na dokončenie", "Colore" to "Farba", "la tua identità" to "tvoja identita", "gestita dall'altro partecipante" to "spravuje druhý účastník", "Visualizzazione" to "Zobrazenie", "ciclo al" to "cyklus na", "per cento" to "percent", "soglia superata" to "prah prekročený"),
        "sl" to mapOf("pronto" to "pripravljeno", "da completare" to "za dokončati", "Colore" to "Barva", "la tua identità" to "tvoja identiteta", "gestita dall'altro partecipante" to "upravlja drugi udeleženec", "Visualizzazione" to "Prikaz", "ciclo al" to "cikel pri", "per cento" to "odstotkih", "soglia superata" to "prag presežen"),
        "hr" to mapOf("pronto" to "spremno", "da completare" to "za dovršiti", "Colore" to "Boja", "la tua identità" to "tvoj identitet", "gestita dall'altro partecipante" to "upravlja drugi sudionik", "Visualizzazione" to "Prikaz", "ciclo al" to "ciklus na", "per cento" to "posto", "soglia superata" to "prag prekoračen"),
        "bg" to mapOf("pronto" to "готово", "da completare" to "за довършване", "Colore" to "Цвят", "la tua identità" to "твоята самоличност", "gestita dall'altro partecipante" to "управлява се от другия участник", "Visualizzazione" to "Изглед", "ciclo al" to "цикъл на", "per cento" to "процента", "soglia superata" to "прагът е надхвърлен"),
        "el" to mapOf("pronto" to "έτοιμο", "da completare" to "προς ολοκλήρωση", "Colore" to "Χρώμα", "la tua identità" to "η ταυτότητά σου", "gestita dall'altro partecipante" to "τη διαχειρίζεται ο άλλος συμμετέχων", "Visualizzazione" to "Προβολή", "ciclo al" to "κύκλος στο", "per cento" to "τοις εκατό", "soglia superata" to "το όριο ξεπεράστηκε"),
        "hu" to mapOf("pronto" to "kész", "da completare" to "még hiányzik", "Colore" to "Szín", "la tua identità" to "a te identitásod", "gestita dall'altro partecipante" to "a másik résztvevő kezeli", "Visualizzazione" to "Nézet", "ciclo al" to "ciklus", "per cento" to "százalékon", "soglia superata" to "küszöb túllépve"),
        "ro" to mapOf("pronto" to "gata", "da completare" to "de completat", "Colore" to "Culoare", "la tua identità" to "identitatea ta", "gestita dall'altro partecipante" to "gestionată de celălalt participant", "Visualizzazione" to "Afișare", "ciclo al" to "ciclu la", "per cento" to "la sută", "soglia superata" to "prag depășit"),
        "et" to mapOf("pronto" to "valmis", "da completare" to "lõpetamata", "Colore" to "Värv", "la tua identità" to "sinu identiteet", "gestita dall'altro partecipante" to "teise osaleja hallata", "Visualizzazione" to "Vaade", "ciclo al" to "tsükkel", "per cento" to "protsenti", "soglia superata" to "lävi ületatud"),
        "lv" to mapOf("pronto" to "gatavs", "da completare" to "jāpabeidz", "Colore" to "Krāsa", "la tua identità" to "tava identitāte", "gestita dall'altro partecipante" to "to pārvalda otrs dalībnieks", "Visualizzazione" to "Skats", "ciclo al" to "cikls", "per cento" to "procenti", "soglia superata" to "slieksnis pārsniegts"),
        "lt" to mapOf("pronto" to "paruošta", "da completare" to "reikia užbaigti", "Colore" to "Spalva", "la tua identità" to "tavo tapatybė", "gestita dall'altro partecipante" to "valdo kitas dalyvis", "Visualizzazione" to "Rodinys", "ciclo al" to "ciklas ties", "per cento" to "procentų", "soglia superata" to "riba viršyta")
    )
}
