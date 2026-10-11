# Runtime notices and matching source

`runtime-libraries.json` records the resolved release runtime coordinates and
their notice files. Both release flavors currently use the same 72 external
components, including metadata/BOM entries, not 72 packaged binaries. Gradle's
`validateRuntimeNotices` fails if the graph changes or a referenced notice is
missing. Review licenses/source obligations before updating the inventory.

The existing notice-asset task includes the inventory, this document, source pins,
build patch and all `LICENSES/` texts in APK/AAB assets under `licenses/`.
Tabler and the modified libsuperuser port have component notices in
`THIRD_PARTY_NOTICES.md`. CompareString2, SLF4J, JDOM and jsoup notices retain their
upstream grants; LuaJ, HtmlCleaner and ASM retain distinct legal source headers
from their exact published source JARs. Apache components use the full Apache-2.0
text, with upstream links and versions in the inventory. OkHttp's Public Suffix
List resource separately retains its MPL license and original source-access
notice; it is not simply relicensed under Apache or GPL.

## Tasker Plugin Library 0.4.10

Source: https://github.com/joaomgcd/TaskerPluginSample
Author: João Dias and contributors. License: GPLv3, in `LICENSES/Tasker-GPL-3.0.txt`.

`sources.json` pins the full source/build archive at revision
`ba80eb174cfef264ad9e77d7481dd040ebd74f8c` and the exact Maven Central 0.4.10
sources JAR, with SHA-256 hashes. All 32 Kotlin/Java source files match after
CRLF-to-LF normalization. A later default-branch revision changes library behavior
and must not be substituted as the matching release source.

The matching source commit still labels its publication metadata `0.4.9`.
`taskerpluginlibrary-0.4.10-build.patch` changes only that publication-version
label to the version of the matched published source, not library behavior.
Apply it after unpacking the source archive:

```sh
tar -xf taskerpluginlibrary-0.4.10-source.tar.gz
cd TaskerPluginSample-ba80eb174cfef264ad9e77d7481dd040ebd74f8c
patch -p1 < /path/to/retui-source/third_party/taskerpluginlibrary-0.4.10-build.patch
ANDROID_HOME=/path/to/android-sdk JAVA_HOME=/path/to/jdk17 \
  bash ./gradlew :taskerpluginlibrary:assembleRelease
```

Upstream build inputs are Gradle 8.0, Android Gradle Plugin 8.1.2, Kotlin 1.9.10
and Android SDK 34. Only assemble the library; publishing/signing tasks are not
needed. No production signing credentials should be added. This is source/build
material delivery, not a claim of byte-identical signed binaries.

## Bundle generation

After the final app build, without editing sources in between:

```sh
python3 scripts/qalculate_source_bundle.py \
  --apk app/build/outputs/apk/playstore/release/app-playstore-release.apk \
  --aab app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab \
  --output app/build/outputs/qalculate/retui-source.tar.gz
```

The tool downloads/caches the two checksum-pinned Tasker archives when necessary,
verifies source-file equivalence and required license/build files, then includes
them under `third_party/upstream/` along with the native archives and app source.
The manifest records their pins and comparison results alongside binary hashes.
Already bundled archives can be reused without downloading them again.

Publish the matching source bundle with clear durable access instructions next
to the binary only after the separate publication approval. Source packaging
does not resolve artwork permissions, historical Lucida redistribution, or legal
clearance of all release source obligations.
