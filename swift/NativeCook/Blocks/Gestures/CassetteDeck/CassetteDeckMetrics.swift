import SwiftUI

/// The block's artboard on the screen it is given: points scale with the width, and a taller screen
/// centres the artboard vertically (the source's `s` and `oy`).
struct CassetteDeckMetrics: Equatable {
    let width: CGFloat
    let height: CGFloat

    init(size: CGSize) {
        width = size.width
        height = size.height
    }

    var scale: CGFloat { width / CassetteDeckTheme.screen.width }

    var offsetY: CGFloat { max(0, (height - CassetteDeckTheme.screen.height * scale) / 2) }
}
