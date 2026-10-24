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

### Interaction, provenance and limits

Only Start, Back and Info are interactive. Each manual action gives a light platform haptic; automatic playback does not. There are no invisible mascot targets.

Source artwork/UI belongs to Swiggy and was supplied through the owner's Mobbin reference. Generated coin and icon materials remain reference-derived approximations. Gilroy comes from observed official Swiggy font preloads. Rossark's MIT motion-helper license does not license that artwork or font. See ASSET-PROVENANCE.md for runtime files and reuse credits.

This document describes runtime behavior, not a completed QA result. Source sampling at 60 fps does not establish physical-device FPS. Physical-device frame time, memory and haptic behavior have not been measured here; complete cross-platform identity, font scaling and accessibility are not established.

### Edge-quality refinement

Moving silhouettes use a cached closed cubic bank rather than straight polygon edges. Each source vertex keeps its position and cubic verb count; handles use the neighboring tangent divided by six, zero at turns above 65 degrees, and cap at 0.45 of the shorter edge. Native interpolation therefore keeps the original 642-frame correspondence. Swift includes its measured blink/face islands in the bank. Swift and Compose retain about 13.28 MiB of Double controls before native path/header overhead. React Native caches native SkPaths rather than retaining a Double control bank. Physical frame time is not established.

Prize layers, key lettering and the bonus ₹5 use fitted closed cubic contours with even-odd counters. True lettering corners stay; the three rounded zero counters use continuous C2 fits. Key contour deviation is bounded to about 0.12 logical point and counter cleanup to about 0.30. Raw measured polygon data remain for provenance. No runtime blur or new shader hides edges. Anti-aliasing remains enabled in the native renderers.

React Native waits for Expo font registration before mounting the scene, then uses the existing image/path readiness gate before starting the clock. This prevents native text from caching a system-font fallback on a cold start.

### Candidate B: retained Signature VFX profile

`motionProfile='approved'` remains candidate A and the public default. `signature` retains candidate B's VFX revision; the RN demo now selects candidate C, `character`, described below. RN's expressive hero revision (version 3) follows the owner's report that the earlier micro-motion still lacked a clear visible payoff. It builds one causal story: return ribbon → conduit → open orbital pull → reward release, followed by the final streak domino. SwiftUI and Compose retain their version 2 Signature choreography; they are not version 3 parity implementations.

The shared `assets/signature.json` remains version 2 and unchanged. RN-local `BuzzStreaksHero.ts` and retained drawing layers provide this revision, using the existing scene clock rather than an additional timer. The stage layout, source geometry, fonts, bundled artwork, reward amounts and dependencies stay unchanged. Reduced motion bypasses Signature; the approved path and the quiet expanded hold at 10.699 s remain intact.

| Beat | RN version 3 timing | Drawing and behavior |
| --- | --- | --- |
| Key contact and receiver response | Existing v2 windows | Keep face/ink insets at 2.8/2.4 stage units, clipped radius-12 reflections at alpha ≤0.24, and the corrected receiver gaze. Key lettering stays above the reflection. |
| Long buzz ribbon | Heads travel 1.24–1.52 s and 2.30–2.58 s; tails drain by 1.72/2.78 s | Existing cubic anchors now carry an 8-unit lime stroke at alpha 0.85, 2-unit white core, and tangent-aligned 18×10 capsule head. A 200 ms trailing trim leaves a long curved ribbon, with a cheap 2-unit-offset purple underside. The ribbon draws behind the source mascots, without blur or accumulated particles. |
| Conduit | 2.60–2.88 s | A retained cubic runs from (75,325), through (40,364)/(58,399), to (96,424). Its 5-unit lime stroke, 1.4-unit white core and radius-4 head draw and drain into the reward orbit. |
| Open reward orbit | Head 2.88–3.60 s; tail drains by 3.82 s | Two cached cubic halves pass from (96,424) through the upper controls at Y=379 to (263,424), then return through lower controls at Y=469. A 5-unit lime arc with 1.4-unit white core stays open and transient. Complementary back/front clips meet at Y=424; only the lower orbital half is foreground, with no duplicated orbital paint or foreground conduit. |
| Reward creation and pull | Source time holds at 2.58 until scene 2.90 s, rejoins the source at 3.40 s; pull peaks 3.24 s, holds through 3.36 s, overshoots −0.12 at 3.59 s and settles by 3.68 s | One derived source-time mapping delays coins, reward waves/field and badges until after the conduit. The first pair pulls outward by 18 units and upward by 12 units at peak, with up to 18% extra radius from the existing upright 1254 px coin masters. Positive pull increases radius; the original radius/alpha return on release. No bitmap rotation or new artwork is introduced. |
| DAY 1 reveal | 3.34–3.55 s | Fill and native text share the complete DAY 1 gate; its old decorative source strokes are hidden in Signature so they cannot compete with the orbital story. The approved badge retains its source strokes. |
| Gold2 portal | Existing v2 entries end at 5.10/5.216667 s | Original gold2 radii/alpha and endpoint joins remain. Lip glints retain 38×1.8 dimensions, 340 ms duration and alpha ≤0.46. The complete day15 badge gate remains 5.216667–5.36 s. |
| Streak domino | Starts 8.82/8.92/9.02 s; 340 ms each, quiet by 9.36 s | DAY 5–7 images translate upward by up to 12 units with a sine-squared envelope and a small animated contact shadow. Images stay upright and unscaled; labels/layout stay fixed. The CTA gleam still starts at 9.45 s. |

Curves, clip rectangles and 129-sample arc-length lookups are created once with `useMemo`. Shared derived scalar props drive retained path start/end trims and exact cubic head coordinates; clock callbacks do not construct paths. The reward source-time mapping is a pure function of the same clock. Every added layer and transform is zero/identity by the final hold. No extra control, timer, dependency, source-JSON change, full-scene flash, custom runtime shader or automatic haptic is added.

React Native replay resets, reads and starts the clock in one UI-thread job; a generation token rejects stale completions from older replays. Every foreground event reconciles playback even when inactive/active Boolean state updates are batched. Readiness and manual-only haptics remain in force. Info seeks 7.25 s, skipping the hero sequence while the later domino still plays.

Version 3 RN was checked on `nc-mob-1` in Expo Go: 25 exact-clock native captures, reviewed storyboard/comparison, cold launch, three rapid replays, Info/Back and final hold. The reduced-motion branch was tested by dependency injection, then restored before a cold restart; no iOS system-setting coverage is claimed. A 1 ms production-math sweep passed 245,302 assertions; scoped TypeScript/ESLint and catalog checks passed. Current evidence is `timing-review/signature-hero-rn` in the showcase research directory. Version 1/version 2 captures, builds and comparisons are historical. SwiftUI/Compose remain at v2 outside this RN-only scope. Physical-device performance is unmeasured. Metro 8099 stays running for the owner's live review.

### Candidate C: character acting profile

RN now exposes three comparison profiles: A=`approved` (public default), B=`signature` (the retained v3 VFX hero above), and C=`character` (the current RN demo). Candidate C emphasizes mascot weight and reaction instead of adding more spectacle. SwiftUI/Compose remain at v2, with no candidate-C source or new runtime QA; the active UI target is only `nc-mob-1` in Expo Go.

`BuzzStreaksActing.ts` supplies pure worklet poses during 0.60–3.84 s. Each mascot's silhouettes, key, clipped foreground hand, eye whites/pupils, smile and reflections sit inside one actor matrix, `T(delta) T(pivot) R S T(-pivot)`. Rotation is radians internally and width compensates height with `sx=1/sy^0.6`. Source paths/gradients stay intact; key, hand and face therefore move as one unit. Orange changes from key-center pivot to body pivot (89.056,318) at 1.58 s; pink changes to (270.75,539.5) at 2.84 s. Both switches occur at exact neutral poses.

| C acting beat | Timing and peak pose |
| --- | --- |
| Orange wind-up/contact | Wind-up 0.68–0.96 s holds through 1.10: X=−5, Y=+4, lean −5°, SY=.95. Contact at 1.20: X=+2, Y=+7, lean +3°, SY=.89; rebound at 1.34: Y=−4, lean −2°, SY=1.06; rest by 1.55. |
| Pink receives and replies | Receive peaks 1.64: X=+7, Y=+5, lean +8°, SY=.94, with −.18 reaction overshoot at 1.81 and rest at 1.98. Wind-up 2.00–2.12 leads to contact at 2.26: X=−3, Y=+6, lean −4°, SY=.91; rebound at 2.43: Y=−4, lean +2°, SY=1.05; rest by 2.70. |
| Orange receives | Starts 2.58, peaks 2.70 at X=−5, Y=−7, lean −8°, SY=1.05; −.16 reaction overshoot at 2.86; rest by 2.98. |
| Asymmetric reward hops | Orange crouches at 3.10, lifts 11 units with +3° lean at 3.25, lands at 3.52 and rests by 3.74. Pink follows 100 ms later, shifts X=−3, lifts 13 units with −3° lean, and rests by 3.84. Happy eye squint peaks at .16 and smile Y scale at 1.22 during 3.18–3.64, staggered 100 ms for pink. |

Gaze blends toward the key, friend, signal and reward at (180,423). World targets pass through the inverse actor matrix before adding at most 2.6 units of pupil movement inside the existing eye clips; C does not add the old Signature gaze again. Pink blink starts at 1.54 s and orange at 2.60 s: 90 ms close, 30 ms hold, 150 ms reopen. Each eye's white/pupil/clip group compresses about its own center/angle with minimum scale .001 and zero opacity at full closure. Uncompressed skin backing reuses the existing eye path and body gradient beneath it, and a cached tonal closed-lid curve keeps protruding eye bulbs readable. Backing and lid opacity both return to zero when blink/happy are zero.

C retains a compact relay: 3.5-unit lime stroke, 1-unit white core, 8×5 capsule and 90 ms tail. Launch/arrival anchors are mapped once through the actor matrices from sampled rig keys and cached; retained trim/length lookup follows them. There is no v3 conduit or orbital arc in C. Its upright first reward pair pulls outward by 8 units/upward by 10, with up to 10% extra radius, while B retains 18/12/18%. Both reuse the same derived reward source-time hold/catch-up, pull timing, DAY 1 gate, gold2 portal/day15 gate and 12-unit contact-shadow domino ending at 9.36 s. Acting, gaze and facial additions return to identity by 3.84 s; the 10.699 s final hold is quiet. Reduced motion skips the additions; A and B retain their mascot rendering branches.

The shared RN Start feedback now presses to scale .965 and Y=+1.5 over 90 ms, then releases with spring stiffness 420, damping 26 and mass .7. Reduced motion uses immediate scale .985 with no translation. This input-driven feedback is canceled/reset on background and canceled on unmount; it adds no automatic haptic or scene clock.

The production-math/source audit passed 25,952 assertions, covering inverse matrices, neutral pivot handoffs, gaze bounds, identity outside the acting window and conservative stage bounds. Native C checks on `nc-mob-1` / Expo Go passed: 30 exact-clock captures after 180 ms presentation settling, reviewed A/B/C comparison/storyboard, corrected skin-backed closed eyes, cold launch, three rapid Start taps, Info/Back and final hold with press progress zero. C's final image matches approved exactly below status chrome. Reduced-motion dependency injection matched the final state; the hook was restored and all QA overrides cleared by cold restart. No iOS system-setting or physical-FPS claim is made. Scoped TypeScript/ESLint, catalog 485 and the independent source review passed; final runtime logs had zero entries. Current evidence is `timing-review/character-acting-rn`.

Research materials are prepared at `timing-review/character-acting-rn/research-protocol.md` in the showcase research directory. The proposed first round is 5–8 relevant participants with counterbalanced A/B/C order; no real participants have been recruited or observed. No user-preference win, conversion improvement or “world-class” rating is claimed. Candidate B's prior native captures remain B evidence and do not validate C.
