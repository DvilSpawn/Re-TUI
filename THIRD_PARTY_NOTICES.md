# Third-party notices

## Qalculate Android library

The optional Qalculate calculator uses `com.jherkenhoff:libqalculate:5.8.2-2`,
Jost Herkenhoff's Android port of libqalculate 5.8.2 by Hanna Knutsson and contributors.
The native libraries are bundled even when the calculator toggle is off.

- Android wrapper and native build sources: https://github.com/jherkenhoff/libqalculate-android
- Core sources: https://github.com/Qalculate/libqalculate/tree/v5.8.2
- Wrapper license declared by the published artifact: GNU GPL version 2.
  https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
- The wrapper also bundles GMP, MPFR, libiconv, libxml2 and LLVM's C++ runtime;
  their respective licenses and build sources are referenced in the wrapper repository.

The project code is now GPL-3.0-or-later; the original MIT notice is retained
verbatim in `LICENSES/MIT-original.txt`. Neither license changes third-party terms.
Core libqalculate grants GPL-2.0-or-later, but the Android wrapper declares only
GPL-2.0 in its published metadata. Its license text is included in
`LICENSES/Qalculate-wrapper-GPL-2.0.txt`; an explicit later-version grant has not
been verified. This is a release blocker with Apache-2.0 dependencies, not a
resolved compatibility claim. See `docs/calculator.md` for source-delivery and
license requirements before public distribution.

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
