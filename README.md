# Nomisma

Nomisma is an Android app for splitting and synchronizing expenses in a shared ledger. It is **local-first**: the ledger is stored on the device in SQLite, while operations can be published and received over Nostr using Amber as an external signer.

Current application version: `0.1.4`. See [CHANGELOG.md](CHANGELOG.md).

## Current status

The project is an advanced prototype / internal beta. The local workflow is usable; Nostr synchronization, pairing, diagnostics, and the lifecycle-safe theme event pipeline are implemented and covered by unit tests. Debug builds use the Android debug key. Release builds are signed with a local release keystore that is not stored in the repository. There is currently no CI pipeline, store distribution, or relay bundled with the repository.

## Available features

- first-run onboarding covering scene selection, privacy, Amber, curated public relays, and pairing;
- local ledger with two participants, configurable currency, and capacity;
- expense creation, editing, and soft deletion;
- Android notifications for new expenses received from the paired participant, including expenses applied during relay backlog reconciliation;
- amount, description, merchant, notes, date, category, and tags;
- shared categories and tags, with rename, archive, and restore actions;
- balance calculation and settlement-cycle closure;
- settlement methods: bank transfer, cash, Lightning, or other;
- expense and closed-cycle history;
- analytics by period, participant, category, and tag, including period comparisons;
- saved filters with custom date ranges and amount thresholds;
- three selectable visual scenes:
  - `Vessel`: a hand-blown glass pitcher with cavity-clipped liquid, participant-colored vertical pours, ballistic splash droplets, ripples, and wet-wall trails;
  - `Woodland`: a natural-history woodland diorama with realistic animals and a hollow oak log whose window shows participant A's walnuts and participant B's acorns;
  - `Mempool`: a period mining environment with a riveted iron ore cart, animated mempool blocks, pickaxe actions, and a local male/female miner appearance preference;
- scene selection is available only during onboarding and in Settings;
- automatic or manual rendering quality (`LOW`, `MEDIUM`, `HIGH`) and reduced-motion mode;
- layered painterly scene assets, asynchronous bitmap decoding, and procedural fallbacks;
- a ViewModel-owned semantic animation queue with live-event ordering and backlog reconciliation;
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

The onboarding offers `wss://relay.damus.io`, `wss://nos.lol`, and `wss://relay.primal.net` as curated public starting points. Nomisma currently uses one primary relay per paired session; a custom relay remains configurable in Settings.

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
./gradlew assembleRelease
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

Install it on a connected device or emulator with ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

`assembleRelease` signs the APK with `keystore/nomisma-release.jks`. The keystore and `keystore/keystore.properties` are gitignored. Without them the release build stops. A release-signed APK does not install over a build signed with the debug key.

## First launch

On a new database, the app creates a local demo ledger named `Casa`, in EUR, with `Alice` and `Bob` as participants. The local ledger is not replaced during pairing until the user imports an invitation for another ledger.

To configure synchronization:

1. install Amber and verify the identity during onboarding or from **Settings**;
2. choose a curated relay during onboarding, or configure a custom relay in **Settings**;
3. configure the recipient public key and local/remote participants;
4. share your pairing code or import the code from the other device;
5. wait until all pairing checks are complete;
6. tap **Connect** and, when needed, **Publish outbox**.

Automatic reconnection can be enabled in Settings. The Nostr session remains active while switching screens as long as the app stays open.

## Project structure

```text
app/src/main/java/com/nomisma/app/
├── data/       SQLite storage, outbox, and saved filters
├── domain/     financial models, policies, calculations, and operations
├── nostr/      WebSocket relay, Amber, NIP-44/NIP-59, and pairing
├── theme/      visual-scene models and state mapping
└── ui/         Compose UI, ViewModel, screens, and Canvas renderers

app/src/main/assets/themes/  Layered runtime artwork and scene manifests
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
- synchronization configuration and diagnostics;
- theme event ordering, burst collapse, consumption, and backlog reconciliation.

## Known limitations

- no local relay is bundled with the project;
- release builds use a debug key and are for local/development distribution only;
- there are no UI or screenshot tests yet;
- synchronization depends on Amber availability and correct relay configuration;
- expense notifications are created after Nostr delivery while the recipient app process is alive; there is no FCM push path when Android has stopped the app;
- the project license has not yet been defined in a dedicated file.
