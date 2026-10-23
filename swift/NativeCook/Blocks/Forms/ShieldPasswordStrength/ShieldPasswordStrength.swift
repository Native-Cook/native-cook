import SwiftUI

/// 0: too short to judge, 1: weak, 2: fair, 3: strong.
enum ShieldPasswordStrengthStrength: Int {
    case idle = 0, weak, fair, strong
}

private enum ShieldPasswordStrengthPalette {
    static let background = Color(hex: 0xf5f9fc)
    static let ink = Color(hex: 0x1f2933)
    static let dot = Color(hex: 0x5a6472)
    static let icon = Color(hex: 0x64748b)
    static let caret = Color(hex: 0x8a94a0)
    static let field = Color.white
    static let colors = [Color(hex: 0x2fe0ea), Color(hex: 0xe5173f), Color(hex: 0xc85a0a), Color(hex: 0x1fc955)]
    static let hintColors = [Color(hex: 0x8a94a0), Color(hex: 0xe5173f), Color(hex: 0xc85a0a), Color(hex: 0x17a34a)]
    static let hints = ["Enter Your Password", "Add more characters to strengthen it", "Nice, try adding a symbol", "Perfect! Strong & secure"]
    static let demo = ["maple", "Sy7", "-river"]
}

private enum ShieldPasswordStrengthMetrics {
    static let shieldWidth = 53.0
    static let shieldHeight = 61.0
    static let shieldCorner = 7.0
    static let glow = 104.0
    static let glowAlpha = 0.44
    static let falloffAt = [0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0]
    static let falloff = [1.0, 0.976, 0.905, 0.794, 0.655, 0.5, 0.345, 0.206, 0.095, 0.024, 0.0]
    static let breathBy = 0.06
    static let shieldToField = 154.0
    static let shieldAt = 0.4
    static let fieldWidth = 346.0
    static let fieldMargin = 28.0
    static let fieldHeight = 40.0
    static let labelGap = 10.0
    static let hintGap = 7.0
    static let textLeft = 28.0
    static let dotSize = 6.0
    static let dotGap = 4.0
    static let icons = 15.0
    static let hintHeight = 14.0
    static let keyboardGap = 16.0
    static let keyholeX = shieldWidth / 2
    static let keyholeY = 23.0
    static let keyholeRadius = 7.6
    static let keyholeStem = 5.0
    static let keyholeBottom = 41.0
    static let checkStroke = 4.2
    static let labelSize = 13.0
    static let labelHeight = 17.0
    static let letterSize = 14.0
    static let letterHeight = 18.0
    static let hintSize = 10.0
    static let caretWidth = 1.5
    static let caretHeight = 15.0
    static let inputRight = 72.0
    static let maxLength = 24
    static let eyeWidth = 26.0
    static let lockLeft = 1.0
    static let lockRight = 13.0
    static let dotFor = 0.42
    static let dotFullAt = 0.35
    static let shadow1 = (x: 0.0, y: 4.0, blur: 14.0, alpha: 0.14)
    static let shadow2 = (x: 0.0, y: 1.0, blur: 2.0, alpha: 0.08)
}

private enum ShieldPasswordStrengthMotion {
    static let loop = 10.5
    static let burstAt = [0.8, 2.5, 3.9]
    static let typeEvery = 0.11
    static let submitAt = 6.0
    static let resetAt = 8.7
    static let userHold = 3.0
    static let breath = loop / 5
    static let blink = loop / 10
    static let shift = 0.9
    static let paleAt = 0.28
    static let colorFrom = 0.15
    static let colorTo = 0.7
    static let paleShield = 0.62
    static let paleGlow = 0.7
    static let paleForm = 0.45
    static let hintClear = 0.25
    static let hintStep = 0.022
    static let formOut = 0.4
    static let glideAt = 0.45
    static let glideFor = 0.9
    static let checkAt = 1.5
    static let checkFor = 0.45
    static let pop = 0.12
    static let securedEnd = checkAt + checkFor + 0.2
    static let resetFor = 0.75
    static let keyboardFor = 0.25
    static let cubicOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 1.0 / 3, y: 1), endControlPoint: UnitPoint(x: 2.0 / 3, y: 1))

    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }
    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double {
        let u = clamp01((t - from) / (to - from))
        return u * u * (3 - 2 * u)
    }
    static func bump(_ t: Double, _ from: Double, _ peak: Double, _ to: Double) -> Double {
        smooth(t, from, peak) * (1 - smooth(t, peak, to))
    }
    static func mix(_ from: Color, _ to: Color, _ t: Double) -> Color {
        var fr: CGFloat = 0, fg: CGFloat = 0, fb: CGFloat = 0, fa: CGFloat = 0
        var tr: CGFloat = 0, tg: CGFloat = 0, tb: CGFloat = 0, ta: CGFloat = 0
        UIColor(from).getRed(&fr, green: &fg, blue: &fb, alpha: &fa)
        UIColor(to).getRed(&tr, green: &tg, blue: &tb, alpha: &ta)
        return Color.rgba(
            (Double(fr + (tr - fr) * t) * 255).rounded(),
            (Double(fg + (tg - fg) * t) * 255).rounded(),
            (Double(fb + (tb - fb) * t) * 255).rounded(), 1
        )
    }
}

/// Length first, then ASCII variety, with the same UTF-16 length that JavaScript uses.
func ShieldPasswordStrengthDefaultStrength(_ password: String) -> ShieldPasswordStrengthStrength {
    let length = password.utf16.count
    guard length >= 5 else { return .idle }
    let kinds = ["[a-z]", "[A-Z]", "[0-9]", "[^A-Za-z0-9]"].filter {
        password.range(of: $0, options: .regularExpression) != nil
    }.count
    if length >= 12 && kinds >= 3 { return .strong }
    if length >= 8 && kinds >= 2 { return .fair }
    return .weak
}

/// A password field under a glowing shield. Strength changes tint the shield and retype the
/// hint; a strong submission glides the shield down and draws a check in place of its keyhole.
struct ShieldPasswordStrength: View {
    var autoPlay: Bool
    var demoPassword: [String]
    var label: String
    var hints: [String]
    var colors: [Color]
    var hintColors: [Color]
    var strengthOf: (String) -> ShieldPasswordStrengthStrength
    var onChangePassword: ((String, ShieldPasswordStrengthStrength) -> Void)?
    var onSecured: ((String) -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var password = ""
    @State private var revealed = false
    @State private var focused = false
    @State private var phase = Phase.form
    @State private var touches = 0
    @State private var origin = Date.now
    @State private var displayedStrength = ShieldPasswordStrengthStrength.idle
    @State private var tintFrom: Color
    @State private var tintTo: Color
    @State private var shiftStart: Date? = nil
    @State private var hintStart: Date? = nil
    @State private var oldHintLevel = 0
    @State private var oldHintCount: Int
    @State private var nextHintLevel = 0
    @State private var securedStart: Date? = nil
    @State private var leavingStart: Date? = nil
    @State private var transitionTask: Task<Void, Never>? = nil
    @State private var securedCallbackTask: Task<Void, Never>? = nil
    @State private var lift = Tween(0)
    @State private var dotStarts: [Date] = []
    @State private var successHaptic = UINotificationFeedbackGenerator()
    private typealias S = ShieldPasswordStrengthMetrics
    private typealias M = ShieldPasswordStrengthMotion
    private typealias P = ShieldPasswordStrengthPalette
    private enum Phase { case form, secured, leaving }

    init(
        autoPlay: Bool = false,
        demoPassword: [String] = ShieldPasswordStrengthPalette.demo,
        label: String = "Password",
        hints: [String] = ShieldPasswordStrengthPalette.hints,
        colors: [Color] = ShieldPasswordStrengthPalette.colors,
        hintColors: [Color] = ShieldPasswordStrengthPalette.hintColors,
        strengthOf: @escaping (String) -> ShieldPasswordStrengthStrength = ShieldPasswordStrengthDefaultStrength,
        onChangePassword: ((String, ShieldPasswordStrengthStrength) -> Void)? = nil,
        onSecured: ((String) -> Void)? = nil
    ) {
        self.autoPlay = autoPlay
        self.demoPassword = demoPassword
        self.label = label
        self.hints = hints
        self.colors = colors
        self.hintColors = hintColors
        self.strengthOf = strengthOf
        self.onChangePassword = onChangePassword
        self.onSecured = onSecured
        _tintFrom = State(initialValue: colors[0])
        _tintTo = State(initialValue: colors[0])
        _oldHintCount = State(initialValue: hints[0].utf16.count)
    }

    private var level: ShieldPasswordStrengthStrength { strengthOf(password) }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let shieldY = size.height * S.shieldAt
            let width = min(S.fieldWidth, size.width - S.fieldMargin * 2)
            TimelineView(.animation) { timeline in
                let time = max(0, timeline.date.timeIntervalSince(origin)).truncatingRemainder(dividingBy: M.loop)
                let shift = shiftStart.map { min(M.shift, max(0, timeline.date.timeIntervalSince($0))) } ?? M.shift
                let pale = M.bump(shift, 0, M.paleAt, M.shift)
                let color = M.mix(tintFrom, tintTo, M.smooth(shift, M.colorFrom, M.colorTo))
                let secured = securedStart.map { min(M.securedEnd, timeline.date.timeIntervalSince($0)) } ?? -1
                let leaving = leavingStart.map { M.clamp01(timeline.date.timeIntervalSince($0) / M.resetFor) } ?? 0
                let there = secured < 0 ? 1 : leaving > 0 ? M.smooth(leaving, 0.5, 1) : 1 - M.smooth(secured, 0, M.formOut)
                let currentHint = hint(at: timeline.date)
                ZStack(alignment: .topLeading) {
                    P.background
                        .contentShape(Rectangle())
                        .onTapGesture { focused = false }

                    ZStack(alignment: .topLeading) {
                        ShieldPasswordStrengthShield(
                            time: time, pale: pale, color: color, secured: secured, leaving: leaving,
                            still: reduceMotion, shieldY: shieldY
                        )
                        .frame(width: size.width, height: size.height)

                        if phase == .secured {
                            Button {
                                touched()
                                reset()
                            } label: {
                                Color.clear
                                    .frame(width: S.shieldWidth + 32, height: S.shieldHeight + 32)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .position(x: size.width / 2, y: size.height / 2)
                            .accessibilityLabel("Change password")
                        }

                        form(width: width, pale: pale, time: time, date: timeline.date, hint: currentHint)
                            .frame(width: width)
                            .offset(x: (size.width - width) / 2, y: shieldY + S.shieldToField - S.fieldHeight / 2)
                            .opacity(there)
                            .allowsHitTesting(phase == .form)
                    }
                    .offset(y: lift.value(at: timeline.date))
                }
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
            }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { event in
                let frame = event.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect ?? .zero
                let duration = event.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double ?? M.keyboardFor
                let hintBottom = shieldY + S.shieldToField + S.fieldHeight / 2 + S.hintGap + S.hintHeight
                let target = min(0, frame.minY - S.keyboardGap - hintBottom)
                lift.retarget(target, duration: reduceMotion ? 0 : duration == 0 ? M.keyboardFor : duration, curve: M.cubicOut)
            }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { event in
                let duration = event.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double ?? M.keyboardFor
                lift.retarget(0, duration: reduceMotion ? 0 : duration == 0 ? M.keyboardFor : duration, curve: M.cubicOut)
            }
        }
        .preferredColorScheme(.light)
        .ignoresSafeArea(.keyboard)
        .onAppear {
            successHaptic.prepare()
            updateStrength(level)
        }
        .onChange(of: level) { _, next in updateStrength(next) }
        .onDisappear {
            transitionTask?.cancel()
            securedCallbackTask?.cancel()
        }
        .task(id: LoopKey(on: autoPlay, touches: touches, demo: demoPassword)) { await loop() }
    }

    private func form(width: Double, pale: Double, time: Double, date: Date, hint: (level: Int, count: Int)) -> some View {
        let inForm = phase == .form
        let caret = inForm && (focused || password.isEmpty)
        return VStack(alignment: .leading, spacing: S.hintGap) {
            ZStack(alignment: .topLeading) {
                Text(label)
                    .font(.system(size: S.labelSize, weight: .bold))
                    .foregroundStyle(P.ink)
                    .frame(height: S.labelHeight)
                    .offset(y: -S.labelGap - S.labelHeight)

                Capsule()
                    .fill(P.field)
                    .shadow(color: Color.rgba(100, 116, 139, S.shadow1.alpha), radius: S.shadow1.blur / 2, x: S.shadow1.x, y: S.shadow1.y)
                    .shadow(color: Color.rgba(100, 116, 139, S.shadow2.alpha), radius: S.shadow2.blur / 2, x: S.shadow2.x, y: S.shadow2.y)
                    .overlay {
                        Capsule().strokeBorder(level == .strong ? colors[3].opacity(115.0 / 255) : .clear, lineWidth: 1)
                    }
                    .onTapGesture { if !focused { focused = true } }

                HStack(spacing: 0) {
                    HStack(spacing: S.dotGap) {
                        if revealed {
                            Text(password)
                                .font(.system(size: S.letterSize, weight: .medium))
                                .foregroundStyle(P.dot)
                                .lineLimit(1)
                                .frame(height: S.letterHeight)
                        } else {
                            ForEach(0..<password.utf16.count, id: \.self) { index in
                                let age = index < dotStarts.count ? date.timeIntervalSince(dotStarts[index]) : S.dotFor
                                let u = reduceMotion ? 1 : M.clamp01(age / S.dotFor)
                                let opacity = u < S.dotFullAt ? 0.15 + 0.2 * u / S.dotFullAt : 0.35 + 0.65 * (u - S.dotFullAt) / (1 - S.dotFullAt)
                                Circle()
                                    .fill(P.dot)
                                    .frame(width: S.dotSize, height: S.dotSize)
                                    .scaleEffect(u < S.dotFullAt ? 0.5 + 0.5 * u / S.dotFullAt : 1)
                                    .opacity(opacity)
                            }
                        }
                        if caret {
                            RoundedRectangle(cornerRadius: S.caretWidth / 2)
                                .fill(P.caret)
                                .frame(width: S.caretWidth, height: S.caretHeight)
                                .opacity(time.truncatingRemainder(dividingBy: M.blink) < M.blink / 2 ? 1 : 0)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .frame(height: S.fieldHeight)
                    .clipped()
                    .padding(.leading, S.textLeft)
                    .allowsHitTesting(false)

                    Button {
                        touched()
                        revealed.toggle()
                        if !revealed { dotStarts = Array(repeating: .now, count: password.utf16.count) }
                    } label: {
                        Image(systemName: revealed ? "eye.fill" : "eye.slash.fill")
                            .font(.system(size: S.icons))
                            .foregroundStyle(P.icon)
                            .frame(width: S.eyeWidth, height: S.fieldHeight)
                            .contentShape(Rectangle().inset(by: -8))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(revealed ? "Hide password" : "Show password")
                    Image(systemName: "lock.fill")
                        .font(.system(size: S.icons - 1))
                        .foregroundStyle(P.icon)
                        .frame(width: S.icons - 1)
                        .padding(.leading, S.lockLeft)
                        .padding(.trailing, S.lockRight + 1)
                }
                .frame(height: S.fieldHeight)

                ShieldPasswordStrengthInput(
                    value: Binding(get: { password }, set: { next in touched(); change(next) }),
                    focused: $focused, label: label,
                    onFocus: touched,
                    onSubmit: { touched(); secure(hand: true) }
                )
                .frame(width: width - S.textLeft - S.inputRight, height: S.fieldHeight)
                .offset(x: S.textLeft)
            }
            .frame(width: width, height: S.fieldHeight)
            .opacity(1 - M.paleForm * pale)

            Text(String(decoding: hints[hint.level].utf16.prefix(hint.count), as: UTF16.self))
                .font(.system(size: S.hintSize))
                .foregroundStyle(hintColors[hint.level])
                .lineLimit(1)
                .frame(height: S.hintHeight)
        }
    }

    private func hint(at date: Date) -> (level: Int, count: Int) {
        guard let hintStart else { return (oldHintLevel, oldHintCount) }
        let elapsed = date.timeIntervalSince(hintStart)
        if elapsed < M.hintClear { return (oldHintLevel, oldHintCount) }
        return (nextHintLevel, min(hints[nextHintLevel].utf16.count, max(0, Int((elapsed - M.hintClear) / M.hintStep))))
    }

    private func updateStrength(_ next: ShieldPasswordStrengthStrength) {
        guard next != displayedStrength else { return }
        let now = Date.now
        let currentHint = hint(at: now)
        oldHintLevel = currentHint.level
        oldHintCount = currentHint.count
        nextHintLevel = next.rawValue
        hintStart = now
        tintFrom = colors[displayedStrength.rawValue]
        tintTo = colors[next.rawValue]
        displayedStrength = next
        shiftStart = now
    }

    private func change(_ next: String) {
        let count = next.utf16.count
        if count > dotStarts.count {
            dotStarts += Array(repeating: Date.now, count: count - dotStarts.count)
        } else {
            dotStarts = Array(dotStarts.prefix(count))
        }
        password = next
        updateStrength(strengthOf(next))
        onChangePassword?(next, strengthOf(next))
    }

    private func touched() { touches += 1 }

    private func secure(hand: Bool) {
        guard phase == .form, level == .strong else { return }
        focused = false
        phase = .secured
        securedStart = .now
        securedCallbackTask?.cancel()
        securedCallbackTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(M.checkAt + M.checkFor / 2))
            guard !Task.isCancelled else { return }
            if hand { successHaptic.notificationOccurred(.success) }
            onSecured?(password)
        }
    }

    private func reset() {
        guard phase == .secured else { return }
        phase = .leaving
        change("")
        leavingStart = .now
        transitionTask?.cancel()
        transitionTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(M.resetFor))
            guard !Task.isCancelled else { return }
            securedStart = nil
            leavingStart = nil
            phase = .form
            securedCallbackTask?.cancel()
        }
    }

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            while !Task.isCancelled {
                if phase == .secured { reset() }
                if phase != .form {
                    try? await Task.sleep(for: .seconds(0.1))
                    continue
                }
                focused = false
                revealed = false
                if !password.isEmpty {
                    change("")
                    try? await Task.sleep(for: .seconds(M.hintClear + Double(hints[0].utf16.count) * M.hintStep + 0.2))
                    continue
                }
                break
            }
        }
        // The glow and caret keep their mount clock; only the typing schedule starts over.
        let clock = ContinuousClock()
        let cycle = clock.now
        var events: [AutoEvent] = []
        var nextOrder = 0
        func appendCycle(at offset: Double) {
            var typed: [UInt16] = []
            for (index, burst) in demoPassword.enumerated() {
                for (place, letter) in burst.utf16.enumerated() {
                    typed.append(letter)
                    events.append(AutoEvent(
                        at: offset + M.burstAt[min(index, M.burstAt.count - 1)] + Double(place) * M.typeEvery,
                        order: nextOrder, action: .change(String(decoding: typed, as: UTF16.self))
                    ))
                    nextOrder += 1
                }
            }
            for (at, action) in [(M.submitAt, AutoAction.secure), (M.resetAt, AutoAction.reset), (M.loop, AutoAction.cycle)] {
                events.append(AutoEvent(at: offset + at, order: nextOrder, action: action))
                nextOrder += 1
            }
            // A later cycle can overlap custom bursts scheduled beyond its boundary.
            events.sort { $0.at == $1.at ? $0.order < $1.order : $0.at < $1.at }
        }
        appendCycle(at: 0)
        while !Task.isCancelled {
            let event = events.removeFirst()
            try? await clock.sleep(until: cycle + .seconds(event.at))
            guard !Task.isCancelled else { return }
            switch event.action {
            case .change(let value): change(value)
            case .secure: secure(hand: false)
            case .reset: reset()
            case .cycle: appendCycle(at: event.at)
            }
        }
    }

    private enum AutoAction {
        case change(String), secure, reset, cycle
    }
    private struct AutoEvent {
        let at: Double
        let order: Int
        let action: AutoAction
    }
    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
        let demo: [String]
    }
}

private struct ShieldPasswordStrengthShield: View {
    let time: Double
    let pale: Double
    let color: Color
    let secured: Double
    let leaving: Double
    let still: Bool
    let shieldY: Double
    private typealias S = ShieldPasswordStrengthMetrics
    private typealias M = ShieldPasswordStrengthMotion

    var body: some View {
        Canvas { context, size in
            let mark = M.smooth(secured, M.checkAt, M.checkAt + M.checkFor) * (1 - M.smooth(leaving, 0, 0.35))
            let swap = M.bump(secured, M.checkAt - 0.1, M.checkAt + 0.12, M.checkAt + M.checkFor) * (leaving > 0 ? 0 : 1)
            let glide = still ? 0 : M.smooth(secured, M.glideAt, M.glideAt + M.glideFor) * (1 - M.smooth(leaving, 0.1, 0.9))
            let drop = size.height / 2 - shieldY
            let centre = CGPoint(x: size.width / 2, y: shieldY + drop * glide)
            let breath = still ? 0 : S.breathBy * sin(tau * time / M.breath)
            let strength = S.glowAlpha * (1 - M.paleGlow * pale)
            context.fillEllipse(
                center: centre,
                radii: CGSize(width: S.glow * (1 + breath), height: S.glow * (1 + breath)),
                gradient: Gradient(stops: zip(S.falloffAt, S.falloff).map { .init(color: color.opacity($0.1 * strength), location: $0.0) })
            )
            let pop = still ? 0 : M.pop * M.bump(secured, M.checkAt, M.checkAt + M.checkFor * 0.45, M.checkAt + M.checkFor + 0.15)
            var shield = context.scaled(1 + pop, about: centre)
            shield.translateBy(x: centre.x - S.shieldWidth / 2, y: centre.y - S.shieldHeight / 2)
            let alpha = 1 - M.paleShield * max(pale, 0.75 * swap)
            shield.fill(Self.outline, with: .color(color.opacity(alpha)))
            var keyhole = shield
            keyhole.opacity *= 1 - M.smooth(mark, 0, 0.5)
            keyhole.fill(Path(ellipseIn: CGRect(x: S.keyholeX - S.keyholeRadius, y: S.keyholeY - S.keyholeRadius, width: S.keyholeRadius * 2, height: S.keyholeRadius * 2)), with: .color(.white))
            keyhole.fill(RoundedRectangle(cornerRadius: S.keyholeStem / 2, style: .circular).path(in: CGRect(x: S.keyholeX - S.keyholeStem / 2, y: S.keyholeY, width: S.keyholeStem, height: S.keyholeBottom - S.keyholeY)), with: .color(.white))
            shield.opacity *= M.smooth(mark, 0.3, 0.6)
            shield.stroke(Self.check.trimmedPath(from: 0, to: mark), with: .color(.white), style: StrokeStyle(lineWidth: S.checkStroke, lineCap: .round, lineJoin: .round))
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// Skia's corner effect trims seven points from each edge and joins through the vertex.
    private static let outline: Path = {
        let points = [CGPoint(x: S.shieldWidth / 2, y: 0), CGPoint(x: S.shieldWidth, y: 3.5), CGPoint(x: S.shieldWidth, y: 40), CGPoint(x: S.shieldWidth / 2, y: S.shieldHeight), CGPoint(x: 0, y: 40), CGPoint(x: 0, y: 3.5)]
        func trimmed(_ vertex: CGPoint, _ neighbor: CGPoint) -> CGPoint {
            let dx = neighbor.x - vertex.x
            let dy = neighbor.y - vertex.y
            let length = hypot(dx, dy)
            let distance = min(S.shieldCorner, length / 2)
            return CGPoint(x: vertex.x + dx / length * distance, y: vertex.y + dy / length * distance)
        }
        var path = Path()
        for index in points.indices {
            let vertex = points[index]
            let before = trimmed(vertex, points[(index + points.count - 1) % points.count])
            let after = trimmed(vertex, points[(index + 1) % points.count])
            if index == 0 { path.move(to: before) } else { path.addLine(to: before) }
            path.addQuadCurve(to: after, control: vertex)
        }
        path.closeSubpath()
        return path
    }()

    private static let check: Path = {
        var path = Path()
        path.move(to: CGPoint(x: 15.5, y: 30.5))
        path.addLine(to: CGPoint(x: 23, y: 37.8))
        path.addLine(to: CGPoint(x: 37.8, y: 22.8))
        return path
    }()
}

private struct ShieldPasswordStrengthInput: UIViewRepresentable {
    @Binding var value: String
    @Binding var focused: Bool
    let label: String
    let onFocus: () -> Void
    let onSubmit: () -> Void

    func makeUIView(context: Context) -> ShieldPasswordStrengthTextInput {
        let input = ShieldPasswordStrengthTextInput()
        input.delegate = context.coordinator
        input.addTarget(context.coordinator, action: #selector(ShieldPasswordStrengthInputCoordinator.changed(_:)), for: .editingChanged)
        input.font = .systemFont(ofSize: ShieldPasswordStrengthMetrics.letterSize)
        input.textColor = .clear
        input.tintColor = .clear
        input.backgroundColor = .clear
        input.isSecureTextEntry = true
        input.autocapitalizationType = .none
        input.autocorrectionType = .no
        input.textContentType = .newPassword
        input.returnKeyType = .done
        input.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return input
    }

    func updateUIView(_ input: ShieldPasswordStrengthTextInput, context: Context) {
        context.coordinator.parent = self
        if input.text != value { input.text = value }
        input.accessibilityLabel = label
        if focused && !input.isFirstResponder { input.becomeFirstResponder() }
        if !focused && input.isFirstResponder { input.resignFirstResponder() }
    }

    func makeCoordinator() -> ShieldPasswordStrengthInputCoordinator { ShieldPasswordStrengthInputCoordinator(self) }

    final class ShieldPasswordStrengthInputCoordinator: NSObject, UITextFieldDelegate {
        var parent: ShieldPasswordStrengthInput
        init(_ parent: ShieldPasswordStrengthInput) { self.parent = parent }
        @objc func changed(_ input: UITextField) { parent.value = input.text ?? "" }
        func textFieldDidBeginEditing(_ textField: UITextField) {
            parent.focused = true
            parent.onFocus()
        }
        func textFieldDidEndEditing(_ textField: UITextField) { parent.focused = false }
        func textFieldShouldReturn(_ textField: UITextField) -> Bool {
            textField.resignFirstResponder()
            parent.onSubmit()
            return true
        }
        func textField(_ textField: UITextField, shouldChangeCharactersIn range: NSRange, replacementString string: String) -> Bool {
            let next = ((textField.text ?? "") as NSString).replacingCharacters(in: range, with: string)
            return next.utf16.count <= ShieldPasswordStrengthMetrics.maxLength
        }
    }
}

private final class ShieldPasswordStrengthTextInput: UITextField {
    override func caretRect(for position: UITextPosition) -> CGRect { .zero }
    override func selectionRects(for range: UITextRange) -> [UITextSelectionRect] { [] }
}

#Preview {
    ShieldPasswordStrength(autoPlay: true)
}
