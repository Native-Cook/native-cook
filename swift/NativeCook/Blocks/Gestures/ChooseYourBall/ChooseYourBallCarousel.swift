import Observation

// The loop of balls: a drag turns it, a spring lands it. Mirrors useChooseBallCarousel.ts.

@MainActor
@Observable
final class ChooseYourBallCarousel {
    let order = ChooseYourBallCatalog.order
    var total: Int { order.count }

    /// Position on the loop in slots. Fractional while moving and unbounded: it accumulates
    /// freely, and every reader that needs a ball folds it with `wrapIndex`.
    private(set) var offset: Double
    /// The slot nearest to `offset`: the ball the screen names. Changes halfway through a turn,
    /// when the ball's face is turned away.
    private(set) var preview: Int

    var previewBall: ChooseYourBallID { order[preview] }

    /// With Reduce Motion on, the snap is not played: the loop is on its slot at once, which is
    /// what the reference's spring does under that setting.
    @ObservationIgnored var reduceMotion = false

    @ObservationIgnored private let runner = ChooseYourBallSpringRunner()
    @ObservationIgnored private var panStart = 0.0
    @ObservationIgnored private var panHome = 0.0

    init(initial: ChooseYourBallID) {
        let start = ChooseYourBallCatalog.order.firstIndex(of: initial) ?? 0
        offset = Double(start)
        preview = start
        runner.onChange = { [weak self] value in self?.move(to: value) }
    }

    /// A finger landed: whatever the loop was doing, it holds still under it.
    func touchBegan() {
        runner.stop()
        panStart = offset
        panHome = ChooseYourBallGeometry.nearestSlot(offset)
    }

    /// `translation` is the finger's horizontal travel since the drag was recognised.
    func panChanged(translation: Double) {
        let drift = panStart - translation / ChooseYourBallGeometry.itemStride - panHome
        let over = abs(drift) - ChooseYourBallGeometry.panReach
        if over <= 0 {
            move(to: panHome + drift)
        } else {
            // Past one ball the loop still answers, less and less.
            let resisted = ChooseYourBallGeometry.panReach + over / (1 + over * ChooseYourBallGeometry.panResist)
            move(to: panHome + (drift < 0 ? -resisted : resisted))
        }
    }

    /// `velocity` is the finger's horizontal speed at release, in points per second.
    func panEnded(velocity: Double) {
        let slotsPerSecond = -velocity / ChooseYourBallGeometry.itemStride
        let projected = offset + slotsPerSecond * ChooseYourBallGeometry.flingProjection
        let reach = ChooseYourBallGeometry.panReach
        settle(on: ChooseYourBallGeometry.nearestSlot(min(max(projected, panHome - reach), panHome + reach)))
    }

    /// A drag the system took away mid-way lands on the nearest slot.
    func panCancelled() {
        settle(on: ChooseYourBallGeometry.nearestSlot(offset))
    }

    /// Goes to `slot` the short way round, any distance, on the same spring.
    func select(slot: Int) {
        runner.stop()
        let here = ChooseYourBallGeometry.nearestSlot(offset)
        settle(on: here + ChooseYourBallGeometry.cyclicDelta(index: Double(slot), offset: here, total: total))
    }

    private func settle(on target: Double) {
        if reduceMotion {
            runner.stop()
            move(to: target)
        } else {
            runner.run(from: offset, to: target, spring: ChooseYourBallGeometry.snapSpring)
        }
    }

    private func move(to value: Double) {
        offset = value
        let nearest = ChooseYourBallGeometry.wrapIndex(Int(ChooseYourBallGeometry.nearestSlot(value)), total: total)
        if nearest != preview { preview = nearest }
    }
}
