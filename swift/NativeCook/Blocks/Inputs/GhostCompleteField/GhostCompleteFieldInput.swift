import SwiftUI
import UIKit

/// What a person's typing does to one field: the tweens behind its pose, and its layout.
@MainActor
final class GhostCompleteFieldController {
    typealias M = GhostCompleteFieldMotion
    typealias L = GhostCompleteFieldLayout
    typealias Tw = GhostCompleteFieldTween

    let font: UIFont
    let width: Double
    let lineHeight: Double
    let multiline: Bool
    let keycapEnabled: Bool
    let suggests: Bool
    var still = false
    var onChange: ((String) -> Void)?
    /// Writes to the real input.
    var write: ((String) -> Void)?

    private(set) var typed = ""
    private(set) var pending = ""
    private(set) var layout = L.empty
    private var focused = false
    private var timer: DispatchWorkItem?

    private var n = 0
    private var run = -1
    private var appearFrom = 0.0
    private var caretFrom = 0, caretTo = 0
    private var blinkFrom: Double?
    var fade = Tw(1), sweep = Tw(), caretMix = Tw(1), caretFade = Tw(), keycapPop = Tw(), keycapPress = Tw(), ink = Tw(1)

    init(font: UIFont, width: Double, lineHeight: Double, multiline: Bool, keycap: Bool, suggests: Bool) {
        self.font = font
        self.width = width
        self.lineHeight = lineHeight
        self.multiline = multiline
        self.keycapEnabled = keycap
        self.suggests = suggests
    }

    private var now: Double { Date().timeIntervalSinceReferenceDate }

    func pose(_ at: Double) -> M.Pose {
        var p = M.Pose()
        p.n = n
        p.run = run
        p.appear = still ? 9 : at - appearFrom
        p.fade = fade.value(at)
        p.sweep = sweep.value(at)
        p.caretFrom = caretFrom
        p.caretTo = caretTo
        p.caretMix = caretMix.value(at)
        if let from = blinkFrom {
            let phase = (at - from).truncatingRemainder(dividingBy: 1.14)
            if phase < 0.5 { p.caret = 1 }
            else if phase < 0.64 { p.caret = 1 - (phase - 0.5) / 0.14 }
            else if phase < 1.0 { p.caret = 0 }
            else { p.caret = (phase - 1.0) / 0.14 }
        } else {
            p.caret = caretFade.value(at)
        }
        p.keycap = keycapPop.value(at)
        p.keycapPress = keycapPress.value(at)
        p.ink = ink.value(at)
        p.hint = n == 0 ? 1 : 1 - p.ink
        return p
    }

    private func kickCaret(_ on: Bool) {
        if on {
            if still {
                blinkFrom = nil
                caretFade.set(1)
            } else {
                blinkFrom = now
            }
        } else {
            if blinkFrom != nil { caretFade.set(1) }
            blinkFrom = nil
            caretFade.to(0, 0.1, now: now)
        }
    }

    private func stopTimer() {
        timer?.cancel()
        timer = nil
    }

    private func arm(_ delay: Double = GhostCompleteFieldMotion.debounceS) {
        stopTimer()
        guard suggests else { return }
        let item = DispatchWorkItem { [weak self] in
            MainActor.assumeIsolated { self?.propose() }
        }
        timer = item
        DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: item)
    }

    private func propose() {
        timer = nil
        guard pending.isEmpty, let rest = L.suggestPhrase(typed) else { return }
        offer(rest)
    }

    @discardableResult
    private func lay(_ text: String, _ rest: String) -> L {
        let length = text.utf16.count
        let ranges: [(Int, Int)] = rest.isEmpty ? [] : [(length, length + rest.utf16.count)]
        let next = L(font: font, text: text + rest, ghosts: ranges, width: width, lineHeight: lineHeight, keycap: keycapEnabled)
        layout = next
        n = length
        caretFrom = length
        caretTo = length
        caretMix.set(1)
        sweep.set(0)
        return next
    }

    /// Puts a suggestion behind the caret. `quiet` skips the blur-in and the keycap (a picked chip).
    func offer(_ rest: String, quiet: Bool = false) {
        let clean = L.cleanText(rest, multiline: multiline)
        guard !clean.isEmpty else { return }
        pending = clean
        let next = lay(typed, clean)
        run = 0
        fade.set(1)
        appearFrom = quiet || still ? now - 9 : now
        if quiet {
            keycapPop.set(0)
        } else {
            let words = Double(next.runs.first?.words.count ?? 1)
            keycapPop.set(0)
            keycapPop.to(1, still ? 0 : M.keycapS, .outBack(1.6), now: now, delay: still ? 0 : words * M.wordGap + 0.12)
        }
    }

    func textChanged(_ raw: String) {
        let v = L.cleanText(raw, multiline: multiline)
        let prev = typed
        let rest = pending
        typed = v
        onChange?(v)
        stopTimer()
        if focused { kickCaret(true) }

        let added = v.utf16.count > prev.utf16.count && v.hasPrefix(prev) ? String(v.dropFirst(prev.count)) : ""
        if !rest.isEmpty && !added.isEmpty && rest.hasPrefix(added) {
            // Typing the suggested letters: the layout already has them, the ink just advances.
            pending = String(rest.dropFirst(added.count))
            n = v.utf16.count
            caretFrom = n
            caretTo = n
            caretMix.set(1)
            if pending.isEmpty {
                run = -1
                keycapPop.set(0)
                arm()
            }
            return
        }
        if !rest.isEmpty {
            // Anything else rejects it: it dissolves where the text now puts it.
            pending = ""
            lay(v, rest)
            fade.to(0, still ? 0 : M.dissolveS, now: now)
            keycapPop.to(0, 0.1, now: now)
        } else {
            lay(v, "")
            run = -1
        }
        arm()
    }

    /// Takes the suggestion. `velocity` (pt/s) is the swipe's and shortens the sweep.
    func accept(velocity: Double = 0, fresh: Bool = false) {
        let rest = pending
        guard !rest.isEmpty else { return }
        stopTimer()
        let from = typed.utf16.count
        let full = typed + rest
        typed = full
        pending = ""
        write?(full)
        onChange?(full)
        let at = now
        let left = fresh ? 1 : 1 - M.clamp01(sweep.value(at))
        let duration = still ? 0 : max(0.16, (M.sweepS * left) / (1 + max(0, velocity) / 1200))
        sweep.to(1, duration, .inOutCubic, now: at)
        caretFrom = from
        caretTo = full.utf16.count
        if still {
            caretMix.set(1)
        } else {
            caretMix.set(0)
            caretMix.to(1, M.jumpS, .outBack(M.jumpBack), now: at)
        }
        keycapPop.to(0, 0.14, now: at)
        if focused { kickCaret(true) }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        arm(duration + M.debounceS)
    }

    /// Replaces the text from outside (the loop handing over, or a reset with "").
    func adopt(_ text: String, thenSuggest: Bool = true) {
        stopTimer()
        let v = L.cleanText(text, multiline: multiline)
        typed = v
        pending = ""
        write?(v)
        lay(v, "")
        run = -1
        fade.set(1)
        keycapPop.set(0)
        ink.set(1)
        if thenSuggest && !v.isEmpty { arm() }
    }

    func didFocus() {
        focused = true
        kickCaret(true)
    }

    func didBlur() {
        focused = false
        kickCaret(false)
    }

    func press(_ down: Bool) {
        keycapPress.to(down ? 1 : 0, down ? 0.06 : 0.14, now: now)
    }

    // MARK: Swipe right to accept

    private var swiping = false
    private var reach = M.swipeDist

    func panBegan() {
        let at = now
        let on = run == 0 && !layout.runs.isEmpty && fade.value(at) > 0.5 && sweep.value(at) < 0.02
        swiping = on
        if on { reach = max(60, min(M.swipeDist, layout.runs[0].length)) }
    }

    func panChanged(_ dx: Double) {
        guard swiping else { return }
        sweep.set(min(0.94, max(0, dx / reach)))
    }

    func panEnded(velocity: Double, cancelled: Bool) {
        guard swiping else { return }
        swiping = false
        let at = now
        if cancelled {
            sweep.to(0, 0.2, .outCubic, now: at)
        } else if sweep.value(at) > M.swipeAccept || velocity > 600 {
            accept(velocity: velocity)
        } else {
            sweep.springTo(0, velocity: -velocity / reach, stiffness: 260, damping: 22, now: at)
        }
    }
}

// MARK: - Real inputs

/// The body's real text view: invisible, it only takes the keyboard and the touches.
struct GhostCompleteFieldBodyInput: UIViewRepresentable {
    let field: GhostCompleteFieldController
    let onTouch: () -> Void
    let onFocus: () -> Void
    let onBlur: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> UITextView {
        let view = UITextView()
        view.backgroundColor = .clear
        view.textColor = .clear
        view.tintColor = .clear
        view.font = field.font
        view.isScrollEnabled = false
        view.textContainerInset = .zero
        view.textContainer.lineFragmentPadding = 0
        view.autocorrectionType = .no
        view.spellCheckingType = .no
        view.accessibilityLabel = "Write your reply"
        view.delegate = context.coordinator
        let pan = UIPanGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.pan(_:)))
        pan.delegate = context.coordinator
        view.addGestureRecognizer(pan)
        field.write = { [weak view] text in
            guard let view, view.text != text else { return }
            view.text = text
        }
        return view
    }

    func updateUIView(_ view: UITextView, context: Context) {
        context.coordinator.parent = self
    }

    @MainActor
    final class Coordinator: NSObject, UITextViewDelegate, UIGestureRecognizerDelegate {
        var parent: GhostCompleteFieldBodyInput

        init(_ parent: GhostCompleteFieldBodyInput) { self.parent = parent }

        func textViewDidBeginEditing(_ textView: UITextView) {
            parent.onTouch()
            parent.field.didFocus()
            parent.onFocus()
        }

        func textViewDidEndEditing(_ textView: UITextView) {
            parent.field.didBlur()
            parent.onBlur()
        }

        func textViewDidChange(_ textView: UITextView) {
            parent.field.textChanged(textView.text ?? "")
        }

        func textView(_ textView: UITextView, shouldChangeTextIn range: NSRange, replacementText text: String) -> Bool {
            let old = (textView.text ?? "") as NSString
            return old.replacingCharacters(in: range, with: text).utf16.count <= 220
        }

        func gestureRecognizerShouldBegin(_ recognizer: UIGestureRecognizer) -> Bool {
            guard let pan = recognizer as? UIPanGestureRecognizer else { return true }
            let d = pan.translation(in: pan.view)
            return d.x > 0 && abs(d.x) > abs(d.y)
        }

        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { false }

        @objc func pan(_ pan: UIPanGestureRecognizer) {
            let dx = Double(pan.translation(in: pan.view).x)
            switch pan.state {
            case .began:
                parent.onTouch()
                parent.field.panBegan()
                parent.field.panChanged(dx)
            case .changed:
                parent.field.panChanged(dx)
            case .ended:
                parent.field.panEnded(velocity: Double(pan.velocity(in: pan.view).x), cancelled: false)
            case .cancelled, .failed:
                parent.field.panEnded(velocity: 0, cancelled: true)
            default:
                break
            }
        }
    }
}

/// A single-line real field: the invisible Cc address, or the visible Subject.
struct GhostCompleteFieldLineInput: UIViewRepresentable {
    enum Kind { case cc, subject }

    let kind: Kind
    let field: GhostCompleteFieldController?
    let register: (UITextField) -> Void
    let onTouch: () -> Void
    let onFocus: () -> Void
    let onBlur: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> UITextField {
        let view = UITextField()
        view.backgroundColor = .clear
        view.autocorrectionType = .no
        view.spellCheckingType = .no
        view.delegate = context.coordinator
        view.returnKeyType = .next
        view.setContentHuggingPriority(.defaultLow, for: .horizontal)
        view.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        view.addTarget(context.coordinator, action: #selector(Coordinator.changed(_:)), for: .editingChanged)
        switch kind {
        case .cc:
            view.font = field?.font
            view.textColor = .clear
            view.tintColor = .clear
            view.keyboardType = .emailAddress
            view.autocapitalizationType = .none
            view.textContentType = .emailAddress
            view.accessibilityLabel = "Add people"
            field?.write = { [weak view] text in
                guard let view, view.text != text else { return }
                view.text = text
            }
        case .subject:
            view.attributedText = NSAttributedString(string: GhostCompleteFieldModel.subject, attributes: Self.subjectAttributes)
            view.defaultTextAttributes = Self.subjectAttributes
            view.tintColor = UIColor(red: 0xD9 / 255, green: 0x47 / 255, blue: 0x2B / 255, alpha: 1)
            view.accessibilityLabel = "Subject"
        }
        register(view)
        return view
    }

    static let subjectAttributes: [NSAttributedString.Key: Any] = [
        .font: UIFont.systemFont(ofSize: 17, weight: .semibold),
        .foregroundColor: UIColor(red: 0x0E / 255, green: 0x12 / 255, blue: 0x1A / 255, alpha: 1),
        .kern: -0.2,
    ]

    func updateUIView(_ view: UITextField, context: Context) {
        context.coordinator.parent = self
    }

    @MainActor
    final class Coordinator: NSObject, UITextFieldDelegate {
        var parent: GhostCompleteFieldLineInput

        init(_ parent: GhostCompleteFieldLineInput) { self.parent = parent }

        @objc func changed(_ view: UITextField) {
            if let field = parent.field {
                field.textChanged(view.text ?? "")
            } else {
                parent.onTouch()
            }
        }

        func textFieldDidBeginEditing(_ textField: UITextField) {
            parent.onTouch()
            parent.field?.didFocus()
            parent.onFocus()
        }

        func textFieldDidEndEditing(_ textField: UITextField) {
            parent.field?.didBlur()
            parent.onBlur()
        }

        func textFieldShouldReturn(_ textField: UITextField) -> Bool {
            textField.resignFirstResponder()
            return false
        }

        func textField(_ view: UITextField, shouldChangeCharactersIn range: NSRange, replacementString string: String) -> Bool {
            guard parent.kind == .cc else { return true }
            let old = (view.text ?? "") as NSString
            return old.replacingCharacters(in: range, with: string).utf16.count <= 36
        }
    }
}

// MARK: - What the field draws

struct GhostCompleteFieldDrawing: View {
    typealias M = GhostCompleteFieldMotion
    typealias L = GhostCompleteFieldLayout

    let pose: M.Pose
    let layout: L
    let font: UIFont
    let width: CGFloat
    let height: CGFloat
    let lineHeight: CGFloat
    let placeholder: String
    let ghost: GhostCompleteFieldRGB

    static let ink = GhostCompleteFieldRGB(0x0E121A)
    static let hint = GhostCompleteFieldRGB(0xA3A9B6)
    static let accent = GhostCompleteFieldRGB(0xD9472B)
    static let front = GhostCompleteFieldRGB(0xE0714F)

    private let pad: CGFloat = 10

    var body: some View {
        let p = pose
        let L = layout
        let caretH = min(lineHeight, CTFontGetAscent(font as CTFont) + CTFontGetDescent(font as CTFont) + 2)
        let stops: [Gradient.Stop] = [
            .init(color: Self.ink.color, location: 0),
            .init(color: Self.accent.color, location: 0.3),
            .init(color: Self.front.color, location: 0.72),
            .init(color: ghost.color, location: 1),
        ]

        return ZStack(alignment: .topLeading) {
            Text(placeholder)
                .font(Font(font))
                .foregroundStyle(Self.hint.color)
                .lineLimit(1)
                .frame(height: lineHeight)
                .opacity(p.hint)

            Canvas { ctx, _ in
                ctx.translateBy(x: pad, y: pad)
                var text = ctx
                text.opacity = p.ink
                if p.n > 0 {
                    text.fill(L.path(0, p.n), with: .color(Self.ink.color))
                }
                if p.run >= 0 && p.run < L.runs.count {
                    let run = L.runs[p.run]
                    for (slot, word) in run.words.enumerated() {
                        let arrived = M.easeOutCubic(M.clamp01((p.appear - Double(slot) * M.wordGap) / M.wordS))
                        let opacity = arrived * p.fade
                        if opacity <= 0.001 { continue }
                        let blur = M.wordBlur * (1 - arrived) + M.dissolveBlur * (1 - p.fade)
                        let front = word.x0 + p.sweep * (run.length + M.sweepBand) - word.off
                        let glyphs = L.path(max(word.a, p.n), word.b)
                        let shading = GraphicsContext.Shading.linearGradient(
                            Gradient(stops: stops),
                            startPoint: CGPoint(x: front - M.sweepBand, y: 0),
                            endPoint: CGPoint(x: front, y: 0)
                        )
                        if blur > 0.05 {
                            text.drawLayer { layer in
                                layer.opacity = opacity
                                layer.addFilter(.blur(radius: blur))
                                layer.fill(glyphs, with: shading)
                            }
                        } else {
                            var layer = text
                            layer.opacity = p.ink * opacity
                            layer.fill(glyphs, with: shading)
                        }
                    }
                }

                let caret = p.caret * p.ink
                if caret > 0.001 {
                    let last = L.cx.count - 1
                    let from = min(last, max(0, p.caretFrom))
                    let to = min(last, max(0, p.caretTo))
                    let x = L.cx[from] + (L.cx[to] - L.cx[from]) * p.caretMix - 1
                    let y = L.cy[from] + (L.cy[to] - L.cy[from]) * M.clamp01(p.caretMix) + (lineHeight - caretH) / 2
                    var c = ctx
                    c.opacity = caret
                    c.fill(Path(roundedRect: CGRect(x: x, y: y, width: 2, height: caretH), cornerRadius: 1),
                           with: .color(Self.accent.color))
                }
            }
            .frame(width: width + pad * 2, height: height + pad * 2)
            .offset(x: -pad, y: -pad)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .allowsHitTesting(false)
    }
}

/// The Tab keycap beside a suggestion: a white face on a darker slab, with real key travel.
struct GhostCompleteFieldKeycap: View {
    let pose: GhostCompleteFieldMotion.Pose
    let layout: GhostCompleteFieldLayout
    let lineHeight: CGFloat
    let onPress: () -> Void
    let onDown: (Bool) -> Void

    private struct Style: ButtonStyle {
        let onDown: (Bool) -> Void
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .onChange(of: configuration.isPressed) { _, down in onDown(down) }
        }
    }

    var body: some View {
        let p = pose
        let run = p.run >= 0 && p.run < layout.runs.count ? layout.runs[p.run] : nil
        let shown = run != nil && p.keycap > 0.01
        let w = CGFloat(GhostCompleteFieldLayout.keycapW)
        let shape = RoundedRectangle(cornerRadius: 8, style: .circular)
        let shade = Color(.sRGB, red: 21 / 255, green: 26 / 255, blue: 36 / 255)

        return Group {
            if let run, shown {
                Button(action: onPress) {
                    ZStack(alignment: .topLeading) {
                        shape
                            .fill(Color(.sRGB, red: 0xC9 / 255, green: 0xCD / 255, blue: 0xD6 / 255))
                            .shadow(color: shade.opacity(0.12), radius: 5, y: 4)
                            .frame(width: w, height: 26)
                            .offset(y: 2)
                            .opacity(1 - 0.7 * p.keycapPress)
                        ZStack {
                            shape.fill(Color.white)
                            shape.strokeBorder(Color(.sRGB, red: 0xD5 / 255, green: 0xD8 / 255, blue: 0xDF / 255), lineWidth: 1 / 3)
                            HStack(spacing: 5) {
                                Text("Tab")
                                    .font(.system(size: 14, weight: .semibold))
                                    .kerning(0.1)
                                    .foregroundStyle(Color(.sRGB, red: 0x5B / 255, green: 0x61 / 255, blue: 0x70 / 255))
                                Text("\u{2192}")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(GhostCompleteFieldDrawing.accent.color)
                                    .offset(y: -1)
                            }
                        }
                        .frame(width: w, height: 26)
                        .offset(y: 1.5 * p.keycapPress)
                    }
                    .frame(width: w, height: 28, alignment: .topLeading)
                    .contentShape(Rectangle().inset(by: -8))
                }
                .buttonStyle(Style(onDown: onDown))
                .accessibilityLabel("Accept suggestion")
                .scaleEffect(0.7 + 0.3 * p.keycap)
                .opacity(GhostCompleteFieldMotion.clamp01(p.keycap * 2) * p.fade * p.ink)
                .offset(x: run.chipX, y: run.chipY + (lineHeight - 26) / 2)
            }
        }
    }
}
