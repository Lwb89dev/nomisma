# Nomisma

Nomisma is an Android app for splitting and synchronizing expenses in a shared ledger. It is **local-first**: the ledger is stored on the device in SQLite, while operations can be published and received over Nostr using Amber as an external signer.

Current application version: `0.1.0`.

## Current status

The project is an advanced prototype / internal beta. The local workflow is usable; Nostr synchronization, pairing, and diagnostics are implemented and covered by unit tests. There is currently no CI pipeline, signed distribution build, or relay bundled with the repository.

## Available features

- first-run onboarding covering privacy, Amber, and pairing;
- local ledger with two participants, configurable currency, and capacity;
- expense creation, editing, and soft deletion;
- amount, description, merchant, notes, date, category, and tags;
- shared categories and tags, with rename, archive, and restore actions;
- balance calculation and settlement-cycle closure;
- settlement methods: bank transfer, cash, Lightning, or other;
- expense and closed-cycle history;
- analytics by period, participant, category, and tag, including period comparisons;
- saved filters with custom date ranges and amount thresholds;
- three selectable visual scenes:
  - `Vessel`: a glass pitcher with liquid and pouring animations;
  - `Woodland`: a woodland diorama with animals and themed contribution materials;
  - `Mempool`: a mining environment with a cart, miners, and pickaxe animations;
- automatic or manual rendering quality (`LOW`, `MEDIUM`, `HIGH`) and reduced-motion mode;
- pairing through a copyable, shareable, or deep-linkable `nomisma1:` code;
- local outbox for operations that have not been published yet;
- relay reconnection, history recovery, and synchronization health reporting;
- copyable diagnostics that omit expenses and complete keys.

## Synchronization and security

Nomisma does not store the private key: signing and encryption requests are delegated to Amber/NIP-55.

The default transport uses NIP-59 gift wrapping with NIP-44 v2 encrypted content. Direct NIP-44 is retained for controlled relay tests. Incoming events are structurally validated, verified, decrypted, and then passed to the repository, which owns ledger validation, deduplication, sequencing, conflict handling, and deferred operations.

Supported relay URLs are:

- `wss://...` for TLS-enabled relays;
- `ws://192.168.1.10/...` for the authorized LAN relay.

Cleartext `ws://` traffic is disabled for other hosts. Even when the relay uses cleartext WebSockets, operation contents remain encrypted at the Nostr layer.

A pairing code contains only the ledger identifier, relay URL, public key, and participant mapping. It contains no expenses or private keys and includes an integrity checksum.

## Requirements

- Android SDK `compileSdk 37`;
- Android `minSdk 26`, `targetSdk 37`;
- a JDK compatible with Android Gradle Plugin `9.4.1`;
- the Gradle wrapper included in the repository;
- Amber installed on the device for identity verification, signing, and encryption;
- a reachable Nostr relay using `wss://`, or the configured LAN relay.

## Build and test

From the project root:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device or emulator with ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## First launch

On a new database, the app creates a local demo ledger named `Casa`, in EUR, with `Alice` and `Bob` as participants. The local ledger is not replaced during pairing until the user imports an invitation for another ledger.

To configure synchronization:

1. install Amber and verify the identity from **Settings**;
2. configure the relay, recipient public key, and local/remote participants;
3. share your pairing code or import the code from the other device;
4. wait until all pairing checks are complete;
5. tap **Connect** and, when needed, **Publish outbox**.

Automatic reconnection can be enabled in Settings. The Nostr session remains active while switching screens as long as the app stays open.

## Project structure

```text
app/src/main/java/com/nomisma/app/
├── data/       SQLite storage, outbox, and saved filters
├── domain/     financial models, policies, calculations, and operations
├── nostr/      WebSocket relay, Amber, NIP-44/NIP-59, and pairing
├── theme/      visual-scene models and state mapping
└── ui/         Compose UI, ViewModel, screens, and Canvas renderers
```

Main entry points:

- `MainActivity`: Compose bootstrap and `nomisma1:` deep-link handling;
- `NomismaApp`: navigation, onboarding, screens, and settings;
- `NomismaViewModel`: UI state and coordination between the repository and Nostr session;
- `LocalLedgerRepository`: local authority for data, validation, and operation application;
- `NomismaThemeScene`: renderer for the `Vessel`, `Woodland`, and `Mempool` scenes.

## Test coverage

The current test suite primarily covers:

- financial models, policies, and operation calculations;
- Nostr model serialization and validation;
- NIP-44 v2 encryption;
- pairing and checksums;
- signer response validation;
- relay reconnection policy;
- synchronization configuration and diagnostics.

## Known limitations

- no local relay is bundled with the project;
- distribution is currently limited to debug/development builds;
- there are no UI or screenshot tests yet;
- synchronization depends on Amber availability and correct relay configuration;
- the project license has not yet been defined in a dedicated file.
