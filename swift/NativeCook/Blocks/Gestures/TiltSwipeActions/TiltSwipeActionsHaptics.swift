import UIKit

/// Discrete tactile events only; never played while the demo runs.
enum TiltSwipeActionsHaptic {
    case detent, split, arm, flag, undo

    @MainActor
    func play() {
        switch self {
        case .detent, .flag:
            UISelectionFeedbackGenerator().selectionChanged()
        case .arm, .split:
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        case .undo:
            UIImpactFeedbackGenerator(style: .soft).impactOccurred()
        }
    }
}
