import SwiftUI

struct AuroraWalletTopUpWallet: Equatable {
    var name: String
    /// What the wallet holds, in units of the currency.
    var balance: Double
    /// The sky of its round picture, top to bottom.
    var sky: [Color]
    /// The far ridge and the near mountain of the picture.
    var ridge: Color
    var peak: Color

    static let samples = [
        AuroraWalletTopUpWallet(
            name: "Wallet 02", balance: 478,
            sky: [Color(hex: 0xc59af2), Color(hex: 0x7f73ee), Color(hex: 0x3d5bd8)], ridge: Color(hex: 0x2f49c4), peak: Color(hex: 0x14246e)
        ),
        AuroraWalletTopUpWallet(
            name: "Wallet 03", balance: 1250.4,
            sky: [Color(hex: 0xf2734f), Color(hex: 0xf7a65a), Color(hex: 0xfbdc9a)], ridge: Color(hex: 0xb5486a), peak: Color(hex: 0x5a2152)
        ),
        AuroraWalletTopUpWallet(
            name: "Wallet 01", balance: 62.15,
            sky: [Color(hex: 0x1f9e8f), Color(hex: 0x5cc7a4), Color(hex: 0xd7efb0)], ridge: Color(hex: 0x1d7a80), peak: Color(hex: 0x0f3f52)
        ),
    ]
}

/// A top-up screen: an amount typed on a number pad, the wallet it goes to, and Continue. Continue
/// blurs the form behind a spinner while light grows out of the button, fills the bottom of the
/// screen and breathes there, then gathers and sweeps up through the screen, leaving the result.
/// Nothing is paid here: `onContinue` is where the real request goes.
struct AuroraWalletTopUp: View {
    /// The amount the pad starts with.
    var amount = 100.0
    var currency = "USD"
    /// Written before a balance.
    var symbol = "$"
    /// The wallets the "Wallets" button goes through; the first one is shown.
    var wallets = AuroraWalletTopUpWallet.samples
    /// Presses Continue by itself every `AuroraWalletTopUpMotion.loop` seconds and leaves the balance as it was.
    var autoPlay = false
    /// Seconds from Continue to the moment the light sweeps up and the result shows.
    var processingDuration = 2.8
    var title = "Add money"
    var availableLabel = "Available"
    var walletsLabel = "Wallets"
    var continueLabel = "Continue"
    var processingLabel = "Topping up wallet"
    var doneLabel = "Wallet topped up"
    /// Called with the amount and the wallet when Continue is released and the wait starts.
    var onContinue: ((Double, AuroraWalletTopUpWallet) -> Void)? = nil
    /// Called when the result shows.
    var onDone: ((Double, AuroraWalletTopUpWallet) -> Void)? = nil
    var onBack: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The amount as typed, digits with at most one point; nil until a key is pressed.
    @State private var typed: String? = nil
    @State private var walletIndex = 0
    /// What was added to each wallet here, by its place in `wallets`.
    @State private var added: [Int: Double] = [:]
    /// The top-up in progress, from Continue until the form is back.
    @State private var run: Run? = nil
    @State private var runTask: Task<Void, Never>? = nil
    /// Continue held down by the autoPlay loop.
    @State private var autoPressed = false
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    // Made once and warmed up before they are needed, so a haptic never holds up a frame of the light.
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var doneHaptic = UINotificationFeedbackGenerator()

    private struct Run {
        let start: Date
        /// The wallet's balance before and after, for the count at the end.
        let from: Double
        let to: Double
    }

    private var draft: String { typed ?? Self.draft(for: amount) }
    private var value: Double { Double(draft) ?? 0 }
    private var wallet: AuroraWalletTopUpWallet { wallets[walletIndex] }
    /// What the wallet holds now: its balance plus the top-ups made here.
    private var held: Double { wallet.balance + (added[walletIndex] ?? 0) }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let bottom = max(insets.bottom - 4, 16)
            let rowHeight = min(Metrics.keyRow, max(Metrics.keyRowMin, (screen.height - insets.top - bottom - Metrics.fixedHeight) / 4))
            let bar = CGRect(
                x: Metrics.buttonMargin, y: screen.height - bottom - Metrics.button,
                width: screen.width - Metrics.buttonMargin * 2, height: Metrics.button
            )

            TimelineView(.animation(paused: run == nil)) { timeline in
                let e = run.map { timeline.date.timeIntervalSince($0.start) } ?? -1
                let busy = run != nil
                let back = M.returnAt(processingDuration)
                let blur = busy ? M.smooth(e, 0, M.blurIn) : 0
                let s = (e - processingDuration) / M.sweep
                let returned = busy ? M.smooth(e, back + M.formBackAfter, back + M.formBackAfter + M.formBackIn) : 1
                let formOpacity = !busy ? 1 : e < back ? (1 - Metrics.dim * blur) * (1 - M.smooth(s, 0.05, 0.4)) : returned
                let statusOpacity = busy ? M.smooth(e, 0.05, M.blurIn) * (1 - M.smooth(e, back, back + M.statusOut)) : 0
                let done = busy ? M.smooth(e, processingDuration + M.resultAfter, processingDuration + M.resultAfter + M.resultIn) : 0
                let counted = M.easeOut((e - back - M.countAfter) / M.countFor)
                let balance = run.map { $0.from + ($0.to - $0.from) * counted } ?? held

                ZStack {
                    Palette.dim.ignoresSafeArea()
                    Palette.background.opacity(1 - blur * (1 - returned)).ignoresSafeArea()

                    form(balance: balance, rowHeight: rowHeight, bottom: bottom)
                        .blur(radius: e < back ? Metrics.blur * blur : 0)
                        .opacity(formOpacity)
                        .allowsHitTesting(!busy)
                        .accessibilityHidden(busy)

                    if busy {
                        AuroraWalletTopUpAurora(elapsed: e, duration: processingDuration, still: reduceMotion, bar: bar)
                            .ignoresSafeArea()
                    }

                    continueButton
                        .frame(width: bar.width, height: bar.height)
                        .opacity(!busy ? 1 : e < back ? 1 - M.smooth(e, 0, 0.18) : returned)
                        .allowsHitTesting(!busy)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                        .padding(.bottom, bottom - insets.bottom)

                    status(spin: reduceMotion ? 0 : e * Metrics.spin, done: done)
                        .opacity(statusOpacity)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .ignoresSafeArea()
                        .allowsHitTesting(false)
                }
            }
        }
        .preferredColorScheme(.dark)
        .onAppear { tapHaptic.prepare() }
        .onDisappear { runTask?.cancel() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The form

    private func form(balance: Double, rowHeight: Double, bottom: Double) -> some View {
        VStack(spacing: 0) {
            ZStack {
                Text(title)
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(Palette.ink)
                Button {
                    touched()
                    onBack?()
                } label: {
                    AuroraWalletTopUpArrow()
                        .stroke(Palette.ink, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                        .frame(width: 18, height: 14)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back")
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.leading, 4)
            }
            .frame(height: Metrics.header)
            .padding(.top, 2)

            VStack(spacing: 22) {
                Text(Self.grouped(draft))
                    .font(.system(size: Self.amountSize(draft), weight: .semibold))
                    .tracking(-2.5)
                    .monospacedDigit()
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .frame(height: 100)
                    .accessibilityLabel("Amount")
                    .accessibilityValue("\(Self.grouped(draft)) \(currency)")
                Text(currency)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Palette.ink)
                    .padding(.horizontal, 13)
                    .frame(height: 32)
                    .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(Palette.well).shadow(color: Palette.well, radius: 3))
            }
            .offset(y: -8)
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            walletRow(balance: balance)

            VStack(spacing: 0) {
                ForEach(Metrics.keys, id: \.self) { row in
                    HStack(spacing: 0) {
                        ForEach(row, id: \.self) { key in
                            Button {
                                press(key)
                            } label: {
                                Group {
                                    if key == "back" {
                                        AuroraWalletTopUpArrow()
                                            .stroke(.white, style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
                                            .frame(width: 24, height: 18.7)
                                    } else {
                                        Text(key)
                                            .font(.system(size: 32, weight: .medium))
                                            .foregroundStyle(.white)
                                    }
                                }
                                .frame(width: Metrics.keyWidth, height: rowHeight)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(KeyStyle())
                            .accessibilityLabel(key == "back" ? "Delete" : key == "." ? "Point" : key)
                            if key != row.last { Spacer(minLength: 0) }
                        }
                    }
                }
            }
            .padding(.horizontal, Metrics.margin)
            .padding(.top, Metrics.padGap)

            Color.clear.frame(height: Metrics.buttonGap + Metrics.button + bottom)
        }
        .ignoresSafeArea(edges: .bottom)
    }

    private func walletRow(balance: Double) -> some View {
        HStack(spacing: 16) {
            AuroraWalletTopUpPicture(wallet: wallet)
                .frame(width: 48, height: 48)
            VStack(alignment: .leading, spacing: 4) {
                Text(wallet.name)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.white)
                Text("\(symbol)\(Self.money(balance)) \(availableLabel)")
                    .font(.system(size: 14))
                    .monospacedDigit()
                    .foregroundStyle(Palette.mute)
            }
            .lineLimit(1)
            Spacer(minLength: 8)
        }
        // A new wallet fades in over the old one.
        .id(walletIndex)
        .transition(.opacity)
        .padding(.leading, 14)
        .padding(.trailing, 100)
        .frame(height: Metrics.card)
        .frame(maxWidth: .infinity)
        .overlay(alignment: .trailing) {
            Button {
                touched()
                tapHaptic.impactOccurred()
                withAnimation(.easeInOut(duration: 0.18)) { walletIndex = (walletIndex + 1) % wallets.count }
            } label: {
                Text(walletsLabel)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Palette.ink)
                    .padding(.horizontal, 14)
                    .frame(height: 34)
                    .background(Capsule().fill(Palette.chip))
            }
            .buttonStyle(DimStyle())
            .padding(.trailing, 15)
        }
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(Palette.well)
                // The well has no hard edge: it sinks into the screen.
                .shadow(color: Palette.well, radius: 5)
        )
        .padding(.horizontal, Metrics.margin)
    }

    private var continueButton: some View {
        Button {
            touched()
            start(auto: false)
        } label: {
            Text(continueLabel)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Palette.buttonInk)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .buttonStyle(ContinueStyle(held: autoPressed))
        .disabled(value <= 0)
        .opacity(value > 0 ? 1 : 0.35)
        .animation(.easeOut(duration: 0.15), value: value > 0)
    }

    private func status(spin: Double, done: Double) -> some View {
        HStack(spacing: 10) {
            ZStack {
                Circle().stroke(Palette.status.opacity(0.3), lineWidth: 1.5)
                Circle()
                    .trim(from: 0, to: 0.3)
                    .stroke(Palette.status, style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
                    .rotationEffect(.degrees(spin - 90))
            }
            .padding(0.75)
            .opacity(1 - done)
            .overlay {
                Circle()
                    .fill(.white)
                    .scaleEffect(0.6 + 0.4 * done)
                    .opacity(done)
                AuroraWalletTopUpCheck()
                    .trim(from: 0, to: done)
                    .stroke(Palette.dim, style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
            }
            .frame(width: 18, height: 18)

            ZStack(alignment: .leading) {
                Text(processingLabel).opacity(1 - done)
                Text(doneLabel).opacity(done)
            }
            .font(.system(size: 20))
            .foregroundStyle(Palette.status)
            .lineLimit(1)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(done > 0.5 ? doneLabel : processingLabel)
    }

    // MARK: Behaviour

    private func press(_ key: String) {
        touched()
        tapHaptic.impactOccurred()
        let now = draft
        if key == "back" {
            typed = now.count > 1 ? String(now.dropLast()) : "0"
        } else if key == "." {
            if !now.contains(".") { typed = now + "." }
        } else if let point = now.firstIndex(of: ".") {
            if now.distance(from: point, to: now.endIndex) <= Metrics.decimals { typed = now + key }
        } else if now == "0" {
            typed = key
        } else if now.count < Metrics.digits {
            typed = now + key
        }
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        autoPressed = false
        touches += 1
    }

    private func start(auto: Bool) {
        guard run == nil, value > 0 else { return }
        let sum = value
        let index = walletIndex
        let from = held
        // A loop that plays by itself must come back to the frame it started from.
        let to = auto ? from : from + sum
        run = Run(start: .now, from: from, to: to)
        // The loop that plays by itself is silent.
        if !auto {
            tapHaptic.impactOccurred()
            doneHaptic.prepare()
        }
        onContinue?(sum, wallet)
        runTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(processingDuration + M.resultAfter))
            guard !Task.isCancelled else { return }
            if !auto { doneHaptic.notificationOccurred(.success) }
            if !auto { added[index, default: 0] += sum }
            onDone?(sum, wallets[index])
            try? await Task.sleep(for: .seconds(M.end(processingDuration) - processingDuration - M.resultAfter))
            guard !Task.isCancelled else { return }
            run = nil
        }
    }

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            while run != nil, !Task.isCancelled { try? await Task.sleep(for: .seconds(0.1)) }
            // The loop starts over from the amount it was given.
            if !Task.isCancelled { typed = nil }
        }
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            try? await clock.sleep(until: origin + .seconds(M.pressAt))
            guard !Task.isCancelled else { return }
            autoPressed = true
            try? await clock.sleep(until: origin + .seconds(M.pressAt + M.pressFor))
            guard !Task.isCancelled else { return }
            autoPressed = false
            start(auto: true)
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    // MARK: Numbers

    /// "1250.5" → "1,250.5": thousands separators on the whole part, the rest as typed.
    private static func grouped(_ draft: String) -> String {
        let parts = draft.split(separator: ".", maxSplits: 1, omittingEmptySubsequences: false)
        var whole = String(parts[0])
        var out = ""
        while whole.count > 3 {
            out = "," + whole.suffix(3) + out
            whole = String(whole.dropLast(3))
        }
        return whole + out + (parts.count > 1 ? "." + parts[1] : "")
    }

    private static func money(_ value: Double) -> String {
        let cents = Int((value * 100).rounded())
        let fraction = cents % 100
        return grouped(String(cents / 100)) + "." + (fraction < 10 ? "0" : "") + String(fraction)
    }

    private static func draft(for amount: Double) -> String {
        let cents = Int((max(0, amount) * 100).rounded())
        if cents % 100 == 0 { return String(cents / 100) }
        let fraction = cents % 100
        return String(cents / 100) + "." + (fraction < 10 ? "0" : "") + (fraction % 10 == 0 ? String(fraction / 10) : String(fraction))
    }

    /// The amount gets smaller as it gets longer, so that six digits and two decimals still fit.
    private static func amountSize(_ draft: String) -> Double {
        let shown = grouped(draft)
        let marks = Double(shown.filter { $0 == "," || $0 == "." }.count)
        let units = Double(shown.count) - marks * 0.6
        return min(Metrics.amount, (Metrics.amountWidth / (units * 0.62)).rounded(.down))
    }

    // MARK: Styles

    private struct KeyStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .background(
                    Circle()
                        .fill(.white.opacity(0.1))
                        .frame(width: Metrics.flash, height: Metrics.flash)
                        .opacity(configuration.isPressed ? 1 : 0)
                        // On at once, off slowly: a flash.
                        .animation(.easeOut(duration: configuration.isPressed ? 0.04 : 0.35), value: configuration.isPressed)
                )
        }
    }

    private struct DimStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .opacity(configuration.isPressed ? 0.6 : 1)
                .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
        }
    }

    private struct ContinueStyle: ButtonStyle {
        let held: Bool

        func makeBody(configuration: Configuration) -> some View {
            let pressed = configuration.isPressed || held
            configuration.label
                .background(Capsule().fill(pressed ? Palette.buttonPressed : Palette.button))
                .contentShape(Capsule())
                .animation(.easeOut(duration: 0.1), value: pressed)
        }
    }
}

private typealias M = AuroraWalletTopUpMotion

private enum Palette {
    static let background = Color(hex: 0x171717)
    /// The screen behind the spinner and the result.
    static let dim = Color(hex: 0x121212)
    /// The wallet row and the currency pill sit in wells darker than the screen.
    static let well = Color(hex: 0x101010)
    static let chip = Color(hex: 0x222222)
    static let ink = Color(hex: 0xededed)
    static let mute = Color(hex: 0x7c7c7c)
    static let status = Color(hex: 0xd6d6d6)
    static let button = Color(hex: 0xf9f9f9)
    static let buttonPressed = Color(hex: 0x8c8c8c)
    static let buttonInk = Color(hex: 0x262626)
}

private enum Metrics {
    static let margin = 16.0
    static let header = 44.0
    static let amount = 84.0
    /// The widest the amount may get before it shrinks.
    static let amountWidth = 330.0
    static let card = 80.0
    static let padGap = 24.0
    static let keyRow = 68.0
    static let keyRowMin = 48.0
    static let keyWidth = 80.0
    static let flash = 64.0
    static let buttonGap = 58.0
    static let button = 56.0
    static let buttonMargin = 14.0
    /// Everything but the pad's rows, with 160 points for the amount: what is left sets the row height.
    static let fixedHeight = header + 2 + 160 + card + padGap + buttonGap + button
    /// Blur of the form behind the spinner.
    static let blur = 16.0
    /// How much of the form's brightness the wait takes away.
    static let dim = 0.5
    /// Degrees a second.
    static let spin = 400.0
    static let digits = 6
    static let decimals = 2
    static let keys = [["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"], [".", "0", "back"]]
}

/// A thin left arrow in an 18 × 14 box, scaled to the frame.
struct AuroraWalletTopUpArrow: Shape {
    func path(in rect: CGRect) -> Path {
        let sx = rect.width / 18
        let sy = rect.height / 14
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + 17 * sx, y: rect.minY + 7 * sy))
        path.addLine(to: CGPoint(x: rect.minX + 1 * sx, y: rect.minY + 7 * sy))
        path.move(to: CGPoint(x: rect.minX + 7 * sx, y: rect.minY + 1 * sy))
        path.addLine(to: CGPoint(x: rect.minX + 1 * sx, y: rect.minY + 7 * sy))
        path.addLine(to: CGPoint(x: rect.minX + 7 * sx, y: rect.minY + 13 * sy))
        return path
    }
}

/// A check in an 18 × 18 box.
struct AuroraWalletTopUpCheck: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 18
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + 5.2 * s, y: rect.minY + 9.4 * s))
        path.addLine(to: CGPoint(x: rect.minX + 8 * s, y: rect.minY + 12.1 * s))
        path.addLine(to: CGPoint(x: rect.minX + 12.9 * s, y: rect.minY + 6.4 * s))
        return path
    }
}

/// The wallet's round picture: a sky, a far ridge and a mountain with snow on it.
struct AuroraWalletTopUpPicture: View {
    let wallet: AuroraWalletTopUpWallet

    static let ridge: [(Double, Double)] = [(0, 0.60), (0.16, 0.50), (0.30, 0.57), (0.50, 0.40), (0.68, 0.55), (0.84, 0.47), (1, 0.56), (1, 1), (0, 1)]
    static let peak: [(Double, Double)] = [(0, 0.88), (0.20, 0.70), (0.42, 0.50), (0.52, 0.60), (0.62, 0.53), (0.82, 0.76), (1, 0.84), (1, 1), (0, 1)]
    static let snow: [(Double, Double)] = [(0.42, 0.50), (0.52, 0.60), (0.455, 0.63), (0.41, 0.59), (0.35, 0.625), (0.33, 0.585)]

    var body: some View {
        Canvas { context, size in
            func shape(_ points: [(Double, Double)]) -> Path {
                var path = Path()
                path.addLines(points.map { CGPoint(x: $0.0 * size.width, y: $0.1 * size.height) })
                path.closeSubpath()
                return path
            }
            context.fill(
                Path(CGRect(origin: .zero, size: size)),
                with: .linearGradient(Gradient(colors: wallet.sky), startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height * 0.7))
            )
            context.fill(shape(Self.ridge), with: .color(wallet.ridge))
            context.fill(shape(Self.peak), with: .color(wallet.peak))
            context.fill(shape(Self.snow), with: .color(.white.opacity(0.85)))
        }
        .clipShape(Circle())
        .accessibilityHidden(true)
    }
}

#Preview {
    AuroraWalletTopUp(autoPlay: true)
}
