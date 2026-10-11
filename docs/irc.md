# Native IRC terminal

Checkpoint before the pivot: local commit `8f70b23`. No push, release or version bump.

`irc` now opens `IrcActivity`, not PocketBase's community feed. `community` remains available to protect the previous work and its data. No migration, reset or removal of that backend has occurred. IRC usernames/passwords are separate from community usernames/passwords.

## Terminal workflow

The implementation keeps server/channel/query buffers, following the original terminal clients, but everyday navigation uses named conversation chips and a guided composer instead of visible buffer numbers:

- [BitchX window source](https://github.com/BitchX/BitchX1.3/blob/master/source/window.c): numbered window switching.
- [irssi window commands](https://irssi.org/documentation/help/window/): window list/goto/next/previous, separate from joining.
- [WeeChat user guide](https://weechat.org/files/doc/stable/weechat_user.en.html): connection, channel and private-message buffers.

No upstream code was copied. Kotlin protocol/client/state files have no Android or PocketBase dependency (TLS hostname verification reuses the already-installed OkHttp). The Android Activity adds the existing Re:TUI frame, font, shared suggestion-chip styling, window geometry and one composer. No discussion forms or web-feed controls.

Open `irc`, tap **Sign in**, enter your existing IRC account name, then enter its password in the same masked composer. On the default network the validated account name also becomes the nickname. Successful authentication requests `JOIN #retui`; membership is not displayed until the server confirms it. Failure offers another sign-in, never guest access as an authentication fallback.

**Channels**, **People**, **Help** and **More** replace the conversation area temporarily. Their input is local navigation/search, never a chat message. Channels lists open conversations first and suggests `#retui` / `#help` on the default network; Browse network requests IRC LIST, capped at 1,000 entries with an explicit truncation notice and 50-row display increments. People refreshes NAMES and opens a local private conversation when tapped, without sending a message. Help starts with three short instructions; All commands is a searchable grouped reference. More includes connection details, a confirmed channel-leave action and disconnect. Server diagnostics remain separate.

Slash commands remain available: `/join #help`, `/window #help`, `/window 2`, `/window next`, `/query nickname`, `/msg nickname hello`, `/me waves`, `/part #help` and `/disconnect`. Typing `/` displays matching command chips and syntax guidance. Touch and command routes use the same join/query/directory/names actions.

Joining sends `JOIN`. Switching only changes the local active buffer; it does not part/rejoin the server. Conversation chips are shortcuts to the same operation. Alt+1..9 and Ctrl+Tab / Ctrl+Shift+Tab retain numeric switching. Each buffer retains its in-session draft and reading position. Hidden conversations, including the current one behind a navigation view, accumulate unread activity; `+N` is unread activity and `!N` indicates a mention or private message. Chat displays `<nickname> message`, not raw `nick!ident@host` transport prefixes. Back first returns from a temporary view, then closes/disconnects the pane. Rotation preserves navigation and drafts but cancels password entry.

`/server host [TLS-port]` selects another TLS server while disconnected. `/connect` opens a nickname-only connection where the network permits it. `/register` uses a masked NickServ password, while `/login account` negotiates SASL PLAIN over verified TLS. The default network is a closed pilot: new-account registration is currently disabled. Registration on other networks may require email or another flow this initial client does not implement.

`/help` opens the temporary help view without switching buffers or writing a manual into scrollback. Commands include `/names`, `/list`, `/topic`, `/whois`, `/mode`, `/kick`, `/oper`, `/clear` and `/window close`. Plain text is only sent to the active joined channel or query. `//text` posts `/text` literally. Unknown slash commands are rejected, not posted or passed to Launcher. There is no raw-command passthrough, shell execution, DCC or automatic CTCP handling.

## Optional remembered-device authentication

On the default network, opt in to **Remember this device** before password sign-in. Android Keystore creates a non-exportable P-256 elliptic-curve client key; verified TLS presents its self-signed identity certificate, then password SASL authenticates the account. The client submits NickServ `CERT ADD` and only saves the host/port/account binding after NickServ confirms authorization. No password or private key is written to settings, transcripts or a backup. The key certificate expires after ten years; hardware backing depends on the phone.

Opening IRC thereafter automatically reconnects with TLS client-certificate authentication and SASL EXTERNAL, then requests `#retui`. Reconnect touch and slash actions also support the remembered binding. Unsupported mechanisms, missing keys, rejected credentials and TLS failures do not fall back to guests or saved passwords. Users can sign in again with a password. Connection details offers **Forget this device**: while connected to the bound account it requests NickServ `CERT DEL`, waits for confirmation, then deletes the local key and disconnects. If disconnected or confirmation fails, local deletion still occurs and the UI explicitly warns that server-side revocation was not confirmed.

This is native [Ergo certificate authentication](https://github.com/ergochat/ergo/blob/master/docs/MANUAL.md), not a new bearer-token backend. Device metadata is in Android's no-backup directory and keys are not portable between phones or after app-data deletion. Anyone able to use the unlocked Launcher can use the remembered account. This adds no background connection, startup service or automatic message replay. Registration remains closed on the default network. This pass changes no VPS configuration and enrolls no production account during tests.

## Security and delivery contract

- Certificate chain and hostname are verified before account credentials are sent. No plaintext or trust-all fallback. The existing OkHttp verifier works on the minimum Android API 23.
- Account login is never silently downgraded to guest login. CAP/SASL frames and own service-command echoes are excluded from scrollback. Password entry disables saved view state/autofill/suggestions/IME learning and screen capture. Back or leaving cancels the password stage.
- Passwords are short-lived process memory only; neither settings nor transcript stores receive them. Java strings/IME/OS memory cannot be promised securely erased.
- Outbound messages appear immediately with an explicit pending marker when `echo-message` is available. A matching server echo removes the marker; this is server acceptance, **not** recipient delivery/read confirmation. Without that extension local echo stays unconfirmed. Disconnect marks pending sends uncertain and never resends them automatically.
- One foreground network connection, 32 buffers, 200 scrollback lines per buffer, 1,000 cached names per channel, bounded 64-frame outgoing and 256-event UI queues, and 8,192-byte incoming frame limit. UI events drain in bounded batches; overload disconnects rather than growing the callback queue. Outgoing IRC frames are capped at 512 UTF-8 bytes including CRLF.
- Rotation preserves the connection and buffers in a ViewModel. Leaving the Activity disconnects. No background service, push, attachments, offline replay, persistent local transcript or process-death draft restoration in this first slice. Ergo's always-on account presence may outlive the client socket; disconnected UI does not claim to remove that server-side presence.
- TLS is transport encryption, not end-to-end encryption. Server operators can read messages. Existing Ergo history is server-side, with a one-week expiry setting, **not** the old HTTP community's 24-hour policy. Actual expiry/purge/recovery behavior needs its own release audit before advertising a retention guarantee.

## VPS state and safe operation

Existing server, reused rather than replaced: Ergo 2.19.1 at `irc.kokodoko.cloud:6697`, `ergo.service`, `/etc/ergo/ircd.yaml`. Hostname lookup is off and IP cloaking is enabled. Existing PocketBase `retui-community` remains active and untouched.

On October 11, 2026, `server/irc/provision.py --apply --rotate-history-password` was run as VPS root after an unclaimed-name check. It reserved and verified `dvil` with SASL, added only the `dvil` operator entry, and registered `#retui` and `#help` with `dvil` as founder. The moderator class grants kill/ban/sajoin/samode/chanreg, not full server-admin/account-management permissions. New channels default to `+ntCR`; channel creation/registration are operator-only. Official channel `+R` rejects unauthenticated joins, while nickname-only connections remain possible for status/protocol checks. New-account registration is disabled for the pilot; the listener was already internet-reachable, so this is a closed-account pilot, not a firewall-private service.

Ergo operator authentication is a **separate operator password**, not an account-name binding. Only `dvil` is configured, but whoever possesses its operator password can use that operator entry. Do not share either credential. The launcher uses a masked `/oper dvil` step, not passwords typed into transcript commands.

Initial credentials are mode `0600` in `/root/retui-irc-dvil.json` on the VPS, with a private handoff copy at `/Users/mako/.ssh/retui-irc-dvil.json`. No password is stored in this repository or printed in test output. Keep that file private and change the initial passwords before wider use through a trusted IRC client's NickServ password flow and an administrator-managed operator hash update.

A diagnostic accidentally printed the old MySQL history password during discovery. It was rotated, the new credential was verified directly, and both services remained active. A protected configuration-only rollback copy is `/root/retui-ergo-config-20261011-044209.yaml`; it contains the now-invalid old database password. Do **not** restore it verbatim: preserve the current history credential or coordinate a deliberate database credential rollback. No full database copy, account deletion or chat deletion was performed.

The provisioning script refuses unexpected server/operator config or an existing `dvil` account it cannot authenticate using its protected recovery file. It does not take over channels belonging to another founder. It is an explicit root-only mutation, **not** a startup task; do not rerun it blindly or use it as a release toggle. The script's live PASS checks verified account authentication, operator authentication, channel ownership/mode and rejection of guest joins/new channels. Logs/raw frames/SQL containing credentials are deliberately withheld.

Live native-client checks are opt-in:

```
RETUI_IRC_TEST_CREDENTIAL_FILE=/Users/mako/.ssh/retui-irc-dvil.json \
  ./gradlew :app:testPlaystoreDebugUnitTest --tests 'ohi.andre.consolelauncher.irc.*'
```

These verify hostname rejection, native TLS/SASL, joining both official channels and guest rejection; they do not post public messages. Offline protocol/state/socket fixtures and device UI fixtures require no account. Public registration, server capacity/load, operational recovery, Android background/reconnect behavior and phone UX still need separate release validation. No public IRC release is claimed.

## Guided-terminal verification — October 11, 2026

- Play Store debug APK/test APK assembly, F-Droid Kotlin compilation and `git diff --check` passed. IRC JVM fixtures passed 28 tests; four credential-dependent live-account tests were deliberately skipped.
- Physical phone `10BE1C0FB40006A`: 12 checks passed, covering masked sign-in cancellation, navigation/draft/reading-position preservation, hidden unread counts, rotation, LIST/NAMES navigation, local-only search, immediate message display and echo deduplication, launcher chip height/48dp targets, largest launcher font in the minimum floating pane, landscape geometry, persistent key enrollment/reconnect/revocation fixtures and a read-only live TLS/capability probe. Emulator: the 11 offline checks passed; the live probe was skipped there.
- The live probe sent only `CAP LS 302`, without NICK/USER, authentication, channel membership or messages. It verified the server requested the phone's client certificate and advertised SASL EXTERNAL. An initial RSA Keystore handshake failed; P-256 with Android's documented pre-hashed TLS signing authorization passed. See [Android key digest requirements](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder#setDigests(java.lang.String...)) and [Conscrypt's delegated-key signing implementation](https://github.com/google/conscrypt/blob/master/common/src/main/java/org/conscrypt/CryptoUpcalls.java). No upstream code was copied.
- One earlier post-install Back fixture was intermittent; isolated and full reruns passed. The harness now waits for the resumed foreground lifecycle and enabled Back callback before entering credentials. The final complete phone run passed, followed by an additional cancellation check.
- Phone screenshots were visually inspected for Help, People, directory, command guidance, chat, landscape and largest-font/narrow floating layout. Non-searchable navigation pages hide the unused composer; searchable pages retain it. Native user fonts survive password-mode changes. Some earlier captures contained the user's picture-in-picture overlay, so they were not treated as proof of unobstructed controls.
- Installed in place with matching signer SHA-256 `f1f50b573286bfbd59b8bfbb39d202ea3adf173f12077d98110bb9ed981b93f2`. Package `com.dvil.tui_renewed`, versionCode 426/versionName 2; first install remains `2026-10-05 20:28:47`, final update `2026-10-11 19:22:27`. APK SHA-256: `25c4abe843b1653b9fa91d49eed64f1716b1dffca31fd92784fa89d4f5f88f82`.
- No real account was enrolled or revoked, no production message was posted, no VPS policy changed, and no commit/push/release was made. Real-account enrollment and end-to-end authenticated reconnect remain a user-account validation step; fixture results plus the live TLS probe do not replace it. This work does not clear the separate public-release gates or constitute a full accessibility audit.
