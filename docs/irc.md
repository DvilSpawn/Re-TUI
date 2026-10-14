# Native IRC

Launcher talks standard IRC over verified TLS. It has no separate proprietary
chat API. The default network is `irc.kokodoko.cloud:6697`; compatible external
TLS networks can be selected while disconnected.

## Accounts and everyday use

Open `irc`, choose **Sign in** or **Create account**, and enter an account name
followed by a masked password in the same composer. Default-network registration
uses username/password without email, subject to server limits. Registered
authentication is required; errors never fall back to guest access.
Successful authentication requests `#retui` and waits for confirmed membership.

Channel and private-conversation chips switch locally. **Channels**, **People**,
**Help** and **More** temporarily replace the conversation area while preserving
drafts and reading positions. Navigation search is never posted as a message.
People refreshes NAMES; tapping a nickname opens a private conversation but sends
nothing. Network LIST results are capped at 1,000 with a truncation notice.

Slash commands use the same action functions as touch controls:

```
/join #help
/window #retui
/query nickname
/help
```

Typing `/` shows command suggestions and syntax. Unknown commands are rejected;
`//text` sends a literal leading slash. Numeric shortcuts remain available even
though buffer numbers are not shown on conversation chips.

## Reconnect, sending and privacy

Optional **Remember this device** authorizes a non-exportable Android Keystore
key after successful account authentication. Passwords are not saved. Opening IRC
automatically reconnects an enrolled device. This is not a portable restore code:
reinstalling, clearing data or using a new phone requires password sign-in again.

**Forget this device** requests server revocation when connected, then removes
the local key. If revocation is unconfirmed, local deletion does not prove server
revocation; remove the old certificate authorization through NickServ on a trusted
client. Forgetting a device does not delete the server account.

Messages appear immediately with pending/server-echo state. Echo confirms server
acceptance, not delivery or reading. Uncertain sends are not automatically retried.
TLS is not end-to-end encryption: server operators can read messages and see
connection addresses. dvil is the default network's configured moderator.

Back first leaves temporary navigation, then closes the pane and disconnects.
There is no background IRC service, push or persistent local transcript. On servers
offering IRCv3 chat history, each confirmed channel join loads up to 100 recent
messages (or the server's smaller limit). This includes messages sent while the
pane was closed. Overlapping history is deduplicated by message ID or server time;
older messages are inserted before newer ones and do not trigger unread alerts.
Completed history refreshes remove previously cached messages absent from the
server's latest window, while preserving new messages arriving during the refresh.
On servers supporting IRCv3 message redaction, server-confirmed deletions replace
the displayed content with a deletion marker immediately. A deleted message ID
cannot reappear from overlapping history. Clearing the local pane only clears that
phone's display; deletion on IRC does not remove already-relayed Discord copies.
On a supported connection, **More → Delete a message** (or `/delete`) lists
server-confirmed channel messages. Selection previews the message; confirmation
sends native IRC `REDACT`. The server decides whether the account may delete it
(own messages, or other messages with channel moderator permission). A refused
request leaves the message visible. No optimistic removal or proprietary chat API
is used; older IRC clients may retain their displayed copy.
History is limited by server retention and the 200-line window; it is not a complete
offline archive. Networks without the required history capabilities keep live chat.
Rotation preserves navigation/drafts but cancels password entry. Default
server history expires after one week in cleanup batches. Current public privacy
details are at https://re-tui.pages.dev/privacy.html.

## Implementation and testing

`IrcClient` owns TLS, capability negotiation, SASL and framed IRC messages.
Guided signup uses Ergo's `draft/account-registration` capability, confirming a
server-origin success for the requested account before JOIN/device enrollment.
Unsupported external networks retain explicit NickServ commands rather than a
guessed registration fallback. Recheck draft capability compatibility on upgrades.

`IrcState` bounds buffers, lines, nickname lists and directory results. The Android
pane uses Launcher font/theme controls, raised headers, inner panes, shared chip
height, corner clearance and independent persisted expanded/floating geometry.

Run deterministic protocol/state/navigation fixtures with:

```
./gradlew :app:testPlaystoreDebugUnitTest
```

Device tests use loopback fixtures unless explicitly opted into real TLS/account
checks. Those checks require a separately supplied protected credential file;
credentials, VPS deployment scripts and run-specific reports are not source inputs
and must remain outside Git. Do not post production messages during verification.
