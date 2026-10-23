import SwiftUI

enum JellyAttachCapsuleKind: Int, CaseIterable {
    case video, camera, photo

    fileprivate var label: String {
        switch self {
        case .video: "Video"
        case .camera: "Camera"
        case .photo: "Photo"
        }
    }
}

/// Violet message capsule made of jelly. A tap on its plus tilts and squashes the whole capsule,
/// turns the plus into a cross, dissolves the message field and pops three attachment buttons out
/// of the plus one after another; closing wobbles it the other way and wipes the field back in.
struct JellyAttachCapsule: View {
    /// Controlled text; leave nil and the field keeps its own.
    var text: Binding<String>? = nil
    /// Opens the capsule, picks the camera and closes again in a loop, without opening the keyboard.
    var autoPlay = false
    var placeholder = "Message"
    /// Called with the text when the keyboard's send key is pressed; the field clears.
    var onSend: ((String) -> Void)? = nil
    /// Called when one of the three attachment buttons is tapped; the capsule closes after its pulse.
    var onPick: ((JellyAttachCapsuleKind) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focused: Bool
    @State private var draft = ""
    @State private var open = false
    @State private var motion = Motion()
    /// Keeps the timeline running only while the jelly moves.
    @State private var busy = false
    /// The button the autoPlay script is pressing: 0 is the plus.
    @State private var scriptPress: Int? = nil
    /// True while the autoPlay script owns the capsule.
    @State private var playing = false
    @State private var runToken = 0
    @State private var resumeToken = 0
    @State private var pickToken = 0
    @State private var quietToken = 0

    /// Moments the wobble and the field are timed from. A class, so writing them never rebuilds the view.
    private final class Motion {
        // The last two kicks, so a quick second tap adds to the first wobble instead of cutting it.
        var kickAt = Date.distantPast
        var kickTurn = 0.0
        var earlierAt = Date.distantPast
        var earlierTurn = 0.0
        var openAt = Date.distantPast
        var closeAt = Date.distantPast
        var pickAt = Date.distantPast
        var picked = 0
    }

    private var controlled: Bool { text != nil }
    private var auto: Bool { autoPlay && !controlled && !reduceMotion }
    private var current: String { text?.wrappedValue ?? draft }

    private var field: Binding<String> {
        Binding(get: { current }, set: { write($0) })
    }

    var body: some View {
        TimelineView(.animation(paused: !busy)) { timeline in
            let now = timeline.date
            let tilt = reduceMotion
                ? 0
                : Metrics.tilt * (motion.kickTurn * wobble(now.timeIntervalSince(motion.kickAt), Metrics.tiltOmega, Metrics.tiltDecay)
                    + motion.earlierTurn * wobble(now.timeIntervalSince(motion.earlierAt), Metrics.tiltOmega, Metrics.tiltDecay))
            let squash = reduceMotion
                ? 0
                : Metrics.squash * (wobble(now.timeIntervalSince(motion.kickAt), Metrics.squashOmega, Metrics.squashDecay)
                    + wobble(now.timeIntervalSince(motion.earlierAt), Metrics.squashOmega, Metrics.squashDecay))

            ZStack(alignment: .topLeading) {
                Canvas { context, _ in drawCapsule(context) }
                    .padding(-Metrics.bleed)
                    .allowsHitTesting(false)
                messageField(at: now)
                ForEach(JellyAttachCapsuleKind.allCases, id: \.rawValue) { kind in
                    attachButton(kind, at: now)
                }
                plusButton
            }
            .frame(width: Metrics.width, height: Metrics.height)
            // The jelly squashes about its middle and swings about the plus, where the finger is.
            .scaleEffect(x: 1 + 0.6 * squash, y: 1 - squash)
            .rotationEffect(.degrees(tilt), anchor: UnitPoint(x: Metrics.slot(0) / Metrics.width, y: 0.5))
        }
        .onChange(of: focused) { _, _ in touch() }
        .onChange(of: auto, initial: true) { _, now in
            playing = now
            runToken += 1
        }
        .task(id: runToken) { await play() }
        .task(id: resumeToken) { await resume() }
        .task(id: pickToken) {
            guard pickToken > 0 else { return }
            try? await Task.sleep(for: .seconds(Metrics.pickSeconds))
            if !Task.isCancelled { setOpen(false) }
        }
        .task(id: quietToken) {
            try? await Task.sleep(for: .seconds(Metrics.settleSeconds))
            if !Task.isCancelled { busy = false }
        }
    }

    // MARK: Parts

    private var plusButton: some View {
        Button {
            touch()
            setOpen(!open)
        } label: {
            ZStack {
                Circle().fill(Palette.button)
                plusGlyph
                    .rotationEffect(.degrees(open ? 45 : 0))
            }
            .frame(width: Metrics.button, height: Metrics.button)
            .contentShape(Circle())
        }
        .buttonStyle(Press(held: scriptPress == 0))
        .animation(reduceMotion ? .easeOut(duration: 0.15) : Metrics.openSpring, value: open)
        .offset(x: Metrics.slot(0) - Metrics.button / 2, y: Metrics.pad)
        .accessibilityLabel(open ? "Close attachments" : "Attach")
    }

    /// One of the three buttons that live under the plus until the capsule opens.
    private func attachButton(_ kind: JellyAttachCapsuleKind, at now: Date) -> some View {
        let index = kind.rawValue + 1
        let pulse = motion.picked == index ? min(max(now.timeIntervalSince(motion.pickAt) / Metrics.pulseSeconds, 0), 1) : 1
        // They leave one after another, and come home in the opposite order.
        let delay = open ? 0.06 + 0.08 * Double(index - 1) : 0.07 * Double(3 - index)
        let spring = reduceMotion ? Animation.easeOut(duration: 0.15) : (open ? Metrics.openSpring : Metrics.closeSpring).delay(delay)
        let travel = reduceMotion || open ? Metrics.slot(index) : Metrics.slot(0)
        return Button {
            touch()
            pick(index)
            onPick?(kind)
        } label: {
            ZStack {
                Circle().fill(Palette.button)
                // A ring leaves the button that was picked.
                Circle()
                    .stroke(.white.opacity(0.6 * (1 - pulse)), lineWidth: 1.5)
                    .scaleEffect(1 + 0.5 * pulse)
                glyph(kind)
            }
            .frame(width: Metrics.button, height: Metrics.button)
            .contentShape(Circle())
        }
        .buttonStyle(Press(held: scriptPress == index))
        .scaleEffect(reduceMotion ? 1 : 1 + Metrics.pulse * sin(.pi * pulse))
        .scaleEffect(reduceMotion || open ? 1 : 0.4)
        .opacity(open ? 1 : 0)
        .offset(x: travel - Metrics.button / 2, y: Metrics.pad)
        .animation(spring, value: open)
        .allowsHitTesting(open)
        .accessibilityHidden(!open)
        .accessibilityLabel(kind.label)
    }

    private func messageField(at now: Date) -> some View {
        let sinceClose = now.timeIntervalSince(motion.closeAt) - Metrics.wipeDelay
        // Opening dissolves the field; closing wipes it back in from the left under a sheen.
        let fade = open ? 1 - min(max(now.timeIntervalSince(motion.openAt) / Metrics.fadeSeconds, 0), 1) : 1
        let wipe = open || reduceMotion ? 1 : 1 - pow(1 - min(max(sinceClose / Metrics.wipeSeconds, 0), 1), 3)
        let sheen = open || reduceMotion ? 1 : min(max(sinceClose / Metrics.sheenSeconds, 0), 1)
        return ZStack(alignment: .leading) {
            Palette.field
            ZStack(alignment: .leading) {
                Text(placeholder)
                    .foregroundStyle(Palette.placeholder)
                    .opacity(current.isEmpty ? 1 : 0)
                TextField("", text: field)
                    .focused($focused)
                    .foregroundStyle(.white)
                    .tint(.white)
                    .submitLabel(.send)
                    .onSubmit { send() }
                    .accessibilityLabel(placeholder)
            }
            .font(.system(size: 15))
            .lineLimit(1)
            .padding(.horizontal, 16)
            .frame(width: Metrics.fieldWidth)
            LinearGradient(colors: [.white.opacity(0), .white.opacity(0.3), .white.opacity(0)], startPoint: .leading, endPoint: .trailing)
                .frame(width: Metrics.sheenWidth)
                .offset(x: -Metrics.sheenWidth + (Metrics.fieldWidth + Metrics.sheenWidth) * sheen)
                .allowsHitTesting(false)
        }
        .frame(width: Metrics.fieldWidth * wipe, height: Metrics.button, alignment: .leading)
        .clipShape(Capsule())
        .opacity(fade)
        .offset(x: Metrics.fieldX, y: Metrics.pad)
        .allowsHitTesting(!open)
        .accessibilityHidden(open)
    }

    // MARK: Behaviour

    private func write(_ value: String) {
        if let text { text.wrappedValue = value } else { draft = value }
    }

    /// Any touch stops the script; it comes back a few seconds after the last one.
    private func touch() {
        playing = false
        scriptPress = nil
        runToken += 1
        resumeToken += 1
    }

    private func wake() {
        busy = true
        quietToken += 1
    }

    /// Opens or closes, and kicks the jelly: the right end swings up to open and dips to close.
    private func setOpen(_ next: Bool) {
        guard next != open else { return }
        let now = Date.now
        motion.earlierAt = motion.kickAt
        motion.earlierTurn = motion.kickTurn
        motion.kickAt = now
        motion.kickTurn = next ? -1 : 1
        if next {
            motion.openAt = now
            focused = false
        } else {
            motion.closeAt = now
        }
        open = next
        wake()
    }

    /// The picked button pulses, then the capsule closes.
    private func pick(_ index: Int) {
        motion.picked = index
        motion.pickAt = .now
        wake()
        pickToken += 1
    }

    private func send() {
        let sent = current
        guard !sent.isEmpty else { return }
        write("")
        onSend?(sent)
    }

    private func play() async {
        guard playing else { return }
        let clock = ContinuousClock()
        var start = clock.now
        while true {
            guard await wait(until: start + .seconds(Metrics.openAt), on: clock) else { return }
            scriptPress = 0
            guard await wait(until: start + .seconds(Metrics.openAt + Metrics.pressSeconds), on: clock) else { return }
            scriptPress = nil
            setOpen(true)
            guard await wait(until: start + .seconds(Metrics.pickAt), on: clock) else { return }
            scriptPress = Metrics.scriptPick
            guard await wait(until: start + .seconds(Metrics.pickAt + Metrics.pressSeconds), on: clock) else { return }
            scriptPress = nil
            pick(Metrics.scriptPick)
            start += .seconds(Metrics.loop)
            guard await wait(until: start, on: clock) else { return }
        }
    }

    private func wait(until instant: ContinuousClock.Instant, on clock: ContinuousClock) async -> Bool {
        (try? await clock.sleep(until: instant)) != nil
    }

    private func resume() async {
        guard resumeToken > 0, auto else { return }
        try? await Task.sleep(for: .seconds(Metrics.resumeAfter))
        guard !Task.isCancelled, !focused else { return }
        setOpen(false)
        draft = ""
        playing = true
        runToken += 1
    }

    // MARK: Drawing

    private func drawCapsule(_ context: GraphicsContext) {
        let rect = CGRect(x: Metrics.bleed, y: Metrics.bleed, width: Metrics.width, height: Metrics.height)
        var shadow = context
        shadow.addFilter(.blur(radius: 15))
        shadow.fill(capsule(rect.insetBy(dx: 14, dy: 6).offsetBy(dx: 0, dy: 18)), with: .color(Palette.capsule.opacity(0.45)))
        context.fill(capsule(rect), with: .color(Palette.capsule))
    }

    private var plusGlyph: some View {
        Path { p in
            p.move(to: CGPoint(x: 9, y: 3.8))
            p.addLine(to: CGPoint(x: 9, y: 14.2))
            p.move(to: CGPoint(x: 3.8, y: 9))
            p.addLine(to: CGPoint(x: 14.2, y: 9))
        }
        .stroke(.white, style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
        .frame(width: 18, height: 18)
    }

    private func glyph(_ kind: JellyAttachCapsuleKind) -> some View {
        let icon = glyphs[kind.rawValue]
        return Path { p in
            p.addRoundedRect(in: icon.box, cornerSize: CGSize(width: icon.corner, height: icon.corner), style: .circular)
            if let lens = icon.lens {
                p.addEllipse(in: CGRect(x: lens.x - lens.r, y: lens.y - lens.r, width: lens.r * 2, height: lens.r * 2))
            }
            for (index, point) in icon.line.enumerated() {
                if index == 0 { p.move(to: point) } else { p.addLine(to: point) }
            }
        }
        .stroke(.white, style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
        .frame(width: 18, height: 18)
    }

    private struct Press: ButtonStyle {
        /// True while the autoPlay script holds the button down.
        let held: Bool

        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed || held ? 0.88 : 1)
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed || held)
                .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
        }
    }
}

private enum Metrics {
    static let width = 262.0
    static let height = 64.0
    static let pad = 12.0
    static let button = 40.0
    /// The canvas overflows the capsule by this much on every side to fit its shadow.
    static let bleed = 64.0
    static let fieldX = pad + button + 10
    static let fieldWidth = width - fieldX - pad
    static let sheenWidth = 70.0

    /// Centre of the plus (0) and of the three buttons spread evenly to its right.
    static func slot(_ index: Int) -> Double {
        pad + button / 2 + Double(index) * (width - 2 * pad - button) / 3
    }

    // The kick: a sine that dies away. The tilt peaks near 5 degrees a fifth of a second in.
    static let tilt = 10.5
    static let tiltOmega = 5.24
    static let tiltDecay = 3.0
    static let squash = 0.1
    static let squashOmega = 15.0
    static let squashDecay = 6.5

    // The buttons pop out with a little overshoot and come home without one.
    static let openSpring = Animation.interpolatingSpring(mass: 1, stiffness: 260, damping: 23)
    static let closeSpring = Animation.interpolatingSpring(mass: 1, stiffness: 300, damping: 34)
    static let fadeSeconds = 0.16
    /// The field waits for the buttons to be home before it wipes in.
    static let wipeDelay = 0.22
    static let wipeSeconds = 0.34
    static let sheenSeconds = 0.52
    /// A picked button swells by this much, and the capsule closes this long after the tap.
    static let pulse = 0.22
    static let pulseSeconds = 0.28
    static let pickSeconds = 0.3
    /// The timeline stops this long after the last kick.
    static let settleSeconds = 1.8

    // autoPlay (seconds): open, look, pick the camera, close, rest.
    static let loop = 5.0
    static let openAt = 0.8
    static let pickAt = 2.5
    static let scriptPick = 2
    static let pressSeconds = 0.12
    /// After a touch the script waits this long, then starts again from rest.
    static let resumeAfter = 4.0
}

private enum Palette {
    static let capsule = Color(hex: 0x5b2ff5)
    static let button = Color(hex: 0x7b5bfa)
    static let field = Color(hex: 0x7453f7)
    static let placeholder = Color(hex: 0xb5a8fc)
}

/// An outline icon in an 18-point box: a rounded box, an optional round lens, and one open line.
private struct Glyph {
    let box: CGRect
    let corner: Double
    let lens: (x: Double, y: Double, r: Double)?
    let line: [CGPoint]
}

private let glyphs = [
    // Video camera: the body and the flared lens at its right.
    Glyph(
        box: CGRect(x: 2.2, y: 5, width: 9.6, height: 8), corner: 2.2, lens: nil,
        line: [CGPoint(x: 11.8, y: 8.2), CGPoint(x: 15.8, y: 5.9), CGPoint(x: 15.8, y: 12.1), CGPoint(x: 11.8, y: 9.8)]
    ),
    // Photo camera: the body, the hump of the viewfinder, the lens.
    Glyph(
        box: CGRect(x: 2.2, y: 5.4, width: 13.6, height: 9.4), corner: 2.4, lens: (9, 10.1, 2.5),
        line: [CGPoint(x: 6.4, y: 5.4), CGPoint(x: 7.4, y: 3.4), CGPoint(x: 10.6, y: 3.4), CGPoint(x: 11.6, y: 5.4)]
    ),
    // Picture: the frame, the sun, the hills.
    Glyph(
        box: CGRect(x: 2.8, y: 2.8, width: 12.4, height: 12.4), corner: 2.8, lens: (6.7, 6.7, 1.3),
        line: [CGPoint(x: 3.2, y: 12.6), CGPoint(x: 6.9, y: 9.3), CGPoint(x: 9.8, y: 11.9), CGPoint(x: 11.7, y: 10.4), CGPoint(x: 14.8, y: 13)]
    ),
]

/// A kick that swings out and dies away: 0 before it, and back to 0 after a second or so.
private func wobble(_ t: Double, _ omega: Double, _ decay: Double) -> Double {
    t <= 0 ? 0 : sin(omega * t) * exp(-decay * t)
}

#Preview {
    ZStack {
        Color(hex: 0xeeeafe).ignoresSafeArea()
        JellyAttachCapsule(autoPlay: true)
    }
}
