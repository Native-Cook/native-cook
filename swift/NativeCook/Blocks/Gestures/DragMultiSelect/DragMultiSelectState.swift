import Foundation
import Observation

/// What the views read. The engine writes it; only the views that read a property redraw when it changes.
@MainActor @Observable
final class DragMultiSelectState {
    /// Every row on screen, the ones leaving included, in list order.
    var mounted: [DragMultiSelectRow] = []
    var isSelecting = false
    var selectedCount = 0
    var pressRow = -1
    /// The add button's ambient breath, -1 to 1.
    var breath = 0.0
    var ghostX = 0.0
    var ghostY = 0.0
    var width = 0.0
}
