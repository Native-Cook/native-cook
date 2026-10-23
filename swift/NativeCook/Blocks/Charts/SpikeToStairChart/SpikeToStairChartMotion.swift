import Foundation
import UIKit

/// One clock owns the film, transitions, queued reversals, press feedback and scrub spring.
@MainActor
final class SpikeToStairChartMotion {
    typealias Film = SpikeToStairChartFilm
    typealias Curves = SpikeToStairChartCurves
    enum Mode { case loop, transition, rest }

    let autoPlay: Bool
    private(set) var reduced = false
    private(set) var L = 0.0
    private(set) var mode = Mode.loop
    private(set) var destination = 1
    private(set) var queue: Int?
    private(set) var elapsed = 0.0
    private(set) var dragging = false
    private(set) var paused = false
    private(set) var scrubU = 0.0
    private(set) var a11y = "Day 0, 0"
    private(set) var columnTimes = Array(repeating: -100.0,count: 5)
    private(set) var pressed = -1
    private var end = 6.9
    private var idleElapsed = 0.0
    private var touchedAt = 0.0
    private var userRun = false
    private var lastDate: Date?
    private var readyElapsed = 0.0
    private var intro = 1.0
    private var scrubStart = -100.0
    private var scrubFromAlpha = 0.0
    private var released = -100.0
    private var scrubVisible = false
    private var scrubFrom = 0.0
    private var scrubTarget = 0.0
    private var scrubVelocity = 0.0
    private var thumbFrom = 1.0
    private var thumbStart = -100.0
    private var pressFrom = 0.0
    private var pressTarget = 0.0
    private var pressStart = -100.0
    private var stillSwap = -100.0
    private var stillSwapped = true
    private let selection = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let heavy = UIImpactFeedbackGenerator(style: .heavy)

    init(autoPlay: Bool) {
        self.autoPlay = autoPlay
        if !autoPlay {
            L = 4.15
            mode = .transition
            destination = 0
            thumbFrom = 0
            intro = 0
        }
    }

    func setReduced(_ value: Bool) {
        guard reduced != value else { return }
        reduced = value
        L = value || autoPlay ? 0 : 4.15
        mode = value ? .rest : autoPlay ? .loop : .transition
        destination = value || autoPlay ? 1 : 0
        thumbFrom = Double(destination)
        thumbStart = -100
        intro = value || autoPlay ? 1 : 0
        queue = nil
        dragging = false
        paused = false
        scrubVisible = false
        stillSwapped = true
        stillSwap = -100
    }

    func advance(to date: Date) {
        guard let lastDate else {
            self.lastDate = date
            return
        }
        self.lastDate = date
        step(min(0.05,max(0,date.timeIntervalSince(lastDate))))
    }

    func suspend() { lastDate = nil }

    /// Also used by the temporary verification driver; it invokes the production state machine.
    func step(_ dt: Double) {
        readyElapsed += dt
        guard readyElapsed >= 0.3 else { return }
        elapsed += dt
        if mode == .rest { idleElapsed += dt } else { idleElapsed = 0 }
        if !reduced && intro < 1 { intro = min(1,intro+dt/0.9) }
        if reduced && !stillSwapped && elapsed-stillSwap >= 0.12 {
            L = destination == 1 ? 0 : 6.9
            thumbFrom = Double(destination)
            thumbStart = -100
            stillSwapped = true
        }
        if !reduced {
            if mode == .loop {
                L = (L+dt).truncatingRemainder(dividingBy: 12)
                destination = L >= 3.6 && L < 6.95 ? 0 : 1
            } else if mode == .transition {
                let old = L
                L = min(end,L+dt*(queue == nil ? 1 : 2.5))
                if userRun && ((old < 6.35 && L >= 6.35) || (old < 9.41 && L >= 9.41)) {
                    heavy.impactOccurred()
                }
                if L >= end {
                    mode = .rest
                    userRun = false
                    touchedAt = elapsed
                    if let next = queue {
                        queue = nil
                        if next != destination { begin(next) }
                    }
                }
            }
            if autoPlay && paused && mode == .rest && !dragging && elapsed-touchedAt >= 3.5 {
                L = destination == 1 ? 0 : 6.75
                mode = .loop
                paused = false
                scrubVisible = false
            }
        }
        if !dragging && scrubVisible && !reduced {
            let t = max(0,elapsed-released)
            let a = 11.0
            let w = sqrt(240-a*a)
            let delta = scrubFrom-scrubTarget
            scrubU = scrubTarget+exp(-a*t)*(delta*cos(w*t)+(scrubVelocity+a*delta)/w*sin(w*t))
        }
    }

    private var thumb: Double {
        if mode == .loop { return Film.frame(L,still: reduced).thumb }
        if reduced { return thumbFrom }
        return Curves.mix(thumbFrom,Double(destination),Film.spring(elapsed-thumbStart,20,260))
    }

    private var pressAmount: Double {
        let t = Curves.clamp((elapsed-pressStart)/0.12)
        // The press ease (0.23, 1, 0.32, 1), solved for time.
        var lo = 0.0
        var hi = 1.0
        var p = t
        for _ in 0..<16 {
            p = (lo+hi)/2
            let q = 1-p
            if 3*q*q*p*0.23+3*q*p*p*0.32+p*p*p < t { lo = p } else { hi = p }
        }
        return Curves.mix(pressFrom,pressTarget,1-pow(1-p,3))
    }

    // Reanimated's default withTiming ease is quadratic in/out.
    private func timingEase(_ value: Double) -> Double {
        let p = Curves.clamp(value)
        return p < 0.5 ? 2*p*p : 1-pow(-2*p+2,2)/2
    }

    private var scrubOpacity: Double {
        guard scrubVisible else { return 0 }
        let reveal = Curves.mix(scrubFromAlpha,1,timingEase((elapsed-scrubStart)/0.12))
        return reveal*(dragging ? 1 : 1-timingEase((elapsed-released-0.9)/0.22))
    }

    var opacity: Double {
        let t = elapsed-stillSwap
        let fade = t < 0.12 ? 1-timingEase(t/0.12) : timingEase((t-0.12)/0.16)
        return timingEase(readyElapsed/0.3)*(reduced ? fade : 1)
    }

    func renderer() -> SpikeToStairChartRenderer {
        var f = Film.frame(L,still: reduced)
        if mode == .rest && !reduced {
            f.life = f.L+idleElapsed
            if f.d == 1 {
                let phase = f.life.truncatingRemainder(dividingBy: 4)/1.6
                f.glow = 1+0.06*sin(f.life * .pi / 2)
                f.packet = Film.smooth(phase/0.08)*(1-Film.smooth((phase-0.92)/0.08))
                f.packetStart = Curves.clamp(phase-0.06)
                f.packetEnd = Curves.clamp(phase)
            } else {
                f.glow = 1+0.1*sin(f.life*53)*sin(f.life*19)
            }
        }
        let demo = !paused && mode == .loop
        return SpikeToStairChartRenderer(f: f,still: reduced,markerU: demo ? f.markerU : scrubU,
                                        markerAlpha: demo ? f.markerAlpha : scrubOpacity,thumb: thumb,
                                        pressed: pressed,pressAmount: pressAmount,intro: intro,
                                        elapsed: elapsed,columnTimes: columnTimes)
    }

    private func begin(_ d: Int) {
        thumbFrom = thumb
        thumbStart = elapsed
        destination = d
        end = d == 1 ? 10.45 : 6.9
        L = d == 1 ? 7.05 : 3.6
        mode = .transition
        userRun = true
    }

    func touch() {
        touchedAt = elapsed
        paused = true
        if mode == .loop {
            let oldThumb = Film.frame(L,still: reduced).thumb
            if L >= 3.6 && L < 6.9 {
                destination = 0
                end = 6.9
                mode = .transition
            } else if L >= 7.05 && L < 10.45 {
                destination = 1
                end = 10.45
                mode = .transition
            } else {
                destination = L >= 6.35 && L < 7.05 ? 0 : 1
                mode = .rest
            }
            thumbFrom = oldThumb
            thumbStart = elapsed
        }
    }

    func go(_ d: Int) {
        touch()
        scrubVisible = false
        guard d != destination else { return }
        if reduced {
            destination = d
            stillSwap = elapsed
            stillSwapped = false
        } else if mode == .transition {
            queue = d
        } else {
            begin(d)
        }
    }

    func press(_ d: Int, on: Bool) {
        if on {
            light.impactOccurred()
            touch()
            pressed = d
        }
        pressFrom = pressAmount
        pressTarget = on ? 1 : 0
        pressStart = elapsed
    }

    func scrubBegan(x: Double) {
        touch()
        guard mode == .rest else { return }
        scrubFromAlpha = scrubOpacity
        dragging = true
        scrubU = coordinate(x)
        scrubStart = elapsed
        scrubVisible = true
        selection.prepare()
    }

    func scrubMoved(x: Double) {
        touchedAt = elapsed
        guard mode == .rest && dragging else { return }
        let u = coordinate(x)
        let previous = scrubU
        scrubU = u
        let cuts = destination == 1 ? Curves.risers : Curves.extrema
        for i in cuts.indices {
            if (previous < cuts[i] && u >= cuts[i]) || (previous >= cuts[i] && u < cuts[i]) {
                selection.selectionChanged()
                if destination == 1 { columnTimes[i] = elapsed }
            }
        }
    }

    func scrubEnded(velocity: Double) {
        guard dragging else { return }
        dragging = false
        released = elapsed
        touchedAt = elapsed
        scrubFrom = scrubU
        scrubTarget = destination == 1 && !reduced ? Curves.nearestTread(scrubU) : scrubU
        scrubVelocity = velocity/274
        announce(reduced ? scrubU : scrubTarget)
    }

    func adjust(_ direction: Int) {
        touch()
        scrubU = Curves.clamp(scrubU+Double(direction)*0.1,0,destination == 1 ? 0.968 : 0.957)
        scrubFrom = scrubU
        scrubTarget = scrubU
        scrubVelocity = 0
        scrubVisible = true
        scrubStart = elapsed-0.12
        released = elapsed
        announce(scrubU)
        selection.selectionChanged()
    }

    private func coordinate(_ x: Double) -> Double {
        Curves.clamp((x-64)/274,0,destination == 1 ? 0.968 : 0.957)
    }

    private func announce(_ u: Double) {
        let p = Curves.atU(destination,u)
        let day = Int((30*u/(destination == 1 ? 1 : 0.957)).rounded())
        let value = Int((Curves.clamp(p.v)*100).rounded())
        a11y = "Day \(day), \(value)"
    }
}
