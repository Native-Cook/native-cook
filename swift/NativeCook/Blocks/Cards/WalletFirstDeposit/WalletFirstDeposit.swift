import SwiftUI
import UIKit

struct WalletFirstDeposit: View {
    static let loop = 10.0
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var playback = WalletFirstDepositPlayback()
    @State private var reducedFunded = true
    @State private var reducedOpacity = 1.0
    @State private var reducedTask: Task<Void, Never>?
    @State private var bannerPress = 0.0
    @State private var cardPress = 0.0
    @State private var voiceOver = UIAccessibility.isVoiceOverRunning
    @State private var lastLanding: Int?
    @State private var landings = 0
    @State private var clockStopped = false

    var body: some View {
        GeometryReader { proxy in
            TimelineView(.animation(paused: clockStopped || reduceMotion || scenePhase != .active)) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let phase = reduceMotion ? (reducedFunded ? 6.2 : 0) : playback.phase(at: now)
                let frame = WalletFirstDepositFrame(at: phase)
                let scale = proxy.size.width / WalletFirstDepositMetrics.referenceWidth
                // The reader starts inside the safe area; subtract the reference inset once.
                let y0 = -WalletFirstDepositMetrics.referenceInset * scale
                let height = max(proxy.size.height, y0 + WalletFirstDepositMetrics.contentBottom * scale)
                ScrollView(.vertical, showsIndicators: false) {
                    ZStack(alignment: .topLeading) {
                        Color.white
                        WalletFirstDepositBackdrop(frame: frame, scale: scale, y0: y0, bannerPress: bannerPress)
                        WalletFirstDepositText(frame: frame, scale: scale, y0: y0, bannerPress: bannerPress)
                            .accessibilityHidden(true)
                        WalletFirstDepositDebitCard(frame: frame, scale: scale, y0: y0, cardPress: cardPress, bannerPress: bannerPress)
                        Color.clear
                            .frame(width: 800 * scale, height: 220 * scale)
                            .position(x: 445 * scale, y: y0 + 535 * scale)
                            .accessibilityElement()
                            .accessibilityLabel(summary(for: phase))
                        if phase < 2.75 || phase >= 9.7 {
                            Button(action: { activate(.fund) }) { Color.clear }
                                .buttonStyle(WalletFirstDepositPressStyle { pressChanged($0, card: false) })
                                .frame(width: 1159.25 * scale, height: 313.8 * scale)
                                .position(x: 601.875 * scale, y: y0 + 901.9 * scale)
                                .accessibilityLabel("Complete verification and preview your first deposit")
                                .accessibilityHint("This is a local demonstration")
                        }
                        if phase >= 3.3 && phase < 9 {
                            Button(action: { activate(.empty) }) { Color.clear }
                                .buttonStyle(WalletFirstDepositPressStyle { pressChanged($0, card: true) })
                                .frame(width: 232 * 0.752 * scale + 30, height: 147 * 0.752 * scale + 30)
                                .position(x: 595.5 * scale, y: y0 + 268.5 * scale)
                                .accessibilityLabel("Debit card. Return to empty wallet")
                        }
                    }
                    .frame(width: proxy.size.width, height: height, alignment: .topLeading)
                    .contentShape(Rectangle())
                }
                .simultaneousGesture(DragGesture(minimumDistance: 0)
                    .onChanged { _ in
                        if !reduceMotion { playback.beginTouch(at: clock) }
                    }
                    .onEnded { _ in releaseTouch() })
                .background(Color.white.ignoresSafeArea())
                .ignoresSafeArea(.container, edges: .bottom)
                .clipped()
                .opacity(reducedOpacity)
                .onChange(of: playback.landingToken(at: now)) { _, token in
                    guard let token, token != lastLanding, !reduceMotion else { return }
                    lastLanding = token
                    landings += 1
                }
                .onChange(of: playback.shouldTick(at: now)) { _, moving in
                    if !moving { clockStopped = true }
                }
            }
        }
        .onAppear {
            lastLanding = nil
            playback.start(autoPlay: autoPlay && !reduceMotion && !voiceOver, at: clock)
            playback.setActive(scenePhase == .active, at: clock)
            clockStopped = !playback.shouldTick(at: clock)
        }
        .onDisappear { reducedTask?.cancel() }
        .onChange(of: scenePhase) { _, next in
            if next != .active { bannerPress = 0; cardPress = 0 }
            playback.setActive(next == .active, at: clock)
            clockStopped = !playback.shouldTick(at: clock)
        }
        .onChange(of: autoPlay) { _, value in
            playback.setAutoPlay(value && !reduceMotion && !voiceOver, at: clock)
            clockStopped = !playback.shouldTick(at: clock)
        }
        .onChange(of: reduceMotion) { _, reduced in
            reducedTask?.cancel()
            reducedOpacity = 1
            lastLanding = nil
            bannerPress = 0
            cardPress = 0
            if reduced { reducedFunded = true }
            playback.start(autoPlay: autoPlay && !reduced && !voiceOver, at: clock, phase: reducedFunded ? 6.2 : 0)
            clockStopped = !playback.shouldTick(at: clock)
        }
        .onReceive(NotificationCenter.default.publisher(for: UIAccessibility.voiceOverStatusDidChangeNotification)) { _ in
            voiceOver = UIAccessibility.isVoiceOverRunning
            if voiceOver {
                // Do not strand assistive navigation in the untappable flight phase.
                playback.start(autoPlay: false, at: clock, phase: 6.2)
                lastLanding = nil
            } else {
                playback.setAutoPlay(autoPlay && !reduceMotion, at: clock)
            }
            clockStopped = !playback.shouldTick(at: clock)
        }
        .sensoryFeedback(.impact(weight: .light), trigger: landings)
    }

    private var clock: Double { Date().timeIntervalSinceReferenceDate }

    private func pressChanged(_ pressed: Bool, card: Bool) {
        guard !reduceMotion else { return }
        if pressed { playback.beginTouch(at: clock) }
        withAnimation(.timingCurve(0.23, 1, 0.32, 1, duration: pressed ? 0.12 : 0.1)) {
            if card { cardPress = pressed ? 1 : 0 }
            else { bannerPress = pressed ? 1 : 0 }
        }
        if !pressed { releaseTouch() }
    }

    private func releaseTouch() {
        if playback.isHeld { playback.endTouch(nil, at: clock) }
        withAnimation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.1)) {
            bannerPress = 0
            cardPress = 0
        }
    }

    private func activate(_ action: WalletFirstDepositPlayback.Action) {
        if reduceMotion {
            reducedTask?.cancel()
            reducedTask = Task { @MainActor in
                withAnimation(.easeInOut(duration: 0.1)) { reducedOpacity = 0 }
                do { try await Task.sleep(for: .seconds(0.1)) } catch { return }
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction) { reducedFunded = action == .fund }
                withAnimation(.easeInOut(duration: 0.1)) { reducedOpacity = 1 }
            }
        } else {
            playback.endTouch(action, at: clock)
            clockStopped = !playback.shouldTick(at: clock)
        }
    }

    private func summary(for phase: Double) -> String {
        if phase < 1.4 {
            return "Wallet. Total balance zero dollars. Verification pending. No recent payments. Spent this week zero dollars."
        }
        if phase < 6.2 { return "Wallet. Completing the first deposit preview." }
        if phase >= 8.4 { return "Wallet. Returning to the empty preview." }
        return "Wallet. Total balance 6,790 dollars and 22 cents. Transactions: Hearth Coffee, purchase 4 dollars and 85 cents. Dieter E., received 109 dollars and 25 cents. Recent payees: Konstantin, Vasya, Aleksa, Joshua. Spent this week 983 dollars."
    }
}
