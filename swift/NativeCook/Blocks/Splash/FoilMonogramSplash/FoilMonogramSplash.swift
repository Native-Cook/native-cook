import SwiftUI
import UIKit

/// A splash for an invented bakery, Loaven. On espresso, a knot of two gold-foil loops draws on
/// stroke by stroke, woven over and under; a light sweeps across the foil while the wordmark rises
/// through its baseline letter by letter. Then the letters drop away, the knot flies up into the
/// app's header and an arched doorway opens from the middle onto the app.
struct FoilMonogramSplash: View {
    /// Plays the splash, hands off to the app, closes and starts over, every loop.
    var autoPlay = false
    var onReserve: (() -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var clock = FoilMonogramSplashClock()
    @State private var lightHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var selectHaptic = UISelectionFeedbackGenerator()

    private typealias S = FoilMonogramSplashScript

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let reduced = reduceMotion
        return TimelineView(.animation) { timeline in
            let t = clock.step(to: timeline.date, looping: autoPlay && !reduced)
            let layout = FoilMonogramSplashLayout(size: size, insets: insets)
            ZStack(alignment: .topLeading) {
                FoilMonogramSplashApp(
                    t: t,
                    reduced: reduced,
                    layout: layout,
                    onReplay: {
                        clock.replay()
                        lightHaptic.impactOccurred()
                    },
                    onReserve: {
                        selectHaptic.selectionChanged()
                        onReserve?()
                    }
                )
                .allowsHitTesting(t >= S.archAt + S.archTime * 0.7 && t < S.closeAt)

                Canvas { context, _ in
                    FoilMonogramSplashArt.draw(
                        context,
                        t: t,
                        reduced: reduced,
                        layout: layout,
                        shineX: clock.shineX(centre: layout.cx, at: timeline.date)
                    )
                }
                .allowsHitTesting(false)

                FoilMonogramSplashTagline(t: t, reduced: reduced)
                    .frame(width: size.width)
                    .offset(y: layout.baseline + 22)
                    .allowsHitTesting(false)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .background(FoilMonogramSplashArt.splash)
        .clipped()
        .contentShape(Rectangle())
        // A drag on the splash moves the light across the foil; a tap plays the rest faster.
        .gesture(
            DragGesture(minimumDistance: 6)
                .onChanged { value in clock.dragChanged(x: value.location.x, centre: size.width / 2, at: .now) }
                .onEnded { _ in clock.dragEnded(at: .now) }
        )
        .onTapGesture {
            if clock.tap(reduced: reduced) {
                lightHaptic.impactOccurred()
            } else {
                clock.holdApp()
            }
        }
    }
}

/// Where things sit on this screen.
struct FoilMonogramSplashLayout {
    let size: CGSize
    let insets: EdgeInsets

    private typealias S = FoilMonogramSplashScript
    private typealias K = FoilMonogramSplashKnot

    var cx: Double { size.width / 2 }
    var cy: Double { size.height * 0.43 }
    var headerScale: Double { S.headerMark / K.height }
    var hx: Double { S.headerLeft + K.width * headerScale / 2 }
    var hy: Double { insets.top + 10 + S.headerMark / 2 }
    var baseline: Double { cy + K.height * S.markScale / 2 + 74 }
    var archReach: Double { hypot(max(cx, size.width - cx), cy) + 40 }
}

// MARK: - The splash

/// The splash drawn in one canvas: the espresso ground with its arched doorway, the wordmark and the
/// knot, in that order.
enum FoilMonogramSplashArt {
    static let splash = Color(hex: 0x130d0a)
    static let splashLight = Color(hex: 0x2a1e16)
    static let rim = Color(hex: 0xd8b77e)
    static let foilEdge = Color(hex: 0x4e341b)
    static let foil = Gradient(stops: [
        .init(color: Color(hex: 0x6f4b29), location: 0),
        .init(color: Color(hex: 0xc9a167), location: 0.22),
        .init(color: Color(hex: 0xf4e2b6), location: 0.38),
        .init(color: Color(hex: 0xb8894e), location: 0.55),
        .init(color: Color(hex: 0xe3c68e), location: 0.7),
        .init(color: Color(hex: 0x8a6236), location: 0.86),
        .init(color: Color(hex: 0x5e3f22), location: 1),
    ])
    static let shine = Gradient(colors: [
        Color(.sRGB, red: 1, green: 248 / 255, blue: 230 / 255, opacity: 0),
        Color(.sRGB, red: 1, green: 248 / 255, blue: 230 / 255, opacity: 0.78),
        Color(.sRGB, red: 1, green: 248 / 255, blue: 230 / 255, opacity: 0),
    ])

    static let word = Array("LOAVEN")
    static let wordSize: CGFloat = 34
    static let wordTrack: CGFloat = 11

    private typealias S = FoilMonogramSplashScript
    private typealias K = FoilMonogramSplashKnot

    // Paths built once.
    private static let loops = [K.loop(1), K.loop(-1)]
    private static let arch = K.arch(outline: false)
    private static let archRim = K.arch(outline: true)

    static func draw(_ context: GraphicsContext, t: Double, reduced: Bool, layout: FoilMonogramSplashLayout, shineX: Double) {
        drawGround(context, t: t, reduced: reduced, layout: layout)
        drawWordmark(context, t: t, reduced: reduced, layout: layout, shineX: shineX)
        drawKnot(context, t: t, reduced: reduced, layout: layout, shineX: shineX)
    }

    /// The splash, with the arch cut out of it as it opens, and a thin gold rim along the opening.
    private static func drawGround(_ context: GraphicsContext, t: Double, reduced: Bool, layout: FoilMonogramSplashLayout) {
        let o = S.opened(t)
        let opacity: Double
        if !reduced {
            opacity = o >= 0.999 ? 0 : 1
        } else if t >= S.closeAt {
            opacity = S.step(t, S.closeAt, 0.45)
        } else {
            opacity = 1 - S.step(t, S.archAt, 0.45)
        }
        let scale = reduced ? 0.001 : max(0.001, o * layout.archReach)
        let transform = CGAffineTransform(translationX: layout.cx, y: layout.cy).scaledBy(x: scale, y: scale)
        if opacity > 0 {
            var ground = context
            ground.opacity = opacity
            ground.drawLayer { layer in
                let rect = CGRect(origin: .zero, size: layout.size)
                layer.fill(
                    Path(rect),
                    with: .radialGradient(
                        Gradient(colors: [splashLight, splash]),
                        center: CGPoint(x: layout.cx, y: layout.cy),
                        startRadius: 0,
                        endRadius: layout.size.height * 0.75
                    )
                )
                layer.blendMode = .destinationOut
                layer.fill(arch.applying(transform), with: .color(.black))
            }
        }
        if !reduced && o > 0 && o < 1 {
            var edge = context
            // Faded out while the doorway is still a speck, so it never shows as a dot.
            edge.opacity = 0.85 * sin(.pi * o) * min(1, o * layout.archReach / S.rimFade)
            edge.stroke(archRim.applying(transform), with: .color(rim), lineWidth: 1.4)
        }
    }

    /// The wordmark: each letter, in foil, slides through a slot at its baseline.
    private static func drawWordmark(_ context: GraphicsContext, t: Double, reduced: Bool, layout: FoilMonogramSplashLayout, shineX: Double) {
        let font = Font.custom("Georgia-Bold", fixedSize: wordSize)
        let letters = word.map { context.resolve(Text(String($0)).font(font).foregroundStyle(.white)) }
        let box = CGSize(width: 400, height: 200)
        let widths = letters.map { max(1, $0.measure(in: box).width) }
        let total = widths.reduce(0, +) + wordTrack * CGFloat(word.count - 1)
        let baseline = layout.baseline
        let reveal = wordSize * 0.95
        var x = layout.cx - total / 2
        context.drawLayer { layer in
            for (i, letter) in letters.enumerated() {
                let w = widths[i]
                defer { x += w + wordTrack }
                let shift = reduced ? 0 : S.letterShift(t, i)
                let opacity = reduced
                    ? S.step(t, S.lettersAt, 0.5) * (1 - S.step(t, S.handoffAt, 0.3))
                    : (shift >= 0.999 || shift <= -0.999 ? 0 : 1)
                if opacity <= 0 { continue }
                var slot = layer
                slot.opacity = opacity
                slot.clip(to: Path(CGRect(x: x - 6, y: baseline - wordSize * 0.86, width: w + 12, height: wordSize * 1.02)))
                let lift = shift * reveal
                let top = baseline - letter.firstBaseline(in: box) + lift
                slot.drawLayer { glyph in
                    glyph.draw(letter, at: CGPoint(x: x, y: top), anchor: .topLeading)
                    glyph.blendMode = .sourceAtop
                    glyph.fill(
                        Path(CGRect(x: x - 2, y: baseline - wordSize + lift, width: w + 4, height: wordSize * 1.4)),
                        with: .linearGradient(
                            foil,
                            startPoint: CGPoint(x: x, y: baseline - wordSize * 0.75 + lift),
                            endPoint: CGPoint(x: x + w, y: baseline + lift)
                        )
                    )
                }
            }
            layer.blendMode = .sourceAtop
            layer.fill(
                Path(CGRect(x: 0, y: baseline - wordSize * 1.2, width: layout.size.width, height: wordSize * 1.6)),
                with: .linearGradient(
                    shine,
                    startPoint: CGPoint(x: shineX - 46, y: baseline - 34),
                    endPoint: CGPoint(x: shineX + 46, y: baseline + 34)
                )
            )
        }
    }

    /// The knot: two foil loops drawn on from their tips, woven at the crossings, under the light.
    private static func drawKnot(_ context: GraphicsContext, t: Double, reduced: Bool, layout: FoilMonogramSplashLayout, shineX: Double) {
        let opacity = S.knotOpacity(t, reduced)
        if opacity <= 0 { return }
        let m = S.moved(t, reduced)
        let s = S.markScale + (layout.headerScale - S.markScale) * m
        let x = layout.cx + (layout.hx - layout.cx) * m
        let y = layout.cy + (layout.hy - layout.cy) * m
        let foilShading = GraphicsContext.Shading.linearGradient(
            foil,
            startPoint: CGPoint(x: -K.width / 2, y: -K.height / 2),
            endPoint: CGPoint(x: K.width / 2, y: K.height / 2)
        )
        let edge = GraphicsContext.Shading.color(foilEdge)
        let round = StrokeStyle(lineWidth: K.stroke, lineCap: .round)
        let roundCore = StrokeStyle(lineWidth: K.stroke - 1.6, lineCap: .round)
        let butt = StrokeStyle(lineWidth: K.stroke, lineCap: .butt)
        let buttCore = StrokeStyle(lineWidth: K.stroke - 1.6, lineCap: .butt)

        var knot = context
        knot.opacity = opacity
        knot.drawLayer { layer in
            layer.translateBy(x: x, y: y)
            layer.scaleBy(x: s, y: s)
            for i in 0..<2 {
                let end = S.drawn(t, i, reduced)
                // A round cap would show as a dot before the stroke starts.
                if end <= 0.001 { continue }
                let path = loops[i].trimmedPath(from: 0, to: end)
                layer.stroke(path, with: edge, style: round)
                layer.stroke(path, with: foilShading, style: roundCore)
            }
            // Where a strand passes over the other, it clears a gap through the one below and is
            // drawn again; its gold core runs a little further each way so no seam shows.
            for crossing in K.crossings {
                let drawn = S.drawn(t, crossing.over, reduced)
                let from = crossing.at - K.overSpan
                if drawn <= from + 0.001 { continue }
                let path = loops[crossing.over]
                let end = min(crossing.at + K.overSpan, drawn)
                let endCore = min(crossing.at + K.overSpan + K.coreOverlap, drawn)
                var cut = layer
                cut.blendMode = .destinationOut
                cut.stroke(path.trimmedPath(from: from, to: end), with: .color(.black), style: StrokeStyle(lineWidth: K.stroke + K.gap * 2, lineCap: .butt))
                layer.stroke(path.trimmedPath(from: from, to: end), with: edge, style: butt)
                layer.stroke(path.trimmedPath(from: from - K.coreOverlap, to: endCore), with: foilShading, style: buttCore)
            }
            // The light, in the knot's own units.
            let lx = (shineX - x) / s
            layer.blendMode = .sourceAtop
            layer.fill(
                Path(CGRect(x: -K.width, y: -K.height, width: K.width * 2, height: K.height * 2)),
                with: .linearGradient(shine, startPoint: CGPoint(x: lx - 46, y: -34), endPoint: CGPoint(x: lx + 46, y: 34))
            )
        }
    }
}

/// The small line under the wordmark.
private struct FoilMonogramSplashTagline: View {
    let t: Double
    let reduced: Bool

    private typealias S = FoilMonogramSplashScript

    var body: some View {
        let enter = S.bezier(S.easeOut, S.step(t, S.tagAt, S.tagIn))
        let leave = S.step(t, S.handoffAt, 0.25)
        Text("HOLLIS STREET BAKEHOUSE")
            .font(.system(size: 11, weight: .semibold))
            .kerning(3.2)
            .foregroundStyle(Color(.sRGB, red: 216 / 255, green: 183 / 255, blue: 126 / 255, opacity: 0.72))
            .opacity(enter * (1 - leave))
            .offset(y: reduced ? 0 : (1 - enter) * 6)
    }
}

// MARK: - The app

/// The app behind the splash: today's bake at a bakery, with a header the knot lands in.
private struct FoilMonogramSplashApp: View {
    let t: Double
    let reduced: Bool
    let layout: FoilMonogramSplashLayout
    let onReplay: () -> Void
    let onReserve: () -> Void

    static let cream = Color(hex: 0xf4ede3)
    static let ink = Color(hex: 0x1f1812)
    static let muted = Color(hex: 0x8a7a68)
    static let card = Color(hex: 0xe7dccd)

    private typealias S = FoilMonogramSplashScript
    private typealias K = FoilMonogramSplashKnot

    var body: some View {
        let insets = layout.insets
        let markWidth = K.width * S.headerMark / K.height
        let heroHeight = min(310, max(210, layout.size.height * 0.34))
        ZStack(alignment: .topLeading) {
            Self.cream

            // The header: the knot lands at its left; the name and the bag rise in beside it.
            Text("LOAVEN")
                .font(.custom("Georgia-Bold", fixedSize: 17))
                .kerning(4.5)
                .foregroundStyle(Self.ink)
                .frame(height: S.headerMark)
                .rise(t, 1, reduced)
                .offset(x: S.headerLeft + markWidth + 10, y: insets.top + 10)
            Button(action: onReplay) {
                Color.clear.frame(width: markWidth + 8, height: S.headerMark).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Replay the splash")
            .offset(x: S.headerLeft - 4, y: insets.top + 10)
            bag
                .rise(t, 1, reduced)
                .offset(x: layout.size.width - 20 - 40, y: insets.top + 8)

            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("TUESDAY MORNING")
                        .font(.system(size: 12, weight: .semibold))
                        .kerning(1.6)
                        .foregroundStyle(Self.muted)
                    Text("The first loaves\nare out.")
                        .font(.custom("Georgia-Bold", fixedSize: 32))
                        .kerning(-0.4)
                        .lineSpacing(37 - (UIFont(name: "Georgia-Bold", size: 32)?.lineHeight ?? 37))
                        .foregroundStyle(Self.ink)
                }
                .rise(t, 2, reduced)

                hero(height: heroHeight).rise(t, 3, reduced)

                HStack(alignment: .firstTextBaseline) {
                    Text("From the shelf")
                        .font(.custom("Georgia-Bold", fixedSize: 20))
                        .foregroundStyle(Self.ink)
                    Spacer()
                    Text("See all")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Self.muted)
                }
                .padding(.top, 2)
                .rise(t, 4, reduced)

                HStack(spacing: 12) {
                    ForEach(Array(FoilMonogramSplashShelf.items.enumerated()), id: \.offset) { i, item in
                        tile(item).rise(t, 5 + i, reduced)
                    }
                }
                .padding(.top, -6)
            }
            .padding(.horizontal, 20)
            .padding(.top, insets.top + 70)

            Button(action: onReserve) {
                Text("Reserve for pickup")
                    .font(.system(size: 16, weight: .semibold))
                    .kerning(-0.1)
                    .foregroundStyle(Self.cream)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Self.ink, in: Capsule())
            }
            .buttonStyle(FoilMonogramSplashPress())
            .padding(.horizontal, 20)
            .rise(t, 7, reduced)
            .frame(width: layout.size.width, height: layout.size.height - insets.bottom - 12, alignment: .bottom)
        }
        .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
    }

    private var bag: some View {
        ZStack(alignment: .topTrailing) {
            Image(systemName: "bag")
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(Self.ink)
                .frame(width: 40, height: 40)
                .overlay(Circle().strokeBorder(Self.ink.opacity(0.22), lineWidth: 0.5))
            Text("2")
                .font(.system(size: 10, weight: .bold).monospacedDigit())
                .foregroundStyle(Self.cream)
                .frame(minWidth: 16, minHeight: 16)
                .background(Self.ink, in: Circle())
                .offset(x: -3, y: 4)
        }
    }

    private func hero(height: Double) -> some View {
        ZStack(alignment: .bottomLeading) {
            FoilMonogramSplashPhoto(url: FoilMonogramSplashShelf.hero)
            LinearGradient(
                stops: [
                    .init(color: Color(.sRGB, red: 14 / 255, green: 9 / 255, blue: 6 / 255, opacity: 0), location: 0.45),
                    .init(color: Color(.sRGB, red: 14 / 255, green: 9 / 255, blue: 6 / 255, opacity: 0.78), location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            HStack(alignment: .bottom, spacing: 12) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Country sourdough")
                        .font(.system(size: 20, weight: .bold))
                        .kerning(-0.3)
                        .foregroundStyle(Color(hex: 0xfbf6ee))
                    Text("Out of the oven at 6:40 · 14 left")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(Color(hex: 0xfbf6ee, opacity: 0.75))
                }
                Spacer(minLength: 0)
                Text("$9")
                    .font(.system(size: 15, weight: .bold).monospacedDigit())
                    .foregroundStyle(Self.ink)
                    .padding(.horizontal, 13)
                    .frame(height: 32)
                    .background(Color(hex: 0xfbf6ee), in: Capsule())
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 16)
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .background(Self.card)
        .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
    }

    private func tile(_ item: (name: String, price: String, url: String)) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            FoilMonogramSplashPhoto(url: item.url)
                .frame(maxWidth: .infinity)
                .frame(height: 112)
                .background(Self.card)
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            HStack(spacing: 8) {
                Text(item.name)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Self.ink)
                    .lineLimit(1)
                Spacer(minLength: 0)
                Text(item.price)
                    .font(.system(size: 15, weight: .semibold).monospacedDigit())
                    .foregroundStyle(Self.muted)
            }
            .padding(.horizontal, 2)
        }
        .frame(maxWidth: .infinity)
    }
}

private extension View {
    /// An app element that rises into place as the doorway opens.
    func rise(_ t: Double, _ k: Int, _ reduced: Bool) -> some View {
        let p = FoilMonogramSplashScript.appIn(t, k)
        return opacity(p).offset(y: reduced ? 0 : (1 - p) * FoilMonogramSplashScript.appRise)
    }
}

/// The dark pill dips to 0.97 under the finger.
private struct FoilMonogramSplashPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: configuration.isPressed ? 0.11 : 0.16), value: configuration.isPressed)
    }
}

/// A photo that fills its frame; until it loads, or if it fails, the frame's colour shows.
private struct FoilMonogramSplashPhoto: View {
    let url: String

    var body: some View {
        Color.clear.overlay {
            AsyncImage(url: URL(string: url), transaction: Transaction(animation: .easeOut(duration: 0.25))) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill()
                } else {
                    Color.clear
                }
            }
        }
        .clipped()
    }
}

enum FoilMonogramSplashShelf {
    private static func photo(_ id: String, _ w: Int) -> String {
        "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(w)&q=80"
    }

    static let hero = photo("photo-1567042661848-7161ce446f85", 1000)
    static let items: [(name: String, price: String, url: String)] = [
        ("Seeded rye", "$8", photo("photo-1509440159596-0249088772ff", 560)),
        ("Twin baguettes", "$6", photo("photo-1622808516114-02a5749cd965", 560)),
    ]
}
