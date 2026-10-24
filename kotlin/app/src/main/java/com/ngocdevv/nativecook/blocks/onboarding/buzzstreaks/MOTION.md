# MOTION.md: living illustrations in this project

> Copy this file next to the project's art (for example `src/art/MOTION.md`) and fill in **Part 2** as you learn the project. Part 1 is the method and stays as it is. The `rossark-motion-skill` reads this file first, and Part 2 overrides the skill's defaults.

---

## Part 1. The method

**The goal:** anything drawn (a character, a plant, a flame, a street, a logo, a chart) feels alive and calm. Nothing teleports, nothing bounces, nothing shouts.

### Principles
1. **Never teleport:** interpolate everything; never swap drawings.
2. **Calm by default:** strong curves, no bouncy overshoot.
3. **Small amplitudes:** when unsure, halve it.
4. **Arcs and pivots:** each moving part is a layer rotating around its joint (stem base, hinge, neck, hook).
5. **Overlap and follow-through:** tips and trailing parts move later and further; give each part the same wave with a growing phase delay.
6. **Variety without chaos:**
   - repeated items get a seeded phase, speed and size;
   - nothing moves in sync, and there's no random value per frame.
7. **One main action per scene, plus two or three idle loops.** Side things stay quiet.
8. **Opacity only to appear or disappear.**
9. **The layout never moves:** motion lives inside the drawing.
10. **Reduced motion:** loops freeze on a good frame 0, and one-shots show their final frame.
11. **Nothing runs unseen:** pause off screen, when hidden and in the background.
12. **Enough frames:** anything under ~100 ms flickers at 30 fps; render and export at 60 when you can. Blinks close in ~110 ms and reopen in ~190 ms.
13. **Interface rules:**
    - frequency decides how much (often seen = barely moves; rare moments get the delight);
    - every motion has a purpose;
    - shared elements travel, never teleport;
    - interface moves stay under 300 ms;
    - exits are faster than entrances;
    - nothing appears from scale 0;
    - interruptions continue from the current value.

### The engine
- **Clocks:** a loop clock for idle life, a one-shot clock for a scene that tells an action. One per character, group or scene.
- **Tracks:** keyframes `[ms, value, 'out'?]` read with `track(t, keys)`. The last key equals the first. `track` stops at every key, so use it for one move (a blink, a lid). For acting (a chain of poses), use `flow(t, keys)`, which passes through the keys without stopping and never overshoots.
- **Helpers:**
  - `wave` (−1 to 1), for sway and breathing;
  - `loop` (0 to 1), for drift and falling;
  - `bell`, to fade in and out;
  - `seg`, for one beat of a one-shot;
  - `seeded(i)`, for per-item variety.
- **Curves:**
  - travel uses ease-in-out `(0.77, 0, 0.175, 1)`;
  - open and arrive use ease-out `(0.23, 1, 0.32, 1)`;
  - leaving uses ease-in;
  - endless loops are linear.

### Quick recipes
| Subject | Recipe |
|---|---|
| Character | breathing 1–2 units over 3–5 s; a blink every 3–4 s; pupils glide; head tilt 3–6° |
| Waiting to be tapped | a jiggle every ~270 ms: squash 2%, widen 2%, the bulge trading sides |
| Floating figure | rises in from 30% of its height below, drifts 5% down and up, leaves rising |
| Tail, hair, ears | rotation around the root, phase delayed along the chain, 4–10° |
| Plant | sway 1.5–4° around the base over 3–6 s; leaves on seeded phases |
| Cloth, flag | a wave that travels along the length, growing away from the fixed edge |
| Fire | a lean of 2–4° and a 3–6% stretch on two waves of different periods |
| Smoke, steam | rises 8–14 units and fades in and out with `bell` |
| Water | ripples grow and fade; floating things bob 1–2 units and rock 2° |
| Rain, snow | seeded `loop`s; snow sways sideways |
| Sky | clouds drift over 20–60 s with parallax; sun rays take 30–60 s per turn |
| Lights | a glow of 2–5% over 2–4 s; stars twinkle at seeded times |
| Machines | gears turn linearly by ratio; a clock hand steps with ease-out |
| Phone | ±3° bursts, then a pause |
| Icon, logo | plays once on appear, then rests |
| Chart | a one-shot draw-on, with bars staggered 40–80 ms |

### Acting (a whole character)
- **Every channel:** key pose (`flow`) + idle (`wave`s, never off while on stage) + follow-through (the parent read 90–140 ms earlier) + accents (a `wave` inside a `seg` window).
- **Arms:** an upper arm and a forearm share a round elbow; the forearm points at shoulder angle + elbow bend.
- **Head turns:** slide the face toward the turn (eyes 10, nose 13 in a 100-wide head) and the ears the other way. Blink on the turn.
- **Weight:** the hips shift and carry the upper body, and the legs angle to keep the feet under it.
- **Landing:** hover, then fall with gravity and stretch; squash hard (0.8) on contact and keep the volume (`width = 1 / height^0.6`).
- **In the air:** limp feet (toes down, one knee folded, trading slowly). The shadow stays on the floor and scales with the height.
- **Props:** enter from off screen already moving and roll by distance / radius. The eyes follow them. A kick hits at the middle of the swing; the prop leaves as a throw, spinning, with a short squash.
- **Asymmetry:** the arms never mirror each other.
- **Camera:** a view from a little above is a whole build, not a tweak: the head is 40–50% of the height, the torso is short and narrower than the head, the legs are short, the feet are round from above, and the shadow is a wide oval with the feet in its lower half. Keep it in one `build` object. A flat view keeps a thin pill shadow.
- **Framing:** on a phone, as big as the screen allows, with the feet low.

### From a reference video
- Contact sheets at the full frame rate (`tools/sheet.sh`).
- Measure motion with numbers (`tools/track_edges.py`): a 2% jiggle or a 5% float doesn't show to the eye.
- Sample colors from pixels (`tools/sample_colors.py`).
- Go part by part: feet, shadow, hands, head.
- Compare proportions as ratios of one unit (head widths). "Stretched" means a ratio is far off.
- Compare your render with the reference at the same moments.
- Don't publish a copy of someone else's art.

### Performance
- Everything is a function of one clock's time.
- Draw many items in one SVG or `Canvas`.
- Reset repeating animations to 0 before starting, and cancel them on cleanup.
- Declare curves at the top of the module.

### Workflow
1. Read Part 2 and the art file's header comments.
2. List what could live in the drawing.
3. Pick one main action and two or three idle loops.
4. Split the art into layers at their pivots.
5. Put the numbers in tokens.
6. Check frame 0, the final frames and the loop seams. For a character, look at a contact sheet of every 4th frame of your own render.
7. Run the checks.
8. Hand off: what moves, and what hasn't been seen on a real device.

---

## Part 2. This project

This owner-requested private Swiggy BuzzStreaks study uses a 360×780, 60 fps, 10.7-second reference and the original 1180×2556 screen PNG. These source measurements override Part 1's generic defaults, including its stationary-layout rule. The owner separately authorized the optional text accents below.

### Scene and measured artwork

- One scene clock drives the one-shot introduction and holds the expanded state at 10.699 seconds. Replay restarts that clock; backgrounding pauses it. Reduced motion shows the expanded state and bypasses the added text accents. There is no invented return loop.
- Geometry and rig data contain 642 source frames. All ten layout channels are dense 642-sample tracks; monotone cubic flow drives header, tutorial, caption and card travel. Adjacent body-point and rig samples interpolate where compatible. Discrete source selectors use a small epsilon at exact frame boundaries.
- Bodies and detached face/hand/tail islands are source-fitted vectors. Orange eye whites are half-domes; pink whites have independently measured position, size and angle. Pupils follow separate gaze tracks and clip to the whites. Body-gradient colors and endpoints hold at the source 60 Hz on all three stacks; geometry can interpolate between them. This is an intentional material-quantization limit above 60 Hz.
- Keycaps are native source-traced plate and compound-letter paths, with 64-point midpoint-smoothed outer/face contours, even-odd counters and measured gradients. Rig center, rotation and size control them; the foreground hand draws above the key. Generated keycap PNGs and old character atlases are retained but not decoded by the final mascot renderers.
- The prize uses five native source-traced layers, even-odd letter counters and measured fills in its complete 190×99 box, including the source padding. The bonus flame uses measured card-local 642-frame paths, a clean high-resolution 64-control flame and 16-control white inset deformed by the measured source motion, with midpoint quadratic smoothing, a native gradient coin and source-traced ₹5 compound glyph. Generated prize and flame PNGs are no longer runtime artwork.
- Rewards use six measured coin flights, three generated high-resolution coin textures, two native-text badges with independent open cyan strokes, 2,875 measured white wave fragments and a separate warm radial field. Yellow lightning uses 139 cached source paths. Wave width, alpha gain and blur come from shared effect metadata; open and empty source fragments remain open and empty.
- Only the moving tutorial and bonus-card layers clip at stage y=674, where the CTA begins. Background, mascots, reward effects and CTA stay outside that clip. Tutorial ribbon, icon frames and daily-row travel use the calibrated source layout. Compose pins native text letter spacing to zero and card-heading line height to 20 so a host Material theme cannot wrap the measured card title.

### Optional text accents

`enhancedText=true` is the default. `enhancedText=false` keeps the base reference captions/timeline and disables the added glints; it is a comparison mode, not a claim of complete source identity. Idle `autoPlay=false` displays the whole current caption. Reduced motion bypasses the enhancements.

| Primitive | Timing and behavior |
| --- | --- |
| Gleam Line Intro word reveal | Invite starts at 0.05 s; back caption at 1.52 s. Words stagger by 35 ms, each with a 240 ms window: smooth opacity reaches 1 in 144 ms, blur falls from 3 to 0 over 192 ms, and vertical offset falls from 4 to 0 over 240 ms with ease-out cubic. Repeated full-phrase draws clip to non-overlapping word regions partitioned at whitespace midpoints. After settling, one whole-phrase draw remains; React Native returns to native Text for the settled caption. |
| Thinking Word Shimmer prize sweep | 0.45–1.15 s, smoothstep travel across the cached number path only, even-odd clipping, white peak 0.42 with SCREEN blending. |
| Thinking Word Shimmer CTA sweep | 9.45–10.15 s, white peak 0.16 through the original label alpha mask. The mask is hidden outside this window; the base label has an explicit 106×23 frame to override its intrinsic bitmap size. |

The sweeps use the same scene clock and a five-stop clear/clear/white/clear/clear band at 0/0.2/0.5/0.8/1. There are no additional repeating timers. Small primitives are adapted locally from the existing text blocks; whole blocks are not imported.

### Optional Signature motion profile

`BuzzStreaksMotionProfile.Approved` remains the component default; the demo selects `Signature`. The helper loads version 2 of `buzzstreaks_signature.json` once on first use and retains its easing and drawing materials.

Contract version 2 retunes the same six mechanisms for phone-scale legibility after the owner found version 1 too subtle. All cues read the existing clock, which holds the expanded state at 10.699 s. Contact/travel tables, cubic anchors and drawing dimensions come from the identical shared JSON contract; paints, gradients and clip paths are retained. Reduced motion disables every Signature cue. Every transient is zero outside its window and at the final hold. No layout, artwork, reward value, timer, control, dependency, custom runtime shader or automatic haptic is added.

| Cue | Version 2 timing | Bounded change |
| --- | --- | --- |
| Keyface contact | Orange 1.10/1.20/1.46 s; pink 2.16/2.26/2.52 s | Face and ink inset peaks at 2.8/2.4 stage units, divided by local key Y scale. Outer shell and source geometry stay unchanged. |
| Buzz relay | 1.24–1.52 s and 2.30–2.58 s; 280 ms each | Cached cubic anchors with tangent-aligned 24×2.1 trail, head radius 2.6, white-core radius 1, alpha ≤0.95 and 45 ms edge fades. Travel uses the shared 129-sample (0.42,0,0.58,1) table. |
| Receiver reflection and gaze | Pink reflection 1.48–1.76 s; orange 2.54–2.82 s | White-to-clear radial radius 12, alpha ≤0.24. Pink clips at key-local (0,0), below ink with stage-scale compensation; orange clips at stage (75,325) inside its silhouette. Pink gaze adds (+1.649968,+1.130312) during 1.57–1.81 s; orange adds (−1.947803,+0.453944) during 2.63–2.87 s, toward their receivers within existing eye clips. |
| Magnetic reward lock | 2.98–3.34 s | First-pair X coefficient −3.6 (opposite for the second coin), Y coefficient +2.4, multiplied by sin(2πp)(1−p)². The first lobe moves outward; actual peak absolute X displacement is about 2.196 stage units. Upper-face gloss alpha ≤0.18 times source alpha; original radius/alpha remain. |
| Coin portal | 4.533333–5.10 s and 4.65–5.216667 s; about 567 ms each | Existing gold2 coins travel behind the tutorial card and join original XY exactly. Lip glints have a 38-unit total round-cap length, width 1.8, duration 340 ms and alpha ≤0.46. Complete day15 badge fill/strokes/text gate together at 5.216667–5.36 s using contact easing. |
| Streak domino | Starts 8.86/8.95/9.04 s; 300 ms each | DAY 5–7 images lift by up to 5.4 stage units with a sine envelope. Rotation is 0° and scale is unchanged: translation alone preserves bitmap clarity. Labels and layout stay fixed; the last cue ends at 9.34 s, before the CTA gleam at 9.45 s. |

React Native replay resets, reads and starts the clock in one UI-thread job; a generation token rejects a stale completion from an older replay. Every foreground event reconciles playback even when inactive/active Boolean state updates are batched. Asset readiness, manual-only haptics, Info's 7.25 s seek and the existing scene pause/resume contract remain in force.

Version 1 native captures and frame comparisons are historical evidence, not version 2 validation. Version 2 implementation is complete; final native checking is pending on the user's selected `nc-mob-1` simulator in Expo Go. The version 2 Android build passed, while Android runtime QA is explicitly skipped at the user's request. Physical-device performance remains unmeasured.

### Interaction, provenance and limits

Only Start, Back and Info are interactive. Each manual action gives a light platform haptic; automatic playback does not. There are no invisible mascot targets.

Source artwork/UI belongs to Swiggy and was supplied through the owner's Mobbin reference. Generated coin and icon materials remain reference-derived approximations. Gilroy comes from observed official Swiggy font preloads. Rossark's MIT motion-helper license does not license that artwork or font. See ASSET-PROVENANCE.md for runtime files and reuse credits.

This document describes runtime behavior, not a completed QA result. Source sampling at 60 fps does not establish physical-device FPS. Physical-device frame time, memory and haptic behavior have not been measured here; complete cross-platform identity, font scaling and accessibility are not established.

### Edge-quality refinement

Moving silhouettes use a cached closed cubic bank rather than straight polygon edges. Each source vertex keeps its position and cubic verb count; handles use the neighboring tangent divided by six, zero at turns above 65 degrees, and cap at 0.45 of the shorter edge. Native interpolation therefore keeps the original 642-frame correspondence. Swift includes its measured blink/face islands in the bank. Swift and Compose retain about 13.28 MiB of Double controls before native path/header overhead. React Native caches native SkPaths rather than retaining a Double control bank. Physical frame time is not established.

Prize layers, key lettering and the bonus ₹5 use fitted closed cubic contours with even-odd counters. True lettering corners stay; the three rounded zero counters use continuous C2 fits. Key contour deviation is bounded to about 0.12 logical point and counter cleanup to about 0.30. Raw measured polygon data remain for provenance. No runtime blur or new shader hides edges. Anti-aliasing remains enabled in the native renderers.

React Native waits for Expo font registration before mounting the scene, then uses the existing image/path readiness gate before starting the clock. This prevents native text from caching a system-font fallback on a cold start.
