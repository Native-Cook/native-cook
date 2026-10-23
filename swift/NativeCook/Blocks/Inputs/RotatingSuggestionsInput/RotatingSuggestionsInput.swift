import SwiftUI
import UIKit

/// Assistant composer with three suggestion pills stacked above the field. The suggestions rotate by
/// themselves: each pill's text slides down and fades while the next one comes in from just above,
/// the three pills a beat apart, and every pill resizes to fit its new text. Tapping a pill lifts it,
/// drops it into the field, where its text appears letter by letter beside a send button, and sends it.
struct RotatingSuggestionsInput: View {
    /// The field's value when the caller owns it; left nil, the component keeps its own.
    var text: Binding<String>? = nil
    /// Rotates through the sets and picks one suggestion per loop by itself. Never opens the keyboard.
    var autoPlay = false
    var title = "Nova"
    var placeholder = "Ask anything"
    /// Sets of three suggestions, top to bottom. The stack shows one set at a time.
    var suggestions = [
        ["Make an image of a rocket", "Top books of the year", "Discuss the theory of relativity"],
        ["Show the concept of time travel", "Top books of the year", "Explain weather versus climate"],
        ["Capture a serene mountain lake", "Fashion trends for next season", "What are the Seven Wonders?"],
        ["Render a superhero in action", "Current environmental issues", "How do neural networks work?"],
    ]
    var footnote = "By continuing you agree to the Terms and Privacy Policy"
    /// Parts of the footnote drawn darker, as links.
    var footnoteLinks = ["Terms", "Privacy Policy"]
    var onChangeText: ((String) -> Void)? = nil
    /// Called with a suggestion when its pill is tapped.
    var onSelect: ((String) -> Void)? = nil
    /// Called with the trimmed message when it is sent: by the send button, the keyboard, or a tapped pill.
    var onSend: ((String) -> Void)? = nil
    var onAttach: (() -> Void)? = nil

    /// Where the picked pill is on its way into the field.
    private enum Stage {
        case idle, lifted, flown, away
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focused: Bool
    @State private var draft = ""
    @State private var set = 0
    @State private var lastRotation = Date.now
    /// True while autoPlay follows its script; otherwise the sets simply rotate.
    @State private var scripted = false
    @State private var picked: Int? = nil
    @State private var pickedByHand = false
    @State private var stage = Stage.idle
    /// The caret drawn while a suggestion is typed into an unfocused field.
    @State private var caretOn = false
    @State private var lastKey = Date.distantPast
    /// The send button's dip when a picked suggestion sends itself.
    @State private var pressed = false
    /// The sent text is on its way out of the field.
    @State private var clearing = false
    @State private var run = 0
    @State private var picks = 0
    @State private var sends = 0
    @State private var resume = 0
    @State private var haptics = 0

    private var value: Binding<String> {
        Binding(
            get: { text?.wrappedValue ?? draft },
            set: { new in
                if let text { text.wrappedValue = new } else { draft = new }
                onChangeText?(new)
            }
        )
    }

    private var hasText: Bool { !value.wrappedValue.isEmpty }

    private var lift: Animation {
        reduceMotion ? .easeOut(duration: 0.2) : .interpolatingSpring(mass: 1, stiffness: Springs.liftStiffness, damping: Springs.liftDamping)
    }

    private var pop: Animation {
        reduceMotion ? .easeOut(duration: 0.2) : .interpolatingSpring(mass: 1, stiffness: Springs.popStiffness, damping: Springs.popDamping)
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: Metrics.panelRadius, style: .circular).fill(Palette.panel)

            Text(title)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Palette.ink)
                .lineLimit(1)
                .frame(width: Metrics.panelWidth, height: 20)
                .offset(y: Metrics.titleY - 10)

            RotatingSuggestionsInputSparkle(points: 1)
                .fill(Palette.mark)
                .frame(width: Metrics.markSize, height: Metrics.markSize)
                .offset(x: (Metrics.panelWidth - Metrics.markSize) / 2, y: Metrics.markY - Metrics.markSize / 2)

            ForEach(0..<Metrics.slots, id: \.self) { slot in
                Button {
                    pick(slot, byHand: true)
                } label: {
                    RotatingSuggestionsInputPill(
                        label: label(slot), slot: slot,
                        lifted: picked == slot && stage == .lifted, away: picked == slot && stage == .away,
                        reduceMotion: reduceMotion
                    )
                }
                .buttonStyle(RotatingSuggestionsInputPress())
                .scaleEffect(scale(slot), anchor: .leading)
                .offset(flight(slot))
                .opacity(picked == slot && (stage == .flown || stage == .away) ? 0 : 1)
                .animation(stageAnimation, value: stage)
                .offset(x: Metrics.fieldX, y: Metrics.stackTop + Double(slot) * (Metrics.pillHeight + Metrics.pillGap))
                .zIndex(picked == slot ? 1 : 0)
                .allowsHitTesting(picked == nil && !clearing)
            }

            Button {
                onAttach?()
            } label: {
                ZStack {
                    Capsule().frame(width: 15, height: 1.6)
                    Capsule().frame(width: 1.6, height: 15)
                }
                .foregroundStyle(Palette.plus)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(RotatingSuggestionsInputPress())
            .accessibilityLabel("Attach")
            .offset(x: Metrics.plusX - 22, y: Metrics.fieldTop)

            field.offset(x: Metrics.fieldX, y: Metrics.fieldTop)

            footnoteLabel
                .frame(width: Metrics.panelWidth, height: 14)
                .offset(y: Metrics.footnoteY - 7)
        }
        .frame(width: Metrics.panelWidth, height: Metrics.panelHeight, alignment: .topLeading)
        .sensoryFeedback(.impact(weight: .light), trigger: haptics)
        .onAppear { restart() }
        .onChange(of: autoPlay) { restart() }
        .onChange(of: focused) { _, isFocused in
            if isFocused, scripted {
                stopScript()
                cancelPick()
            }
            resume += 1
        }
        .task(id: run) { await rotate() }
        .task(id: picks) { await runPick() }
        .task(id: sends) { await finishSend() }
        .task(id: resume) { await resumeWhenIdle() }
    }

    // MARK: Field

    private var field: some View {
        ZStack(alignment: .leading) {
            Text(placeholder)
                .foregroundStyle(Palette.placeholder)
                .lineLimit(1)
                .opacity(hasText ? 0 : 1)
                .animation(.easeOut(duration: 0.2), value: hasText)
                .padding(.leading, Metrics.fieldPadX)
                .allowsHitTesting(false)

            TextField("", text: value)
                .foregroundStyle(Palette.ink)
                .tint(Palette.caret)
                .focused($focused)
                .submitLabel(.send)
                .onSubmit { send(byHand: true) }
                .opacity(clearing ? 0 : 1)
                .offset(y: clearing && !reduceMotion ? -Metrics.clearRise : 0)
                .padding(.leading, Metrics.fieldPadX)
                .padding(.trailing, Metrics.fieldHeight)
                .frame(height: Metrics.fieldHeight)

            if caretOn, !focused {
                TimelineView(.animation) { timeline in
                    RoundedRectangle(cornerRadius: 1, style: .circular)
                        .fill(Palette.caret)
                        .frame(width: 2, height: 18)
                        .opacity(rotatingSuggestionsInputBlink(idle: timeline.date.timeIntervalSince(lastKey)))
                }
                .offset(x: Metrics.fieldPadX + Metrics.textWidth(value.wrappedValue, size: Metrics.fieldFont) + 1)
                .allowsHitTesting(false)
            }

            Button {
                send(byHand: true)
            } label: {
                ZStack {
                    Circle().fill(Palette.send)
                    RotatingSuggestionsInputArrow()
                        .stroke(.white, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                }
                .frame(width: Metrics.sendSize, height: Metrics.sendSize)
                .frame(width: Metrics.fieldHeight, height: Metrics.fieldHeight)
                .contentShape(Rectangle())
            }
            .buttonStyle(RotatingSuggestionsInputPress())
            .accessibilityLabel("Send")
            .scaleEffect(pressed ? Metrics.pressScale : 1)
            .animation(.easeOut(duration: Pick.press), value: pressed)
            .scaleEffect(hasText ? 1 : (reduceMotion ? 1 : Metrics.popScale))
            .opacity(hasText ? 1 : 0)
            .animation(pop, value: hasText)
            .allowsHitTesting(hasText && !clearing)
            .offset(x: Metrics.fieldWidth - Metrics.fieldHeight)
        }
        .font(.system(size: Metrics.fieldFont))
        .frame(width: Metrics.fieldWidth, height: Metrics.fieldHeight, alignment: .leading)
        .background(Capsule().fill(.white).shadow(color: .black.opacity(0.06), radius: 5, y: 2))
    }

    private var footnoteLabel: some View {
        var styled = AttributedString(footnote)
        styled.foregroundColor = Palette.footnote
        for link in footnoteLinks {
            if let range = styled.range(of: link) { styled[range].foregroundColor = Palette.footnoteLink }
        }
        return Text(styled).font(.system(size: 11)).lineLimit(1).minimumScaleFactor(0.8)
    }

    // MARK: Pills

    private func label(_ slot: Int) -> String {
        guard suggestions.indices.contains(set), suggestions[set].indices.contains(slot) else { return "" }
        return suggestions[set][slot]
    }

    private func scale(_ slot: Int) -> Double {
        guard picked == slot, !reduceMotion else { return 1 }
        switch stage {
        case .idle: return 1
        case .lifted: return Metrics.liftScale
        case .flown: return Metrics.flownScale
        case .away: return Metrics.returnScale
        }
    }

    /// The picked pill rises a little, then drops onto the start of the field's text.
    private func flight(_ slot: Int) -> CGSize {
        guard picked == slot, !reduceMotion else { return .zero }
        switch stage {
        case .idle, .away: return .zero
        case .lifted: return CGSize(width: 0, height: -Metrics.liftRise)
        case .flown:
            let pillCentre = Metrics.stackTop + Double(slot) * (Metrics.pillHeight + Metrics.pillGap) + Metrics.pillHeight / 2
            return CGSize(width: Metrics.fieldPadX - Metrics.pillPadLeft, height: Metrics.fieldTop + Metrics.fieldHeight / 2 - pillCentre)
        }
    }

    private var stageAnimation: Animation? {
        switch stage {
        case .lifted: return lift
        case .flown: return reduceMotion ? .easeOut(duration: 0.2) : .timingCurve(0.5, 0, 0.8, 0.4, duration: Pick.fly)
        // Invisible by now: it goes back to its slot at once, ready to return.
        case .away: return nil
        case .idle: return pop
        }
    }

    // MARK: Behaviour

    private func restart() {
        scripted = autoPlay
        cancelPick()
        set = 0
        run += 1
    }

    private func stopScript() {
        guard scripted else { return }
        scripted = false
        run += 1
    }

    private func cancelPick() {
        // Only a suggestion that was on its way into the field is taken out again.
        let typing = picked != nil
        picked = nil
        stage = .idle
        caretOn = false
        pressed = false
        clearing = false
        picks += 1
        if typing, hasText { value.wrappedValue = "" }
    }

    private func advance(to next: Int? = nil) {
        lastRotation = .now
        guard !suggestions.isEmpty else { return }
        set = next ?? (set + 1) % suggestions.count
    }

    private func pick(_ slot: Int, byHand: Bool) {
        let suggestion = label(slot)
        guard picked == nil, !clearing, !suggestion.isEmpty else { return }
        if byHand {
            stopScript()
            haptics += 1
            onSelect?(suggestion)
        }
        pickedByHand = byHand
        picked = slot
        stage = .lifted
        picks += 1
    }

    private func send(byHand: Bool) {
        let message = value.wrappedValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clearing, !message.isEmpty else { return }
        if byHand {
            if picked == nil { haptics += 1 }
            onSend?(message)
        }
        withAnimation(.easeIn(duration: Pick.clear)) { clearing = true }
        sends += 1
    }

    /// The sent text has left the field: empty it and let the placeholder return.
    private func finishSend() async {
        guard clearing else { return }
        try? await Task.sleep(for: .seconds(Pick.clear))
        guard !Task.isCancelled else { return }
        value.wrappedValue = ""
        clearing = false
        if picked == nil { resume += 1 }
    }

    /// A picked pill lifts, drops into the field, types itself there, waits a beat and sends.
    private func runPick() async {
        guard let slot = picked, stage == .lifted else { return }
        let suggestion = label(slot)
        let clock = ContinuousClock()
        let start = clock.now
        do {
            try await clock.sleep(until: start + .seconds(Pick.lift))
            stage = .flown
            try await clock.sleep(until: start + .seconds(Pick.typeAt))
            caretOn = true
            let key = min(Pick.slowestKey, Pick.typeSpan / Double(suggestion.count))
            for count in 1...suggestion.count {
                value.wrappedValue = String(suggestion.prefix(count))
                lastKey = .now
                try await clock.sleep(until: start + .seconds(Pick.typeAt + key * Double(count)))
            }
            stage = .away
            try await clock.sleep(until: start + .seconds(Pick.pressAt))
            pressed = true
            try await clock.sleep(until: start + .seconds(Pick.pressAt + Pick.press))
            pressed = false
            caretOn = false
            send(byHand: pickedByHand)
            try await clock.sleep(until: start + .seconds(Pick.pressAt + Pick.press + Pick.clear + 0.02))
            // The next set arrives; the pill returns to its slot with its new text, in step with the others.
            advance()
            try await clock.sleep(for: .seconds(Double(slot) * Script.stagger))
            picked = nil
            stage = .idle
            resume += 1
        } catch {}
    }

    /// Rotation. Under autoPlay every step waits for its own moment after the loop began, so the
    /// period stays `Script.loop`; otherwise the sets change every `Script.rotateEvery` seconds.
    private func rotate() async {
        let clock = ContinuousClock()
        do {
            guard scripted else {
                while true {
                    let wait = Script.rotateEvery - Date.now.timeIntervalSince(lastRotation)
                    if wait > 0 {
                        try await clock.sleep(for: .seconds(wait))
                    } else if picked == nil {
                        advance()
                    } else {
                        try await clock.sleep(for: .seconds(0.2))
                    }
                }
            }
            if set != 0 {
                advance(to: 0)
                try await clock.sleep(for: .seconds(Script.rotateEvery))
            }
            var start = clock.now
            while true {
                for at in Script.rotateAt {
                    try await clock.sleep(until: start + .seconds(at))
                    advance()
                }
                try await clock.sleep(until: start + .seconds(Script.pickAt))
                pick(Script.pickSlot, byHand: false)
                try await clock.sleep(until: start + .seconds(Script.homeAt))
                advance(to: 0)
                start += .seconds(Script.loop)
                try await clock.sleep(until: start)
            }
        } catch {}
    }

    /// autoPlay's script comes back once nothing has been touched for a while and the field is empty and unfocused.
    private func resumeWhenIdle() async {
        guard autoPlay, !scripted, !focused else { return }
        try? await Task.sleep(for: .seconds(Script.resumeAfter))
        guard !Task.isCancelled, !focused, !hasText, picked == nil else { return }
        scripted = true
        run += 1
    }
}

private enum Metrics {
    static let panelWidth = 350.0
    static let panelHeight = 400.0
    static let panelRadius = 40.0
    static let titleY = 36.0
    static let markY = 108.0
    static let markSize = 60.0

    static let slots = 3
    static let stackTop = 176.0
    static let pillHeight = 34.0
    static let pillGap = 8.0
    static let pillPadLeft = 11.0
    static let pillPadRight = 14.0
    static let iconSize = 15.0
    static let iconGap = 7.0
    static let pillFont = 14.0
    /// How far a text travels on its way out and on its way in.
    static let slide = 6.0

    static let plusX = 32.0
    static let fieldX = 56.0
    static let fieldTop = 306.0
    static let fieldWidth = 278.0
    static let fieldHeight = 44.0
    static let fieldPadX = 16.0
    static let fieldFont = 15.0
    static let sendSize = 32.0
    static let footnoteY = 372.0

    static let liftScale = 1.05
    static let liftRise = 5.0
    static let flownScale = 0.6
    static let returnScale = 0.8
    static let popScale = 0.4
    static let pressScale = 0.86
    static let clearRise = 6.0

    static func textWidth(_ text: String, size: Double) -> Double {
        (text as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: size)]).width
    }
}

private enum Springs {
    // The picked pill rising off the stack.
    static let liftStiffness = 380.0
    static let liftDamping = 20.0
    // The send button appearing, and a pill returning to its slot.
    static let popStiffness = 420.0
    static let popDamping = 24.0
}

/// A picked suggestion, in seconds from the tap.
private enum Pick {
    static let lift = 0.16
    static let fly = 0.3
    /// The first letter lands just before the pill has vanished into the field.
    static let typeAt = 0.4
    static let typeSpan = 0.8
    static let slowestKey = 0.045
    static let pressAt = 1.9
    static let press = 0.12
    static let clear = 0.2
}

/// The autoPlay loop, in seconds from its start.
private enum Script {
    static let rotateEvery = 1.4
    /// How long one text takes to leave and the next to arrive, and how far apart the pills start.
    static let change = 0.35
    static let stagger = 0.1
    static let rotateAt = [0.8, 2.2]
    static let pickAt = 3.2
    static let pickSlot = 2
    /// The last rotation, back to the first set.
    static let homeAt = 6.85
    static let loop = 8.4
    static let resumeAfter = 3.0
}

private enum Palette {
    static let panel = Color(hex: 0xf7f7f8)
    static let ink = Color(hex: 0x141416)
    static let mark = Color(hex: 0xe9e9ed)
    static let pillText = Color(hex: 0x55555a)
    static let pillIcon = Color(hex: 0x8e8e93)
    static let plus = Color(hex: 0x8e8e93)
    static let placeholder = Color(hex: 0x9a9aa0)
    static let caret = Color(hex: 0x2d6bff)
    static let send = Color(hex: 0x0e0e10)
    static let footnote = Color(hex: 0xa4a4aa)
    static let footnoteLink = Color(hex: 0x6e6e73)
}

/// One suggestion: an icon and a text that slides out below as the next one comes in from above.
private struct RotatingSuggestionsInputPill: View {
    let label: String
    let slot: Int
    let lifted: Bool
    /// The pill has gone into the field: its next text is set while nobody can see it.
    let away: Bool
    let reduceMotion: Bool

    private var width: Double {
        let chrome = Metrics.pillPadLeft + Metrics.iconSize + Metrics.iconGap + Metrics.pillPadRight
        return min(Metrics.fieldWidth, chrome + ceil(Metrics.textWidth(label, size: Metrics.pillFont)))
    }

    var body: some View {
        HStack(spacing: Metrics.iconGap) {
            icon.frame(width: Metrics.iconSize, height: Metrics.iconSize)
            ZStack(alignment: .leading) {
                Text(label)
                    .font(.system(size: Metrics.pillFont))
                    .foregroundStyle(Palette.pillText)
                    .lineLimit(1)
                    .fixedSize()
                    .id(label)
                    .transition(
                        reduceMotion
                            ? .opacity
                            : .asymmetric(
                                insertion: .offset(y: -Metrics.slide).combined(with: .opacity),
                                removal: .offset(y: Metrics.slide).combined(with: .opacity)
                            )
                    )
            }
        }
        .padding(.leading, Metrics.pillPadLeft)
        .frame(width: width, height: Metrics.pillHeight, alignment: .leading)
        .clipShape(Capsule())
        .background(
            Capsule().fill(.white)
                .shadow(color: .black.opacity(lifted ? 0.16 : 0.07), radius: lifted ? 10 : 3, y: lifted ? 7 : 1)
        )
        .contentShape(Capsule())
        .animation(
            away ? nil : .timingCurve(0.4, 0, 0.2, 1, duration: Script.change).delay(Double(slot) * Script.stagger),
            value: label
        )
    }

    @ViewBuilder private var icon: some View {
        switch slot % Metrics.slots {
        case 0: RotatingSuggestionsInputSparkle(points: 2).fill(Palette.pillIcon)
        case 1: RotatingSuggestionsInputBars().fill(Palette.pillIcon)
        default:
            RotatingSuggestionsInputBubble()
                .stroke(Palette.pillIcon, style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
        }
    }
}

private struct RotatingSuggestionsInputPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
    }
}

/// Solid while letters are landing; after half a second without one it blinks like the system caret.
private func rotatingSuggestionsInputBlink(idle: Double) -> Double {
    guard idle > 0.5 else { return 1 }
    let t = (idle - 0.5).truncatingRemainder(dividingBy: 1)
    if t < 0.12 { return 1 - t / 0.12 }
    if t < 0.5 { return 0 }
    if t < 0.62 { return (t - 0.5) / 0.12 }
    return 1
}

/// Four-pointed sparkles drawn in a 15 × 15 box and scaled to the frame: one large, and with
/// `points: 2` a small one at its shoulder.
private struct RotatingSuggestionsInputSparkle: Shape {
    let points: Int

    func path(in rect: CGRect) -> Path {
        var path = Path()
        if points == 1 {
            star(&path, x: 7.5, y: 7.5, r: 7)
        } else {
            star(&path, x: 6.5, y: 8.5, r: 6)
            star(&path, x: 12, y: 3.2, r: 2.8)
        }
        return path.applying(CGAffineTransform(scaleX: rect.width / 15, y: rect.height / 15))
    }

    private func star(_ path: inout Path, x: Double, y: Double, r: Double) {
        let centre = CGPoint(x: x, y: y)
        path.move(to: CGPoint(x: x, y: y - r))
        path.addQuadCurve(to: CGPoint(x: x + r, y: y), control: centre)
        path.addQuadCurve(to: CGPoint(x: x, y: y + r), control: centre)
        path.addQuadCurve(to: CGPoint(x: x - r, y: y), control: centre)
        path.addQuadCurve(to: CGPoint(x: x, y: y - r), control: centre)
        path.closeSubpath()
    }
}

/// Three rising bars in a 15 × 15 box.
private struct RotatingSuggestionsInputBars: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.addRoundedRect(in: CGRect(x: 1.8, y: 8.5, width: 2.6, height: 5), cornerSize: CGSize(width: 1.2, height: 1.2))
        path.addRoundedRect(in: CGRect(x: 6.2, y: 5.2, width: 2.6, height: 8.3), cornerSize: CGSize(width: 1.2, height: 1.2))
        path.addRoundedRect(in: CGRect(x: 10.6, y: 1.8, width: 2.6, height: 11.7), cornerSize: CGSize(width: 1.2, height: 1.2))
        return path
    }
}

/// Speech bubble outline in a 15 × 15 box.
private struct RotatingSuggestionsInputBubble: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 4.6, y: 11))
        path.addLine(to: CGPoint(x: 4.2, y: 11))
        path.addQuadCurve(to: CGPoint(x: 1.6, y: 8.4), control: CGPoint(x: 1.6, y: 11))
        path.addLine(to: CGPoint(x: 1.6, y: 4.8))
        path.addQuadCurve(to: CGPoint(x: 4.2, y: 2.2), control: CGPoint(x: 1.6, y: 2.2))
        path.addLine(to: CGPoint(x: 10.8, y: 2.2))
        path.addQuadCurve(to: CGPoint(x: 13.4, y: 4.8), control: CGPoint(x: 13.4, y: 2.2))
        path.addLine(to: CGPoint(x: 13.4, y: 8.4))
        path.addQuadCurve(to: CGPoint(x: 10.8, y: 11), control: CGPoint(x: 13.4, y: 11))
        path.addLine(to: CGPoint(x: 7.8, y: 11))
        path.addLine(to: CGPoint(x: 4.6, y: 13.6))
        path.closeSubpath()
        return path
    }
}

/// Up arrow in the 32 × 32 send button.
private struct RotatingSuggestionsInputArrow: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 16, y: 22.2))
        path.addLine(to: CGPoint(x: 16, y: 10.4))
        path.move(to: CGPoint(x: 10.8, y: 15.4))
        path.addLine(to: CGPoint(x: 16, y: 10.2))
        path.addLine(to: CGPoint(x: 21.2, y: 15.4))
        return path
    }
}

#Preview {
    ZStack {
        Color(hex: 0xe4e4e9).ignoresSafeArea()
        RotatingSuggestionsInput(autoPlay: true)
    }
}
