import Foundation

/// The React Native block's shared values (`clock`, `page`, `playing`, `loopStart`, `gestureStart`,
/// `resumeAt`) with the frame callback and the pan handlers that move them, as a value that is
/// sampled at a date. Nothing here runs per frame: the view samples it when it draws.
struct DonationFanCarouselPlayback {
    enum Mode {
        /// `playing` is false: `clock` and `page` stay where they were left.
        case held
        /// `playing` is true: the frame callback writes both from the loop clock.
        case looping(since: Date)
        /// `page.set(withSpring(...))` is running.
        case spring(DonationFanCarouselSpring)
    }

    /// `value *= 0.28` and `3 + (value - 3) * 0.28` in the pan's `onUpdate`.
    private static let rubberBand = 0.28
    /// `value < 1.5 ? 0 : 1800` in the pan's `onUpdate`.
    private static let dragClockSplit = 1.5
    private static let dragClockLate = 1800.0

    private(set) var mode = Mode.held
    private var heldClock = 0.0
    private var heldPage = 0.0
    private var gestureStart = 0.0
    private(set) var resumeAt: Date?
    private var touching = false

    var isLooping: Bool {
        if case .looping = mode { return true }
        return false
    }

    /// Seconds the running spring needs to settle, or nil when none runs.
    var springSettleSeconds: Double? {
        if case let .spring(spring) = mode { return spring.settleSeconds }
        return nil
    }

    /// The values the screen draws at `date`.
    func sample(at date: Date) -> (clock: Double, page: Double) {
        switch mode {
        case .held:
            return (heldClock, heldPage)
        case let .looping(since):
            let elapsed = max(0, date.timeIntervalSince(since) * 1000)
            let time = elapsed.truncatingRemainder(dividingBy: DonationFanCarouselMotion.loopMs)
            return (time, DonationFanCarouselMotion.pageAt(time))
        case let .spring(spring):
            return (heldClock, spring.value(at: date))
        }
    }

    /// The effect's start (autoPlay and not reduced): `loopStart = -1; playing = true`, with the loop
    /// clock beginning at `date`, the first frame.
    mutating func startLooping(at date: Date) {
        touching = false
        mode = .looping(since: date)
        heldClock = 0
        heldPage = 0
        resumeAt = nil
    }

    /// The effect's other branch: `playing = false; clock = 0; page = 0`.
    mutating func reset() {
        touching = false
        mode = .held
        heldClock = 0
        heldPage = 0
        resumeAt = nil
    }

    /// `freezeAt`: the clock and the page held at a fixed time.
    mutating func freeze(atMilliseconds time: Double) {
        touching = false
        mode = .held
        heldClock = time
        heldPage = DonationFanCarouselMotion.pageAt(time)
        resumeAt = nil
    }

    /// The pan's and the tap's `onBegin`: `cancelAnimation(page)`, `playing = false`,
    /// `gestureStart = page`, `resumeAt = 0`.
    mutating func touchBegan(at date: Date) {
        touching = true
        let current = sample(at: date)
        mode = .held
        heldClock = current.clock
        heldPage = current.page
        gestureStart = current.page
        resumeAt = nil
    }

    /// The finger owns the fan until it lifts, including drags longer than the idle timeout.
    mutating func drag(translationX: Double, scale: Double) {
        guard !isLooping else { return }
        var value = gestureStart - translationX / (DonationFanCarouselMotion.dragPointsPerPage * scale)
        if value < 0 { value *= Self.rubberBand }
        if value > 3 { value = 3 + (value - 3) * Self.rubberBand }
        mode = .held
        heldPage = value
        heldClock = value < Self.dragClockSplit ? 0 : Self.dragClockLate
    }

    /// The pan's `onEnd`, with the release velocity in points per second.
    mutating func release(velocityX: Double, scale: Double, at date: Date) {
        guard !isLooping else { return }
        let velocity = -velocityX / (DonationFanCarouselMotion.dragPointsPerPage * scale)
        let current = sample(at: date)
        let projected = DonationFanCarouselMath.jsRound(current.page + velocity * DonationFanCarouselMotion.projectSeconds)
        let target = max(0, min(3, projected))
        mode = .spring(
            DonationFanCarouselSpring(
                from: current.page,
                to: target,
                velocity: velocity,
                durationMs: DonationFanCarouselMotion.snapDurationMs,
                dampingRatio: DonationFanCarouselMotion.snapDampingRatio,
                startedAt: date
            )
        )
        heldClock = current.clock
        heldPage = current.page
    }

    /// A cancelled drag settles to the nearest valid donor without carrying untrustworthy velocity.
    mutating func cancelTouch(at date: Date) {
        guard !isLooping else { return }
        let current = sample(at: date)
        let target = max(0, min(3, DonationFanCarouselMath.jsRound(current.page)))
        heldClock = target < Self.dragClockSplit ? 0 : Self.dragClockLate
        heldPage = current.page
        mode = .spring(
            DonationFanCarouselSpring(
                from: current.page,
                to: target,
                velocity: 0,
                durationMs: DonationFanCarouselMotion.snapDurationMs,
                dampingRatio: DonationFanCarouselMotion.snapDampingRatio,
                startedAt: date
            )
        )
    }

    /// VoiceOver's increment/decrement action uses the same settle and idle-resume state as touch.
    mutating func moveFromAccessibility(
        to target: Double,
        animated: Bool,
        autoPlay: Bool,
        at date: Date
    ) {
        let current = sample(at: date)
        touching = false
        heldClock = target < Self.dragClockSplit ? 0 : Self.dragClockLate
        heldPage = current.page
        if animated {
            mode = .spring(
                DonationFanCarouselSpring(
                    from: current.page,
                    to: target,
                    velocity: 0,
                    durationMs: DonationFanCarouselMotion.snapDurationMs,
                    dampingRatio: DonationFanCarouselMotion.snapDampingRatio,
                    startedAt: date
                )
            )
        } else {
            mode = .held
            heldPage = target
        }
        resumeAt = autoPlay ? date.addingTimeInterval(DonationFanCarouselMotion.resumeMs / 1000) : nil
    }

    /// The spring has settled: the page rests on its target.
    mutating func finishSpring() {
        if case let .spring(spring) = mode {
            heldPage = spring.to
            mode = .held
        }
    }

    /// Only the end of the touch session starts the idle countdown.
    mutating func finalizeTouch(autoPlay: Bool, at date: Date) {
        touching = false
        if autoPlay {
            resumeAt = date.addingTimeInterval(DonationFanCarouselMotion.resumeMs / 1000)
        }
    }

    /// The frame callback's resume branch: back to rest and the loop from its start.
    mutating func resume(at date: Date) {
        guard !isLooping, !touching else { return }
        startLooping(at: date)
    }
}
