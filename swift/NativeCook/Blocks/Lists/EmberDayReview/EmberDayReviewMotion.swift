import SwiftUI
import UIKit

/// Which day page is in front.
enum EmberDayReviewDay {
    case yesterday
    case today
}

/// Interpolation helpers that behave like Reanimated's.
enum EmberDayReviewMath {
    /// `interpolate(x, input, output, 'clamp')`: piecewise linear, held at both ends.
    static func interpolate(_ x: Double, _ input: [Double], _ output: [Double]) -> Double {
        guard let first = input.first, let last = input.last, input.count == output.count else { return 0 }
        if x <= first { return output[0] }
        if x >= last { return output[output.count - 1] }
        var index = 1
        while input[index] < x { index += 1 }
        let span = input[index] - input[index - 1]
        let fraction = span > 0 ? (x - input[index - 1]) / span : 1
        return output[index - 1] + (output[index] - output[index - 1]) * fraction
    }

    static func clamp(_ x: Double) -> Double {
        min(1, max(0, x))
    }
}

/// `Easing.bezier(x1, y1, x2, y2)`: the cubic Reanimated and CSS use, solved for y at a given x.
struct EmberDayReviewBezier {
    private let ax: Double
    private let bx: Double
    private let cx: Double
    private let ay: Double
    private let by: Double
    private let cy: Double

    /// `OUT` in the React Native file: `Easing.bezier(0.23, 1, 0.32, 1)`.
    static let out = EmberDayReviewBezier(0.23, 1, 0.32, 1)

    init(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) {
        cx = 3 * x1
        bx = 3 * (x2 - x1) - cx
        ax = 1 - cx - bx
        cy = 3 * y1
        by = 3 * (y2 - y1) - cy
        ay = 1 - cy - by
    }

    private func x(_ t: Double) -> Double { ((ax * t + bx) * t + cx) * t }
    private func y(_ t: Double) -> Double { ((ay * t + by) * t + cy) * t }
    private func slope(_ t: Double) -> Double { (3 * ax * t + 2 * bx) * t + cx }

    func value(at progress: Double) -> Double {
        if progress <= 0 { return 0 }
        if progress >= 1 { return 1 }
        var t = progress
        for _ in 0..<8 {
            let error = x(t) - progress
            if abs(error) < 1e-6 { return y(t) }
            let derivative = slope(t)
            if abs(derivative) < 1e-6 { break }
            t -= error / derivative
        }
        var low = 0.0
        var high = 1.0
        t = progress
        for _ in 0..<40 {
            let current = x(t)
            if abs(current - progress) < 1e-6 { break }
            if current < progress { low = t } else { high = t }
            t = (low + high) / 2
        }
        return y(t)
    }
}

/// A shared value that is being driven to a target: `withTiming` or the critically damped
/// `withSpring({ duration, dampingRatio: 1 })`, evaluated from the time it started.
struct EmberDayReviewTween {
    enum Kind {
        case timing(duration: Double, curve: EmberDayReviewBezier)
        case spring(duration: Double)
    }

    let from: Double
    let to: Double
    let start: Date
    let kind: Kind
    /// Initial velocity of a spring, in value units per second.
    let velocity: Double

    func value(at now: Date) -> Double {
        let elapsed = max(0, now.timeIntervalSince(start))
        switch kind {
        case let .timing(duration, curve):
            return from + (to - from) * curve.value(at: elapsed / duration)
        case let .spring(duration):
            // Critically damped: x(t) = (x0 + (v0 + w x0) t) e^(-w t), with Reanimated 4.5's w (see `omega(for:)`).
            let omega = Self.omega(for: duration)
            let x0 = from - to
            return to + (x0 + (velocity + omega * x0) * elapsed) * exp(-omega * elapsed)
        }
    }

    /// Speed of a spring at `now`; a timing animation hands no velocity to the next one.
    func rate(at now: Date) -> Double {
        guard case let .spring(duration) = kind else { return 0 }
        let elapsed = max(0, now.timeIntervalSince(start))
        let omega = Self.omega(for: duration)
        let x0 = from - to
        let c2 = velocity + omega * x0
        return (c2 - omega * (x0 + c2 * elapsed)) * exp(-omega * elapsed)
    }

    /// Natural frequency of `{ duration, dampingRatio: 1 }`. Reanimated 4.5 reads `duration` as perceptual and
    /// solves the stiffness that has the energy at 6e-9 after 1.5 x duration (`calculateNewStiffnessToMatchDuration`);
    /// at damping ratio 1 and rest velocity that is w = 8.2456 / duration (20.61 rad/s for 400 ms, stiffness 424.9).
    static func omega(for duration: Double) -> Double {
        8.2456 / duration
    }

    /// Seconds after which the value is within a hair of its target.
    var settleTime: Double {
        switch kind {
        case let .timing(duration, _): return duration
        case let .spring(duration): return duration * 1.5
        }
    }
}

/// Everything the screen draws is a function of this, taken at one instant.
struct EmberDayReviewSample {
    /// The loop clock (`time` in the React Native file), 0 to 10.
    var t: Double
    /// `pageProgress`: 0 shows Yesterday, 1 shows Today.
    var page: Double
    /// `reviewProgress`: 0 shows the day surface, 1 has lowered it to reveal the Review page.
    var review: Double
    var visibleDay: EmberDayReviewDay
    var reviewVisible: Bool
}

/// The state machine of EmberDayReview.tsx: the 10 s loop, the touch that pauses it, the manual
/// page and review animations, and the day, tasks and commit state. The React Native file keeps
/// these in `useSharedValue` and `useState`; here the animated values are tweens read once a
/// frame, and what the structure depends on is published.
@MainActor
final class EmberDayReviewMotion: ObservableObject {
    /// Seconds the demo stays paused after the last touch (`setTimeout(..., 2000)`).
    static let resumeDelay = 2.0

    let autoPlay: Bool
    /// `normalizedPreview`: the loop time to hold, or nil.
    let previewTime: Double?
    var onCommit: ((Int) -> Void)?
    /// Mirrors of the environment, written by the view every body pass.
    var reduced = false
    var foreground = true

    @Published private(set) var day: EmberDayReviewDay = .yesterday
    @Published private(set) var review = false
    @Published private(set) var committed = false
    @Published private(set) var paused = false
    @Published private(set) var ticking = false
    /// The loop is between two moving stretches: nothing changes, so the timeline can rest.
    @Published private(set) var still = false
    @Published private(set) var epoch = 0
    @Published private(set) var completed: [Int] = []
    @Published private(set) var tasks = EmberDayReviewContent.today

    private var loopStart: Date?
    private var frozenTime: Double
    private var timeTween: EmberDayReviewTween?
    private var pageBase = 0.0
    private var pageTween: EmberDayReviewTween?
    private var reviewBase = 0.0
    private var reviewTween: EmberDayReviewTween?
    private var holding = false
    private var editing = false
    private var reviewTarget = false
    private var resumeWork: DispatchWorkItem?
    private var tickEnd = Date.distantPast
    private var stillWork: DispatchWorkItem?

    /// Stretches of the loop (seconds) in which something moves, each padded by 50 ms: the hero, stars,
    /// summary and timeline until 1.85; the Review drop 4.55 to 6.35; the page to Today 7 to 7.5; and the
    /// page back with the fade out from 9.25 to the end of the loop, which runs on into the first.
    private static let movingStretches: [(start: Double, end: Double)] = [
        (0, 1.9), (4.5, 6.4), (6.95, 7.55), (9.2, 10),
    ]

    private let selectionHaptic = UISelectionFeedbackGenerator()
    private let impactHaptic = UIImpactFeedbackGenerator(style: .light)
    private let notificationHaptic = UINotificationFeedbackGenerator()

    init(autoPlay: Bool, previewTime: Double?) {
        self.autoPlay = autoPlay
        if let previewTime, previewTime.isFinite {
            let loop = EmberDayReview.loop
            let wrapped = previewTime >= 0
                ? previewTime.truncatingRemainder(dividingBy: loop)
                : (previewTime.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop)
            self.previewTime = wrapped
            frozenTime = wrapped
        } else {
            self.previewTime = nil
            frozenTime = autoPlay ? 0 : 2
        }
    }

    // MARK: - Reading the clocks

    /// `automatic`: the loop drives the screen.
    var automatic: Bool {
        previewTime != nil || (autoPlay && !paused && foreground && !reduced && !review && !committed)
    }

    /// Whether the timeline has anything to draw next frame.
    var isRunning: Bool {
        previewTime == nil && ((automatic && !still) || ticking)
    }

    func timeValue(at now: Date) -> Double {
        if let previewTime { return previewTime }
        if let loopStart {
            let elapsed = max(0, now.timeIntervalSince(loopStart))
            return elapsed.truncatingRemainder(dividingBy: EmberDayReview.loop)
        }
        return timeTween?.value(at: now) ?? frozenTime
    }

    func pageValue(at now: Date) -> Double {
        pageTween?.value(at: now) ?? pageBase
    }

    func reviewValue(at now: Date) -> Double {
        reviewTween?.value(at: now) ?? reviewBase
    }

    func sample(at now: Date) -> EmberDayReviewSample {
        let t = timeValue(at: now)
        if automatic {
            let page = EmberDayReviewMath.interpolate(t, [7, 7.5, 9.25, 9.85], [0, 1, 1, 0])
            let review = EmberDayReviewMath.interpolate(
                t,
                [4.55, 4.6, 4.7, 4.8, 4.95, 6.03, 6.1, 6.2, 6.35],
                [0, 0.05, 0.32, 0.8, 1, 1, 0.85, 0.1, 0]
            )
            // `autoStage`: review wins over the page once it is past half way.
            let stage = review > 0.5 ? 1 : (page > 0.5 ? 2 : 0)
            return EmberDayReviewSample(
                t: t,
                page: page,
                review: review,
                visibleDay: stage == 2 ? .today : .yesterday,
                reviewVisible: stage == 1
            )
        }
        return EmberDayReviewSample(
            t: t,
            page: pageValue(at: now),
            review: reviewValue(at: now),
            visibleDay: day,
            reviewVisible: review
        )
    }

    // MARK: - The loop clock

    /// The effect on `[autoPlay, automatic, foreground, normalizedPreview, reduced, time]`.
    func sync() {
        let now = Date()
        if previewTime != nil {
            loopStart = nil
            return
        }
        if !foreground {
            freezeTime(at: now)
        } else if reduced || !autoPlay {
            loopStart = nil
            timeTween = nil
            frozenTime = 2
        } else if automatic {
            if loopStart == nil {
                // `time.set(0)` then `withRepeat(withTiming(LOOP, linear))`.
                timeTween = nil
                loopStart = now
            }
        } else if loopStart != nil {
            freezeTime(at: now)
        }
        scheduleStillCheck()
        epoch += 1
    }

    /// Works out whether the loop is in a moving stretch now, and wakes up at the next edge to look again.
    private func scheduleStillCheck() {
        stillWork?.cancel()
        stillWork = nil
        guard previewTime == nil, automatic, let loopStart else {
            if still { still = false }
            return
        }
        let loop = EmberDayReview.loop
        let now = Date()
        let t = max(0, now.timeIntervalSince(loopStart)).truncatingRemainder(dividingBy: loop)
        var delay: Double
        if let current = Self.movingStretches.first(where: { t >= $0.start && t < $0.end }) {
            if still { still = false }
            delay = current.end - t
            // The stretch that ends with the loop runs on into the one that starts it.
            if current.end >= loop { delay = Self.movingStretches[0].end + (loop - t) }
        } else {
            if !still { still = true }
            let next = Self.movingStretches.first(where: { $0.start > t })?.start ?? loop
            delay = next - t
        }
        let work = DispatchWorkItem { [weak self] in self?.scheduleStillCheck() }
        stillWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + max(0.01, delay), execute: work)
    }

    /// `cancelAnimation(time)`: the value stays where it is.
    private func freezeTime(at now: Date) {
        frozenTime = timeValue(at: now)
        timeTween = nil
        loopStart = nil
    }

    private func beginTicking(for seconds: Double) {
        let end = Date().addingTimeInterval(seconds + 0.1)
        tickEnd = max(tickEnd, end)
        ticking = true
        DispatchQueue.main.asyncAfter(deadline: .now() + seconds + 0.15) { [weak self] in
            guard let self, Date() >= self.tickEnd else { return }
            self.ticking = false
        }
    }

    // MARK: - Touch

    /// `touch()`: take the screen over from the loop, wherever it is.
    func touch() {
        guard previewTime == nil else { return }
        let now = Date()
        resumeWork?.cancel()
        let current = sample(at: now)
        freezeTime(at: now)
        pageBase = current.page
        pageTween = nil
        reviewBase = current.review
        reviewTween = nil
        paused = true
        day = current.visibleDay
        reviewTarget = current.reviewVisible
        review = current.reviewVisible
        if !holding { scheduleResume() }
        epoch += 1
    }

    func touchStart() {
        guard previewTime == nil else { return }
        holding = true
        touch()
    }

    func touchEnd() {
        guard previewTime == nil else { return }
        holding = false
        let now = Date()
        let reviewEnd: Double = reviewTarget ? 1 : 0
        if reduced {
            reviewBase = reviewEnd
            reviewTween = nil
        } else {
            reviewTween = EmberDayReviewTween(
                from: reviewValue(at: now),
                to: reviewEnd,
                start: now,
                kind: .timing(duration: reviewEnd > 0 ? 0.28 : 0.24, curve: .out),
                velocity: 0
            )
        }
        drivePage(to: day == .today ? 1 : 0, at: now)
        scheduleResume()
        beginTicking(for: 0.6)
        epoch += 1
    }

    private func scheduleResume() {
        resumeWork?.cancel()
        let work = DispatchWorkItem { [weak self] in
            guard let self, !self.editing else { return }
            self.paused = false
            self.sync()
        }
        resumeWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.resumeDelay, execute: work)
    }

    /// `page.set(withSpring(target, { duration: 400, dampingRatio: 1 }))`, or the value itself under Reduce Motion.
    private func drivePage(to target: Double, at now: Date) {
        if reduced {
            pageBase = target
            pageTween = nil
            return
        }
        let carried = pageTween?.rate(at: now) ?? 0
        pageTween = EmberDayReviewTween(
            from: pageValue(at: now),
            to: target,
            start: now,
            kind: .spring(duration: 0.4),
            velocity: carried
        )
    }

    // MARK: - Actions

    func showReview(_ open: Bool) {
        guard previewTime == nil else { return }
        touch()
        let now = Date()
        reviewTarget = open
        review = open
        if reduced {
            reviewBase = open ? 1 : 0
            reviewTween = nil
        } else {
            reviewTween = EmberDayReviewTween(
                from: reviewValue(at: now),
                to: open ? 1 : 0,
                start: now,
                kind: .timing(duration: open ? 0.28 : 0.24, curve: .out),
                velocity: 0
            )
            beginTicking(for: 0.4)
        }
    }

    func select(_ next: EmberDayReviewDay) {
        guard previewTime == nil else { return }
        touch()
        let now = Date()
        day = next
        drivePage(to: next == .today ? 1 : 0, at: now)
        if next == .yesterday {
            if reduced {
                frozenTime = 2
                timeTween = nil
            } else {
                timeTween = EmberDayReviewTween(
                    from: frozenTime,
                    to: 2,
                    start: now,
                    kind: .timing(duration: 0.65, curve: .out),
                    velocity: 0
                )
            }
        }
        if !reduced { beginTicking(for: 0.8) }
        selectionHaptic.selectionChanged()
    }

    func toggle(_ index: Int) {
        guard previewTime == nil else { return }
        touch()
        committed = false
        if completed.contains(index) {
            completed.removeAll { $0 == index }
        } else {
            completed.append(index)
        }
        selectionHaptic.selectionChanged()
    }

    /// The footer button: "Today" on the first page, "Commit" on the second.
    func primaryTapped() {
        guard previewTime == nil else { return }
        if sample(at: Date()).visibleDay == .yesterday {
            select(.today)
        } else {
            touch()
            committed = true
            onCommit?(completed.count)
            notificationHaptic.notificationOccurred(.success)
        }
    }

    func addTask(_ draft: String) {
        guard previewTime == nil else { return }
        let title = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty else { return }
        if !tasks.contains(title) { tasks.append(title) }
        committed = false
        impactHaptic.impactOccurred()
    }

    /// `onFocus` / `onBlur` of the Add Action field: typing never lets the demo take the screen back.
    func focusChanged(_ focused: Bool) {
        guard previewTime == nil else { return }
        editing = focused
        touch()
    }
}
