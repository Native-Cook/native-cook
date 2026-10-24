# Artwork provenance and runtime inventory

This private reference study follows the owner's Swiggy BuzzStreaks reference and explicit authorization to use source tracing, generated replacements and small text-motion primitives. Source artwork and UI are credited to [Swiggy via Mobbin](https://mobbin.com/screens/b08be24e-ba5e-4b57-8433-fa7401a5aa46). These are reconstructions, not original Swiggy vector design files.

## Source-derived native artwork

- `geometry.json` and `rig.json`: bodies, detached islands, eye/gaze measurements, key/hand tracks and world-coordinate body gradients from 642 source frames. Clean native eye whites and clipped pupils replace low-resolution pose imagery.
- `key-vectors.json`: measured keycap outer/face plates and compound letters with even-odd holes. Runtime applies the rig's center, rotation and scale. Generated keycap PNGs are preserved but no longer decoded.
- `illustrations.json`: the five-layer prize traced from the original 623×325 crop, kept in its complete 190×99 logical box; a clean high-resolution bonus-flame/white-inset template with measured per-frame deformation, source-traced ₹5 compound glyph, coin geometry and colors. Neither the generated prize PNG nor the generated bonus-flame PNG is used by the final renderers.
- `poses.json`: ten dense layout channels with 642 samples each. `effects.json`: measured six-coin travel, badge appearance/open cyan strokes, white waves and field styling. `sparks.json`: measured lightning silhouettes.
- Wordmark and CTA lettering are source-derived reconstructions bundled as PNGs. The background is source-derived/reconstructed. No component plays the reference video or draws a whole-screen screenshot.

Paths, gradients and text masks preserve measured shapes and timing; they do not reproduce every source blur, texture or antialiasing pixel. Body-gradient material holds at source 60 Hz even when geometric channels interpolate.

## Generated PNGs still used

Generated with the built-in imagegen tool on 2026-10-24, using supplied raster crops as visual references:

- Tutorial envelope/invitation, tapping phone and rupee coin: 1254×1254, 1182×1331 and 1312×1199 masters.
- Reward pink ₹5, gold ₹1 and gold ₹2: 1254×1254 masters. Gold ₹1/₹2 also supply the first two daily coins.
- Daily ₹3/₹4/₹5 coins: 1254×1254 masters; days 5–7 reuse the ₹5 artwork.

Shared `artwork.json` selects alpha-content rectangles while preserving the calibrated external display frames. Swift/Compose crop decoded static bitmaps; React Native crops inside a fixed native view. Reward-sprite metadata separately aligns visible coin bounds. Runtime does not sharpen the PNGs.

The normalized generation brief was to preserve the reference silhouette, proportions, palette and visible lettering in crisp restrained illustration, with transparency and without blur, grain, unrelated copy or decoration. Generated coin/icon materials remain approximations and are not original Swiggy assets. Earlier generated keycap, prize and bonus PNGs and old character atlases remain bundled as retained iterations; final runtime uses native vectors for those roles.

## Motion and font credits

- Gilroy binaries come from observed official Swiggy web font preloads at `media-assets.swiggy.com`; no open-font license is asserted here.
- [rossark-motion-skill](https://github.com/rossark0/rossark-motion-skill), MIT, supplies the monotone-flow method; see ROSSARK-LICENSE.txt. Its license does not cover the supplied Swiggy artwork or Gilroy.
- The owner authorized local primitives adapted from [Gleam Line Intro](../../text/gleamlineintro/GleamLineIntro.kt) for word arrival and [Thinking Word Shimmer](../../text/thinkingwordshimmer/ThinkingWordShimmer.kt) for the prize/CTA sweeps. The implementation keeps the scene's one clock and does not import entire text blocks.

## Runtime files

- [BuzzStreaks.kt](BuzzStreaks.kt): Clock, calibrated native UI, optional enhancedText mode and three visible controls.
- [BuzzStreaksMascots.kt](BuzzStreaksMascots.kt): Source body/key/eye geometry and contact layering.
- [BuzzStreaksRewards.kt](BuzzStreaksRewards.kt): Coin flights, field, measured waves and independent badge strokes.
- [BuzzStreaksIllustrations.kt](BuzzStreaksIllustrations.kt): Native source-traced prize and animated bonus-flame artwork.
- [BuzzStreaksTextMotion.kt](BuzzStreaksTextMotion.kt): Word clips, reveal math and shimmer primitives.
- [BuzzStreaksDemo.kt](BuzzStreaksDemo.kt): Gallery entrypoint.

Bundled motion/vector data are under `../../../../../../../assets/buzzstreaks/buzzstreaks_*.json`; retained PNGs are alongside them. `enhancedText=true` is the default; `false` disables the added caption/glint accents for base-reference comparison, and reduced motion bypasses them.

This provenance is not a fidelity or performance certification. Source-derived paths and generated materials still differ in texture, rasterization and background reconstruction. Physical-device FPS/frame time and memory have not been measured; final native-mode, accessibility and font-scaling coverage must be reported from actual QA evidence.

### Cubic edge reconstruction

The active static contracts are `letterCurves` in key-vectors and `curves` / `glyphCurves` in illustrations. Each contour is a start point followed by cubic control/control/end groups, closed and filled with the even-odd rule. These were fitted offline from the existing reference masks; raw letters/paths are retained. No generated PNG master was sharpened or rewritten. Native body curves derive from the original point bank at load time. New runtime evidence uses full-resolution device captures; the older 360×780 frame archive is a timing baseline from before this refinement.

### Signature cue contract

`signature.json` (platform resource prefixes apply) is original additive choreography authorized after the source study was approved. It holds contact/travel easing samples, visible-artwork anchors and six bounded cue windows. It does not replace or resample source artwork, rig, curve or layout assets. Public components default to the approved profile; demos select Signature. Portal keeps the existing gold coin amounts and source endpoint/radius/alpha data; no new reward is depicted.
