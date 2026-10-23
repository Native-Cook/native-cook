import SwiftUI

/// One flat shape of a stamp's illustration.
struct PostageStampLayer {
    var color: Color
    /// 0 for the horizon, 1 for the foreground: nearer layers travel further.
    var depth: Double
    let shape: Path

    /// `path` is an SVG path (absolute M, L, Q, C and Z) drawn in the stamp's 156 × 204 window.
    /// Let it run past the window on both sides: the layers slide against each other.
    init(_ path: String, color: UInt32, depth: Double) {
        self.color = Color(hex: color)
        self.depth = depth
        shape = postageStampPath(path)
    }
}

struct PostageStampPlace {
    /// The two words of the name, set above and below the stamp.
    var top: String
    var bottom: String
    var country = "ITALIA"
    var region: String
    /// The line of coordinates along the bottom edge.
    var caption: String
    /// Colour of the panel while this place is shown.
    var paper: Color
    /// Colour of the big name.
    var ink: Color
    /// Colour of the region printed on the illustration.
    var regionInk: Color
    var sky: Color
    /// Ground colour of the small stamp that peeks in from a corner.
    var ground: Color
    /// The illustration, back to front.
    var layers: [PostageStampLayer]
}

/// A place shown as a postage stamp on a panel of coloured paper, its name in huge capitals behind
/// the stamp. A horizontal swipe slides the next illustration into the stamp while the paper and the
/// name change with it; a tap opens the place: the stamp grows until its illustration fills the
/// panel, the perforated border leaving through the edges, and the name comes forward over the scene.
struct PostageStampReveal: View {
    var places = postageStampPlaces
    /// Plays the interaction by itself: next place, open, close, next, next.
    var autoPlay = false
    /// The three small labels along the top edge.
    var labels = ["INDEX", "ALL", "ABOUT"]
    /// The letter in the boxed emblem of the stamp.
    var emblem = "B"
    var closeLabel = "CLOSE"
    /// Called with the index of the place a swipe lands on.
    var onSelect: ((Int) -> Void)? = nil
    /// Called with the index of the place that was opened or closed.
    var onOpen: ((Int) -> Void)? = nil
    var onClose: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Index of the place on show; fractional while one slides into the other, and it keeps counting past the last place.
    @State private var pos = 0.0
    @State private var open = 0.0
    @State private var dragFrom: Double? = nil
    /// Counts touches: a change restarts the autoPlay loop after a pause.
    @State private var touches = 0
    @State private var ticks = 0
    @State private var thumps = 0

    private var auto: Bool { autoPlay && !reduceMotion }

    private struct Run: Equatable {
        var auto: Bool
        var touches: Int
    }

    var body: some View {
        PostageStampFace(
            places: places, labels: labels, emblem: emblem, closeLabel: closeLabel,
            reduced: reduceMotion, pos: pos, open: open
        )
        .frame(width: Metrics.width, height: Metrics.height)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.radius, style: .circular))
        .contentShape(Rectangle())
        .onTapGesture {
            touches += 1
            thumps += 1
            toggle(report: true)
        }
        .gesture(drag)
        .sensoryFeedback(.selection, trigger: ticks)
        .sensoryFeedback(.impact(weight: .light), trigger: thumps)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: label))
        .accessibilityAddTraits(.isButton)
        .accessibilityAdjustableAction { direction in
            touches += 1
            slide(to: pos.rounded() + (direction == .increment ? 1 : -1), report: true)
        }
        .task(id: Run(auto: auto, touches: touches)) { await play() }
    }

    private var label: String {
        guard !places.isEmpty else { return "" }
        let place = places[index(of: pos)]
        return "\(place.top) \(place.bottom), \(place.region)"
    }

    private func index(of pos: Double) -> Int {
        let n = places.count
        return ((Int(pos.rounded()) % n) + n) % n
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { value in
                guard open < 0.5 else { return }
                if dragFrom == nil {
                    dragFrom = pos
                    touches += 1
                }
                var still = Transaction()
                still.disablesAnimations = true
                withTransaction(still) {
                    pos = (dragFrom ?? 0) - value.translation.width / Metrics.dragSpan
                }
            }
            .onEnded { value in
                guard let from = dragFrom else { return }
                dragFrom = nil
                touches += 1
                // A swipe moves one place at most, however far it is thrown.
                let thrown = from - value.predictedEndTranslation.width / Metrics.dragSpan
                let target = min(from.rounded() + 1, max(from.rounded() - 1, thrown.rounded()))
                slide(to: target, velocity: -value.velocity.width / Metrics.dragSpan, report: target != from.rounded())
            }
    }

    private func slide(to target: Double, velocity: Double = 0, report: Bool = false) {
        let distance = target - pos
        let spring = Animation.interpolatingSpring(
            mass: 1, stiffness: Metrics.slideStiffness, damping: Metrics.slideDamping,
            initialVelocity: abs(distance) > 0.001 ? velocity / distance : 0
        )
        withAnimation(reduceMotion ? Metrics.reduced : spring) { pos = target }
        if report {
            ticks += 1
            onSelect?(index(of: target))
        }
    }

    private func toggle(report: Bool = false) {
        let opening = open < 0.5
        let spring = Animation.interpolatingSpring(mass: 1, stiffness: Metrics.openStiffness, damping: Metrics.openDamping)
        withAnimation(reduceMotion ? Metrics.reduced : spring) { open = opening ? 1 : 0 }
        if report { (opening ? onOpen : onClose)?(index(of: pos)) }
    }

    /// The autoPlay loop. After a touch it waits, walks back to the first place and starts over.
    private func play() async {
        guard auto, !places.isEmpty else { return }
        do {
            if touches > 0 {
                try await Task.sleep(for: .seconds(Metrics.idle))
                guard dragFrom == nil else { return }
                if open > 0.5 { toggle() }
                let n = Double(places.count)
                slide(to: (pos / n).rounded() * n)
                try await Task.sleep(for: .seconds(Metrics.settle))
            }
            while true {
                // The same place under a smaller number, so the counter never grows.
                pos = 0
                var clock = 0.0
                for step in Metrics.script {
                    try await Task.sleep(for: .seconds(step.at - clock))
                    clock = step.at
                    if step.opens { toggle() } else { slide(to: pos.rounded() + 1) }
                }
                try await Task.sleep(for: .seconds(Metrics.loop - clock))
            }
        } catch {}
    }
}

private enum Metrics {
    static let width = 340.0
    static let height = 420.0
    static let radius = 10.0

    // The stamp with its perforated border, and the window its illustration shows through.
    static let stamp = CGRect(x: 80, y: 96, width: 180, height: 228)
    static let window = CGRect(x: 92, y: 108, width: 156, height: 204)
    // Perforations: circles of this radius, centred on the edge, one per pitch.
    static let pitch = 12.0
    static let hole = 3.6
    // Soft shadows the stamps cast on the paper.
    static let shadow = CGSize(width: 4, height: 7)
    static let shadowBlur = 6.0
    static let miniShadow = CGSize(width: 3, height: 5)
    static let miniShadowBlur = 5.0
    // The small stamps of the neighbouring places in two corners.
    static let mini = CGSize(width: 96, height: 120)
    static let miniBorder = 9.0
    static let miniHole = 3.2
    static let miniTopLeft = CGPoint(x: -34, y: -22)
    static let miniBottomRight = CGPoint(x: 294, y: 350)
    static let miniTravel = CGSize(width: 70, height: 80)

    /// Scale at which the window covers the whole panel.
    static let openScale = 2.2
    // Opening pushes near layers down and towards the viewer more than far ones.
    static let openLift = 14.0
    static let openZoom = 0.14
    static let pivot = CGPoint(x: 78, y: 122)
    /// How far a far layer lags behind its scene while it slides in.
    static let slideLag = 44.0

    // Centre lines of the two words: behind the stamp, and stacked at the top once the place is open.
    static let titleSize = 64.0
    static let titleLine = 76.0
    static let titleTop = 89.0
    static let titleBottom = 332.0
    static let openTitleTop = 68.0
    static let openTitleBottom = 126.0
    // The words slide further than the illustration, the lower one further still.
    static let titleShift = 300.0
    static let titleStagger = 1.2

    /// Finger travel that moves one place.
    static let dragSpan = 200.0

    static let slideStiffness = 130.0
    static let slideDamping = 19.0
    static let openStiffness = 90.0
    static let openDamping = 14.0
    static let reduced = Animation.timingCurve(0.23, 1, 0.32, 1, duration: 0.2)

    // autoPlay (seconds): next place, open, close, next, next, which is the first place again.
    static let script: [(at: Double, opens: Bool)] = [(0.8, false), (2.3, true), (4.3, true), (5.7, false), (7.0, false)]
    static let loop = 8.4
    /// After a touch the loop waits this long, returns to the first place, and starts over.
    static let idle = 2.5
    static let settle = 1.2

    static let grain = 700
    static let cream = Color(hex: 0xefe6cf)
    static let trail = Color(hex: 0xf2c64f)
}

/// Everything that is drawn, as a function of `pos` and `open`; SwiftUI animates both numbers.
private struct PostageStampFace: View, Animatable {
    let places: [PostageStampPlace]
    let labels: [String]
    let emblem: String
    let closeLabel: String
    let reduced: Bool
    var pos: Double
    var open: Double

    var animatableData: AnimatablePair<Double, Double> {
        get { AnimatablePair(pos, open) }
        set {
            pos = newValue.first
            open = newValue.second
        }
    }

    /// Signed distance of place `i` from the one on show, wrapped so the last place neighbours the first.
    private func rel(_ i: Int) -> Double {
        let n = Double(places.count)
        var r = (Double(i) - pos).truncatingRemainder(dividingBy: n)
        if r < -n / 2 { r += n } else if r >= n / 2 { r -= n }
        return r
    }

    private func place(at offset: Int) -> PostageStampPlace {
        let n = places.count
        return places[(((Int(pos.rounded()) + offset) % n) + n) % n]
    }

    private var o: Double { min(1, max(0, open)) }
    /// 1 at rest on a place, 0 halfway between two.
    private var settled: Double {
        let d = min(1, abs(pos - pos.rounded()) * 2)
        return 1 - d * d * (3 - 2 * d)
    }
    /// Under reduced motion nothing slides or grows: scenes cross-fade and the stamp jumps open.
    private var travel: Double { reduced ? 0 : 1 }
    private var scale: Double {
        reduced ? (open > 0.5 ? Metrics.openScale : 1) : 1 + (Metrics.openScale - 1) * open
    }

    var body: some View {
        let near = place(at: 0)

        ZStack {
            Canvas { context, _ in drawBack(context) }

            ForEach(Array(places.enumerated()), id: \.offset) { i, place in
                let r = rel(i)
                word(place.top, place.ink)
                    .opacity(reduced ? max(0, 1 - abs(r)) : 1)
                    .position(x: Metrics.width / 2 + r * Metrics.titleShift * travel, y: Metrics.titleTop)
                word(place.bottom, place.ink)
                    .opacity(reduced ? max(0, 1 - abs(r)) : 1)
                    .position(
                        x: Metrics.width / 2 + r * Metrics.titleShift * Metrics.titleStagger * travel,
                        y: Metrics.titleBottom
                    )
            }

            Canvas { context, _ in drawStamp(context) }

            // What is printed on the stamp: it grows with it and is gone before the border is.
            ZStack {
                Text(near.country)
                    .font(.system(size: 11, weight: .heavy))
                    .tracking(0.4)
                    .foregroundStyle(.white)
                    .fixedSize()
                    .frame(width: 100, height: 14, alignment: .leading)
                    .position(x: Metrics.window.minX + 9 + 50, y: Metrics.window.minY + 9 + 7)
                Text(emblem)
                    .font(.system(size: 10, weight: .heavy))
                    .foregroundStyle(.white)
                    .frame(width: 15, height: 15)
                    .overlay(RoundedRectangle(cornerRadius: 2.5, style: .circular).strokeBorder(.white, lineWidth: 1.3))
                    .position(x: Metrics.window.maxX - 9 - 7.5, y: Metrics.window.minY + 9 + 7.5)
                ForEach(Array(places.enumerated()), id: \.offset) { i, place in
                    Text(place.region)
                        .font(.system(size: 10, weight: .heavy))
                        .tracking(0.6)
                        .foregroundStyle(place.regionInk)
                        .fixedSize()
                        .frame(width: 138, height: 13, alignment: .leading)
                        .opacity(max(0, 1 - abs(rel(i)) * 2))
                        .position(x: Metrics.window.minX + 9 + 69, y: Metrics.window.maxY - 10 - 6.5)
                }
            }
            .frame(width: Metrics.width, height: Metrics.height)
            .scaleEffect(scale)
            .opacity(max(0, 1 - o * 3))

            // The name again, in front of the scene once the place is open.
            Group {
                word(near.top, near.ink)
                    .position(x: Metrics.width / 2, y: Metrics.titleTop + (Metrics.openTitleTop - Metrics.titleTop) * open * travel)
                word(near.bottom, near.ink)
                    .position(x: Metrics.width / 2, y: Metrics.titleBottom + (Metrics.openTitleBottom - Metrics.titleBottom) * open * travel)
            }
            .opacity(min(1, max(0, (o - 0.15) / 0.35)))

            Text(closeLabel)
                .font(.system(size: 8, weight: .heavy))
                .tracking(0.8)
                .foregroundStyle(Color(hex: 0x2c2a26))
                .frame(width: 62, height: 24)
                .background(Capsule().fill(Metrics.cream))
                .opacity(min(1, max(0, (o - 0.6) / 0.4)))
                .position(x: Metrics.width / 2, y: Metrics.height - 50 + (1 - open) * 10 * travel)

            HStack {
                HStack(spacing: 4) {
                    Text(labels.first ?? "")
                    Circle().fill(.white).frame(width: 4, height: 4)
                }
                Spacer()
                Text(labels.count > 2 ? labels[2] : "")
            }
            .overlay(Text(labels.count > 1 ? labels[1] : ""))
            .font(.system(size: 7.5, weight: .heavy))
            .tracking(0.5)
            .foregroundStyle(.white.opacity(0.92))
            .frame(width: Metrics.width - 28, height: 10)
            .position(x: Metrics.width / 2, y: 16)

            Text(near.caption)
                .font(.system(size: 6.5, weight: .bold))
                .tracking(0.4)
                .foregroundStyle(.white.opacity(0.92))
                .lineLimit(1)
                .frame(width: Metrics.width - 28, height: 9)
                .opacity(settled)
                .position(x: Metrics.width / 2, y: Metrics.height - 15)
        }
        .frame(width: Metrics.width, height: Metrics.height)
    }

    private func word(_ text: String, _ color: Color) -> some View {
        Text(text)
            .font(.system(size: Metrics.titleSize, weight: .black))
            .tracking(-1.5)
            .foregroundStyle(color)
            .lineLimit(1)
            .fixedSize()
            .frame(height: Metrics.titleLine)
    }

    /// The paper, the dotted trail and the two corner stamps: everything behind the name.
    private func drawBack(_ context: GraphicsContext) {
        let n = places.count
        guard n > 0 else { return }
        let base = Int(pos.rounded(.down))
        let mix = pos - Double(base)
        let from = places[((base % n) + n) % n]
        let to = places[(((base + 1) % n) + n) % n]
        let panel = Path(CGRect(x: 0, y: 0, width: Metrics.width, height: Metrics.height))
        // The next place's colour is laid over this one's as it slides in.
        context.fill(panel, with: .color(from.paper))
        context.fill(panel, with: .color(to.paper.opacity(mix)))

        // Both leave while a place slides or is open, and come back for the next one.
        let shown = settled * (1 - o)
        var trail = context
        trail.translateBy(x: (pos.rounded() - pos) * 30 * travel, y: 0)
        trail.stroke(
            postageStampTrail, with: .color(Metrics.trail.opacity(shown)),
            style: StrokeStyle(lineWidth: 5.5, dash: [5.5, 6.5])
        )

        let away = (1 - shown) * travel
        var before = context
        before.opacity = reduced ? shown : 1
        before.translateBy(
            x: Metrics.miniTopLeft.x - away * Metrics.miniTravel.width,
            y: Metrics.miniTopLeft.y - away * Metrics.miniTravel.height
        )
        drawMini(before, place(at: -1))
        var after = context
        after.opacity = reduced ? shown : 1
        after.translateBy(
            x: Metrics.miniBottomRight.x + away * Metrics.miniTravel.width,
            y: Metrics.miniBottomRight.y + away * Metrics.miniTravel.height
        )
        drawMini(after, place(at: 1))
    }

    private func drawMini(_ context: GraphicsContext, _ place: PostageStampPlace) {
        let rect = CGRect(origin: .zero, size: Metrics.mini)
        var shadow = context
        shadow.addFilter(.blur(radius: Metrics.miniShadowBlur))
        shadow.fill(Path(rect.offsetBy(dx: Metrics.miniShadow.width, dy: Metrics.miniShadow.height)), with: .color(.black.opacity(0.26)))
        var paper = context
        paper.clip(to: Path(rect))
        paper.fill(postageStampMiniEdge, with: .color(Metrics.cream), style: FillStyle(eoFill: true))
        var window = context
        window.clip(to: Path(rect.insetBy(dx: Metrics.miniBorder, dy: Metrics.miniBorder)))
        window.fill(Path(rect), with: .color(place.sky))
        window.fill(postageStampMiniGround, with: .color(place.ground))
    }

    /// The stamp: shadow, perforated paper and the illustrations sliding through its window.
    private func drawStamp(_ context: GraphicsContext) {
        let centre = CGPoint(x: Metrics.width / 2, y: Metrics.height / 2)
        let stamp = context.scaled(scale, about: centre)

        var shadow = stamp
        shadow.addFilter(.blur(radius: Metrics.shadowBlur))
        shadow.fill(
            Path(Metrics.stamp.offsetBy(dx: Metrics.shadow.width, dy: Metrics.shadow.height)),
            with: .color(.black.opacity(0.3 * (1 - o)))
        )
        var paper = stamp
        paper.clip(to: Path(Metrics.stamp))
        paper.fill(postageStampEdge, with: .color(Metrics.cream), style: FillStyle(eoFill: true))

        var window = stamp
        window.clip(to: Path(Metrics.window))
        let frame = CGRect(origin: .zero, size: Metrics.window.size)
        for (i, place) in places.enumerated() {
            let r = rel(i)
            guard abs(r) < 1 else { continue }
            var scene = window
            scene.translateBy(x: Metrics.window.minX + r * Metrics.window.width * travel, y: Metrics.window.minY)
            scene.clip(to: Path(frame))
            scene.opacity = reduced ? 1 - abs(r) : 1
            scene.fill(Path(frame), with: .color(place.sky))
            for layer in place.layers {
                var near = scene
                near.translateBy(
                    x: -r * (1 - layer.depth) * Metrics.slideLag * travel,
                    y: open * (layer.depth - 0.5) * Metrics.openLift * travel
                )
                near = near.scaled(1 + open * layer.depth * Metrics.openZoom * travel, about: Metrics.pivot)
                near.fill(layer.shape, with: .color(layer.color))
            }
        }

        // Paper grain over the whole panel, the same specks every frame.
        context.fill(postageStampGrain.light, with: .color(.white.opacity(0.07)))
        context.fill(postageStampGrain.dark, with: .color(.black.opacity(0.06)))
    }
}

/// A rectangle and a circle on every perforation; filled even-odd and clipped to the rectangle,
/// that leaves a half-round bite at each one.
private func postageStampPerforated(_ rect: CGRect, pitch: Double, hole: Double) -> Path {
    var path = Path(rect)
    func punch(_ x: Double, _ y: Double) {
        path.addEllipse(in: CGRect(x: x - hole, y: y - hole, width: hole * 2, height: hole * 2))
    }
    for i in 0..<Int((rect.width / pitch).rounded()) {
        let x = rect.minX + pitch * (Double(i) + 0.5)
        punch(x, rect.minY)
        punch(x, rect.maxY)
    }
    for i in 0..<Int((rect.height / pitch).rounded()) {
        let y = rect.minY + pitch * (Double(i) + 0.5)
        punch(rect.minX, y)
        punch(rect.maxX, y)
    }
    return path
}

private let postageStampEdge = postageStampPerforated(Metrics.stamp, pitch: Metrics.pitch, hole: Metrics.hole)
private let postageStampMiniEdge = postageStampPerforated(
    CGRect(origin: .zero, size: Metrics.mini), pitch: Metrics.pitch, hole: Metrics.miniHole
)
private let postageStampMiniGround = postageStampPath("M0 70 C24 54 60 50 96 66 L96 120 L0 120 Z")
private let postageStampTrail = postageStampPath("M-10 236 C4 200 22 192 34 216 C44 238 60 240 66 216 C72 190 92 186 110 214")

/// Two sets of one-point specks from a fixed sequence (Park–Miller), so the grain never changes.
private let postageStampGrain: (light: Path, dark: Path) = {
    var seed = 7.0
    func next() -> Double {
        seed = (seed * 16807).truncatingRemainder(dividingBy: 2147483647)
        return seed / 2147483647
    }
    var light = Path()
    var dark = Path()
    for i in 0..<(Metrics.grain * 2) {
        let speck = CGRect(x: next() * Metrics.width, y: next() * Metrics.height, width: 1, height: 1)
        if i % 2 == 0 { light.addRect(speck) } else { dark.addRect(speck) }
    }
    return (light, dark)
}()

private func postageStampPath(_ svg: String) -> Path {
    var path = Path()
    let scanner = Scanner(string: svg)
    func point() -> CGPoint {
        let x = scanner.scanDouble() ?? 0
        return CGPoint(x: x, y: scanner.scanDouble() ?? 0)
    }
    while let command = scanner.scanCharacter() {
        switch command {
        case "M": path.move(to: point())
        case "L": path.addLine(to: point())
        case "Q":
            let control = point()
            path.addQuadCurve(to: point(), control: control)
        case "C":
            let first = point()
            let second = point()
            path.addCurve(to: point(), control1: first, control2: second)
        default: path.closeSubpath()
        }
    }
    return path
}

/// A snowy hill with a hut, rock towers at sunset, and sea stacks.
let postageStampPlaces: [PostageStampPlace] = [
    PostageStampPlace(
        top: "MONTE", bottom: "TOMBA", region: "MONTI LESSINI",
        caption: "BOSCO CHIESANUOVA · N 45°41′31″ · E 11°03′27″",
        paper: Color(hex: 0x5d8fc0), ink: Color(hex: 0xe58bb5), regionInk: Color(hex: 0x3f6fc4),
        sky: Color(hex: 0x5b97ea), ground: Color(hex: 0xf4f1e8),
        layers: [
            PostageStampLayer("M-40 124 C0 106 40 100 80 110 C120 120 160 102 200 98 L200 250 L-40 250 Z", color: 0x86b4f1, depth: 0.25),
            PostageStampLayer("M106.4 50 L107.6 50 L107.6 72 L106.4 72 Z", color: 0x2e1f6e, depth: 0.6),
            PostageStampLayer("M103 55 L111 55 L111 56.2 L103 56.2 Z", color: 0x2e1f6e, depth: 0.6),
            PostageStampLayer("M104.5 60 L109.5 60 L109.5 61.2 L104.5 61.2 Z", color: 0x2e1f6e, depth: 0.6),
            PostageStampLayer("M100 80 L114 80 L114 110 L100 110 Z", color: 0x5a3fb5, depth: 0.6),
            PostageStampLayer("M114 80 L127 84 L127 110 L114 110 Z", color: 0xd77fb4, depth: 0.6),
            PostageStampLayer("M98 81 L107 71 L116 81 Z", color: 0x2e1f6e, depth: 0.6),
            PostageStampLayer("M107 71 L120 75 L129 85 L116 81 Z", color: 0x40298c, depth: 0.6),
            PostageStampLayer("M-40 140 C10 120 60 100 104 99 C140 98 170 106 200 116 L200 250 L-40 250 Z", color: 0xf4f1e8, depth: 1),
            PostageStampLayer("M-40 186 C30 160 90 162 200 184 L200 250 L-40 250 Z", color: 0xe2e8f3, depth: 1),
        ]
    ),
    PostageStampPlace(
        top: "CINQUE", bottom: "TORRI", region: "DOLOMITI",
        caption: "CORTINA D'AMPEZZO · N 46°30′38″ · E 12°03′07″",
        paper: Color(hex: 0xcf7fae), ink: Color(hex: 0xf2c44e), regionInk: Color(hex: 0xfbe9d2),
        sky: Color(hex: 0xef9a76), ground: Color(hex: 0x8a2f55),
        layers: [
            PostageStampLayer("M124 40 C131.73 40 138 46.27 138 54 C138 61.73 131.73 68 124 68 C116.27 68 110 61.73 110 54 C110 46.27 116.27 40 124 40 Z", color: 0xfde2b4, depth: 0.1),
            PostageStampLayer("M-40 138 L-6 112 L18 126 L46 100 L70 122 L104 104 L134 126 L160 108 L200 130 L200 250 L-40 250 Z", color: 0xdc7a80, depth: 0.3),
            PostageStampLayer("M26 160 L30 104 L41 94 L52 102 L54 160 Z", color: 0xee9dbd, depth: 0.65),
            PostageStampLayer("M52 102 L61 109 L62 160 L54 160 Z", color: 0xa94a76, depth: 0.65),
            PostageStampLayer("M64 160 L68 70 L81 60 L94 70 L97 160 Z", color: 0xee9dbd, depth: 0.65),
            PostageStampLayer("M94 70 L105 80 L107 160 L97 160 Z", color: 0xa94a76, depth: 0.65),
            PostageStampLayer("M112 160 L115 110 L124 102 L133 110 L135 160 Z", color: 0xee9dbd, depth: 0.65),
            PostageStampLayer("M133 110 L141 117 L142 160 L135 160 Z", color: 0xa94a76, depth: 0.65),
            PostageStampLayer("M-40 150 C10 140 50 152 90 146 C130 140 160 150 200 142 L200 250 L-40 250 Z", color: 0xc4577a, depth: 0.85),
            PostageStampLayer("M-40 178 C20 160 70 176 110 170 C150 164 170 176 200 168 L200 250 L-40 250 Z", color: 0x8a2f55, depth: 1),
        ]
    ),
    PostageStampPlace(
        top: "ISOLA", bottom: "BELLA", region: "TAORMINA",
        caption: "BAIA DI TAORMINA · N 37°51′02″ · E 15°18′01″",
        paper: Color(hex: 0x3f9c80), ink: Color(hex: 0xf4a3c6), regionInk: Color(hex: 0xffffff),
        sky: Color(hex: 0xa8dde0), ground: Color(hex: 0x2389a0),
        layers: [
            PostageStampLayer("M118 43 C126.28 43 133 49.72 133 58 C133 66.28 126.28 73 118 73 C109.72 73 103 66.28 103 58 C103 49.72 109.72 43 118 43 Z", color: 0xfff4cf, depth: 0.1),
            PostageStampLayer("M-40 124 L200 124 L200 250 L-40 250 Z", color: 0x2389a0, depth: 0.3),
            PostageStampLayer("M22 160 L28 100 L40 88 L58 92 L64 108 L68 160 Z", color: 0x3f5c94, depth: 0.65),
            PostageStampLayer("M58 92 L70 100 L78 160 L68 160 L64 108 Z", color: 0x24365e, depth: 0.65),
            PostageStampLayer("M28 100 L40 88 L58 92 L52 99 L38 102 Z", color: 0x5cc08a, depth: 0.65),
            PostageStampLayer("M88 160 L92 72 L101 62 L110 72 L112 160 Z", color: 0x3f5c94, depth: 0.65),
            PostageStampLayer("M110 72 L118 81 L120 160 L112 160 Z", color: 0x24365e, depth: 0.65),
            PostageStampLayer("M92 72 L101 62 L110 72 L103 76 Z", color: 0x5cc08a, depth: 0.65),
            PostageStampLayer("M126 160 L128 122 L135 115 L142 122 L144 160 Z", color: 0x3f5c94, depth: 0.65),
            PostageStampLayer("M142 122 L148 128 L149 160 L144 160 Z", color: 0x24365e, depth: 0.65),
            PostageStampLayer("M-40 158 C0 150 30 164 70 157 C110 150 140 164 200 154 L200 250 L-40 250 Z", color: 0x45b5b0, depth: 1),
            PostageStampLayer("M-40 158 C0 150 30 164 70 157 C110 150 140 164 200 154 L200 158 C140 168 110 154 70 161 C30 168 0 154 -40 162 Z", color: 0xeefaf3, depth: 1),
        ]
    ),
]

#Preview {
    ZStack {
        Color(hex: 0xc8d9f0).ignoresSafeArea()
        PostageStampReveal(autoPlay: true)
    }
}
