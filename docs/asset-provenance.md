# Asset provenance and redistribution boundaries

Recorded 2026-10-11 from the project maintainer's direct confirmations during
the public-source/license audit. These are maintainer attestations, not invented
creator correspondence or independent verification of original editing files.

## Maintainer-authored assets

The maintainer confirmed the bundled launcher sounds and branding/avatar/podcast
images are their own creations. The record covers:

- All 12 `app/src/main/res/raw/*.wav` sounds: `alarm_loop`, `boot`, `cancel`,
  `click`, `confirm`, `error`, `notification`, `processing_loop`, `reminder`,
  `shutdown`, `success` and `timer_loop`.
- Re:TUI's `app_logo.png`, `app/src/main/res/drawable/profile_avatar.png` and
  `app/src/main/res/drawable/podcast_24.png`.

The five current `app/src/main/res/mipmap-*/ic_launcher.png` images are Re:TUI's
skull branding, updated by commit `c443cb7` on 2026-07-22, not the original T-UI
artwork. They fall within the maintainer's branding-authorship confirmation.
The baseline audit's first-path-introduction date did not identify the origin of
their current bytes; it must not be mistaken for an unchanged inherited bitmap.

These project-authored assets use the project's GPL-3.0-or-later license in
`LICENSE`. Copyright/trademark rights are distinct; this is not blanket permission
to impersonate the project or use third-party branding.

## Nagomi adaptation

The maintainer confirms explicit verbal author permission for the paid/free
Re:TUI adaptation and for other people modifying and redistributing it, including
commercial GPL forks. See [the dated permission record](nagomi.md) for its adapted
code/artwork scope and upstream credit. The separate upstream project retains its
PolyForm Noncommercial license; this record does not change that license.

## Separate unresolved or conditional boundaries

- CraftPix clouds follow-up: completed at the maintainer's direction on
  2026-10-12; assets remain bundled and creator coordination is maintainer-owned.
  See the [review and dated closure](clouds-licensing.md). This attestation does
  not invent independently verified correspondence or GPL asset sublicensing.
- Sprout's paid purchase is maintainer-confirmed. Both the supplied Sprites premium
  readme and the exact downloaded Premium UI Pack readme explicitly permit
  open-source projects with credit and terms. The UI readme is preserved in
  `LICENSES/Sprout-Lands-Premium-UI-Readme.txt`, with CRLF normalized to LF.
  Original UI readme SHA-256:
  `9df888a47a656f104af28a06b53a124eb9b4f1efe98af6dc930aa65ebfc7e7d6`.
  Original supplied Sprites readme SHA-256:
  `881ea89c5fd735ef5755e32202722f8cbe1b26146eefa121b0a9323b1693d27e`.
  This is project-use permission, not GPL relicensing or standalone pack resale.
- The 20 inherited toolbar PNGs are `ic_back`, `ic_clear`, `ic_enter`, `ic_next`
  and `ic_paste`, each in `drawable-hdpi`, `mdpi`, `xhdpi` and `xxhdpi`.
  All match upstream revision `058f54116c4125ee72732a31d0f83e58c30534ad`
  byte-for-byte. That upstream's MIT license exactly matches the preserved
  `LICENSES/MIT-original.txt`. This establishes their repository/license trail,
  not independent proof that upstream created every pixel or had no third-party
  obligations. They are not established infringements requiring automatic deletion.
- Current icon provenance is resolved by replacement on 2026-10-12. The 13
  unverified Notes formatting PNGs are removed; their resource names now use
  documented Apache-2.0 Google Material Symbols vectors. All 66 current Material
  vectors have exact source URLs/SVG checksums at revision
  `49d4db35df873165d6bd6ba09b063c7dafbac2f4` in
  `third_party/material-icons.json`, including Android output checksums.
  Existing resource names, intrinsic dimensions and the resize-arrow rotation
  wrapper are retained. GitHub/Discord/Reddit support actions use neutral code/
  chat/forum symbols, not unverified brand logos; destinations and accessible
  descriptions are unchanged.
- The wordmark and two simple tree connectors are independently authored geometric
  paths under GPL-3.0-or-later, with no font outlines. Their exact output hashes are
  in `third_party/project-vector-icons.json`. Together with the 11 MIT Tabler
  vectors, every current vector is classified and checksum-checked. This resolves
  current assets, not the origin/rights of replaced files in old revisions.
- Lucida Console is absent from current builds but remains in earlier Git
  revisions/releases. On 2026-10-12 the maintainer chose to keep its handling as a
  separate historical follow-up. No history rewrite, old-release deletion or
  retroactive font grant is implied.

Contributors should retain per-component notices and record the origin, license
and modifications of new assets. The code's GPL does not grant rights to every
third-party asset. A technical build or source bundle is not blanket licensing
clearance for the entire repository.
