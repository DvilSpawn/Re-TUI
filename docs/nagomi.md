# Nagomi wallpaper

Source: https://github.com/msk1039/nagomi
Original experience: https://nagomi-blue.vercel.app/
Author: Mayank Kadam, copyright 2026.
Based on revision `01e93a410c0a317ee0a2b81e84a71e54a6db81d0`.

## Permission and credit

The project maintainer confirmed the author granted permission for the paid
Google Play and free GPL-3.0-or-later Re:TUI distributions on 2026-09-29 and
instructed implementation. The upstream PolyForm license is retained in
`LICENSES/Nagomi-PolyForm-Noncommercial.txt` and the installed credits dialog.
This record describes the maintainer's confirmation; it does not fabricate a
verbatim grant or claim upstream changed its public license. Retain the full
author correspondence with the project's permission records.

GitHub and demo links appear on the Nagomi wallpaper page. Any future author
app link should be added once supplied/verified; no speculative redirect exists.

## Adaptation

- `NagomiSchool`: Kotlin adaptation of `koi.ts`, `school.ts`, `math.ts`.
  Retains the five swimming states, neighbor steering, depth, feeding, 14-node
  constrained spine, body parameters and smooth turn/speed response. Removes
  visitor input and uses Kotlin's seeded RNG instead of XorShift32.
- `NagomiPainter`: Canvas adaptation of body/fin geometry, procedural markings,
  palettes and depth coloration from `fish-renderer.ts`, `fish-appearance.ts`
  and `settings/definition.ts`.
- `NagomiWater`: original GLSL cellular currents/refraction adapted for GLES2,
  eight spawn/feeding ripples and Android bitmap texture orientation. Omits the
  separate fish-depth distortion pass.
- Bundled pond-bed PNG derives its colors/grain/edge shading from `pond-bed.ts`
  and the original defaults. Custom photos replace this layer with a center crop.
- `NagomiTinyFish`: the original three visible schools (24 gold, 15 blue,
  34 pink fish), palettes and silhouettes from `tiny-fish.ts` and
  `tiny-fish-renderer.ts`. Retains separation, alignment, cohesion, swirl, wander,
  bounded turning and speed variation. Uses the shared fixed simulation clock,
  Kotlin RNG and a hard boundary guard; omits pointer-triggered fleeing.
- `NagomiPlants`: the 15 visible lotus leaves, four flowers and eight duckweed
  patches from `lotus-leaves.ts`, `duckweed.ts`, `duckweed-geometry.ts` and
  `settings/definition.ts`. Retains original placements, geometry, palettes,
  shadows and drift/sway formulas. Geometry is cached as small pixel sprites;
  duckweed moves as patches without the upstream per-leaf ripple response.
  Placement adapts to portrait or landscape without stretching leaf shapes.
- Fixed atmosphere. No web editor, analytics, network content, rain, butterflies
  or weather presets are bundled in this adaptation.

The scene has a dedicated EGL wallpaper service, leaving existing Canvas scenes
on their current service. Picker and applied wallpaper share the same renderer.
Fish/shadows are painted into a reused bitmap, refracted at low resolution by
the original water shader. Lotus shadows are below the fish; floating plants
are composited above the water before the finished pond is enlarged with
nearest-neighbor sampling, matching
upstream's CSS pixelated/crisp-edges display. Fish geometry is not antialiased,
matching upstream's renderer. At the user's request, underwater refraction,
surface compositing and background resizing also use nearest-neighbor sampling
instead of upstream's linear underwater filtering. This keeps source texel edges
sharp; it does not increase the pond's internal resolution or restore detail
already lost in an imported image.
Short edge targets 270
pixels and long edge is capped at 640. Rendering targets 30 fps, simulation 60 Hz.
Visibility/screen interaction gates stop frame scheduling; resuming resets the
clock so hidden time is not simulated.

## Notifications and images

Six ambient koi keep the pond alive. Each new non-ongoing, non-group-summary
notification adds a koi using cached app-icon colors (notification accent is the
fallback). Notification keys suppress updates and reconnect seeding. Removed
notifications do not remove their fish. No notification text or actions reach the
pond. Fish live for 180 seconds of animation time; population is capped at 24,
replacing the oldest notification fish at capacity. A bounded in-memory event
list retains recent signals for five minutes while the surface is unavailable.
Process death resets the pond; it does not replay notification history.

Image imports accept at most 32 MB, subsample to a maximum 2048-pixel edge,
apply EXIF orientation, and store a private JPEG. Import/reset only alters the
preview until Use on phone; applying uses an atomic file replacement. The original
photo is never changed and need not remain available afterward. User images are
not bundled or uploaded. Pending imports use cache files.

## Validation

See `NagomiSchoolTest` for population, notification identity, deterministic
fixed-step motion, bounds and pause checks. Device instrumentation additionally
checks GLES rendering, image import/apply/reset, preview links and lifecycle.

On 2026-09-29, all four instrumentation tests passed on the connected I2220
phone, including a real OS notification delivered through Re:TUI's listener and
update deduplication. The matching-signature debug APK was installed as an update
without clearing app data. Nagomi was applied to home and lock screens.
SurfaceFlinger wallpaper-layer counts over three-second samples advanced
545→617 at home and 674→744 after returning, stayed at 623 while Settings was
open and at 747 with the screen off, and advanced 793→867 on the visible lock
screen. Observed animation was about 22–24 fps on this device; the 30 fps target
is not a measured guarantee. No extended battery/thermal run was performed.

Pixel-edge regression: the original smoothed phone build preserved only
33,182/114,750 sampled adjacent pixels inside enlarged texels. After restoring
upstream-style crisp scaling and disabling geometry antialiasing, the same
on-device assertion passed its 98% threshold, with all four instrumentation
tests passing again. This checks actual rendered pixels, not just filter flags.

The vegetation/small-fish pass passed four JVM tests and all five phone
instrumentation tests, including portrait/landscape plant placement, visible
flowers, drift, crisp pixel scaling and notification spawning. Play Store and
F-Droid debug builds passed. The applied wallpaper with all new layers measured
30.12 fps over a ten-second home-screen sample after making the frame delay
account for drawing time. Frame count stayed at 304 for the three-second
Settings check, then increased 364→456 over three seconds after returning home.
The same layer pass also held its frame count unchanged with the screen off
(307→307) before that scheduling adjustment. Custom-photo data was preserved.
Butterflies, weather and individual duckweed ripple reactions remain outside
this pass; no long-duration battery claim is made.

The subsequent nearest-neighbor pass explicitly disables both Android Paint
antialiasing and bitmap filtering (modern Android enables them in `Paint()`),
and sets both GPU textures' min/mag filters to nearest. A black/white checker
regression failed on the prior phone build because background resizing added
gray pixels, then passed with the explicit flags. All six phone instrumentation
tests and both distribution debug builds passed. The prior enlargement-only
pixel check did not detect smoothing already baked into individual pond texels.
