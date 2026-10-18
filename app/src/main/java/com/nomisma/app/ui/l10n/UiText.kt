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
        return table[source]
            ?: shortLabels[language]?.get(source)
            ?: extraLabels[language]?.get(source)
            ?: source
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

    private val extraLabels = mapOf(
        "en" to mapOf(
            "Da dividere 50/50" to "Split 50/50",
            "L'importo è il totale. A credito va la metà." to "The amount is the total. Half of it becomes credit.",
            "L'importo è già la quota. A credito va per intero." to "The amount is already the share. The full amount becomes credit.",
            "Quota già suddivisa" to "Already divided share",
            "Andamento per tag" to "Trend by tag",
            "Ogni linea è un tag nel periodo selezionato" to "Each line is a tag in the selected period",
            "I tag con più spesa nel periodo" to "The tags with the most spending in the period"
        ),
        "de" to mapOf(
            "Da dividere 50/50" to "50/50 teilen",
            "L'importo è il totale. A credito va la metà." to "Der Betrag ist die Summe. Die Hälfte wird zum Guthaben.",
            "L'importo è già la quota. A credito va per intero." to "Der Betrag ist bereits der Anteil. Der ganze Betrag wird zum Guthaben.",
            "Quota già suddivisa" to "Bereits aufgeteilter Anteil",
            "Andamento per tag" to "Verlauf nach Tags",
            "Ogni linea è un tag nel periodo selezionato" to "Jede Linie ist ein Tag im gewählten Zeitraum",
            "I tag con più spesa nel periodo" to "Die Tags mit den höchsten Ausgaben im Zeitraum"
        ),
        "fr" to mapOf(
            "Da dividere 50/50" to "À partager 50/50",
            "L'importo è il totale. A credito va la metà." to "Le montant est le total. La moitié devient un crédit.",
            "L'importo è già la quota. A credito va per intero." to "Le montant est déjà la part. Le montant entier devient un crédit.",
            "Quota già suddivisa" to "Part déjà répartie",
            "Andamento per tag" to "Évolution par tag",
            "Ogni linea è un tag nel periodo selezionato" to "Chaque ligne est un tag sur la période sélectionnée",
            "I tag con più spesa nel periodo" to "Les tags avec le plus de dépenses sur la période"
        ),
        "es" to mapOf(
            "Da dividere 50/50" to "A dividir 50/50",
            "L'importo è il totale. A credito va la metà." to "El importe es el total. La mitad queda a crédito.",
            "L'importo è già la quota. A credito va per intero." to "El importe ya es la cuota. El importe entero queda a crédito.",
            "Quota già suddivisa" to "Cuota ya repartida",
            "Andamento per tag" to "Evolución por tag",
            "Ogni linea è un tag nel periodo selezionato" to "Cada línea es un tag en el período seleccionado",
            "I tag con più spesa nel periodo" to "Los tags con más gasto en el período"
        ),
        "pt" to mapOf(
            "Da dividere 50/50" to "A dividir 50/50",
            "L'importo è il totale. A credito va la metà." to "O montante é o total. Metade fica a crédito.",
            "L'importo è già la quota. A credito va per intero." to "O montante já é a quota. O montante inteiro fica a crédito.",
            "Quota già suddivisa" to "Quota já repartida",
            "Andamento per tag" to "Evolução por tag",
            "Ogni linea è un tag nel periodo selezionato" to "Cada linha é um tag no período selecionado",
            "I tag con più spesa nel periodo" to "Os tags com mais despesa no período"
        ),
        "nl" to mapOf(
            "Da dividere 50/50" to "50/50 verdelen",
            "L'importo è il totale. A credito va la metà." to "Het bedrag is het totaal. De helft wordt tegoed.",
            "L'importo è già la quota. A credito va per intero." to "Het bedrag is al het aandeel. Het hele bedrag wordt tegoed.",
            "Quota già suddivisa" to "Al verdeeld aandeel",
            "Andamento per tag" to "Verloop per tag",
            "Ogni linea è un tag nel periodo selezionato" to "Elke lijn is een tag in de gekozen periode",
            "I tag con più spesa nel periodo" to "De tags met de meeste uitgaven in de periode"
        ),
        "pl" to mapOf(
            "Da dividere 50/50" to "Do podziału 50/50",
            "L'importo è il totale. A credito va la metà." to "Kwota to suma. Połowa staje się kredytem.",
            "L'importo è già la quota. A credito va per intero." to "Kwota jest już udziałem. Cała kwota staje się kredytem.",
            "Quota già suddivisa" to "Już podzielona część",
            "Andamento per tag" to "Przebieg według tagu",
            "Ogni linea è un tag nel periodo selezionato" to "Każda linia to tag w wybranym okresie",
            "I tag con più spesa nel periodo" to "Tagi z największymi wydatkami w okresie"
        ),
        "sv" to mapOf(
            "Da dividere 50/50" to "Dela 50/50",
            "L'importo è il totale. A credito va la metà." to "Beloppet är totalsumman. Hälften blir tillgodo.",
            "L'importo è già la quota. A credito va per intero." to "Beloppet är redan andelen. Hela beloppet blir tillgodo.",
            "Quota già suddivisa" to "Redan fördelad andel",
            "Andamento per tag" to "Utveckling per tagg",
            "Ogni linea è un tag nel periodo selezionato" to "Varje linje är en tagg i den valda perioden",
            "I tag con più spesa nel periodo" to "Taggarna med mest utgifter i perioden"
        ),
        "da" to mapOf(
            "Da dividere 50/50" to "Del 50/50",
            "L'importo è il totale. A credito va la metà." to "Beløbet er totalen. Halvdelen bliver tilgode.",
            "L'importo è già la quota. A credito va per intero." to "Beløbet er allerede andelen. Hele beløbet bliver tilgode.",
            "Quota già suddivisa" to "Allerede fordelt andel",
            "Andamento per tag" to "Udvikling pr. tag",
            "Ogni linea è un tag nel periodo selezionato" to "Hver linje er et tag i den valgte periode",
            "I tag con più spesa nel periodo" to "Taggene med mest forbrug i perioden"
        ),
        "fi" to mapOf(
            "Da dividere 50/50" to "Jaetaan 50/50",
            "L'importo è il totale. A credito va la metà." to "Summa on kokonaissumma. Puolet tulee saatavaksi.",
            "L'importo è già la quota. A credito va per intero." to "Summa on jo osuus. Koko summa tulee saatavaksi.",
            "Quota già suddivisa" to "Jo jaettu osuus",
            "Andamento per tag" to "Kehitys tunnisteittain",
            "Ogni linea è un tag nel periodo selezionato" to "Jokainen viiva on tunniste valitulla jaksolla",
            "I tag con più spesa nel periodo" to "Jakson eniten kulutetut tunnisteet"
        ),
        "cs" to mapOf(
            "Da dividere 50/50" to "Rozdělit 50/50",
            "L'importo è il totale. A credito va la metà." to "Částka je celková suma. Polovina jde do kreditu.",
            "L'importo è già la quota. A credito va per intero." to "Částka je už podíl. Celá částka jde do kreditu.",
            "Quota già suddivisa" to "Už rozdělený podíl",
            "Andamento per tag" to "Vývoj podle štítku",
            "Ogni linea è un tag nel periodo selezionato" to "Každá čára je štítek ve zvoleném období",
            "I tag con più spesa nel periodo" to "Štítky s nejvyššími výdaji v období"
        ),
        "sk" to mapOf(
            "Da dividere 50/50" to "Rozdeliť 50/50",
            "L'importo è il totale. A credito va la metà." to "Suma je celková čiastka. Polovica ide do kreditu.",
            "L'importo è già la quota. A credito va per intero." to "Suma je už podiel. Celá suma ide do kreditu.",
            "Quota già suddivisa" to "Už rozdelený podiel",
            "Andamento per tag" to "Vývoj podľa značky",
            "Ogni linea è un tag nel periodo selezionato" to "Každá čiara je značka vo zvolenom období",
            "I tag con più spesa nel periodo" to "Značky s najvyššími výdavkami v období"
        ),
        "sl" to mapOf(
            "Da dividere 50/50" to "Deliti 50/50",
            "L'importo è il totale. A credito va la metà." to "Znesek je skupni znesek. Polovica gre v dobro.",
            "L'importo è già la quota. A credito va per intero." to "Znesek je že delež. Celoten znesek gre v dobro.",
            "Quota già suddivisa" to "Že razdeljen delež",
            "Andamento per tag" to "Potek po oznaki",
            "Ogni linea è un tag nel periodo selezionato" to "Vsaka črta je oznaka v izbranem obdobju",
            "I tag con più spesa nel periodo" to "Oznake z največ stroški v obdobju"
        ),
        "hr" to mapOf(
            "Da dividere 50/50" to "Podijeliti 50/50",
            "L'importo è il totale. A credito va la metà." to "Iznos je ukupni iznos. Polovica ide u kredit.",
            "L'importo è già la quota. A credito va per intero." to "Iznos je već udio. Cijeli iznos ide u kredit.",
            "Quota già suddivisa" to "Već podijeljeni udio",
            "Andamento per tag" to "Kretanje po oznaci",
            "Ogni linea è un tag nel periodo selezionato" to "Svaka linija je oznaka u odabranom razdoblju",
            "I tag con più spesa nel periodo" to "Oznake s najviše troškova u razdoblju"
        ),
        "bg" to mapOf(
            "Da dividere 50/50" to "За делене 50/50",
            "L'importo è il totale. A credito va la metà." to "Сумата е общата сума. Половината става кредит.",
            "L'importo è già la quota. A credito va per intero." to "Сумата вече е дял. Цялата сума става кредит.",
            "Quota già suddivisa" to "Вече разпределен дял",
            "Andamento per tag" to "Динамика по етикет",
            "Ogni linea è un tag nel periodo selezionato" to "Всяка линия е етикет в избрания период",
            "I tag con più spesa nel periodo" to "Етикетите с най-много разходи в периода"
        ),
        "el" to mapOf(
            "Da dividere 50/50" to "Διαίρεση 50/50",
            "L'importo è il totale. A credito va la metà." to "Το ποσό είναι το σύνολο. Το μισό γίνεται πιστωτικό.",
            "L'importo è già la quota. A credito va per intero." to "Το ποσό είναι ήδη το μερίδιο. Ολόκληρο το ποσό γίνεται πιστωτικό.",
            "Quota già suddivisa" to "Ήδη επιμερισμένο μερίδιο",
            "Andamento per tag" to "Εξέλιξη ανά ετικέτα",
            "Ogni linea è un tag nel periodo selezionato" to "Κάθε γραμμή είναι μια ετικέτα στην επιλεγμένη περίοδο",
            "I tag con più spesa nel periodo" to "Οι ετικέτες με τη μεγαλύτερη δαπάνη στην περίοδο"
        ),
        "hu" to mapOf(
            "Da dividere 50/50" to "50/50 arányban osztandó",
            "L'importo è il totale. A credito va la metà." to "Az összeg a teljes összeg. A fele jóváírás.",
            "L'importo è già la quota. A credito va per intero." to "Az összeg már a rész. A teljes összeg jóváírás.",
            "Quota già suddivisa" to "Már felosztott rész",
            "Andamento per tag" to "Alakulás címke szerint",
            "Ogni linea è un tag nel periodo selezionato" to "Minden vonal egy címke a kiválasztott időszakban",
            "I tag con più spesa nel periodo" to "A legnagyobb kiadású címkék az időszakban"
        ),
        "ro" to mapOf(
            "Da dividere 50/50" to "De împărțit 50/50",
            "L'importo è il totale. A credito va la metà." to "Suma este totalul. Jumătatea devine credit.",
            "L'importo è già la quota. A credito va per intero." to "Suma este deja cota. Suma întreagă devine credit.",
            "Quota già suddivisa" to "Cotă deja împărțită",
            "Andamento per tag" to "Evoluție după etichetă",
            "Ogni linea è un tag nel periodo selezionato" to "Fiecare linie este o etichetă în perioada selectată",
            "I tag con più spesa nel periodo" to "Etichetele cu cele mai multe cheltuieli în perioadă"
        ),
        "et" to mapOf(
            "Da dividere 50/50" to "Jagada 50/50",
            "L'importo è il totale. A credito va la metà." to "Summa on kogusumma. Pool sellest läheb kreeditiks.",
            "L'importo è già la quota. A credito va per intero." to "Summa on juba osa. Kogu summa läheb kreeditiks.",
            "Quota già suddivisa" to "Juba jagatud osa",
            "Andamento per tag" to "Kulgemine sildi järgi",
            "Ogni linea è un tag nel periodo selezionato" to "Iga joon on silt valitud perioodis",
            "I tag con più spesa nel periodo" to "Perioodi suurima kuluga sildid"
        ),
        "lv" to mapOf(
            "Da dividere 50/50" to "Dalīt 50/50",
            "L'importo è il totale. A credito va la metà." to "Summa ir kopējā summa. Puse kļūst par kredītu.",
            "L'importo è già la quota. A credito va per intero." to "Summa jau ir daļa. Visa summa kļūst par kredītu.",
            "Quota già suddivisa" to "Jau sadalītā daļa",
            "Andamento per tag" to "Tendence pēc atzīmes",
            "Ogni linea è un tag nel periodo selezionato" to "Katra līnija ir atzīme izvēlētajā periodā",
            "I tag con più spesa nel periodo" to "Atzīmes ar lielākajiem izdevumiem periodā"
        ),
        "lt" to mapOf(
            "Da dividere 50/50" to "Dalinti 50/50",
            "L'importo è il totale. A credito va la metà." to "Suma yra bendra suma. Pusė tampa kreditu.",
            "L'importo è già la quota. A credito va per intero." to "Suma jau yra dalis. Visa suma tampa kreditu.",
            "Quota già suddivisa" to "Jau padalinta dalis",
            "Andamento per tag" to "Eiga pagal žymą",
            "Ogni linea è un tag nel periodo selezionato" to "Kiekviena linija yra žyma pasirinktame laikotarpyje",
            "I tag con più spesa nel periodo" to "Žymos su didžiausiomis išlaidomis laikotarpyje"
        )
    )
}
