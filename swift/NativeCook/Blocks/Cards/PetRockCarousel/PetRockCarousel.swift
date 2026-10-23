import SwiftUI
import UIKit

/// A carousel of painted pet rocks on paper. It opens on the title "PET ROCK" with a tiny rock for
/// the gap between the words; Enter grows that rock into the carousel as the letters split away.
/// The rock in front is large, its neighbours smaller above and below; dragging scrolls them with
/// the finger and a release snaps to a rock. When a rock comes to rest its face blurs into the
/// next one and its googly eyes jiggle; tapping it squashes it and makes it wobble.
struct PetRockCarousel: View {
    /// Plays the intro, scrolls the rocks, morphs their faces and starts over, on a loop of 14 s.
    var autoPlay = false
    /// Called with a rock's name whenever one comes to rest in front.
    var onFocus: ((String) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    // The carousel's motion lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = PetRockCarouselEngine()
    // True while a touch is down; reset by SwiftUI when the system cancels the drag.
    @GestureState private var touching = false

    private typealias M = PetRockCarouselMetrics
    private static let paper = Color(hex: 0xedeae4)
    private static let paperTop = Color(hex: 0xf2f0eb)
    private static let paperBottom = Color(hex: 0xe6e2db)
    private static let ink = Color(hex: 0x141414)
    private static let muted = Color(hex: 0x8a857d)
    private static let family = "Helvetica Neue"
    private static let titleFont = UIFont(name: "HelveticaNeue-Medium", size: M.titleSize) ?? .systemFont(ofSize: M.titleSize, weight: .medium)

    var body: some View {
        // The block draws under the system bars and keeps its text clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            // Nothing to lay out until the screen is measured: a zero width makes the rocks' scale infinite.
            if size.width > 0 && size.height > 0 {
                stage(size: size, insets: insets)
                    .frame(width: size.width, height: size.height)
                    .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .background(Self.paper.ignoresSafeArea())
        .task(id: displayScale) {
            let big = min(M.bigMax, UIScreen.main.bounds.width * M.bigShare)
            await engine.art.prepare(rocks: engine.rocks, pixels: displayScale * big / 200)
        }
    }

    // MARK: Title

    /// The title's letters: PET, a gap the little rock sits in, ROCK. Each letter is measured on
    /// its own, with its trailing letter spacing, as RN lays out one Text per letter.
    private static let letters: [(char: String, side: Double, rank: Int)] = [
        ("P", -1, 2), ("E", -1, 1), ("T", -1, 0), ("R", 1, 0), ("O", 1, 1), ("C", 1, 2), ("K", 1, 3),
    ]

    private static func letterWidth(_ char: String) -> Double {
        (char as NSString).size(withAttributes: [.font: titleFont, .kern: -0.6]).width
    }

    /// Centres of the letters and of the gap, for a screen `width` wide.
    private static func titleCentres(width: Double) -> (letters: [Double], gap: Double) {
        let gap = M.titleRock + 6
        let widths = letters.map { letterWidth($0.char) }
        let row = widths.reduce(0, +) + gap
        var x = (width - row) / 2
        var centres: [Double] = []
        var gapCentre = 0.0
        for (k, w) in widths.enumerated() {
            if k == 3 {
                gapCentre = x + gap / 2
                x += gap
            }
            centres.append(x + w / 2)
            x += w
        }
        return (centres, gapCentre)
    }

    private func settings(size: CGSize) -> PetRockCarouselSettings {
        let titleX = Self.titleCentres(width: size.width).gap
        return PetRockCarouselSettings(
            layout: PetRockCarouselLayout(width: size.width, height: size.height, titleX: titleX),
            autoPlay: autoPlay && !reduceMotion,
            reducedMotion: reduceMotion,
            onFocus: onFocus
        )
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let settings = settings(size: size)
        let layout = settings.layout
        let centres = Self.titleCentres(width: size.width).letters
        return TimelineView(.animation) { timeline in
            let _ = engine.step(to: timeline.date, settings: settings)
            let intro = engine.intro.value
            let e = M.clamp(intro, 0, 1)
            let chrome = M.clamp((intro - 0.5) * 2, 0, 1)
            ZStack(alignment: .topLeading) {
                Canvas { ctx, _ in
                    draw(in: ctx, layout: layout, insets: insets)
                }
                .frame(width: size.width, height: size.height)
                .contentShape(Rectangle())
                .gesture(
                    // One recogniser stands in for the race of a pan (after 8 pt of travel) and a
                    // tap (a touch lifted within 400 ms that never travelled that far).
                    DragGesture(minimumDistance: 0)
                        .updating($touching) { _, state, _ in state = true }
                        .onChanged { engine.touchMoved($0, settings: settings) }
                        .onEnded { engine.touchEnded($0, settings: settings) }
                )
                .onChange(of: touching) { _, down in
                    // A drag the system cancels never reaches onEnded.
                    if !down { DispatchQueue.main.async { engine.touchCancelled(settings) } }
                }

                // The title: PET, a gap the little rock sits in, ROCK.
                ZStack(alignment: .topLeading) {
                    ForEach(Self.letters.indices, id: \.self) { k in
                        let letter = Self.letters[k]
                        let p = M.clamp((intro - Double(letter.rank) * M.splitLag) / 0.62, 0, 1)
                        Text(letter.char)
                            .font(.custom("HelveticaNeue-Medium", fixedSize: M.titleSize))
                            .kerning(-0.6)
                            .foregroundStyle(Self.ink)
                            .fixedSize()
                            .opacity(1 - M.clamp(p * 1.5, 0, 1))
                            .offset(x: reduceMotion ? 0 : letter.side * p * p * (M.split + Double(letter.rank) * 18))
                            .position(x: centres[k], y: layout.titleY - 1.4)
                    }
                }
                .frame(width: size.width, height: size.height)
                .opacity(1 - M.clamp(intro * 2, 0, 1))
                .allowsHitTesting(false)

                enterButton(settings: settings, e: e)
                    .padding(.horizontal, 22)
                    .frame(width: size.width, height: size.height - insets.bottom - 14, alignment: .bottom)
                    .allowsHitTesting(!engine.inCarousel)

                header(settings: settings)
                    .frame(width: size.width)
                    .offset(y: insets.top + 8)
                    .opacity(chrome)
                    .allowsHitTesting(engine.inCarousel)

                caption
                    .padding(.horizontal, 24)
                    .frame(width: size.width, height: size.height - insets.bottom - 22, alignment: .bottom)
                    .opacity(chrome)
                    .allowsHitTesting(false)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
        }
        .frame(width: size.width, height: size.height)
        .clipped()
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Pet rocks: \(engine.rocks.map(\.name).joined(separator: ", "))")
    }

    private func enterButton(settings: PetRockCarouselSettings, e: Double) -> some View {
        Button {
            engine.enterPressed(settings)
        } label: {
            Text("Enter")
                .font(.custom(Self.family, fixedSize: 16).weight(.medium))
                .foregroundStyle(Color(hex: 0xf4f1ea))
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(Self.ink, in: Capsule())
        }
        .buttonStyle(PetRockCarouselPressStyle())
        .scaleEffect(engine.press.value)
        .offset(y: reduceMotion ? 0 : e * 120)
        .opacity(1 - M.clamp(e * 2.2, 0, 1))
        .accessibilityLabel("Enter")
    }

    private func header(settings: PetRockCarouselSettings) -> some View {
        Button {
            engine.titlePressed(settings)
        } label: {
            Text("PET ROCK")
                .font(.custom(Self.family, fixedSize: 13).weight(.semibold))
                .kerning(3)
                .foregroundStyle(Self.ink)
                .padding(12)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(-12)
        .accessibilityLabel("Back to the title")
    }

    private var caption: some View {
        let count = engine.rocks.count
        let now = engine.now
        return HStack(alignment: .bottom) {
            ZStack(alignment: .bottomLeading) {
                if let old = engine.oldCaption {
                    let t = M.clamp((now - old.at) / M.captionOut, 0, 1)
                    if t < 1 { captionLine(focus: old.focus, face: old.face).opacity(1 - M.inOutQuad(t)) }
                }
                let t = engine.caption.at < 0 ? 1 : M.clamp((now - engine.caption.at) / M.captionIn, 0, 1)
                captionLine(focus: engine.caption.focus, face: engine.caption.face).opacity(M.inOutQuad(t))
            }
            .frame(maxWidth: .infinity, minHeight: 44, maxHeight: 44, alignment: .bottomLeading)
            Text(String(format: "%02d / %02d", engine.focus + 1, count))
                .font(.custom(Self.family, fixedSize: 13).weight(.medium).monospacedDigit())
                .foregroundStyle(Self.muted)
        }
    }

    private func captionLine(focus: Int, face: Int) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(engine.rocks[focus].name)
                .font(.custom(Self.family, fixedSize: 22).weight(.semibold))
                .kerning(-0.3)
                .foregroundStyle(Self.ink)
            Text(PetRockCarouselFaces.moods[face])
                .font(.custom(Self.family, fixedSize: 14))
                .foregroundStyle(Self.muted)
        }
        .fixedSize()
    }

    // MARK: Drawing

    private func draw(in ctx: GraphicsContext, layout: PetRockCarouselLayout, insets: EdgeInsets) {
        let w = layout.width, h = layout.height
        ctx.fill(
            Path(CGRect(x: 0, y: 0, width: w, height: h)),
            with: .linearGradient(Gradient(colors: [Self.paperTop, Self.paperBottom]), startPoint: .zero, endPoint: CGPoint(x: 0, y: h))
        )
        for index in engine.rocks.indices { drawRock(index, in: ctx, layout: layout) }
        // The paper fades over the rocks at the top and bottom, behind the header and caption.
        let top = insets.top
        ctx.fill(
            Path(CGRect(x: 0, y: 0, width: w, height: top + 130)),
            with: .linearGradient(
                Gradient(colors: [Self.paperTop, Color.rgba(242, 240, 235, 0)]),
                startPoint: CGPoint(x: 0, y: top + 34), endPoint: CGPoint(x: 0, y: top + 130)
            )
        )
        let bottom = h - insets.bottom
        ctx.fill(
            Path(CGRect(x: 0, y: bottom - 130, width: w, height: insets.bottom + 130)),
            with: .linearGradient(
                Gradient(colors: [Color.rgba(230, 226, 219, 0), Self.paperBottom]),
                startPoint: CGPoint(x: 0, y: bottom - 130), endPoint: CGPoint(x: 0, y: bottom - 40)
            )
        )
    }

    /// One painted rock: its ground shadow, the stone, its face's paint, grain and shading, and the
    /// googly eyes on top.
    private func drawRock(_ index: Int, in context: GraphicsContext, layout: PetRockCarouselLayout) {
        let art = engine.art
        guard let layers = art.rocks[index] else { return }
        let rock = engine.rocks[index]
        let p = Double(index) - engine.scroll.value
        let e = engine.intro.value
        let opacity = index == 0 ? 1 : M.clamp(e, 0, 1) * M.clamp(4 - abs(p), 0, 1)
        guard opacity > 0.001 else { return }

        let at = layout.slot(p)
        var x = layout.width / 2
        var y = at.y
        var scale = at.scale
        let titleScale = M.titleRock / layout.big
        if index == 0 {
            // The first rock is the gap in the title: it grows out of it into the carousel.
            x = layout.titleX + (x - layout.titleX) * e
            y = layout.titleY + (y - layout.titleY) * e
            scale = titleScale + (scale - titleScale) * e
        } else if !reduceMotion {
            // The others come in from below as the carousel opens.
            y += (1 - M.clamp(e, 0, 1)) * layout.height * 0.7
        }
        // The tap's squash and wobble belong to the rock in front.
        let front = max(0, 1 - abs(p) * 2)
        let sq = engine.squash.value * front
        let morph = engine.morphs[index]
        let m = morph.mix.value
        let pop = reduceMotion ? 1 : 1 + M.morphPop * 4 * m * (1 - m) * (morph.morphing ? 1 : 0)
        let swing = reduceMotion ? 0 : M.clamp(p, -1.5, 1.5) * 0.12

        var ctx = context
        ctx.opacity = opacity
        ctx.translateBy(x: x, y: y)
        ctx.rotate(by: .radians(rock.tilt + swing + engine.wobble.value * front * M.wobble))
        ctx.scaleBy(x: layout.big / 200 * scale, y: layout.big / 200 * scale)
        // Squash about the base, so the rock stays standing where it is.
        ctx.translateBy(x: 0, y: 90 * rock.aspect)
        ctx.scaleBy(x: (1 + M.squashX * sq) * pop, y: (1 - M.squashY * sq) * pop)
        ctx.translateBy(x: 0, y: -90 * rock.aspect)

        if let shadow = layers.shadow { ctx.draw(Image(decorative: shadow, scale: 1), in: PetRockCarouselArt.shadowBox) }

        var stone = ctx
        stone.clip(to: layers.path)
        let box = PetRockCarouselArt.box
        stone.fill(Path(box), with: .color(Color(hex: rock.tone)))
        if let photo = art.photos[index] {
            // The photo covers a square `photoZoom` times the rock box, laid over the tone in soft light.
            let side = 200 * M.photoZoom
            let w = Double(photo.width), h = Double(photo.height)
            let fill = max(side / w, side / h)
            var surface = stone
            surface.blendMode = .softLight
            surface.opacity *= 0.75
            surface.draw(Image(decorative: photo, scale: 1), in: CGRect(x: -w * fill / 2, y: -h * fill / 2, width: w * fill, height: h * fill))
        }
        faceLayer(morph, slotB: false, in: stone) { c, face in
            if let paint = art.paint[face] { c.draw(Image(decorative: paint, scale: 1), in: box) }
        }
        faceLayer(morph, slotB: true, in: stone) { c, face in
            if let paint = art.paint[face] { c.draw(Image(decorative: paint, scale: 1), in: box) }
        }
        // Grain over stone and paint alike, so the paint looks sprayed on. Each rock samples its own
        // part of the shared texture.
        if let grain = art.grain {
            var g = stone
            g.blendMode = .overlay
            g.opacity *= M.grainOpacity
            let side = PetRockCarouselArt.grainSide
            let shift = Double((rock.seed * 37) % 100)
            g.draw(Image(decorative: grain, scale: 1), in: CGRect(x: -side / 2 + shift - 50, y: -side / 2 + 50 - shift, width: side, height: side))
        }
        if let shade = layers.shade { stone.draw(Image(decorative: shade, scale: 1), in: box) }

        let px = engine.px, py = engine.py
        faceLayer(morph, slotB: false, in: ctx) { c, face in
            guard let domes = art.domes[face] else { return }
            c.draw(Image(decorative: domes, scale: 1), in: box)
            PetRockCarouselFaces.pupils(face, px: px, py: py, in: c)
        }
        faceLayer(morph, slotB: true, in: ctx) { c, face in
            guard let domes = art.domes[face] else { return }
            c.draw(Image(decorative: domes, scale: 1), in: box)
            PetRockCarouselFaces.pupils(face, px: px, py: py, in: c)
        }
    }

    /// One of a rock's two face slots: alone and sharp when it is the one showing, or, during a
    /// morph, faded and blurred by how far the crossfade has gone.
    private func faceLayer(_ morph: PetRockCarouselMorph, slotB: Bool, in ctx: GraphicsContext, draw: @escaping (inout GraphicsContext, Int) -> Void) {
        let face = slotB ? morph.b : morph.a
        if !morph.morphing {
            guard morph.showB == slotB else { return }
            var c = ctx
            draw(&c, face)
            return
        }
        let m = morph.mix.value
        let opacity = slotB ? m : 1 - m
        guard opacity > 0.002 else { return }
        let sigma = (slotB ? 1 - m : m) * M.morphBlur
        var c = ctx
        c.opacity *= opacity
        c.drawLayer { layer in
            if sigma > 0.05 { layer.addFilter(.blur(radius: PetRockCarouselFaces.radius(sigma))) }
            draw(&layer, face)
        }
    }
}

/// The Enter pill gives a little under the finger.
private struct PetRockCarouselPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? 0.97 : 1)
    }
}
