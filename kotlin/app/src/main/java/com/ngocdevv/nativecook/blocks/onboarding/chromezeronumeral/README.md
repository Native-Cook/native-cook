# Chrome Zero Numeral

`ChromeZeroNumeral(autoPlay = false)` is the standalone screen. The gallery entry calls it with autoplay enabled. One `withFrameNanos` clock advances `ZeroMotion`; the Canvas reads its frame during drawing, without recomposing the screen on every tick. Lifecycle suspension stops the clock. The system animator-duration setting is observed live.

`ZeroTimeline` translates the source timeline and closed-form springs to Kotlin doubles. The loop lasts 12 seconds. Touch interrupts playback immediately; dragging uses 110 stage points per radian, with velocity-driven coast and spring return. A stationary 180 ms hold loosens the numeral. Release fuses it from the touch position. Autoplay waits 3.5 seconds after release, then settles for 700 ms before restarting. The accessibility activation actions use the same state machine.

The three AGSL strings preserve the original SkSL equations and uniform names. API 33+ renders the numeral, backdrop and lit card natively. The studio PNG is ordinary display-colour imagery, sampled linearly; it is not a normal/height data texture.

All six original PNGs live in `assets/`. Their `Zero*Asset.kt` files contain byte-identical Base64 chunks. The larger fallback backdrop is kept in `kotlin/app/src/main/assets/chromezeronumeral/zero-fallback-backdrop.png` so lint can process all Kotlin sources; copy that standard assets folder too. No Gradle changes are needed. The chunking avoids the JVM constant-string limit. The two fallback PNGs are offline renders of the original shader at rest; their generator is retained under the job's `out/verification/` directory. They are decoded lazily only below API 33. No Python or JavaScript executes in the app.

API 26–32 retains the original resting chrome artwork, navy ribbons, card, sparkles, copy and button. It deliberately has no moving 3D reflection, rain, fusion, or gesture rotation. The button remains operable. The assigned emulator runs API 37: the fallback drawing path was forced temporarily for a smoke test, and that override was removed before the final build.

The poster uses a 402-point stage scaled to the available width so Android display-density presets do not unexpectedly enlarge its typography. Height and the reserved navigation inset remain container-relative. Fonts use the Android system family. No new app dependency is required.
