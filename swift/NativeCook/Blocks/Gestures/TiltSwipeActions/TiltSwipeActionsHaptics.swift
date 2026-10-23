import UIKit

/// Discrete tactile events only; never played while the demo runs.
enum TiltSwipeActionsHaptic {
    case open, arm, disarm, undo, tap, commit

    @MainActor
    func play() {
        switch self {
        case .open:
            UISelectionFeedbackGenerator().selectionChanged()
        case .arm:
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        case .disarm:
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        case .commit:
            UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
        case .undo:
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        case .tap:
            UIImpactFeedbackGenerator(style: .soft).impactOccurred()
        }
    }
}
