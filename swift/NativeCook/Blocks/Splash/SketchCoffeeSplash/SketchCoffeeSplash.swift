import CoreText
import SwiftUI
import UIKit

// Sketch Coffee Splash: the launch of an invented coffee shop, Inkpot Coffee Co., drawn in white ink
// on ultramarine. Every line is a hand-drawn ribbon (uneven width, tapered ends) that "boils": it is
// redrawn with a slightly different wobble 8 times a second, from four fixed variants.
//
// Timeline of one 10 s loop (autoPlay), t in seconds:
//   0.00-1.20  the welcome screen at rest: title, a tilted paper cup with coffee splashing out of it,
//              the drops bobbing, the notes and the round button.
//   1.20-1.90  it leaves: the button shrinks to a dot and goes, the title slides off to the right and the
//              notes to the left (ease-in), the cup drops away to the bottom right, turning.
//   1.90-2.20  plain blue.
//   2.20-2.60  a top-view cup pops up from a dot in the middle (back-out), "0%" fades in under it.
//   2.50-3.10  a hand-drawn arm slides in from the bottom right holding a spoon (ease-out).
//   2.95-6.95  the spoon stirs circles in the cup (0.85 s a turn, eased in and out over 0.4 s); the hand
//              follows it, a swirl runs ahead of it in the coffee, and the percentage counts 1 to 100
//              from 3.0 to 6.55.
//   6.70-7.15  the hand pulls out the way it came (ease-in).
//   7.00-7.30  the cup shrinks to nothing with a small anticipation, the percentage fades.
//   7.30-7.68  the button comes back as a dot that grows into the round button (back-out).
//   7.40-8.20  the title wipes in from the right and the notes from the left (ease-out, lines 0.05 s apart).
//   7.50-8.40  the paper cup bounces in from the bottom right on a damped spring, the splash grows out of
//              it from 7.75 and the drops fly out to their places from 7.8, 0.04 s apart.
//   8.40-10.0  at rest again, identical to t = 0.
//
// First draw: under autoPlay the welcome holds at t = 0 while three frames draw the loop's later moments under the
// blue ground (SketchCoffeeSplashRenderer.warm) and until two frames in a row come within 50 ms (1.5 s at most), so the
// first exit, stir and return do not stall while their glyphs and paths are first rasterised; t = 0 is the frame after.
//
// Touch: a tap on the welcome plays the loader; while the spoon stirs, dragging round the cup clockwise
// stirs it by hand and pushes the percentage on (a selection tick every 10%), and a tap skips to the
// welcome. Under reduced motion the welcome is shown still, with no boil and no loop.

struct SketchCoffeeSplash: View {
    static let loop = 10.0
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var clock = SketchCoffeeSplashClock()
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var tickHaptic = UISelectionFeedbackGenerator()

    private typealias S = SketchCoffeeSplashScript

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _ = SketchCoffeeSplashFonts.registered
    }

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            let layout = SketchCoffeeSplashLayout(size: size, top: insets.top, bottom: insets.bottom)
            TimelineView(.animation(paused: reduceMotion || scenePhase != .active)) { timeline in
                let now = timeline.date
                let t = reduceMotion ? 0 : clock.time(at: now, autoPlay: autoPlay)
                // The boil runs on the script's clock while it loops, so a recorded loop repeats exactly.
                let ambient = reduceMotion ? 0 : (autoPlay || clock.held != nil ? t : now.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1000))
                Canvas { context, canvas in
                    if autoPlay && !reduceMotion && clock.warming(at: now) {
                        SketchCoffeeSplashRenderer.warm(context, layout: layout)
                        context.fill(Path(CGRect(origin: .zero, size: canvas)), with: .color(SketchCoffeeSplashRenderer.ground))
                    }
                    SketchCoffeeSplashRenderer.draw(context, t: t, ambient: ambient, still: reduceMotion, layout: layout)
                }
            }
            .frame(width: size.width, height: size.height)
            .background(SketchCoffeeSplashRenderer.ground)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        guard !reduceMotion else { return }
                        let crossed = clock.dragChanged(at: value.location, start: value.startLocation, centre: layout.cupCentre, now: .now, autoPlay: autoPlay)
                        if crossed { tickHaptic.selectionChanged() }
                    }
                    .onEnded { value in
                        guard !reduceMotion else { return }
                        if clock.dragEnded(translation: value.translation, now: .now, autoPlay: autoPlay) {
                            tapHaptic.impactOccurred()
                        }
                    }
            )
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(SketchCoffeeSplashRenderer.ground.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Inkpot Coffee Co. Welcome to our coffee haven! 12 Linden Row, Old Town. Open 7 am to 8 pm: coffee, milkshakes, desserts.")
        .accessibilityAddTraits(.isButton)
    }
}

// MARK: - Clock

/// The script's time. It is worked out from a start date rather than added up frame by frame, so the
/// loop keeps a constant period; a touch moves the start date or holds the time.
@Observable
final class SketchCoffeeSplashClock {
    private typealias S = SketchCoffeeSplashScript

    // Read only inside the per-frame timeline, so changing it needs no view update.
    @ObservationIgnored private var origin = Date()
    private var running = false
    @ObservationIgnored private var warmed = false
    @ObservationIgnored private var warmFrames = 0
    @ObservationIgnored private var warmQuick = 0
    @ObservationIgnored private var warmFirst: CFTimeInterval?
    @ObservationIgnored private var warmLast: CFTimeInterval?
    /// The time held while a finger stirs the cup.
    private(set) var held: Double?
    private var lastAngle: Double?
    private var scrubbing = false

    func time(at now: Date, autoPlay: Bool) -> Double {
        if let held { return held }
        if autoPlay && !warmed { return 0 }
        let e = max(0, now.timeIntervalSince(origin))
        if autoPlay { return e.truncatingRemainder(dividingBy: S.loop) }
        if running && e < S.loop { return e }
        return 0
    }

    /// Called once a frame while autoPlay holds t = 0. Returns whether this frame draws the warm-up (the second to
    /// fourth do); the loop starts on the first frame after two in a row came within 50 ms, or after 1.5 s at most.
    func warming(at now: Date) -> Bool {
        if warmed { return false }
        let clock = CACurrentMediaTime()
        warmQuick = warmLast.map { clock - $0 < 0.05 } == true ? warmQuick + 1 : 0
        warmLast = clock
        if warmFirst == nil { warmFirst = clock }
        warmFrames += 1
        if warmFrames > 4 && (warmQuick >= 2 || clock - (warmFirst ?? clock) > 1.5) {
            warmed = true
            origin = now
            return false
        }
        return warmFrames >= 2 && warmFrames <= 4
    }

    private func jump(to t: Double, now: Date) {
        warmed = true
        origin = now.addingTimeInterval(-t)
        running = true
        held = nil
    }

    /// Returns true when the stirring crossed another 10%.
    func dragChanged(at p: CGPoint, start: CGPoint, centre: CGPoint, now: Date, autoPlay: Bool) -> Bool {
        let t = held ?? time(at: now, autoPlay: autoPlay)
        if !scrubbing {
            let near = hypot(start.x - centre.x, start.y - centre.y) < 150
            guard near, t >= S.scrubFrom, t < S.pctTo, hypot(p.x - start.x, p.y - start.y) > 6 else { return false }
            scrubbing = true
            held = t
            lastAngle = atan2(p.y - centre.y, p.x - centre.x)
            return false
        }
        let a = atan2(p.y - centre.y, p.x - centre.x)
        defer { lastAngle = a }
        guard let last = lastAngle, let h = held else { return false }
        var d = a - last
        if d > .pi { d -= 2 * .pi }
        if d < -.pi { d += 2 * .pi }
        guard d > 0 else { return false }
        let next = min(S.pctTo, h + d / S.omega)
        held = next
        return Int(S.percent(next) / 10) != Int(S.percent(h) / 10)
    }

    /// Returns true when the touch was a tap that did something.
    func dragEnded(translation: CGSize, now: Date, autoPlay: Bool) -> Bool {
        if scrubbing {
            scrubbing = false
            lastAngle = nil
            jump(to: held ?? S.scrubFrom, now: now)
            return false
        }
        guard hypot(translation.width, translation.height) < 10 else { return false }
        let t = time(at: now, autoPlay: autoPlay)
        if t < S.exitAt || t >= S.restAt {
            jump(to: S.exitAt, now: now)
            return true
        }
        if t >= S.cupInAt && t < S.skipTo {
            jump(to: S.skipTo, now: now)
            return true
        }
        return false
    }
}

// MARK: - Script

enum SketchCoffeeSplashScript {
    static let loop = SketchCoffeeSplash.loop
    static let exitAt = 1.2
    static let cupInAt = 2.2
    static let handInAt = 2.5
    static let stirFrom = 2.95
    static let stirTo = 6.55
    static let ramp = 0.4
    static let pctFrom = 3.0
    static let pctTo = 6.55
    static let scrubFrom = 3.1
    static let skipTo = 6.6
    static let handOutAt = 6.7
    static let cupOutAt = 7.0
    static let buttonInAt = 7.3
    static let titleInAt = 7.4
    static let notesInAt = 7.45
    static let cupBackAt = 7.5
    static let splashAt = 7.75
    static let dropsAt = 7.8
    static let restAt = 8.4
    /// The spoon's speed, radians a second (0.85 s a turn).
    static let omega = 2 * Double.pi / 0.85
    static let boilRate = 8.0
    static let restTilt = -14.0

    static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }
    static func span(_ t: Double, _ from: Double, _ length: Double) -> Double { clamp01((t - from) / length) }
    static func easeIn(_ u: Double) -> Double { u * u * u }
    static func easeOut(_ u: Double) -> Double { 1 - pow(1 - u, 3) }
    static func easeOutQuint(_ u: Double) -> Double { 1 - pow(1 - u, 5) }
    static func backOut(_ u: Double, _ s: Double = 1.7) -> Double {
        let v = u - 1
        return 1 + (s + 1) * v * v * v + s * v * v
    }
    static func backIn(_ u: Double, _ s: Double = 1.7) -> Double { (s + 1) * u * u * u - s * u * u }
    /// A damped spring from 0 to 1 that overshoots by about 8% and settles exactly at u = 1.
    static func spring(_ u: Double) -> Double {
        let c = clamp01(u)
        return 1 - exp(-5 * c) * cos(2.5 * .pi * c) * (1 - c)
    }

    /// How far the spoon has turned (in seconds at full speed): its speed ramps up over `ramp`
    /// from `stirFrom`, and down over `ramp` from `stirTo`.
    static func stirTravel(_ t: Double) -> Double {
        let a = stirFrom, b = stirTo, r = ramp
        if t < a { return 0 }
        if t < a + r { return (t - a) * (t - a) / (2 * r) }
        if t < b { return r / 2 + (t - a - r) }
        if t < b + r { return r / 2 + (b - a - r) + (t - b) - (t - b) * (t - b) / (2 * r) }
        return b - a
    }

    /// The speed of the spoon, 0 to 1.
    static func stirSpeed(_ t: Double) -> Double {
        clamp01((t - stirFrom) / ramp) * (1 - clamp01((t - stirTo) / ramp))
    }

    static func percent(_ t: Double) -> Double { 100 * clamp01((t - pctFrom) / (pctTo - pctFrom)) }
}

// MARK: - Layout

struct SketchCoffeeSplashLayout {
    let size: CGSize
    let top: Double
    let bottom: Double

    var width: Double { size.width }
    var height: Double { size.height }
    var cupCentre: CGPoint { CGPoint(x: width / 2, y: height * 0.5) }
    var rim: CGPoint { CGPoint(x: width * 0.656, y: height - 258) }
    var button: CGPoint { CGPoint(x: 38, y: height - 52) }
}

// MARK: - Fonts

enum SketchCoffeeSplashFonts {
    static let hand = "CoveredByYourGrace"
    static let pen = "ReenieBeanie"

    static let registered: Bool = {
        for name in ["SketchCoffeeSplash-CoveredByYourGrace", "SketchCoffeeSplash-ReenieBeanie"] {
            if let url = Bundle.main.url(forResource: name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
        return true
    }()
}

// MARK: - Drawing

enum SketchCoffeeSplashRenderer {
    static let ground = Color(.sRGB, red: 38 / 255, green: 65 / 255, blue: 168 / 255, opacity: 1)
    static let ink = Color.white
    static let shadow = Color(.sRGB, red: 178 / 255, green: 214 / 255, blue: 246 / 255, opacity: 1)

    private typealias S = SketchCoffeeSplashScript
    private typealias A = SketchCoffeeSplashArt
    private typealias K = SketchCoffeeSplashSketch

    static func draw(_ context: GraphicsContext, t: Double, ambient: Double, still: Bool, layout: SketchCoffeeSplashLayout) {
        let variant = still ? 0 : Int(floor(ambient * S.boilRate)) % K.variants
        drawWelcomeArt(context, t: t, ambient: still ? 0 : ambient, variant: variant, layout: layout)
        drawWelcomeText(context, t: t, layout: layout)
        drawButton(context, t: t, layout: layout)
        drawLoader(context, t: t, variant: variant, layout: layout)
    }

    /// The loop's later moments, drawn once out of sight before it starts (the caller covers them with the ground).
    static func warm(_ context: GraphicsContext, layout: SketchCoffeeSplashLayout) {
        for t in [1.3, 1.45, 1.6, 1.75, 1.9, 2.3, 2.6, 3.0, 3.4, 3.8, 4.2, 4.6, 5.0, 5.4, 5.8, 6.2, 6.6, 6.9, 7.1, 7.35, 7.5, 7.7, 7.9, 8.1, 8.3] {
            draw(context, t: t, ambient: t, still: false, layout: layout)
        }
    }

    // MARK: Welcome

    /// Where the paper cup is and how far it is turned, leaving and coming back.
    private static func cupPlacement(_ t: Double, layout: SketchCoffeeSplashLayout) -> (CGPoint, Double) {
        let rest = layout.rim
        if t < 5 {
            let u = S.easeIn(S.span(t, S.exitAt, 0.65))
            let lift = 14 * sin(.pi * S.span(t, S.exitAt, 0.3))
            return (CGPoint(x: rest.x + layout.width * 0.8 * u, y: rest.y + layout.height * 0.34 * u - lift), S.restTilt + 30 * u)
        }
        let p = S.spring(S.span(t, S.cupBackAt, 0.9))
        let from = CGPoint(x: rest.x + layout.width * 0.62, y: rest.y + layout.height * 0.36)
        return (CGPoint(x: from.x + (rest.x - from.x) * p, y: from.y + (rest.y - from.y) * p), S.restTilt + 32 * (1 - p))
    }

    private static func drawWelcomeArt(_ context: GraphicsContext, t: Double, ambient: Double, variant: Int, layout: SketchCoffeeSplashLayout) {
        if t >= 1.95 && t < S.cupBackAt { return }
        let (at, tilt) = cupPlacement(t, layout: layout)
        let cup = CGAffineTransform(translationX: at.x, y: at.y).rotated(by: tilt * .pi / 180)
        let back = t >= 5

        // The splash grows out of the cup, from its left part, on its way back in.
        let grow = back ? S.spring(S.span(t, S.splashAt, 0.8)) : 1
        if grow > 0.001 {
            let base = CGPoint(x: -60, y: -6)
            let sx = 0.55 + 0.45 * grow
            let splash = CGAffineTransform(translationX: base.x, y: base.y).scaledBy(x: sx, y: grow).translatedBy(x: -base.x, y: -base.y).concatenating(cup)
            K.draw(A.splashInk[variant], in: context, transform: splash, ground: ground, ink: ink)
        }

        // Drops: thrown out of the cup to their places, then bobbing.
        let launch = CGPoint(x: -40, y: -20)
        for (i, centre) in A.dropCentres.enumerated() {
            let u = back ? S.span(t, S.dropsAt + 0.04 * Double(i), 0.6) : 1
            if u <= 0 { continue }
            let q = S.backOut(u, 1.4)
            let bob = 2.4 * sin(2 * .pi * ambient / 2.5 + Double(i) * 1.7)
            let pos = CGPoint(x: launch.x + (centre.x - launch.x) * q, y: launch.y + (centre.y - launch.y) * q + bob)
            let scale = 0.35 + 0.65 * S.easeOut(u)
            let drop = CGAffineTransform(translationX: pos.x, y: pos.y).scaledBy(x: scale, y: scale).translatedBy(x: -centre.x, y: -centre.y).concatenating(cup)
            K.draw([A.dropInk[variant][i]], in: context, transform: drop, ground: ground, ink: ink)
        }

        K.draw(A.cupInk[variant], in: context, transform: cup, ground: ground, ink: ink)
    }

    private static func text(_ s: String, _ font: Font, opacity: Double = 1) -> Text {
        Text(s).font(font).foregroundStyle(ink.opacity(opacity))
    }

    /// How far a block of text is pushed off screen: out to `out` while leaving, back in from `out` while returning.
    private static func slide(_ t: Double, outAt: Double, inAt: Double, distance: Double) -> Double {
        if t < 5 { return distance * S.easeIn(S.span(t, outAt, 0.5)) }
        return distance * (1 - S.easeOutQuint(S.span(t, inAt, 0.75)))
    }

    private static func drawWelcomeText(_ context: GraphicsContext, t: Double, layout: SketchCoffeeSplashLayout) {
        if t >= 1.95 && t < S.titleInAt { return }
        let w = layout.width, top = layout.top, h = layout.height
        let title = Font.system(size: 50, weight: .medium)
        let lines: [(Text, CGPoint, UnitPoint)] = [
            (text("Welcome to", title).kerning(-1), CGPoint(x: 20, y: top + 66), .leading),
            (text("our coffee", title).kerning(-1), CGPoint(x: w - 22, y: top + 116), .trailing),
            (text("haven!", .custom(SketchCoffeeSplashFonts.hand, fixedSize: 78)), CGPoint(x: w - 18, y: top + 176), .trailing),
            (text("Inkpot Coffee Co.", .system(size: 14, weight: .medium), opacity: 0.9), CGPoint(x: w - 203, y: top + 230), .leading),
            (text("12 Linden Row, Old Town", .system(size: 14), opacity: 0.8), CGPoint(x: w - 203, y: top + 250), .leading),
        ]
        for (i, line) in lines.enumerated() {
            let dx = slide(t, outAt: S.exitAt + 0.05 + 0.04 * Double(i), inAt: S.titleInAt + 0.05 * Double(i), distance: w * 1.1)
            context.draw(line.0, at: CGPoint(x: line.1.x + dx, y: line.1.y), anchor: line.2)
        }
        let notes: [(Text, Double)] = [
            (text("7 am - 8 pm", .custom(SketchCoffeeSplashFonts.pen, fixedSize: 26)), h - 232),
            (text("Coffee", .system(size: 15), opacity: 0.9), h - 203),
            (text("Milkshakes", .system(size: 15), opacity: 0.9), h - 179),
            (text("Desserts", .system(size: 15), opacity: 0.9), h - 155),
        ]
        for (i, note) in notes.enumerated() {
            let dx = slide(t, outAt: S.exitAt + 0.1 + 0.03 * Double(i), inAt: S.notesInAt + 0.04 * Double(i), distance: -w * 0.6)
            context.draw(note.0, at: CGPoint(x: 20 + dx, y: note.1), anchor: .leading)
            // The pen line is thin: a second pass a hair to the right gives it a marker's weight.
            if i == 0 { context.draw(note.0, at: CGPoint(x: 20.7 + dx, y: note.1 + 0.3), anchor: .leading) }
        }
    }

    private static func drawButton(_ context: GraphicsContext, t: Double, layout: SketchCoffeeSplashLayout) {
        var scale = 1.0, alpha = 1.0
        if t < 5 {
            scale = 1 - 0.8 * S.easeIn(S.span(t, S.exitAt, 0.22))
            alpha = 1 - S.span(t, S.exitAt + 0.22, 0.13)
        } else {
            alpha = S.span(t, S.buttonInAt, 0.08)
            scale = 0.2 + 0.8 * S.backOut(S.span(t, S.buttonInAt + 0.08, 0.3), 2.2)
        }
        guard alpha > 0.001, scale > 0.001 else { return }
        let c = layout.button
        let r = 24 * scale
        var layer = context
        layer.opacity = alpha
        layer.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)), with: .color(ink))
        let chevron = S.clamp01((scale - 0.6) / 0.4)
        if chevron > 0 {
            var p = Path()
            p.move(to: CGPoint(x: c.x - 4 * scale, y: c.y - 9 * scale))
            p.addLine(to: CGPoint(x: c.x + 5 * scale, y: c.y))
            p.addLine(to: CGPoint(x: c.x - 4 * scale, y: c.y + 9 * scale))
            layer.opacity = alpha * chevron
            layer.stroke(p, with: .color(ground), style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
        }
    }

    // MARK: Loader

    private static func drawLoader(_ context: GraphicsContext, t: Double, variant: Int, layout: SketchCoffeeSplashLayout) {
        guard t >= S.cupInAt && t < S.cupOutAt + 0.32 else { return }
        let c = layout.cupCentre
        let grow = S.backOut(S.span(t, S.cupInAt, 0.4), 2.0)
        let shrink = 1 - S.backIn(S.span(t, S.cupOutAt, 0.3), 2.2)
        let scale = max(0, grow * shrink)

        // The percentage.
        let pctAlpha = S.span(t, S.cupInAt + 0.15, 0.25) * (1 - S.span(t, S.cupOutAt, 0.25))
        if pctAlpha > 0 {
            var layer = context
            layer.opacity = pctAlpha
            let label = Text("\(Int(S.percent(t)))%").font(.custom(SketchCoffeeSplashFonts.pen, fixedSize: 40)).kerning(2).foregroundStyle(ink)
            layer.draw(label, at: CGPoint(x: c.x, y: c.y + 96), anchor: .center)
            layer.draw(label, at: CGPoint(x: c.x + 0.8, y: c.y + 96.3), anchor: .center)
        }
        guard scale > 0.001 else { return }

        // The cup: a pale shadow, the cup itself (ground inside), its sketched ring and handle.
        let cup = CGAffineTransform(translationX: c.x, y: c.y).scaledBy(x: scale, y: scale)
        let r = A.cupRadius
        context.fill(Path(ellipseIn: CGRect(x: -r - 2, y: -r + 6, width: 2 * r, height: 2 * r)).applying(cup), with: .color(shadow))
        context.fill(Path(roundedRect: CGRect(x: r - 4, y: -2, width: 22, height: 13), cornerRadius: 5).applying(cup), with: .color(shadow))
        context.fill(Path(ellipseIn: CGRect(x: -r, y: -r, width: 2 * r, height: 2 * r)).applying(cup), with: .color(ground))
        context.fill(Path(roundedRect: CGRect(x: r - 2, y: -6, width: 20, height: 12), cornerRadius: 5).applying(cup), with: .color(ground))
        K.draw(A.topInk[variant], in: context, transform: cup, ground: ground, ink: ink)

        // The stir.
        let theta = Double.pi + S.omega * S.stirTravel(t)
        let swirlAlpha = S.stirSpeed(t)
        if swirlAlpha > 0.01 {
            var layer = context
            layer.opacity = swirlAlpha
            let swirl = CGAffineTransform(rotationAngle: theta).concatenating(cup)
            K.draw(A.swirlInk[variant], in: layer, transform: swirl, ground: ground, ink: ink)
        }

        // The hand, arriving and leaving along its arm.
        let enter = 1 - S.easeOut(S.span(t, S.handInAt, 0.6))
        let leave = S.easeIn(S.span(t, S.handOutAt, 0.45))
        let away = A.armAway * (enter + leave)
        if enter + leave >= 1 { return }
        let tip = CGPoint(x: c.x + A.stirRadius * cos(theta) + A.armDirection.x * away, y: c.y + A.stirRadius * sin(theta) + A.armDirection.y * away)
        let tipRel = CGPoint(x: A.stirRadius * cos(theta), y: A.stirRadius * sin(theta))
        let pinch = CGPoint(
            x: c.x + A.pinchRest.x + 0.35 * tipRel.x + A.armDirection.x * away,
            y: c.y + A.pinchRest.y + 0.35 * tipRel.y + A.armDirection.y * away
        )
        // Keep the arm pointing at the same shoulder off screen.
        let shoulder = CGPoint(x: c.x + A.pinchRest.x + A.armDirection.x * 420, y: c.y + A.pinchRest.y + A.armDirection.y * 420)
        let restAngle = atan2(A.armDirection.y, A.armDirection.x)
        let nowAngle = atan2(shoulder.y - pinch.y, shoulder.x - pinch.x)
        let hand = CGAffineTransform(translationX: pinch.x, y: pinch.y).rotated(by: (nowAngle - restAngle) * (1 - enter - leave))
        K.draw(A.handInk[variant], in: context, transform: hand, ground: ground, ink: ink)

        // The spoon: its handle into the fist, and a small bowl in the coffee.
        let (spoon, _) = K.ribbon([tip, pinch], closed: false, width: 4.2, seed: 98, variant: variant, jitter: 0.35)
        var sp = Path()
        sp.addLines(spoon)
        sp.closeSubpath()
        context.fill(sp, with: .color(ink))
        let angle = atan2(pinch.y - tip.y, pinch.x - tip.x)
        let bowl = CGAffineTransform(translationX: tip.x, y: tip.y).rotated(by: angle)
        context.fill(Path(ellipseIn: CGRect(x: -5.5, y: -4.2, width: 11, height: 8.4)).applying(bowl), with: .color(ink))
        K.draw(A.thumbInk[variant], in: context, transform: hand, ground: ground, ink: ink)
    }
}
