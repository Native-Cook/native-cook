# Pineapple Lift Product artwork

The four images come from three generations with Codex's image tool on 2026-10-29 (one image per call, transparent background) and are invented: no brand, logo, text or person. The same files are bundled in the Swift and Kotlin apps. Everything else on the screen (backdrop, juice drops, glow, shadows, the bottle's label text, UI) is drawn in code.

The prompts below are the exact prompts sent to the tool.

## pineapple-top.webp and pineapple-bottom.webp

Generated with Codex's image tool as one image (`pineapple-split.png`, 1024 × 1536 px) so both halves share the same fruit and lighting, then cut apart along the empty gap between the halves: the top half (790 × 1046 px crop) is saved at 632 × 837 px (139 KB) and the bottom half (610 × 458 px crop) at 488 × 366 px (54 KB), as lossy WebP with alpha (`cwebp -q 82 -alpha_q 90 -m 6 -sharp_yuv`).

```text
Use your image generation tool to create one image: a photoreal ripe golden pineapple cut straight across its middle into two halves, product photography: the top half with its full spiky green crown floats above the bottom half with a clear gap of empty space between them, the top half tilted about 12 degrees so its juicy yellow cut face shows underneath as a narrow ellipse, the bottom half seen slightly from above so its flat juicy yellow cut face shows as an ellipse, both halves centred on one vertical axis, side view, bright warm studio light from the upper left, crisp edges, on a fully transparent background, portrait 2:3, no text, no logo, no shadow on the ground. Then copy the generated PNG into the current directory as pineapple-split.png and print its path and pixel size.
```

## bottle.webp

Generated with Codex's image tool (1024 × 1536 px), cropped to the bottle and saved at 276 × 716 px (28 KB). The cream label is blank on purpose: the label text is printed on it in code.

```text
Use your image generation tool to create one image: a photoreal amber glass cosmetic body-oil bottle, cylindrical with rounded shoulders, a glossy black screw cap, filled with golden oil, a completely blank plain cream paper label wrapped around the middle of the bottle with nothing printed on it, upright, straight front view at eye level, bright warm studio light from the upper left with a crisp vertical highlight on the glass, on a fully transparent background, portrait 2:3, no text, no logo, no shadow. Then copy the generated PNG into the current directory as bottle.png and print its path and pixel size.
```

## hibiscus.webp

Generated with Codex's image tool (1254 × 1254 px), cropped to the flower and saved at 400 × 392 px (30 KB).

```text
Use your image generation tool to create one image: a single photoreal vivid red-orange hibiscus flower in full bloom, five ruffled petals and a yellow-tipped stamen, seen from the front and slightly above, bright warm studio light, crisp clean edges, no stem and no leaves, on a fully transparent background, square, no text, no logo, no shadow. Then copy the generated PNG into the current directory as hibiscus.png and print its path and pixel size.
```
