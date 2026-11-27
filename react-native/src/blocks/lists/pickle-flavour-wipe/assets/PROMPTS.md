# Pickle Flavour Wipe artwork

The six images were generated with Codex's image tool on 2026-10-29 (one image per call, 1254 × 1254 px, transparent background) and are invented: no brand, logo, text or person. The same files are bundled in the Swift and Kotlin apps. Everything else on the screen (the patterned backdrops, the wipe, the leaves, the text printed on the jar labels, shadows and UI) is drawn in code.

Each image was cropped to its subject and saved as lossy WebP with alpha (`cwebp -q 82 -alpha_q 90 -m 6 -sharp_yuv`): the jars at 720 px high, the ingredients at 420 px wide; 472 KB in total. The jars' coloured labels are blank on purpose: the brand, flavour and "pickle" line are printed on them in code.

The prompts below are the exact prompts sent to the tool.

## jar-mango.webp

Saved at 680 × 720 px (133 KB).

```text
Use your image generation tool to create one image: a photoreal squat round glass jar of Indian mixed pickle in glistening oil, a wide ribbed shiny gold metal screw lid, the glass is clear so the chunky pickle pieces and spices show through, a completely blank plain paper label band wrapped around the middle of the jar with nothing printed on it, upright, straight front view at eye level, bright warm studio light from the upper left with a crisp vertical highlight on the glass, crisp clean edges, on a fully transparent background, square, no text, no letters, no logo, no shadow; the pickle is green mango chunks, carrot sticks and red chillies in deep red-orange oil; the blank label is a solid deep violet purple colour. Then copy the generated PNG into the current directory as jar-mango.png and print its path and pixel size.
```

## jar-lime.webp

Saved at 641 × 720 px (125 KB).

```text
Use your image generation tool to create one image: a photoreal squat round glass jar of Indian mixed pickle in glistening oil, a wide ribbed shiny gold metal screw lid, the glass is clear so the chunky pickle pieces and spices show through, a completely blank plain paper label band wrapped around the middle of the jar with nothing printed on it, upright, straight front view at eye level, bright warm studio light from the upper left with a crisp vertical highlight on the glass, crisp clean edges, on a fully transparent background, square, no text, no letters, no logo, no shadow; the pickle is lime wedges, green chillies and mustard seeds in golden yellow oil; the blank label is a solid crimson red colour. Then copy the generated PNG into the current directory as jar-lime.png and print its path and pixel size.
```

## jar-garlic.webp

Saved at 657 × 720 px (127 KB).

```text
Use your image generation tool to create one image: a photoreal squat round glass jar of Indian mixed pickle in glistening oil, a wide ribbed shiny gold metal screw lid, the glass is clear so the chunky pickle pieces and spices show through, a completely blank plain paper label band wrapped around the middle of the jar with nothing printed on it, upright, straight front view at eye level, bright warm studio light from the upper left with a crisp vertical highlight on the glass, crisp clean edges, on a fully transparent background, square, no text, no letters, no logo, no shadow; the pickle is whole peeled garlic cloves and red chillies in amber brown oil; the blank label is a solid cobalt blue colour. Then copy the generated PNG into the current directory as jar-garlic.png and print its path and pixel size.
```

## carrot.webp

Saved at 420 × 417 px (31 KB).

```text
Use your image generation tool to create one image: a single photoreal fresh orange carrot with a short tuft of green leafy top, lying diagonally, side view, bright warm studio light from the upper left, crisp clean edges, on a fully transparent background, square, no text, no logo, no shadow. Then copy the generated PNG into the current directory as carrot.png and print its path and pixel size.
```

## lime.webp

Saved at 420 × 328 px (37 KB).

```text
Use your image generation tool to create one image: a photoreal whole green lime next to a half lime showing its juicy pale green cut face, product photography, slightly from above, bright warm studio light from the upper left, crisp clean edges, on a fully transparent background, square, no text, no logo, no shadow. Then copy the generated PNG into the current directory as lime.png and print its path and pixel size.
```

## garlic.webp

Saved at 420 × 403 px (23 KB).

```text
Use your image generation tool to create one image: a photoreal whole white garlic bulb with two loose peeled cloves beside it, slightly from above, bright warm studio light from the upper left, crisp clean edges, on a fully transparent background, square, no text, no logo, no shadow. Then copy the generated PNG into the current directory as garlic.png and print its path and pixel size.
```
