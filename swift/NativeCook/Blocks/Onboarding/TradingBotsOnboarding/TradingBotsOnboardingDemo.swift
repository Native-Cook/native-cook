import SwiftUI

struct TradingBotsOnboardingDemo: View {
    var body: some View { TradingBotsExperience() }
}

private struct TradingBotsExperience: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var flow = TradingBotsFlow(start: TradingBotsExperience.launchStep)
    @State private var revision = 0
    @State private var showQA = false
    @State private var forceReducedMotion = false
    @State private var replayToken = 0
    @State private var proInteracted = false
    @State private var ctaDimmed = false
    @State private var ctaPressed = false
    @State private var detail: TradingBotsDetail?
    private var reduced: Bool { reduceMotion || forceReducedMotion }

    /// `-tbStep <0…5>` opens a scene directly (QA and screenshots).
    private static var launchStep: TradingBotsFlow.Step {
        TradingBotsFlow.Step(rawValue: UserDefaults.standard.integer(forKey: "tbStep")) ?? .wallet
    }

    var body: some View {
        GeometryReader { geometry in
            let scale = min(geometry.size.width / 402, geometry.size.height / 874)
            ZStack {
                background
                prewarm
                scene.id("\(flow.step.rawValue)-\(revision)")
                    .transition(.asymmetric(insertion: .identity, removal: .opacity))
                header
                footer
            }
            .frame(width: 402, height: 874)
            .scaleEffect(scale)
            .frame(width: geometry.size.width, height: geometry.size.height)
        }
        .ignoresSafeArea()
        .background(Color.black.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .foregroundStyle(.white)
        .transaction { if reduced { $0.animation = nil } }
        .sheet(isPresented: $showQA) {
            TradingBotsQAHarness(step: flow.step, reduced: $forceReducedMotion,
                show: { destination in showQA = false; navigate(destination) },
                replay: { showQA = false; flow.reset(); revision += 1; replayToken += 1 },
                reset: { showQA = false; replayToken = 0; flow.reset(); revision += 1 })
        }
        .sheet(item: $detail) { item in
            TradingBotsDetailSheet(detail: item, risk: flow.risk.rawValue, allocation: Int(flow.allocation),
                replay: { detail = nil; replayToken = 0; flow.reset(); revision += 1 })
        }
        .task(id: "\(flow.step.rawValue)-\(revision)-\(reduced)") {
            guard flow.step == .pro, !reduced, !UserDefaults.standard.bool(forKey: "tbHoldFocus") else { return }
            do { try await Task.sleep(for: .seconds(2.0)) } catch { return }
            guard !proInteracted, flow.proFocus == 0 else { return }
            withAnimation(.spring(response: 0.75, dampingFraction: 0.86)) { flow.focusPro(1) }
        }
        .task(id: replayToken) {
            // `-tbAutoplay 1` plays the walkthrough on launch, timed like the reference video.
            if replayToken == 0, UserDefaults.standard.bool(forKey: "tbAutoplay") { replayToken = 1; return }
            guard replayToken != 0 else { return }
            for duration in [2.5, 3.6, 2.4, 3.0, 2.9] {
                do {
                    try await Task.sleep(for: .seconds(duration - 0.45))
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.7)) { ctaPressed = true }
                    try await Task.sleep(for: .seconds(0.45))
                } catch { ctaPressed = false; return }
                withAnimation(.spring(response: 0.28, dampingFraction: 0.7)) { ctaPressed = false }
                advance()
            }
        }
    }

    private var background: some View {
        LinearGradient(stops: flow.step == .pro
                       ? [.init(color: .black, location: 0), .init(color: Color(white: 0.075), location: 0.42), .init(color: Color(white: 0.068), location: 1)]
                       : [.init(color: .black, location: 0), .init(color: .black, location: 0.62), .init(color: Color(white: 0.075), location: 0.93), .init(color: Color(white: 0.068), location: 1)],
                       startPoint: .top, endPoint: .bottom)
            .animation(.easeInOut(duration: 0.4), value: flow.step == .pro)
    }

    /// Draws every shader and image once, invisibly, at launch, so the first visit to a
    /// scene does not stall on a pipeline compile or an image decode mid-transition.
    private var prewarm: some View {
        HStack(spacing: 0) {
            TradingBotsCharacter(kind: .orb, size: 4)
            TradingBotsCharacter(kind: .green, size: 4)
            TradingBotsCharacter(kind: .purple, size: 4)
            TradingBotsCharacter(kind: .red, size: 4)
            TradingBotsCharacter(kind: .avatar, size: 4)
            Image(decorative: "TradingBots-toggle-tile").resizable().frame(width: 4, height: 4)
            Image(decorative: "TradingBots-wallet-icon").resizable().frame(width: 4, height: 4)
        }
        .opacity(0.011).allowsHitTesting(false).accessibilityHidden(true)
        .position(x: 20, y: 870)
    }

    @ViewBuilder private var scene: some View {
        switch flow.step {
        case .wallet: TradingBotsWalletScene(reducedMotion: reduced)
        case .selection:
            TradingBotsSelectorScene(selection: Binding(get: { flow.selectedBot }, set: { flow.chooseBot($0) }), reducedMotion: reduced)
        case .setup:
            TradingBotsSetupScene(reducedMotion: reduced, configure: { navigate(.allocation) }, overview: { detail = .overview })
        case .allocation:
            TradingBotsAllocationScene(allocation: Binding(get: { flow.allocation }, set: { flow.allocate($0) }), risk: $flow.risk,
                autoTrading: $flow.autoTrading, maxLoss: $flow.maxDailyLoss, reducedMotion: reduced)
        case .results:
            TradingBotsResultsScene(reducedMotion: reduced, configure: { navigate(.allocation) }, overview: { detail = .overview })
        case .pro:
            TradingBotsProScene(focus: Binding(get: { flow.proFocus }, set: { proInteracted = true; flow.focusPro($0) }), reducedMotion: reduced)
        }
    }

    // Back (44 pt disc at 20…64), five-step progress from x 84, Skip at the right edge.
    private var header: some View {
        ZStack {
            Button {
                replayToken = 0
                withAnimation(reduced ? nil : .easeOut(duration: 0.18)) { flow.back(); revision += 1 }
                dimCTA()
            } label: {
                ZStack {
                    Circle().fill(TradingBotsPalette.hex(0x0d0d0d))
                    Circle().strokeBorder(.white.opacity(0.035), lineWidth: 1)
                    TradingBotsBackArrow().stroke(.white, style: StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round))
                        .frame(width: 17, height: 13.5)
                }
                .frame(width: 44, height: 44)
            }
            .buttonStyle(TradingBotsPressStyle())
            .accessibilityLabel(flow.step == .wallet ? "Restart wallet introduction" : "Back")
            .accessibilityHint("Hold to open the separate showcase QA harness")
            .simultaneousGesture(LongPressGesture(minimumDuration: 0.8).onEnded { _ in showQA = true })
            .position(x: 42, y: 93.3)

            if flow.step != .pro {
                HStack(spacing: 4) {
                    ForEach(0..<5, id: \.self) { index in
                        Capsule().fill(index == flow.step.rawValue ? TradingBotsPalette.hex(0xd8d8d8) : TradingBotsPalette.hex(0x363636))
                            .frame(width: index == flow.step.rawValue ? 43 : 8, height: 5)
                    }
                }
                .animation(reduced ? nil : .spring(response: 0.42, dampingFraction: 0.9), value: flow.step)
                .frame(width: 91, alignment: .leading)
                .accessibilityElement(children: .ignore).accessibilityLabel("Onboarding step \(flow.step.rawValue + 1) of 5")
                .onLongPressGesture(minimumDuration: 0.8) { showQA = true }
                .position(x: 84 + 45.5, y: 93.3)
                .transition(.opacity)
            }

            Button("Skip") { if flow.step == .pro { detail = .finish } else { navigate(.pro) } }
                .font(.system(size: 15.2)).tracking(-0.2).foregroundStyle(TradingBotsPalette.ink)
                .frame(minWidth: 44, minHeight: 44).buttonStyle(TradingBotsPressStyle())
                .position(x: 356, y: 95.3)
        }
    }

    @ViewBuilder private var footer: some View {
        let title = flow.step == .pro ? "Start 3 Day Free Trial" : flow.step == .wallet ? "Let’s Get Started" : flow.step == .selection ? "Choose Your Bots" : "Continue"
        TradingBotsPrimaryButton(title: title, pro: flow.step == .pro, dimmed: ctaDimmed, pressed: ctaPressed) {
            replayToken = 0
            if flow.step == .pro { detail = .trial; return }
            advance()
        }
        .accessibilityIdentifier("trading-primary-cta")
        .position(x: 200.8, y: flow.step == .selection ? 803.8 : flow.step == .pro ? 753.2 : 757)
        if flow.step == .pro {
            HStack(spacing: 10) {
                Button("Terms of use") { detail = .terms }
                Text("|").foregroundStyle(Color(white: 0.25))
                Button("Privacy policy") { detail = .privacy }
            }
            .font(.system(size: 15.6)).tracking(-0.2).foregroundStyle(TradingBotsPalette.hex(0x585858)).buttonStyle(.plain)
            .position(x: 200, y: 820)
            .tbReveal(true, reduced: reduced)
        } else if flow.step != .selection {
            Button("skip and start") { navigate(.pro) }
                .font(.system(size: 16)).tracking(-0.2).foregroundStyle(TradingBotsPalette.hex(0xdedede))
                .frame(minHeight: 34).buttonStyle(TradingBotsPressStyle()).position(x: 201, y: 819)
        }
    }

    private func advance() {
        withAnimation(reduced ? nil : .easeOut(duration: 0.18)) {
            guard flow.advance(at: ProcessInfo.processInfo.systemUptime) else { return }
            proInteracted = false
        }
        dimCTA()
    }

    private func navigate(_ step: TradingBotsFlow.Step) {
        replayToken = 0; proInteracted = false
        withAnimation(reduced ? nil : .easeOut(duration: 0.18)) { flow.show(step); revision += 1 }
        dimCTA()
    }

    /// The reference greys the CTA while a scene changes, then lights it again.
    private func dimCTA() {
        guard !reduced else { return }
        withAnimation(.easeOut(duration: 0.12)) { ctaDimmed = true }
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(0.4))
            withAnimation(.easeInOut(duration: 0.5)) { ctaDimmed = false }
        }
    }
}

#Preview { TradingBotsOnboardingDemo() }
