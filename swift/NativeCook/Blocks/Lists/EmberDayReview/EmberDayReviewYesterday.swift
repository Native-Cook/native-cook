import SwiftUI

/// Everything on the Yesterday page that moves with the loop clock, taken at one instant.
struct EmberDayReviewYesterdayFrame: Equatable {
    /// `heroStyle`: how far the hero has opened, 0 to 1.
    var heroProgress: Double
    var haloOpacity: Double
    var haloScale: Double
    var poses: [EmberDayReviewStarPose]
    var summaryOpacity: Double
    var summaryLift: Double
    var historyOpacity: [Double]
    var historyLift: [Double]

    static func at(t: Double, reduced: Bool) -> EmberDayReviewYesterdayFrame {
        let math = EmberDayReviewMath.self
        let hero = reduced
            ? 1
            : math.interpolate(t, [0, 0.25, 0.4, 0.6, 0.8, 9.6, 10], [0, 0, 0.62, 0.94, 1, 1, 0])
        let summaryFade = 1 - math.interpolate(t, [9.6, 9.95], [0, 1])
        var historyOpacity: [Double] = []
        var historyLift: [Double] = []
        for index in 0..<EmberDayReviewContent.history.count {
            let start = 0.72 + Double(index) * 0.18
            let end = 1.12 + Double(index) * 0.18
            historyOpacity.append(reduced ? 1 : math.interpolate(t, [start, end, 9.6, 9.95], [0, 1, 1, 0]))
            historyLift.append(reduced ? 0 : math.interpolate(t, [start, end], [10, 0]))
        }
        return EmberDayReviewYesterdayFrame(
            heroProgress: hero,
            haloOpacity: reduced ? 0 : math.interpolate(t, [0.3, 0.5, 0.8, 1.45], [0, 0.6, 0.35, 0]),
            haloScale: math.interpolate(t, [0.3, 0.7, 1.4], [0.2, 0.85, 1.2]),
            poses: (0..<3).map { EmberDayReviewStarMotion.pose(index: $0, t: t, reduced: reduced) },
            summaryOpacity: reduced ? 1 : math.interpolate(t, [0.85, 1.4], [0, 1]) * summaryFade,
            summaryLift: reduced ? 0 : math.interpolate(t, [0.85, 1.4], [10, 0]),
            historyOpacity: historyOpacity,
            historyLift: historyLift
        )
    }
}

/// The first page: the hero, the date row, the summary card and the timeline of what was done.
struct EmberDayReviewYesterdayPanel: View {
    let motion: EmberDayReviewMotion
    let metrics: EmberDayReviewMetrics
    let frame: EmberDayReviewYesterdayFrame
    let reduced: Bool
    /// `visibleDay === 'yesterday' && !reviewVisible`.
    let active: Bool

    var body: some View {
        EmberDayReviewScroller(
            motion: motion,
            size: CGSize(width: metrics.width, height: metrics.viewportHeight),
            active: active
        ) {
            VStack(alignment: .leading, spacing: 0) {
                EmberDayReviewHero(
                    width: metrics.width,
                    baseHeight: metrics.heroHeight,
                    progress: frame.heroProgress,
                    haloOpacity: frame.haloOpacity,
                    haloScale: frame.haloScale,
                    poses: frame.poses
                )
                following
            }
            .frame(width: metrics.width, alignment: .topLeading)
            .padding(.bottom, metrics.bottom + 96)
        }
    }

    /// `followingStyle`: the rest of the page rides up under the hero as it opens.
    private var following: some View {
        VStack(alignment: .leading, spacing: 0) {
            dateRow
            summary
            history
        }
        .offset(y: reduced ? 0 : metrics.heroHeight * (frame.heroProgress - 1))
    }

    // MARK: Date row

    @ViewBuilder
    private var dateRow: some View {
        if metrics.largeText {
            // `flexWrap: 'wrap'`, `gap: 8`, `flexBasis: '100%'` on Yesterday: one tab a line.
            VStack(alignment: .leading, spacing: 8) {
                yesterdayButton(size: 24)
                todayButton(size: 21)
            }
            .padding(.horizontal, 24)
            .frame(width: metrics.width, alignment: .leading)
            .frame(minHeight: 64)
        } else {
            // Yesterday sits in the middle; "Oct 24" ends at 16.5% and Today starts at 83% of the width,
            // so both are cut by the row's edges (`overflow: 'hidden'`).
            ZStack(alignment: .leading) {
                EmberDayReviewLabel(
                    "Oct 24",
                    size: 24,
                    color: EmberDayReviewPalette.dateHint,
                    cap: 1.5,
                    lineLimit: 1
                )
                .fixedSize()
                .frame(width: metrics.width * 0.165, alignment: .trailing)
                yesterdayButton(size: 35)
                    .frame(width: metrics.width)
                todayButton(size: 28)
                    .padding(.leading, metrics.width * 0.83)
            }
            .frame(width: metrics.width, height: 64, alignment: .leading)
            .clipped()
        }
    }

    private func yesterdayButton(size: CGFloat) -> some View {
        Button {
            motion.select(.yesterday)
        } label: {
            EmberDayReviewLabel(
                "Yesterday",
                size: size,
                weight: .bold,
                color: EmberDayReviewPalette.dateActive,
                tracking: -0.8,
                cap: 1.5
            )
            .frame(minHeight: 48)
        }
        .buttonStyle(EmberDayReviewFlatButtonStyle())
        .accessibilityLabel("Yesterday")
        .accessibilityAddTraits(.isSelected)
    }

    private func todayButton(size: CGFloat) -> some View {
        Button {
            motion.select(.today)
        } label: {
            EmberDayReviewLabel(
                "Today",
                size: size,
                weight: .semibold,
                color: EmberDayReviewPalette.dateInactive,
                cap: 1.5,
                lineLimit: 1
            )
            .fixedSize()
            .frame(minHeight: 48)
        }
        .buttonStyle(EmberDayReviewFlatButtonStyle())
        .accessibilityLabel("Today")
    }

    // MARK: Summary

    private var summary: some View {
        let inner = metrics.width - 32 - 16
        let sideWidth = inner / 3.3
        return Group {
            if metrics.largeText {
                VStack(spacing: 12) {
                    completedSide
                    middle
                    openSide
                }
                .frame(maxWidth: .infinity)
            } else {
                HStack(spacing: 0) {
                    completedSide.frame(width: sideWidth)
                    middle.frame(width: sideWidth * 1.3)
                    openSide.frame(width: sideWidth)
                }
            }
        }
        .padding(.vertical, 26)
        .padding(.horizontal, 8)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Color.white)
                .shadow(color: EmberDayReviewPalette.summaryShadow, radius: 10, x: 0, y: 4)
        )
        .padding(.horizontal, 16)
        .opacity(frame.summaryOpacity)
        .offset(y: frame.summaryLift)
    }

    private var completedSide: some View {
        VStack(spacing: 0) {
            EmberDayReviewLabel("42 min", size: 12, weight: .medium, color: EmberDayReviewPalette.smallValue)
            smallLabel("well spent")
        }
        .frame(minHeight: 48)
    }

    private var middle: some View {
        HStack(spacing: 7) {
            EmberDayReviewLaurel()
            VStack(spacing: 0) {
                EmberDayReviewLabel(
                    "\u{2713} 3",
                    size: 24,
                    weight: .semibold,
                    color: EmberDayReviewPalette.total,
                    cap: 1.6,
                    alignment: .center
                )
                smallLabel("completed")
            }
            EmberDayReviewLaurel(flip: true)
        }
    }

    private var openSide: some View {
        Button {
            motion.showReview(true)
        } label: {
            VStack(spacing: 0) {
                EmberDayReviewLabel("0 open", size: 12, weight: .medium, color: EmberDayReviewPalette.smallValue)
                smallLabel("to review \u{203A}")
            }
            .frame(minHeight: 48)
            .contentShape(Rectangle())
        }
        .buttonStyle(EmberDayReviewFlatButtonStyle())
        .accessibilityLabel("Review zero unfinished actions")
    }

    private func smallLabel(_ text: String) -> some View {
        EmberDayReviewLabel(text, size: 10, color: EmberDayReviewPalette.smallLabel, alignment: .center)
            .padding(.top, 4)
    }

    // MARK: Timeline

    private var history: some View {
        VStack(alignment: .leading, spacing: 20) {
            ForEach(EmberDayReviewContent.history.indices, id: \.self) { index in
                let entry = EmberDayReviewContent.history[index]
                EmberDayReviewHistoryEntry(
                    stamp: entry.stamp,
                    title: entry.title,
                    hasRail: index < EmberDayReviewContent.history.count - 1,
                    opacity: frame.historyOpacity[index],
                    lift: frame.historyLift[index]
                )
            }
        }
        .padding(.horizontal, 28)
        .padding(.top, 14)
        .frame(width: metrics.width, alignment: .leading)
    }
}

/// One entry of the timeline: a dot and a time, the title below, a rail down to the next entry.
struct EmberDayReviewHistoryEntry: View {
    let stamp: String
    let title: String
    let hasRail: Bool
    let opacity: Double
    let lift: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack(spacing: 5) {
                Circle()
                    .fill(EmberDayReviewPalette.dot)
                    .frame(width: 5, height: 5)
                EmberDayReviewLabel(
                    stamp,
                    size: 11,
                    color: EmberDayReviewPalette.stamp,
                    tracking: 0.5,
                    tabular: true
                )
                .padding(.leading, 5)
            }
            EmberDayReviewLabel(
                title,
                size: 16,
                color: EmberDayReviewPalette.body,
                lineHeight: 22,
                cap: 1.8
            )
            .padding(.leading, 10)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(alignment: .topLeading) {
            if hasRail {
                // `rail`: left 2, top 9, bottom -27, 1 pt wide.
                GeometryReader { proxy in
                    Rectangle()
                        .fill(EmberDayReviewPalette.rail)
                        .frame(width: 1, height: proxy.size.height + 18)
                        .offset(x: 2, y: 9)
                }
            }
        }
        .opacity(opacity)
        .offset(y: lift)
    }
}
