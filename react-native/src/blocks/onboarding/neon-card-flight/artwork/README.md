# Generated sculpture and card artwork

Nine selected `*-final.png` sculpture images are local transparent artwork created with the built-in `image_gen` tool from object crops of the exact design at https://www.pinterest.com/pin/440086194862933052/. Reference source times are2.50,2.67,3.25 and4.00 seconds. No source pixels, screen composite or video ship.

Four built-in generations supplied the selected final identities: white/lilac headphones, four purple padlocks, chrome shield embossed invented N, irregular pink cup with lid/flag, eight-point neon star, mortarboard, silver/lime coin, two closed interlocking capsule links, and orange puzzle piece. The central links have a long closed aperture and molded red/pink/lavender surfaces. The cup retains its concave pinched body and red bulbous lower end. Every selected cutout is centered on a512×512 transparent canvas. Identical selected PNG bytes are used in all three stacks.

Each object draws ONE opaque image. Continuous local affine projection changes its apparent side/face angle, with diagonal-axis compression around the links’ upper pivot, thin-to-frontal locks, narrow-to-full shield, and coin edge turns. Full-body angle images never crossfade: this prevents displaced outlines and apertures from becoming transparent duplicate shells. Source motion and draw-order tables live in each stack’s motion helper.

The card is drawn in native CoreGraphics. The approach face has vertical Nova along the far-right edge and a lower-centred chip; the lateral/returned face has horizontal top-left Nova and lower-right chip. `render-card.swift` is an offscreen macOS bridge of the exact Swift card drawing, used to export `card-intro.png` for the ports (`swift render-card.swift <output-folder>`). It needs no package, app, simulator or network.

The complete generation prompts, original output atlases, source crops, sprite packing script and quantitative audit remain outside the repository under `codex/research/neon-card-flight/motion-revision-20261018/`. They are not runtime dependencies.
