import Foundation

/// Every timing value of the living heart, as pure functions (motion.ts).
///
/// The heart is drawn by a Metal shader (`PulseHeartCardHeart.metal`). Each frame the engine computes
/// one small set of numbers from "time since the last lub" and hands them to the shader as uniforms.
/// Units: seconds, and "ref pt" (points of the 402 pt wide pin screen).
enum PulseHeartCardMotion {
    /// Autoplay loop: six beats at 62 BPM, then the heart rests (about 20 % of the loop: 0-0.14 s and 6.52-8 s).
    static let loop = 8.0
    static let restBPM = 62.0
    static let beat = 60.0 / restBPM
    /// First R-peak of the loop. The ECG pen enters the left edge half a beat before it.
    static let firstLub = 0.62
    static let lubs = 6
    /// A finger lifted for this long hands the heart back to the autoplay loop (from rest).
    static let idleReset = 2.0
    /// The chart selection fades out over the last 200 ms of that wait.
    static let cursorFade = 0.2
    /// "Never happened" for a time-since-beat.
    static let never = 99.0

    /// The final fixture is the resting 62 BPM reading.
    static let samples: [Int] = [
        82, 100, 100, 110, 100, 88, 65, 90, 60, 62,
        60, 43, 43, 120, 100, 82, 84, 60, 84, 62,
    ]

    struct BarBounds {
        let x: Double
        let width: Double
        let y0: Double
        let y1: Double
        /// The pin draws a range as one or more capsules with small gaps.
        let segments: [(Double, Double)]
    }

    /// Measured on the pin (ref pt), in the same fixture order as `samples`.
    private static let segments: [[(Double, Double)]] = [
        [(679, 696.3)],
        [(670.3, 687)],
        [(670.7, 693)],
        [(675, 679), (681.7, 693.3)],
        [(671, 678.3), (682, 695)],
        [(678.3, 696.7)],
        [(685, 697.7)],
        [(677.3, 697.3)],
        [(688.3, 698.3)],
        [(686.3, 702)],
        [(688, 702.7)],
        [(696.7, 702.3)],
        [(697.3, 702.7)],
        [(660.7, 675), (680.7, 697.3)],
        [(672.3, 691)],
        [(682.3, 694.3)],
        [(681, 699)],
        [(688, 699.3)],
        [(679.3, 682.7), (684.3, 688.3), (692.7, 698.7)],
        [(675.7, 702.3)],
    ]

    static let barBounds: [BarBounds] = segments.enumerated().map { index, segments in
        BarBounds(x: 34 + Double(index) * 7.25, width: 3.8, y0: segments[0].0, y1: segments[segments.count - 1].1, segments: segments)
    }

    // MARK: The beat: a damped spring kicked at each lub (S1) and, weaker, at each dub (S2).

    /// Ringing period of the heart's flesh (s): ~3.3 Hz reads as a soft, heavy muscle.
    private static let springPeriod = 0.3
    /// Exponential decay time (s): one visible rebound, a faint second one.
    private static let springDecay = 0.1
    private static let omega = 2 * Double.pi / springPeriod
    /// Normalises the first peak of the impulse response to 1 (peak at ~0.054 s).
    private static let peak: Double = {
        let t = atan(omega * springDecay) / omega
        return exp(-t / springDecay) * sin(omega * t)
    }()

    enum Shape {
        /// Dub follows the lub by this long, or by this share of the period at fast rates.
        static let dubDelay = 0.3
        static let dubShare = 0.36
        static let dubWeight = 0.45
        /// Whole-body expansion at the lub's peak (+5.2 %).
        static let body = 0.052
        /// Pre-beat gather: the heart draws in by 1.4 % over 90 ms before the lub.
        static let anticipation = 0.014
        static let anticipationLead = 0.09
        /// The lobes are heavier than the body: they arrive 35 ms late and overshoot.
        static let lobe = 0.024
        static let lobeLag = 0.035
        /// Width bulge after the peak (positive = wider and shorter), 70 ms late.
        static let squash = 0.022
        static let squashLag = 0.07
        /// The heart hops up by this many ref pt at the peak.
        static let lift = -2.4
        /// Halo ring pushed outwards by this many ref pt, 20 ms after the lub.
        static let push = 7.0
        static let pushLag = 0.02
        /// Wet gloss: rises in 25 ms, fades over 320 ms.
        static let glossRise = 0.025
        static let glossFade = 0.32
        /// The highlight slides this far (light-direction units) towards the centre and back.
        static let light = 0.28
        static let lightRise = 0.06
        static let lightFade = 0.4
        /// The reading flashes white on the R-peak and fades over 200 ms.
        static let flashFade = 0.2
    }

    /// Normalised impulse response of the heart's flesh, 0 before the kick.
    static func kick(_ tau: Double) -> Double {
        if tau < 0 || tau > 1.2 { return 0 }
        return exp(-tau / springDecay) * sin(omega * tau) / peak
    }

    private static func rise(_ tau: Double, _ riseTime: Double, _ fadeTime: Double) -> Double {
        if tau < 0 || tau > 3 { return 0 }
        return (1 - exp(-tau / riseTime)) * exp(-tau / fadeTime)
    }

    private static func gather(_ untilNext: Double) -> Double {
        let lead = Shape.anticipationLead
        if untilNext <= 0 || untilNext >= lead { return 0 }
        let s = sin(Double.pi * (lead - untilNext) / lead)
        return s * s
    }

    struct Beat {
        var body = 0.0
        var lobe = 0.0
        var squash = 0.0
        var lift = 0.0
        var gloss = 0.0
        var light = 0.0
        var push = 0.0
        var flash = 0.0
    }

    /// Everything the heart does at one instant.
    /// tau0/tau1: seconds since the last and the previous lub (`never` if none);
    /// untilNext: seconds until the next lub (`never` if none is coming); period: seconds per beat.
    static func beatAt(tau0: Double, tau1: Double, untilNext: Double, period: Double) -> Beat {
        let b = Shape.self
        let dub = min(b.dubDelay, b.dubShare * period)
        func both(_ lag: Double) -> Double { kick(tau0 - lag) + kick(tau1 - lag) }
        let lub = both(0)
        let dubs = both(dub) * b.dubWeight
        let gloss = min(1,
            rise(tau0, b.glossRise, b.glossFade) + rise(tau1, b.glossRise, b.glossFade)
            + b.dubWeight * (rise(tau0 - dub, b.glossRise, b.glossFade) + rise(tau1 - dub, b.glossRise, b.glossFade)))
        return Beat(
            body: b.body * (lub + dubs) - b.anticipation * gather(untilNext),
            lobe: b.lobe * (both(b.lobeLag) + b.dubWeight * both(dub + b.lobeLag)),
            squash: b.squash * (both(b.squashLag) + b.dubWeight * both(dub + b.squashLag)),
            lift: b.lift * (lub + dubs),
            gloss: gloss,
            light: b.light * min(1, rise(tau0, b.lightRise, b.lightFade) + rise(tau1, b.lightRise, b.lightFade)),
            push: b.push * max(0, both(b.pushLag) + b.dubWeight * both(dub + b.pushLag)),
            flash: tau0 >= 0 && tau0 < 1.5 ? exp(-tau0 / b.flashFade) : 0
        )
    }

    // MARK: Autoplay: a pure function of loop time.

    struct Pulse {
        /// Continuous beat count: an integer at every R-peak. Drives the ECG pen.
        var phi: Double
        /// Trace is drawn for beat counts in [traceStart, traceEnd]; it fades after traceEnd.
        var traceStart: Double
        var traceEnd: Double
        var tau0: Double
        var tau1: Double
        var untilNext: Double
        var period: Double
    }

    /// The ECG pen enters at the left edge (half a beat before the R-peak at the centre).
    static let traceLead = 0.5
    /// Old trace fades with age (per beat) like phosphor; a gap opens ahead of the pen.
    static let traceFadeOut = 0.6

    static func autoplayAt(_ time: Double) -> Pulse {
        let t = (time.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop)
        let phi = (t - firstLub) / beat
        let last = min(Int(phi.rounded(.down)), lubs - 1)
        let tau0 = last >= 0 ? t - (firstLub + Double(last) * beat) : never
        let tau1 = last >= 1 ? tau0 + beat : never
        let next = last + 1
        let untilNext = next <= lubs - 1 ? firstLub + Double(next) * beat - t : never
        return Pulse(phi: phi, traceStart: -traceLead, traceEnd: Double(lubs - 1) + traceLead,
                     tau0: tau0, tau1: tau1, untilNext: untilNext, period: beat)
    }

    /// Below these the beat is invisible (body/lobe/squash as a share of size, lift/push in ref pt, the
    /// rest 0-1), so the autoplay frame is the pin's and the shader need not redraw: the loop's rest stretch.
    private enum Idle {
        static let shape = 0.0002
        static let lift = 0.02
        static let push = 0.03
        static let gloss = 0.02
        static let light = 0.01
        static let flash = 0.01
        static let ripple = 0.75
    }

    /// True when nothing on the heart moves: the beat has died away, its ripples are gone and the ECG trace has faded.
    static func autoplayIdle(_ beat: Beat, tau0: Double, phi: Double, traceStart: Double, traceEnd: Double) -> Bool {
        if phi >= traceStart && phi <= traceEnd + traceFadeOut { return false }
        if tau0 < Idle.ripple { return false }
        return abs(beat.body) < Idle.shape && abs(beat.lobe) < Idle.shape && abs(beat.squash) < Idle.shape
            && abs(beat.lift) < Idle.lift && abs(beat.push) < Idle.push
            && beat.gloss < Idle.gloss && beat.light < Idle.light && beat.flash < Idle.flash
    }

    /// Loop time at which the idle stretch ends: the ECG pen enters at phi = -traceLead (a few ms inside, so
    /// a timer that fires on time finds the trace window open).
    static let wakeLoopTime = firstLub - traceLead * beat + 0.004

    // MARK: Chart scrub helpers.

    static func clampIndex(_ index: Double) -> Int {
        min(max(Int(index.rounded(.toNearestOrAwayFromZero)), 0), samples.count - 1)
    }

    static func sampleTime(_ index: Int) -> String {
        let hour = index / 2 == 0 ? 12 : index / 2
        return String(format: "%d:%02d AM", hour, (index % 2) * 30)
    }

    /// Press feedback on the Heart chip.
    enum Press {
        static let scale = 0.97
        static let inSeconds = 0.12
        static let outSeconds = 0.1
    }
}
