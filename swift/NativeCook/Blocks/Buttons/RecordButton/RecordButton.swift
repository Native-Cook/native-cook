import SwiftUI

/// Round glass microphone button. A tap opens it into a recording pill with a pulsing red dot and
/// a running timer; a second tap flashes it green and closes it again.
struct RecordButton: View {
    /// Controlled state. Leave `nil` to let the button start and stop by itself on tap.
    var recording: Binding<Bool>? = nil
    /// Records for a few seconds, stops, rests, and starts over.
    var autoPlay = false
    var minutesLabel = "MIN."
    var secondsLabel = "SEC."
    var accessibilityLabel = "Record"
    var onRecordingChange: (Bool) -> Void = { _ in }
    var action: () -> Void = {}

    @State private var phase = Phase.idle
    @State private var seconds = 0

    var body: some View {
        Button(action: toggle) { Text(accessibilityLabel) }
            .buttonStyle(
                Style(phase: phase, seconds: seconds, minutesLabel: minutesLabel, secondsLabel: secondsLabel)
            )
            .accessibilityLabel(accessibilityLabel)
            .accessibilityValue(phase == .idle ? "" : String(format: "%02d:%02d", seconds / 60, seconds % 60))
            .accessibilityAddTraits(phase == .recording ? .isSelected : [])
            .onChange(of: recording?.wrappedValue, initial: true) { _, value in
                if value == true, phase != .recording {
                    seconds = 0
                    phase = .recording
                } else if value == false, phase == .recording {
                    phase = .done
                }
            }
            .task(id: phase) {
                switch phase {
                case .idle:
                    break
                case .recording:
                    while true {
                        try? await Task.sleep(for: .seconds(1))
                        if Task.isCancelled { return }
                        seconds += 1
                    }
                case .done:
                    try? await Task.sleep(for: .seconds(Metrics.doneSeconds))
                    if !Task.isCancelled { phase = .idle }
                }
            }
            .task(id: [autoPlay ? 1 : 0, phase.rawValue]) {
                guard autoPlay, phase != .done else { return }
                let wait = phase == .recording ? Metrics.autoPlayRecord : Metrics.autoPlayRest
                try? await Task.sleep(for: .seconds(wait))
                guard !Task.isCancelled else { return }
                if phase == .recording {
                    phase = .done
                } else {
                    seconds = 0
                    phase = .recording
                }
            }
    }

    private func toggle() {
        action()
        guard phase != .done else { return }
        let next = phase != .recording
        onRecordingChange(next)
        if let recording {
            recording.wrappedValue = next
            return
        }
        if next { seconds = 0 }
        phase = next ? .recording : .done
    }

    private struct Style: ButtonStyle {
        let phase: Phase
        let seconds: Int
        let minutesLabel: String
        let secondsLabel: String

        func makeBody(configuration: Configuration) -> some View {
            Surface(
                phase: phase, seconds: seconds, minutesLabel: minutesLabel, secondsLabel: secondsLabel,
                isPressed: configuration.isPressed
            )
        }
    }
}

private enum Phase: Int {
    case idle, recording, done
}

private enum Metrics {
    /// Diameter of the glass sphere at rest and while recording, and the pill that opens behind it.
    static let sphere = 72.0
    static let sphereRecording = 76.0
    static let pillWidth = 208.0
    static let pillHeight = 66.0
    /// Distance from the centre to the middle of each timer column.
    static let column = 68.0
    /// The canvas overflows the sphere by this much on every side to fit the pill, the glow and the reflection.
    static let bleed = 104.0
    static let reflectionDrop = 84.0
    /// How long the green "saved" flash stays before the pill closes.
    static let doneSeconds = 0.8
    static let autoPlayRest = 1.5
    static let autoPlayRecord = 3.4
    static let pulseHz = 1.1
}

private func disc(_ c: CGPoint, _ r: Double) -> Path {
    Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
}

private func fade(_ r: Double, _ g: Double, _ b: Double, _ peak: Double, _ mid: Double) -> Gradient {
    Gradient(stops: [
        .init(color: .rgba(r, g, b, peak), location: 0),
        .init(color: .rgba(r, g, b, mid), location: 0.5),
        .init(color: .rgba(r, g, b, 0), location: 1),
    ])
}

private func glass(_ inner: UInt32, _ middle: UInt32, _ outer: UInt32, _ stop: Double) -> Gradient {
    Gradient(stops: [
        .init(color: Color(hex: inner), location: 0),
        .init(color: Color(hex: middle), location: stop),
        .init(color: Color(hex: outer), location: 1),
    ])
}

private let greyGlass = glass(0x625e5f, 0x3b3838, 0x222020, 0.6)
private let redGlass = glass(0x7f3136, 0xa8272c, 0xf42c30, 0.55)
private let greenGlass = glass(0x56663a, 0x7c9a30, 0xb9e23c, 0.55)

/// Microphone drawn around the origin: capsule body, cradle, stem and foot.
private let micBody = Path(
    roundedRect: CGRect(x: -9, y: -21.6, width: 18, height: 32.4), cornerRadius: 9, style: .circular
)
private let micLines: Path = {
    var path = Path()
    // Angles grow clockwise on screen, so 0° → 180° passes through the bottom.
    path.addArc(
        center: CGPoint(x: 0, y: 1.8), radius: 14, startAngle: .degrees(0), endAngle: .degrees(180), clockwise: false
    )
    path.move(to: CGPoint(x: 0, y: 15.8))
    path.addLine(to: CGPoint(x: 0, y: 20.7))
    path.move(to: CGPoint(x: -10, y: 20.7))
    path.addLine(to: CGPoint(x: 10, y: 20.7))
    return path
}()

private struct Surface: View {
    let phase: Phase
    let seconds: Int
    let minutesLabel: String
    let secondsLabel: String
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var epoch = Date.now
    /// 0 = sphere only, 1 = pill fully open.
    @State private var expand = Tween(0)
    @State private var red = Tween(0)
    @State private var green = Tween(0)
    /// 1 = microphone, 0 = record dot.
    @State private var mic = Tween(1)
    @State private var settling = false
    @State private var settleToken = 0

    var body: some View {
        TimelineView(.animation(paused: !((phase == .recording && !reduceMotion) || settling))) { timeline in
            let now = timeline.date
            let e = expand.value(at: now)
            let time = reduceMotion ? 0 : now.timeIntervalSince(epoch)
            let motion = Motion(
                expand: e, red: red.value(at: now), green: green.value(at: now), mic: mic.value(at: now),
                pulse: 0.5 + 0.5 * sin(tau * Metrics.pulseHz * time)
            )
            // The timer columns slide out from behind the sphere once the pill is mostly open.
            let columnOpacity = min(1, max(0, (e - 0.45) / 0.55))

            Color.clear
                .frame(width: Metrics.sphere, height: Metrics.sphere)
                .background {
                    Canvas { context, size in
                        draw(context, size: size, motion: motion)
                    }
                    .padding(-Metrics.bleed)
                    .allowsHitTesting(false)
                }
                .overlay {
                    column(String(format: "%02d", seconds / 60), minutesLabel)
                        .offset(x: -Metrics.column + 22 * (1 - e))
                        .opacity(columnOpacity)
                }
                .overlay {
                    column(String(format: "%02d", seconds % 60), secondsLabel)
                        .offset(x: Metrics.column - 22 * (1 - e))
                        .opacity(columnOpacity)
                }
        }
        .contentShape(Circle())
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        .onChange(of: phase) { _, phase in
            // These are the button's states, so they still play (as short fades) under reduced motion.
            switch phase {
            case .recording:
                expand.retarget(1, duration: 0.52, curve: Ease.out)
                red.retarget(1, duration: 0.32, curve: Ease.out)
                green.retarget(0, duration: 0.2, curve: Ease.out)
                mic.retarget(0, duration: 0.26, curve: Ease.out)
            case .done:
                red.retarget(0, duration: 0.26, curve: Ease.out)
                green.retarget(1, duration: 0.26, curve: Ease.out)
                mic.retarget(1, duration: 0.3, curve: Ease.out)
            case .idle:
                expand.retarget(0, duration: 0.48, curve: Ease.out)
                red.retarget(0, duration: 0.3, curve: Ease.out)
                green.retarget(0, duration: 0.5, curve: Ease.out)
                mic.retarget(1, duration: 0.3, curve: Ease.out)
            }
            settling = true
            settleToken += 1
        }
        .task(id: settleToken) {
            guard settleToken > 0 else { return }
            try? await Task.sleep(for: .seconds(0.6))
            if !Task.isCancelled { settling = false }
        }
    }

    private func column(_ digits: String, _ unit: String) -> some View {
        VStack(spacing: 0.3) {
            Text(digits)
                .font(.system(size: 23, weight: .thin))
                .monospacedDigit()
                .foregroundStyle(Color(hex: 0xf2f2f2))
            Text(unit)
                .font(.system(size: 8, weight: .medium))
                .kerning(0.6)
                .foregroundStyle(.white.opacity(0.78))
        }
        .fixedSize()
        .offset(y: -1.4)
        .allowsHitTesting(false)
    }

    private struct Motion {
        let expand: Double
        let red: Double
        let green: Double
        let mic: Double
        let pulse: Double
    }

    private func draw(_ context: GraphicsContext, size: CGSize, motion: Motion) {
        let c = CGPoint(x: size.width / 2, y: size.height / 2)
        let e = motion.expand
        let red = motion.red
        let green = motion.green
        let radius = (Metrics.sphere + (Metrics.sphereRecording - Metrics.sphere) * e) / 2
        let sphere = disc(c, radius)

        // Faint reflection on the floor below, tinted by whatever the sphere is doing.
        var floor = context
        floor.translateBy(x: c.x, y: c.y + Metrics.reflectionDrop)
        floor.scaleBy(x: 1 + 1.6 * e, y: 1)
        let reflection = CGSize(width: 26, height: 20)
        floor.fillEllipse(center: .zero, radii: reflection, gradient: fade(255, 255, 255, 0.1, 0.03))
        var redFloor = floor
        redFloor.opacity = red
        redFloor.fillEllipse(center: .zero, radii: reflection, gradient: fade(255, 40, 44, 0.2, 0.07))
        var greenFloor = floor
        greenFloor.opacity = green
        greenFloor.fillEllipse(center: .zero, radii: reflection, gradient: fade(170, 235, 60, 0.16, 0.05))

        // The pill grows out from behind the sphere.
        let pillWidth = Metrics.sphere + (Metrics.pillWidth - Metrics.sphere) * e
        let pillRect = CGRect(
            x: c.x - pillWidth / 2, y: c.y - Metrics.pillHeight / 2, width: pillWidth, height: Metrics.pillHeight
        )
        let pillShape = capsule(pillRect)
        let pillOpacity = min(1, 4 * e)
        var pill = context
        pill.opacity = pillOpacity
        pill.fill(pillShape, with: .color(Color(hex: 0x161415)))
        var tint = context
        tint.opacity = pillOpacity * red * e
        tint.fill(
            pillShape,
            with: .radialGradient(
                Gradient(colors: [.rgba(255, 48, 52, 0.3), .rgba(255, 48, 52, 0)]),
                center: c, startRadius: 0, endRadius: 92
            )
        )
        pill.stroke(
            pillShape,
            with: .linearGradient(
                Gradient(colors: [.white.opacity(0.14), .white.opacity(0.03)]),
                startPoint: CGPoint(x: c.x, y: pillRect.minY),
                endPoint: CGPoint(x: c.x, y: pillRect.maxY)
            ),
            lineWidth: 1
        )

        let glowShape = disc(c, radius + 4 + 3 * motion.pulse * red)
        var redGlow = context
        redGlow.opacity = red * (0.7 + 0.3 * motion.pulse)
        redGlow.addFilter(.blur(radius: 14))
        redGlow.fill(glowShape, with: .color(.rgba(255, 44, 48, 0.75)))
        var greenGlow = context
        greenGlow.opacity = green
        greenGlow.addFilter(.blur(radius: 14))
        greenGlow.fill(glowShape, with: .color(.rgba(170, 235, 60, 0.6)))

        // Glass sphere: grey at rest, with a red and a green version faded over it.
        context.fill(
            sphere,
            with: .radialGradient(
                greyGlass, center: CGPoint(x: c.x, y: c.y - 4), startRadius: 0,
                endRadius: Metrics.sphereRecording * 0.55
            )
        )
        let tintCenter = CGPoint(x: c.x, y: c.y - 6)
        let tintRadius = Metrics.sphereRecording * 0.6
        var redSphere = context
        redSphere.opacity = red
        redSphere.fill(sphere, with: .radialGradient(redGlass, center: tintCenter, startRadius: 0, endRadius: tintRadius))
        var greenSphere = context
        greenSphere.opacity = green
        greenSphere.fill(
            sphere, with: .radialGradient(greenGlass, center: tintCenter, startRadius: 0, endRadius: tintRadius)
        )
        context.stroke(
            sphere,
            with: .linearGradient(
                Gradient(colors: [.white.opacity(0.3), .white.opacity(0.04)]),
                startPoint: CGPoint(x: c.x, y: c.y - Metrics.sphere / 2),
                endPoint: CGPoint(x: c.x, y: c.y + Metrics.sphere / 2)
            ),
            lineWidth: 1
        )
        redSphere.stroke(sphere, with: .color(.rgba(255, 176, 176, 0.9)), lineWidth: 1)
        greenSphere.stroke(sphere, with: .color(.rgba(228, 255, 170, 0.9)), lineWidth: 1)

        // Record dot.
        var dot = context
        dot.opacity = 1 - motion.mic
        var dotGlow = dot
        dotGlow.addFilter(.blur(radius: 7))
        dotGlow.fill(disc(c, 15), with: .color(.rgba(255, 70, 74, 0.85)))
        dot.fill(disc(c, 8 + 1.2 * motion.pulse), with: .color(Color(hex: 0xff5357)))

        // Microphone.
        var micLayer = context
        micLayer.opacity = motion.mic
        var micGlow = micLayer
        micGlow.addFilter(.blur(radius: 10))
        micGlow.fill(disc(c, 18), with: .color(.white.opacity(0.22)))
        micLayer.translateBy(x: c.x, y: c.y)
        let scale = 0.5 + 0.5 * motion.mic
        micLayer.scaleBy(x: scale, y: scale)
        let ink = GraphicsContext.Shading.color(Color(hex: 0xf4f4f4))
        micLayer.fill(micBody, with: ink)
        micLayer.stroke(micLines, with: ink, style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
    }
}

#Preview {
    ZStack {
        Color(hex: 0x050505).ignoresSafeArea()
        RecordButton(autoPlay: true)
    }
}
