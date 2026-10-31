import AVFoundation
import SwiftUI
import UIKit

/// Drives autoplay and touch using seconds on a shared bag clock.
/// Packing and unpacking timestamps let a gesture take over the current script pose without a jump.
/// Sleeping skips stationary intervals while keeping the physical frame delta capped at 0.05 seconds.
final class MarketBagCheckoutClock: ObservableObject {
    @Published var running = false
    @Published private(set) var sleeping = false
    private var sleepPending = false
    private var sleepStart: Date?
    private var wakeTimer: Timer?
    private(set) var automatic = false
    private(set) var loopTime = 0.0
    private(set) var bagTime = 0.0
    private(set) var holding = false
    private var lastTouchTime = -10.0
    private var paidTime = -10.0
    private var resetTime = -10.0
    private var packTimes = [Double](repeating: -1, count: 5)
    private var unpackTimes = [Double](repeating: -1, count: 5)
    private var holdProgress = 0.0
    private var hapticTicks = 0
    private var previousDate: Date?
    private(set) var pose = MarketBagCheckoutMotion.Pose()
    private var players: [String: AVAudioPlayer] = [:]
    private let selection = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let success = UINotificationFeedbackGenerator()
    var reduceMotion = false

    /// Follows scene activity and prepares the bundled sound cues for meaningful interaction events.
    func active(_ active: Bool) {
        wake()
        previousDate = nil
        if !active {
            players.values.forEach { $0.stop() }
            return
        }
        guard players.isEmpty else { return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
        for (name, volume) in [("lift", 0.35), ("drop", 0.6), ("cinch", 0.5), ("paid", 0.6)] {
            if let url = Bundle.main.url(forResource: "MarketBagCheckout-mbc-\(name)", withExtension: "wav"),
                let player = try? AVAudioPlayer(contentsOf: url)
            {
                player.volume = Float(volume)
                player.prepareToPlay()
                players[name] = player
            }
        }
    }

    /// Restarts a prepared cue unless reduced motion suppresses feedback.
    private func cue(_ name: String) {
        guard !reduceMotion, let player = players[name] else { return }
        player.currentTime = 0
        player.play()
    }

    /// Routes the reference selection, landing and payment milestones to native haptics.
    private func buzz(_ kind: Int) {
        if kind == 0 {
            selection.selectionChanged()
        } else if kind == 1 {
            light.impactOccurred()
        } else {
            success.notificationOccurred(.success)
        }
    }

    /// Samples a pinned script time or advances the clock by a capped elapsed time in seconds.
    /// Manual holds tick ten times, complete payment at 1.25 seconds, and resume autoplay after idle rest.
    func sample(_ sampleDate: Date, autoPlay: Bool, reduceMotion: Bool, pinnedTime: Double?)
        -> MarketBagCheckoutMotion.Pose
    {
        self.reduceMotion = reduceMotion
        if let pinnedTime { return MarketBagCheckoutMotion.script(pinnedTime) }
        // The script follows the wall clock, so a dropped frame never stretches the loop (the site's
        // recording is cut at exactly `loop`); physics and touch keep React Native's 0.05 s cap.
        let wallSeconds = min(max(0, previousDate.map { sampleDate.timeIntervalSince($0) } ?? 0.016), 1)
        let deltaSeconds = min(wallSeconds, 0.05)
        previousDate = sampleDate
        bagTime += deltaSeconds
        let anyPacked = (0..<5).contains { packTimes[$0] >= 0 && unpackTimes[$0] < 0 }
        if autoPlay && !reduceMotion && !automatic && !anyPacked && holdProgress < 0.001
            && bagTime - lastTouchTime > 2 && paidTime < 0 && bagTime - resetTime > 1.2
        {
            automatic = true
            loopTime = 0
        }
        if automatic {
            let previousLoopTime = loopTime
            loopTime += wallSeconds
            if loopTime >= MarketBagCheckoutMotion.loop { loopTime -= MarketBagCheckoutMotion.loop }
            pose = MarketBagCheckoutMotion.script(loopTime)
            if DemoSound.inAutoPlay && !reduceMotion {
                for i in 0..<5 {
                    let lift = 1 + Double(i) * 0.32
                    if previousLoopTime < lift && loopTime >= lift { cue("lift") }
                    if previousLoopTime < lift + MarketBagCheckoutMotion.fly
                        && loopTime >= lift + MarketBagCheckoutMotion.fly
                    {
                        cue("drop")
                    }
                }
                if previousLoopTime < 4.25 && loopTime >= 4.25 {
                    cue("paid")
                    cue("cinch")
                }
            }
            if loopTime < 1 - 1.0 / 60 {
                suspend(for: 1 - loopTime - 1.0 / 60)
            } else if loopTime >= 9.5 {
                suspend(for: 11 - loopTime - 1.0 / 60)
            }
        } else {
            if paidTime < 0 {
                if holding {
                    holdProgress = min(1, holdProgress + deltaSeconds / MarketBagCheckoutMotion.hold)
                    let step = Int(floor(holdProgress * 10))
                    if step != hapticTicks {
                        hapticTicks = step
                        buzz(0)
                    }
                    if holdProgress >= 1 {
                        paidTime = bagTime
                        holding = false
                        buzz(2)
                        cue("paid")
                        cue("cinch")
                    }
                } else {
                    holdProgress = max(0, holdProgress - deltaSeconds * 2.4)
                }
            }
            var sampledPose = MarketBagCheckoutMotion.Pose()
            sampledPose.bag = bagTime
            for i in 0..<5 {
                let packTime = packTimes[i]
                let unpackTime = unpackTimes[i]
                if packTime < 0 { continue }
                if unpackTime < 0 {
                    sampledPose.fly[i] = MarketBagCheckoutMotion.easeInOut(
                        MarketBagCheckoutMotion.seg(bagTime - packTime, 0, MarketBagCheckoutMotion.fly))
                    if bagTime - packTime - deltaSeconds < MarketBagCheckoutMotion.fly
                        && bagTime - packTime >= MarketBagCheckoutMotion.fly
                    {
                        buzz(1)
                        cue("drop")
                    }
                    sampledPose.land[i] =
                        bagTime - packTime >= MarketBagCheckoutMotion.fly
                        ? packTime + MarketBagCheckoutMotion.fly : -1
                } else {
                    let f =
                        1
                        - MarketBagCheckoutMotion.easeInOut(
                            MarketBagCheckoutMotion.seg(bagTime - unpackTime, 0, MarketBagCheckoutMotion.fly * 0.8)
                        )
                    sampledPose.fly[i] = f
                    sampledPose.land[i] = packTime + MarketBagCheckoutMotion.fly
                    sampledPose.leave[i] = unpackTime
                    if f <= 0 && bagTime - unpackTime > 1 {
                        packTimes[i] = -1
                        unpackTimes[i] = -1
                    }
                }
            }
            let reset = resetTime > 0 ? MarketBagCheckoutMotion.seg(bagTime - resetTime, 0, 0.35) : 0
            if resetTime > 0 && bagTime - resetTime > 0.35 {
                paidTime = -10
                holdProgress = 0
            }
            sampledPose.hold = paidTime >= 0 ? 1 - reset : holdProgress
            sampledPose.paid = paidTime >= 0 ? bagTime - paidTime : -1
            sampledPose.reset = reset
            pose = sampledPose
            let moving =
                holding || holdProgress > 0 && paidTime < 0
                || (0..<5).contains {
                    packTimes[$0] >= 0
                        && (bagTime - packTimes[$0] < 2 || unpackTimes[$0] >= 0 && bagTime - unpackTimes[$0] < 1.5)
                } || paidTime >= 0 && bagTime - paidTime < 1.2
            if !moving {
                let canResume = autoPlay && !reduceMotion && !anyPacked && holdProgress < 0.001 && paidTime < 0
                let delay =
                    canResume ? max(0.001, max(2 - (bagTime - lastTouchTime), 1.2 - (bagTime - resetTime))) : nil
                suspend(for: delay)
            }
        }
        return pose
    }
    // No display-link work while the picture is stationary. Waking advances the pure script
    // through the skipped resting interval; physical frames still use RN's capped frame delta.
    private func suspend(for delay: Double?) {
        guard !sleeping && !sleepPending else { return }
        sleepPending = true
        DispatchQueue.main.async { [weak self] in
            guard let self, self.sleepPending else { return }
            self.sleepPending = false
            self.sleeping = true
            self.sleepStart = Date()
            if let delay {
                self.wakeTimer = Timer.scheduledTimer(withTimeInterval: delay, repeats: false) { [weak self] _ in
                    self?.wake()
                }
            }
        }
    }

    /// Accounts for real seconds skipped during sleep before resuming frame-based updates.
    private func wake() {
        wakeTimer?.invalidate()
        wakeTimer = nil
        sleepPending = false
        if let start = sleepStart {
            let elapsed = max(0, Date().timeIntervalSince(start))
            bagTime += elapsed
            if automatic {
                loopTime = (loopTime + elapsed).truncatingRemainder(dividingBy: MarketBagCheckoutMotion.loop)
                pose = MarketBagCheckoutMotion.script(loopTime)
            }
        }
        sleepStart = nil
        previousDate = Date()
        sleeping = false
    }

    /// Converts scripted packing and payment times into manual bag-clock timestamps.
    private func interrupt() {
        wake()
        lastTouchTime = bagTime
        previousDate = nil
        running = true
        guard automatic else { return }
        automatic = false
        for i in 0..<5 {
            let packStart = 1 + Double(i) * 0.32
            let unpackStart = 8.55 + Double(4 - i) * 0.12
            if loopTime >= 9.5 || loopTime < packStart {
                packTimes[i] = -1
                unpackTimes[i] = -1
            } else {
                packTimes[i] = bagTime - (loopTime - packStart)
                unpackTimes[i] = loopTime >= unpackStart ? bagTime - (loopTime - unpackStart) : -1
            }
        }
        holdProgress = pose.hold
        paidTime = loopTime >= 4.25 && loopTime < 8.55 ? bagTime - (loopTime - 4.25) : -10
        resetTime = loopTime >= 8.55 && loopTime < 9.5 ? bagTime - (loopTime - 8.55) : -10
    }

    /// Starts packing an item, or returns a landed item to its row before payment.
    func toggle(_ i: Int) {
        interrupt()
        if paidTime < 0 {
            if packTimes[i] < 0 {
                packTimes[i] = bagTime
                unpackTimes[i] = -1
            } else if unpackTimes[i] < 0 && bagTime - packTimes[i] > MarketBagCheckoutMotion.fly {
                unpackTimes[i] = bagTime
            }
        }
        cue("lift")
    }

    /// Starts payment and staggers missing items into the bag; a paid basket instead begins unpacking.
    func holdStart() {
        interrupt()
        if paidTime >= 0 {
            resetTime = bagTime
            for i in 0..<5 where packTimes[i] >= 0 && unpackTimes[i] < 0 {
                unpackTimes[i] = bagTime + Double(4 - i) * 0.12
            }
            return
        }
        holding = true
        hapticTicks = 0
        var pendingItemCount = 0
        for i in 0..<5 where packTimes[i] < 0 {
            packTimes[i] = bagTime + Double(pendingItemCount) * 0.18
            unpackTimes[i] = -1
            pendingItemCount += 1
        }
    }

    /// Releases the hold and restarts the two-second idle interval.
    func holdEnd() {
        wake()
        holding = false
        lastTouchTime = bagTime
    }
    deinit { wakeTimer?.invalidate() }
}
