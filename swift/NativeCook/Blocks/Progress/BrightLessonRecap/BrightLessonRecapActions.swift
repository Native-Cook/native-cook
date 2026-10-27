import SwiftUI

/// The share button and the CLAIM XP key (and, with large text, the success line and the replay link).
/// RN `actions` view.
struct BrightLessonRecapActions: View {
    let metrics: BrightLessonRecapMetrics
    let claimed: Bool
    let reduced: Bool
    let enabled: Bool
    let rewardStart: Date?
    let onShare: () -> Void
    let onClaim: () -> Void
    let onReplay: () -> Void
    let onPressChange: (Bool) -> Void

    private typealias M = BrightLessonRecapMotion
    private typealias P = BrightLessonRecapPalette

    var body: some View {
        Group {
            if metrics.flow {
                flowBody
            } else {
                compactBody
            }
        }
        .allowsHitTesting(enabled)
        .accessibilityHidden(!enabled)
    }

    // MARK: Compact

    /// RN: a row of the 48 pt share button and the flexible key, 9 pt apart. The success line floats 30 pt above it.
    private var compactBody: some View {
        HStack(spacing: 9) {
            shareButton
            claimButton
        }
        .overlay(alignment: .topTrailing) {
            rewardText
                .offset(x: -(metrics.contentWidth * 0.3), y: -6)
        }
        .overlay(alignment: .top) {
            if claimed {
                successLine
                    .frame(width: metrics.contentWidth)
                    .offset(y: -30)
            }
        }
    }

    private var shareButton: some View {
        Button {
            onShare()
        } label: {
            BrightLessonRecapShareGlyph()
                .frame(width: 48)
                .frame(minHeight: 48, maxHeight: .infinity)
                .overlay { RoundedRectangle(cornerRadius: 12).strokeBorder(P.color(0xC7D2D8), lineWidth: 1) }
                .contentShape(Rectangle())
        }
        .buttonStyle(BrightLessonRecapPressScaleStyle(reduced: reduced))
        .accessibilityLabel("Share your result")
    }

    // MARK: Flow

    /// RN with large text: the success line above a full-width key, then a "Replay celebration" link.
    private var flowBody: some View {
        VStack(spacing: 0) {
            successLine
                .frame(maxWidth: .infinity)
                .padding(.bottom, 13)
            claimButton
                .overlay(alignment: .topTrailing) {
                    rewardText
                        .offset(x: -(metrics.contentWidth * 0.3), y: -6)
                }
            Button {
                onReplay()
            } label: {
                metrics.label("Replay celebration", face: .black, size: 13, color: 0x597D94)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .contentShape(Rectangle())
            }
            .buttonStyle(BrightLessonRecapDimButtonStyle())
            .padding(.top, 3)
            .accessibilityLabel("Replay celebration")
        }
        .padding(.top, 8)
    }

    // MARK: Shared parts

    private var successLine: some View {
        metrics.label(
            claimed ? "45 XP added to this demo session." : "A perfect finish. You earned this.",
            face: .system, size: 12, lineHeight: 18, color: 0x5D7463
        )
    }

    /// A physical key: the face sits 4 pt proud of its base and drops flush while held.
    private var claimButton: some View {
        Button {
            onClaim()
        } label: {
            BrightLessonRecapSwapText(
                text: claimed ? "XP CLAIMED  \u{2713}" : "CLAIM XP",
                reduced: reduced,
                metrics: metrics
            )
        }
        .buttonStyle(BrightLessonRecapClaimStyle(compact: !metrics.flow, claimed: claimed, reduced: reduced, onPressChange: onPressChange))
        .disabled(claimed || !enabled)
        .accessibilityLabel(claimed ? "XP claimed" : "Claim 45 XP")
    }

    /// "+45 XP" rising 56 pt out of the key and fading over 700 ms.
    private var rewardText: some View {
        BrightLessonRecapReward(start: rewardStart, metrics: metrics)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

// MARK: - Styles

/// RN `PressScale`: the whole surface settles to 0.96 in 150 ms on the strong ease-out and returns
/// on the same curve. Under Reduce Motion it dims to 0.72 instead.
struct BrightLessonRecapPressScaleStyle: ButtonStyle {
    let reduced: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduced ? 0.96 : 1)
            .opacity(reduced && configuration.isPressed ? 0.72 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.15), value: configuration.isPressed)
    }
}

/// RN `Pressable` with `opacity: pressed ? 0.6 : 1` and no transition.
struct BrightLessonRecapDimButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.6 : 1)
    }
}

/// The claim key: a dark base with a 4 pt lip, and the lighter face that drops by 4 pt while held
/// (not under Reduce Motion). It reports press in and out, which RN uses to pause the loop.
struct BrightLessonRecapClaimStyle: ButtonStyle {
    let compact: Bool
    let claimed: Bool
    let reduced: Bool
    let onPressChange: (Bool) -> Void

    private typealias P = BrightLessonRecapPalette

    func makeBody(configuration: Configuration) -> some View {
        let radius: CGFloat = compact ? 12 : 17
        let drop: CGFloat = configuration.isPressed && !reduced ? 4 : 0
        configuration.label
            .padding(.vertical, compact ? 10 : 16)
            .padding(.horizontal, 15)
            .frame(maxWidth: .infinity, minHeight: compact ? 44 : 52)
            .background(RoundedRectangle(cornerRadius: radius).fill(P.color(claimed ? 0x2E8A64 : 0x1F9BEA)))
            .offset(y: drop)
            .padding(.bottom, 4)
            .background(RoundedRectangle(cornerRadius: radius).fill(P.color(claimed ? 0x1B6847 : 0x1679C4)))
            .contentShape(Rectangle())
            .onChange(of: configuration.isPressed) { _, pressed in
                onPressChange(pressed)
            }
    }
}

// MARK: - Swap text

/// RN `SwapText`: text that changes in place. The old words leave in 140 ms and the new ones settle a
/// beat later (after 60 ms) from 10 pt below over 260 ms, on the strong ease-out. Under Reduce Motion it
/// is a plain cross-fade (120 ms out, 180 ms in). The first text does not animate.
struct BrightLessonRecapSwapText: View {
    let text: String
    let reduced: Bool
    let metrics: BrightLessonRecapMetrics

    @State private var previous: String?
    @State private var changedAt: Date?
    @State private var revision = 0

    private typealias M = BrightLessonRecapMotion

    var body: some View {
        // The invisible line sizes the box; the animated copies sit on top of it.
        copy(text)
            .opacity(0)
            .overlay {
                TimelineView(.animation(paused: changedAt == nil)) { timeline in
                    let elapsed: Double = age(at: timeline.date)
                    ZStack {
                        if let previous, elapsed < exitDuration {
                            copy(previous)
                                .opacity(1 - exitCurve(elapsed / exitDuration))
                                .fixedSize()
                        }
                        copy(text)
                            .opacity(enterOpacity(elapsed))
                            .offset(y: enterOffset(elapsed))
                            .fixedSize()
                    }
                }
            }
            .onChange(of: text) { old, _ in
                previous = old
                changedAt = Date()
                revision += 1
            }
            .task(id: revision) {
                guard revision > 0 else { return }
                try? await Task.sleep(nanoseconds: 450_000_000)
                guard !Task.isCancelled else { return }
                changedAt = nil
                previous = nil
            }
            .accessibilityHidden(true)
    }

    private func copy(_ string: String) -> some View {
        metrics.label(string, face: .black, size: 14, lineHeight: 20, kern: 0.7, color: 0xFFFFFF)
            .fixedSize()
    }

    /// Seconds since the text last changed; infinite when it never has (nothing animates).
    private func age(at date: Date) -> Double {
        guard let changedAt else { return Double.infinity }
        return date.timeIntervalSince(changedAt)
    }

    private var exitDuration: Double { reduced ? 0.12 : 0.14 }

    private func exitCurve(_ u: Double) -> Double {
        reduced ? M.inOutQuad(u) : M.enter(u)
    }

    private func enterOpacity(_ elapsed: Double) -> Double {
        if reduced { return M.inOutQuad(elapsed / 0.18) }
        return M.enter((elapsed - 0.06) / 0.26)
    }

    private func enterOffset(_ elapsed: Double) -> CGFloat {
        if reduced { return 0 }
        let settle: Double = M.enter((elapsed - 0.06) / 0.26)
        return CGFloat(10 * (1 - settle))
    }
}

// MARK: - Reward

/// "+45 XP": from the moment of the claim it rises 56 pt and fades over 700 ms on a linear clock.
/// RN `reward` shared value and `rewardStyle`.
struct BrightLessonRecapReward: View {
    /// When the claim happened; nil before any claim.
    let start: Date?
    let metrics: BrightLessonRecapMetrics

    @State private var finished = true

    private typealias M = BrightLessonRecapMotion

    var body: some View {
        TimelineView(.animation(paused: start == nil || finished)) { timeline in
            rewardLabel(progress(at: timeline.date))
        }
        .task(id: start) {
            guard start != nil else {
                finished = true
                return
            }
            finished = false
            try? await Task.sleep(nanoseconds: 800_000_000)
            guard !Task.isCancelled else { return }
            finished = true
        }
    }

    /// RN `reward`: 0 to 1 over 700 ms, linear, from the claim.
    private func progress(at date: Date) -> Double {
        guard let start else { return 0 }
        let elapsed: Double = date.timeIntervalSince(start)
        let linear: Double = elapsed / 0.7
        return min(1, max(0, linear))
    }

    /// RN `rewardStyle`: opacity, rise and pop for a progress `p`.
    private func rewardLabel(_ p: Double) -> some View {
        let visible: Bool = p > 0 && p < 1
        let rampIn: Double = min(1, p * 6)
        let fadeOut: Double = 1 - M.enter(M.fraction(p, 0.45, 1))
        let opacity: Double = visible ? rampIn * fadeOut : 0
        let pop: CGFloat = CGFloat(0.9 + 0.2 * M.enter(M.fraction(p, 0, 0.3)))
        let rise: CGFloat = CGFloat(-56 * M.enter(p))
        return metrics.label("+45 XP", face: .black, size: 22, kern: -0.4, color: 0xD98A00)
            .fixedSize()
            .scaleEffect(pop)
            .offset(y: rise)
            .opacity(opacity)
    }
}
