import Foundation
import Observation

/// One to-do row as the screen holds it: what it shows and the numbers that move it.
@MainActor @Observable
final class DragMultiSelectRow: Identifiable {
    /// A row that is leaving matches no index, so a held finger never previews on it.
    static let noIndex = Int.min

    nonisolated let id: String
    let task: DragMultiSelectTask
    /// Position in the full list, which keeps the rows in their order on screen.
    let order: Int
    /// Position among the rows that are still in the list.
    var liveIndex: Int

    var isSelected = false
    /// A soft light seam where this row meets a selected row above.
    var seam = false
    /// -1 grow from the top edge, 1 from the bottom, 0 from the centre.
    var origin = 0.0
    var isExiting = false

    /// The band's growth: 0 none, 1 full.
    let band: DragMultiSelectNumber
    /// 0 to 1 sweeps a light across the band when the row joins.
    let gloss: DragMultiSelectNumber
    /// 0 to 1 a ring leaves the circle when the row is picked.
    let ring: DragMultiSelectNumber
    let topRadius: DragMultiSelectNumber
    let bottomRadius: DragMultiSelectNumber
    /// The selection circle sliding in and out.
    let circleIn: DragMultiSelectNumber
    /// Where the row sits in the list, and how far it has slid off to the right.
    let y: DragMultiSelectNumber
    let x: DragMultiSelectNumber
    /// Entering and leaving: opacity and a rise in pt.
    let appear: DragMultiSelectNumber
    let lift: DragMultiSelectNumber

    @ObservationIgnored var topTarget = DragMultiSelectLayout.bandRadius
    @ObservationIgnored var bottomTarget = DragMultiSelectLayout.bandRadius
    @ObservationIgnored var targetY: Double

    init(task: DragMultiSelectTask, order: Int, liveIndex: Int, y: Double, circleIn: Double, clock: DragMultiSelectClock) {
        id = task.id
        self.task = task
        self.order = order
        self.liveIndex = liveIndex
        band = DragMultiSelectNumber(0, clock: clock)
        gloss = DragMultiSelectNumber(1, clock: clock)
        ring = DragMultiSelectNumber(1, clock: clock)
        topRadius = DragMultiSelectNumber(DragMultiSelectLayout.bandRadius, clock: clock)
        bottomRadius = DragMultiSelectNumber(DragMultiSelectLayout.bandRadius, clock: clock)
        self.circleIn = DragMultiSelectNumber(circleIn, clock: clock)
        self.y = DragMultiSelectNumber(y, clock: clock)
        x = DragMultiSelectNumber(0, clock: clock)
        appear = DragMultiSelectNumber(1, clock: clock)
        lift = DragMultiSelectNumber(0, clock: clock)
        targetY = y
    }

    /// Stops every number where it is: a row that is leaving keeps the look it had when it was picked.
    func freeze() {
        for number in [band, gloss, ring, topRadius, bottomRadius, circleIn, y] { number.freeze() }
        liveIndex = Self.noIndex
        isExiting = true
    }
}
