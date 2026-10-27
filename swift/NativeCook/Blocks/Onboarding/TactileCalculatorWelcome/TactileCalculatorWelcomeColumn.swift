import SwiftUI

/// The scroll content of the React Native screen (`styles.content` and its three children) as a
/// flex column: the hero, the copy and the Continue pill, with the container at least as tall
/// as the window and `paddingBottom` at its end.
///
/// SwiftUI has no `flex: 1`, so this layout does what Yoga does for the three children:
///
/// - The hero is `heroHeight` tall and as wide as the column.
/// - The copy has `flex: 1` and `minHeight: 125`: its basis is the 125 pt minimum and it grows
///   by whatever the window leaves over. At large text sizes it has `flex: 0` instead
///   (`growsCopy` false) and is as tall as its content, at least 125 pt.
/// - The pill is as wide as the column less 30 pt on each side, 10 pt below the copy, and as tall
///   as it measures.
///
/// The column is `max(minHeight, sum of the parts)` tall, so it scrolls only when the parts do
/// not fit the window.
struct TactileCalculatorWelcomeColumn: Layout {
    var minHeight: CGFloat
    var bottomPadding: CGFloat
    var heroHeight: CGFloat
    var growsCopy: Bool

    /// `copy.minHeight`.
    private static let copyMinHeight: CGFloat = 125
    /// `cta.marginTop` and `cta.marginHorizontal`.
    private static let buttonTop: CGFloat = 10
    private static let buttonInset: CGFloat = 30

    private struct Metrics {
        var copyHeight: CGFloat
        var buttonHeight: CGFloat
        var total: CGFloat
    }

    private func metrics(width: CGFloat, subviews: Subviews) -> Metrics {
        let buttonWidth = max(0, width - 2 * Self.buttonInset)
        let buttonHeight = subviews[2].sizeThatFits(ProposedViewSize(width: buttonWidth, height: nil)).height
        let copyBase: CGFloat
        if growsCopy {
            copyBase = Self.copyMinHeight
        } else {
            let natural = subviews[1].sizeThatFits(ProposedViewSize(width: width, height: nil)).height
            copyBase = max(natural, Self.copyMinHeight)
        }
        let sum = heroHeight + copyBase + Self.buttonTop + buttonHeight + bottomPadding
        let total = max(minHeight, sum)
        let copyHeight = growsCopy ? copyBase + (total - sum) : copyBase
        return Metrics(copyHeight: copyHeight, buttonHeight: buttonHeight, total: total)
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 0
        guard subviews.count == 3 else {
            return CGSize(width: width, height: minHeight)
        }
        return CGSize(width: width, height: metrics(width: width, subviews: subviews).total)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard subviews.count == 3 else { return }
        let width = bounds.width
        let measured = metrics(width: width, subviews: subviews)
        let buttonWidth = max(0, width - 2 * Self.buttonInset)
        subviews[0].place(
            at: CGPoint(x: bounds.minX, y: bounds.minY),
            anchor: .topLeading,
            proposal: ProposedViewSize(width: width, height: heroHeight)
        )
        subviews[1].place(
            at: CGPoint(x: bounds.minX, y: bounds.minY + heroHeight),
            anchor: .topLeading,
            proposal: ProposedViewSize(width: width, height: measured.copyHeight)
        )
        subviews[2].place(
            at: CGPoint(
                x: bounds.minX + Self.buttonInset,
                y: bounds.minY + heroHeight + measured.copyHeight + Self.buttonTop
            ),
            anchor: .topLeading,
            proposal: ProposedViewSize(width: buttonWidth, height: measured.buttonHeight)
        )
    }
}
