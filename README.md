# Nomisma

Nomisma è un'app Android per dividere e sincronizzare le spese di un ledger condiviso. L'app è **local-first**: il ledger viene salvato sul dispositivo in SQLite e le operazioni possono essere pubblicate e ricevute tramite Nostr, con Amber come signer esterno.

Versione applicativa attuale: `0.1.0`.

## Stato attuale

Il progetto è un prototipo avanzato / beta interna. Il flusso locale è già utilizzabile; la sincronizzazione Nostr, il pairing e la diagnostica sono implementati e coperti da test unitari. Non sono ancora presenti una pipeline CI, una build firmata per la distribuzione o un relay incluso nel repository.

## Funzionalità disponibili

- onboarding iniziale con spiegazione di privacy, Amber e pairing;
- ledger locale con due partecipanti, valuta e capacità configurabili;
- inserimento, modifica ed eliminazione logica delle spese;
- importo, descrizione, esercente, note, data, categoria e tag;
- categorie e tag condivisi, con rinomina, archiviazione e ripristino;
- calcolo del saldo tra partecipanti e chiusura del ciclo;
- metodi di settlement: bonifico, contanti, Lightning o altro;
- storico delle spese e dei cicli chiusi;
- analisi per periodo, partecipante, categoria e tag, con confronto tra periodi;
- filtri salvabili, inclusi intervalli personalizzati e soglie di importo;
- tre scene visuali selezionabili:
  - `Vessel`: caraffa in vetro con liquido e animazioni di versamento;
  - `Woodland`: diorama del bosco con animali e materiali associati;
  - `Mempool`: ambiente minerario con carrello, miner e piccone;
- qualità grafica automatica o manuale (`LOW`, `MEDIUM`, `HIGH`) e modalità movimento ridotto;
- pairing tramite codice `nomisma1:` copiabile, condivisibile o importabile come deep link;
- outbox locale per le operazioni non ancora pubblicate;
- riconnessione del relay, recupero dello storico e stato di salute della sincronizzazione;
- diagnostica copiabile senza includere spese o chiavi complete.

## Sincronizzazione e sicurezza

La chiave privata non viene salvata da Nomisma: la firma e le operazioni di cifratura passano attraverso Amber/NIP-55.

Il trasporto predefinito usa NIP-59 gift wrap con contenuto cifrato NIP-44 v2. Il percorso diretto NIP-44 è mantenuto per test controllati. Gli eventi in ingresso vengono validati, verificati, decrittati e poi consegnati al repository, che gestisce validazione del ledger, deduplicazione, sequenze, conflitti e operazioni differite.

Per il relay sono accettati:

- `wss://...` per relay con TLS;
- `ws://192.168.1.10/...` per il relay LAN autorizzato dal progetto.

Il traffico `ws://` in chiaro è disabilitato per gli altri host. Anche quando il relay è in chiaro, il contenuto delle operazioni resta cifrato a livello Nostr.

Il codice di pairing contiene solo identificativo del ledger, relay, chiave pubblica e associazione dei partecipanti. Non contiene spese né chiavi private e include un checksum di integrità.

## Requisiti

- Android SDK `compileSdk 37`;
- Android `minSdk 26`, `targetSdk 37`;
- JDK compatibile con Android Gradle Plugin `9.4.1`;
- Gradle wrapper incluso nel repository;
- Amber installato sul dispositivo per verificare l'identità, firmare e cifrare;
- un relay Nostr raggiungibile, `wss://` oppure il relay LAN previsto dalla configurazione.

## Build e test

Dalla directory principale:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

L'APK debug viene prodotto in:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Per installarlo su un dispositivo o emulatore con ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Primo avvio

Al primo avvio il database crea un ledger locale demo chiamato `Casa`, in EUR, con i partecipanti `Alice` e `Bob`. Il ledger non viene sostituito durante un pairing finché l'utente non importa un invito relativo a un altro ledger.

Per configurare la sincronizzazione:

1. installare Amber e completare la verifica dell'identità nella schermata **Impostazioni**;
2. configurare relay, chiave pubblica del destinatario e partecipanti locale/remoto;
3. condividere il proprio codice pairing oppure importare quello dell'altro dispositivo;
4. attendere che i controlli di pairing risultino completi;
5. premere **Connetti** e, quando necessario, **Pubblica outbox**.

La riconnessione automatica può essere abilitata dalle impostazioni. La sessione Nostr resta attiva mentre si cambia schermata finché l'app rimane aperta.

## Struttura del progetto

```text
app/src/main/java/com/nomisma/app/
├── data/       SQLite locale, outbox e filtri salvati
├── domain/     modelli finanziari, policy, calcoli e operazioni
├── nostr/      relay WebSocket, Amber, NIP-44/NIP-59 e pairing
├── theme/      modelli e mapping delle scene visuali
└── ui/         Compose, ViewModel, schermate e renderer Canvas
```

I punti di ingresso principali sono:

- `MainActivity`: bootstrap Compose e deep link `nomisma1:`;
- `NomismaApp`: navigazione, onboarding, schermate e impostazioni;
- `NomismaViewModel`: stato UI e coordinamento tra repository e sessione Nostr;
- `LocalLedgerRepository`: autorità locale per dati, validazione e applicazione delle operazioni;
- `NomismaThemeScene`: renderer delle scene `Vessel`, `Woodland` e `Mempool`.

## Test presenti

La suite copre principalmente:

- modelli finanziari, policy e calcolo delle operazioni;
- serializzazione e validazione dei modelli Nostr;
- cifratura NIP-44 v2;
- pairing e checksum;
- validazione delle risposte del signer;
- policy di riconnessione del relay;
- configurazione e diagnostica della sincronizzazione.

## Limiti noti

- non è ancora disponibile un relay locale incluso nel progetto;
- la distribuzione è al momento limitata a build debug / sviluppo;
- non ci sono ancora test UI o screenshot test automatici;
- la sincronizzazione dipende dalla disponibilità di Amber e dalla configurazione corretta del relay;
- la licenza del progetto non è ancora definita in un file dedicato.

