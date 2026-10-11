# Third-party notices

## Android runtime libraries

The pinned runtime inventory is `third_party/runtime-libraries.json`, including
transitive components and metadata/BOM entries. Full texts and source copyright
notices for CompareString2 1.0.9 (Francesco Andreuzzi, 2019), LuaJ 3.0.1,
SLF4J 2.0.17 (QOS.ch, 2004-2022), HtmlCleaner 2.29, JDOM 2.0.6.1, ASM 9.7.1
and jsoup 1.22.1 are in the corresponding `LICENSES/` files. LuaJ, HtmlCleaner
and ASM notices retain the distinct copyright headers from their exact published
source JARs; source filenames identify their origin.

AndroidX/Android Open Source Project, JetBrains Kotlin/coroutines/annotations,
Google Guava/ZXing, Square OkHttp/Okio, JsonPath/json-smart and JSpecify runtime
components use Apache-2.0; the full text is in
`LICENSES/Material-Symbols-Apache-2.0.txt`. The manifest gives exact coordinates
and upstream links. OkHttp's Public Suffix List data separately retains MPL-2.0
and its source-access notice in `LICENSES/Public-Suffix-*`.

The existing notice asset bundle packages these texts, the runtime inventory
and third-party source instructions with both APK and AAB builds. Build validation
rejects a changed release dependency graph until its notice inventory is reviewed.
An inventory entry is not a grant to relicense third-party components.

## Tasker Plugin Library

Tasker Plugin Library 0.4.10, João Dias and contributors, is included under GPLv3.
See `LICENSES/Tasker-GPL-3.0.txt` and `third_party/README.md` for matching source
and build-material delivery. The source-bundle tool verifies the pinned upstream
revision against all 32 source files in the exact published sources JAR.

Source: https://github.com/joaomgcd/TaskerPluginSample

## Tabler Icons

The 11 `remember_ic_tree_*` vectors with Tabler source comments adapt the outline
icons folder, folder-plus, file-text, trash, share, plus, check, x, chevron-right,
chevron-down and edit. Re:TUI converted their SVG paths to Android vector XML.

Copyright (c) 2020-2026 Paweł Kuna. MIT License; full copyright and grant:
`LICENSES/Tabler-MIT.txt`. Source: https://github.com/tabler/tabler-icons

## libsuperuser

The inherited `Shell` and `StreamGobbler` implementation was ported to Kotlin
and adapted for Re:TUI; `ShellHolder` is the Launcher's integration adapter.
Original libsuperuser copyright: Jorrit "Chainfire" Jongma and contributors.
The original Shell notice names 2012-2015; current upstream StreamGobbler names
2012-2019. Copyright headers remain in the modified files.

Apache-2.0; full license in `LICENSES/Material-Symbols-Apache-2.0.txt`.
Source: https://github.com/Chainfire/libsuperuser

## JetBrains Mono Nerd Font Regular

The default bundled typeface is JetBrains Mono Nerd Font Regular: JetBrains
Mono 2.304, patched by Nerd Fonts v3.5.1. The font is bundled unmodified under
SIL Open Font License 1.1; commercial use and redistribution with the required
notices are permitted. The font retains its own license, not the code's GPL.

Copyright 2020 The JetBrains Mono Project Authors. Nerd Fonts contributions:
Ryan L McIntyre and contributors. The patch includes additional icon sets;
their attribution, licenses and exact download provenance are preserved in
`LICENSES/JetBrainsMonoNerdFont/`. These notices are also packaged in the app.
Font Logos 1.3.0 includes an Unlicense grant despite the release README's
outdated "unlicensed" label. Brand glyphs do not grant trademark rights.

Sources: https://github.com/JetBrains/JetBrainsMono and
https://github.com/ryanoasis/nerd-fonts/releases/tag/v3.5.1.

This replaces the inherited Lucida Console asset in current builds; removing
that asset does not remove it from earlier Git revisions or published binaries.

## Maintainer-authored sounds and imagery

On 2026-10-11 the maintainer confirmed the bundled launcher WAV sounds and
Re:TUI branding/avatar/podcast images are their own creations. Their authorship
record and its limits are in `docs/asset-provenance.md`; project-authored assets
are covered by the project's GPL-3.0-or-later license. This does not establish
authorship of inherited action icons, Notes formatting icons or third-party logos,
and does not grant trademark rights.

## Google Material Symbols

The `open_in_full_24` and `pip_24` window-action vectors adapt Google's outlined
Material Symbols `open_in_full` and `picture_in_picture` SVG paths. The SVG
coordinate origin is translated into an Android vector viewport; the paths are
otherwise unchanged.

Source: https://github.com/google/material-design-icons/tree/master/symbols/web
License: Apache-2.0; see `LICENSES/Material-Symbols-Apache-2.0.txt`.

## Qalculate native engine

Re:TUI uses an independently authored GPL-3.0-or-later JNI bridge and native build
recipe. Jost Herkenhoff's Android wrapper, generated bindings and Maven artifact
are no longer included. All native inputs are pinned in `native/qalculate/sources.json`.

- **libqalculate 5.8.2**, Hanna Knutsson and contributors: GPL-2.0-or-later;
  distributed here under the GPLv3 option. https://github.com/Qalculate/libqalculate
  See `LICENSES/Qalculate-GPL-2.0.txt` and the source headers.
- **GMP 6.3.0**, Free Software Foundation and contributors: LGPL-3.0-or-later OR
  GPL-2.0-or-later; using the GPLv3 option. https://gmplib.org/
  See `LICENSES/GMP-GPL-3.0.txt` and the original archive's notices.
- **MPFR 4.2.2**, Free Software Foundation and contributors: LGPL-3.0-or-later;
  combined under GPLv3 as permitted by LGPLv3 section 3. https://www.mpfr.org/
  See `LICENSES/MPFR-LGPL-3.0.txt` and the project's GPLv3 text in `LICENSE`.
- **libxml2 2.15.1**, Daniel Veillard and the Libxml2 contributors: MIT-style
  license, with per-file notices retained in the source archive.
  https://gitlab.gnome.org/GNOME/libxml2 See `LICENSES/libxml2-MIT.txt`.
- **LLVM libc++ runtime**, from Android NDK 27.1.12297006: Apache-2.0 with LLVM
  exceptions. See `LICENSES/LLVM-Apache-2.0-with-exceptions.txt` and the complete
  pinned NDK notices in `LICENSES/Android-NDK-NOTICE.toolchain.txt` (including
  earlier runtime notices). https://github.com/llvm/llvm-project/tree/main/libcxx

The original MIT notice for Re:TUI is retained in `LICENSES/MIT-original.txt`.
Neither the project license nor this bridge changes third-party artwork terms.

See `native/qalculate/README.md` for exact build inputs, our Android cancellation
patch and source-bundle generation. Distribute the matching complete source and
build materials with a public release; optional runtime activation does not
remove source obligations for the native engine packaged in the APK.

## CraftPix sky with clouds

The Clouds wallpaper uses the original PNG layers of all eight skies from CraftPix's
Free Sky with Clouds Background Pixel Art Set, supplied by the user.

License: https://craftpix.net/file-licenses/ (Freebie Products).
The artwork is bundled for in-app rendering; it is not offered for image export.
These images remain subject to the CraftPix license, not the repository's code license.
Source: https://craftpix.net/freebies/free-sky-with-clouds-background-pixel-art-set/.
The 2026-10-11 review in `docs/clouds-licensing.md` supports app use but leaves
raw public-Git redistribution unresolved; the separate four-background itch.io
listing does not establish additional permissions for this eight-sky set.

## csakura

The Re:T-UI wallpaper renderer is an Android Canvas port of csakura by
realstrawhat, based on upstream revision
`9664fcbffac096acdb44cbc8c81527fb57d13639`.

Source: https://github.com/realstrawhat/csakura

MIT License

Copyright (c) 2026 realstrawhat

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## topowall

The Re:TUI Topo Noise wallpaper adapts topowall's contour-wallpaper concept
to a local seeded noise heightmap and Android Canvas renderer.

Source: https://github.com/gonzalezerik/topowall

MIT License

Copyright (c) 2026 Erik Gonzalez

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Sprout Lands Premium UI Pack

The built-in **Sprout Lands — Art by Cup Nooble** frame pack contains modified
UI assets from the [Sprout Lands UI Pack](https://cupnooble.itch.io/sprout-lands-ui-pack).

Copyright and artwork credit: **Cup Nooble**.

The premium-pack terms supplied with the artwork allow modification and use in
commercial and non-commercial software. They prohibit NFTs, AI training,
reselling, or redistributing the asset pack itself. Projects made with the
assets may be distributed and may be open source when this attribution and the
licensing terms are included.

The original downloaded Premium UI Pack readme is preserved in
`LICENSES/Sprout-Lands-Premium-UI-Readme.txt` (line endings normalized).
Its explicit open-source-project permission was verified on 2026-10-11;
provenance and original-file checksum are recorded in `docs/asset-provenance.md`.

The Launcher keeps the bundled artwork read-only and excludes its image payloads
from exported frame packs, presets, shareable configurations, and backups.

## Material Symbols Rounded

The compact unified-status controls and calculator tabs include a subset of Material Symbols
Rounded by Google LLC.

Source: https://github.com/google/material-design-icons

Licensed under the Apache License, Version 2.0:
https://www.apache.org/licenses/LICENSE-2.0

## Nagomi

Copyright 2026 Mayank Kadam (https://github.com/msk1039).

Source: https://github.com/msk1039/nagomi
Demo: https://nagomi-blue.vercel.app/
Revision: `01e93a410c0a317ee0a2b81e84a71e54a6db81d0`

The Nagomi wallpaper adapts the original koi simulation, spine/body geometry,
markings, palettes, small-fish schooling, lotus leaves and flowers, duckweed,
water shader and pond-bed appearance for Android. Re:TUI adds
notification-driven fish, local photo backgrounds and native wallpaper lifecycle
handling. The settings page links to both the source and original experience.

Upstream uses [PolyForm Noncommercial 1.0.0](LICENSES/Nagomi-PolyForm-Noncommercial.txt).
The Re:TUI maintainer confirmed additional author permission on 2026-09-29 for
this adaptation in the paid Google Play and free GPL-3.0-or-later distributions.
On 2026-10-11 the maintainer confirmed the explicit verbal permission also covers
other people modifying and redistributing the adaptation, including commercial
GPL forks, for the adapted code and artwork described above.
This records that confirmation, not a verbatim grant or a change to the license
of the upstream repository. The full upstream license and required notice are
also included in the installed wallpaper page under Credits & license.

See [adaptation details](docs/nagomi.md).
