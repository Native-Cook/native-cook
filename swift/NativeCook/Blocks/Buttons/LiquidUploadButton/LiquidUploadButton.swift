import SwiftUI

/// "Upload" button whose icon circle fills with liquid. A tap folds the arrow into a tick that
/// floats up on the rising, rippling level while the label reads "Uploading"; when the circle is
/// full a check is drawn and the label reads "Uploaded". A second tap (or autoPlay) resets it.
struct LiquidUploadButton: View {
    enum Shape { case pill, round }
    enum Tone { case blue, dark, light }

    var label = "Upload"
    var loadingLabel = "Uploading"
    var doneLabel = "Uploaded"
    /// `pill` shows the label next to the icon, `round` is the icon alone.
    var shape = Shape.pill
    var tone = Tone.blue
    /// Uploads by itself, shows the result, resets and starts over.
    var autoPlay = false
    /// Extra wait before the first automatic run, to stagger a group of buttons.
    var autoPlayDelay = 0.0
    var action: () -> Void = {}

    @State private var taps = 0
    @State private var phase = Phase.idle

    var body: some View {
        Button {
            if phase == .idle { action() }
            taps += 1
        } label: {
            Text(status)
        }
        .buttonStyle(Style(parent: self, taps: taps, phase: $phase))
        .accessibilityLabel(status)
    }

    private var status: String {
        switch phase {
        case .uploading: loadingLabel
        case .done: doneLabel
        default: label
        }
    }

    private struct Style: ButtonStyle {
        let parent: LiquidUploadButton
        let taps: Int
        @Binding var phase: Phase

        func makeBody(configuration: Configuration) -> some View {
            Surface(
                label: parent.label, loadingLabel: parent.loadingLabel, doneLabel: parent.doneLabel,
                round: parent.shape == .round, colors: palette(parent.shape, parent.tone),
                autoPlay: parent.autoPlay, autoPlayDelay: parent.autoPlayDelay,
                taps: taps, isPressed: configuration.isPressed, phase: $phase
            )
        }
    }
}

private enum Phase {
    case idle, uploading, done, resetting
}

private enum Metrics {
    static let height = 52.0
    static let pillWidth = 164.0
    /// The icon sits in a circle as tall as the button; the liquid rises inside it.
    static let r = height / 2
    static let labelSlide = 30.0
    static let autoPause = 1.1
    static let autoHold = 2.4
    /// The liquid's surface bulges in the middle and ripples while it rises.
    static let dome = 4.5
    static let wave = 1.4
    static let arrowHalf = 6.5
    static let head = 5.2
    static let tick = 5.5
}

/// One clock in seconds runs the upload (0 → `done`) and later the reset (`done` → `total`).
private enum Clock {
    static let swapLoading = (0.0, 0.35)
    static let flat = (0.0, 0.18)
    static let drop = (0.05, 0.4)
    static let dashOut = (0.25, 0.45)
    static let rise = (0.45, 2.15)
    static let swapDone = (2.35, 2.7)
    static let check = (2.5, 2.85)
    static let done = 2.9
    static let fade = (2.9, 3.25)
    static let swapIdle = (2.95, 3.35)
    static let arrowBack = (3.1, 3.45)
    static let total = 3.5
}

private struct Palette {
    let base: Color
    let well: Color
    let liquid: Color
    let ink: Color
    let doneInk: Color
}

private func palette(_ shape: LiquidUploadButton.Shape, _ tone: LiquidUploadButton.Tone) -> Palette {
    let round = shape == .round
    let white = Color.white
    switch tone {
    case .light:
        return Palette(
            base: Color(hex: 0xf2f7ff), well: Color(hex: round ? 0xf2f7ff : 0xe4ecfb),
            liquid: Color(hex: 0x3350fc), ink: Color(hex: 0x3350fc), doneInk: white
        )
    case .dark:
        return round
            ? Palette(base: Color(hex: 0x1c2030), well: Color(hex: 0x1c2030), liquid: Color(hex: 0x57cf88), ink: white, doneInk: white)
            : Palette(base: Color(hex: 0x1b1f2e), well: Color(hex: 0x171a27), liquid: Color(hex: 0x272c3c), ink: white, doneInk: white)
    case .blue:
        return round
            ? Palette(base: Color(hex: 0x2743e7), well: Color(hex: 0x2743e7), liquid: Color(hex: 0x4a66ec), ink: white, doneInk: white)
            : Palette(base: Color(hex: 0x3350fc), well: Color(hex: 0x2743e7), liquid: Color(hex: 0x4a66ec), ink: white, doneInk: white)
    }
}

private func seg(_ t: Double, _ window: (Double, Double)) -> Double {
    min(1, max(0, (t - window.0) / (window.1 - window.0)))
}

private func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }

/// Height of the liquid's surface at `u` (−1…1 across the circle), relative to the circle's centre.
private func surface(_ u: Double, _ t: Double) -> Double {
    let rise = pow(seg(t, Clock.rise), 1.7)
    let swell = sin(.pi * rise)
    let level = Metrics.r - rise * (2 * Metrics.r + Metrics.dome + 2)
    return level - Metrics.dome * swell * (1 - u * u) + Metrics.wave * swell * sin(u * 2.6 + t * 6)
}

private struct Surface: View {
    let label: String
    let loadingLabel: String
    let doneLabel: String
    let round: Bool
    let colors: Palette
    let autoPlay: Bool
    let autoPlayDelay: Double
    let taps: Int
    let isPressed: Bool
    @Binding var phase: Phase

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// When the current upload or reset started.
    @State private var origin = Date.distantPast
    @State private var firstRun = true

    private var width: Double { round ? Metrics.height : Metrics.pillWidth }

    var body: some View {
        TimelineView(.animation(paused: phase == .idle || phase == .done)) { timeline in
            let t = clock(at: timeline.date)
            let slide = reduceMotion ? 0 : Metrics.labelSlide

            ZStack(alignment: .leading) {
                Canvas { context, _ in
                    draw(context, t: t)
                }
                if !round {
                    // Labels leave through the top and arrive from below; the reset runs the other way.
                    let toLoading = seg(t, Clock.swapLoading)
                    let toDone = seg(t, Clock.swapDone)
                    let toIdle = seg(t, Clock.swapIdle)
                    ZStack {
                        Text(label)
                            .opacity(1 - toLoading + toIdle)
                            .offset(y: -slide * (toIdle > 0 ? 1 - easeOut(toIdle) : easeOut(toLoading)))
                        Text(loadingLabel)
                            .opacity(toLoading - toDone)
                            .offset(y: slide * (1 - easeOut(toLoading)) - slide * easeOut(toDone))
                        Text(doneLabel)
                            .opacity(toDone - toIdle)
                            .offset(y: slide * (1 - easeOut(toDone)) + slide * easeOut(toIdle))
                    }
                    .font(.system(size: 15, weight: .semibold))
                    .lineLimit(1)
                    .foregroundStyle(colors.ink)
                    // The label area is everything left of the icon circle; labels sliding out are clipped by it.
                    .frame(width: Metrics.pillWidth - Metrics.height + 8, height: Metrics.height)
                    .clipped()
                    // The button itself announces the current label.
                    .accessibilityHidden(true)
                }
            }
            .frame(width: width, height: Metrics.height)
        }
        .contentShape(Capsule())
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        .onChange(of: taps) {
            if phase == .idle {
                begin(.uploading)
            } else if phase == .done {
                begin(.resetting)
            }
        }
        .task(id: Step(phase: phase, auto: autoPlay && !reduceMotion && !isPressed)) {
            await advance()
        }
    }

    private struct Step: Equatable {
        let phase: Phase
        let auto: Bool
    }

    private func clock(at date: Date) -> Double {
        switch phase {
        case .idle: 0
        case .uploading: min(max(date.timeIntervalSince(origin), 0), Clock.done)
        case .done: Clock.done
        case .resetting: min(Clock.done + max(date.timeIntervalSince(origin), 0), Clock.total)
        }
    }

    private func begin(_ next: Phase) {
        origin = .now
        phase = next
    }

    /// Moves on when the current phase has run its course. autoPlay starts an upload after a pause
    /// and clears the result after a moment; left alone, the result stays up until the next tap.
    private func advance() async {
        let auto = autoPlay && !reduceMotion && !isPressed
        switch phase {
        case .uploading:
            guard await sleep(Clock.done - Date.now.timeIntervalSince(origin)) else { return }
            phase = .done
        case .resetting:
            guard await sleep(Clock.total - Clock.done - Date.now.timeIntervalSince(origin)) else { return }
            // The reset ends looking exactly like the start, so rewinding the clock is invisible.
            phase = .idle
        case .idle:
            guard auto, await sleep(Metrics.autoPause + (firstRun ? autoPlayDelay : 0)) else { return }
            firstRun = false
            begin(.uploading)
        case .done:
            guard auto, await sleep(Metrics.autoHold) else { return }
            begin(.resetting)
        }
    }

    /// False when the wait was cancelled.
    private func sleep(_ seconds: Double) async -> Bool {
        if seconds > 0 { try? await Task.sleep(for: .seconds(seconds)) }
        return !Task.isCancelled
    }

    private func draw(_ context: GraphicsContext, t: Double) {
        let r = Metrics.r
        let cx = width - r
        let still = reduceMotion
        let resetting = t > Clock.done
        // How far the returning arrow still has to rise during the reset.
        let back = seg(t, Clock.arrowBack)
        let arrowOffset = resetting && !still ? 8 * (1 - easeOut(back)) : 0
        let stroke = StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round)
        let well = Path(ellipseIn: CGRect(x: cx - r, y: 0, width: r * 2, height: r * 2))

        context.fill(capsule(CGRect(x: 0, y: 0, width: width, height: Metrics.height)), with: .color(colors.base))
        if !round { context.fill(well, with: .color(colors.well)) }

        var inside = context
        inside.clip(to: well)

        // The liquid. Reduced motion has no rising level: the fill simply fades in.
        let fade = 1 - seg(t, Clock.fade)
        if still {
            inside.fill(well, with: .color(colors.liquid.opacity(seg(t, Clock.rise) * fade)))
        } else if t > Clock.rise.0 {
            var liquid = Path()
            liquid.move(to: CGPoint(x: cx - r, y: Metrics.height))
            for i in 0...12 {
                let u = -1 + Double(i) / 6
                liquid.addLine(to: CGPoint(x: cx + u * r, y: r + surface(u, t)))
            }
            liquid.addLine(to: CGPoint(x: cx + r, y: Metrics.height))
            liquid.closeSubpath()
            inside.fill(liquid, with: .color(colors.liquid.opacity(fade)))
        }

        // The arrow's stem. It drops to the bottom of the circle as a short tick and floats up on the liquid.
        var stem = Path()
        let stemOpacity: Double
        if resetting || still {
            stem.move(to: CGPoint(x: cx, y: r + Metrics.arrowHalf + arrowOffset))
            stem.addLine(to: CGPoint(x: cx, y: r - Metrics.arrowHalf + arrowOffset))
            stemOpacity = resetting ? back : 1 - seg(t, Clock.dashOut)
        } else {
            let drop = seg(t, Clock.drop) * seg(t, Clock.drop)
            let length = Metrics.arrowHalf * 2 - (Metrics.arrowHalf * 2 - Metrics.tick) * drop
            let floor = min(Metrics.arrowHalf + (r - 1.5 - Metrics.arrowHalf) * drop, surface(0, t))
            stem.move(to: CGPoint(x: cx, y: r + floor))
            stem.addLine(to: CGPoint(x: cx, y: r + floor - length))
            stemOpacity = t >= Clock.rise.1 ? 0 : 1
        }
        inside.stroke(stem, with: .color(colors.ink.opacity(stemOpacity)), style: stroke)

        // The arrow's head. It folds flat into a dash at the start of an upload.
        let fold = resetting || still ? 0 : easeOut(seg(t, Clock.flat))
        let tip = r - Metrics.arrowHalf + 3.5 * fold + arrowOffset
        let wing = Metrics.head * (1 - fold)
        var head = Path()
        head.move(to: CGPoint(x: cx - Metrics.head, y: tip + wing))
        head.addLine(to: CGPoint(x: cx, y: tip))
        head.addLine(to: CGPoint(x: cx + Metrics.head, y: tip + wing))
        let headOpacity = resetting ? back : 1 - seg(t, Clock.dashOut)
        context.stroke(head, with: .color(colors.ink.opacity(headOpacity)), style: stroke)

        let drawn = easeOut(seg(t, Clock.check))
        if drawn > 0 {
            var check = Path()
            check.move(to: CGPoint(x: cx - 6.5, y: r + 0.5))
            check.addLine(to: CGPoint(x: cx - 2, y: r + 5))
            check.addLine(to: CGPoint(x: cx + 7, y: r - 4.5))
            context.stroke(check.trimmedPath(from: 0, to: drawn), with: .color(colors.doneInk.opacity(fade)), style: stroke)
        }
    }
}

#Preview {
    ZStack {
        Color(hex: 0xe1e9f7).ignoresSafeArea()
        VStack(spacing: 22) {
            HStack(spacing: 24) {
                LiquidUploadButton(autoPlay: true)
                LiquidUploadButton(shape: .round, tone: .dark, autoPlay: true, autoPlayDelay: 0.9)
            }
            HStack(spacing: 24) {
                LiquidUploadButton(shape: .round, tone: .light, autoPlay: true, autoPlayDelay: 1.8)
                LiquidUploadButton(tone: .dark, autoPlay: true, autoPlayDelay: 2.7)
            }
        }
    }
}
