# Third-party notices

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

The Launcher keeps the bundled artwork read-only and excludes its image payloads
from exported frame packs, presets, shareable configurations, and backups.

## Material Symbols Rounded

The compact unified-status controls include a subset of Material Symbols
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
This records that confirmation, not a verbatim grant or a change to the license
of the upstream repository. The full upstream license and required notice are
also included in the installed wallpaper page under Credits & license.

See [adaptation details](docs/nagomi.md).
