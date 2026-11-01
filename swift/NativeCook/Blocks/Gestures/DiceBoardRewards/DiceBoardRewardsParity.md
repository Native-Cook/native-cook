# Dice Board Rewards parity

The React Native component is the reference, including behavior that differs from its older comments. The loop is 9 seconds, with six-step throws at 0.6 and 4.6 seconds. Both ports use its projection, cubic flight easing, smootherstep pawn interpolation, flip, bounce, home arc, reward timing, palette, copy and fonts.

## Pinned-frame review

The same ten times are used for every comparison. Full-resolution images and the comparison sheet are in `.work/port/check/dice-board-rewards/` in the shared workspace.

| Time | Phase | First comparison and correction |
| --- | --- | --- |
| 0.3 | Rest | Board, pawn and die positions match. Correct the tile text bounds; remaining UI differences are glyph rasterization and color rounding. |
| 1.05 | First throw, flight | Die position, orientation and shadow match. Same static text correction. |
| 1.7 | First landing bounce | Bounce pose and all cube faces match. Same static text correction. |
| 2.5 | First pawn traversal | Pawn position, hop and squash match. Same static text correction. |
| 3.72 | Coupon flip | Flipping tile width, hidden icon and pawn pose match. Same static text correction. |
| 4.25 | Coupon toast and home arc | Die, pawn and toast pose match. Correct the one-pixel button content baseline; toast glyph edges differ. |
| 5.05 | Second throw, flight | Die position and orientation match. Same static text correction. |
| 6.5 | Second pawn traversal | Pawn position, hop and squash match. Same static text correction. |
| 7.72 | Start flip | Flipping tile width and pawn pose match. Same static text correction. |
| 8.95 | End hold | Pawn, revealed Start and home die match. Same static text correction. |

The tile text correction preserves `SkFont.measureText`'s glyph bounds rather than substituting string advances. Tile glyphs are drawn without shaping or kerning, as with Skia `drawSimpleText`. Native UI text and all fonts are cached, and fixed tiles and controls are rasterized once per screen geometry.

## Final pinned-frame comparison

Two complete capture passes were inspected, including full-resolution board, toast and end-hold crops. An intermediate queued invocation was cancelled before acquiring the simulator lock and restarted; it did not build or capture frames.

| Time | Mean difference / 255 | Pixels with difference > 40 | Final observation |
| --- | --- | --- | --- |
| 0.3 | 0.53 | 0.09% | Rest geometry matches; residual glyph edges and renderer color rounding. |
| 1.05 | 0.54 | 0.09% | Flight position/orientation match; residual glyph edges and renderer color rounding. |
| 1.7 | 0.54 | 0.09% | Bounce and settled orientation match; residual glyph edges and renderer color rounding. |
| 2.5 | 0.54 | 0.10% | Hop pose matches; residual glyph edges and renderer color rounding. |
| 3.72 | 0.54 | 0.09% | Coupon flip pose matches; residual glyph edges and renderer color rounding. |
| 4.25 | 0.53 | 0.09% | Toast, home arc and pawn match; residual glyph/icon edges and renderer color rounding. |
| 5.05 | 0.54 | 0.10% | Second flight matches; additional Dynamic Island/status-bar difference is above the measured region. |
| 6.5 | 0.54 | 0.10% | Hop pose matches; residual glyph edges and renderer color rounding. |
| 7.72 | 0.54 | 0.09% | Start flip pose matches; residual glyph edges and renderer color rounding. |
| 8.95 | 0.53 | 0.09% | Revealed Start, pawn and tray die match; residual glyph edges and renderer color rounding. |

The corrected 10% label has the same horizontal pixel bounds as RN (567–633); the +20 label starts at the same pixel (716), with its right antialias edge differing by one pixel. START differs by one pixel at the upper/left glyph edges. The remaining differences are not missing layers or neighboring animation poses. They include native versus Skia glyph/icon rasterization, shape-edge antialiasing, status-bar content, and approximately one-byte gradient/shadow color quantization between renderers. This evidence establishes the sampled layout and animation poses; it is not a claim of byte-identical screenshots or an exhaustive manual gesture test.

## Interaction and clock review

- Flick uses translation Y < -30 pt OR velocity Y < -500 pt/s. Roll uses the same throw guard, deterministic time-dependent face selection, rolls counter and haptic phases as the reference.
- Autoplay does not emit haptics or audio. The reference has no sound assets or playback calls.
- Reduced motion disables autoplay and die tumbling. The reference still animates flight and pawn hops when the user rolls; both ports retain this actual code behavior.
- Autoplay resumes under the reference's 3.5-second inactivity, busy-state and pawn-position conditions, rather than substituting the generic repository timeout.
- The Swift debug key accepts a String or number, samples the pure script, freezes the clock and disables interaction.
- The Swift loop clock advances by wall time with a 0.5-second cap. No Core Image processing runs during animation. Idle manual/reduced-motion scenes and inactive scenes pause the timeline.
- Button roles, header, balance label, roll/flick actions, hint and reward announcements are retained. Swift pressed state resets on cancellation.

## Reference reset

At 8.95 seconds the Start tile is still revealed; at 0.3 seconds it is yellow. The reference resets that tile at the 9-second boundary. This discontinuity is deliberately preserved. The exact frame at 9 seconds is the frame at 0 because the script uses modulo 9.

## Gates

- Swift block typecheck: `SWIFT OK`.
- Kotlin block compile: `KOTLIN OK` (three runs; the final run includes the opacity and press-retention corrections).
- The supplied simulator script verifies Swift against React Native. It does not exercise touch or render the Android port; those paths have code review and compilation evidence only.
