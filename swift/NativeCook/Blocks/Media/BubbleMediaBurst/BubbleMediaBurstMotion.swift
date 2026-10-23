import SwiftUI

/// The numbers and the timeline of Bubble Media Burst, the same in the three stacks.
enum BubbleMediaBurstMotion {
    /// One loop of the autoPlay, in seconds.
    static let loop = 7.0
    // The autoPlay's timeline, in seconds from the start of a loop: "+" goes down and comes up (which
    // opens the bubbles), one photo is picked and let go, then "+" is pressed again (which closes them).
    static let openPress = 0.8
    static let pressFor = 0.12
    static let pickAt = 3.2
    static let unpickAt = 4.2
    static let closePress = 4.8
    /// The bubble the autoPlay picks: the second one.
    static let autoPick = 1
    /// Seconds after the last touch before the autoPlay takes over again.
    static let userHold = 3.0

    struct Spring {
        let mass: Double
        let stiffness: Double
        let damping: Double
    }

    // Where a bubble is along its path (a little past the end, then back), and how big it is
    // (from a fifth of its size to about 1.09 and back to 1).
    static let travel = Spring(mass: 1, stiffness: 170, damping: 18)
    static let grow = Spring(mass: 1, stiffness: 220, damping: 17)
    /// The "+" turning into an "×" and back.
    static let turn = Spring(mass: 1, stiffness: 260, damping: 22)
    /// A picked bubble swelling and its check popping in.
    static let pop = Spring(mass: 1, stiffness: 320, damping: 17)
    /// The "+" coming back up after a press.
    static let release = Spring(mass: 1, stiffness: 380, damping: 20)

    /// A bubble is born at this share of its size, behind the "+".
    static let bornScale = 0.2
    /// Seconds a new bubble takes to become opaque, so it never pops in.
    static let bornFade = 0.08
    /// Seconds after its birth over which a bubble eases into its float.
    static let floatIn = 0.8
    /// How far a path bows to the left of the straight line, as a share of its length.
    static let curve = 0.18
    /// Seconds each bubble takes to fall back into the "+".
    static let fall = 0.35
    /// Seconds the "+" takes to go down when pressed, and how far it goes.
    static let pressIn = 0.1
    static let pressScale = 0.92
    /// The give of the "+" when a bubble lands in it: down, then back up.
    static let pulseIn = 0.07
    static let pulseOut = 0.28
    /// A picked bubble's size, and how long it takes to shrink back when let go.
    static let pickScale = 1.06
    static let unpickFor = 0.22
    static let turnAngle = 45.0
    /// Reduced motion: every change is a fade of this length, in place.
    static let fade = 0.2

    static let easeOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))
    /// A falling bubble starts slowly and drops into the "+" fast.
    static let slide = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.55, y: 0.085), endControlPoint: UnitPoint(x: 0.68, y: 0.53))
    /// It keeps most of its size until it is well on its way.
    static let shrink = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.5, y: 0), endControlPoint: UnitPoint(x: 0.75, y: 0.6))

    /// Where a bubble rests and how it floats, at cluster scale 1.
    struct Slot {
        /// Its centre at rest, from the centre of the "+": x to the right, y up, in points.
        let x: Double
        let y: Double
        let size: Double
        /// Seconds after the "+" is released that it is born, and that it starts to fall.
        let openDelay: Double
        let closeDelay: Double
        /// Its float: points each way and seconds a period on each axis, and where it starts (radians).
        let floatX: Double
        let floatY: Double
        let periodX: Double
        let periodY: Double
        let phase: Double
        /// How far the "+" gives when it lands in it.
        let dip: Double
    }

    // Born big one first, then the mushroom, the flower, the burst and the tiny one; they fall back in
    // the order big, tiny, mushroom, flower, burst.
    static let slots: [Slot] = [
        Slot(x: 53, y: 126, size: 165, openDelay: 0, closeDelay: 0, floatX: 1, floatY: 3, periodX: 3.9, periodY: 3.4, phase: 0, dip: 0.08),
        Slot(x: -81, y: 157, size: 95, openDelay: 0.12, closeDelay: 0.45, floatX: 1.5, floatY: 3, periodX: 3.3, periodY: 2.8, phase: 1.9, dip: 0.06),
        Slot(x: -22, y: 232, size: 76, openDelay: 0.06, closeDelay: 0.3, floatX: 1.2, floatY: 3, periodX: 3.6, periodY: 3.1, phase: 4, dip: 0.05),
        Slot(x: -60, y: 66, size: 76, openDelay: 0.18, closeDelay: 0.6, floatX: 1.5, floatY: 2.5, periodX: 3, periodY: 2.6, phase: 2.7, dip: 0.05),
        Slot(x: 36, y: 230, size: 25, openDelay: 0.24, closeDelay: 0.15, floatX: 4, floatY: 5, periodX: 3.4, periodY: 3, phase: 5.2, dip: 0.03),
    ]

    /// The cluster's width and height above the centre of the "+" at scale 1, float included.
    static let clusterWidth = 272.0
    static let clusterHeight = 274.0
    /// Seconds from the "+" closing the cluster until everything is still.
    static let settle = 0.6 + fall + pulseIn + pulseOut

    static let never = -1e6

    /// Everything that happened, as times on the clock in seconds.
    struct Events: Equatable {
        var openAt = never
        var closeAt = never
        var downAt = never
        var upAt = never
        var pickOn: [Bool]
        var pickAt: [Double]

        init(count: Int) {
            pickOn = Array(repeating: false, count: count)
            pickAt = Array(repeating: never, count: count)
        }

        var isOpen: Bool { openAt > closeAt }
    }

    /// A slot's place on screen: its centre at rest, the bend of its path, and the centre of the "+".
    struct Place {
        let rest: CGPoint
        let control: CGPoint
        let button: CGPoint
    }

    struct Pose {
        var x: Double
        var y: Double
        var scale: Double
        var opacity: Double
    }

    static func clamp01(_ value: Double) -> Double { min(1, max(0, value)) }

    /// A spring let go from 0 towards 1 at rest, `t` seconds later (the closed form, so every stack agrees).
    static func spring(_ t: Double, _ s: Spring) -> Double {
        guard t > 0 else { return 0 }
        let w0 = (s.stiffness / s.mass).squareRoot()
        let zeta = s.damping / (2 * (s.stiffness * s.mass).squareRoot())
        if zeta >= 1 { return 1 - exp(-w0 * t) * (1 + w0 * t) }
        let wd = w0 * (1 - zeta * zeta).squareRoot()
        return 1 - exp(-zeta * w0 * t) * (cos(wd * t) + (zeta * w0 / wd) * sin(wd * t))
    }

    private static func smoothstep(_ x: Double) -> Double {
        let t = clamp01(x)
        return t * t * (3 - 2 * t)
    }

    /// The time of an event at `offset` in a loop: this loop's if it has happened, else the last loop's.
    private static func last(_ base: Double, _ tau: Double, _ offset: Double) -> Double {
        tau >= offset ? base + offset : base + offset - loop
    }

    /// The events of the autoPlay at `now` for a loop that started at `start`.
    static func autoEvents(now: Double, start: Double, count: Int) -> Events {
        let tau = (now - start).truncatingRemainder(dividingBy: loop)
        let base = now - tau
        let opened = last(base, tau, openPress + pressFor)
        let closed = last(base, tau, closePress + pressFor)
        let picked = last(base, tau, pickAt)
        let unpicked = last(base, tau, unpickAt)
        var e = Events(count: count)
        if autoPick < count {
            e.pickOn[autoPick] = picked > unpicked
            e.pickAt[autoPick] = max(picked, unpicked)
        }
        e.openAt = opened
        e.closeAt = closed
        e.downAt = max(last(base, tau, openPress), last(base, tau, closePress))
        e.upAt = max(opened, closed)
        return e
    }

    /// Where a bubble is, how big and how opaque, at `now`.
    static func pose(_ slot: Slot, _ place: Place, now: Double, openAt: Double, closeAt: Double, still: Bool) -> Pose {
        let hidden = Pose(x: place.button.x, y: place.button.y, scale: 0, opacity: 0)
        let closing = closeAt >= openAt
        if closing && now - closeAt >= (still ? fade : slot.closeDelay + fall) { return hidden }
        let since = now - openAt
        var p = Pose(x: place.rest.x, y: place.rest.y, scale: 1, opacity: 1)
        if still {
            p.opacity = clamp01(since / fade)
        } else {
            let t = since - slot.openDelay
            guard t > 0 else { return hidden }
            // Along a quadratic curve from the "+" to its place, a little past it and back.
            let u = spring(t, travel)
            let v = 1 - u
            p.x = v * v * place.button.x + 2 * v * u * place.control.x + u * u * place.rest.x
            p.y = v * v * place.button.y + 2 * v * u * place.control.y + u * u * place.rest.y
            let float = smoothstep(t / floatIn)
            p.x += float * slot.floatX * sin(2 * .pi * since / slot.periodX + slot.phase)
            p.y += float * slot.floatY * sin(2 * .pi * since / slot.periodY + slot.phase)
            p.scale = bornScale + (1 - bornScale) * spring(t, grow)
            p.opacity = clamp01(t / bornFade)
        }
        if closing {
            if still {
                p.opacity *= 1 - clamp01((now - closeAt) / fade)
            } else {
                let c = clamp01((now - closeAt - slot.closeDelay) / fall)
                let k = slide.value(at: c)
                p.x += (place.button.x - p.x) * k
                p.y += (place.button.y - p.y) * k
                p.scale *= 1 - shrink.value(at: c)
            }
        }
        return p
    }

    /// How picked a bubble looks: 0 to 1, with an overshoot when it is picked.
    static func pickAmount(on: Bool, at: Double, now: Double, still: Bool) -> Double {
        let t = now - at
        if on { return still ? clamp01(t / fade) : spring(t, pop) }
        return 1 - (still ? clamp01(t / fade) : easeOut.value(at: clamp01(t / unpickFor)))
    }

    /// The size of the "+": down while pressed, springing back, and giving as each bubble lands.
    static func buttonScale(_ e: Events, now: Double, still: Bool) -> Double {
        let press = e.downAt > e.upAt
            ? easeOut.value(at: clamp01((now - e.downAt) / pressIn))
            : easeOut.value(at: clamp01((e.upAt - e.downAt) / pressIn)) * (1 - spring(now - e.upAt, release))
        var dip = 0.0
        if !still && e.closeAt >= e.openAt {
            for slot in slots {
                let d = now - e.closeAt - slot.closeDelay - fall
                guard d >= 0, d <= pulseIn + pulseOut else { continue }
                let amount = d < pulseIn ? easeOut.value(at: d / pulseIn) : 1 - shrink.value(at: (d - pulseIn) / pulseOut)
                dip = max(dip, amount * slot.dip)
            }
        }
        return (1 - (1 - pressScale) * press) * (1 - dip)
    }

    /// The turn of the "+", in degrees: 45 while open.
    static func plusTurn(_ e: Events, now: Double, still: Bool) -> Double {
        if e.isOpen { return still ? turnAngle : turnAngle * spring(now - e.openAt, turn) }
        return still ? 0 : turnAngle * (1 - spring(now - e.closeAt, turn))
    }
}
