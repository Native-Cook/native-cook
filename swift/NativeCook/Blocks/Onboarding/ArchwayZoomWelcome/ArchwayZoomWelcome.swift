import SwiftUI

/// A sign-in sheet under a pale scene the camera travels through without end: into a stone archway,
/// up to the bar chart behind it until two green bars tower over the sheet, and on through them to
/// the next archway. A drag up or down over the scene moves the camera by hand.
struct ArchwayZoomWelcome: View {
    var brand = "VERRA"
    /// Two lines, split with a line break.
    var title = "Smarter financial moves\nawait"
    var continueLabel = "Continue with Passkey"
    var signInLabel = "Sign in with your Verra Account"
    /// The small print: what comes before the two links, the links, and the word between them.
    var legal = "by proceeding, you agree to our "
    var termsLabel = "terms of use"
    var legalJoin = " & "
    var privacyLabel = "privacy policy"
    /// Travels through the scene by itself, one round every `ArchwayZoomWelcomeScene.loop` seconds.
    /// Off, the scene stays where a drag leaves it.
    var autoPlay = false
    var onContinue: (() -> Void)? = nil
    var onSignIn: (() -> Void)? = nil
    var onTerms: (() -> Void)? = nil
    var onPrivacy: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Where the camera was last let go: how many rounds in, when, and how fast it was thrown.
    @State private var from = Throw(rounds: 0, at: .now, speed: nil)
    /// The rounds while a finger holds the scene, and what they were when it took it.
    @State private var hold: Double? = nil
    @State private var grabbed = 0.0
    /// The clock runs on for a moment after a throw when the camera has no pace of its own.
    @State private var settling = false
    @State private var throwsMade = 0
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)

    private struct Throw {
        var rounds: Double
        var at: Date
        /// Rounds a second; nil for the camera's own pace.
        var speed: Double?
    }

    /// The camera's own pace, in rounds a second.
    private var rate: Double { autoPlay && !reduceMotion ? 1 / ArchwayZoomWelcomeScene.loop : 0 }

    /// The rounds travelled: a throw eases into the camera's pace, so the speed never jumps.
    private func rounds(at date: Date, rate: Double) -> Double {
        if let hold { return hold }
        let t = max(0, date.timeIntervalSince(from.at))
        return from.rounds + rate * t + ((from.speed ?? rate) - rate) / Metrics.ease * (1 - exp(-Metrics.ease * t))
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let bottom = max(insets.bottom, 12) + Metrics.bottom
            let sheetTop = screen.height - bottom - Metrics.sheetHeight

            ZStack(alignment: .bottom) {
                TimelineView(.animation(paused: rate == 0 && hold == nil && !settling)) { timeline in
                    let travelled = rounds(at: timeline.date, rate: rate)
                    Canvas { context, size in
                        ArchwayZoomWelcomeScene.paint(
                            context, size: size, horizon: sheetTop - ArchwayZoomWelcomeScene.horizon,
                            tau: (travelled - travelled.rounded(.down)) * ArchwayZoomWelcomeScene.loop
                        )
                    }
                }
                .ignoresSafeArea()
                .accessibilityHidden(true)
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            if hold == nil { grabbed = rounds(at: .now, rate: rate) }
                            // Up is forwards.
                            hold = grabbed - value.translation.height / Metrics.drag
                        }
                        .onEnded { value in
                            let speed = min(Metrics.throwMax, max(-Metrics.throwMax, -value.velocity.height / Metrics.drag))
                            from = Throw(rounds: hold ?? grabbed, at: .now, speed: speed)
                            hold = nil
                            settling = true
                            throwsMade += 1
                        },
                    including: reduceMotion ? .none : .all
                )

                sheet
                    .padding(.horizontal, Metrics.sheetSide)
                    .padding(.bottom, bottom - insets.bottom)
            }
        }
        .preferredColorScheme(.light)
        .onAppear { tapHaptic.prepare() }
        .onChange(of: rate) { old, _ in
            // A change of pace starts from where the camera is.
            from = Throw(rounds: rounds(at: .now, rate: old), at: .now, speed: old)
        }
        .task(id: throwsMade) {
            guard throwsMade > 0 else { return }
            try? await Task.sleep(for: .seconds(Metrics.settle))
            if !Task.isCancelled { settling = false }
        }
    }

    private var sheet: some View {
        VStack(spacing: 0) {
            Text(brand)
                .font(.system(size: 14, weight: .bold))
                .tracking(2.5)
                // The spacing after the last letter would push the word off centre.
                .padding(.trailing, -2.5)
                .frame(height: 18)
                .padding(.top, 35)
            Text(title)
                .font(.system(size: 21, weight: .semibold))
                .lineSpacing(Metrics.titleLeading)
                .padding(.vertical, Metrics.titleLeading / 2)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .padding(.top, 21)
            Button {
                tapHaptic.impactOccurred()
                onContinue?()
            } label: {
                HStack(spacing: 10) {
                    ArchwayZoomWelcomeShield()
                        .stroke(.white, style: StrokeStyle(lineWidth: 1.3, lineJoin: .round))
                        .frame(width: 16, height: 16)
                    Text(continueLabel)
                        .font(.system(size: 13, weight: .semibold))
                        .lineLimit(1)
                    ArchwayZoomWelcomeArrow()
                        .stroke(.white, style: StrokeStyle(lineWidth: 1, lineCap: .round, lineJoin: .round))
                        .frame(width: 18, height: 12)
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: Metrics.button)
                .background(RoundedRectangle(cornerRadius: 2).fill(Palette.button))
            }
            .buttonStyle(PressStyle())
            .padding(.horizontal, Metrics.inset)
            .padding(.top, 25)
            Button {
                onSignIn?()
            } label: {
                Text(signInLabel)
                    .font(.system(size: 12.5, weight: .semibold))
                    .lineLimit(1)
                    .frame(height: 18)
                    .contentShape(Rectangle())
            }
            .buttonStyle(DimStyle())
            .padding(.top, 24)
            HStack(spacing: 0) {
                Text(legal)
                Button { onTerms?() } label: { Text(termsLabel).underline() }
                Text(legalJoin)
                Button { onPrivacy?() } label: { Text(privacyLabel).underline() }
            }
            .buttonStyle(DimStyle())
            .font(.system(size: 8))
            .foregroundStyle(Palette.mute)
            .lineLimit(1)
            .frame(height: 12)
            .padding(.top, 30)
            Spacer(minLength: 0)
        }
        .foregroundStyle(Palette.ink)
        .frame(maxWidth: .infinity)
        .frame(height: Metrics.sheetHeight)
        .background(
            RoundedRectangle(cornerRadius: Metrics.sheetRadius, style: .continuous)
                .fill(Palette.sheet)
                .shadow(color: .black.opacity(0.1), radius: 15, y: 10)
        )
    }

    private struct PressStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? Metrics.pressScale : 1)
                .animation(.easeInOut(duration: 0.12), value: configuration.isPressed)
        }
    }

    private struct DimStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label.opacity(configuration.isPressed ? 0.5 : 1)
        }
    }
}

private enum Palette {
    static let sheet = Color(hex: 0xfafafa)
    static let ink = Color(hex: 0x1c1c1e)
    static let mute = Color(hex: 0x8e8e93)
    static let button = Color(hex: 0x111111)
}

private enum Metrics {
    static let sheetSide = 22.0
    static let sheetHeight = 315.0
    static let sheetRadius = 20.0
    static let bottom = 27.0
    static let inset = 24.0
    static let button = 49.0
    static let pressScale = 0.98
    /// What a 21 point line lacks to be 28 tall.
    static let titleLeading = 28 - UIFont.systemFont(ofSize: 21, weight: .semibold).lineHeight
    /// A drag of this many points up moves the camera on by one round.
    static let drag = 500.0
    /// The fastest a release may throw the camera, in rounds a second, and how fast a throw eases
    /// into the camera's own pace (per second).
    static let throwMax = 1.5
    static let ease = 3.0
    /// How long the clock runs on after a throw: the throw has eased out by then.
    static let settle = 2.5
}

/// A shield in a 16 × 16 box.
struct ArchwayZoomWelcomeShield: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 16
        func point(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: rect.minX + x * s, y: rect.minY + y * s) }
        var path = Path()
        path.move(to: point(2, 2.6))
        path.addLine(to: point(8, 1))
        path.addLine(to: point(14, 2.6))
        path.addLine(to: point(14, 8))
        path.addQuadCurve(to: point(8, 15), control: point(14, 12.6))
        path.addQuadCurve(to: point(2, 8), control: point(2, 12.6))
        path.closeSubpath()
        return path
    }
}

/// A thin arrow in an 18 × 12 box.
struct ArchwayZoomWelcomeArrow: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 18
        func point(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: rect.minX + x * s, y: rect.minY + y * s) }
        var path = Path()
        path.move(to: point(1, 6))
        path.addLine(to: point(17, 6))
        path.move(to: point(12.5, 1.5))
        path.addLine(to: point(17, 6))
        path.addLine(to: point(12.5, 10.5))
        return path
    }
}

#Preview {
    ArchwayZoomWelcome(autoPlay: true)
}
