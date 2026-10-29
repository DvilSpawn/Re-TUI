# Native IRC

`irc` opens an IRC client that talks standard IRC over verified TLS to any network,
several at once. Re:TUI's own server (`irc.kokodoko.cloud`) has been retired; the
first run offers Libera.Chat, and **Networks → Add a network** lists OFTC, Rizon
and hackint or takes any `hostname [TLS port]`.

## Networks

Each saved network keeps its own nickname, account name, windows and the channels
you were in, in no-backup app storage (`irc-networks.json`). Passwords are never
stored. The first window chip names the current network (`⇄ Libera.Chat`); its
marker counts unread messages and mentions on the other networks, and tapping it
opens **Networks**. `/network` lists networks, `/network add host [port]` adds one,
`/network next|prev|name` and Alt+←/→ switch. Switching keeps every network
connected and each network's drafts and reading positions.

A network rejoins its channels whenever it connects. Networks that were connected
when the pane closed reconnect when it opens again, if they need no password
(guest, or a remembered device). A working connection that drops reconnects by
itself after 5, 15, 30, 60, 120 and 300 seconds, then stops. Connecting tries the
other address family when the first one fails, so IPv4-only phones still reach
networks that publish IPv6 addresses.

## Accounts and everyday use

On a network's home, **Connect as …** joins as a guest, **Sign in** takes an
account name followed by a masked password in the same composer, and **Create
account** uses in-app signup where the network supports it (Ergo's
`draft/account-registration`). Elsewhere, connect first and use `/register
[email]`, which sends a masked password to NickServ. Signing in uses SASL; a network
that offers no SASL at all (OFTC) receives `NickServ IDENTIFY` after the welcome
instead. A network that offers SASL never falls back to NickServ or guest access.

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

## Remembered devices, sending and privacy

Optional **Remember this device** (on by default for new accounts) creates a
non-exportable Android Keystore key for that network account and asks NickServ to
trust it (`CERT ADD`). Later connections present the key and sign in with SASL
EXTERNAL, or are matched by services on networks without SASL. Passwords are not
saved. This is not a portable restore code: reinstalling, clearing data or using
a new phone requires password sign-in again.

**Forget this device** deletes the local key. The network may still list the
certificate, but it can no longer be used; remove it with NickServ `CERT DEL` from
a signed-in client if wanted. Forgetting a device does not delete the account.
Removing a network also deletes its key. Upgrading from a build that used the
retired server deletes that server's key and shows a one-time note.

Messages appear immediately with pending/server-echo state. Echo confirms server
acceptance, not delivery or reading. Uncertain sends are not automatically retried.
TLS is not end-to-end encryption: server operators can read messages and see
connection addresses.

Back first leaves temporary navigation, then closes the pane and disconnects every network.
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
phone's display; deletion on IRC does not remove copies relayed elsewhere.
On a supported connection, **More → Delete a message** (or `/delete`) lists
server-confirmed channel messages. Selection previews the message; confirmation
sends native IRC `REDACT`. The server decides whether the account may delete it
(own messages, or other messages with channel moderator permission). A refused
request leaves the message visible. No optimistic removal or proprietary chat API
is used; older IRC clients may retain their displayed copy.
History is limited by server retention and the 200-line window; it is not a complete
offline archive. Networks without the required history capabilities keep live chat.
Rotation preserves navigation/drafts but cancels password entry. History retention
is each network's own policy.

## Implementation and testing

`IrcClient` owns TLS, capability negotiation, SASL and framed IRC messages.
Guided signup uses Ergo's `draft/account-registration` capability, confirming a
server-origin success for the requested account before JOIN/device enrollment.
Other networks use explicit NickServ registration rather than a guessed
registration fallback. Recheck draft capability compatibility on upgrades.
`IrcConnection` owns one network (rejoin list, reconnect, device sign-in);
`IrcTerminalSession` holds every saved network and which one the pane shows.

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
