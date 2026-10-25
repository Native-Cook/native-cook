import Foundation
import UIKit

/// Railway Switch Nav — the trips and the loop. A "trip" is everything one lever pull does: the
/// lever travels, two switches throw, the signals change, the route lights up, the train runs from
/// one section's platform to another's, and the page changes. Every value is a closed-form function
/// of the time since the trip began, so the loop (three trips) is exact and a finger's trip uses
/// the same code.
enum RailwaySwitchNavData {
    static let loop = 9.0
    static let sections = ["Home", "Trips", "Tickets", "Account"]
    static let count = 4

    /// A trip: start (s), lever pull (s), run (s), from, to.
    struct Trip {
        let start: Double
        let pull: Double
        let run: Double
        let from: Int
        let to: Int
    }

    static let trips = [
        Trip(start: 1.2, pull: 0.8, run: 1.4, from: 0, to: 2),
        Trip(start: 4.4, pull: 0.6, run: 1.2, from: 2, to: 1),
        Trip(start: 6.8, pull: 0.6, run: 1.0, from: 1, to: 0),
    ]
    /// A finger's trip: the same shape, a little quicker.
    static let touchPull = 0.5
    static let touchRun = 1.1
    /// The quick trip home after a touch.
    static let homePull = 0.3
    static let homeRun = 0.55
    /// The page change starts this far into the run and lasts pageFor.
    static let pageAt = 0.71
    static let pageFor = 0.6
}

func railwaySwitchNavClamp01(_ x: Double) -> Double { min(max(x, 0), 1) }

func railwaySwitchNavSeg(_ t: Double, _ a: Double, _ b: Double) -> Double {
    railwaySwitchNavClamp01((t - a) / (b - a))
}

func railwaySwitchNavEaseInOut(_ x: Double) -> Double {
    let t = railwaySwitchNavClamp01(x)
    return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

func railwaySwitchNavEaseOut(_ x: Double) -> Double {
    let t = railwaySwitchNavClamp01(x)
    return 1 - pow(1 - t, 3)
}

/// A lever's travel: a short anticipation back (4 %), then the pull with a 5 % overshoot that
/// settles into the notch.
func railwaySwitchNavLeverCurve(_ x: Double) -> Double {
    let t = railwaySwitchNavClamp01(x)
    if t < 0.15 { return -0.04 * sin(t / 0.15 * Double.pi) }
    let u = (t - 0.15) / 0.85
    let c = 1.7
    return 1 + (c + 1) * pow(u - 1, 3) + c * pow(u - 1, 2)
}

struct RailwaySwitchNavPose {
    var levers: [Double]
    /// Switch blades, -1 (set left) … 0 (closed) … 1 (set right).
    var blades: [Double]
    /// Green share of each section's signal.
    var signals: [Double]
    var from: Int
    var to: Int
    /// The route's light (0..1) and the train's progress along it (0..1).
    var route: Double
    var run: Double
    /// The page change (0 = from's page, 1 = to's page).
    var page: Double

    static func trip(_ tau: Double, pull: Double, run: Double, from: Int, to: Int, pullFrom: Double) -> RailwaySwitchNavPose {
        typealias D = RailwaySwitchNavData
        let dir = to > from ? 1.0 : -1.0
        var levers: [Double] = []
        var blades: [Double] = []
        var signals: [Double] = []
        let depart = pull
        let arrive = pull + run
        let throwIn = railwaySwitchNavEaseInOut(railwaySwitchNavSeg(tau, pull * 0.55, pull * 0.55 + 0.3))
        let release = railwaySwitchNavEaseInOut(railwaySwitchNavSeg(tau, arrive + 0.15, arrive + 0.5))
        let green = railwaySwitchNavEaseInOut(railwaySwitchNavSeg(tau, pull * 0.8, pull * 0.8 + 0.18))
        for k in 0..<D.count {
            var lever = 0.0
            var blade = 0.0
            var signal = 0.0
            if k == to {
                lever = pullFrom + (1 - pullFrom) * railwaySwitchNavLeverCurve(tau / pull)
                blade = -dir * throwIn * (1 - release)
                signal = green
            } else if k == from {
                lever = 1 - railwaySwitchNavEaseInOut(railwaySwitchNavSeg(tau, 0.1, 0.1 + pull * 0.8))
                blade = dir * throwIn * (1 - release)
                signal = 1 - green
            }
            levers.append(lever)
            blades.append(blade)
            signals.append(signal)
        }
        return RailwaySwitchNavPose(
            levers: levers,
            blades: blades,
            signals: signals,
            from: from,
            to: to,
            route: railwaySwitchNavEaseOut(railwaySwitchNavSeg(tau, pull * 0.55, pull))
                * (1 - railwaySwitchNavEaseInOut(railwaySwitchNavSeg(tau, arrive, arrive + 0.45))),
            run: railwaySwitchNavEaseInOut(railwaySwitchNavSeg(tau, depart, arrive)),
            page: railwaySwitchNavEaseInOut(railwaySwitchNavSeg(tau, depart + run * D.pageAt, depart + run * D.pageAt + D.pageFor))
        )
    }

    /// The loop's current trip and the time since it began.
    static func loopTrip(_ t: Double) -> (Int, Double) {
        let trips = RailwaySwitchNavData.trips
        var k = trips.count - 1
        var tau = t + RailwaySwitchNavData.loop - trips[k].start
        for i in trips.indices where t >= trips[i].start {
            k = i
            tau = t - trips[i].start
        }
        return (k, tau)
    }

    static func loop(_ t: Double) -> RailwaySwitchNavPose {
        let (k, tau) = loopTrip(t)
        let trip = RailwaySwitchNavData.trips[k]
        return Self.trip(tau, pull: trip.pull, run: trip.run, from: trip.from, to: trip.to, pullFrom: 0)
    }
}

/// How long a trip lasts until everything has settled.
func railwaySwitchNavTripLength(_ pull: Double, _ run: Double) -> Double {
    max(pull + run + 0.6, pull + run * RailwaySwitchNavData.pageAt + RailwaySwitchNavData.pageFor)
}

/// The loop clock, the finger's trip and its lever drag, stepped once a frame from the timeline.
@MainActor
final class RailwaySwitchNavModel {
    private typealias D = RailwaySwitchNavData

    private(set) var auto = false
    private(set) var reduced = false
    private var configured = false
    private var t = 0.0
    private var manual = true
    private var clock = 0.0
    private var resumeAt = -1.0
    // The finger's trip.
    private var tripStart = -100.0
    private var tripPull = 0.6
    private var tripRun = 1.0
    private var tripFrom = 1
    private var tripTo = 0
    private var tripPullFrom = 0.0
    // A lever under the finger.
    private var drag = -1
    private var dragAmount = 0.0
    private var dragV = 0.0
    private var dragSpringing = false
    private var dragStartY = 0.0
    private var dragStartX = 0.0
    private var dragStartAt = 0.0
    private var crossed = false
    private var arrived = true
    private var last: Date?
    private var cached = RailwaySwitchNavPose.loop(0)
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let tick = UISelectionFeedbackGenerator()

    func configure(auto: Bool, reduced: Bool) {
        guard !configured || auto != self.auto || reduced != self.reduced else { return }
        configured = true
        self.auto = auto
        self.reduced = reduced
        manual = !auto
        t = 0
        cached = RailwaySwitchNavPose.loop(0)
    }

    private func startTrip(to: Int, pull: Double, run: Double, pullFrom: Double) {
        tripFrom = tripTo
        tripStart = clock
        tripPull = pull
        tripRun = run
        tripTo = to
        tripPullFrom = pullFrom
        arrived = false
    }

    func pose(at date: Date) -> RailwaySwitchNavPose {
        if let last, date == last { return cached }
        let raw = last.map { date.timeIntervalSince($0) } ?? 0
        last = date
        let dt = min(max(raw, 0), 0.05)
        clock += dt
        if !manual {
            t = (t + dt).truncatingRemainder(dividingBy: D.loop)
            cached = RailwaySwitchNavPose.loop(t)
            return cached
        }
        if resumeAt > 0 && clock >= resumeAt && drag < 0 {
            if tripTo != 0 {
                startTrip(to: 0, pull: D.homePull, run: D.homeRun, pullFrom: 0)
                resumeAt = clock + railwaySwitchNavTripLength(D.homePull, D.homeRun)
            } else {
                resumeAt = -1
                t = 0
                manual = false
                cached = RailwaySwitchNavPose.loop(0)
                return cached
            }
        }
        // A lever let go short of the notch springs back (380 ms, damping 0.7).
        if dragSpringing {
            let omega = 4 / 0.7 / 0.38
            var left = dt
            while left > 0 {
                let h = min(left, 1.0 / 240)
                let a = -omega * omega * dragAmount - 2 * 0.7 * omega * dragV
                dragV += a * h
                dragAmount += dragV * h
                left -= h
            }
            if abs(dragAmount) < 0.001 && abs(dragV) < 0.01 {
                dragAmount = 0
                dragSpringing = false
                drag = -1
            }
        }
        let tau = reduced ? 100 : clock - tripStart
        let from = tripFrom == tripTo ? (tripTo == 0 ? 1 : 0) : tripFrom
        var p = RailwaySwitchNavPose.trip(tau, pull: tripPull, run: tripRun, from: from, to: tripTo, pullFrom: tripPullFrom)
        if drag >= 0 { p.levers[drag] = dragAmount }
        // A light haptic when a finger's train arrives.
        if !arrived && p.run > 0.995 {
            arrived = true
            if resumeAt > 0 && tripTo != 0 { light.impactOccurred() }
        }
        cached = p
        return cached
    }

    var moving: Bool { !manual || resumeAt > 0 || dragSpringing || drag >= 0 || clock - tripStart < railwaySwitchNavTripLength(tripPull, tripRun) + 0.1 }

    private func takeOver() {
        resumeAt = -1
        if !manual {
            let (k, tau) = RailwaySwitchNavPose.loopTrip(t)
            let tr = D.trips[k]
            tripStart = clock - tau
            tripPull = tr.pull
            tripRun = tr.run
            tripFrom = tr.from
            tripTo = tr.to
            tripPullFrom = 0
            arrived = tau >= tr.pull + tr.run
            manual = true
        }
    }

    func began(at p: CGPoint, xs: [Double]) {
        takeOver()
        var best = 0
        for k in 1..<D.count where abs(p.x - xs[k]) < abs(p.x - xs[best]) { best = k }
        dragStartX = p.x
        dragStartY = p.y
        dragStartAt = clock
        crossed = false
        let settled = clock - tripStart >= tripPull + tripRun
        if best != tripTo && settled {
            drag = best
            dragAmount = 0
            dragV = 0
            dragSpringing = false
        } else if !dragSpringing {
            drag = -1
        }
    }

    func moved(to p: CGPoint) {
        guard drag >= 0, !dragSpringing else { return }
        let raw = (p.y - dragStartY) / 70
        let over = max(0, raw - 1)
        let amount = max(-0.06, min(raw, 1) + 0.5 * over / (1 + over))
        dragAmount = amount
        if amount > 0.4 && !crossed {
            crossed = true
            tick.selectionChanged()
        }
    }

    func ended(at p: CGPoint) {
        if drag >= 0 && !dragSpringing {
            let tap = hypot(p.x - dragStartX, p.y - dragStartY) < 10 && clock - dragStartAt < 0.35
            if dragAmount > 0.4 || tap {
                startTrip(to: drag, pull: D.touchPull, run: D.touchRun, pullFrom: tap ? 0 : min(1, dragAmount))
                drag = -1
                light.impactOccurred()
            } else {
                dragSpringing = true
                dragV = 0
            }
        }
        if auto { resumeAt = clock + 2 + 0.01 }
    }
}
