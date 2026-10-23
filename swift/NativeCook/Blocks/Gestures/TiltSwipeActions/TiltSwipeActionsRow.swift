import SwiftUI

/// One spending row: its shared values (the React Native `SwipeRow`'s `useSharedValue`s) and the
/// plain flags of its swipe. The model drives it; the row's view only reads the animated values.
@MainActor
final class TiltSwipeActionsRow {
    let item: TiltSwipeActionsTransaction
    /// A row put back by Undo grows in instead of being there.
    let restoring: Bool

    /// Horizontal offset of the content (negative = left).
    let x: TiltSwipeActionsAnimated
    /// 1 = full height, 0 = collapsed.
    let height: TiltSwipeActionsAnimated
    /// Lean, in degrees.
    let tilt = TiltSwipeActionsAnimated(0)
    /// 0...1: the row is held (glass slab, scale).
    let lift = TiltSwipeActionsAnimated(0)
    /// 0...1: Delete has taken the whole strip.
    let full = TiltSwipeActionsAnimated(0)
    /// 0...1: Delete tile colour, white to pink.
    let red = TiltSwipeActionsAnimated(0)
    /// Hides the actions while a row slides back in.
    let strip: TiltSwipeActionsAnimated
    let fade: TiltSwipeActionsAnimated

    var start = 0.0
    var dragging = false
    var removing = false
    var opened = false

    var key: String { item.key }

    init(item: TiltSwipeActionsTransaction, restoring: Bool = false, reduceMotion: Bool = false) {
        self.item = item
        self.restoring = restoring
        x = TiltSwipeActionsAnimated(restoring ? -TiltSwipeActionsFlat.width * 1.15 : 0)
        height = TiltSwipeActionsAnimated(restoring ? 0 : 1)
        strip = TiltSwipeActionsAnimated(restoring ? 0 : 1)
        fade = TiltSwipeActionsAnimated(restoring && reduceMotion ? 0 : 1)
    }

    private var values: [TiltSwipeActionsAnimated] { [x, height, tilt, lift, full, red, strip, fade] }

    func advance(_ dt: Double) {
        for value in values where value.isAnimating {
            value.advance(dt)
        }
    }
}
