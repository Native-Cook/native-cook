import UIKit

/// Discrete tactile events only; never played while the demo plays.
@MainActor
final class DragMultiSelectHaptics {
    enum Kind { case enter, tick, exit, edge, commit }

    private let selection = UISelectionFeedbackGenerator()
    private let rigid = UIImpactFeedbackGenerator(style: .rigid)
    private let heavy = UIImpactFeedbackGenerator(style: .heavy)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let light = UIImpactFeedbackGenerator(style: .light)

    func play(_ kind: Kind) {
        switch kind {
        case .tick: selection.selectionChanged()
        case .edge: rigid.impactOccurred()
        case .commit: heavy.impactOccurred()
        case .enter: medium.impactOccurred()
        case .exit: light.impactOccurred()
        }
    }
}
