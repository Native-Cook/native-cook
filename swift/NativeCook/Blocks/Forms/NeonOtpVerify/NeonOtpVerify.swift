import SwiftUI
import QuartzCore

private enum NeonOtpVerifyPalette {
    static let screen = Color(hex: 0x0b0b0c)
    static let sheet = Color(hex: 0x1b1b1d)
    static let grabber = Color(hex: 0x5a5a5e)
    static let ink = Color(hex: 0xffffff)
    static let mute = Color(hex: 0x7c7c82)
    static let keyInk = Color(hex: 0xf2f2f4)
    static let boxFill = Color(hex: 0x1f1f21)
    static let boxBorder = Color(hex: 0x2e2e31)
    static let tileFill = Color(hex: 0x24120f)
    static let accent = Color(hex: 0xe0452a)
    static let neon = Color(hex: 0xff5a2a)
    static let hot = Color(hex: 0xffd9cb)
    static let warm = Color(hex: 0xff9c7a)
}

private enum NeonOtpVerifyMetrics {
    static let sheetRadius = 28.0
    static let grabberTop = 13.0
    static let grabberWidth = 40.0
    static let grabberHeight = 5.0
    static let titleTop = 52.0
    static let subtitleTop = 97.0
    static let rowTop = 184.0
    static let resendTop = 289.0
    static let padTop = 326.0
    static let successTop = 83.0
    static let box = 70.0
    static let boxRadius = 17.0
    static let boxGap = 15.0
    static let rowMargin = 24.0
    static let keyRow = 54.0
    static let keyRowMin = 44.0
    static let keyFlash = CGSize(width: 92, height: 46)
    static let keys = [["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"], ["", "0", "back"]]
    static let titleSize = 27.0
    static let titleHeight = 34.0
    static let subtitleSize = 14.0
    static let subtitleHeight = 24.0
    static let subtitleLeading = subtitleHeight - UIFont.systemFont(ofSize: subtitleSize).lineHeight
    static let resendHeight = 20.0
    static let digitSize = 25.0
    static let digitHeight = 30.0
    static let keySize = 26.0
    static let keyHeight = 32.0
    static let keyInset = 20.0
    static let keyFlashRadius = 14.0
    static let focusFor = 0.14
    static let focusBlur = 12.0
    static let borderWidth = 1.5
    static let spill = 30.0
    static let head = 0.42
    static let lift = 0.08
    static let lean = [-2.5, 1.5, -1.5, 2.5]
    static let deckStep = 15.0
    static let deckTurn = -44.0
    static let deckFront = 0.08
    static let perspective = 500.0
    static let tileEnter = 1 + lift + deckFront
    static let tileBurst = 1.4
    static let tileSpringOmega = 10.0
    static let tileSpringZeta = 0.6
    static let tileCanvas = 280.0
    static let hazeCanvas = 360.0
    static let hazeRowRX = 0.56
    static let hazeRowRY = 124.0
    static let hazeRowDrop = 28.0
    static let hazeRowAlpha = 0.4
    static let hazeTileRadius = 150.0
    static let hazeTileBurst = 0.36
    static let hazeTileRest = 0.17
    static let falloffAt = [0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0]
    static let falloff = [1.0, 0.976, 0.905, 0.794, 0.655, 0.5, 0.345, 0.206, 0.095, 0.024, 0.0]
    static let embers = [
        [-27.0, 0.0, -12.0, 14.0, 1.3],
        [-10.0, 0.07, -4.0, 26.0, 1.0],
        [6.0, 0.03, 5.0, 18.0, 1.5],
        [23.0, 0.1, 13.0, 12.0, 1.1],
        [-18.0, 0.16, -7.0, 30.0, 0.9],
        [15.0, 0.2, 8.0, 24.0, 1.2],
    ]
    static let emberFrom = 56.0
    static let emberGravity = 110.0
    static let emberFor = 1.0
}

private enum NeonOtpVerifyMotion {
    static let loop = 6.9
    static let typeAt = 0.8
    static let typeEvery = 0.32
    static let keyFor = 0.13
    static let resetAfter = 3.6
    static let userHold = 3.0
    static let traceAt = 0.15
    static let traceFor = 0.45
    static let liftAt = traceAt + traceFor
    static let liftFor = 0.12
    static let mergeAt = 0.74
    static let deckFor = 0.18
    static let closeFor = 0.12
    static let tileAt = mergeAt + deckFor + closeFor - 0.06
    static let tileIn = 0.08
    static let popFor = 0.14
    static let checkFor = 0.22
    static let textOut = 0.2
    static let titleAfter = 0.08
    static let titleIn = 0.3
    static let settled = 2.2
    static let resetFor = 0.5
    static let shakeAfter = 0.1
    static let shake = [-10.0, 9.0, -7.0, 5.0, -2.0, 0.0]
    static let shakeStep = 0.065
    static let wrongFor = 0.62
    static let digitFor = 0.17
    static let digitFrom = 0.6
    static let digitBack = 2.0
    static let flashIn = 0.04
    static let flashOut = 0.32
    static let cssEase = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.25, y: 0.1),
        endControlPoint: UnitPoint(x: 0.25, y: 1)
    )

    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double {
        let u = clamp01((t - from) / (to - from))
        return u * u * (3 - 2 * u)
    }

    /// An analytic spring keeps the canvas and the tile on the same verification clock.
    static func springLeft(_ t: Double, omega: Double, zeta: Double) -> Double {
        guard t > 0 else { return 1 }
        let damped = omega * sqrt(1 - zeta * zeta)
        return exp(-zeta * omega * t) * (cos(damped * t) + zeta * omega / damped * sin(damped * t))
    }

    static func digitIn(_ t: Double) -> Double {
        let u = clamp01(t / digitFor) - 1
        return 1 + (digitBack + 1) * u * u * u + digitBack * u * u
    }

    static func shakeAt(_ t: Double) -> Double {
        let age = t - shakeAfter
        guard age > 0 else { return 0 }
        let step = Int(age / shakeStep)
        guard step < shake.count else { return 0 }
        let from = step == 0 ? 0 : shake[step - 1]
        let u = clamp01((age - Double(step) * shakeStep) / shakeStep)
        let eased = u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2
        return from + (shake[step] - from) * eased
    }

    /// A clockwise outline starting at the middle of the top, matching the Skia path.
    static func outline(size: Double, radius: Double, origin: Double) -> Path {
        let a = origin
        let b = origin + size
        let r = radius
        var path = Path()
        path.move(to: CGPoint(x: a + size / 2, y: a))
        path.addLine(to: CGPoint(x: b - r, y: a))
        path.addArc(center: CGPoint(x: b - r, y: a + r), radius: r, startAngle: .degrees(-90), endAngle: .degrees(0), clockwise: false)
        path.addLine(to: CGPoint(x: b, y: b - r))
        path.addArc(center: CGPoint(x: b - r, y: b - r), radius: r, startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false)
        path.addLine(to: CGPoint(x: a + r, y: b))
        path.addArc(center: CGPoint(x: a + r, y: b - r), radius: r, startAngle: .degrees(90), endAngle: .degrees(180), clockwise: false)
        path.addLine(to: CGPoint(x: a, y: a + r))
        path.addArc(center: CGPoint(x: a + r, y: a + r), radius: r, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
        path.closeSubpath()
        return path
    }

    static func stroke(_ path: Path, in context: GraphicsContext, color: Color, width: Double, opacity: Double = 1, blur: Double = 0) {
        guard opacity > 0 else { return }
        var copy = context
        copy.opacity *= opacity
        if blur > 0 { copy.addFilter(.blur(radius: blur)) }
        copy.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
    }
}

/// A phone verification sheet with its own pad. The correct code traces and folds the boxes
/// into a glowing check tile; an incorrect code turns them red and clears them.
struct NeonOtpVerify: View {
    /// The code that verifies; its length is the number of boxes.
    var code = "6988"
    var autoPlay = false
    var title = "Let’s verify your number"
    var subtitle = "We’ve sent a 4-digit code to your phone.\nIt’ll auto-verify once entered."
    var resendPrompt = "Didn’t receive the code?"
    var resendLabel = "Resend"
    var successTitle = "Verified successfully"
    var accent = NeonOtpVerifyPalette.accent
    var neon = NeonOtpVerifyPalette.neon
    /// Called when the code matches, as the boxes have become the check tile.
    var onVerified: ((String) -> Void)? = nil
    var onRejected: ((String) -> Void)? = nil
    var onResend: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var digits = ""
    @State private var status = Status.idle
    @State private var autoKey: String? = nil
    @State private var touches = 0
    @State private var verificationStart: Date? = nil
    @State private var leavingStart: Date? = nil
    @State private var wrongStart: Date? = nil
    @State private var transitionTask: Task<Void, Never>? = nil
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var notificationHaptic = UINotificationFeedbackGenerator()

    private typealias M = NeonOtpVerifyMotion
    private typealias S = NeonOtpVerifyMetrics
    private typealias P = NeonOtpVerifyPalette
    private enum Status { case idle, wrong, verifying, verified, leaving }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let bottom = max(insets.bottom, 16) + 6
            let keyRow = min(S.keyRow, max(S.keyRowMin, (screen.height - insets.top - 24 - S.padTop - bottom) / Double(S.keys.count)))
            let box = min(S.box, (screen.width - S.rowMargin * 2 - S.boxGap * Double(code.count - 1)) / Double(max(1, code.count)))
            TimelineView(.animation) { timeline in
                let t = verificationStart.map { min(M.settled, timeline.date.timeIntervalSince($0)) } ?? -1
                let away = leavingStart.map { M.clamp01(timeline.date.timeIntervalSince($0) / M.resetFor) } ?? 0
                let shake = reduceMotion ? 0 : wrongStart.map { M.shakeAt(timeline.date.timeIntervalSince($0)) } ?? 0
                ZStack(alignment: .bottom) {
                    P.screen
                    sheet(width: screen.width, box: box, bottom: bottom, keyRow: keyRow, t: t, away: away, shake: shake, date: timeline.date)
                }
                .frame(width: screen.width, height: screen.height)
                .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .preferredColorScheme(.dark)
        .onAppear {
            tapHaptic.prepare()
            notificationHaptic.prepare()
        }
        .onDisappear { transitionTask?.cancel() }
        .task(id: LoopKey(on: autoPlay, touches: touches, code: code)) { await loop() }
    }

    private func sheet(width: Double, box: Double, bottom: Double, keyRow: Double, t: Double, away: Double, shake: Double, date: Date) -> some View {
        let rowCentre = S.rowTop + S.box / 2
        let out = M.smooth(t, M.liftAt, M.liftAt + M.textOut)
        let askOpacity = away > 0 ? M.smooth(away, 0.5, 1) : 1 - out
        let askY = away > 0 || reduceMotion ? 0 : -6 * out
        let success = M.smooth(t, M.mergeAt + M.titleAfter, M.mergeAt + M.titleAfter + M.titleIn)
        let dimmed = t < 0 ? 0 : away > 0 ? 1 - M.smooth(away, 0.5, 1) : M.smooth(t, M.traceAt, M.liftAt)
        let idle = status == .idle
        return ZStack(alignment: .topLeading) {
            P.sheet
            Capsule()
                .fill(P.grabber)
                .frame(width: S.grabberWidth, height: S.grabberHeight)
                .position(x: width / 2, y: S.grabberTop + S.grabberHeight / 2)

            VStack(spacing: S.subtitleTop - S.titleTop - S.titleHeight) {
                titleText(title)
                Text(subtitle)
                    .font(.system(size: S.subtitleSize))
                    .foregroundStyle(P.mute)
                    .multilineTextAlignment(.center)
                    .lineSpacing(S.subtitleLeading)
                    .padding(.vertical, S.subtitleLeading / 2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(width: width - S.rowMargin * 2)
            .offset(x: S.rowMargin, y: S.titleTop + askY)
            .opacity(askOpacity)
            .allowsHitTesting(false)
            .accessibilityHidden(!idle)

            titleText(successTitle)
                .frame(width: width - S.rowMargin * 2)
                .offset(x: S.rowMargin, y: S.successTop + (reduceMotion ? 0 : 10 * (1 - success)))
                .opacity(t < 0 ? 0 : success * (1 - M.smooth(away, 0, 0.5)))
                .allowsHitTesting(false)
                .accessibilityHidden(status != .verified)

            NeonOtpVerifyHaze(t: t, away: away, still: reduceMotion, neon: neon)
                .frame(width: width, height: S.hazeCanvas)
                .position(x: width / 2, y: rowCentre)

            ZStack {
                // Lower indices draw last so the first box remains the front of the deck.
                ForEach(Array((0..<code.count).reversed()), id: \.self) { index in
                    let home = (Double(index) - Double(code.count - 1) / 2) * (box + S.boxGap)
                    NeonOtpVerifyBox(
                        index: index, count: code.count, size: box,
                        digit: index < digits.count ? String(Array(digits)[index]) : "",
                        focused: idle && index == digits.count, wrong: status == .wrong,
                        accent: accent, neon: neon, t: t, away: away, still: reduceMotion, date: date
                    )
                    .offset(x: home)
                }
            }
            .frame(width: width, height: S.box)
            .offset(x: shake, y: S.rowTop)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Verification code")
            .accessibilityValue(digits.map(String.init).joined(separator: " "))

            NeonOtpVerifyTile(t: t, away: away, still: reduceMotion, neon: neon, box: box)
                .frame(width: S.tileCanvas, height: S.tileCanvas)
                .position(x: width / 2, y: rowCentre)

            HStack(spacing: 0) {
                Text(resendPrompt + " ")
                    .foregroundStyle(P.mute)
                Button(action: resend) {
                    Text(resendLabel).fontWeight(.semibold).foregroundStyle(P.ink)
                }
                .buttonStyle(ResendStyle())
            }
            .font(.system(size: S.subtitleSize))
            .frame(width: width, height: S.resendHeight)
            .offset(y: S.resendTop + askY)
            .opacity(askOpacity)
            .allowsHitTesting(idle)

            VStack(spacing: 0) {
                ForEach(S.keys.indices, id: \.self) { row in
                    HStack(spacing: 0) {
                        ForEach(S.keys[row], id: \.self) { key in
                            if key.isEmpty {
                                Color.clear.frame(maxWidth: .infinity).frame(height: keyRow)
                            } else {
                                Button { press(key) } label: {
                                    Group {
                                        if key == "back" {
                                            Image(systemName: "delete.left")
                                                .font(.system(size: S.digitSize))
                                        } else {
                                            Text(key)
                                                .font(.system(size: S.keySize))
                                                .monospacedDigit()
                                                .frame(height: S.keyHeight)
                                        }
                                    }
                                    .foregroundStyle(P.keyInk)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: keyRow)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(KeyStyle(held: autoKey == key))
                                .accessibilityLabel(key == "back" ? "Delete" : key)
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, S.keyInset)
            .frame(width: width)
            .offset(y: S.padTop)
            .opacity(1 - 0.74 * dimmed)
            .allowsHitTesting(idle)
        }
        .frame(width: width, height: S.padTop + keyRow * Double(S.keys.count) + bottom)
        .clipShape(UnevenRoundedRectangle(topLeadingRadius: S.sheetRadius, topTrailingRadius: S.sheetRadius, style: .continuous))
    }

    private func titleText(_ text: String) -> some View {
        Text(text)
            .font(.system(size: S.titleSize, weight: .semibold))
            .tracking(-0.3)
            .foregroundStyle(P.ink)
            .lineLimit(1)
            .minimumScaleFactor(0.5)
            .frame(height: S.titleHeight)
    }

    private func touched() {
        autoKey = nil
        touches += 1
    }

    private func press(_ key: String) {
        touched()
        tapHaptic.impactOccurred()
        enter(key, hand: true)
    }

    private func resend() {
        touched()
        tapHaptic.impactOccurred()
        if status == .idle { digits = "" }
        onResend?()
    }

    /// Manual and automatic keys both reach the same validation path.
    private func enter(_ key: String, hand: Bool) {
        guard status == .idle else { return }
        if key == "back" {
            digits = String(digits.dropLast())
            return
        }
        guard digits.count < code.count else { return }
        digits += key
        if digits.count == code.count { submit(digits, hand: hand) }
    }

    private func submit(_ entered: String, hand: Bool) {
        transitionTask?.cancel()
        if entered == code {
            status = .verifying
            verificationStart = .now
            transitionTask = Task { @MainActor in
                try? await Task.sleep(for: .seconds(M.tileAt))
                guard !Task.isCancelled else { return }
                status = .verified
                if hand { notificationHaptic.notificationOccurred(.success) }
                onVerified?(code)
            }
        } else {
            status = .wrong
            wrongStart = .now
            if hand { notificationHaptic.notificationOccurred(.error) }
            onRejected?(entered)
            transitionTask = Task { @MainActor in
                try? await Task.sleep(for: .seconds(M.wrongFor))
                guard !Task.isCancelled else { return }
                digits = ""
                status = .idle
                wrongStart = nil
            }
        }
    }

    private func reset() {
        transitionTask?.cancel()
        digits = ""
        status = .leaving
        leavingStart = .now
        transitionTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(M.resetFor))
            guard !Task.isCancelled else { return }
            verificationStart = nil
            leavingStart = nil
            status = .idle
        }
    }

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            // Finish a manual result before the next cycle starts from an empty sheet.
            while !Task.isCancelled {
                if status == .verified { reset() }
                if status == .idle {
                    if digits.isEmpty { break }
                    digits = ""
                }
                try? await Task.sleep(for: .seconds(0.1))
            }
        }
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            for (index, digit) in code.enumerated() {
                let at = M.typeAt + Double(index) * M.typeEvery
                try? await clock.sleep(until: origin + .seconds(at))
                guard !Task.isCancelled else { return }
                autoKey = String(digit)
                enter(String(digit), hand: false)
                try? await clock.sleep(until: origin + .seconds(at + M.keyFor))
                guard !Task.isCancelled else { return }
                autoKey = nil
            }
            try? await clock.sleep(until: origin + .seconds(M.typeAt + Double(code.count - 1) * M.typeEvery + M.resetAfter))
            guard !Task.isCancelled else { return }
            reset()
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
        let code: String
    }

    private struct KeyStyle: ButtonStyle {
        let held: Bool
        func makeBody(configuration: Configuration) -> some View {
            let pressed = configuration.isPressed || held
            configuration.label
                .background(
                    RoundedRectangle(cornerRadius: S.keyFlashRadius, style: .continuous)
                        .fill(.white.opacity(0.09))
                        .frame(width: S.keyFlash.width, height: S.keyFlash.height)
                        .opacity(pressed ? 1 : 0)
                        .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: pressed ? M.flashIn : M.flashOut), value: pressed)
                )
        }
    }

    private struct ResendStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .opacity(configuration.isPressed ? 0.6 : 1)
                .contentShape(Rectangle().inset(by: -12))
        }
    }
}

private struct NeonOtpVerifyBox: View {
    let index: Int
    let count: Int
    let size: Double
    let digit: String
    let focused: Bool
    let wrong: Bool
    let accent: Color
    let neon: Color
    let t: Double
    let away: Double
    let still: Bool
    let date: Date
    @State private var focus = Tween(0)
    @State private var border = Tween(0)
    @State private var digitStart = Date.distantPast

    private typealias M = NeonOtpVerifyMotion
    private typealias S = NeonOtpVerifyMetrics
    private typealias P = NeonOtpVerifyPalette

    var body: some View {
        let radius = S.boxRadius * size / S.box
        let home = (Double(index) - Double(count - 1) / 2) * (size + S.boxGap)
        let back = M.smooth(away, 0.5, 1)
        let lift = still ? 0 : M.smooth(t, M.liftAt, M.liftAt + M.liftFor)
        let deck = still ? 0 : M.smooth(t, M.mergeAt, M.mergeAt + M.deckFor)
        let close = still ? 0 : M.smooth(t, M.mergeAt + M.deckFor, M.mergeAt + M.deckFor + M.closeFor)
        let rank = Double(index) - Double(count - 1) / 2
        let x = home + (rank * S.deckStep * (1 - close) - home) * deck
        let scale = away > 0 ? 0.92 + 0.08 * back : 1 + S.lift * lift + (index == 0 ? S.deckFront * deck : 0)
        let dim = index == 0 ? 0 : 0.5 * M.smooth(t, M.liftAt, M.mergeAt)
        let gone = M.smooth(t, M.mergeAt, M.mergeAt + M.deckFor)
        let pop = M.digitIn(date.timeIntervalSince(digitStart))
        ZStack {
            RoundedRectangle(cornerRadius: radius, style: .circular)
                .fill(P.boxFill)
                // A CSS shadow's blur radius is twice the Gaussian radius.
                .shadow(color: accent.opacity(0.4), radius: S.focusBlur / 2)
                .frame(width: size, height: size)
                .opacity(focus.value(at: date))
            RoundedRectangle(cornerRadius: radius, style: .circular)
                .fill(P.boxFill)
                .overlay {
                    RoundedRectangle(cornerRadius: radius, style: .circular)
                        .strokeBorder(P.boxBorder, lineWidth: S.borderWidth)
                    RoundedRectangle(cornerRadius: radius, style: .circular)
                        .strokeBorder(accent, lineWidth: S.borderWidth)
                        .opacity(border.value(at: date))
                }
                // The canvas below is larger than the box by the light's spill, and the stack with it.
                .frame(width: size, height: size)
            Canvas { context, _ in
                let outline = M.outline(size: size - S.borderWidth, radius: radius - 0.75, origin: S.spill + 0.75)
                let end = still ? 1 : M.clamp01((t - M.traceAt) / M.traceFor)
                let lit = t < M.traceAt || away > 0 ? 0 : still ? M.smooth(t, M.traceAt, M.traceAt + M.traceFor) : 1
                let body = lit * (0.5 + 0.5 * M.smooth(end, 0.82, 1))
                let head = lit * (1 - M.smooth(end, 0.9, 1))
                let inside = lit * M.smooth(t, M.traceAt + M.traceFor * 0.45, M.liftAt) * (1 - 0.55 * M.smooth(t, M.liftAt, M.mergeAt))
                let fill = RoundedRectangle(cornerRadius: radius, style: .circular)
                    .path(in: CGRect(x: S.spill, y: S.spill, width: size, height: size))
                var inner = context
                inner.opacity *= inside
                inner.fill(fill, with: .linearGradient(
                    Gradient(stops: [.init(color: neon.opacity(20.0 / 255), location: 0), .init(color: neon.opacity(46.0 / 255), location: 0.5), .init(color: neon.opacity(140.0 / 255), location: 1)]),
                    startPoint: CGPoint(x: 0, y: S.spill), endPoint: CGPoint(x: 0, y: S.spill + size)
                ))
                let trace = outline.trimmedPath(from: 0, to: end)
                let tip = outline.trimmedPath(from: max(0, end - S.head), to: end)
                M.stroke(trace, in: context, color: neon, width: 6, opacity: body, blur: 9)
                M.stroke(tip, in: context, color: neon, width: 7, opacity: head, blur: 12)
                M.stroke(trace, in: context, color: neon, width: 2, opacity: lit)
                M.stroke(tip, in: context, color: P.hot, width: 2, opacity: head, blur: 0.6)
            }
            .frame(width: size + S.spill * 2, height: size + S.spill * 2)
            if !digit.isEmpty {
                Text(digit)
                    .font(.system(size: S.digitSize, weight: .medium))
                    .monospacedDigit()
                    .foregroundStyle(P.ink)
                    .frame(height: S.digitHeight)
                    .opacity(still ? 1 : M.clamp01(pop))
                    .scaleEffect(still ? 1 : M.digitFrom + (1 - M.digitFrom) * pop)
                    .opacity(t < 0 || away > 0 ? 1 : (1 - dim) * (1 - gone))
                    .scaleEffect(t < 0 || away > 0 ? 1 : 1 - 0.5 * gone)
            }
        }
        .frame(width: size, height: size)
        .modifier(NeonOtpVerifyDeckTransform(
            scale: scale,
            lean: away > 0 ? 0 : S.lean[index % S.lean.count] * lift * (1 - close),
            turn: away > 0 || index == 0 ? 0 : S.deckTurn * deck * (1 - close)
        ))
        .offset(x: away > 0 ? 0 : x - home)
        .opacity(away > 0 ? back : t < 0 ? 1 : 1 - M.smooth(t, M.tileAt, M.tileAt + M.tileIn))
        .allowsHitTesting(false)
        .onAppear {
            focus.retarget(focused ? 1 : 0, duration: 0, curve: .linear)
            border.retarget(focused || wrong ? 1 : 0, duration: 0, curve: .linear)
            if !digit.isEmpty { digitStart = .now }
        }
        .onChange(of: focused) { _, value in
            focus.retarget(value ? 1 : 0, duration: S.focusFor, curve: M.cssEase)
        }
        .onChange(of: focused || wrong) { _, value in
            border.retarget(value ? 1 : 0, duration: S.focusFor, curve: M.cssEase)
        }
        .onChange(of: digit) { _, value in
            if !value.isEmpty { digitStart = .now }
        }
    }

}

/// The right-edge pivot and 500-point perspective preserve the fanned deck's narrow slivers.
private struct NeonOtpVerifyDeckTransform: GeometryEffect {
    let scale: Double
    let lean: Double
    let turn: Double

    func effectValue(size: CGSize) -> ProjectionTransform {
        var transform = CATransform3DIdentity
        transform.m34 = -1 / NeonOtpVerifyMetrics.perspective
        transform = CATransform3DTranslate(transform, size.width / 2, size.height / 2, 0)
        transform = CATransform3DScale(transform, scale, scale, 1)
        transform = CATransform3DRotate(transform, lean * .pi / 180, 0, 0, 1)
        transform = CATransform3DTranslate(transform, size.width / 2, 0, 0)
        transform = CATransform3DRotate(transform, turn * .pi / 180, 0, 1, 0)
        transform = CATransform3DTranslate(transform, -size.width, -size.height / 2, 0)
        return ProjectionTransform(transform)
    }
}

private struct NeonOtpVerifyHaze: View {
    let t: Double
    let away: Double
    let still: Bool
    let neon: Color
    private typealias M = NeonOtpVerifyMotion
    private typealias S = NeonOtpVerifyMetrics

    var body: some View {
        Canvas { context, size in
            let gathered = M.smooth(t, M.mergeAt, M.tileAt + M.tileIn)
            let rx = S.hazeRowRX * size.width + (S.hazeTileRadius - S.hazeRowRX * size.width) * gathered
            let ry = S.hazeRowRY + (S.hazeTileRadius - S.hazeRowRY) * gathered
            let grown = S.hazeRowAlpha * M.smooth(t, M.traceAt, M.liftAt)
            let burst = still ? S.hazeTileRest : S.hazeTileBurst + (S.hazeTileRest - S.hazeTileBurst) * M.smooth(t, M.tileAt + M.popFor, M.tileAt + 0.8)
            let opacity = t < 0 ? 0 : (grown + (burst - grown) * gathered) * (1 - M.smooth(away, 0, 0.5))
            var haze = context
            haze.opacity *= opacity
            haze.fillEllipse(
                center: CGPoint(x: size.width / 2, y: S.hazeCanvas / 2 + S.hazeRowDrop * (1 - gathered)),
                radii: CGSize(width: rx, height: ry),
                gradient: Gradient(stops: zip(S.falloffAt, S.falloff).map { .init(color: neon.opacity($0.1), location: $0.0) })
            )
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

private struct NeonOtpVerifyTile: View {
    let t: Double
    let away: Double
    let still: Bool
    let neon: Color
    let box: Double
    private typealias M = NeonOtpVerifyMotion
    private typealias S = NeonOtpVerifyMetrics
    private typealias P = NeonOtpVerifyPalette

    var body: some View {
        Canvas { context, size in
            let opacity = t < 0 ? 0 : M.smooth(t, M.tileAt, M.tileAt + M.tileIn) * (1 - M.smooth(away, 0, 0.5))
            guard opacity > 0 else { return }
            var drawn = context
            drawn.opacity *= opacity
            let centre = CGPoint(x: size.width / 2, y: size.height / 2)
            let age = t - M.tileAt
            let u = M.clamp01(age / M.popFor)
            let up = S.tileEnter + (S.tileBurst - S.tileEnter) * (1 - (1 - u) * (1 - u))
            let spring = 1 + (S.tileBurst - 1) * M.springLeft(age - M.popFor, omega: S.tileSpringOmega, zeta: S.tileSpringZeta)
            let scale = still ? 1 : (age < M.popFor ? up : spring) * (1 - 0.12 * M.smooth(away, 0, 0.5))
            var tile = drawn.scaled(scale, about: centre)
            let rect = CGRect(x: (size.width - box) / 2, y: (size.height - box) / 2, width: box, height: box)
            let outline = RoundedRectangle(cornerRadius: S.boxRadius * box / S.box, style: .circular).path(in: rect)
            let heat = still ? 0 : 1 - M.smooth(t, M.tileAt + M.popFor, M.tileAt + 0.75)
            var flare = tile
            flare.opacity *= 0.5 * heat
            flare.addFilter(.blur(radius: 30))
            flare.fill(outline, with: .color(neon))
            M.stroke(outline, in: tile, color: neon, width: 12, opacity: 0.3 + 0.7 * heat, blur: 24)
            M.stroke(outline, in: tile, color: neon, width: 5, blur: 7)
            tile.fill(outline, with: .color(P.tileFill))
            var inner = tile
            inner.opacity *= 0.2 + 0.8 * heat
            inner.fill(outline, with: .linearGradient(
                Gradient(colors: [neon.opacity(31.0 / 255), neon.opacity(71.0 / 255)]),
                startPoint: CGPoint(x: 0, y: rect.minY), endPoint: CGPoint(x: 0, y: rect.maxY)
            ))
            M.stroke(outline, in: tile, color: neon, width: 2)
            M.stroke(outline, in: tile, color: P.warm, width: 2, opacity: 0.8 * heat)
            let check = M.smooth(t, M.tileAt + 0.02, M.tileAt + 0.02 + M.checkFor)
            let dot = M.smooth(t, M.tileAt - 0.04, M.tileAt + 0.02)
            tile.translateBy(x: centre.x, y: centre.y)
            tile.scaleBy(x: box / S.box, y: box / S.box)
            var path = Path()
            path.move(to: CGPoint(x: -12, y: 1.5))
            path.addLine(to: CGPoint(x: -4, y: 9.5))
            path.addLine(to: CGPoint(x: 13, y: -9.5))
            if check == 0 {
                tile.fill(Path(ellipseIn: CGRect(x: -13.6, y: -0.1, width: 3.2, height: 3.2)), with: .color(P.ink.opacity(dot)))
            } else {
                M.stroke(path.trimmedPath(from: 0, to: check), in: tile, color: P.ink, width: 3.2, opacity: dot)
            }
            if !still {
                for ember in S.embers {
                    let emberAge = t - M.tileAt - ember[1]
                    let e = max(0, emberAge)
                    let x = centre.x + ember[0] + ember[2] * e
                    let y = centre.y + S.emberFrom + ember[3] * e + 0.5 * S.emberGravity * e * e
                    let alpha = 0.9 * M.smooth(emberAge, 0, 0.06) * (1 - M.smooth(emberAge, S.emberFor * 0.4, S.emberFor))
                    let r = ember[4]
                    drawn.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)), with: .color(neon.opacity(alpha)))
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

#Preview {
    NeonOtpVerify(autoPlay: true)
}
