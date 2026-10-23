import SwiftUI
import UIKit

/// A lime splash with a mark in the middle. A dark capsule rises from the bottom edge holding an
/// arrow button, fills with colour and grows into the whole screen, pushing the mark away; then the
/// board settles in: three glazed jars floating in the dark, coins dropping into them, a big headline
/// and a lime button. Drag the capsule up (or tap) to open it, drag the jars, tap a jar for a coin,
/// and press the button to go back to the splash.
struct PillRiseSplash: View {
    /// Plays the splash, the reveal, a few coins and the way back, on a loop.
    var autoPlay = false
    /// The headline, one entry per line.
    var headline = ["90 countries,", "30 currencies.", "One wallet"]
    var link = "See our rates"
    var cta = "Get started"
    /// Splash and button colour, the mark on it, and the board's background.
    var lime = Color(hex: 0xa3eb6e)
    var ink = Color(hex: 0x173300)
    var night = Color(hex: 0x161a14)
    var onGetStarted: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The splash's motion lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = PillRiseSplashEngine()
    @State private var reveal: Image?
    @State private var glazes: [Image?] = [nil, nil, nil]
    // False again once the finger is gone, also when the system takes the touch away.
    @GestureState private var touching = false

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(layout: PillRiseSplashLayout(width: size.width, height: size.height, top: insets.top,
                                               bottom: insets.bottom, lines: headline.count))
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .task { await loadPhotos() }
    }

    private func stage(layout: PillRiseSplashLayout) -> some View {
        let settings = PillRiseSplashSettings(
            autoPlay: autoPlay, reducedMotion: reduceMotion, layout: layout, onGetStarted: onGetStarted
        )
        return TimelineView(.animation) { timeline in
            let frame = engine.step(to: timeline.date, settings: settings)
            let jars = engine.placedJars(for: layout)
            Canvas(rendersAsynchronously: false) { context, _ in
                PillRiseSplashPainter(
                    frame: frame, jars: jars, reveal: reveal, glazes: glazes, headline: headline, link: link,
                    cta: cta, lime: lime, ink: ink, night: night
                ).draw(in: context)
            }
        }
        .frame(width: layout.width, height: layout.height)
        .background(lime)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .updating($touching) { _, state, _ in state = true }
                .onChanged { engine.touchChanged($0) }
                .onEnded { engine.touchEnded($0) }
        )
        .onChange(of: touching) { _, now in
            if !now { engine.touchLost() }
        }
        .accessibilityElement()
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel("\(headline.joined(separator: " ")). \(cta)")
    }

    private func loadPhotos() async {
        async let swirl = PillRiseSplashPhotos.load(PillRiseSplashPhotos.address(PillRiseSplashPhotos.reveal, width: 1200))
        async let first = PillRiseSplashPhotos.load(PillRiseSplashPhotos.address(PillRiseSplashJars.specs[0].photo, width: 360))
        async let second = PillRiseSplashPhotos.load(PillRiseSplashPhotos.address(PillRiseSplashJars.specs[1].photo, width: 360))
        async let third = PillRiseSplashPhotos.load(PillRiseSplashPhotos.address(PillRiseSplashJars.specs[2].photo, width: 360))
        let loaded = await (swirl, first, second, third)
        reveal = loaded.0.map { Image(uiImage: $0) }
        glazes = [loaded.1, loaded.2, loaded.3].map { $0.map { Image(uiImage: $0) } }
    }
}

/// Draws one frame: the splash (mark, capsule, arrow button), the board over it and the way back.
private struct PillRiseSplashPainter {
    let frame: PillRiseSplashFrame
    let jars: [PillRiseSplashPlacedJar]
    let reveal: Image?
    let glazes: [Image?]
    let headline: [String]
    let link: String
    let cta: String
    let lime: Color
    let ink: Color
    let night: Color

    private typealias M = PillRiseSplashMetrics

    private static let mark: Path = {
        var p = Path()
        p.move(to: CGPoint(x: 14, y: 16))
        p.addLine(to: CGPoint(x: 44, y: 16))
        p.addLine(to: CGPoint(x: 54, y: 24.5))
        p.addLine(to: CGPoint(x: 44, y: 33))
        p.addLine(to: CGPoint(x: 14, y: 33))
        p.closeSubpath()
        p.move(to: CGPoint(x: 50, y: 37))
        p.addLine(to: CGPoint(x: 20, y: 37))
        p.addLine(to: CGPoint(x: 10, y: 45.5))
        p.addLine(to: CGPoint(x: 20, y: 54))
        p.addLine(to: CGPoint(x: 50, y: 54))
        p.closeSubpath()
        return p
    }()

    private static let arrow: Path = {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: 9))
        p.addLine(to: CGPoint(x: 0, y: -9))
        p.move(to: CGPoint(x: -7, y: -2))
        p.addLine(to: CGPoint(x: 0, y: -9))
        p.addLine(to: CGPoint(x: 7, y: -2))
        return p
    }()

    func draw(in context: GraphicsContext) {
        drawSplash(context)
        if frame.board > 0 { drawBoard(context) }
        drawExit(context)
    }

    /// The photo, cropped to cover `rect`.
    private func cover(_ size: CGSize, _ rect: CGRect) -> CGRect {
        let scale = max(rect.width / size.width, rect.height / size.height)
        let w = size.width * scale
        let h = size.height * scale
        return CGRect(x: rect.midX - w / 2, y: rect.midY - h / 2, width: w, height: h)
    }

    // MARK: - The splash

    private func drawSplash(_ context: GraphicsContext) {
        let f = frame
        let l = f.layout
        let W = l.width
        let H = l.height
        let full = CGRect(x: 0, y: 0, width: W, height: H)
        let reduced = f.reducedMotion
        // Without big motion the capsule stays at its peek and the colour fades in instead.
        let shape = reduced ? min(f.s, M.peek) : f.s
        let pillTop = M.lerp(shape, [0, M.peek, 0.48, 0.68, 1], [H + 2, l.peekTop, H * 0.3, 0, 0])

        // The capsule pushes the mark up as its top passes it; the mark fades on the way.
        let mid = H / 2
        let push = max(0, mid + M.markSize / 2 + 24 - pillTop)
        let markAlpha = f.logoIn * (1 - M.seg(push, 20, 150)) * (1 - M.seg(f.s, M.peek, 0.6) * (reduced ? 1 : 0))
        if markAlpha > 0 {
            var c = context
            c.opacity = markAlpha
            c.translateBy(x: W / 2, y: mid - push * (reduced ? 0 : 0.85))
            let grow = (0.92 + 0.08 * M.easeOut.value(at: f.logoIn)) * M.markScale
            c.scaleBy(x: grow, y: grow)
            // Skia's skewX leans the mark's rows: the right side rises.
            c.concatenate(CGAffineTransform(a: 1, b: tan(M.markSkew), c: 0, d: 1, tx: 0, ty: 0))
            c.translateBy(x: -M.markSize / 2, y: -M.markSize / 2)
            c.fill(Self.mark, with: .color(ink))
        }

        if f.s > 0.001 {
            let g = shape
            let w = M.lerp(g, [0, 0.48, 0.68, 1], [M.pillWidth, M.pillWidth, M.pillWidth * 1.8, W])
            let r = min(w / 2, M.lerp(g, [0, 0.48, 0.68, 0.9, 1], [M.pillWidth / 2, M.pillWidth / 2, M.pillWidth * 0.9, 46, 0]))
            let pill = Path(roundedRect: CGRect(x: (W - w) / 2, y: pillTop, width: w, height: H - pillTop + 80),
                            cornerSize: CGSize(width: r, height: r), style: .circular)
            var c = context
            c.clip(to: pill)
            c.fill(Path(full), with: .linearGradient(
                Gradient(colors: [Color(hex: 0xf25fb4), Color(hex: 0x8b5cf6), Color(hex: 0x38bdf8)]),
                startPoint: .zero, endPoint: CGPoint(x: W, y: H)
            ))
            if let reveal {
                // The colour inside grows still as the capsule opens.
                let image = c.resolve(reveal)
                let scaled = c.scaled(M.lerp(g, [M.peek, 1], [1.2, 1]), about: CGPoint(x: W / 2, y: H / 2))
                var clipped = scaled
                clipped.clip(to: Path(full))
                clipped.draw(image, in: cover(image.size, full))
            }
            // A dark cap at the top of the capsule, gone by the time it is a third of the way up.
            let cap = 1 - M.seg(g, M.peek + 0.04, 0.46)
            if cap > 0 {
                var capped = c
                capped.opacity = cap
                capped.fill(Path(CGRect(x: 0, y: 0, width: W, height: H + 80)), with: .linearGradient(
                    Gradient(colors: [night, night.opacity(0)]),
                    startPoint: CGPoint(x: 0, y: pillTop + 40), endPoint: CGPoint(x: 0, y: pillTop + 150)
                ))
            }
        }
        if reduced, let reveal {
            let fade = M.seg(f.s, M.peek, 1)
            if fade > 0 {
                var c = context
                c.opacity = fade
                c.clip(to: Path(full))
                let image = c.resolve(reveal)
                c.draw(image, in: cover(image.size, full))
            }
        }

        // The arrow button rides the top of the capsule, then flies off the top and fades.
        let buttonAlpha = M.seg(shape, 0.03, M.peek * 0.9) * (1 - M.seg(shape, 0.52, 0.68))
        if buttonAlpha > 0 {
            let lift = M.lerp(shape, [0.48, 0.7], [0, -110])
            let grow = M.lerp(shape, [0.02, M.peek], [0.55, 1]) * M.lerp(shape, [0.5, 0.7], [1, 0.85])
            var c = context
            c.opacity = buttonAlpha
            c.drawLayer { b in
                b.translateBy(x: W / 2, y: pillTop + M.pillWidth / 2 + lift)
                b.scaleBy(x: grow, y: grow)
                let ring = M.buttonRadius + 4
                b.stroke(Path(ellipseIn: CGRect(x: -ring, y: -ring, width: ring * 2, height: ring * 2)),
                         with: .color(lime.opacity(0.45)), lineWidth: 1.5)
                let br = M.buttonRadius
                b.fill(Path(ellipseIn: CGRect(x: -br, y: -br, width: br * 2, height: br * 2)), with: .color(lime))
                // A soft nudge upwards while the capsule waits at its peek.
                let at = 1 - M.seg(abs(shape - M.peek), 0, 0.04)
                let nudge = reduced ? 0 : -3 * at * (0.5 - 0.5 * cos(.pi * 2 * f.time / 1.5))
                b.translateBy(x: 0, y: nudge)
                b.stroke(Self.arrow, with: .color(ink), style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
            }
        }
    }

    // MARK: - The board

    private func drawBoard(_ context: GraphicsContext) {
        let f = frame
        let l = f.layout
        let W = l.width
        let board = f.board
        let reduced = f.reducedMotion

        var backdrop = context
        backdrop.opacity = M.seg(board, 0, 0.5)
        backdrop.fill(Path(CGRect(x: 0, y: 0, width: W, height: l.height)), with: .color(night))

        // Three steps of progress; the first fills.
        let barAlpha = M.seg(board, 0.35, 0.6)
        if barAlpha > 0 {
            var bar = context
            bar.opacity = barAlpha
            bar.drawLayer { c in
                let track = (W - 48 - 12) / 3
                for step in 0..<3 {
                    let rect = CGRect(x: 24 + Double(step) * (track + 6), y: l.barTop, width: track, height: 3)
                    let shape = Path(roundedRect: rect, cornerRadius: 1.5)
                    c.fill(shape, with: .color(.white.opacity(0.14)))
                    if step == 0 {
                        var fill = c
                        fill.clip(to: shape)
                        let p = M.easeOut.value(at: M.seg(board, 0.5, 1))
                        fill.fill(Path(CGRect(x: rect.minX, y: rect.minY, width: rect.width * p, height: 3)), with: .color(lime))
                    }
                }
            }
        }

        for (index, jar) in jars.enumerated() {
            PillRiseSplashJars.draw(jar, index: index, image: glazes[index], frame: f, in: context)
        }

        // The headline, each line rising out of its own baseline.
        for (index, line) in headline.enumerated() {
            let from = 0.1 + Double(index) * 0.08
            let p = M.easeOut.value(at: M.seg(board, from, from + 0.38))
            let alpha = reduced ? p : M.seg(board, from, from + 0.2)
            guard alpha > 0 else { continue }
            let top = l.headlineTop + Double(index) * M.headlineLine
            var c = context
            c.clip(to: Path(CGRect(x: 24, y: top, width: W - 48, height: M.headlineLine)))
            c.opacity = alpha
            let text = c.resolve(
                Text(line.uppercased())
                    .font(.custom(M.headlineFont, fixedSize: M.headlineSize))
                    .kerning(0.3)
                    .foregroundColor(Color(hex: 0xf2f5ec))
            )
            let rise = reduced ? 0 : (1 - p) * M.headlineLine * 1.1
            // The line box is 45 pt tall and hangs from the bottom of its 41 pt slot.
            let box = top + M.headlineLine - (M.headlineLine + 4)
            let size = text.measure(in: CGSize(width: 1000, height: 200))
            let baseline = text.firstBaseline(in: size)
            c.draw(text, at: CGPoint(x: W / 2, y: box + (M.headlineLine + 4 - size.height) / 2 + baseline + rise), anchor: UnitPoint(x: 0.5, y: baseline / size.height))
        }

        let linkAlpha = M.seg(board, 0.55, 0.85)
        if linkAlpha > 0 {
            var c = context
            c.opacity = linkAlpha
            c.draw(
                Text(link).font(.system(size: 15, weight: .semibold)).underline(color: lime).foregroundColor(lime),
                at: CGPoint(x: W / 2, y: l.linkTop), anchor: .top
            )
        }

        let ctaAlpha = M.seg(board, 0.42, 0.6)
        if ctaAlpha > 0 {
            let p = M.easeOut.value(at: M.seg(board, 0.42, 0.85))
            let rect = l.ctaRect
            var c = context
            c.opacity = ctaAlpha
            c.translateBy(x: rect.midX, y: rect.midY + (reduced ? 0 : (1 - p) * 90))
            let scale = 1 - f.press * 0.03
            c.scaleBy(x: scale, y: scale)
            c.drawLayer { b in
                let local = CGRect(x: -rect.width / 2, y: -rect.height / 2, width: rect.width, height: rect.height)
                b.fill(Path(roundedRect: local, cornerRadius: rect.height / 2, style: .continuous), with: .color(lime))
                b.draw(Text(cta).font(.system(size: 17, weight: .semibold)).kerning(-0.2).foregroundColor(ink), at: .zero)
            }
        }
    }

    // MARK: - The way back

    /// The button opens into the full screen, from its own frame.
    private func drawExit(_ context: GraphicsContext) {
        let e = frame.exit
        guard e > 0 else { return }
        let l = frame.layout
        if frame.reducedMotion {
            var c = context
            c.opacity = e
            c.fill(Path(CGRect(x: 0, y: 0, width: l.width, height: l.height)), with: .color(lime))
            return
        }
        let rect = CGRect(
            x: M.ctaLeft * (1 - e),
            y: l.ctaTop * (1 - e),
            width: l.ctaWidth + (l.width - l.ctaWidth) * e,
            height: M.ctaHeight + (l.height - M.ctaHeight) * e
        )
        let radius = min((M.ctaHeight / 2) * (1 - e) + 46 * sin(.pi * min(e, 0.5)) * (1 - e), min(rect.width, rect.height) / 2)
        context.fill(Path(roundedRect: rect, cornerRadius: radius, style: .circular), with: .color(lime))
    }
}

// MARK: - Photos

/// Downloads a photo once and keeps it decoded.
enum PillRiseSplashPhotos {
    // The colour that fills the capsule as it rises: an iridescent swirl, drawn full screen.
    static let reveal = "photo-1620421680010-0766ff230392"

    private static let cache = NSCache<NSString, UIImage>()

    static func address(_ id: String, width: Int) -> String {
        "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(width)&q=80"
    }

    static func load(_ address: String) async -> UIImage? {
        if let hit = cache.object(forKey: address as NSString) { return hit }
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        // Decoded off the main thread, so the first frame that draws it does not stall.
        let ready = await image.byPreparingForDisplay() ?? image
        cache.setObject(ready, forKey: address as NSString)
        return ready
    }
}
