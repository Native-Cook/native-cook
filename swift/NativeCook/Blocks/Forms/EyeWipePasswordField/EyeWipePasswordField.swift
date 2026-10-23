import SwiftUI

private enum EyeWipePasswordFieldMetrics {
    static let margin = 32.0
    static let maxWidth = 400.0
    static let gap = 14.0
    static let button = 58.0
    static let icon = 21.0
    static let titleSize = 32.0
    static let titleHeight = 38.0
    static let subtitleSize = 16.0
    static let subtitleHeight = 22.0
    static let subtitleLeading = subtitleHeight - UIFont.systemFont(ofSize: subtitleSize).lineHeight
    static let subtitleTop = 8.0
    static let subtitleBottom = 32.0
    static let buttonSize = 17.0
    static let buttonTextHeight = 22.0
    static let forgotSize = 15.0
    static let forgotHeight = 20.0
    static let forgotTop = 20.0
    static let buttonPressFor = 0.12
    static let bottomInset = 72.0
    static let emailInk = Color(hex: 0xaeb4e6)
    static let placeholder = Color(hex: 0x5d648f)
}

private enum EyeWipePasswordFieldMotion {
    static let loop = 6.0
    static let showAt = 0.8
    static let hideAt = 3.5
    static let pressFor = 0.12
    static let userHold = 3.0
}

/// A sign-in form whose eye floods a dark password field white and resolves dots into letters.
struct EyeWipePasswordField: View {
    var autoPlay: Bool
    var email: String
    var password: String
    var minLength: Int
    var title: String
    var subtitle: String
    var emailPlaceholder: String
    var submitLabel: String
    var forgotLabel: String
    var background: Color
    var fieldColor: Color
    var accent: Color
    var onToggle: ((Bool) -> Void)?
    var onSubmit: ((String, String) -> Void)?
    var onForgot: (() -> Void)?

    @State private var emailValue: String
    @State private var passwordValue: String
    @State private var shown = false
    @State private var held = false
    @State private var touches = 0
    @State private var emailFocused = false
    @State private var passwordFocused = false
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)

    private typealias S = EyeWipePasswordFieldMetrics
    private typealias M = EyeWipePasswordFieldMotion
    private typealias F = EyeWipePasswordFieldWipeMetrics

    init(
        autoPlay: Bool = false,
        email: String = "alex@example.com",
        password: String = "91u2dhjk",
        minLength: Int = 6,
        title: String = "Welcome back",
        subtitle: String = "Sign in to pick up where you left off.",
        emailPlaceholder: String = "Email",
        submitLabel: String = "Sign in",
        forgotLabel: String = "Forgot password?",
        background: Color = Color(hex: 0x6fa0ee),
        fieldColor: Color = Color(hex: 0x121829),
        accent: Color = Color(hex: 0x3b3690),
        onToggle: ((Bool) -> Void)? = nil,
        onSubmit: ((String, String) -> Void)? = nil,
        onForgot: (() -> Void)? = nil
    ) {
        self.autoPlay = autoPlay
        self.email = email
        self.password = password
        self.minLength = minLength
        self.title = title
        self.subtitle = subtitle
        self.emailPlaceholder = emailPlaceholder
        self.submitLabel = submitLabel
        self.forgotLabel = forgotLabel
        self.background = background
        self.fieldColor = fieldColor
        self.accent = accent
        self.onToggle = onToggle
        self.onSubmit = onSubmit
        self.onForgot = onForgot
        _emailValue = State(initialValue: email)
        _passwordValue = State(initialValue: password)
    }

    private var trimmedEmail: String { emailValue.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var valid: Bool {
        trimmedEmail.range(of: #"^[^\s@]+@[^\s@]+\.[^\s@]+$"#, options: .regularExpression) != nil && passwordValue.utf16.count >= minLength
    }

    var body: some View {
        GeometryReader { proxy in
            let width = min(proxy.size.width - S.margin * 2, S.maxWidth)
            ZStack {
                background.ignoresSafeArea()
                    .contentShape(Rectangle())
                    .onTapGesture {
                        touched()
                        dismissKeyboard()
                    }
                form(width: width)
                    .frame(width: width)
                    // The safe-area content is centred with 72 more points below than above.
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(.bottom, S.bottomInset)
            }
        }
        .preferredColorScheme(.light)
        .ignoresSafeArea(.keyboard)
        .onAppear { tapHaptic.prepare() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    private func form(width: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title)
                .font(.system(size: S.titleSize, weight: .bold))
                .tracking(-0.6)
                .foregroundStyle(fieldColor)
                .lineLimit(1)
                .frame(height: S.titleHeight)
            Text(subtitle)
                .font(.system(size: S.subtitleSize))
                .foregroundStyle(fieldColor.opacity(0.62))
                .lineSpacing(S.subtitleLeading)
                .padding(.vertical, S.subtitleLeading / 2)
                .lineLimit(2)
                .padding(.top, S.subtitleTop)
                .padding(.bottom, S.subtitleBottom)

            HStack(spacing: F.dotsFrom - F.lockX - S.icon / 2 - 3) {
                EyeWipePasswordFieldMail()
                    .fill(.white)
                    .frame(width: S.icon, height: S.icon)
                    .padding(.leading, F.lockX - S.icon / 2)
                EyeWipePasswordFieldInput(
                    value: $emailValue, focused: $emailFocused,
                    placeholder: emailPlaceholder, placeholderColor: S.placeholder,
                    ink: S.emailInk, tint: S.emailInk, hidden: false,
                    keyboard: .emailAddress, maxLength: nil,
                    onTouch: touched, onChange: { _ in touched() }
                )
                .frame(height: F.field)
                .padding(.trailing, 20)
            }
            .frame(height: F.field)
            .background(RoundedRectangle(cornerRadius: F.radius, style: .continuous).fill(fieldColor))
            .padding(.bottom, S.gap)

            EyeWipePasswordFieldWipeField(
                value: passwordValue,
                onChangeText: { next in
                    touched()
                    passwordValue = next
                },
                shown: shown, onToggle: { toggle(auto: false) }, width: width,
                held: held, onTouch: touched, fill: fieldColor,
                focused: $passwordFocused
            )

            Button {
                touched()
                tapHaptic.impactOccurred()
                dismissKeyboard()
                onSubmit?(trimmedEmail, passwordValue)
            } label: {
                Text(submitLabel)
                    .font(.system(size: S.buttonSize, weight: .semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .frame(height: S.buttonTextHeight)
                    .frame(maxWidth: .infinity)
                    .frame(height: S.button)
                    .background(RoundedRectangle(cornerRadius: F.radius, style: .continuous).fill(accent))
            }
            .buttonStyle(SubmitStyle(valid: valid))
            .disabled(!valid)
            .padding(.top, S.gap + 10)

            Button {
                touched()
                onForgot?()
            } label: {
                Text(forgotLabel)
                    .font(.system(size: S.forgotSize, weight: .medium))
                    .foregroundStyle(fieldColor)
                    .lineLimit(1)
                    .frame(height: S.forgotHeight)
            }
            .buttonStyle(ForgotStyle())
            .frame(maxWidth: .infinity)
            .padding(.top, S.forgotTop)
        }
    }

    private func touched() {
        held = false
        touches += 1
    }

    private func toggle(auto: Bool) {
        if !auto { tapHaptic.impactOccurred() }
        shown.toggle()
        onToggle?(shown)
    }

    private func dismissKeyboard() {
        emailFocused = false
        passwordFocused = false
    }

    private func reset() {
        dismissKeyboard()
        emailValue = email
        passwordValue = password
        shown = false
    }

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            reset()
        }
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            for at in [M.showAt, M.hideAt] {
                try? await clock.sleep(until: origin + .seconds(at))
                guard !Task.isCancelled else { return }
                held = true
                try? await clock.sleep(until: origin + .seconds(at + M.pressFor))
                guard !Task.isCancelled else { return }
                held = false
                toggle(auto: true)
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private struct SubmitStyle: ButtonStyle {
        let valid: Bool
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .opacity(!valid ? 0.45 : configuration.isPressed ? 0.8 : 1)
                .scaleEffect(configuration.isPressed ? 0.98 : 1)
                .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: S.buttonPressFor), value: configuration.isPressed)
        }
    }

    private struct ForgotStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .opacity(configuration.isPressed ? 0.45 : 0.7)
                .contentShape(Rectangle().inset(by: -10))
        }
    }
}

private struct EyeWipePasswordFieldMail: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 20, y: 4))
        path.addLine(to: CGPoint(x: 4, y: 4))
        path.addCurve(to: CGPoint(x: 2.01, y: 6), control1: CGPoint(x: 2.9, y: 4), control2: CGPoint(x: 2.01, y: 4.9))
        path.addLine(to: CGPoint(x: 2, y: 18))
        path.addCurve(to: CGPoint(x: 4, y: 20), control1: CGPoint(x: 2, y: 19.1), control2: CGPoint(x: 2.9, y: 20))
        path.addLine(to: CGPoint(x: 20, y: 20))
        path.addCurve(to: CGPoint(x: 22, y: 18), control1: CGPoint(x: 21.1, y: 20), control2: CGPoint(x: 22, y: 19.1))
        path.addLine(to: CGPoint(x: 22, y: 6))
        path.addCurve(to: CGPoint(x: 20, y: 4), control1: CGPoint(x: 22, y: 4.9), control2: CGPoint(x: 21.1, y: 4))
        path.closeSubpath()
        path.move(to: CGPoint(x: 20, y: 8))
        path.addLine(to: CGPoint(x: 12, y: 13))
        path.addLine(to: CGPoint(x: 4, y: 8))
        path.addLine(to: CGPoint(x: 4, y: 6))
        path.addLine(to: CGPoint(x: 12, y: 11))
        path.addLine(to: CGPoint(x: 20, y: 6))
        path.addLine(to: CGPoint(x: 20, y: 8))
        path.closeSubpath()
        return path.applying(CGAffineTransform(scaleX: rect.width / 24, y: rect.height / 24).concatenating(CGAffineTransform(translationX: rect.minX, y: rect.minY)))
    }
}

#Preview {
    EyeWipePasswordField(autoPlay: true)
}
