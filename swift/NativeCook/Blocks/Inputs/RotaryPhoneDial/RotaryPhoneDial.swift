import SwiftUI
import UIKit

/// A vintage rotary dial on a bottle-green bakelite body. Put a finger in a hole and turn the wheel
/// clockwise to the finger stop; let go and it runs home at a governed speed, a pulse for each
/// number, and the digit drops onto the paper tape above. Letting go early returns without
/// dialling; holding the number card clears the tape.
struct RotaryPhoneDial: View {
    /// Dials a number, clears it with a press on the card, and starts over.
    var autoPlay = false
    /// Called with each digit as it is dialled.
    var onDigit: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = RotaryPhoneDialEngine()
    @State private var digits: [RotaryPhoneDialDigit] = []
    @State private var nextID = 0
    /// True while a drag is down; SwiftUI resets it when the drag ends or is cancelled.
    @GestureState private var touching = false

    private typealias M = RotaryPhoneDialMetrics

    var body: some View {
        // The block draws under the system bars and keeps its contents clear of them itself.
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

    private func geometry(size: CGSize, insets: EdgeInsets) -> RotaryPhoneDialGeometry {
        let width = size.width
        let height = size.height
        let stripY = insets.top + M.header
        let top = stripY + M.stripH + 20
        let bottom = height - insets.bottom - M.footer
        // Never below a usable size, even on a very short window.
        let plateR = max(40, min(width * M.plateShare, (bottom - top) / 2 * 0.86, M.plateMax))
        let wheelR = plateR * M.wheel
        return RotaryPhoneDialGeometry(
            width: width,
            height: height,
            cx: width / 2,
            cy: (top + bottom) / 2,
            plateR: plateR,
            wheelR: wheelR,
            ringR: wheelR * M.ring,
            holeR: wheelR * M.hole,
            hubR: wheelR * M.hub,
            strip: CGRect(x: M.side, y: stripY, width: width - M.side * 2, height: M.stripH)
        )
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let g = geometry(size: size, insets: insets)
        let fonts = RotaryPhoneDialFonts(g)
        let settings = RotaryPhoneDialSettings(
            auto: autoPlay && !reduceMotion,
            reduced: reduceMotion,
            onDigit: { value, byUser in addDigit(value, byUser: byUser) },
            onClear: { clearDigits() }
        )
        return ZStack(alignment: .topLeading) {
            // Everything that never moves is drawn once.
            Canvas { context, _ in
                RotaryPhoneDialDrawing.drawStatic(&context, g, fonts)
            }
            .frame(width: size.width, height: size.height)

            TimelineView(.animation) { timeline in
                let frame = engine.step(to: timeline.date, settings: settings)
                Canvas { context, _ in
                    RotaryPhoneDialDrawing.drawWheel(&context, g, frame.phi)
                    RotaryPhoneDialDrawing.drawStop(&context, g, frame.kick)
                    RotaryPhoneDialDrawing.drawCard(&context, g, fonts, frame.card)
                    if frame.finger.hole >= 0 {
                        RotaryPhoneDialDrawing.drawFinger(&context, g, frame.finger.angle, frame.finger.shown)
                    }
                    RotaryPhoneDialDrawing.drawPulses(&context, g, frame.pulses, frame.pulseShown)
                }
                .frame(width: size.width, height: size.height)
            }

            header(insets: insets)
            strip(g)

            Text("Turn a hole to the stop · Hold the card to clear")
                .font(.system(size: 12, weight: .medium))
                .kerning(0.3)
                .multilineTextAlignment(.center)
                .foregroundStyle(M.pale)
                .padding(.horizontal, M.side)
                .frame(width: max(0, size.width), height: max(0, size.height - insets.bottom - 24), alignment: .bottom)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .allowsHitTesting(true)
        .contentShape(Rectangle())
        .gesture(
            // One recogniser for both: a touch in a hole turns the wheel, a touch on the card
            // presses it, anything else is left alone.
            DragGesture(minimumDistance: 0)
                .updating($touching) { _, state, _ in state = true }
                .onChanged { engine.touchMoved($0, geometry: g) }
                .onEnded { engine.touchEnded($0) }
        )
        .onChange(of: touching) { _, now in
            // A drag the system cancelled (a swipe from the edge, an alert) never reports its end;
            // after a normal end (onEnded has run) this does nothing.
            if !now { engine.touchCancelled() }
        }
        .clipped()
        .accessibilityElement()
        .accessibilityLabel("Rotary dial. Number dialled: \(digits.isEmpty ? "none" : digits.map { String($0.value) }.joined(separator: " ")). Drag a hole to the stop to dial; hold the card to clear.")
    }

    private func header(insets: EdgeInsets) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text("ROTARY DIAL")
                .font(.system(size: 11, weight: .bold))
                .kerning(3)
            Spacer(minLength: 8)
            Text("Elmwood \(RotaryPhoneDialDrawing.cardNumber)")
                .font(.custom("AmericanTypewriter", fixedSize: 13))
        }
        .foregroundStyle(M.pale)
        .padding(.horizontal, M.side)
        .padding(.top, insets.top + 16)
        .allowsHitTesting(false)
    }

    /// The typed digits, left to right on the paper, grouped 3 · 3 · 4.
    private func strip(_ g: RotaryPhoneDialGeometry) -> some View {
        HStack(spacing: 3) {
            ForEach(Array(digits.enumerated()), id: \.element.id) { index, digit in
                RotaryPhoneDialTypedDigit(digit: digit, reduced: reduceMotion)
                    .padding(.leading, index == 3 || index == 6 ? 12 : 0)
                    .transition(.asymmetric(insertion: .identity, removal: .opacity))
            }
        }
        .frame(width: max(0, g.width - (g.strip.minX + 18) - (g.strip.minX + 104)), height: g.strip.height, alignment: .leading)
        .offset(x: g.strip.minX + 18, y: g.strip.minY)
        .allowsHitTesting(false)
    }

    private func addDigit(_ value: Int, byUser: Bool) {
        let id = nextID
        nextID += 1
        digits = Array((digits + [RotaryPhoneDialDigit(id: id, value: value)]).suffix(M.maxDigits))
        if byUser { onDigit?(value) }
    }

    private func clearDigits() {
        withAnimation(M.fadeOut) { digits = [] }
    }
}

struct RotaryPhoneDialDigit: Identifiable, Equatable {
    let id: Int
    let value: Int
}

/// One typed digit: it drops onto the strip from above, a little large, and settles with a spring
/// (or fades in under reduced motion), with a small typewriter wobble of its own.
private struct RotaryPhoneDialTypedDigit: View {
    let digit: RotaryPhoneDialDigit
    let reduced: Bool

    @State private var landed = false
    @State private var settled = false
    @State private var inked = false

    private typealias M = RotaryPhoneDialMetrics

    var body: some View {
        let j = M.jitter(digit.id)
        Text(String(digit.value))
            .font(.custom("AmericanTypewriter-Semibold", fixedSize: 28))
            .monospacedDigit()
            .foregroundStyle(RotaryPhoneDialDrawing.rgb(0x201D18, j.ink))
            .rotationEffect(.degrees(j.rotate))
            .offset(y: j.y)
            .scaleEffect(reduced || settled ? 1 : 1.18)
            .offset(y: reduced || landed ? 0 : -22)
            .opacity(inked ? 1 : 0)
            .onAppear {
                if reduced {
                    withAnimation(M.fadeIn) { inked = true }
                    return
                }
                withAnimation(.linear(duration: 0.09)) { inked = true }
                withAnimation(M.drop) { landed = true }
                withAnimation(M.dropScale) { settled = true }
            }
    }
}

enum RotaryPhoneDialMetrics {
    // Layout, in points: the strip under the header, the dial below it, the hint at the bottom.
    static let side = 24.0
    static let header = 54.0
    static let stripH = 74.0
    static let footer = 56.0
    // The plate's radius: at most this share of the width, and this many points.
    static let plateShare = 0.42
    static let plateMax = 186.0
    // The wheel and its holes, as shares of the plate and wheel.
    static let wheel = 0.93
    static let ring = 0.71
    static let hole = 0.158
    static let hub = 0.4
    // The strip keeps the last maxDigits digits, grouped 3 · 3 · 4 like a phone number.
    static let maxDigits = 10
    // Long press on the card clears the strip after clearPress s, unless the finger strays cardSlop pt.
    static let clearPress = 0.55
    static let cardSlop = 14.0
    // After a touch the loop waits this long, then starts over.
    static let userHold = 3.5

    static let pale = RotaryPhoneDialDrawing.rgb(0xF1E8D3, 0.62)

    // Reanimated's springs with a duration and a damping ratio, solved for their stiffness the way
    // Reanimated solves them: the drop (420 ms, 0.55) and the size settling (360 ms, 0.7).
    static let drop = Animation.interpolatingSpring(mass: 1, stiffness: 1231.2, damping: 2 * 0.55 * 1231.2.squareRoot())
    static let dropScale = Animation.interpolatingSpring(mass: 1, stiffness: 1045.5, damping: 2 * 0.7 * 1045.5.squareRoot())
    // Reanimated's default timing curve, ease-in-out quad.
    static let fadeIn = Animation.timingCurve(0.455, 0.03, 0.515, 0.955, duration: 0.2)
    static let fadeOut = Animation.timingCurve(0.455, 0.03, 0.515, 0.955, duration: 0.22)

    /// A little typewriter wobble per position, so the digits do not sit on a ruler.
    static func jitter(_ index: Int) -> (rotate: Double, y: Double, ink: Double) {
        let i = Double(index)
        let a = sin(i * 12.9898 + 4.1) * 43758.5453
        let b = sin(i * 78.233 + 1.7) * 24634.6345
        let fa = a - floor(a)
        let fb = b - floor(b)
        return ((fa - 0.5) * 4, (fb - 0.5) * 1.6, 0.8 + fa * 0.2)
    }
}

struct RotaryPhoneDialSettings {
    var auto: Bool
    var reduced: Bool
    var onDigit: (Int, Bool) -> Void
    var onClear: () -> Void
}

/// One frame to draw.
struct RotaryPhoneDialFrame {
    var phi = 0.0
    var kick = 0.0
    var card = 0.0
    var pulses = 0
    var pulseShown = 0.0
    var finger = RotaryPhoneDialFinger()
}

/// The mechanism and the autoPlay clock, stepped once a frame like the RN frame callback, and the
/// touches that turn the wheel or press the card.
@MainActor
final class RotaryPhoneDialEngine {
    private typealias T = RotaryPhoneDialTuning
    private typealias M = RotaryPhoneDialMetrics

    private enum Touch {
        case none
        case ignored
        case wheel
        case card(start: Double, fired: Bool)
    }

    private var sim: RotaryPhoneDialSim?
    private var last: Date?
    private var clock = 0.0
    private var hold = 0.0
    private var wasAuto: Bool?
    private var pulseShown = 0.0
    private var now = 0.0
    private var touch = Touch.none
    /// Where the drag being tracked started, to tell a new drag from the one before.
    private var touchStart: CGPoint?
    private let userID = 1
    private let impactLight = UIImpactFeedbackGenerator(style: .light)
    private let impactMedium = UIImpactFeedbackGenerator(style: .medium)
    private let selection = UISelectionFeedbackGenerator()

    private func ensure(reduced: Bool) -> RotaryPhoneDialSim {
        if let sim { return sim }
        let s = RotaryPhoneDialSim(reduced: reduced)
        sim = s
        return s
    }

    func step(to date: Date, settings: RotaryPhoneDialSettings) -> RotaryPhoneDialFrame {
        if wasAuto != settings.auto {
            wasAuto = settings.auto
            clock = 0
            hold = 0
        }
        // The script and the mechanism share one clock, capped so a slow frame cannot skip ahead.
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), T.maxFrame)
        now += dt
        let s = ensure(reduced: settings.reduced)
        var finger = RotaryPhoneDialFinger()
        if dt > 0 {
            // A finger resting in a hole keeps the script away.
            if s.touch != -1 && !s.scripted { hold = M.userHold }
            if hold > 0 {
                hold -= dt
                if hold <= 0 { clock = 0 }
            } else if settings.auto {
                let before = clock
                let t = (before + dt).truncatingRemainder(dividingBy: T.loop)
                clock = t
                finger = s.script(before: before, now: t)
            }
            // A held card clears the strip once it has been held long enough.
            if case let .card(start, fired) = touch, !fired, now - start >= M.clearPress {
                touch = .card(start: start, fired: true)
                s.clear(byUser: true)
                s.cardPress(false)
            }
            s.step(dt)
            let showing = s.dialling || (s.mode == T.modeDrag && s.reached) ? 1.0 : 0.0
            pulseShown += (showing - pulseShown) * (1 - exp(-dt * (showing > 0 ? 14 : 5)))
            if s.events != 0 {
                let events = s.events
                let byUser = s.eventsByUser
                let digit = s.digit
                s.events = 0
                s.eventsByUser = false
                // State changes wait until this frame's body has been built.
                DispatchQueue.main.async {
                    if events & T.eventDigit != 0 { settings.onDigit(digit, byUser) }
                    if events & T.eventClear != 0 { settings.onClear() }
                }
                if byUser { feel(events) }
            }
        }
        return RotaryPhoneDialFrame(phi: s.phi, kick: s.kick, card: s.card, pulses: s.pulses, pulseShown: pulseShown, finger: finger)
    }

    private func feel(_ events: Int) {
        if events & T.eventClear != 0 {
            impactMedium.impactOccurred()
        } else if events & T.eventStop != 0 {
            impactLight.impactOccurred()
        } else if events & T.eventTick != 0 {
            selection.selectionChanged()
        }
    }

    private func touched(_ s: RotaryPhoneDialSim) {
        hold = M.userHold
        s.cancelScripted()
    }

    private static func angle(_ p: CGPoint, _ g: RotaryPhoneDialGeometry) -> Double {
        atan2(p.y - g.cy, p.x - g.cx) * 180 / .pi
    }

    func touchMoved(_ drag: DragGesture.Value, geometry g: RotaryPhoneDialGeometry) {
        guard let s = sim else { return }
        // A new drag while the last one never reported its end: close that one first.
        if let start = touchStart, start != drag.startLocation { cancelNow() }
        touchStart = drag.startLocation
        switch touch {
        case .none:
            let p = drag.startLocation
            let dx = p.x - g.cx
            let dy = p.y - g.cy
            // The wheel only moves with a finger in a hole: anywhere else the touch is not for it.
            if s.touch != -1 && !s.scripted {
                touch = .ignored
                return
            }
            let hole = s.holeAt(angle: Self.angle(p, g), radius: hypot(dx, dy), holeR: g.holeR, ringR: g.ringR)
            if hole >= 0 {
                touched(s)
                s.grab(userID, hole: hole, angle: Self.angle(p, g), scripted: false)
                touch = .wheel
                if drag.location != p { s.drag(userID, angle: Self.angle(drag.location, g)) }
            } else if hypot(dx, dy) < g.hubR {
                // Holding the number card clears the strip.
                touched(s)
                s.cardPress(true)
                touch = .card(start: now, fired: false)
            } else {
                touch = .ignored
            }
        case .wheel:
            hold = M.userHold
            s.drag(userID, angle: Self.angle(drag.location, g))
        case .card:
            if hypot(drag.translation.width, drag.translation.height) > M.cardSlop {
                s.cardPress(false)
                touch = .ignored
            }
        case .ignored:
            break
        }
    }

    func touchEnded(_ drag: DragGesture.Value) {
        guard let s = sim else {
            touch = .none
            return
        }
        switch touch {
        case .wheel: s.release(userID)
        case .card: s.cardPress(false)
        default: break
        }
        touch = .none
        touchStart = nil
    }

    /// The drag may have been cancelled without an end. Checked a moment later, once a normal
    /// `onEnded` has surely run: if the same drag is still open, it is closed.
    func touchCancelled() {
        guard let start = touchStart else { return }
        Task { @MainActor [weak self] in
            guard let self, self.touchStart == start else { return }
            self.cancelNow()
        }
    }

    /// Closes the open drag: the wheel goes home, the card comes back out.
    private func cancelNow() {
        if let s = sim {
            switch touch {
            case .wheel: s.release(userID)
            case .card: s.cardPress(false)
            default: break
            }
        }
        touch = .none
        touchStart = nil
    }
}
