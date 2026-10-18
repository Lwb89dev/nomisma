# Changelog

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
