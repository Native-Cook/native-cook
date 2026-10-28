# MOTION.md: living illustrations in this project


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

### Where the code is
- **Platform:** React Native first (Expo 57, Reanimated 4, Skia 2.6, a native WebGPU field). Swift and Kotlin only when the owner says "Port".
- **Clocks:** `HabitatMotion.ts`. One ambient loop of 13.5 s (`LOOP`) for idle life; the carousel's timeline (3 s at rest, 1.5 s crossing per specimen); a move's progress (`power`, 1.8 s) and its effect clock (`fx`, seconds from the move's start to 3.9 s, while what it throws lands). `timelinePerformer` keeps a move's effects with the specimen that made it through the crossing.
- **Rig:** `HabitatRig.ts`. Every coordinate is a pixel of the 512×512 source PNG. `RIGS` holds, per creature: feet, chest, four joints (pivot, centre, radius, sway waves; joint 0 is the head), eyes and blinks, the face for the 2.5D head turn, the drawn flame, the signature light and the acting keys. `lifePose` layers every channel (key pose by `flow`, idle waves, follow-through by delay, accents); `bodyPoint` runs the warp forwards so effects attach to the posed body.
- **Drawing (Skia):** `HabitatShaders.ts`. `CREATURE_LIFE` inverts the pose and the rig per pixel, lights the art from a normal map baked from its alpha (`NORMAL_BAKE`), draws lids, the head turn and the live tail flame. `SIGNATURE_EFFECT` draws what stays attached to a move (jets, muzzle spray, beam, charge), and its particles only when WebGPU is missing or a frame is frozen for review (the analytic leaf physics in `HabitatParticles.ts`).
- **Particles (WebGPU):** `gpu/HabitatParticleShader.ts`. One stateful world of 520 particles, stepped by a compute pass each frame on the UI thread and drawn as instanced quads into two canvases, behind the creatures (depth < 0) and in front of them. Ambient motes morph into the new habitat's mote when the front passes them; a finger stirs them (wake, swirl, push; petting gathers them into an orbit), a tap's release sends a ring of air, a landing kicks a gust along the floor, tilt turns which way is up. A move's particles are born from the posed body at their birth time (embers off the flame's tip, spray off the muzzles that lands and splashes, leaves and pollen off the bulb); settled leaves can be swept up again by a finger.

### The owner's rules
- 2026-10-27: "Vì assets này không phải là 3D ... carousel này nên cần em lại để phù hợp với assets là ảnh" — the art is flat; nothing pretends to turn round. The exchange is a habitat front that wipes each specimen.
- 2026-10-27: "lá phải rớt xuống rồi mới ẩn đi" — anything thrown falls and settles before it fades; nothing vanishes mid-air.
- 2026-10-27: "Em giữ 2.5D" — 2.5D on flat art: lighting, bends, head turns by sliding the face, depth layers for particles. No 3D models.
- 2026-10-27: effects must sit exactly on the body part that makes them, and no Skia layer may show as a haze, a disc, a seam or a doubled drawing.
- Codex only generates raster assets; every line of motion code is written here.

### Rig limits
- One flat PNG per creature: no separate layers, eyes baked in (lids are skin sampled above the eye), no pupils, no back side.
- Bends are rotations of soft regions around a pivot, so the art stretches between regions; idle sways stay small (1–3.5°) to keep it intact.
- The head turn only slides the face's middle (Charizard 5 px, Blastoise 6, Bulbasaur 9 at a full turn); more reads as a smear.
- Charizard's tail flame is always drawn live from its own pixels, so the drawn flame and the live one can never both show.

### How changes are checked
- `npx tsc --noEmit -p .` and `npx eslint src/blocks/cards/habitat-specimen-carousel` from `react-native/`.
- Offline: the scratch harness renders the production SkSL in CanvasKit with the real rig and particle code, at chosen effect-clock seconds, for frame-by-frame review. The WGSL runs in headless Chrome's WebGPU (driven over the DevTools protocol) with scripted frames: compile errors, morphs, finger, tap and each move.
- Device: iPhone 18 Pro simulator, `nativecookfree://__new_refs_motion_qa?block=pokemon&t=<loop seconds>` for a frozen frame, `&auto=1` live, `&reduced=1` for Reduced Motion.
- Not yet measured: frame rate on a real phone.
