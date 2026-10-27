import SwiftUI

/// The bar over the day surface: a grey grabber (or "3 in Review" on Today) and the "more" button.
struct EmberDayReviewTopBar: View {
    let motion: EmberDayReviewMotion
    let metrics: EmberDayReviewMetrics
    let visibleDay: EmberDayReviewDay
    /// `topTintStyle`: the bar turns solid white as the day surface is lowered.
    let tint: Double
    /// `headerContentStyle`: its contents fade out as the surface is lowered.
    let contentOpacity: Double

    var body: some View {
        HStack(spacing: 0) {
            Color.clear.frame(width: 48, height: 48)
            Spacer(minLength: 0)
            badge
            Spacer(minLength: 0)
            moreButton
        }
        .opacity(contentOpacity)
        .padding(.horizontal, 12)
        .padding(.top, metrics.top - 4)
        .frame(width: metrics.width, height: metrics.topBarHeight, alignment: .top)
        .background(Color.white.opacity(tint))
    }

    @ViewBuilder
    private var badge: some View {
        if visibleDay == .today {
            EmberDayReviewLabel(
                "3 in Review",
                size: 10,
                weight: .semibold,
                color: EmberDayReviewPalette.ember,
                cap: 1.4,
                lineLimit: 1
            )
            .fixedSize()
            .padding(.vertical, 2)
            .padding(.horizontal, 9)
            .frame(minWidth: 36, minHeight: 20)
            .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(EmberDayReviewPalette.badgeToday))
        } else {
            RoundedRectangle(cornerRadius: 3, style: .continuous)
                .fill(EmberDayReviewPalette.badge)
                .frame(width: 36, height: 6)
        }
    }

    private var moreButton: some View {
        Button {
            motion.showReview(true)
        } label: {
            ZStack {
                Circle()
                    .fill(EmberDayReviewPalette.moreDisc)
                    .frame(width: 28, height: 28)
                EmberDayReviewIcon(kind: .ellipsis, size: 15, color: EmberDayReviewPalette.ember)
            }
            .frame(minWidth: 48, minHeight: 48)
            // `hitSlop={8}`.
            .contentShape(Rectangle().inset(by: -8))
        }
        .buttonStyle(EmberDayReviewFlatButtonStyle())
        .accessibilityLabel("Review unfinished actions")
    }
}

/// The iOS-style Review page that the lowered day surface reveals.
struct EmberDayReviewReviewPage: View {
    let motion: EmberDayReviewMotion
    let metrics: EmberDayReviewMetrics
    /// `reviewVisible`: only then does it take touches and appear to VoiceOver.
    let visible: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                Spacer(minLength: 0)
                Button {
                    motion.showReview(false)
                } label: {
                    EmberDayReviewLabel(
                        "Done",
                        size: 14,
                        weight: .semibold,
                        color: EmberDayReviewPalette.emberText
                    )
                    .frame(minWidth: 48, minHeight: 48)
                    .contentShape(Rectangle())
                }
                .buttonStyle(EmberDayReviewFlatButtonStyle())
                // `reviewHeader` has `marginRight: -12`.
                .padding(.trailing, -12)
            }
            EmberDayReviewLabel(
                "Review",
                size: 30,
                weight: .bold,
                color: EmberDayReviewPalette.ink,
                lineHeight: 36,
                cap: 1.6
            )
            .accessibilityAddTraits(.isHeader)
            EmberDayReviewLabel(
                "No unfinished actions\nfor selected day.",
                size: 25,
                color: EmberDayReviewPalette.reviewEmpty,
                lineHeight: 38,
                cap: 1.6,
                alignment: .center
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .padding(.horizontal, 24)
        .padding(.top, metrics.top + 4)
        .padding(.bottom, metrics.bottom + 210)
        .frame(width: metrics.width, height: metrics.height, alignment: .topLeading)
        .background(EmberDayReviewPalette.reviewScreen)
        .allowsHitTesting(visible)
        .accessibilityHidden(!visible)
    }
}

/// What the lowered day surface turns into: a grabber over "Showing unfinished for selected day".
struct EmberDayReviewCompactCard: View {
    let width: CGFloat
    let opacity: Double

    var body: some View {
        ZStack(alignment: .top) {
            RoundedRectangle(cornerRadius: 2, style: .continuous)
                .fill(EmberDayReviewPalette.handle)
                .frame(width: 32, height: 3)
                .padding(.top, 12)
            VStack(spacing: 6) {
                EmberDayReviewLabel(
                    "Showing unfinished for selected day",
                    size: 16,
                    color: EmberDayReviewPalette.notice,
                    lineHeight: 18,
                    alignment: .center
                )
                EmberDayReviewLabel(
                    "Yesterday \u{203A}",
                    size: 15,
                    weight: .semibold,
                    color: EmberDayReviewPalette.ember
                )
            }
            .padding(.top, 30)
        }
        .frame(width: width, alignment: .top)
        .opacity(opacity)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// The foot of the screen: a fade to white (or peach on Today), the Add Action field on Today and the
/// main button, "Today" on the first page and "Commit" on the second.
struct EmberDayReviewFooter: View {
    let motion: EmberDayReviewMotion
    let metrics: EmberDayReviewMetrics
    let visibleDay: EmberDayReviewDay
    let committed: Bool
    let reduced: Bool

    @State private var draft = ""
    @FocusState private var focused: Bool
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        VStack(spacing: 0) {
            if visibleDay == .today {
                addAction
            }
            primary
        }
        .padding(.top, visibleDay == .today ? 44 : 28)
        .padding(.bottom, metrics.bottom + 12)
        .frame(width: metrics.width)
        .background {
            LinearGradient(colors: gradient, startPoint: .top, endPoint: .bottom)
                .allowsHitTesting(false)
        }
    }

    private var gradient: [Color] {
        if visibleDay == .today {
            return [
                EmberDayReviewPalette.rgb(0xFFFFFF, 0),
                EmberDayReviewPalette.rgb(0xFFF1EA),
                EmberDayReviewPalette.rgb(0xFFD9C7),
            ]
        }
        return [EmberDayReviewPalette.rgb(0xFFFFFF, 0), EmberDayReviewPalette.rgb(0xFFFFFF), EmberDayReviewPalette.rgb(0xFFFFFF)]
    }

    // MARK: Add Action

    private var addAction: some View {
        let factor = EmberDayReviewScale.factor(typeSize, cap: 1.6)
        return HStack(spacing: 8) {
            EmberDayReviewIcon(kind: .add, size: 20, color: EmberDayReviewPalette.placeholder)
            TextField(
                "",
                text: $draft,
                prompt: Text("Add Action").foregroundColor(EmberDayReviewPalette.placeholder)
            )
            .font(.system(size: 15 * factor))
            .foregroundStyle(EmberDayReviewPalette.input)
            .multilineTextAlignment(.center)
            .submitLabel(.done)
            .focused($focused)
            .onSubmit(submit)
            .onChange(of: focused) { _, isFocused in
                motion.focusChanged(isFocused)
            }
            .frame(height: 50)
            .accessibilityLabel("Add an action for today")
            Color.clear.frame(width: 20, height: 20)
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 54)
        .background(
            RoundedRectangle(cornerRadius: 27, style: .continuous)
                .fill(Color.white)
                .shadow(color: EmberDayReviewPalette.addShadow, radius: 7, x: 0, y: 0)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 27, style: .continuous)
                .strokeBorder(EmberDayReviewPalette.addBorder, lineWidth: 2)
        )
        .padding(.horizontal, 12)
        .padding(.bottom, 6)
    }

    /// `onSubmitEditing={addTask}` with `blurOnSubmit={false}`: the keyboard stays up for the next one.
    private func submit() {
        let title = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty else { return }
        motion.addTask(title)
        draft = ""
        DispatchQueue.main.async { focused = true }
    }

    // MARK: Main button

    private var primary: some View {
        let onFirstPage = visibleDay == .yesterday
        return Button {
            motion.primaryTapped()
        } label: {
            HStack(spacing: 8) {
                if onFirstPage {
                    dayBadge
                } else {
                    EmberDayReviewIcon(
                        kind: committed ? .lockClosed : .lockOpen,
                        size: 17,
                        color: EmberDayReviewPalette.ember
                    )
                }
                EmberDayReviewSwapText(
                    text: onFirstPage ? "Today" : (committed ? "Committed" : "Commit"),
                    reduced: reduced,
                    size: 14,
                    weight: .semibold,
                    color: EmberDayReviewPalette.emberText,
                    cap: 1.6
                )
                if onFirstPage {
                    EmberDayReviewIcon(kind: .arrowForward, size: 17, color: EmberDayReviewPalette.emberArrow)
                }
            }
            .padding(.horizontal, 24)
            .frame(minHeight: 48)
            .contentShape(Rectangle())
        }
        .buttonStyle(EmberDayReviewDimButtonStyle())
        .accessibilityLabel(onFirstPage ? "Today" : (committed ? "Committed" : "Commit today"))
    }

    /// The date badge: today's day of the month in a small orange disc.
    private var dayBadge: some View {
        ZStack {
            Circle().fill(EmberDayReviewPalette.ember)
            EmberDayReviewLabel("25", size: 9, weight: .bold, color: .white, tabular: true, lineLimit: 1)
                .fixedSize()
        }
        .frame(width: 18, height: 18)
    }
}
