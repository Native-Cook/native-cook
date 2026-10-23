import SwiftUI

enum EyeWipePasswordFieldWipeMetrics {
    static let field = 64.0
    static let radius = 14.0
    static let wipeFor = 0.34
    static let wipeCurve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.55, y: 0), endControlPoint: UnitPoint(x: 0.15, y: 1))
    static let pressFor = 0.09
    static let disc = 33.0
    static let eyeInset = 43.0
    static let lockX = 33.0
    static let lock = 21.0
    static let dotsFrom = 64.0
    static let pitch = 12.5
    static let dot = 5.0
    static let fontSize = 17.0
    static let mono = "Menlo"
    static let bleed = 28.0
    static let textFrom = 0.1
    static let textTo = 0.6
    static let textBlur = 3.5
    static let eyeFrom = 0.15
    static let eyeTo = 0.85
    static let eye = 9.5
    static let lid = 5.5
    static let lashGap = 2.6
    static let lash = 3.6
    static let lashes = [[0.2, -0.5], [0.5, 0.0], [0.8, 0.5]]
    static let pupil = 2.9
    static let stroke = 2.1
    static let caretWidth = 2.0
    static let caretHeight = 22.0
    static let eyeHit = eyeInset * 2 - 16
}

private enum EyeWipePasswordFieldWipeDrawing {
    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }
    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double {
        let u = clamp01((t - from) / (to - from))
        return u * u * (3 - 2 * u)
    }

    static func quadratic(_ t: Double) -> Double {
        let u = clamp01(t)
        return u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2
    }

    static func color(_ from: Color, _ to: Color, at t: Double) -> Color {
        var fr: CGFloat = 0, fg: CGFloat = 0, fb: CGFloat = 0, fa: CGFloat = 0
        var tr: CGFloat = 0, tg: CGFloat = 0, tb: CGFloat = 0, ta: CGFloat = 0
        UIColor(from).getRed(&fr, green: &fg, blue: &fb, alpha: &fa)
        UIColor(to).getRed(&tr, green: &tg, blue: &tb, alpha: &ta)
        let u = clamp01(t)
        return Color.rgba(
            (Double(fr + (tr - fr) * u) * 255).rounded(),
            (Double(fg + (tg - fg) * u) * 255).rounded(),
            (Double(fb + (tb - fb) * u) * 255).rounded(), 1
        )
    }

    static func eye(open: Double) -> Path {
        typealias S = EyeWipePasswordFieldWipeMetrics
        let bow = 2 * S.lid * (1 - 2 * open)
        var path = Path()
        path.move(to: CGPoint(x: -S.eye, y: 0))
        path.addQuadCurve(to: CGPoint(x: S.eye, y: 0), control: CGPoint(x: 0, y: 2 * S.lid))
        path.move(to: CGPoint(x: -S.eye, y: 0))
        path.addQuadCurve(to: CGPoint(x: S.eye, y: 0), control: CGPoint(x: 0, y: bow))
        let side = 1 - 2 * open
        let reach = sqrt(abs(side))
        for lash in S.lashes {
            let t = lash[0]
            let x = -S.eye + 2 * S.eye * t
            let y = bow * 2 * t * (1 - t)
            let dx = lash[1]
            let dy = side > 0 ? 1.0 : -1.0
            let length = hypot(dx, dy)
            path.move(to: CGPoint(x: x + dx / length * S.lashGap * reach, y: y + dy / length * S.lashGap * reach))
            path.addLine(to: CGPoint(x: x + dx / length * (S.lashGap + S.lash) * reach, y: y + dy / length * (S.lashGap + S.lash) * reach))
        }
        return path
    }

    static let lock: Path = {
        var path = Path()
        path.move(to: CGPoint(x: 18, y: 8))
        path.addLine(to: CGPoint(x: 17, y: 8))
        path.addLine(to: CGPoint(x: 17, y: 6))
        path.addCurve(to: CGPoint(x: 12, y: 1), control1: CGPoint(x: 17, y: 3.24), control2: CGPoint(x: 14.76, y: 1))
        path.addCurve(to: CGPoint(x: 7, y: 6), control1: CGPoint(x: 9.24, y: 1), control2: CGPoint(x: 7, y: 3.24))
        path.addLine(to: CGPoint(x: 7, y: 8))
        path.addLine(to: CGPoint(x: 6, y: 8))
        path.addCurve(to: CGPoint(x: 4, y: 10), control1: CGPoint(x: 4.9, y: 8), control2: CGPoint(x: 4, y: 8.9))
        path.addLine(to: CGPoint(x: 4, y: 20))
        path.addCurve(to: CGPoint(x: 6, y: 22), control1: CGPoint(x: 4, y: 21.1), control2: CGPoint(x: 4.9, y: 22))
        path.addLine(to: CGPoint(x: 18, y: 22))
        path.addCurve(to: CGPoint(x: 20, y: 20), control1: CGPoint(x: 19.1, y: 22), control2: CGPoint(x: 20, y: 21.1))
        path.addLine(to: CGPoint(x: 20, y: 10))
        path.addCurve(to: CGPoint(x: 18, y: 8), control1: CGPoint(x: 20, y: 8.9), control2: CGPoint(x: 19.1, y: 8))
        path.closeSubpath()
        path.move(to: CGPoint(x: 9, y: 6))
        path.addCurve(to: CGPoint(x: 12, y: 3), control1: CGPoint(x: 9, y: 4.34), control2: CGPoint(x: 10.34, y: 3))
        path.addCurve(to: CGPoint(x: 15, y: 6), control1: CGPoint(x: 13.66, y: 3), control2: CGPoint(x: 15, y: 4.34))
        path.addLine(to: CGPoint(x: 15, y: 8))
        path.addLine(to: CGPoint(x: 9, y: 8))
        path.addLine(to: CGPoint(x: 9, y: 6))
        path.closeSubpath()
        return path
    }()

    static func caret(at date: Date, since start: Date) -> Double {
        let u = max(0, date.timeIntervalSince(start)).truncatingRemainder(dividingBy: 1)
        if u <= 0.5 { return 1 }
        if u < 0.56 { return 1 - (u - 0.5) / 0.06 }
        if u <= 0.94 { return 0 }
        return (u - 0.94) / 0.06
    }
}

/// A real keyboard input sits transparently over the canvas, keeping the drawn letters and caret
/// independent of the native field's layout while the eye wipes from disc to pill to field.
struct EyeWipePasswordFieldWipeField: View {
    var value: String
    var onChangeText: (String) -> Void
    var shown: Bool
    var onToggle: () -> Void
    var width: Double
    var held: Bool
    var onTouch: (() -> Void)?
    var maxLength: Int
    var placeholder: String
    var placeholderColor: Color
    var fill: Color
    var wipe: Color
    var dotColor: Color
    var textColor: Color
    var eyeColor: Color
    var eyeOpenColor: Color
    var focused: Binding<Bool>?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var progress: Tween
    @State private var pressFrom = 0.0
    @State private var pressTo = 0.0
    @State private var pressStart = Date.distantPast
    @State private var pressDuration = 0.0
    @State private var caretStart = Date.now
    @State private var localFocused = false
    private typealias S = EyeWipePasswordFieldWipeMetrics
    private typealias D = EyeWipePasswordFieldWipeDrawing

    init(
        value: String, onChangeText: @escaping (String) -> Void,
        shown: Bool, onToggle: @escaping () -> Void, width: Double,
        held: Bool = false, onTouch: (() -> Void)? = nil,
        maxLength: Int = 14, placeholder: String = "Password",
        placeholderColor: Color = Color(hex: 0x5d648f),
        fill: Color = Color(hex: 0x121829), wipe: Color = .white,
        dotColor: Color = Color(hex: 0x5b5fe0), textColor: Color = Color(hex: 0x5f5f9e),
        eyeColor: Color = Color(hex: 0x3b3690), eyeOpenColor: Color = Color(hex: 0x4b3fb5),
        focused: Binding<Bool>? = nil
    ) {
        self.value = value
        self.onChangeText = onChangeText
        self.shown = shown
        self.onToggle = onToggle
        self.width = width
        self.held = held
        self.onTouch = onTouch
        self.maxLength = maxLength
        self.placeholder = placeholder
        self.placeholderColor = placeholderColor
        self.fill = fill
        self.wipe = wipe
        self.dotColor = dotColor
        self.textColor = textColor
        self.eyeColor = eyeColor
        self.eyeOpenColor = eyeOpenColor
        self.focused = focused
        _progress = State(initialValue: Tween(shown ? 1 : 0))
    }

    private var focus: Binding<Bool> { focused ?? $localFocused }

    var body: some View {
        TimelineView(.animation) { timeline in
            let p = progress.value(at: timeline.date)
            let pressed = pressed(at: timeline.date)
            let resolved = D.smooth(p, S.textFrom, S.textTo)
            ZStack(alignment: .topLeading) {
                background(progress: p, pressed: pressed, resolved: resolved)
                    .frame(width: width + S.bleed * 2, height: S.field + S.bleed * 2)
                    .offset(x: -S.bleed, y: -S.bleed)

                ForEach(Array(value.utf16.enumerated()), id: \.offset) { index, character in
                    Text(String(decoding: CollectionOfOne(character), as: UTF16.self))
                        .font(.custom(S.mono, size: S.fontSize).weight(.medium))
                        .foregroundStyle(textColor)
                        .blur(radius: S.textBlur * (1 - resolved))
                        .opacity(resolved)
                        .position(x: S.dotsFrom + Double(index) * S.pitch, y: S.field / 2)
                }

                eye(progress: p, pressed: pressed)
                    .frame(width: width, height: S.field)

                EyeWipePasswordFieldInput(
                    value: Binding(get: { value }, set: onChangeText), focused: focus,
                    placeholder: placeholder, placeholderColor: placeholderColor,
                    ink: .clear, tint: .clear, hidden: true, keyboard: .default,
                    maxLength: maxLength, onTouch: { onTouch?() }, onChange: nil
                )
                .frame(width: max(0, width - S.eyeHit), height: S.field)

                if focus.wrappedValue {
                    RoundedRectangle(cornerRadius: 1)
                        .fill(dotColor)
                        .frame(width: S.caretWidth, height: S.caretHeight)
                        .offset(x: S.dotsFrom + Double(value.utf16.count) * S.pitch - S.pitch / 2 - 1, y: S.field / 2 - 11)
                        .opacity(D.caret(at: timeline.date, since: caretStart))
                        .allowsHitTesting(false)
                }

                Button(action: onToggle) {
                    Color.clear.frame(width: S.eyeHit, height: S.field).contentShape(Rectangle())
                }
                .buttonStyle(EyeStyle(
                    onDown: {
                        onTouch?()
                        retargetPress(1, duration: S.pressFor)
                    },
                    onUp: { retargetPress(0, duration: S.pressFor * 2) }
                ))
                .offset(x: width - S.eyeHit)
                .accessibilityLabel(shown ? "Hide password" : "Show password")
            }
            // The stack is as large as its background, which bleeds past the field on every side.
            .frame(width: width, height: S.field, alignment: .topLeading)
        }
        .frame(width: width, height: S.field)
        .onChange(of: shown) { _, value in
            progress.retarget(value ? 1 : 0, duration: reduceMotion ? 0 : S.wipeFor, curve: S.wipeCurve)
        }
        .onChange(of: held) { _, value in retargetPress(value ? 1 : 0, duration: S.pressFor) }
        .onChange(of: focus.wrappedValue) { _, value in if value { caretStart = .now } }
        .onChange(of: reduceMotion) { _, value in
            if value {
                progress.retarget(shown ? 1 : 0, duration: 0, curve: S.wipeCurve)
                retargetPress(pressTo, duration: 0)
            }
        }
    }

    private func pressed(at date: Date) -> Double {
        guard pressDuration > 0 else { return pressTo }
        let u = D.quadratic(date.timeIntervalSince(pressStart) / pressDuration)
        return pressFrom + (pressTo - pressFrom) * u
    }

    private func retargetPress(_ target: Double, duration: Double) {
        let now = Date.now
        pressFrom = pressed(at: now)
        pressTo = target
        pressStart = now
        pressDuration = reduceMotion ? 0 : duration
    }

    private func background(progress p: Double, pressed: Double, resolved: Double) -> some View {
        Canvas { context, _ in
            var field = context
            field.translateBy(x: S.bleed, y: S.bleed)
            let disc = S.disc * (1 - 0.08 * pressed * (1 - p))
            let left = (width - S.eyeInset - disc / 2) * (1 - p)
            let right = width - (S.eyeInset - disc / 2) * (1 - p)
            let height = disc + (S.field - disc) * p
            let radius = height / 2 + (S.radius - height / 2) * D.smooth(p, 0.75, 1)
            let flood = RoundedRectangle(cornerRadius: radius, style: .circular)
                .path(in: CGRect(x: left, y: (S.field - height) / 2, width: right - left, height: height))
            let full = RoundedRectangle(cornerRadius: S.radius, style: .circular)
                .path(in: CGRect(x: 0, y: 0, width: width, height: S.field))
            var shadow = field
            shadow.opacity *= D.smooth(p, 0.5, 1)
            shadow.addFilter(.blur(radius: 12))
            shadow.fill(
                RoundedRectangle(cornerRadius: S.radius, style: .circular).path(in: CGRect(x: 4, y: 10, width: width - 8, height: S.field - 6)),
                with: .color(Color.rgba(70, 60, 190, 0.3))
            )
            field.fill(full, with: .color(fill))
            let lock = D.lock.applying(
                CGAffineTransform(scaleX: S.lock / 24, y: S.lock / 24)
                    .concatenating(CGAffineTransform(translationX: S.lockX - S.lock / 2, y: S.field / 2 - S.lock / 2 - 1))
            )
            field.fill(lock, with: .color(.white))
            field.fill(flood, with: .color(wipe))
            var clipped = field
            clipped.clip(to: flood)
            clipped.fill(lock, with: .color(fill))
            var dots = field
            dots.opacity *= 1 - resolved
            dots.addFilter(.blur(radius: S.textBlur * resolved))
            for index in 0..<value.utf16.count {
                let x = S.dotsFrom + Double(index) * S.pitch
                dots.fill(Path(ellipseIn: CGRect(x: x - S.dot / 2, y: S.field / 2 - S.dot / 2, width: S.dot, height: S.dot)), with: .color(dotColor))
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func eye(progress: Double, pressed: Double) -> some View {
        Canvas { context, _ in
            let open = D.smooth(progress, S.eyeFrom, S.eyeTo)
            let tint = D.color(eyeColor, eyeOpenColor, at: open)
            var eye = context
            eye.translateBy(x: width - S.eyeInset, y: S.field / 2 - 2.5 * (1 - open))
            eye.scaleBy(x: 1 - 0.08 * pressed, y: 1 - 0.08 * pressed)
            eye.stroke(D.eye(open: open), with: .color(tint), style: StrokeStyle(lineWidth: S.stroke, lineCap: .round, lineJoin: .round))
            let pupil = S.pupil * D.smooth(open, 0.45, 1)
            eye.fill(Path(ellipseIn: CGRect(x: -pupil, y: -pupil, width: pupil * 2, height: pupil * 2)), with: .color(tint))
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private struct EyeStyle: ButtonStyle {
        let onDown: () -> Void
        let onUp: () -> Void
        func makeBody(configuration: Configuration) -> some View {
            configuration.label.onChange(of: configuration.isPressed) { _, pressed in
                if pressed { onDown() } else { onUp() }
            }
        }
    }
}

/// The native input supplies editing only; hiding its caret and menu leaves the canvas in charge
/// of the password's drawn value. No first responder is requested by the self-playing loop.
struct EyeWipePasswordFieldInput: UIViewRepresentable {
    @Binding var value: String
    @Binding var focused: Bool
    let placeholder: String
    let placeholderColor: Color
    let ink: Color
    let tint: Color
    let hidden: Bool
    let keyboard: UIKeyboardType
    let maxLength: Int?
    let onTouch: () -> Void
    let onChange: ((String) -> Void)?

    func makeUIView(context: Context) -> EyeWipePasswordFieldTextInput {
        let input = EyeWipePasswordFieldTextInput()
        input.delegate = context.coordinator
        input.addTarget(context.coordinator, action: #selector(EyeWipePasswordFieldInputCoordinator.changed(_:)), for: .editingChanged)
        input.autocapitalizationType = .none
        input.autocorrectionType = .no
        input.spellCheckingType = .no
        input.textContentType = nil
        input.borderStyle = .none
        input.backgroundColor = .clear
        input.font = .systemFont(ofSize: EyeWipePasswordFieldWipeMetrics.fontSize)
        input.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return input
    }

    func updateUIView(_ input: EyeWipePasswordFieldTextInput, context: Context) {
        context.coordinator.parent = self
        if input.text != value { input.text = value }
        input.textColor = UIColor(ink)
        input.tintColor = UIColor(tint)
        input.drawn = hidden
        input.leftInset = hidden ? EyeWipePasswordFieldWipeMetrics.dotsFrom : 0
        input.onTouch = onTouch
        input.keyboardType = keyboard
        input.accessibilityLabel = placeholder
        input.attributedPlaceholder = NSAttributedString(string: placeholder, attributes: [.foregroundColor: UIColor(placeholderColor)])
        if !focused && input.isFirstResponder { input.resignFirstResponder() }
    }

    func makeCoordinator() -> EyeWipePasswordFieldInputCoordinator { EyeWipePasswordFieldInputCoordinator(self) }

    final class EyeWipePasswordFieldInputCoordinator: NSObject, UITextFieldDelegate {
        var parent: EyeWipePasswordFieldInput
        init(_ parent: EyeWipePasswordFieldInput) { self.parent = parent }

        @objc func changed(_ input: UITextField) {
            let next = input.text ?? ""
            parent.value = next
            parent.onChange?(next)
        }

        func textFieldDidBeginEditing(_ textField: UITextField) { parent.focused = true }
        func textFieldDidEndEditing(_ textField: UITextField) { parent.focused = false }
        func textFieldShouldReturn(_ textField: UITextField) -> Bool {
            textField.resignFirstResponder()
            return true
        }

        func textField(_ textField: UITextField, shouldChangeCharactersIn range: NSRange, replacementString string: String) -> Bool {
            guard let maxLength = parent.maxLength else { return true }
            let proposed = ((textField.text ?? "") as NSString).replacingCharacters(in: range, with: string)
            return proposed.utf16.count <= maxLength
        }
    }
}

final class EyeWipePasswordFieldTextInput: UITextField {
    var drawn = false
    var leftInset = 0.0
    var onTouch: (() -> Void)?

    override func textRect(forBounds bounds: CGRect) -> CGRect {
        super.textRect(forBounds: inset(bounds))
    }

    override func editingRect(forBounds bounds: CGRect) -> CGRect {
        super.editingRect(forBounds: inset(bounds))
    }

    override func placeholderRect(forBounds bounds: CGRect) -> CGRect {
        super.placeholderRect(forBounds: inset(bounds))
    }

    private func inset(_ bounds: CGRect) -> CGRect {
        CGRect(x: bounds.minX + leftInset, y: bounds.minY, width: max(0, bounds.width - leftInset), height: bounds.height)
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        onTouch?()
        super.touchesBegan(touches, with: event)
    }

    override func caretRect(for position: UITextPosition) -> CGRect {
        drawn ? .zero : super.caretRect(for: position)
    }

    override func selectionRects(for range: UITextRange) -> [UITextSelectionRect] {
        drawn ? [] : super.selectionRects(for: range)
    }

    override func canPerformAction(_ action: Selector, withSender sender: Any?) -> Bool {
        drawn ? false : super.canPerformAction(action, withSender: sender)
    }
}
