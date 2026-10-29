# Petal Glass Scenes artwork

The three images were generated with Codex's image tool on 2026-10-29 (one image per call, 1024 × 1536 px, opaque) and are invented: no brand, logo, text or person. The same files are bundled in the Swift and Kotlin apps. Everything else on the screen (the glass card and its refraction, the scene-change front, the shadows, the type and the UI) is drawn in code.

Each image was scaled to 1080 px high and saved as lossy WebP (`cwebp -q 86 -m 6 -sharp_yuv`): 720 × 1080 px, 325 KB in total.

The prompts below are the exact prompts sent to the tool.

## petal-clarity.webp

Saved at 720 × 1080 px (95 KB).

```text
Use your image generation tool to create one image: a photoreal macro render of one huge soft folded orange petal, glowing tangerine to deep orange, with fine dense parallel vein lines fanning out like a ginkgo leaf, sweeping in from the lower right and filling the lower right two thirds of the frame, its thin ruffled translucent edge running diagonally from the top right to the lower left, against a pale clear sky-blue gradient background (light aqua at the top left), soft diffuse daylight, crisp sharp detail, full-bleed edge to edge, portrait orientation 2:3, opaque background with no transparency, no text, no letters, no logo, no frame. Then copy the generated PNG into the current directory as petal-clarity.png and print its path and pixel size.
```

## petal-depth.webp

Saved at 720 × 1080 px (125 KB).

```text
Use your image generation tool to create one image: a photoreal macro render of large soft folded silky petals in cobalt blue and violet, with fine dark vein lines, curling and twisting like a fluttering iris in a big S-shaped sweep from the top left down across the middle and out to the lower right, deep cobalt in the folds and lavender where the light catches, against a pale dusky sky background that grades from soft periwinkle grey at the top to slate blue at the bottom right, soft diffuse daylight, crisp sharp detail, full-bleed edge to edge, portrait orientation 2:3, opaque background with no transparency, no text, no letters, no logo, no frame. Then copy the generated PNG into the current directory as petal-depth.png and print its path and pixel size.
```

## petal-echo.webp

Saved at 720 × 1080 px (106 KB).

```text
Use your image generation tool to create one image: a photoreal macro render of a dense cluster of soft ruffled peach and coral petals like an opening peony, with pale cream and ivory outer petals curling around it, filling the lower two thirds of the frame and rising up the left side, fine delicate vein texture, against a pale warm sky background that grades from soft apricot at the top to light peach, soft diffuse daylight, crisp sharp detail, full-bleed edge to edge, portrait orientation 2:3, opaque background with no transparency, no text, no letters, no logo, no frame. Then copy the generated PNG into the current directory as petal-echo.png and print its path and pixel size.
```
