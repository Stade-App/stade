# Stade

Stade is a peer-to-peer encrypted chat app with no central server. Every message travels directly between devices — over Tor onion services or the local network — and is protected by a post-quantum-hybrid encryption scheme. There are no accounts, no phone numbers, and no servers that ever see your messages or metadata.

Built with Kotlin Multiplatform and Compose Multiplatform, Stade runs natively on **Android**, **Windows**, **macOS**, and **Linux** from a single shared codebase.

## Core principles

- **No servers.** Messages go directly from sender to recipient. There is nothing in the middle to breach, subpoena, or go down.
- **No accounts.** Your identity is a self-sovereign `STADE-XXXXXXXXXXXX` ID, derived from your own keys — nothing is registered anywhere.
- **Post-quantum by default.** Every conversation is protected against both today's attackers and tomorrow's quantum computers.

## Security & Encryption

- **Hybrid post-quantum Double Ratchet** — every message is encrypted with a Double Ratchet that mixes a classical X25519 Diffie-Hellman ratchet with **ML-KEM-768** (a post-quantum key encapsulation mechanism) at every step, so forward secrecy and break-in recovery hold even against a future quantum adversary.
- **Dual signatures** — every handshake and identity claim is signed twice: once with classical **Ed25519** and once with post-quantum **ML-DSA-65**, so authentication doesn't rest on a single cryptographic assumption.
- **Self-sovereign identity** — your `STADE-` ID is deterministically derived from the hash of your own public keys. No server issues it, no server can revoke it.
- **Contact verification** — verify a contact out-of-band to confirm you're really talking to them and not a man-in-the-middle.
- **Persistent ratchet sessions** — encrypted session state survives app restarts without ever weakening forward secrecy.

## Privacy & App Protection

- **PIN-protected vault** — the entire local database is encrypted at rest and locked behind a PIN, with configurable auto-lock timing.
- **Duress PIN** — a second, separate PIN that instantly and permanently wipes the device if entered under coercion.
- **Scrambled PIN keypad** — randomizes key layout to defeat shoulder-surfing and smudge attacks.
- **Failed-attempt lockout** — escalating delays after repeated wrong PIN entries.
- **Screenshot & screen-recording blocking** — app content is hidden from screenshots and the OS recent-apps switcher.
- **Transport lock** — require your PIN before network/transport settings can even be viewed.
- **Tor identity wipe** — instantly discard and regenerate your Tor circuit identity.
- **Private notifications** — notifications can show "you have new messages" instead of sender names or message content.
- **Disappearing messages** — set a conversation to auto-delete its messages after a timer (30 minutes up to 1 day), synced automatically with the other side.
- **Launcher shortcut control** — choose whether recent conversations appear in the Android long-press app shortcut menu; deleted contacts are pruned automatically so nothing stale ever lingers there.

## Networking & Transport

- **Dual transport** — messages route over embedded **Tor** (onion services, no reliance on a system-installed Tor) or the **local network**, automatically or by your choice.
- **Bundled Tor binary** — Tor ships inside the app itself, per-device architecture, so there's no external dependency to install or trust.
- **Configurable Tor bridges**, including built-in obfs4 bridges, for use on censored or restrictive networks.
- **Resilient reconnection** — connections automatically retry with adaptive backoff, tuned to minimize battery drain while the app is backgrounded.
- **Tor-routed link previews** — link previews for URLs shared in chat are always fetched over Tor, regardless of which transport the conversation itself is using, and can be disabled entirely.

## Messaging

- One-on-one chats and multi-member **group chats**.
- **Stadiums** — a broadcast channel primitive: one owner posts, everyone who joins receives. Perfect for announcements or one-to-many updates without turning it into a group chat. Owners can rename, mute, or remove members and delete individual broadcasts or the whole stadium — all changes propagate peer-to-peer to every subscriber.
- **Voice messages** — record and send Opus-encoded voice clips directly in a conversation.
- **Stickers** — create your own stickers from photos, with built-in AI background removal.
- **Reactions** — react to any message with an emoji.
- **Receipts & delivery status** for every message.
- **Local full-text search** across your conversations.
- **Media attachments** with an in-app editor.

## Platforms

| Platform | Package format |
|---|---|
| Android | native APK |
| Windows | `.exe` installer |
| macOS | `.dmg` |
| Linux | `.deb`, `.rpm` |

## Localization

Stade is available in **English** and **Turkish**, switchable from Settings.

## What Stade deliberately doesn't do

- **No accounts, no phone numbers, no central directory.** Every identity is self-issued and every message path is peer-to-peer.
