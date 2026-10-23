import SwiftUI

/// Dark assistant chat bar whose border is alive: a white hot spot with pink and violet tails keeps
/// travelling around the rounded field, the whole border flares when a prompt starts, and the light
/// runs a faster, brighter lap after it is sent. The model picker sits inside the placeholder
/// sentence, the mic gives way to the send button once there is text, and the chips fill the field.
struct GlowBorderChatbar: View {
    /// Controlled text; leave nil and the bar keeps its own.
    var text: Binding<String>? = nil
    /// Types `prompt`, sends it and starts over, without ever opening the keyboard.
    var autoPlay = false
    var greeting = "Hi Alex"
    var title = "What’s the opening move?"
    /// The grey word in front of the model picker.
    var lead = "Ask"
    var placeholder = "anything..."
    /// A tap on the picker moves to the next of these.
    var models = ["Swift", "Balanced", "Deep"]
    var chips = ["Create image", "Compose music", "Help me learn", "Boost my day"]
    /// What autoPlay types; about thirty characters fit the field.
    var prompt = "Plan a weekend in Lisbon"
    /// Colour behind the bar: the chip row fades into it at its right end.
    var backdrop = Color.black
    /// Called with the text when the send button or the keyboard's send key is pressed.
    var onSend: ((String) -> Void)? = nil
    var onModelChange: ((String) -> Void)? = nil
    var onChip: ((String) -> Void)? = nil
    var onAttach: (() -> Void)? = nil
    var onTools: (() -> Void)? = nil
    var onMic: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focused: Bool
    @State private var draft = ""
    @State private var model = 0
    @State private var glow = Glow()
    /// True while the autoPlay script owns the field.
    @State private var playing = false
    @State private var runToken = 0
    @State private var resumeToken = 0
    @State private var scriptPress = false
    /// The text that was just sent, on its way out.
    @State private var ghost = ""

    /// Moments the border reacts to. A class, so writing them never rebuilds the view: the canvas
    /// reads them every frame anyway.
    private final class Glow {
        var epoch = Date.now
        var flareAt = Date.distantPast
        var boostAt = Date.distantPast
        var keyAt = Date.distantPast
        var sentAt = Date.distantPast
    }

    private var controlled: Bool { text != nil }
    private var auto: Bool { autoPlay && !controlled && !reduceMotion }
    private var current: String { text?.wrappedValue ?? draft }
    private var hasText: Bool { !current.isEmpty }
    private var modelName: String { models.isEmpty ? "" : models[model % models.count] }

    private var field: Binding<String> {
        Binding(get: { current }, set: { write($0) })
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                mark
                Text(greeting)
                    .font(.system(size: 17))
                    .foregroundStyle(.white)
            }
            .frame(height: 24)
            .padding(.leading, Metrics.inset)

            Text(title)
                .font(.system(size: 24, weight: .medium))
                .tracking(-0.3)
                .foregroundStyle(.white)
                .frame(height: 30)
                .padding(.leading, Metrics.inset)
                .padding(.top, 6)

            bar.padding(.top, 14)
            chipRow.padding(.top, 12)
        }
        .lineLimit(1)
        .frame(width: Metrics.width)
        .onChange(of: hasText) { _, now in
            // The border flares whenever a prompt begins, whoever types it.
            if now { glow.flareAt = .now }
        }
        .onChange(of: focused) { _, now in
            // A tap on the field takes it over from the script and starts from empty.
            if now && playing { draft = "" }
            touch()
        }
        .onChange(of: auto, initial: true) { _, now in
            playing = now
            runToken += 1
        }
        .task(id: runToken) { await play() }
        .task(id: resumeToken) { await resume() }
    }

    // MARK: The field

    private var bar: some View {
        ZStack(alignment: .topLeading) {
            TimelineView(.animation) { timeline in
                Canvas { context, _ in drawBorder(context, at: timeline.date) }
            }
            .padding(-Metrics.bleed)
            .allowsHitTesting(false)

            HStack(spacing: 0) {
                Text(lead)
                    .font(.system(size: 15))
                    .foregroundStyle(Palette.lead)
                    .padding(.trailing, 6)
                picker.padding(.trailing, 8)
                input
            }
            .frame(height: 24)
            .padding(.horizontal, 16)
            .offset(y: 14)

            HStack(spacing: 2) {
                iconButton("Attach", action: onAttach) { plusIcon }
                iconButton("Tools", action: onTools) { slidersIcon }
            }
            .offset(x: 9, y: Metrics.height - 10 - 33)

            micAndSend
        }
        .frame(width: Metrics.width, height: Metrics.height)
    }

    private var picker: some View {
        Button {
            guard !models.isEmpty else { return }
            touch()
            model = (model + 1) % models.count
            onModelChange?(models[model])
        } label: {
            HStack(spacing: 3) {
                Text(modelName)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white)
                    .fixedSize()
                    .id(model)
                    // The next name rolls in from below as the old one leaves upwards.
                    .transition(reduceMotion ? .opacity : .push(from: .bottom))
                chevronIcon
            }
            .frame(height: 24)
            .clipped()
            .contentShape(Rectangle())
        }
        .buttonStyle(Press(scale: 0.96))
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.18), value: model)
        .accessibilityLabel("Model: \(modelName)")
    }

    private var input: some View {
        ZStack(alignment: .leading) {
            Text(placeholder)
                .foregroundStyle(Palette.placeholder)
                .opacity(hasText ? 0 : 1)
                // It makes way at once, and comes back after the sent text has lifted off.
                .animation(hasText ? nil : .easeOut(duration: 0.18).delay(0.15), value: hasText)

            TextField("", text: field)
                .focused($focused)
                .foregroundStyle(.white)
                .tint(Palette.lilac)
                .submitLabel(.send)
                .onSubmit { send() }
                .accessibilityLabel("\(lead) \(modelName) \(placeholder)")

            TimelineView(.animation) { timeline in
                let sent = min(max(timeline.date.timeIntervalSince(glow.sentAt) / Metrics.ghostSeconds, 0), 1)
                let out = 1 - pow(1 - sent, 3)
                ZStack(alignment: .leading) {
                    // The sent text lifts off the line and fades.
                    Text(ghost)
                        .foregroundStyle(.white)
                        .offset(y: reduceMotion ? 0 : -10 * out)
                        .opacity(1 - out)
                    if playing && hasText {
                        // The script does not focus the field, so it draws the caret itself: an
                        // invisible copy of the text pushes it to the end of the line.
                        HStack(spacing: 1) {
                            Text(current).hidden()
                            Capsule()
                                .fill(Palette.lilac)
                                .frame(width: 2, height: 18)
                                .opacity(caretOpacity(timeline.date.timeIntervalSince(glow.keyAt)))
                        }
                    }
                }
            }
            .allowsHitTesting(false)
        }
        .font(.system(size: 15))
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(height: 24)
        .clipped()
    }

    /// The mic steps aside as soon as there is something to send, and the send button lights up.
    private var micAndSend: some View {
        let armed = hasText
        let spring = reduceMotion
            ? Animation.easeOut(duration: 0.15)
            : .interpolatingSpring(mass: Metrics.popMass, stiffness: Metrics.popStiffness, damping: Metrics.popDamping)
        return ZStack(alignment: .topLeading) {
            iconButton("Dictate", action: onMic) { micIcon }
                .scaleEffect(armed ? 0.6 : 1)
                .opacity(armed ? 0 : 1)
                .offset(x: armed && !reduceMotion ? 12 : 0)
                .allowsHitTesting(!armed)
                .offset(x: Metrics.width - 10 - Metrics.send - 6 - 32, y: Metrics.height - 10 - 33)

            Button {
                send()
            } label: {
                ZStack {
                    Circle().fill(Palette.sendIdle)
                    arrowIcon(Palette.sendIdleInk)
                    ZStack {
                        Circle().fill(.white)
                        arrowIcon(.black)
                    }
                    .scaleEffect(armed ? 1 : 0.6)
                    .opacity(armed ? 1 : 0)
                }
                .frame(width: Metrics.send, height: Metrics.send)
                .contentShape(Circle())
            }
            .buttonStyle(Press(scale: 0.9))
            .scaleEffect(scriptPress ? 0.9 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: scriptPress)
            .accessibilityLabel("Send")
            .offset(x: Metrics.width - 10 - Metrics.send, y: Metrics.height - 10 - Metrics.send)
        }
        .animation(spring, value: armed)
    }

    private var chipRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(chips, id: \.self) { chip in
                    Button {
                        touch()
                        write(chip)
                        onChip?(chip)
                    } label: {
                        Text(chip)
                            .font(.system(size: 14))
                            .foregroundStyle(Palette.chipInk)
                            .padding(.horizontal, 14)
                            .frame(height: 36)
                            .background(Capsule().fill(Palette.chip))
                    }
                    .buttonStyle(Press(scale: 0.96))
                }
            }
            // Room for the last chip to clear the fade once the row is scrolled to its end.
            .padding(.trailing, Metrics.fade)
        }
        .frame(width: Metrics.width, height: 36)
        .overlay(alignment: .trailing) {
            LinearGradient(colors: [backdrop.opacity(0), backdrop], startPoint: .leading, endPoint: .trailing)
                .frame(width: Metrics.fade)
                .allowsHitTesting(false)
        }
    }

    private func iconButton(_ label: String, action: (() -> Void)?, @ViewBuilder icon: () -> some View) -> some View {
        Button {
            action?()
        } label: {
            icon()
                .frame(width: 32, height: 32)
                .contentShape(Rectangle())
        }
        .buttonStyle(Press(scale: 0.9))
        .accessibilityLabel(label)
    }

    // MARK: Behaviour

    private func write(_ value: String) {
        if let text { text.wrappedValue = value } else { draft = value }
        glow.keyAt = .now
    }

    /// Any touch stops the script; it comes back a few seconds after the last one.
    private func touch() {
        playing = false
        scriptPress = false
        runToken += 1
        resumeToken += 1
    }

    private func send() {
        let sent = current
        guard !sent.isEmpty else { return }
        touch()
        submit()
        focused = false
        onSend?(sent)
    }

    /// Clears the field with the sent text lifting away, and sends the light on its fast lap.
    private func submit() {
        ghost = current
        glow.sentAt = .now
        glow.boostAt = .now
        write("")
    }

    private func play() async {
        guard playing else { return }
        let clock = ContinuousClock()
        var start = clock.now
        let keys = keyTimes(prompt)
        while true {
            for (index, at) in keys.enumerated() {
                guard await wait(until: start + .seconds(at), on: clock) else { return }
                draft = String(prompt.prefix(index + 1))
                glow.keyAt = .now
            }
            guard await wait(until: start + .seconds(Metrics.sendAt), on: clock) else { return }
            scriptPress = true
            guard await wait(until: start + .seconds(Metrics.sendAt + Metrics.pressSeconds), on: clock) else { return }
            scriptPress = false
            submit()
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
        draft = ""
        playing = true
        runToken += 1
    }

    // MARK: Drawing

    private func drawBorder(_ context: GraphicsContext, at date: Date) {
        let rect = CGRect(x: Metrics.bleed, y: Metrics.bleed, width: Metrics.width, height: Metrics.height)
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let edge = Path(roundedRect: rect, cornerRadius: Metrics.radius, style: .circular)
        let rim = Path(roundedRect: rect.insetBy(dx: 1, dy: 1), cornerRadius: Metrics.radius - 1, style: .circular)
        let fill = GraphicsContext.Shading.linearGradient(
            Gradient(colors: [Palette.fieldTop, Palette.fieldBottom]),
            startPoint: CGPoint(x: rect.midX, y: rect.minY), endPoint: CGPoint(x: rect.midX, y: rect.maxY)
        )

        let flare = envelope(date.timeIntervalSince(glow.flareAt), rise: 0.16, hold: 0.45, fall: 0.8)
        let sinceBoost = date.timeIntervalSince(glow.boostAt)
        let boost = envelope(sinceBoost, rise: 0.25, hold: 0.9, fall: 0.45)
        // Whole laps bring the hot spot back to the middle of the top edge.
        let laps = reduceMotion
            ? Metrics.stillLap
            : date.timeIntervalSince(glow.epoch) / Metrics.lap + Metrics.boostLaps * smooth(sinceBoost / Metrics.boost)
        let light = GraphicsContext.Shading.conicGradient(
            lightGradient, center: center, angle: Angle(radians: angleAt(laps) - .pi)
        )
        // The gradient is drawn in a space stretched sideways, so its streaks keep a similar length
        // on the long and the short edges; the outline is squeezed first and lands where it was.
        let squeeze = CGAffineTransform(translationX: center.x, y: 0)
            .scaledBy(x: 1 / Metrics.stretch, y: 1)
            .translatedBy(x: -center.x, y: 0)
        func lit(_ layer: GraphicsContext, _ path: Path, width: Double) {
            var stretched = layer
            stretched.translateBy(x: center.x, y: 0)
            stretched.scaleBy(x: Metrics.stretch, y: 1)
            stretched.translateBy(x: -center.x, y: 0)
            stretched.fill(path.strokedPath(StrokeStyle(lineWidth: width)).applying(squeeze), with: light)
        }

        context.fill(edge, with: fill)

        // The blurs are filters (applied after shading), so the bloom follows the border instead
        // of smearing into an angular wedge.
        var bloom = context
        bloom.opacity = 0.85
        bloom.addFilter(.blur(radius: 7))
        bloom.drawLayer { lit($0, edge, width: 5) }

        if boost > 0 {
            var wide = context
            wide.opacity = boost
            wide.addFilter(.blur(radius: 14))
            wide.drawLayer { lit($0, edge, width: 9) }
        }
        if flare > 0 {
            var halo = context
            halo.opacity = flare * 0.8
            halo.addFilter(.blur(radius: 16))
            halo.stroke(edge, with: .color(Palette.violet), lineWidth: 10)
            var soft = context
            soft.opacity = flare
            soft.addFilter(.blur(radius: 6))
            soft.stroke(edge, with: .color(Palette.lilac), lineWidth: 5)
        }

        // A veil of the field's own colour keeps the bloom that spills inwards quieter than the
        // one outside.
        var veil = context
        veil.opacity = Metrics.veil
        veil.fill(edge, with: fill)

        context.stroke(
            Path(roundedRect: rect.insetBy(dx: 0.75, dy: 0.75), cornerRadius: Metrics.radius - 0.75, style: .circular),
            with: .color(Palette.rim), lineWidth: 1.5
        )
        lit(context, rim, width: 2)
        if flare > 0 {
            context.stroke(rim, with: .color(.white.opacity(flare)), lineWidth: 2)
        }
    }

    private var mark: some View {
        Path { p in
            p.move(to: CGPoint(x: 10, y: 0))
            p.addCurve(to: CGPoint(x: 20, y: 10), control1: CGPoint(x: 10.9, y: 6), control2: CGPoint(x: 14, y: 9.1))
            p.addCurve(to: CGPoint(x: 10, y: 20), control1: CGPoint(x: 14, y: 10.9), control2: CGPoint(x: 10.9, y: 14))
            p.addCurve(to: CGPoint(x: 0, y: 10), control1: CGPoint(x: 9.1, y: 14), control2: CGPoint(x: 6, y: 10.9))
            p.addCurve(to: CGPoint(x: 10, y: 0), control1: CGPoint(x: 6, y: 9.1), control2: CGPoint(x: 9.1, y: 6))
            p.closeSubpath()
        }
        .fill(LinearGradient(colors: [Palette.lilac, Palette.violet], startPoint: .topLeading, endPoint: .bottomTrailing))
        .frame(width: 20, height: 20)
        .accessibilityHidden(true)
    }

    private var plusIcon: some View {
        Path { p in
            p.move(to: CGPoint(x: 9, y: 3.5))
            p.addLine(to: CGPoint(x: 9, y: 14.5))
            p.move(to: CGPoint(x: 3.5, y: 9))
            p.addLine(to: CGPoint(x: 14.5, y: 9))
        }
        .stroke(.white, style: StrokeStyle(lineWidth: 1.7, lineCap: .round))
        .frame(width: 18, height: 18)
    }

    private var slidersIcon: some View {
        Path { p in
            p.move(to: CGPoint(x: 3, y: 6))
            p.addLine(to: CGPoint(x: 8.2, y: 6))
            p.addEllipse(in: CGRect(x: 8.6, y: 3.9, width: 4.2, height: 4.2))
            p.move(to: CGPoint(x: 13.2, y: 6))
            p.addLine(to: CGPoint(x: 15, y: 6))
            p.move(to: CGPoint(x: 3, y: 12))
            p.addLine(to: CGPoint(x: 4.8, y: 12))
            p.addEllipse(in: CGRect(x: 5.2, y: 9.9, width: 4.2, height: 4.2))
            p.move(to: CGPoint(x: 9.8, y: 12))
            p.addLine(to: CGPoint(x: 15, y: 12))
        }
        .stroke(.white, style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
        .frame(width: 18, height: 18)
    }

    private var micIcon: some View {
        ZStack {
            Path(roundedRect: CGRect(x: 6.4, y: 2, width: 5.2, height: 8.6), cornerRadius: 2.6, style: .circular)
                .fill(.white)
            Path { p in
                p.addArc(center: CGPoint(x: 9, y: 8.6), radius: 4.8, startAngle: .zero, endAngle: .degrees(180), clockwise: false)
                p.move(to: CGPoint(x: 9, y: 13.4))
                p.addLine(to: CGPoint(x: 9, y: 16))
            }
            .stroke(.white, style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
        }
        .frame(width: 18, height: 18)
    }

    private var chevronIcon: some View {
        Path { p in
            p.move(to: CGPoint(x: 2, y: 3.5))
            p.addLine(to: CGPoint(x: 5, y: 6.5))
            p.addLine(to: CGPoint(x: 8, y: 3.5))
        }
        .stroke(.white, style: StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round))
        .frame(width: 10, height: 10)
    }

    private func arrowIcon(_ color: Color) -> some View {
        Path { p in
            p.move(to: CGPoint(x: 9, y: 15.5))
            p.addLine(to: CGPoint(x: 9, y: 2.5))
            p.move(to: CGPoint(x: 3.8, y: 7.7))
            p.addLine(to: CGPoint(x: 9, y: 2.5))
            p.addLine(to: CGPoint(x: 14.2, y: 7.7))
        }
        .stroke(color, style: StrokeStyle(lineWidth: 2.1, lineCap: .round, lineJoin: .round))
        .frame(width: 18, height: 18)
    }

    private struct Press: ButtonStyle {
        let scale: Double

        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? scale : 1)
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
                .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
        }
    }
}

private enum Metrics {
    static let width = 350.0
    static let height = 92.0
    static let radius = 22.0
    /// The canvas overflows the field by this much on every side to fit the bloom.
    static let bleed = 44.0
    /// The greeting lines up with the field's text, not with its border.
    static let inset = 16.0
    static let send = 34.0
    /// Width of the fade at the right end of the chip row.
    static let fade = 40.0

    /// Seconds the light takes to go around once at rest.
    static let lap = 3.5
    /// Extra laps a sent prompt adds, and the seconds they take.
    static let boostLaps = 1.0
    static let boost = 1.6
    /// Where the hot spot stays under reduced motion, in laps from the middle of the top edge.
    static let stillLap = 0.07
    /// Sideways stretch of the light's gradient: the square root of the field's proportions.
    static let stretch = (width / height).squareRoot()
    /// How much of the bloom inside the field is covered again.
    static let veil = 0.5

    // autoPlay (seconds): the prompt is typed, sent, and the bar rests until the loop closes.
    // The loop is a whole number of laps, so the light is back where it started.
    static let loop = 7.0
    static let typeAt = 0.8
    /// The typing never takes longer than this, however long the prompt.
    static let typeSpan = 2.0
    static let sendAt = 3.25
    static let pressSeconds = 0.13
    /// After a touch the script waits this long, then starts again from rest.
    static let resumeAfter = 4.0

    static let ghostSeconds = 0.28

    // The send button lights up with a little overshoot.
    static let popMass = 1.0
    static let popStiffness = 320.0
    static let popDamping = 18.0
}

private enum Palette {
    static let fieldTop = Color(hex: 0x242424)
    static let fieldBottom = Color(hex: 0x1e1e1e)
    static let rim = Color(hex: 0x3d3547)
    static let lead = Color(hex: 0x8a8a8a)
    static let placeholder = Color(hex: 0x6e6e6e)
    static let chip = Color(hex: 0x1c1c1c)
    static let chipInk = Color(hex: 0x7a7a7a)
    static let sendIdle = Color.white.opacity(0.14)
    static let sendIdleInk = Color.white.opacity(0.55)
    static let lilac = Color(hex: 0xd9a8ff)
    static let pink = Color(hex: 0xff7ad9)
    static let violet = Color(hex: 0xa855f7)
    static let clear = Color(hex: 0xa855f7, opacity: 0)
}

/// Seconds between two letters of the typed prompt: uneven, and the same every loop.
private let typeGaps = [0.07, 0.055, 0.09, 0.06, 0.075, 0.05, 0.11, 0.065]

// Conic stops run clockwise from 3 o'clock. The hot spot sits at 0.5 with a short lilac nose ahead
// of it and a long pink and violet tail behind; a dimmer pink streak runs on the opposite side.
private let lightGradient = Gradient(stops: [
    .init(color: Palette.pink, location: 0),
    .init(color: Palette.violet.opacity(0.55), location: 0.025),
    .init(color: Palette.clear, location: 0.06),
    .init(color: Palette.clear, location: 0.33),
    .init(color: Palette.violet.opacity(0.7), location: 0.37),
    .init(color: Palette.pink, location: 0.43),
    .init(color: .white, location: 0.475),
    .init(color: .white, location: 0.505),
    .init(color: Palette.lilac, location: 0.525),
    .init(color: Palette.clear, location: 0.56),
    .init(color: Palette.clear, location: 0.88),
    .init(color: Palette.violet.opacity(0.5), location: 0.93),
    .init(color: Palette.pink, location: 1),
])

private func smooth(_ t: Double) -> Double {
    let u = min(max(t, 0), 1)
    return u * u * (3 - 2 * u)
}

/// 0…1 over time: up in `rise` seconds, held, then down in `fall`.
private func envelope(_ t: Double, rise: Double, hold: Double, fall: Double) -> Double {
    if t <= 0 { return 0 }
    if t < rise { return smooth(t / rise) }
    return 1 - smooth((t - rise - hold) / fall)
}

/// Direction, seen from the centre of the field, of the point `laps` of the way around its
/// outline (clockwise from the middle of the top edge). Turning the gradient by this angle
/// instead of a steady one makes the light travel at a constant speed along the border.
private func angleAt(_ laps: Double) -> Double {
    let halfW = Metrics.width / 2
    let halfH = Metrics.height / 2
    var d = (laps - laps.rounded(.down)) * 2 * (Metrics.width + Metrics.height)
    var x = -halfW
    var y = -halfH
    if d < halfW {
        x = d
    } else if d < halfW + Metrics.height {
        x = halfW
        y = d - halfW - halfH
    } else if d < halfW + Metrics.height + Metrics.width {
        x = halfW - (d - halfW - Metrics.height)
        y = halfH
    } else if d < halfW + 2 * Metrics.height + Metrics.width {
        y = halfH - (d - halfW - Metrics.height - Metrics.width)
    } else {
        x = d - 2 * Metrics.height - Metrics.width - 3 * halfW
    }
    // Measured in the gradient's stretched space.
    return atan2(y, x / Metrics.stretch)
}

/// When each letter of the prompt appears, in seconds from the start of the loop.
private func keyTimes(_ prompt: String) -> [Double] {
    var at: [Double] = []
    var t = 0.0
    for index in 0..<prompt.count {
        at.append(t)
        t += typeGaps[index % typeGaps.count]
    }
    let scale = min(1, Metrics.typeSpan / max(t, 0.001))
    return at.map { Metrics.typeAt + $0 * scale }
}

/// The drawn caret stays lit for a moment after a letter, then blinks once a second.
private func caretOpacity(_ sinceKey: Double) -> Double {
    sinceKey.truncatingRemainder(dividingBy: 1) < 0.55 ? 1 : 0
}

#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        GlowBorderChatbar(autoPlay: true)
    }
}
