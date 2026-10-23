import SwiftUI

enum ModeSwitcherInputIcon {
    case bolt, scales, bulb, flask
}

struct ModeSwitcherInputMode: Equatable {
    /// The name in the menu, and the shorter one on the pill.
    var label: String
    var short: String
    var icon: ModeSwitcherInputIcon
    /// The colours this mode gives the glows and the border: left end and right end.
    var warm: UInt32
    var cool: UInt32
}

/// Dark prompt composer lit by two slow glows, with a hairline border that runs from one glow's
/// colour to the other's. Its mode pill opens a menu; choosing a mode rolls the pill's icon and
/// label and shifts glows and border to that mode's colours, and sending runs a light once around
/// the border.
struct ModeSwitcherInput: View {
    /// The field's text when the parent owns it. Leave nil and the composer keeps its own.
    var text: Binding<String>? = nil
    /// Changes mode, types `script` and sends it by itself in a loop, without the keyboard, until
    /// the composer is touched.
    var autoPlay = false
    var placeholder = "Ask anything..."
    /// What autoPlay types.
    var script = "Plan three days in Lisbon"
    var modes = [
        ModeSwitcherInputMode(label: "Quick answer", short: "Quick", icon: .bolt, warm: 0xffb648, cool: 0xff5c8a),
        ModeSwitcherInputMode(label: "Balanced", short: "Balanced", icon: .scales, warm: 0xff7a59, cool: 0x4d6bff),
        ModeSwitcherInputMode(label: "Deep think", short: "Deep think", icon: .bulb, warm: 0x9b6bff, cool: 0x2fd3ff),
        ModeSwitcherInputMode(label: "Research", short: "Research", icon: .flask, warm: 0x54e08a, cool: 0x19c7c0),
    ]
    /// The mode at rest. autoPlay goes from it to the next one and back.
    var initialMode = 1
    /// The tone pill steps through these.
    var tones = ["Normal", "Concise", "Formal", "Playful"]
    var onChangeText: ((String) -> Void)? = nil
    /// Called with the trimmed text and the index of the mode.
    var onSend: ((String, Int) -> Void)? = nil
    var onModeChange: ((Int) -> Void)? = nil
    var onToneChange: ((Int) -> Void)? = nil
    var onAdd: (() -> Void)? = nil
    var onVoice: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focused: Bool
    @State private var draft = ""
    /// The script is running: the field is not focused and the composer draws its own caret.
    @State private var playing = false
    /// The script's stand-in for focus.
    @State private var engaged = false
    /// When the last key went down: the caret's blink starts over from there.
    @State private var keyAt = Date.now
    @State private var mode: Int? = nil
    @State private var tone = 0
    @State private var menuOpen = false
    /// The script's finger.
    @State private var heldPill = false
    @State private var heldRow: Int? = nil
    @State private var heldSend = false
    /// The text on its way out of the field.
    @State private var ghost = ""
    @State private var ghostGone = true
    /// The clock of the glows, the colours they are leaving, and how far the change has come.
    @State private var bornAt = Date.now
    @State private var leaving: (warm: ModeSwitcherInputTint, cool: ModeSwitcherInputTint)? = nil
    @State private var shift = Tween(1)
    /// The light that runs around the border after a send: 0 at the start of its lap, 1 at the end.
    @State private var lap = Tween(1)
    @State private var taps = 0
    @State private var steps = 0
    @State private var resumes = 0

    private var controlled: Bool { text != nil }
    private var auto: Bool { autoPlay && !controlled && !reduceMotion && modes.count > 1 }
    private var value: String { text?.wrappedValue ?? draft }
    private var current: Int { min(max(mode ?? initialMode, 0), modes.count - 1) }

    var body: some View {
        ZStack(alignment: .topLeading) {
            TimelineView(.animation(paused: reduceMotion)) { timeline in
                Canvas { context, _ in
                    drawAura(context, date: timeline.date)
                }
            }
            .padding(-Metrics.bleed)
            .allowsHitTesting(false)

            field
                .padding(.horizontal, 16)
                .padding(.top, 13)

            toolbar
                .padding(10)
                .frame(maxHeight: .infinity, alignment: .bottom)
        }
        .frame(width: Metrics.width, height: Metrics.height)
        .sensoryFeedback(.impact(weight: .light), trigger: taps)
        .sensoryFeedback(.selection, trigger: steps)
        .onAppear {
            bornAt = .now
            playing = auto
        }
        .onChange(of: auto) { playing = auto }
        .onChange(of: focused) { _, now in
            if now {
                // A touch on the field ends the script: the field is emptied and the keyboard takes over.
                if playing { draft = "" }
                interrupt()
                menuOpen = false
            } else {
                resumes += 1
            }
        }
        .task(id: playing) {
            guard playing else { return }
            await play()
        }
        .task(id: resumes) {
            // The loop starts over from rest a few seconds after the last touch.
            guard resumes > 0, auto, !playing else { return }
            try? await Task.sleep(for: .seconds(Metrics.resume))
            guard !Task.isCancelled, !focused, value.isEmpty else { return }
            menuOpen = false
            choose(initialMode, byUser: false)
            try? await Task.sleep(for: .seconds(0.9))
            guard !Task.isCancelled, !focused, value.isEmpty else { return }
            playing = true
        }
    }

    // MARK: Field

    private var field: some View {
        ZStack(alignment: .leading) {
            Text(placeholder)
                .foregroundStyle(Color(hex: 0x8a8f9c))
                .lineLimit(1)
                .opacity(value.isEmpty && ghostGone ? 1 : 0)
                // It waits for the sent text to clear the field before it returns.
                .animation(value.isEmpty && ghostGone ? .easeOut(duration: 0.15).delay(Metrics.placeholderDelay) : nil, value: value.isEmpty && ghostGone)
                .allowsHitTesting(false)

            TextField("", text: Binding(get: { value }, set: { write($0) }))
                .focused($focused)
                // While the script types, the letters are drawn by the row below, with its caret.
                .foregroundStyle(playing ? .clear : Metrics.ink)
                .tint(Metrics.ink)
                .submitLabel(.send)
                .onSubmit(submit)
                .accessibilityLabel(placeholder)

            if playing, engaged {
                HStack(spacing: 1) {
                    Text(mirror).foregroundStyle(Metrics.ink).lineLimit(1)
                    TimelineView(.periodic(from: keyAt, by: Metrics.blink)) { timeline in
                        let beat = Int((timeline.date.timeIntervalSince(keyAt) + 0.05) / Metrics.blink)
                        RoundedRectangle(cornerRadius: 1, style: .circular)
                            .fill(Metrics.ink)
                            .frame(width: 2, height: 18)
                            .opacity(beat % 2 == 0 ? 1 : 0)
                    }
                }
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            }

            Text(ghost)
                .foregroundStyle(Metrics.ink)
                .lineLimit(1)
                .opacity(ghostGone ? 0 : 1)
                .offset(y: ghostGone && !reduceMotion ? -12 : 0)
                .animation(.easeOut(duration: 0.25), value: ghostGone)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
        .font(.system(size: 15))
        .frame(height: 22)
    }

    /// The typed text as the mirror row draws it: a trailing space must keep its width.
    private var mirror: String {
        value.hasSuffix(" ") ? value.dropLast() + "\u{00a0}" : value
    }

    // MARK: Toolbar

    private var toolbar: some View {
        HStack(spacing: 6) {
            Button {
                interrupt()
                onAdd?()
            } label: {
                ModeSwitcherInputGlyph(d: Glyph.plus)
                    .frame(width: Metrics.control, height: Metrics.control)
                    .background(Circle().fill(Metrics.chip))
                    .overlay(Circle().strokeBorder(Metrics.edge, lineWidth: 1))
            }
            .buttonStyle(ModeSwitcherInputPress(held: false))
            .accessibilityLabel("Add")

            Button {
                interrupt()
                guard !tones.isEmpty else { return }
                tone = (tone + 1) % tones.count
                steps += 1
                onToneChange?(tone)
            } label: {
                pill {
                    ModeSwitcherInputGlyph(d: Glyph.feather, size: 14)
                    ModeSwitcherInputRoll(labels: tones, index: tone, still: reduceMotion)
                    ModeSwitcherInputGlyph(d: Glyph.chevron, size: 10)
                }
            }
            .buttonStyle(ModeSwitcherInputPress(held: false))
            .accessibilityLabel("Tone")
            .accessibilityValue(tones.indices.contains(tone) ? tones[tone] : "")

            Button {
                interrupt()
                menuOpen.toggle()
                taps += 1
            } label: {
                pill {
                    // The icon rolls with the label, one mode above the other.
                    VStack(spacing: 0) {
                        ForEach(Array(modes.enumerated()), id: \.offset) { _, item in
                            ModeSwitcherInputGlyph(d: Glyph.mode(item.icon), size: 16)
                                .frame(width: 16, height: Metrics.rollHeight)
                        }
                    }
                    .offset(y: -Double(current) * Metrics.rollHeight)
                    .frame(width: 16, height: Metrics.rollHeight, alignment: .top)
                    .clipped()
                    .animation(reduceMotion ? .easeOut(duration: 0.2) : Metrics.rollSpring, value: current)
                    ModeSwitcherInputRoll(labels: modes.map(\.short), index: current, still: reduceMotion)
                    ModeSwitcherInputGlyph(d: Glyph.chevron, size: 10)
                        .rotationEffect(.degrees(menuOpen ? 180 : 0))
                        .animation(reduceMotion ? nil : (menuOpen ? Metrics.menuSpring : .easeIn(duration: 0.14)), value: menuOpen)
                }
            }
            .buttonStyle(ModeSwitcherInputPress(held: heldPill))
            .accessibilityLabel("Mode")
            .accessibilityValue(modes[current].label)
            // The menu hangs under the pill, over the composer's bottom edge.
            .overlay(alignment: .topLeading) {
                menu
                    .frame(width: Metrics.menuWidth)
                    .fixedSize()
                    .offset(y: Metrics.control + 5)
            }

            Spacer(minLength: 0)

            Button {
                interrupt()
                onVoice?()
            } label: {
                ModeSwitcherInputGlyph(d: Glyph.wave)
                    .frame(width: 36, height: Metrics.pill)
                    .background(Capsule().fill(Metrics.chip))
                    .overlay(Capsule().strokeBorder(Metrics.edge, lineWidth: 1))
            }
            .buttonStyle(ModeSwitcherInputPress(held: false))
            .accessibilityLabel("Voice")

            Button(action: submit) {
                modeSwitcherInputPath(Glyph.plane)
                    .fill(.white)
                    .frame(width: 16, height: 16)
                    .frame(width: Metrics.control, height: Metrics.control)
                    .background(
                        Circle().fill(
                            LinearGradient(colors: [Color(hex: 0x7b4dff), Color(hex: 0xff7a59)], startPoint: .topLeading, endPoint: .bottomTrailing)
                        )
                    )
            }
            .buttonStyle(ModeSwitcherInputPress(held: heldSend))
            .accessibilityLabel("Send")
        }
        .foregroundStyle(Metrics.label)
        .frame(height: Metrics.control)
    }

    private func pill<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        HStack(spacing: 4, content: content)
            .padding(.leading, 8)
            .padding(.trailing, 7)
            .frame(height: Metrics.pill)
            .background(Capsule().fill(Metrics.chip))
            .overlay(Capsule().strokeBorder(Metrics.edge, lineWidth: 1))
            .contentShape(Capsule())
    }

    private var menu: some View {
        VStack(spacing: 0) {
            ForEach(Array(modes.enumerated()), id: \.offset) { slot, item in
                Button {
                    interrupt()
                    taps += 1
                    choose(slot, byUser: true)
                    menuOpen = false
                } label: {
                    HStack(spacing: 8) {
                        ModeSwitcherInputGlyph(d: Glyph.mode(item.icon), size: 16)
                        Text(item.label)
                            .font(.system(size: 14))
                            .lineLimit(1)
                        Spacer(minLength: 0)
                        // The mode in use is ticked.
                        ModeSwitcherInputGlyph(d: Glyph.check, size: 16)
                            .opacity(slot == current ? 1 : 0)
                    }
                    .foregroundStyle(slot == current || slot == heldRow ? .white : Color(hex: 0xaeb3bf))
                    .padding(.horizontal, 8)
                    .frame(height: Metrics.rowHeight)
                    .contentShape(Rectangle())
                }
                .buttonStyle(ModeSwitcherInputRow(held: heldRow == slot, current: slot == current && heldRow == nil))
                // The rows follow the panel in, one after another.
                .opacity(menuOpen ? 1 : 0)
                .offset(y: menuOpen || reduceMotion ? 0 : 6)
                .animation(
                    menuOpen ? .easeOut(duration: 0.2).delay(0.05 + Double(slot) * 0.04) : .easeOut(duration: 0.1),
                    value: menuOpen
                )
            }
        }
        .padding(6)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .circular)
                .fill(Color(hex: 0x14161d))
                .shadow(color: .black.opacity(0.5), radius: 18, y: 10)
        )
        .overlay(RoundedRectangle(cornerRadius: 12, style: .circular).strokeBorder(Metrics.edge, lineWidth: 1))
        // It grows out of the pill's corner.
        .scaleEffect(menuOpen || reduceMotion ? 1 : 0.86, anchor: .topLeading)
        .offset(y: menuOpen || reduceMotion ? 0 : -6)
        .opacity(menuOpen ? 1 : 0)
        .animation(
            reduceMotion ? .easeOut(duration: 0.2) : (menuOpen ? Metrics.menuSpring : .easeIn(duration: 0.14)),
            value: menuOpen
        )
        .allowsHitTesting(menuOpen)
        .accessibilityHidden(!menuOpen)
    }

    // MARK: Aura

    /// The colours of the glows at `date`: those of the mode in use, or on the way to them.
    private func tints(at date: Date) -> (warm: ModeSwitcherInputTint, cool: ModeSwitcherInputTint) {
        let target = (warm: ModeSwitcherInputTint(modes[current].warm), cool: ModeSwitcherInputTint(modes[current].cool))
        guard let leaving, !reduceMotion else { return target }
        let k = shift.value(at: date)
        return (leaving.warm.mixed(with: target.warm, k), leaving.cool.mixed(with: target.cool, k))
    }

    /// Glows, the dots they light up, the box, its border and the light running around it.
    private func drawAura(_ context: GraphicsContext, date: Date) {
        let time = reduceMotion ? 0 : date.timeIntervalSince(bornAt)
        let tint = tints(at: date)
        let box = CGRect(x: Metrics.bleed, y: Metrics.bleed, width: Metrics.width, height: Metrics.height)
        let shape = Path(roundedRect: box, cornerRadius: Metrics.radius, style: .circular)

        // The glows breathe against each other and sway a little.
        let breath = 0.5 + 0.5 * sin(time * tau / Metrics.breathPeriod)
        let sway = sin(time * tau / Metrics.swayPeriod)
        let bob = cos(time * tau / Metrics.breathPeriod)
        let blooms: [(center: CGPoint, radius: Double, tint: ModeSwitcherInputTint, strength: Double)] = [
            (CGPoint(x: box.minX + 30 + 8 * sway, y: box.minY + 16 + 5 * bob), 128 * (0.94 + 0.12 * breath), tint.warm, 0.6 + 0.2 * breath),
            (CGPoint(x: box.maxX - 30 - 8 * sway, y: box.maxY - 16 - 5 * bob), 138 * (1.06 - 0.12 * breath), tint.cool, 0.8 - 0.2 * breath),
        ]
        for bloom in blooms {
            let disc = Path(ellipseIn: CGRect(x: bloom.center.x - bloom.radius, y: bloom.center.y - bloom.radius, width: bloom.radius * 2, height: bloom.radius * 2))
            context.fill(
                disc,
                with: .radialGradient(
                    Gradient(stops: [
                        .init(color: bloom.tint.color(bloom.strength), location: 0),
                        .init(color: bloom.tint.color(bloom.strength * 0.38), location: 0.42),
                        .init(color: bloom.tint.color(0), location: 1),
                    ]),
                    center: bloom.center, startRadius: 0, endRadius: bloom.radius
                )
            )
            // A grid of dots shows only where a glow falls on it.
            context.fill(
                Metrics.dots,
                with: .radialGradient(
                    Gradient(stops: [
                        .init(color: bloom.tint.pale.color(0.55 * bloom.strength), location: 0),
                        .init(color: bloom.tint.pale.color(0), location: 0.85),
                    ]),
                    center: bloom.center, startRadius: 0, endRadius: bloom.radius
                )
            )
        }

        // The two ends of the box are rimmed with light, as if the glows sat right behind it.
        let across = (start: CGPoint(x: box.minX, y: box.midY), end: CGPoint(x: box.maxX, y: box.midY))
        var rim = context
        rim.addFilter(.blur(radius: Metrics.rimBlur))
        rim.stroke(
            shape,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: tint.warm.color(0.95), location: 0),
                    .init(color: tint.warm.color(0), location: 0.42),
                    .init(color: tint.cool.color(0), location: 0.58),
                    .init(color: tint.cool.color(0.95), location: 1),
                ]),
                startPoint: across.start, endPoint: across.end
            ),
            lineWidth: Metrics.rimWidth
        )

        context.fill(shape, with: .color(Color(hex: 0x07080c)))
        context.stroke(
            Path(roundedRect: box.insetBy(dx: 0.5, dy: 0.5), cornerRadius: Metrics.radius - 0.5, style: .circular),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: tint.warm.color(), location: 0),
                    .init(color: Color(hex: 0xd9d4e6, opacity: 0.55), location: 0.5),
                    .init(color: tint.cool.color(), location: 1),
                ]),
                startPoint: across.start, endPoint: across.end
            ),
            lineWidth: 1
        )

        // After a send a light runs once around the border, from the send button and back to it.
        let run = lap.value(at: date)
        guard run > 0, run < 1, !reduceMotion else { return }
        let glint = Gradient(stops: [
            .init(color: .white.opacity(0), location: 0),
            .init(color: .white.opacity(0), location: 0.7),
            .init(color: .white.opacity(0.55), location: 0.93),
            .init(color: .white, location: 1),
        ])
        let shading = GraphicsContext.Shading.conicGradient(
            glint, center: CGPoint(x: box.midX, y: box.midY), angle: .degrees(Metrics.lapStart + 360 * run)
        )
        var light = context
        light.opacity = min(1, min(run, 1 - run) * 5)
        var halo = light
        halo.opacity = light.opacity * 0.25
        halo.stroke(shape, with: shading, lineWidth: 5)
        light.stroke(shape, with: shading, lineWidth: 1.5)
    }

    // MARK: Actions

    private func write(_ next: String) {
        if let text { text.wrappedValue = next } else { draft = next }
        onChangeText?(next)
    }

    private func choose(_ slot: Int, byUser: Bool) {
        guard modes.indices.contains(slot), slot != current else { return }
        leaving = tints(at: .now)
        mode = slot
        shift = Tween(0)
        shift.retarget(1, duration: Metrics.shift, curve: .easeInOut)
        if byUser { onModeChange?(slot) }
    }

    private func submit() {
        interrupt()
        let message = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !message.isEmpty else { return }
        taps += 1
        onSend?(message, current)
        write("")
        deliver(message)
    }

    /// The text leaves the field and a light runs around the border.
    private func deliver(_ message: String) {
        var still = Transaction()
        still.disablesAnimations = true
        withTransaction(still) {
            ghost = message
            ghostGone = false
        }
        lap = Tween(0)
        lap.retarget(1, duration: Metrics.lap, curve: .easeInOut)
        Task { @MainActor in
            // One frame at the starting pose, then the text moves.
            try? await Task.sleep(for: .milliseconds(20))
            ghostGone = true
        }
    }

    /// Any touch takes the composer away from the script.
    private func interrupt() {
        resumes += 1
        guard playing else { return }
        playing = false
        engaged = false
        heldPill = false
        heldRow = nil
        heldSend = false
    }

    /// One loop after another, every step at its own moment after the loop began.
    private func play() async {
        let letters = Array(script)
        let keys = Metrics.keyTimes(count: letters.count)
        let home = current
        let away = (home + 1) % modes.count
        while !Task.isCancelled {
            let start = ContinuousClock.now
            func reach(_ seconds: Double) async -> Bool {
                try? await Task.sleep(until: start + .seconds(seconds), clock: .continuous)
                return !Task.isCancelled
            }
            /// Opens the menu from the pill, rests on a row, and chooses it.
            func pick(_ slot: Int, at seconds: Double) async -> Bool {
                guard await reach(seconds) else { return false }
                heldPill = true
                guard await reach(seconds + Metrics.pressLead) else { return false }
                heldPill = false
                menuOpen = true
                guard await reach(seconds + Metrics.pointAfter) else { return false }
                heldRow = slot
                guard await reach(seconds + Metrics.chooseAfter) else { return false }
                heldRow = nil
                choose(slot, byUser: false)
                menuOpen = false
                return true
            }

            guard await pick(away, at: Metrics.openAt) else { return }
            guard await reach(Metrics.focusAt) else { return }
            engaged = true
            keyAt = .now
            for (index, at) in keys.enumerated() {
                guard await reach(at) else { return }
                draft = String(letters[...index])
                keyAt = .now
            }
            guard await reach(Metrics.sendAt - Metrics.pressLead) else { return }
            heldSend = true
            guard await reach(Metrics.sendAt) else { return }
            heldSend = false
            engaged = false
            let message = draft
            draft = ""
            deliver(message)
            guard await pick(home, at: Metrics.reopenAt) else { return }
            guard await reach(Metrics.loop) else { return }
        }
    }
}

private enum Metrics {
    static let width = 350.0
    static let height = 100.0
    static let radius = 22.0
    /// The canvas of the glows overflows the composer by this much on every side.
    static let bleed = 160.0
    static let control = 32.0
    static let pill = 30.0
    /// Height of one line in a pill's rolling label.
    static let rollHeight = 18.0
    static let menuWidth = 172.0
    static let rowHeight = 34.0
    static let blink = 0.53
    static let placeholderDelay = 0.2
    /// The light on the box's rim: a stroke this wide, blurred by this much.
    static let rimWidth = 7.0
    static let rimBlur = 7.0
    /// The glows swell and fade over this many seconds, and sway over twice that.
    static let breathPeriod = 5.0
    static let swayPeriod = 10.0
    /// How long the colours take to change, and the light to run around the border.
    static let shift = 0.6
    static let lap = 1.2
    /// The light starts at the send button: degrees clockwise from three o'clock.
    static let lapStart = 16.0

    static let ink = Color(hex: 0xe6e8ee)
    static let label = Color(hex: 0xc9cdd6)
    static let chip = Color(hex: 0x12141b)
    static let edge = Color(hex: 0x2a2d38)

    static let rollSpring = Animation.interpolatingSpring(mass: 1, stiffness: 300, damping: 24)
    static let menuSpring = Animation.interpolatingSpring(mass: 1, stiffness: 380, damping: 26)

    // autoPlay loop, in seconds from its start.
    static let openAt = 0.8
    /// A scripted tap holds its button down this long before it acts.
    static let pressLead = 0.15
    /// After the pill is pressed: when the finger rests on a row, and when it chooses it.
    static let pointAfter = 0.9
    static let chooseAfter = 1.15
    static let focusAt = 2.9
    static let typeAt = 3.2
    /// The longest the typing may take; a longer script is typed faster.
    static let typeSpan = 2.2
    static let sendAt = 5.9
    static let reopenAt = 7.3
    static let loop = 10.0
    /// After the last touch, the loop waits this long before it starts again.
    static let resume = 3.0

    /// Milliseconds before each key, used in turn: uneven like a hand, the same in every loop.
    static let rhythm = [90.0, 60, 110, 70, 50, 130, 80, 60, 100, 70, 150, 60, 90]

    /// The moment each letter of the script lands.
    static func keyTimes(count: Int) -> [Double] {
        var total = 0.0
        let sums = (0..<count).map { index -> Double in
            total += rhythm[index % rhythm.count] / 1000
            return total
        }
        let pace = total > typeSpan ? typeSpan / total : 1
        return sums.map { typeAt + $0 * pace }
    }

    /// The grid of dots behind the composer, in the canvas's coordinates.
    static let dots: Path = {
        var path = Path()
        let step = 13.0
        let size = 1.0
        var y = step / 2
        while y < height + bleed * 2 {
            var x = step / 2
            while x < width + bleed * 2 {
                path.addEllipse(in: CGRect(x: x - size, y: y - size, width: size * 2, height: size * 2))
                x += step
            }
            y += step
        }
        return path
    }()
}

/// Outlines in 16 pt boxes (the chevron in a 10 pt one), the same strings in the three stacks.
private enum Glyph {
    static let plus = "M 8 3.6 L 8 12.4 M 3.6 8 L 12.4 8"
    static let feather = "M 12.8 3.2 C 9.6 2.6 6.6 3.8 5.4 6.4 C 4.8 7.8 4.9 9.4 5.4 10.6 L 8.6 10.6 C 10.6 10.2 12.2 8.8 12.8 6.8 C 13.2 5.6 13.2 4.4 12.8 3.2 Z M 2.8 13.2 L 9.6 6.4"
    static let wave = "M 2.8 6.6 L 2.8 9.4 M 5.4 4.6 L 5.4 11.4 M 8 2.8 L 8 13.2 M 10.6 5.2 L 10.6 10.8 M 13.2 6.8 L 13.2 9.2"
    static let plane = "M 13.6 2.4 L 2.4 6.9 L 6.9 9.1 L 9.1 13.6 Z"
    static let check = "M 3.4 8.4 L 6.6 11.4 L 12.6 4.8"
    static let chevron = "M 2.5 3.8 L 5 6.3 L 7.5 3.8"

    static func mode(_ icon: ModeSwitcherInputIcon) -> String {
        switch icon {
        case .bolt: "M 9 1.8 L 3.8 9 L 7.6 9 L 7 14.2 L 12.2 7 L 8.4 7 Z"
        case .scales: "M 8 2.6 L 8 13.4 M 4.8 13.4 L 11.2 13.4 M 3.2 4.6 L 12.8 4.6 M 1.6 9.2 L 3.4 4.8 L 5.2 9.2 C 5.2 10.03 4.39 10.7 3.4 10.7 C 2.41 10.7 1.6 10.03 1.6 9.2 M 10.8 9.2 L 12.6 4.8 L 14.4 9.2 C 14.4 10.03 13.59 10.7 12.6 10.7 C 11.61 10.7 10.8 10.03 10.8 9.2"
        case .bulb: "M 6 10.4 C 4.6 9.6 3.8 8.2 3.8 6.6 C 3.8 4.3 5.7 2.4 8 2.4 C 10.3 2.4 12.2 4.3 12.2 6.6 C 12.2 8.2 11.4 9.6 10 10.4 L 10 11.6 L 6 11.6 Z M 6.6 13.8 L 9.4 13.8"
        case .flask: "M 6 2.4 L 10 2.4 M 6.8 2.4 L 6.8 6.4 L 3.4 12 C 2.9 12.9 3.5 13.8 4.5 13.8 L 11.5 13.8 C 12.5 13.8 13.1 12.9 12.6 12 L 9.2 6.4 L 9.2 2.4 M 5 10.2 L 11 10.2"
        }
    }
}

/// A colour kept as its three channels, so two of them can be mixed frame by frame.
private struct ModeSwitcherInputTint {
    var red: Double
    var green: Double
    var blue: Double

    init(red: Double, green: Double, blue: Double) {
        self.red = red
        self.green = green
        self.blue = blue
    }

    init(_ hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xff) / 255, green: Double((hex >> 8) & 0xff) / 255, blue: Double(hex & 0xff) / 255)
    }

    /// Halfway to white: the colour of what a glow lights up.
    var pale: ModeSwitcherInputTint {
        mixed(with: ModeSwitcherInputTint(red: 1, green: 1, blue: 1), 0.55)
    }

    func mixed(with other: ModeSwitcherInputTint, _ share: Double) -> ModeSwitcherInputTint {
        ModeSwitcherInputTint(
            red: red + (other.red - red) * share,
            green: green + (other.green - green) * share,
            blue: blue + (other.blue - blue) * share
        )
    }

    func color(_ opacity: Double = 1) -> Color {
        Color(.sRGB, red: red, green: green, blue: blue, opacity: opacity)
    }
}

/// Builds a path from "M x y L x y C x1 y1 x2 y2 x y Z": absolute commands, separated by spaces.
private func modeSwitcherInputPath(_ d: String) -> Path {
    var path = Path()
    var command: Character = "M"
    var numbers: [Double] = []
    for token in d.split(separator: " ") {
        guard let number = Double(token) else {
            command = token.first ?? "M"
            numbers = []
            if command == "Z" { path.closeSubpath() }
            continue
        }
        numbers.append(number)
        switch (command, numbers.count) {
        case ("M", 2):
            path.move(to: CGPoint(x: numbers[0], y: numbers[1]))
        case ("L", 2):
            path.addLine(to: CGPoint(x: numbers[0], y: numbers[1]))
        case ("C", 6):
            path.addCurve(
                to: CGPoint(x: numbers[4], y: numbers[5]),
                control1: CGPoint(x: numbers[0], y: numbers[1]),
                control2: CGPoint(x: numbers[2], y: numbers[3])
            )
        default:
            break
        }
    }
    return path
}

/// A stroked outline in the current foreground colour.
private struct ModeSwitcherInputGlyph: View {
    let d: String
    var size = 16.0

    var body: some View {
        modeSwitcherInputPath(d)
            .stroke(style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
            .frame(width: size, height: size)
    }
}

/// A pill's label: all the names stand one above the other and the strip rolls to the one in
/// use, while the pill takes that name's width.
private struct ModeSwitcherInputRoll: View {
    let labels: [String]
    let index: Int
    let still: Bool

    @State private var widths: [Int: Double] = [:]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(labels.enumerated()), id: \.offset) { slot, label in
                Text(label)
                    .font(.system(size: 13, weight: .medium))
                    .lineLimit(1)
                    .fixedSize()
                    .frame(height: Metrics.rollHeight)
                    .onGeometryChange(for: Double.self) { $0.size.width } action: { widths[slot] = $0 }
            }
        }
        .offset(y: -Double(index) * Metrics.rollHeight)
        .frame(width: widths[index] ?? 0, height: Metrics.rollHeight, alignment: .topLeading)
        .clipped()
        .animation(still ? .easeOut(duration: 0.2) : Metrics.rollSpring, value: index)
    }
}

/// A control dips under a finger, or under the script's.
private struct ModeSwitcherInputPress: ButtonStyle {
    let held: Bool

    func makeBody(configuration: Configuration) -> some View {
        let down = configuration.isPressed || held
        configuration.label
            .scaleEffect(down ? 0.94 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: down)
    }
}

/// A row of the menu lights up under a finger, and more softly while it is the mode in use.
private struct ModeSwitcherInputRow: ButtonStyle {
    let held: Bool
    let current: Bool

    func makeBody(configuration: Configuration) -> some View {
        let down = configuration.isPressed || held
        configuration.label
            .background(
                RoundedRectangle(cornerRadius: 8, style: .circular)
                    .fill(Color(hex: down ? 0x262a36 : 0x1e212b))
                    .opacity(down || current ? 1 : 0)
            )
            .animation(.easeOut(duration: 0.12), value: down)
    }
}
