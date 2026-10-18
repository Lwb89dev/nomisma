# Changelog

## 0.1.0 — 2026-10-18

Prima release di Nomisma.

### Registro

- Libro locale per due partecipanti, con valuta e soglia di spesa del ciclo.
- Spese con importo, descrizione, esercente, note, data, categoria e tag.
- Interruttore «Da dividere 50/50». Acceso, l'importo è la spesa intera e a credito va la metà di chi ha pagato. Spento, l'importo è già la quota e va a credito per intero. In entrambi i casi il volume, i tag e la scena contano l'importo pieno.
- Categorie e tag condivisi, con rinomina, archivio e ripristino.
- Saldo, chiusura del ciclo e metodi di saldo: bonifico, contanti, Lightning o altro.
- Storico di spese e cicli chiusi.
- Analisi per periodo, partecipante, categoria e tag, con confronto tra periodi.
- Andamento cartesiano dei tag nel periodo scelto.
- Filtri salvati, con intervallo di date e soglie di importo.

### Scene

- Vessel, Woodland e Mempool. Le opzioni di scena compaiono solo per il tema a cui servono: animale per Woodland, aspetto del minatore per Mempool, colore del liquido per Vessel.
- Quando la spesa del ciclo supera la soglia, Vessel resta pieno e l'eccesso esce dal beccuccio, fuori dal vetro.
- Qualità di rendering automatica o manuale, e movimento ridotto.

### Privacy e sincronizzazione

- Il libro sul dispositivo è cifrato con SQLCipher. La chiave di cifratura è protetta dal Keystore di Android con AES-GCM.
- Nomisma non conserva la chiave privata: firma e cifratura passano da Amber.
- La sincronizzazione pubblica operazioni cifrate con NIP-59 e NIP-44 su un relay `wss://`, oppure sul relay LAN autorizzato.
- Il codice di associazione non contiene spese né chiavi private.

### Interfaccia

- Interfaccia tradotta nelle lingue dell'Unione europea, esclusi maltese e gaelico irlandese. I nomi, i testi delle spese e i tag scritti da chi usa l'app restano come sono stati inseriti.
- Il tasto Back dalle schermate interne torna alla home. Dalla home chiede conferma prima di uscire.
- Donazione Lightning verso l'indirizzo del progetto.

### Distribuzione

- APK di release firmato con una chiave di release dedicata. Chiave e password non sono nel repository.
