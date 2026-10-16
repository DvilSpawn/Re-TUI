# Android host identity publication and crash recovery

16 October 2026, 09:27 UTC. Read-only source review plus isolated runtime checks; no production files were edited by this review.

The kernel flock implementation fixes the directory-lock orphan problem found in review. All seven host Python fixtures passed at the review snapshot (including the subsequently removed experimental penalty-directive case); all six final fixtures pass, including killed lock owner retry and concurrent first publication. Existing Termux `flock` was also verified on the actual Android 15/API 35 emulator as util-linux 2.40.2.

The existing `retui_palette_api35` emulator was started without wiping its data. The exact current `setup.sh` asset and dispatcher ran as the actual official Termux app UID, under two newly created temporary HOME directories. Four concurrent first setup runs all succeeded and returned the same host identity. The resulting private key parsed successfully through the real Termux ssh-keygen. A separate kernel-lock holder was killed with SIGKILL before a retry; the real setup then created and validated a fresh host identity successfully. No packages were installed or removed. Both temporary directories were deleted afterward. The user's phone and its Termux data were untouched; the emulator remains running.

Evidence: [actual Android output](android-lock-check.log). These checks validate the current flock and rename path on Android app-private storage; they do not establish overnight OEM behavior or a 120-second full SSH lifecycle result.
