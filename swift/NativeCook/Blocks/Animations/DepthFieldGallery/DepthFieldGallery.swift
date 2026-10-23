import SwiftUI
import UIKit

/// A field of cards floating in space, photos printed in black and white or lime among pieces of a
/// dotted identity. By itself the camera flies from card to card, drifting in on each, and now and
/// then the lights turn over on the way. Dragging flies the camera through the field and lands it
/// on the nearest card; tapping a card flies to it. The switch turns the lights over by hand, the
/// new scene spreading from it across the screen.
struct DepthFieldGallery: View {
    /// What the cards in the field show; they repeat as far as the field runs.
    var cards: [DepthFieldGalleryCard] = DepthFieldGalleryCard.samples
    /// Flies from card to card, drifting in on each, and turns the lights over now and then.
    var autoPlay = false
    /// The identity's colour: the dotted cards, the lime prints and the switch.
    var accent: UInt32 = 0xd8f05a
    /// Called with a card when the camera is sent to it by hand.
    var onFocus: ((DepthFieldGalleryCard) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The camera lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine: DepthFieldGalleryEngine

    /// `dark` starts on the dark scene.
    init(
        cards: [DepthFieldGalleryCard] = DepthFieldGalleryCard.samples,
        autoPlay: Bool = false,
        dark: Bool = false,
        accent: UInt32 = 0xd8f05a,
        onFocus: ((DepthFieldGalleryCard) -> Void)? = nil
    ) {
        self.cards = cards
        self.autoPlay = autoPlay
        self.accent = accent
        self.onFocus = onFocus
        _engine = State(wrappedValue: DepthFieldGalleryEngine(dark: dark))
    }

    var body: some View {
        // The block draws under the system bars and keeps its labels clear of them itself.
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
        .task(id: "\(cards.count)-\(accent)") { await loadPhotos() }
    }

    private var settings: DepthFieldGallerySettings {
        DepthFieldGallerySettings(cards: cards, autoPlay: autoPlay && !reduceMotion, reducedMotion: reduceMotion, accent: accent, onFocus: onFocus)
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let settings = settings
        return TimelineView(.animation) { timeline in
            let frame = engine.step(to: timeline.date, width: size.width, height: size.height, bottom: insets.bottom, settings: settings)
            DepthFieldGalleryScene(
                frame: frame,
                field: engine.field,
                cards: cards,
                accent: accent,
                insets: insets,
                reducedMotion: reduceMotion
            )
        }
        .frame(width: size.width, height: size.height)
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { engine.touchMoved($0) }
                .onEnded { engine.touchEnded($0) }
        )
        .accessibilityElement()
        .accessibilityLabel("Depth field gallery")
        .accessibilityHint("Drag to fly through the cards; tap a card to fly to it")
        .accessibilityAction { engine.turnByHand() }
    }

    // Each photo arrives on its own and fades in; until then its card shows its paper.
    private func loadPhotos() async {
        engine.prepare(cards)
        let accent = accent
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for (index, card) in cards.enumerated() {
                guard let photo = card.photo else { continue }
                let print = card.print
                group.addTask { (index, await DepthFieldGalleryPhotos.load(photo.uri, print: print, accent: accent)) }
            }
            for await (index, image) in group {
                if let image { engine.photoArrived(image, card: index) }
            }
        }
    }
}

/// Everything one frame draws: the ground, the cards far to near, and the labels and switch in
/// each scene's ink, cut by the spreading edge.
private struct DepthFieldGalleryScene: View {
    let frame: DepthFieldGalleryFrame
    let field: [DepthFieldGalleryPlacement]
    let cards: [DepthFieldGalleryCard]
    let accent: UInt32
    let insets: EdgeInsets
    let reducedMotion: Bool

    private typealias M = DepthFieldGalleryMetrics

    var body: some View {
        let now = frame.elapsed
        let arrive = reducedMotion ? 1 : DepthFieldGalleryCurve.outCubic(M.clamp01(now / 0.6))
        let unit = DepthFieldGalleryFaces.unit

        ZStack(alignment: .topLeading) {
            Canvas(rendersAsynchronously: false) { context, size in
                drawGround(in: &context, size: size)
            }

            ForEach(frame.shown, id: \.index) { item in
                let p = field[item.index]
                let alpha = item.alpha * arrive
                if alpha > 0.004 {
                    let w = p.width * unit
                    DepthFieldGalleryFace(
                        card: cards[p.card],
                        index: p.card,
                        loaded: frame.photos.indices.contains(p.card) ? frame.photos[p.card] : nil,
                        sharper: frame.sharp.first { $0.card == p.card }?.loaded,
                        accent: accent,
                        dark: frame.theme,
                        now: now,
                        w: w,
                        h: w * p.aspect
                    )
                    .opacity(alpha)
                    .projectionEffect(item.projection(unit: unit, placement: p))
                }
            }

            Canvas(rendersAsynchronously: false) { context, size in
                drawChrome(in: &context, size: size)
            }
        }
        .allowsHitTesting(false)
    }

    // MARK: The ground

    private func drawGround(in context: inout GraphicsContext, size: CGSize) {
        let t = frame.theme
        let whole = Path(CGRect(origin: .zero, size: size))
        if reducedMotion {
            context.fill(whole, with: .color(Color(hex: Self.mix(M.lightBg, M.darkBg, t))))
            return
        }
        context.fill(whole, with: .color(Color(hex: M.lightBg)))
        let radius = t * M.reach(width: size.width, height: size.height, bottom: insets.bottom)
        if radius > 0 {
            context.fill(circle(radius, size: size), with: .color(Color(hex: M.darkBg)))
        }
    }

    private func circle(_ radius: Double, size: CGSize) -> Path {
        let s = M.switchCentre(width: size.width, height: size.height, bottom: insets.bottom)
        let r = max(0, radius)
        return Path(ellipseIn: CGRect(x: s.x - r, y: s.y - r, width: r * 2, height: r * 2))
    }

    /// Mixes two colours a channel at a time, rounded, as the RN `mix` does.
    static func mix(_ a: UInt32, _ b: UInt32, _ t: Double) -> UInt32 {
        var out: UInt32 = 0
        for shift: UInt32 in [16, 8, 0] {
            let x = Double((a >> shift) & 0xff)
            let y = Double((b >> shift) & 0xff)
            out |= UInt32((x + (y - x) * t).rounded(.toNearestOrAwayFromZero)) << shift
        }
        return out
    }

    // MARK: The chrome

    private func drawChrome(in context: inout GraphicsContext, size: CGSize) {
        let t = frame.theme
        if reducedMotion {
            if t < 1 { paintChrome(in: context, size: size, bg: M.lightBg, ink: M.lightInk, alpha: 1 - t) }
            if t > 0 { paintChrome(in: context, size: size, bg: M.darkBg, ink: M.darkInk, alpha: t) }
            return
        }
        let reach = M.reach(width: size.width, height: size.height, bottom: insets.bottom)
        let radius = t * reach
        let spread = circle(radius, size: size)
        if radius < reach {
            var outside = context
            var cut = Path(CGRect(origin: .zero, size: size))
            cut.addPath(spread)
            outside.clip(to: cut, style: FillStyle(eoFill: true))
            paintChrome(in: outside, size: size, bg: M.lightBg, ink: M.lightInk, alpha: 1)
        }
        if radius > 0 {
            var inside = context
            inside.clip(to: spread)
            paintChrome(in: inside, size: size, bg: M.darkBg, ink: M.darkInk, alpha: 1)
        }
    }

    private func paintChrome(in context: GraphicsContext, size: CGSize, bg: UInt32, ink: UInt32, alpha: Double) {
        let width = size.width
        let height = size.height
        var context = context
        let ground = Color(hex: bg)

        // The edges fade into the scene's ground, under the labels.
        var fade = context
        fade.opacity = alpha
        let topFade = insets.top + M.edgeFade
        fade.fill(Path(CGRect(x: 0, y: 0, width: width, height: topFade)), with: .linearGradient(
            Gradient(colors: [ground.opacity(Double(0xd9) / 255), ground.opacity(0)]),
            startPoint: .zero, endPoint: CGPoint(x: 0, y: topFade)
        ))
        let low = height - insets.bottom - M.edgeFade - 40
        fade.fill(Path(CGRect(x: 0, y: low, width: width, height: height - low)), with: .linearGradient(
            Gradient(colors: [ground.opacity(0), ground.opacity(Double(0xe6) / 255)]),
            startPoint: CGPoint(x: 0, y: low), endPoint: CGPoint(x: 0, y: height)
        ))

        let chromeIn = frame.chrome
        let color = Color(hex: ink)
        let top = insets.top + 26
        let label = DepthFieldGallerySkiaText.font("HelveticaNeue-Medium", 11)
        func tracked(_ text: String, x: Double, baseline: Double, opacity: Double) {
            guard opacity > 0 else { return }
            let run = DepthFieldGallerySkiaText.tracked(text, label, tracking: 1)
            var layer = context
            layer.opacity = opacity
            for (i, letter) in run.letters.enumerated() where letter != " " {
                Self.draw(Text(letter).font(.custom("HelveticaNeue-Medium", fixedSize: 11)).foregroundColor(color), in: &layer, x: x + run.at[i], baseline: baseline)
            }
        }
        func trackedWidth(_ text: String) -> Double {
            DepthFieldGallerySkiaText.tracked(text, label, tracking: 1).width
        }
        tracked("ALOFT STUDIO", x: 20, baseline: top, opacity: alpha * chromeIn)
        tracked("DEPTH FIELD", x: width / 2 - trackedWidth("DEPTH FIELD") / 2, baseline: top, opacity: alpha * chromeIn)

        // The card the camera is on, numbered top right and named bottom left: the new one rises in
        // as the last one rises away.
        let base = height - insets.bottom - 22
        func numbered(_ card: Int) -> String {
            String(format: "%02d / %02d", card + 1, cards.count)
        }
        func caption(_ card: Int, shown: Double, rise: Double) {
            guard shown > 0.01 else { return }
            let number = numbered(card)
            tracked(number, x: width - 20 - trackedWidth(number), baseline: top + rise * 0.5, opacity: alpha * chromeIn * shown)
            tracked(cards[card].note.uppercased(), x: 20, baseline: base - 36 + rise, opacity: alpha * chromeIn * shown * 0.6)
            var title = context
            title.opacity = alpha * chromeIn * shown
            Self.draw(Text(cards[card].title).font(.custom("HelveticaNeue-Light", fixedSize: 30)).foregroundColor(color), in: &title, x: 20, baseline: base + rise)
        }
        let current = field[frame.focus].card
        let last = field[frame.lastFocus].card
        let fresh = DepthFieldGalleryCurve.outCubic(M.clamp01((frame.elapsed - frame.focusAt) / M.captionIn))
        if last != current { caption(last, shown: 1 - fresh, rise: -fresh * M.captionRise) }
        caption(current, shown: fresh, rise: (1 - fresh) * M.captionRise)

        // The switch: a track, and a lime knob that slides across as the lights turn over.
        let s = M.switchCentre(width: width, height: height, bottom: insets.bottom)
        context.stroke(
            Path(roundedRect: CGRect(x: s.x - 22, y: s.y - 12, width: 44, height: 24), cornerRadius: 12),
            with: .color(color.opacity(0.5 * alpha * chromeIn)),
            lineWidth: 1
        )
        let knobX = s.x - 10 + 20 * frame.theme
        context.fill(Path(ellipseIn: CGRect(x: knobX - 8, y: s.y - 8, width: 16, height: 16)), with: .color(Color(hex: accent).opacity(alpha * chromeIn)))
        // The RN paint's colour is set again for the dot in the knob, which puts its alpha back to one.
        context.fill(Path(ellipseIn: CGRect(x: knobX - 2.2, y: s.y - 2.2, width: 4.4, height: 4.4)), with: .color(Color(hex: M.cardInk)))
    }

    /// Text with its baseline's left end at (x, baseline).
    static func draw(_ text: Text, in context: inout GraphicsContext, x: Double, baseline: Double) {
        let resolved = context.resolve(text)
        let size = resolved.measure(in: CGSize(width: 10_000, height: 10_000))
        let top = resolved.firstBaseline(in: size)
        context.draw(resolved, in: CGRect(x: x, y: baseline - top, width: size.width, height: size.height))
    }
}

/// One card's face, `w` by `h` points from its top left.
private struct DepthFieldGalleryFace: View {
    let card: DepthFieldGalleryCard
    let index: Int
    let loaded: DepthFieldGalleryLoaded?
    let sharper: DepthFieldGalleryLoaded?
    let accent: UInt32
    let dark: Double
    let now: Double
    let w: Double
    let h: Double

    private typealias M = DepthFieldGalleryMetrics

    var body: some View {
        let corner = DepthFieldGalleryFaces.corner(w: w, h: h)
        switch card.kind {
        case let .photo(_, print):
            let shown = fade(loaded)
            ZStack {
                // The paper shows until the photo has faded in over it.
                if shown < 1 {
                    Color(hex: print == .lime ? accent : 0xd6d6d2)
                }
                photo(loaded, width: w, height: h)
                photo(sharper, width: w, height: h)
            }
            .frame(width: w, height: h)
            .clipShape(RoundedRectangle(cornerRadius: corner, style: .circular))
        case .poster:
            let portrait = DepthFieldGalleryFaces.portrait(w: w, h: h)
            ZStack(alignment: .topLeading) {
                base
                ZStack {
                    photo(loaded, width: portrait.width, height: portrait.height)
                    photo(sharper, width: portrait.width, height: portrait.height)
                }
                .frame(width: portrait.width, height: portrait.height)
                .clipped()
                .offset(x: portrait.minX, y: portrait.minY)
            }
            .frame(width: w, height: h, alignment: .topLeading)
        case .dots:
            base.frame(width: w, height: h)
        case .glyph, .chip, .mark:
            base
                .frame(width: w, height: h)
                .overlay {
                    // On the dark scene the dark cards keep a faint edge, a hundredth of a unit wide.
                    if dark > 0 {
                        RoundedRectangle(cornerRadius: corner, style: .circular)
                            .stroke(Color.white.opacity(0.1 * dark), lineWidth: DepthFieldGalleryFaces.unit / 100)
                    }
                }
        }
    }

    @ViewBuilder
    private var base: some View {
        if let image = DepthFieldGalleryFaces.image(card: card, index: index, accent: accent) {
            Image(uiImage: image)
                .resizable()
                .interpolation(.high)
                .frame(width: w, height: h)
        }
    }

    private func fade(_ loaded: DepthFieldGalleryLoaded?) -> Double {
        loaded.map { M.clamp01((now - $0.at) / M.imageFade) } ?? 0
    }

    /// A photo cropped to fill its place, faded in once it arrives.
    @ViewBuilder
    private func photo(_ loaded: DepthFieldGalleryLoaded?, width: Double, height: Double) -> some View {
        if let loaded {
            Image(uiImage: loaded.image)
                .resizable()
                .interpolation(.high)
                .scaledToFill()
                .frame(width: width, height: height)
                .clipped()
                .opacity(fade(loaded))
        }
    }
}
