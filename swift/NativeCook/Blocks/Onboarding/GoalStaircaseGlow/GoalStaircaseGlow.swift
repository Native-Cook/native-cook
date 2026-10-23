import SwiftUI

/// A black stage with two lights: a glowing ball at the lower left and a goal at the upper right.
/// The ball wonders how to get there; a tap calls a staircase of words, each further tap makes the
/// ball hop one step up (a long press runs the rest), every label lights as the ball lands on it,
/// and on the last hop the goal bursts. A tap after that fades everything out and starts again.
struct GoalStaircaseGlow: View {
    /// The words on the stairs, from the lowest step up.
    var steps = ["PLAN", "ACTION", "FOCUS", "PATIENCE", "DISCIPLINE"]
    var goalLabel = "GOAL"
    var background = Color(hex: 0x010101)
    /// Plays the whole climb by itself, every `GoalStaircaseGlowMotion.loop` seconds.
    var autoPlay = false
    /// Called with the place in `steps` of the label the ball lands on.
    var onStep: ((Int) -> Void)? = nil
    /// Called when the ball reaches the goal.
    var onGoal: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The scene's clock counts in seconds from here.
    @State private var epoch = Date()
    /// The times the scene is drawn from; nil until something has happened.
    @State private var plot: GoalStaircaseGlowPlot? = nil
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// Something moves until this time on the clock; after it the frames stop until the next touch.
    @State private var busyUntil = M.intro
    @State private var activity = 0
    @State private var settled = false
    /// What waits for the ball to land.
    @State private var landings: [Task<Void, Never>] = []
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var goalHaptic = UINotificationFeedbackGenerator()

    private var now: Double { Date().timeIntervalSince(epoch) }
    private var current: GoalStaircaseGlowPlot { plot ?? GoalStaircaseGlowPlot(born: 0, steps: steps.count) }

    var body: some View {
        TimelineView(.animation(paused: settled && !autoPlay)) { timeline in
            GoalStaircaseGlowStage(t: timeline.date.timeIntervalSince(epoch), plot: current, steps: steps, goalLabel: goalLabel, still: reduceMotion)
        }
        .background(background)
        .ignoresSafeArea()
        .contentShape(Rectangle())
        .onTapGesture { press(all: false) }
        .onLongPressGesture(minimumDuration: 0.5) { press(all: true) }
        .preferredColorScheme(.dark)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(goalLabel): \(steps.joined(separator: ", "))")
        .accessibilityHint("Takes the next step")
        .accessibilityAddTraits(.isButton)
        .onDisappear { landings.forEach { $0.cancel() } }
        .onChange(of: steps.count) {
            // Another number of steps is another staircase: the scene starts over.
            landings.forEach { $0.cancel() }
            landings = []
            plot = GoalStaircaseGlowPlot(born: now, steps: steps.count)
            wake(M.intro)
        }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
        .task(id: activity) {
            settled = false
            try? await Task.sleep(for: .seconds(max(0, busyUntil - now) + M.idle))
            if !Task.isCancelled { settled = true }
        }
    }

    private func press(all: Bool) {
        // A touch takes over from the autoPlay loop, which starts again from the dark a while later.
        touches += 1
        tapHaptic.impactOccurred()
        act(all: all, byHand: true)
    }

    /// Keeps the frames coming for the next `seconds`.
    private func wake(_ seconds: Double) {
        busyUntil = max(busyUntil, now + seconds)
        activity += 1
    }

    /// Everything fades to black, and after a dark moment the scene appears again. Returns when,
    /// on the clock: that is also the answer while a fade or the dark is already under way.
    @discardableResult
    private func reset() -> Double {
        let t = now
        var plot = current.at(t)
        guard plot.fade >= M.none else { return plot.fade + M.fade + M.dark }
        guard t >= plot.born else { return plot.born }
        landings.forEach { $0.cancel() }
        landings = []
        plot.fade = t
        self.plot = plot
        wake(M.fade + M.dark + M.intro)
        return t + M.fade + M.dark
    }

    /// The next thing: the stairs, then a hop (`all`: every hop that is left), then starting over.
    private func act(all: Bool, byHand: Bool) {
        let t = now
        var plot = current.at(t)
        guard plot.fade >= M.none, t >= plot.born else { return }
        let count = steps.count
        if plot.stairs >= M.none {
            // The first touch calls the stairs; a long press goes on to climb them as they arrive.
            plot.stairs = t
            if !all {
                self.plot = plot
                wake(Double(count) * M.stairGap + M.stairIn)
                return
            }
        }
        guard var next = plot.hops.firstIndex(where: { $0 >= M.none }) else {
            // The climb is over: a touch after the burst starts again.
            if t > plot.hops[count] + M.goalHop + M.burst { reset() }
            return
        }
        repeat {
            // A hop waits for the one before it to land, and for its label to be there.
            let free = next == 0 ? 0 : plot.hops[next - 1] + M.hop
            let ready = next < count ? plot.stairs + Double(next) * M.stairGap + M.stairIn : 0
            let from = max(t, free, ready)
            plot.hops[next] = from
            let landing = from + (next < count ? M.hop : M.goalHop) - t
            let index = next
            landings.append(Task { @MainActor in
                try? await Task.sleep(for: .seconds(max(0, landing)))
                guard !Task.isCancelled else { return }
                // The loop that plays by itself is silent.
                if index < count {
                    if byHand { tapHaptic.impactOccurred() }
                    onStep?(index)
                } else {
                    if byHand { goalHaptic.notificationOccurred(.success) }
                    onGoal?()
                }
            })
            next += 1
        } while all && next <= count
        self.plot = plot
        wake(plot.hops[next - 1] + M.goalHop + M.burst - t)
    }

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            // The loop starts over from the dark, when the scene appears again.
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            let born = reset()
            try? await Task.sleep(for: .seconds(max(0, born - now)))
            guard !Task.isCancelled else { return }
        }
        let moves: [(Double, () -> Void)] = [
            (M.stairsAt, { act(all: false, byHand: false) }), (M.climbAt, { act(all: true, byHand: false) }), (M.resetAt, { reset() }),
        ]
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            for (at, move) in moves {
                try? await clock.sleep(until: origin + .seconds(at))
                guard !Task.isCancelled else { return }
                move()
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
}

private typealias M = GoalStaircaseGlowMotion

#Preview {
    GoalStaircaseGlow(autoPlay: true)
}
