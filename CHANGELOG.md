# Changelog

## 0.1.3 — 2026-10-27

Sync that works on its own, and a two-way check that both devices hold the same balances. Both devices need 0.1.3.

### Added

- Always-on sync. Once paired, Nomisma connects to the relay at start, after importing an invite, after choosing the other participant, and after Amber confirms the identity. Every change is published as soon as it is saved, with no button to press; whatever a relay refuses is retried with growing delays. "Disconnect" still turns it off.
- Two-way balance check. Each device keeps a vector clock (the last operation it applied for each author) and a SHA-256 fingerprint of the financial state: entries, amounts, payers, split, deletions, and cycles. The devices exchange these checkpoints and compare fingerprints only when their clocks match, so a sync in progress never raises a false alarm. When one device has not seen some of the other's operations, the other sends them again by itself. Both screens show the same short fingerprint and the time of the last agreement, so it can be compared by eye too. Checkpoints are gift-wrapped like operations and expire on the relay after 15 minutes (NIP-40).
- NIP-42 relay authentication, for relays that only deliver gift wraps to their recipient.

### Fixed

- Operations never reached the other device. Three bugs stacked up: the native secp256k1 library was never loaded, the gift-wrap Schnorr signature had message and key swapped, and the NIP-44 key came from hashed ECDH instead of the raw shared point. The cryptography is now tested against the real native library and the official NIP-44 vectors.
- relay.damus.io no longer delivers gift wraps (it demands NIP-42 and then rejects it). The default relay is now nos.lol, and the presets are relays that deliver gift wraps to their recipient. A pairing made on damus needs the relay changed on both devices.
- Operations published to a relay or to a partner that never received them are sent again when the relay or the other participant changes.
- Private messages from other apps share the same inbox. Nomisma no longer decrypts them: a gift wrap whose seal is not from the other participant is dropped before its content is opened.
- Incoming sync could freeze for good when an Amber request was closed or cancelled. Amber now answers in the background (NIP-55 content provider) when the permission is remembered, and its screen opens only when needed.
- Recipient profiles showed as unknown, for the same native-library bug.

## 0.1.2 — 2026-10-18

Easier pairing: the invite is picked up by the app, and the other participant is found by profile name.

### Added

- Pairing invite recognition. Copy the message from a chat and open Nomisma: the import dialog opens with the code already filled in. The invite is also accepted from the Share sheet and from the text-selection menu ("Import Nomisma invite"). To do this, Nomisma reads each newly copied text while it is in front and ignores anything that is not an invite. The code of this device and one already imported are skipped.
- The invite message now says how to accept it.
- Recipient search by profile name. The field that took a raw public key now searches public relays (NIP-50) for `name`, `name@domain`, or an `npub`; a pasted `npub` or hex key is taken at once. Every profile is signature-checked, and its NIP-05 address is checked on its own site, so a verified match is marked and lookalikes are not. The chosen recipient is shown with its name and shortened `npub`, with a Change button.

### Fixed

- Importing an invite failed with "the local ledger contains unsynced changes" after any theme, name, or color change made before pairing. Only unsynced entries, categories, and tags still block the import. Unsynced configuration is dropped and the configuration goes back to defaults, so the ledger starts from the same state as the other device. A name or theme set before importing has to be set again afterwards.

## 0.1.1 — 2026-10-18

Sync fix. Devices must all be on 0.1.1: a 0.1.0 device cannot exchange operations with a 0.1.1 one.

### Fixed

- Sync between two devices never delivered anything. Event ids were hashed from JSON that Android escapes differently from the NIP-01 specification (`/` became `\/`), so Amber-signed events failed with "Event id does not match canonical event data" and events from other clients were dropped on arrival. Event serialization now follows NIP-01 exactly, and a test pins it against a reference computed outside Android.

## 0.1.0 — 2026-10-18

First Nomisma release.

### Ledger

- Local ledger for two participants, with a currency and a spending cap for the cycle.
- Expenses with amount, description, merchant, notes, date, category, and tags.
- A "Split 50/50" switch. On, the amount is the whole expense and half of it becomes credit for whoever paid. Off, the amount is already the share and the full amount becomes credit. Either way, volume, tags, and the scene count the full amount.
- Shared categories and tags, with rename, archive, and restore.
- Balance, cycle close, and settlement methods: bank transfer, cash, Lightning, or other.
- History of expenses and closed cycles.
- Analytics by period, participant, category, and tag, with a comparison between periods.
- Cartesian chart of each tag across the selected period.
- Saved filters, with a date range and amount thresholds.

### Scenes

- Vessel, Woodland, and Mempool. Scene options appear only for the theme that uses them: animal for Woodland, miner appearance for Mempool, liquid color for Vessel.
- When cycle spending passes the cap, Vessel stays full and the excess leaves through the spout, outside the glass.
- Automatic or manual rendering quality, and reduced motion.

### Privacy and sync

- The ledger on the device is encrypted with SQLCipher. The encryption key is protected by the Android Keystore with AES-GCM.
- Nomisma does not keep the private key: signing and encryption go through Amber.
- Sync publishes encrypted operations with NIP-59 and NIP-44 to a `wss://` relay, or to the authorized LAN relay.
- The pairing code contains neither expenses nor private keys.

### Interface

- The interface is translated into the languages of the European Union, except Maltese and Irish Gaelic. Names, expense text, and tags written by the people using the app stay as entered.
- Back from an inner screen returns home. On home, it asks for confirmation before leaving.
- Lightning donation to the project address.

### Distribution

- Release APK signed with a dedicated release key. The key and its password are not in the repository.
