# Re-TUI community backend — API contract v3 (pilot)

## Live-delivery upgrade

The authenticated `GET /api/realtime` connection and `POST /api/realtime` subscription
allow only the exact custom `community` topic (or an empty unsubscribe list). Native
collection subscriptions, topic options, anonymous streams and cross-account connection
reuse are rejected. Tokens are revalidated against current accounts before each notice;
expired/revoked tokens, password rotation and inactive accounts cannot keep receiving it.

Create/update notices are `{action,collection,id}` and respect source view permissions.
General expiry is checked before publication. Deletes emit only `{action:"invalidate"}`:
no retained/expired message body or deleted-record metadata is serialized. REST reads
remain authoritative. Streams close after 60 seconds idle and reconnect with history
reconciliation; nginx has buffering disabled and a 90-second stream-specific timeout.
The public ingress still blocks dashboard, settings and private/native collections.

The generic PocketBase API fallback is 240 requests/minute, matching the proxy read
budget. Existing authentication, signup, posting and moderation quotas remain intact.
Stream requests have their own bucket and a 10-concurrent-stream/IP ceiling. Nginx
429 responses now advise 15 seconds rather than a blanket minute. Native limits may
still require a longer wait; shared-IP allowances are bounded, not unlimited.

`/var/log/nginx/retui-community-timing.log` records operation category, method, status,
request duration and upstream duration only. It does NOT contain IPs, handles, record
IDs, paths/queries, authorization, cookies or bodies. The existing nginx log rotation
retains 14 daily files; PocketBase request logging remains disabled. Long SSE durations
measure connection lifetime, not message latency.

`upgrade_live.py` is an existing-service-only, hash-checked upgrade. It prepares a
root-private persistent recovery snapshot, stops/restarts only `retui-community`, applies
the additive migration, preserves ACME configuration and account/forum IDs, validates
nginx and reloads it. Hook hot-reloading is disabled to prevent partial deploys. Failure
restores code/config without replacing the live database or rolling back accepted writes.
The separate Ergo IRC service is not changed.

`selective_backup.py --source /var/lib/retui-community/data/data.db --destination <new-private-file>`
creates a fresh 0600 SQLite database in a required 0700 directory, from a consistent
read-only transaction. All schemas/indexes are retained; only system configuration,
migration metadata, superusers, members, persistent threads and replies are copied.
General messages, reports/private requests, mentions and presence are EMPTY. Integrity
and reference checks run, and existing destination files cannot be overwritten. Snapshots
contain password hashes/auth secrets: keep root-only and encrypt before off-site transfer.
Hourly selective recovery and seven-day rotation are managed by the operations timer.
No off-site transfer is enabled. Restore only to a separate protected instance, never over
live data. Authorized legacy-data cleanup follows verification of a fresh recovery snapshot.

Regression tests: `test_realtime.py --binary <pinned-pocketbase>` includes live notices,
subscription restrictions, revocation, password rotation, denied-source metadata and
an actual reopened selective recovery snapshot. `test_selective_backup.py` verifies
transient body exclusion, private permissions and overwrite prevention.

PocketBase **0.40.5**, before 1.0: not a production-critical stability guarantee.
No IRC bridge. This independently isolated service does not use launcher/device inventory,
Google, OAuth, email, push, analytics, or any external identity service.

## Public-release gates (2026-10-10)

The additive `1791401000_release_gates.js` migration grants the existing `dvil` account
the sole moderator role and removes other moderator flags. All moderation reads/actions
require a current active member, stored moderator status and the canonical handle `dvil`.
Registration cannot grant roles. Role revocation/bans apply to existing tokens.

`POST /api/community/policy` accepts only `{version:"2026-10-10"}`. The server stores
the accepted version/time. Posting messages, discussions, replies, reports or private
requests without current acceptance fails **428**. Native member updates remain closed;
clients cannot forge the stored acceptance fields. The Android confirmation does not
automatically send a retained draft. Rules are public at `/community/rules`.

`POST /api/community/report` accepts `{collection,id,reason}` for messages, threads or
thread_replies. It creates private immutable evidence in moderator_requests, including
the source collection and original author ID. General expiry still applies. Evidence
retains its original six-calendar-month expiry even after removal/account deletion.

`GET /api/community/moderation?collection=reports|moderator_requests&page=1&perPage=50`
returns bounded unresolved private requests with source previews. Only dvil can use it.
The response includes privacy-safe operations health, never credentials or log bodies.
`POST /api/community/moderation/action` accepts `{queue,id,action}` with action
`dismiss`, `remove` or `ban`. It rechecks the role within its transaction and stores
resolution identity/time. Repeating an already resolved action does not act twice.
Ban uses the immutable original author ID, never a potentially reclaimed username;
self-ban is rejected. Native `/report` requests also retain the original author ID,
so action remains possible after their source chat is deleted or expires.
Removing a discussion also removes its replies. All other native
member/content writes stay inaccessible. Android exposes these actions with confirmation.

`POST /api/community/account-delete` accepts only `{confirm:"canonical_handle",password}`.
It checks the current password and deletes that account plus authored content in a
transaction; when a token is supplied, it must belong to the same account. It also works
without a session token, including for banned accounts, with the same strict rate limits.
Unknown accounts/wrong passwords return a generic **400**. Logout is not deletion.
The public `/community/account-deletion` page works without Launcher; credentials/tokens
are never placed in URLs, local storage or logs. Retained moderation evidence remains
until its original expiry. Forgotten passwords still require private operator verification.

`operations.py backup` creates hourly root-private selective snapshots with seven-day
rotation. Its named snapshots are reconciled against live account/thread/reply deletions;
the filename creation time controls expiry, not VACUUM/mtime. Backups exclude transient
chat, reports, requests, presence and mentions. Restore into an isolated instance first;
never replace live account data with an old snapshot. `operations.py monitor` checks local
and public HTTPS health, overdue retention, dvil-only moderation, >24-hour unresolved
requests, fresh recovery and disk space every five minutes. Failures are recorded in
the system journal and a role-protected in-app operator status. These are VPS-local checks,
not an independent external availability alarm. No off-site destination is configured.

`verify_recovery.py` verifies the newest snapshot against live persistent identities,
checks integrity and empty transient tables, then reopens a temporary protected loopback
copy with the deployed backend. It never overwrites live data and removes its test copy.
On 2026-10-10, the operator-approved two October 8 whole-database archives (and metadata)
and the v2 rollback `data.db`/`auxiliary.db` were removed after this verification. Code/config
rollback files and selective recovery snapshots were retained. This does not establish
forensic erasure of provider snapshots or storage free pages.

Operator routine: sign in as dvil, open Menu → Moderator review, inspect both queues and
resolve reports promptly (within 24 hours as a working target, not a guaranteed SLA).
For deletion support, privately verify ownership; never delete or transfer an identity
based on a public username alone. Update Play Console Data safety/content-rating/reviewer
access declarations and the published privacy policy before store submission.

Verified 2026-10-10: deployed PocketBase 0.40.5 and source hashes; original dvil ID
and the one account/discussion/reply preserved; only dvil has a moderator flag.
Both operations timers are enabled and active, with healthy repeated monitor results.
Six external HTTPS ingress checks pass, including public page MIME/CSP headers and
unauthenticated gate denial. The fresh production selective snapshot passes integrity,
identity and zero-transient-row checks and reopens in an isolated instance.
Local suites pass 22 backend contract, 20 realtime/recovery, three release-gate,
two selective-backup and one operations checks, plus the isolated nginx regression.
Android passes 90 Community host tests and a combined 16-test emulator regression run,
including the new rules/deletion/moderation flows. Normal Play debug assembles, and F-Droid debug/Play release Kotlin
compile. A matching-signer in-place update is installed on physical I2220 device
`10BE1C0FB40006A`; the app identity, first-install time and encrypted community-state
fingerprint are unchanged. All three gate flows pass there in the isolated fixture package,
without production posts or account deletion. No store publication is claimed.
Off-site recovery and independent alert delivery remain unset.

## General/identity API contract (v1-compatible)

Base: `https://irc.kokodoko.cloud/community` (no trailing slash).
All requests/responses JSON. Validate normal HTTPS certificates; never bypass validation.
Relation expansion is disabled globally: any `expand` query parameter (even empty,
repeated, nested or reverse) returns **400** `Relation expansion is disabled.` before
native queries or mutations, including superuser, collection-ID aliases and auth responses.
Read related records explicitly through their own retention/access-controlled endpoints;
SDK HTTP calls have the same restriction. Internal server model lookups are not HTTP APIs.
The UI label is **Username**; its API field is `handle`.

- **Open registration:** `POST /api/collections/members/records` with ONLY
  `{ "handle": "username", "password": "…", "passwordConfirm": "…" }`.
  Native success is **200**, returning the member record, not an auth token.
  Then perform password login. No email required or accepted; no approval gate.
  Handle: ASCII `[-_a-zA-Z0-9]`, 3–24 characters; stored lowercase, unique case-insensitively.
  Use the returned canonical lowercase handle for login/display. Creation forces `active=true`, `moderator=false`.
  Unexpected user-controlled fields (including active, moderator, email, verified, role, id) are rejected;
  PocketBase itself discards its private `tokenKey` field before request hooks.
- **Login:** `POST /api/collections/members/auth-with-password` with
  `{ "identity": "canonical_handle", "password": "…" }`.
  Native **200** response: `{ "token": "…", "record": { "id": "…", "handle": "…", "active": true, … } }`.
  Tokens last 86400 seconds. Optional native `POST /api/collections/members/auth-refresh`
  with the current token is allowed; expired tokens require login again.
- Auth header is `Authorization: TOKEN` (raw token, not a Bearer prefix).
- **Latest:** `GET /api/collections/messages/records?sort=-created,-id&perPage=50&page=1`.
  Native list shape: `{page,perPage,totalItems,totalPages,items:[…]}`.
  Message: `{id,created,updated,author,author_handle,body,client_id,…}`.
  `created`/`updated` are server UTC timestamps, e.g. `2026-10-07 19:00:00.000Z`.
  No sequence/seq field. IDs are opaque 15-character native PocketBase IDs.
- **Older:** same list path, `sort=-created,-id&perPage=50&page=1` plus URL-encoded
  `filter=(created < "CURSOR_CREATED") || (created = "CURSOR_CREATED" && id < "CURSOR_ID")`.
  Take the oldest item from the descending page as cursor. Preserve exact milliseconds and ID.
  Reject page sizes above 50. Render reversed if chronological UI is desired.
- **Post:** `POST /api/collections/messages/records` with ONLY
  `{ "author": "current_member_id", "body": "plain text", "client_id": "UUID" }`.
  Native **200** record response is authoritative. `author_handle` is set by the server;
  clients must NOT send it. Reject spoofed author, id, created/updated and unknown fields.
  Nonblank, at most 500 Unicode code points and 2000 UTF-8 bytes; preserve literal text.
  UUID string uses hexadecimal 8-4-4-4-12 layout. Keep one canonical UUID representation
  for retries; native unique `(author,client_id)` is case-sensitive.
  Duplicate creates return **400**, not an idempotency-specific HTTP status.
  Reconcile using the native list `filter=author="CURRENT_ID" && client_id="UUID"`.
  Never blindly retry uncertain acknowledgements or equate all 400 errors with duplicates.
- **Report:** `POST /api/collections/reports/records` with ONLY
  `{ "reporter": "current_member_id", "message": "message_id", "reason": "plain text" }`.
  Reason nonblank, maximum 500 code points / 2000 UTF-8 bytes.
  Native **200** response. Unique reporter/source-message identity; duplicates return **400**.
  Only the active dvil moderator may list/view reports; ordinary member access is forbidden.
  Native mutations and member list/view/update/delete remain loopback-superuser-only;
  the release-gate custom routes provide confirmed moderation and self-deletion.
- Member-authenticated active accounts can read feed; no anonymous reading/posting.
  Missing, invalid, expired or revoked auth on message listing returns **401**;
  banned account listing returns **403**. Single inaccessible message may return **404**.
  Invalid login is **400** or **403**; validation is **400**. Public mutation methods are blocked.
  Generic ingress **403/404/413/429** errors are JSON `{status,message,data:{}}`.
  Nginx 429 supplies `Retry-After: 60`; honor it and back off. Native 429 may not supply it.

### Deletions/cache

Latest refresh replaces/reconciles the latest window by server IDs, never only appends.
Messages absent from an authoritative refreshed window must not remain in that window.
Older cached pages are not covered by a latest-50 refresh: refresh their cursor ranges or
revalidate message IDs before presenting old cached content as current. Moderation deletes
return 404 on per-ID GET. Clear private cache on account change/sign-out. No deletion stream.

### Forgotten password

No recovery/reset service. Register a **new username** and continue with a distinct identity.
The old handle remains reserved; do not rename/delete/reassign real member records to free it,
reset passwords on request, or transfer identity/history ownership. Bans keep accounts intact
with `active=false`. Only disposable test fixtures are deleted by automated tests.

## Fixed-channel threads and private queue (v2)

The fixed registry is static: `general` uses the existing messages API; `bugs`, `features`
and `presets` use persistent threads/replies (`help` is added in v3). No channels collection, public mods channel
or standalone chat posting into persistent channels exists.

### Persistent thread API

- `POST /api/collections/threads/records`, ONLY
  `{"author":"CURRENT_ID","channel":"bugs","title":"Title","body":"Root text","client_id":"UUID"}`.
  Channel enum: exactly `bugs`, `features`, `presets`, `help` (v3). Native **200** record fields:
  `id,author,author_handle,channel,title,body,client_id,created,updated` plus collection metadata.
  Title nonblank, max 120 Unicode code points / 480 UTF-8 bytes; body same 500/2000 limit
  as messages. Unknown fields, timestamps and handle spoofing are rejected.
- List: `GET /api/collections/threads/records?sort=-created,-id&perPage=50`
  with URL-encoded `filter=channel="bugs"`. Combine author filters with parentheses.
  Older paging uses the messages descending `(created,id)` cursor. Per-ID GET is allowed.
- `POST /api/collections/thread_replies/records`, ONLY
  `{"author":"CURRENT_ID","thread":"EXISTING_THREAD_ID","body":"Reply","client_id":"UUID"}`.
  Native **200** fields: `id,author,author_handle,thread,body,client_id,created,updated`.
  Any active member may reply, not only the root creator. A parent is required; its channel
  determines reply placement. Server assigns the author's handle.
- Reply list: `GET /api/collections/thread_replies/records?sort=created,id&perPage=50`
  with URL-encoded `filter=thread="THREAD_ID"`. Forward cursor:
  `(created > "CURSOR_CREATED") || (created = "CURSOR_CREATED" && id > "CURSOR_ID")`.
  Per-ID GET and author filters are native. Maximum page size is 50.
- UUID syntax/duplicate handling are the same as messages. Unique `(author,client_id)`
  is enforced separately per collection. Reconcile uncertain delivery using signed-in
  author, UUID and body on the matching collection. Never release an uncertain UUID early.
- All reads/creates require an active member. No creator/moderator edit/delete in v2;
  only loopback superusers can moderate content in the original v2 API. Release gates
  add dvil-only custom moderation routes. Threads and replies have no timed expiry.
  Deleting a parent deletes its replies; administrative account deletion cascades authored
  messages/threads/replies. Moderation bans retain identities; password-confirmed
  owner-requested account deletion is supported by the release-gate custom route.

### Private moderator delivery

Only active members with accepted current rules can send; only the active dvil member with stored `moderator=true` (or
loopback superusers) can list/view. Roles are assigned only through loopback admin APIs;
registration cannot self-grant a role. Current stored roles/bans apply to existing tokens.
Moderators use their own member login, never the administrator secret. They are read-only:
no member management, queue mutation, reply or resolve workflow is implemented in v2.

- Existing `reports` create remains `{reporter,message,reason}`. It requires a nonexpired
  general message and snapshots its text/handle. Added server response fields:
  `source_message_id,source_reporter_id,snapshot_body,snapshot_author_handle,expires_at`.
  `message` and `reporter` are now optional noncascading live relations: clearing them
  cannot destroy evidence. Unique stable `(source_reporter_id,source_message_id)` preserves
  original reporter/message deduplication after deletion. Existing reports are backfilled.
- `/moderate` maps to `POST /api/collections/moderator_requests/records`, ONLY
  `{"reporter":"CURRENT_ID","kind":"moderate","body":"Private request"}`.
  Optional `message` names a fresh general message and triggers a server snapshot.
  `kind="report"` requires `message`; `kind="moderate"` does not. Body is nonblank,
  max 500 code points / 2000 bytes. Native **200** includes `id,reporter,kind,body,message,
  source_message_id,snapshot_body,snapshot_author_handle,created,updated,expires_at`.
  Unknown fields, IDs, snapshots and timestamps are server-controlled/rejected from clients.
  This API has no UUID reconciliation; do not blindly retry an uncertain private submission.
- Moderator reads: native `GET /api/collections/reports/records` and
  `GET /api/collections/moderator_requests/records`, `sort=created,id&perPage=50`, plus
  per-ID GETs. These are separate private queues, never exposed by public feed collections.
  Sender-only reads are not granted: ordinary list **403**, anonymous **401**, views **404**.
- Both private collections expire **six calendar months** after immutable creation in UTC,
  with end-of-month clamping, not 180 days. Aug 31 2023 → Feb 29 2024; Aug 31 2024 →
  Feb 28 2025; Dec 31 2024 → Jun 30 2025. Preserve time of day/milliseconds. Admin edits
  cannot move creation/snapshots or extend expiry from `updated`. Routine account/message
  deletion does not cascade private evidence; deliberate admin deletion can remove it early.
- General messages expire at age **>=24 hours** based on posting time, not channel-wide
  midnight or last update. Native lists/reconciliation immediately exclude expired and
  future-dated rows; per-ID GET is **404**, even before purge. Rules plus pre-query
  middleware and view hooks enforce this for ordinary and superuser APIs, including
  collection-ID aliases. Private expired content is similarly hidden immediately.

## Foreground community additions (v3)

The fixed registry adds persistent `help` threads/replies. `features` remains its wire
name; clients display **Suggestions**. Existing strict posting payloads, UUID duplicate
**400** reconciliation and six-calendar-month private queues are unchanged. No public
mods channel, IRC protocol or sleeping-phone push is implemented.

### Safe member directory

`GET /api/community/users?q=<fragment>&page=1&perPage=20` returns only
`{page,perPage,hasMore,items:[{id,handle,online}]}`. Authentication must be a current
active member, including stored bans on old tokens; anonymous/invalid tokens **401**,
inactive members **403**. Moderator status, roles, email, tokens, IP/device and last-seen
fields never appear. The native members list/view stays closed.

Case-insensitive ranking: exact, prefix, substring, then ordered subsequence (fuzzy),
with online preferred within equal rank, followed by canonical handle and ID. An empty
query sorts online first, then handle/ID. `q` is at most 24 ASCII handle characters;
underscore/hyphen are literal, not SQL wildcards. Queries are bound SQL parameters.
Exact/prefix candidates use the existing NOCASE handle index; substring/subsequence
candidates scan SQLite, not a downloaded member table. This pilot deliberately refuses
the directory with **503** above 10,000 identities; install a search index before growing
past that ceiling. Results are bounded in SQL to perPage+1; no general collection opening.
Maximum perPage **20**, page **1000**. Invalid/unknown/repeated parameters are **400**.
Search is not an account-recovery or handle-transfer mechanism; banned/deleted members
are absent. Debounce client input rather than download every member per keystroke.

### Foreground presence lease

`POST /api/community/presence` accepts only `{"active":true}` or `{"active":false}`.
Owner is inferred from the authenticated current member, never a client ID. True renews
a server-timed **90-second** lease and returns `{online:true,expires_at:"UTC timestamp"}`;
false deletes it and returns `{online:false,expires_at:""}`. Heartbeat about every 30
seconds only while the community surface is foreground; release on leave/sign-out.
Directory presence hides expired, future-issued and implausibly future-dated leases
immediately. The minute retention job removes expired leases, and account deletion
cascades them. There is no persistent last-seen history or device/IP tracking.

Presence has an independent native/nginx POST budget **120/min/IP** (nginx burst 30),
not the shared messages/threads/replies **20/min** budget. Directory and mentions use
the existing shared GET ingress budget **240/min/IP**. Shared NAT remains bounded.

### Validated mention metadata

The server extracts mentions from message/thread/reply **body** during transactional
creation, without changing accepted payload fields. Tokens are `@[-_a-z0-9]{3,24}`,
case-insensitive, with ASCII handle-token boundaries (not embedded in a handle/email,
and not a prefix of a longer handle). Canonical exact currently-active identities are
resolved once; repeated targets collapse, self-mentions and unknown/banned names are
ignored. More than **10 unique active non-author recipients** rejects the whole post
with **400**, never silently truncates or stores a partial post. UUID duplicates do not
create extra metadata. Mention targets are stable record IDs, never re-resolved when a
handle is deleted/recreated. Do not delete/reassign real accounts.

`GET /api/community/mentions?page=1&perPage=50` returns
`{page,perPage,hasMore,items:[{id,kind,source_id,thread_id,channel,author_handle,created,expires_at}]}`.
`kind` is exactly `message`, `thread`, or `thread_reply`; `source_id` is the exact source
record ID. General messages have `channel="general"`, `thread_id=""`, and expiry at
source creation +24 hours. Persistent roots/replies use the root channel and root ID,
with `expires_at=""`. `created` and `author_handle` come from the immutable server-owned
source snapshot. Items sort descending mention `(created,id)`; max perPage **50**, page
**1000**. No bodies are copied into the mention collection or returned by this endpoint;
read the original source through its own API. Only the authenticated active recipient
can read metadata and only while source/root view rules currently allow access. Expired
or future-created chat is immediately hidden before paging; source/parent/account deletion
cascades metadata physically. The ordinary native mentions/presence APIs remain closed,
and global relation-expansion rejection is unchanged. No moderation/private queue content
is mentioned or discoverable. This is a foreground poll, not a notification delivery promise.

Each request checks at most **1,000 recipient metadata rows** against the current source/root
rules, plus one metadata-only exhaustion probe. Page offsets count accessible items, not
raw rows. **200** with `hasMore=false` means the raw list was actually exhausted; a full
page with an accessible extra item returns `hasMore=true`. If more raw rows remain before
that answer can be established, the endpoint returns **503**
`{"status":503,"message":"Mention scan limit exceeded.","data":{}}`, never a partial
successful page or a fabricated end. This work ceiling also applies to valid deep pages;
page **1000** is a parameter limit, not a promise that every page can be served.
Treat this as a failed GET: retain only otherwise-valid stale cache, do not mark missing
mentions read, and do not advance the page. Back off before retrying the same request;
do not repeatedly walk pages or immediately retry to evade the scan budget. A later retry
can succeed after source access/metadata changes; persistent failures require operator
review or a permission-preserving indexed query, not client cursor/contract changes.

## Isolation and operations

Installed source/binary: `/opt/retui-community` (root-owned, service read-only).
Data/backups: `/var/lib/retui-community/data` (0700 hierarchy, service-owned, umask 0077).
Service: `/etc/systemd/system/retui-community.service`; distinct nologin user `retui-community`.
Loopback only `127.0.0.1:8095`, 256 MiB hard cap / 192 MiB Go soft limit, 50% CPU quota,
no privileges, protected filesystem/home/devices/kernel settings.
Public allowlist lives in existing `/etc/nginx/sites-available/irc.kokodoko.cloud` appended
HTTPS server; HTTP ACME block remains untouched. Proxy snippet:
`/etc/nginx/snippets/retui-community-proxy.conf`. Existing TLS certificate is reused.
Nginx strips `/community/`; only member signup/password login/refresh, named
messages/threads/thread_replies read/create/view, named reports/moderator_requests
read/create/view, and health paths reach the backend. Dashboard/setup,
superuser APIs, batch, SQL, backups, cron, OAuth, email/recovery, file uploads and unknown
paths are not public, including encoded dashboard paths. Body cap 8 KiB.

Nginx IP quotas: signup 5/min (burst 2), password auth/refresh 20/min (burst 5),
posts across messages/threads/replies 20/min aggregate (burst 5), reports/private requests
5/min aggregate (burst 2), and GET reads 240/min aggregate (burst 60).
Health and all named collection list/view GETs share the read bucket. Method-keyed
empty keys keep GET reads out of the POST bucket and POST creates out of the GET bucket.
The Android maximum of five refresh requests per 15 seconds costs 20 reads/min/client;
two clients behind one NAT cost 40 reads/min, below the aggregate read capacity. Quotas
remain per IP, not per account: other clients/reads behind that NAT also spend the same
read allowance, and posts behind it still share 20/min. This is not unlimited NAT support.
Backend native operation quotas remain unchanged; direct loopback administration is
excluded, and nginx **overwrites** X-Real-IP/X-Forwarded-For.
Privacy-safe timing logging is enabled for this HTTPS host; PocketBase request logs remain disabled.

Root-only generated administrator config: `/etc/retui-community/admin.json` (0600).
Never copy it to the app, print it, pass its password on command lines, or commit it.
No default/admin user password is distributed. Root-side maintenance programs read it
inside the process and use loopback native APIs. Administrative dashboard requires SSH
forwarding only, and the same credentials must be entered using a hidden local form:

```
ssh -i ~/.ssh/retui_hostinger_ed25519 -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes \
  -L 18095:127.0.0.1:8095 root@72.61.124.153
```

Then `http://127.0.0.1:18095/_/`. Admin dashboard must never be opened from Android.
The sole moderator dvil uses their member login and the custom review/action routes.
Only loopback superusers can change roles; the custom action route lets dvil remove
abusive content or ban its original author after confirmation.
Set active=false without deleting the account. Report snapshots do NOT cascade away with
message/account deletion. Administrator handles must not be used to reassign identities.

Posting kill switch: in local dashboard lock messages createRule to null; restore the
migration's active-account/author rule after review. Full feed kill switch:
`systemctl stop retui-community` (only this service). No game/IRC restart required.

### Retention/backups

Every minute: remove messages at age >=24 hours and reports/private requests at
`expires_at <= now`, in bounded batches of 100. Accounts and persistent threads/replies
have no timed purge. API expiry applies immediately before physical deletion, including
loopback superuser list/view APIs. Client filters cannot bypass the server cutoff.

**Scheduled whole-database backups are disabled** by a versioned migration: archives
would preserve expired message/request bodies. Tests no longer create production archives.
Selective local recovery snapshots run hourly with seven-day rotation and deletion
reconciliation; no offsite transfer is configured, so persistent accounts/threads still
have single-VPS loss risk. Legacy archives are NOT automatically deleted.
Inventory exact paths, sizes, permissions, timestamps and hashes; request explicit operator
approval before removing them. Legacy full-data copies are retention-policy exceptions;
new rollback directories contain code/config only. Do not claim full historical compliance until
those copies are resolved. Provider snapshots are outside application control.

Deletion means SQLite row removal, **not forensic secure erasure**: WAL/free pages and
filesystem/provider snapshots may retain bytes. Report snapshots intentionally retain
reported message text six calendar months beyond the source message's posting-time cutoff.
Operational failure can delay physical purge; monitor the service and retention job.
No production-critical readiness, secure-erasure or full historical retention claim is made.
Restore only to a separate protected loopback instance for inspection, never over live data.

The original migration remains unchanged. Additive migrations reproduce v2; do not rerun
`install.py` on the existing service. Stage and verify source hashes/current counts, protect
a local rollback copy, stop ONLY retui-community, run pinned `migrate up` as its service user,
restart only that service and read back schema/settings. Back up the nginx site, preserve
its HTTP ACME/TLS configuration, apply only the named community allowlist, `nginx -t`,
then reload nginx (not restart). Migrations have no destructive down path; do not roll back
newly accepted records. Never restart IRC, databases, containers, games or other services.

## Reproducibility / verification

New v3 real suite: `python3 server/community/test_v3.py --binary /path/to/pocketbase`
(**22 tests**, including all 14 prior regressions). `--tests 'test_1[4-9]*,test_20*'` selects the
seven additive v3 tests for bounded live batches. The additional `test_21*` is local-only:
1,000 permission-denied metadata rows before an accessible source must produce explicit
503, with exact-boundary exhaustion, deep-page failure and ordinary lookahead success
checked against real PocketBase. Its bulk SQLite fixture and temporary source view rule
are never run against an existing remote service. Disposable fixture accounts are the only
accounts ever deleted/recreated; permission-lock testing is local-only, not live schema mutation.

Additional real fixtures: `python3 server/community/test_migration.py --binary /path/to/pocketbase`
(1 preexisting-data upgrade test), and `test_nginx.py` (1 isolated nginx quota/allowlist test).
`test_https.py` has 5 credential-free public ingress tests. `RETUI_TEST_JOURNAL` can point
to a protected file recording only successful fixture collection/ID pairs for exact cleanup.
No password/token is journaled. Backend has 14 tests, including calendar leap/end-of-month,
snapshot survival, account deletion, current moderator role changes, inactive users,
thread/reply ownership and uniqueness, API expiry before purge and physical SQLite removal.
Native list request hooks execute after query: pre-query middleware preserves counts/paging.
Official syntax: https://pocketbase.io/docs/api-rules-and-filters/ ,
https://pocketbase.io/docs/js-routing/ and https://pocketbase.io/docs/js-migrations/ .

Linux amd64 release archive:
`https://github.com/pocketbase/pocketbase/releases/download/v0.40.4/pocketbase_0.40.4_linux_amd64.zip`
SHA256 `9042ec818570e79c3628dadcd0a756c1496d9e1173918ec409d133c02f82e5fa` (GitHub release digest and checksums).
Mac arm64 SHA256 `eeb619ea4f8a06421daedb946d133bed269fea334a760941d147f76befc25ebc`.
Binary/archive are not in the repository. Migrations and JS hooks reproduce the schema.
`install.py` refuses existing service/data, checks loopback port, preserves service/listener
snapshots and nginx backup under `/root/retui-community-install`, generates credentials
inside the protected process, applies migrations as service user, starts only feed, checks
local moderation/security/retention tests, then nginx -t/reload. Do not rerun it for upgrades.

Local real-backend suite (14 tests):
```
TMPDIR=/private/restricted/scratch python3 server/community/test_backend.py --binary /path/to/pocketbase
```
Existing VPS localhost or real public HTTPS suite (root, secrets loaded inside process):
```
TMPDIR=/root/retui-community-install python3 /opt/retui-community/test_backend.py \
  --base https://irc.kokodoko.cloud/community \
  --admin-file /etc/retui-community/admin.json \
  --data-dir /var/lib/retui-community/data
```
The suite generates disposable users/passwords in memory, retains exact IDs, removes
only its journaled exact record IDs in cleanup and verifies absent SQLite rows. It ages
only exact fixture ID/owner pairs in SQLite. No production backup is created. Public tests respect registration quotas and take several minutes.
`python3 server/community/test_https.py` performs credential-free TLS/allowlist/body-cap/port checks.
It also checks 40 consecutive unauthorized GETs return 401 and a subsequent empty,
unauthorized POST is rejected by the backend (400/401), not by nginx (429); no accounts
or messages are created. Allow the IP read bucket to drain before repeating this check.
`python3 server/community/test_nginx.py` requires nginx on PATH and runs the shipped
config in an isolated loopback fixture: reads cannot exhaust posting capacity, the post
quota still returns 429, and disallowed methods/paths stay blocked. It touches no service
configuration or production data. Authenticated public fixture tests require operator
permission; do not rerun them after approval expires or an approval request times out.

Remaining public-release work: choose off-site recovery and external alert delivery,
complete final release-device and longer network/soak checks, address the bounded-load
findings below, and update store declarations/published policy.
VPS-local monitoring does not detect total VPS loss independently. No claim of production-critical readiness. Native APIs and deployment
reference: https://pocketbase.io/docs/ and https://pocketbase.io/docs/going-to-production/.

## Bounded VPS load/stress verification (2026-10-11 IST)

`load_test.py` requires Linux systemd, root, nginx and the pinned PocketBase 0.40.5
binary. It cannot accept an existing database, live API base or account credentials:
every run uses a fresh private loopback fixture with synthetic accounts/history.
Use a private copy of the deployed hooks, migrations, public pages, nginx route/proxy
files and `selective_backup.py` alongside the harness. Do not run it against public
ingress. The trusted synthetic-IP header exists only in its private nginx instance.

Example with a new output filename and unused transient unit name:

```sh
sudo systemd-run --quiet --collect --wait --pipe --service-type=exec \
  --unit=retui-load-check -p CPUQuota=100% -p MemoryMax=768M \
  -p TasksMax=512 -p RuntimeMaxSec=600 -p Nice=10 -p IOWeight=10 \
  -p LimitNOFILE=4096 python3 /path/to/private-fixture/load_test.py \
  --binary /opt/retui-community/pocketbase --nginx /usr/sbin/nginx \
  --duration 20 --guard-production --output /path/to/private-fixture/new-report.json
```

Backend subprocesses use production-equivalent CPUQuota=50%, MemoryMax=256M,
GOMEMLIMIT=192MiB, TasksMax=128 and LimitNOFILE=4096. `--backend-cpu 100` changes
only the synthetic backend for a one-core comparison. Production service/configuration
is never changed. The guard sends a lightweight production health GET every two seconds
and stops further load on failure or a >1-second health response. Generated databases,
credentials and private server logs are removed; reports contain aggregate metrics.

Two completed VPS runs measured 4,559 and 4,818 HTTP requests respectively, plus
setup/SSE/probe calls, over 10/25/50/100-client 20-second stages. Synthetic history:
100 accounts, 2,000 messages, 200 discussions, 1,000 replies. The workload intentionally
uses two-second rotating reads and whole-feed live refreshes; it is more aggressive
than the launcher's periodic polling and is not an exact Android traffic simulation.
All 25 deployed hook/migration hashes matched the local checkout.

At production-equivalent limits, 100-client p95 read/message-post/live-notice latency
was 1.61/3.13/3.22 seconds. In the fixture-only one-core run it was 0.87/0.91/0.72 seconds.
Ten concurrent posts with retries and 100 listeners had successful-post p95
4.47 seconds versus 2.08 seconds. CPU quota throttling was observed. Peak cgroup memory
was 240.34/230.37 MiB out of 256 MiB; extra memory headroom has not yet been tested.
Ordinary stage post percentiles have only seven successful message samples per stage.
These are observed short-run results, not a capacity/SLA guarantee.

All 13 aggregate correctness checks pass for both completed reports: no ordinary
request failures, no missed live create notices, no duplicate same-UUID posts, successful
login/read bursts, clean 429 abuse rejection, the ten-stream/IP ceiling and quota isolation,
forced-process-crash recovery with retained message counts and SQLite integrity, and an
actually reopened selective snapshot with persistent identities/forums but no transient rows.
Production stayed healthy in all 71/56 guard samples; six fresh public ingress tests pass.
Production remains half-core/256M, dvil-only moderation is unchanged and no synthetic
production accounts or test services remain. No deployment, commit or release was made.

An exploratory five-second-deadline run recorded 59 transport timeouts in the 100-client
join stage; the completed runs use the launcher's actual 15-second read deadline and
recorded none. An earlier recovery-controller error was corrected and is not counted
as a completed recovery pass. Seven harness metric/isolation self-tests pass:
`python3 server/community/test_load_test.py`. The harness exits unsuccessfully for
incomplete or failed correctness checks; latency remains a measured result for review.
