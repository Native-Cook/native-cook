# X-Ray Bag Scan artwork

The suitcase and its x-ray were generated with Codex's image tool (one image per call, 1254 × 1254 px, transparent background). The photo came first; the x-ray was then generated with the photo attached as the reference image, so the two share one outline. Both were cropped to the same box around the bag (670 × 1221 px), so a single rect draws either. The x-ray was clipped to the photo's alpha (the scanner window reveals exactly the photo's silhouette), its body darkened toward navy with a luminance curve so the bright contents glow, and given a thin cyan rim along that silhouette. Both were scaled to 329 × 600 px and saved as lossy WebP with alpha (`cwebp -q 84 -alpha_q 92`). The same files are bundled in the Swift and Kotlin apps. The bag, its stickers and its blank tag are invented: no brand, logo or text.

The prompts below are the exact prompts sent to the tool.

## suitcase.webp

Generated with Codex's image tool.

```text
Use your image generation tool to create one image: a photoreal playful lilac (soft lavender purple) hard-shell carry-on suitcase with fine vertical ribbed grooves across its front shell, rounded corners, a pastel teal top carry handle and a pastel teal grip on a short extended telescoping trolley handle with silver bars, four small cream spinner wheels, a cute round green four-leaf clover sticker and a cute golden horseshoe sticker with a tiny red star on the front shell, and a blank pale mint paper luggage tag with no writing hanging from the top handle by a green strap; exact straight-on front view, standing upright, the whole suitcase centered and filling about 70 percent of the image height, soft warm studio lighting from the upper left with gentle highlights along the ribs, 3D product render look, on a fully transparent background with no floor, no shadow, square. No logos, no brand marks, no text, no letters, no numbers, no barcodes, no people. Then copy the generated PNG into the current directory as suitcase.png and print its path and pixel size.
```

## suitcase-xray.webp

Generated with Codex's image tool, with `suitcase.png` attached as the input image.

```text
The attached image is suitcase.png (also in the current directory). Use your image generation tool, with the attached image as the reference image to edit, to create one image: an airport security X-ray scan of this exact suitcase, matching this exact suitcase outline, position, framing and size pixel for pixel (same trolley handle, top handle, luggage tag, rounded corners, four wheels, same place in the square). Render it as a glowing X-ray: thin cyan and white glowing outline lines and translucent cyan-blue density on a semi-transparent dark navy suitcase body, the shell ribs shown as faint cyan lines, the trolley bars and wheels as bright cyan outlines, and the contents clearly visible inside the shell: a pair of sneakers at the bottom, a laptop standing flat across the middle, a folded umbrella lying diagonally, a toiletry bottle and over-ear headphones near the top, drawn as layered translucent cyan X-ray shapes with brighter dense parts (metal, battery, zips). No stickers. Everything outside the suitcase outline fully transparent, square. No text, no letters, no numbers, no logos, no people. Then copy the generated PNG into the current directory as suitcase-xray.png and print its path and pixel size.
```
