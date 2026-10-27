import SwiftUI

/// The second page: big day tabs, the checklist and, once committed, a thank-you line.
struct EmberDayReviewTodayPanel: View {
    let motion: EmberDayReviewMotion
    let metrics: EmberDayReviewMetrics
    let tasks: [String]
    let completed: [Int]
    let committed: Bool
    let reduced: Bool
    /// `visibleDay === 'today' && !reviewVisible`.
    let active: Bool
    /// Height of the keyboard over the screen: `automaticallyAdjustKeyboardInsets`.
    let keyboard: CGFloat

    var body: some View {
        EmberDayReviewScroller(
            motion: motion,
            size: CGSize(width: metrics.width, height: metrics.viewportHeight),
            active: active
        ) {
            VStack(alignment: .leading, spacing: 0) {
                tabs
                ForEach(Array(tasks.enumerated()), id: \.element) { index, title in
                    row(index: index, title: title)
                }
                if committed {
                    EmberDayReviewLabel(
                        "Committed. Small steps count.",
                        size: 14,
                        color: EmberDayReviewPalette.saved,
                        lineHeight: 21
                    )
                    .padding(.top, 24)
                }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 170 + metrics.bottom + keyboard)
            .frame(width: metrics.width, alignment: .topLeading)
            // `LinearTransition.duration(240).easing(OUT)`: rows glide when the list changes.
            .animation(reduced ? nil : .timingCurve(0.23, 1, 0.32, 1, duration: 0.24), value: tasks)
        }
    }

    // MARK: Tabs

    @ViewBuilder
    private var tabs: some View {
        let contentWidth = metrics.width - 48
        if metrics.largeText {
            EmberDayReviewFlow(spacing: 16) {
                yesterdayTab
                todayTab
                mutedTab("Tomorrow")
                mutedTab("Wednesday")
            }
            .frame(width: contentWidth, alignment: .leading)
            .frame(minHeight: 64)
            .padding(.top, -8)
        } else {
            // Yesterday peeks in from the left edge: its right end sits 14 pt before the row.
            ZStack(alignment: .leading) {
                HStack(spacing: 16) {
                    todayTab
                    mutedTab("Tomorrow")
                    mutedTab("Wednesday")
                }
                .fixedSize()
                yesterdayTab
                    .alignmentGuide(.leading) { $0.width + 14 }
            }
            .frame(width: contentWidth, alignment: .leading)
            .frame(minHeight: 64)
            .padding(.top, -8)
        }
    }

    private var yesterdayTab: some View {
        Button {
            motion.select(.yesterday)
        } label: {
            EmberDayReviewLabel(
                "Yesterday",
                size: 34,
                weight: .bold,
                color: EmberDayReviewPalette.tabMuted,
                tracking: -0.8,
                cap: 1.5,
                lineLimit: 1
            )
            .fixedSize()
            .frame(minHeight: 48)
        }
        .buttonStyle(EmberDayReviewFlatButtonStyle())
        .accessibilityLabel("Yesterday")
    }

    private var todayTab: some View {
        EmberDayReviewLabel(
            "Today",
            size: 34,
            weight: .bold,
            color: EmberDayReviewPalette.ink,
            tracking: -0.8,
            cap: 1.5
        )
        .fixedSize()
        .accessibilityAddTraits(.isSelected)
    }

    private func mutedTab(_ title: String) -> some View {
        EmberDayReviewLabel(
            title,
            size: 34,
            weight: .bold,
            color: EmberDayReviewPalette.tabMuted,
            tracking: -0.8,
            cap: 1.5,
            lineLimit: 1
        )
        .fixedSize()
    }

    // MARK: Tasks

    private func row(index: Int, title: String) -> some View {
        let done = completed.contains(index)
        return Button {
            motion.toggle(index)
        } label: {
            HStack(spacing: 12) {
                EmberDayReviewCheck(checked: done, reduced: reduced)
                EmberDayReviewLabel(
                    title,
                    size: 17,
                    color: done ? EmberDayReviewPalette.taskDone : EmberDayReviewPalette.body,
                    lineHeight: 23,
                    strikethrough: done
                )
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(8)
            .frame(minHeight: 50)
            .contentShape(Rectangle())
        }
        .buttonStyle(EmberDayReviewPressScaleStyle(scale: 0.98, reduced: reduced))
        .accessibilityLabel(title)
        .accessibilityValue(done ? "checked" : "unchecked")
        // A task added at the foot rises in over 320 ms; the first seven are there from the start.
        .transition(
            reduced
                ? .identity
                : .opacity.combined(with: .offset(y: 10)).animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.32))
        )
    }
}

/// The round checkbox: a grey well; ticked, an orange ring and a disc that lands with a small overshoot
/// (`ZoomIn.springify().damping(13).stiffness(260)`) and fades out in 120 ms. Under Reduce Motion the
/// disc fades in over 120 ms.
struct EmberDayReviewCheck: View {
    let checked: Bool
    let reduced: Bool

    var body: some View {
        ZStack {
            Circle().fill(EmberDayReviewPalette.checkWell)
            Circle().strokeBorder(
                checked ? EmberDayReviewPalette.emberCheck : EmberDayReviewPalette.checkRing,
                lineWidth: 1.5
            )
            if checked {
                Circle()
                    .fill(EmberDayReviewPalette.emberCheck)
                    .overlay {
                        EmberDayReviewIcon(kind: .checkmark, size: 15, color: .white)
                    }
                    .transition(transition)
            }
        }
        .frame(width: 28, height: 28)
        .accessibilityHidden(true)
    }

    private var transition: AnyTransition {
        let fadeOut = AnyTransition.opacity.animation(.linear(duration: 0.12))
        if reduced {
            return .asymmetric(insertion: .opacity.animation(.linear(duration: 0.12)), removal: fadeOut)
        }
        return .asymmetric(
            insertion: .scale(scale: 0).animation(.interpolatingSpring(mass: 1, stiffness: 260, damping: 13)),
            removal: fadeOut
        )
    }
}
