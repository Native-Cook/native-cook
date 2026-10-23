import SwiftUI
import UIKit

/// A picture attached to the prompt: its address, and the flat colour shown until it arrives.
struct OrbitPromptPillPhoto {
    var url: String
    var tint: Color
}

/// A prompt field whose attachments circle it. Once something is typed, tag chips rise from behind
/// the pill's top edge, a cyan light crosses it and the attached pictures burst out onto a tilted
/// orbit: behind the pill on the far side, in front of it on the near side, larger the nearer they
/// are. "+" adds a picture to the ring; sending spirals them all into the send button.
struct OrbitPromptPill: View {
    /// The field's text. Leave it out and the component keeps the text itself.
    var text: Binding<String>? = nil
    /// Types `script`, lets the pictures orbit, sends and starts over, in a loop.
    var autoPlay = false
    var placeholder = "Ask anything"
    /// What the autoPlay loop types.
    var script = "What's today's run?"
    var tags = ["Daily", "Trail", "Run"]
    /// The pictures that can be attached; the first `attached` of them are on the ring from the start.
    var photos = [
        OrbitPromptPillPhoto(url: orbitPhoto("1441974231531-c6227db76b6e"), tint: Color(hex: 0x4a6332)),
        OrbitPromptPillPhoto(url: orbitPhoto("1507525428034-b723cf961d3e"), tint: Color(hex: 0xc9906c)),
        OrbitPromptPillPhoto(url: orbitPhoto("1470071459604-3b5ec3a7fe05"), tint: Color(hex: 0x5f7c4c)),
        OrbitPromptPillPhoto(url: orbitPhoto("1501785888041-af3ef285b470"), tint: Color(hex: 0x2f8f9a)),
        OrbitPromptPillPhoto(url: orbitPhoto("1501594907352-04cda38ebc29"), tint: Color(hex: 0xb5573c)),
        OrbitPromptPillPhoto(url: orbitPhoto("1472214103451-9374bd1c798e"), tint: Color(hex: 0x8a8f45)),
        OrbitPromptPillPhoto(url: orbitPhoto("1526772662000-3f88f10405ff"), tint: Color(hex: 0x6d808c)),
    ]
    var attached = 5
    /// Colour of the tag chips and the caret.
    var accent = Color(hex: 0x2f7bea)
    /// Called with the text and the number of pictures on the ring.
    var onSend: ((String, Int) -> Void)? = nil
    /// Called with the new number of pictures when "+" adds one.
    var onAttach: ((Int) -> Void)? = nil
    var onRemoveTag: ((String) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focused: Bool
    @State private var sim = Simulation()
    @State private var own = ""

    /// Per-frame state: the ring, the chips and everything that eases after them.
    private final class Simulation {
        enum Stage { case closed, open, folding }

        var last: Date? = nil
        var time = 0.0
        var clock = 0.0
        var looping = false
        /// Seconds the loop still waits after a touch.
        var hold = 0.0
        /// Letters of the script the loop has typed, and whether the text in the field is the loop's.
        var typed = 0
        var scripted = false
        var pressed = false
        var sent = false

        var hasText = false
        var lastEdit = -10.0
        var stage = Stage.closed
        var openAt = 0.0
        var foldAt = 0.0
        /// The ring folds into the send button (1) or back behind the pill (0).
        var toSend = 0.0
        var base = 5
        var count = 5
        var bornAt = [Double](repeating: 0, count: Metrics.maxPhotos)
        var gone = [Bool](repeating: false, count: Metrics.maxTags)

        var intro = 0.0
        var ink = 1.0
        var fieldAlpha = 1.0
        var sendPress = 0.0
        var pulse = Chase(1)
        var pulseAt = Double.infinity
        var rippleAt = -10.0
        var plusTurn = 0.0
        var plusSpin = Chase(0)
        var chips = [Chase](repeating: Chase(0), count: Metrics.maxTags)
        var out = [Chase](repeating: Chase(0), count: Metrics.maxPhotos)
        var slot = [Chase](repeating: Chase(0), count: Metrics.maxPhotos)
        /// A text the loop wants in the field.
        var pendingText: String? = nil

        func touch() {
            hold = Metrics.resume
        }

        func edited(hasText: Bool) {
            self.hasText = hasText
            lastEdit = time
        }

        func attach() -> Bool {
            guard count < Metrics.maxPhotos, stage != .folding else { return false }
            bornAt[count] = time
            count += 1
            plusTurn += 90
            return true
        }

        func send() {
            hasText = false
            fieldAlpha = 0
            if stage == .open {
                fold(toSend: 1)
                pulseAt = time + Metrics.foldTime
            } else {
                pulseAt = time
            }
        }

        private func fold(toSend: Double) {
            stage = .folding
            foldAt = time
            self.toSend = toSend
        }

        private func close() {
            stage = .closed
            count = base
            for i in 0..<Metrics.maxPhotos { out[i] = Chase(0) }
            for i in 0..<Metrics.maxTags { gone[i] = false }
        }

        func advance(to date: Date, engaged: Bool, script: [Character], still: Bool) {
            guard let last else {
                self.last = date
                return
            }
            let dt = min(date.timeIntervalSince(last), 0.064)
            guard dt > 0 else { return }
            self.last = date
            time += dt

            if engaged { hold = Metrics.resume }
            if hold > 0 {
                hold -= dt
                if hold <= 0, looping {
                    clock = 0
                    typed = 0
                    sent = false
                }
            } else if looping {
                clock += dt
                if clock >= Metrics.loop {
                    clock -= Metrics.loop
                    typed = 0
                    sent = false
                }
                var letters = 0
                while letters < script.count, clock >= typedAt(letters, in: script) { letters += 1 }
                if clock < Metrics.sendAt, letters != typed {
                    typed = letters
                    scripted = true
                    pendingText = String(script.prefix(letters))
                    edited(hasText: letters > 0)
                }
                pressed = clock >= Metrics.pressAt && clock < Metrics.sendAt
                if clock >= Metrics.sendAt, !sent {
                    sent = true
                    scripted = false
                    pendingText = ""
                    send()
                }
            }

            switch stage {
            case .closed where (hasText && time - lastEdit >= Metrics.openDelay) || count > base:
                stage = .open
                openAt = time
                // Pictures added to an empty field come out at once; after typing they wait for the light.
                let wait = hasText ? Metrics.burstDelay : 0
                for i in 0..<count {
                    slot[i] = Chase(Double(i) / Double(count))
                    bornAt[i] = time + wait + Double(i) * Metrics.burstStagger
                }
            case .open where !hasText && count <= base:
                fold(toSend: 0)
            case .folding where time - foldAt >= Metrics.foldTime + Metrics.foldStagger * Double(count):
                close()
            default:
                break
            }
            if time >= pulseAt {
                pulseAt = .infinity
                rippleAt = time
                pulse.v += Metrics.pulseKick
            }

            intro = approach(intro, 1, 7, dt)
            ink = approach(ink, time - lastEdit > Metrics.settle ? 1 : 0, 10, dt)
            fieldAlpha = approach(fieldAlpha, 1, 6, dt)
            sendPress = approach(sendPress, pressed ? 1 : 0, 30, dt)
            pulse.step(to: 1, omega: 22, zeta: 0.35, dt)
            plusSpin.step(to: plusTurn, omega: 16, zeta: 0.6, dt)
            for i in 0..<Metrics.maxTags {
                let up = stage == .open && !gone[i] && time >= openAt + Double(i) * Metrics.chipStagger
                if still {
                    chips[i].x = approach(chips[i].x, up ? 1 : 0, 12, dt)
                } else {
                    chips[i].step(to: up ? 1 : 0, omega: 15, zeta: 0.6, dt)
                }
            }
            for i in 0..<Metrics.maxPhotos {
                let live = stage != .closed && i < count && time >= bornAt[i]
                if still {
                    out[i].x = approach(out[i].x, live ? 1 : 0, 10, dt)
                } else {
                    out[i].step(to: live ? 1 : 0, omega: 9.5, zeta: 0.55, dt)
                }
                slot[i].step(to: Double(i) / Double(max(count, 1)), omega: 8, zeta: 0.8, dt)
            }
        }
    }

    private var current: String { text?.wrappedValue ?? own }

    private var field: Binding<String> {
        Binding(
            get: { current },
            set: { new in
                guard new != current else { return }
                store(new)
                sim.scripted = false
                sim.edited(hasText: !new.isEmpty)
            }
        )
    }

    var body: some View {
        TimelineView(.animation) { timeline in
            let date = timeline.date
            let _ = sim.advance(to: date, engaged: focused || (!sim.scripted && (sim.hasText || sim.count > sim.base)), script: Array(script), still: reduceMotion)
            let s = sim
            let sweep = reduceMotion || s.stage != .open ? -1 : (s.time - s.openAt - Metrics.sweepDelay) / Metrics.sweepTime
            let ripple = (s.time - s.rippleAt) / Metrics.rippleTime
            let sinceEdit = s.time - s.lastEdit
            let blink = sinceEdit < 0.5 ? 1 : min(1, max(0, 0.5 + 3 * sin(tau * sinceEdit / Metrics.blink)))

            ZStack(alignment: .topLeading) {
                // Laid out afresh every frame: the list itself is what tells SwiftUI the pictures moved.
                ForEach(ring(s)) { spot in
                    thumbnail(photos[spot.id], size: spot.size)
                        .shadow(color: .black.opacity(0.1 + 0.1 * max(0, spot.depth)), radius: 8, y: 5)
                        .scaleEffect(spot.scale)
                        .opacity(spot.alpha * s.intro)
                        .offset(x: spot.x - spot.size / 2, y: spot.y - spot.size / 2)
                        .zIndex((spot.front ? Metrics.frontLayer : Metrics.backLayer) + spot.depth)
                        .allowsHitTesting(false)
                }

                HStack(spacing: 5) {
                    ForEach(Array(tags.prefix(Metrics.maxTags).enumerated()), id: \.offset) { index, tag in
                        let up = s.chips[index].x
                        Button {
                            sim.touch()
                            sim.gone[index] = true
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            onRemoveTag?(tag)
                        } label: {
                            chip(tag)
                        }
                        .buttonStyle(.plain)
                        .offset(y: reduceMotion ? 0 : (1 - up) * Metrics.chipTravel)
                        .opacity(min(1, max(0, up * 3)))
                        .allowsHitTesting(up > 0.9)
                    }
                }
                .fixedSize()
                .offset(x: Metrics.pillX + Metrics.chipInset, y: Metrics.pillY - Metrics.chipGap - Metrics.chipHeight)
                .zIndex(Metrics.chipLayer)

                pill(s, sweep: sweep, ripple: ripple, blink: blink)
                    .zIndex(Metrics.pillLayer)
            }
            .frame(width: Metrics.width, height: Metrics.height, alignment: .topLeading)
            .scaleEffect(reduceMotion ? 1 : Metrics.introScale + (1 - Metrics.introScale) * s.intro)
            // One piece for the parent: when the keyboard moves the layout, the layers move together.
            .geometryGroup()
            .onChange(of: date) { drain() }
        }
        .onAppear {
            sim.base = min(attached, photos.count, Metrics.maxPhotos)
            sim.count = sim.base
            sim.looping = autoPlay && !reduceMotion
        }
        .onChange(of: autoPlay) { sim.looping = autoPlay && !reduceMotion }
        .onChange(of: reduceMotion) { sim.looping = autoPlay && !reduceMotion }
        .onChange(of: focused) {
            // A tap on the field takes it over from the loop: what the loop typed is cleared.
            guard focused, sim.scripted else { return }
            sim.scripted = false
            store("")
            sim.edited(hasText: false)
        }
    }

    /// Where each picture is on the ring right now.
    private func ring(_ s: Simulation) -> [Spot] {
        let folding = s.stage == .folding
        let turn = reduceMotion ? 0 : Metrics.speed * (s.time - s.openAt)
        return (0..<min(photos.count, Metrics.maxPhotos)).map { index in
            let fold = folding ? min(1, max(0, (s.time - s.foldAt - Double(index) * Metrics.foldStagger) / Metrics.foldTime)) : 0
            let pull = fold * fold * fold
            let reach = (reduceMotion ? 1 : s.out[index].x) * (1 - pull)
            let angle = tau * (s.slot[index].x + turn) + Metrics.startAngle + Metrics.spiral * pull
            let depth = sin(angle - Metrics.nearAngle)
            let ex = Metrics.orbitX * cos(angle) * reach
            let ey = Metrics.orbitY * sin(angle) * reach
            let home = Metrics.width / 2 + Metrics.orbitShift
            return Spot(
                id: index,
                size: orbitSizes[index],
                x: home + (Metrics.sendX - home) * pull * s.toSend + ex * cos(Metrics.tilt) - ey * sin(Metrics.tilt),
                y: Metrics.height / 2 + ex * sin(Metrics.tilt) + ey * cos(Metrics.tilt),
                scale: (Metrics.midScale + Metrics.depthScale * depth) * (0.3 + 0.7 * min(1, max(0, reach))),
                alpha: min(1, max(0, s.out[index].x * (1 - pull) / 0.2)),
                depth: depth,
                // Near pictures pass in front of the pill, far ones behind it; nothing is in front until it has left the pill.
                front: folding ? s.toSend > 0.5 : depth > 0 && reach > 0.92
            )
        }
    }

    private struct Spot: Identifiable {
        let id: Int
        let size: Double
        let x: Double
        let y: Double
        let scale: Double
        let alpha: Double
        let depth: Double
        let front: Bool
    }

    private func thumbnail(_ photo: OrbitPromptPillPhoto, size: Double) -> some View {
        AsyncImage(url: URL(string: photo.url), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
            ZStack {
                photo.tint
                if let image = phase.image {
                    image.resizable().scaledToFill().transition(.opacity)
                }
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.photoRadius, style: .circular))
    }

    private func chip(_ tag: String) -> some View {
        HStack(spacing: 5) {
            Text(tag)
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(.white)
                .lineLimit(1)
            Canvas { context, _ in
                var cross = Path()
                cross.move(to: CGPoint(x: 1, y: 1))
                cross.addLine(to: CGPoint(x: 7, y: 7))
                cross.move(to: CGPoint(x: 7, y: 1))
                cross.addLine(to: CGPoint(x: 1, y: 7))
                context.stroke(cross, with: .color(.white), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
            }
            .frame(width: 8, height: 8)
        }
        .padding(.leading, 9)
        .padding(.trailing, 8)
        .frame(height: Metrics.chipHeight)
        .background(Capsule().fill(accent))
        .accessibilityLabel(Text(verbatim: "Remove \(tag)"))
    }

    private func pill(_ s: Simulation, sweep: Double, ripple: Double, blink: Double) -> some View {
        let font = Font.system(size: 15, weight: .medium)
        return ZStack(alignment: .topLeading) {
            Capsule(style: .circular)
                .fill(.white)
                .shadow(color: .black.opacity(0.07), radius: 10, y: 6)
            // The light that crosses the pill just before the pictures come out.
            LinearGradient(
                stops: [
                    .init(color: Color(hex: 0x7fe3ff, opacity: 0), location: 0),
                    .init(color: Color(hex: 0x7fe3ff, opacity: 0.55), location: 0.35),
                    .init(color: Color(hex: 0xc8f6ff, opacity: 0.85), location: 0.5),
                    .init(color: Color(hex: 0x7fe3ff, opacity: 0.55), location: 0.65),
                    .init(color: Color(hex: 0x7fe3ff, opacity: 0), location: 1),
                ],
                startPoint: .leading, endPoint: .trailing
            )
            .frame(width: Metrics.sweepWidth, height: Metrics.pillHeight)
            .offset(x: -Metrics.sweepWidth + (Metrics.pillWidth + Metrics.sweepWidth) * sweep)
            .opacity(sweep > 0 && sweep < 1 ? 1 : 0)
            .frame(width: Metrics.pillWidth, height: Metrics.pillHeight, alignment: .topLeading)
            .clipShape(Capsule(style: .circular))
            .allowsHitTesting(false)
            Capsule(style: .circular)
                .strokeBorder(Color(hex: 0xe6e6e6), lineWidth: 1)

            Button {
                sim.touch()
                if sim.attach() {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    onAttach?(sim.count)
                }
            } label: {
                Canvas { context, _ in
                    var plus = Path()
                    plus.move(to: CGPoint(x: 12, y: 22))
                    plus.addLine(to: CGPoint(x: 32, y: 22))
                    plus.move(to: CGPoint(x: 22, y: 12))
                    plus.addLine(to: CGPoint(x: 22, y: 32))
                    context.stroke(plus, with: .color(Color(hex: 0xb4b4b4)), style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
                }
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .rotationEffect(.degrees(s.plusSpin.x))
            .offset(x: Metrics.plusX - 22, y: Metrics.pillHeight / 2 - 22)
            .accessibilityLabel("Attach a picture")

            TextField("", text: field, prompt: Text(placeholder).foregroundStyle(Color(hex: 0xa8a8a8)))
                .font(font)
                .foregroundStyle(Color.rgba(34 + 126 * (1 - s.ink), 34 + 126 * (1 - s.ink), 34 + 126 * (1 - s.ink), 1))
                .tint(accent)
                .focused($focused)
                .submitLabel(.send)
                .onSubmit(send)
                .frame(width: Metrics.fieldWidth, height: Metrics.pillHeight)
                .offset(x: Metrics.fieldX)
                .opacity(s.fieldAlpha)

            // The loop's own caret, right after the last letter it typed.
            HStack(spacing: 1) {
                Text(current).font(font).lineLimit(1).opacity(0)
                RoundedRectangle(cornerRadius: 1, style: .circular)
                    .fill(accent)
                    .frame(width: 2, height: 19)
                    .opacity(s.scripted && s.hasText && !focused ? blink : 0)
            }
            .frame(width: Metrics.fieldWidth, height: Metrics.pillHeight, alignment: .leading)
            .offset(x: Metrics.fieldX)
            .allowsHitTesting(false)

            Circle()
                .strokeBorder(Color(hex: 0x0b0b0b, opacity: 0.5 * max(0, 1 - ripple)), lineWidth: 2)
                .frame(width: Metrics.sendSize, height: Metrics.sendSize)
                .scaleEffect(1 + Metrics.rippleGrow * min(1, max(0, ripple)))
                .opacity(ripple > 0 && ripple < 1 ? 1 : 0)
                .offset(x: Metrics.sendX - Metrics.pillX - Metrics.sendSize / 2, y: (Metrics.pillHeight - Metrics.sendSize) / 2)
                .allowsHitTesting(false)
            Button(action: send) {
                Canvas { context, _ in
                    var arrow = Path()
                    arrow.move(to: CGPoint(x: 19, y: 26.5))
                    arrow.addLine(to: CGPoint(x: 19, y: 12))
                    arrow.move(to: CGPoint(x: 12.5, y: 18.5))
                    arrow.addLine(to: CGPoint(x: 19, y: 12))
                    arrow.addLine(to: CGPoint(x: 25.5, y: 18.5))
                    context.stroke(arrow, with: .color(.white), style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
                }
                .frame(width: Metrics.sendSize, height: Metrics.sendSize)
                .background(Circle().fill(Color(hex: 0x0b0b0b)))
            }
            .buttonStyle(PressStyle(scale: s.pulse.x * (1 - Metrics.pressDip * s.sendPress)) { down in
                sim.touch()
                sim.pressed = down
            })
            .offset(x: Metrics.sendX - Metrics.pillX - Metrics.sendSize / 2, y: (Metrics.pillHeight - Metrics.sendSize) / 2)
            .accessibilityLabel("Send")
        }
        .frame(width: Metrics.pillWidth, height: Metrics.pillHeight, alignment: .topLeading)
        .opacity(s.intro)
        .offset(x: Metrics.pillX, y: Metrics.pillY)
    }

    private func store(_ new: String) {
        if let text { text.wrappedValue = new } else { own = new }
    }

    private func send() {
        let sent = current.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !sent.isEmpty || sim.count > sim.base else { return }
        let pictures = sim.stage == .open ? sim.count : 0
        store("")
        sim.touch()
        sim.scripted = false
        sim.send()
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        onSend?(sent, pictures)
    }

    /// Applies what the last frame decided: state can't change while the frame is being drawn.
    private func drain() {
        guard let pending = sim.pendingText else { return }
        sim.pendingText = nil
        store(pending)
    }

    private struct PressStyle: ButtonStyle {
        let scale: Double
        let onPress: (Bool) -> Void

        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(scale)
                .onChange(of: configuration.isPressed) { _, pressed in onPress(pressed) }
        }
    }
}

private enum Metrics {
    static let width = 350.0
    static let height = 300.0
    static let pillWidth = 330.0
    static let pillHeight = 52.0
    static let pillX = (width - pillWidth) / 2
    static let pillY = (height - pillHeight) / 2
    /// Centre of "+" from the pill's left end, and where the text starts.
    static let plusX = 27.0
    static let fieldX = 50.0
    static let sendSize = 38.0
    /// Centre of the send button, in the component's box.
    static let sendX = pillX + pillWidth - 7 - sendSize / 2
    static let fieldWidth = pillWidth - fieldX - sendSize - 16
    static let pressDip = 0.08
    /// The pill grows from this scale as it fades in.
    static let introScale = 0.92

    // Back to front: far pictures (each shifted by its depth, -1…1), chips, the pill, near pictures.
    static let backLayer = 2.0
    static let chipLayer = 4.0
    static let pillLayer = 5.0
    static let frontLayer = 8.0

    static let maxTags = 6
    static let chipHeight = 22.0
    /// Between the chips and the pill's top edge, and from the pill's left end to the first chip.
    static let chipGap = 5.0
    static let chipInset = 30.0
    /// A chip starts this far down, hidden behind the pill.
    static let chipTravel = 28.0
    static let chipStagger = 0.15

    static let maxPhotos = 7
    static let photoRadius = 12.0
    // The ring: its radii, how far right of the pill's centre it sits, and its slant.
    static let orbitX = 116.0
    static let orbitY = 92.0
    static let orbitShift = 6.0
    static let tilt = -0.19
    /// Where on the ring the first picture starts, and the direction pictures are nearest in.
    static let startAngle = -1.12
    static let nearAngle = -0.7
    /// Turns per second.
    static let speed = 0.2
    // A picture's scale halfway between far and near, and how much depth adds or takes.
    static let midScale = 0.87
    static let depthScale = 0.25

    /// The ring opens once the text has been left alone for this long.
    static let openDelay = 0.3
    /// Typed letters stay grey until the typing has paused for this long.
    static let settle = 0.25
    // After the ring opens: when the light starts across the pill, and when the first picture leaves.
    static let sweepDelay = 0.55
    static let sweepTime = 0.5
    static let sweepWidth = 110.0
    static let burstDelay = 0.7
    static let burstStagger = 0.06
    // Sending: each picture takes this long to reach the button, one a little after the other, turning this much more.
    static let foldTime = 0.55
    static let foldStagger = 0.04
    static let spiral = 2.4
    /// Scale per second the send button is kicked with when the pictures land, and its ring.
    static let pulseKick = 5.0
    static let rippleTime = 0.5
    static let rippleGrow = 0.9
    static let blink = 1.1

    // autoPlay loop (seconds): typing starts, the send button is pressed, the prompt is sent.
    static let typeAt = 0.8
    static let pressAt = 7.45
    static let sendAt = 7.6
    static let loop = 9.4
    /// After a touch, or once the field is left empty, the loop waits this long before it starts over.
    static let resume = 3.0
}

/// Seconds between letters as the loop types: uneven like a hand, the same every time.
private let typingRhythm = [0.09, 0.07, 0.11, 0.06, 0.13, 0.08, 0.06, 0.1, 0.07, 0.12]

/// When the loop types letter `index` of the script; a new word takes a little longer.
private func typedAt(_ index: Int, in script: [Character]) -> Double {
    var t = Metrics.typeAt
    for i in 0..<index {
        t += typingRhythm[i % typingRhythm.count] + (script[i] == " " ? 0.08 : 0)
    }
    return t
}

/// Side of each picture at scale 1, in points.
private let orbitSizes = [84.0, 66, 78, 62, 74, 68, 72]

private func orbitPhoto(_ id: String) -> String {
    "https://images.unsplash.com/photo-\(id)?auto=format&fit=crop&w=240&h=240&q=80"
}

/// A value that follows its target like a spring (mass 1, stiffness omega², damping 2·zeta·omega).
private struct Chase {
    var x: Double
    var v = 0.0

    init(_ x: Double) {
        self.x = x
    }

    mutating func step(to target: Double, omega: Double, zeta: Double, _ dt: Double) {
        v += (omega * omega * (target - x) - 2 * zeta * omega * v) * dt
        x += v * dt
    }
}

/// Moves `x` towards `target`, covering the same share of the distance in the same time at any frame rate.
private func approach(_ x: Double, _ target: Double, _ rate: Double, _ dt: Double) -> Double {
    x + (target - x) * (1 - exp(-rate * dt))
}

#Preview {
    ZStack {
        Color(hex: 0xf4f4f4).ignoresSafeArea()
        OrbitPromptPill(autoPlay: true)
    }
}
