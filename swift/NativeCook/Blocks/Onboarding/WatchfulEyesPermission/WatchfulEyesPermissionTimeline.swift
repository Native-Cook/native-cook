import SwiftUI

/// A permission primer told by a pair of eyes. The screen has four pages; a change from one to the
/// next is a `Stage`, and every layer is a function of the stage and of the seconds since it began.
/// What a touch changes (the page, the switches) is stored; nothing is stored per frame.
enum WatchfulEyesPermissionTimeline {
    static let chatPage = 0
    static let askPage = 1
    static let switchesPage = 2
    static let donePage = 3

    /// The change from one page to another that began at `t0`, on the screen's clock.
    struct Stage: Equatable {
        var from: Int
        var to: Int
        var t0: Double
    }

    // What a tap can mean. A switch is `tapSwitch` plus its row.
    static let tapNone = -1
    static let tapChat = 0
    static let tapStart = 1
    static let tapLater = 2
    static let tapAll = 3
    static let tapPill = 4
    static let tapSwitch = 5

    /// autoPlay is back at the chat every `loop` seconds. In between it taps these, at these seconds.
    static let loop = 10.0
    static let play: [(at: Double, tap: Int)] = [(1.5, tapChat), (4.4, tapStart), (6.3, tapPill), (9.2, tapPill)]
    /// A loop starts with the chat this long on the screen.
    static let rest = 0.8
    /// After a touch autoPlay waits this long before it goes back to the chat and starts over.
    static let userHold = 3.0
    // A page takes taps once it has been arriving this long: its eyes are in place by then.
    static let readyAfter = [0.75, 1.8, 1.3, 0.6]

    // Sizes are in points of a 393 × 852 screen. The eyes hang from the safe area's top, the middle
    // of every page from the screen's middle, and the buttons stand on the safe area's floor.
    static let eyePlaces = (top: 37.0, hover: -176.0, ask: -86.0)
    static let chatTop = 89.0
    static let bubble = (side: 32.0, bottom: 118.0)
    static let row = (side: 40.0, bottom: 58.0, height: 48.0)
    static let later = (bottom: 18.0, reach: 56.0, height: 36.0)
    static let pillBox = (side: 32.0, bottom: 10.0, height: 46.0)
    static let phone = (half: 136.0, top: -171.0, radius: 44.0, notch: 46.0, depth: 16.0, fillet: 8.0)
    static let tile = (y: -66.0, size: 54.0, radius: 13.0, heart: 26.0)
    static let titleY = -16.0
    static let all = (top: 12.0, height: 30.0)
    static let list = (width: 226.0, top: 74.0, pitch: 23.5, bar: 152.0, thick: 9.0)
    static let toggle = (width: 26.0, height: 15.0, knob: 11.0)
    /// The check mark of the last page; the switches shrink into its middle.
    static let mark = (y: 44.0, disc: 80.0)
    static let labelY = 126.0
    /// Switches in the list.
    static let count = 6
    // The order the switches' page comes in: the tile, its name, the first row, the switches, the button.
    static let itemOrder = (tile: 0, title: 1, all: 2, row: 3, pill: 9)

    // The eyes: small at the top, large in the home.
    static let smallEyes = (size: 14.0, gap: 34.0, glow: 22.0)
    static let largeEyes = (size: 26.0, gap: 72.0, glow: 48.0)
    /// The soft light round the large eyes reaches this far.
    static let halo = 130.0
    static let travel = (stiffness: 120.0, damping: 17.5)
    // Down to the hover first, then a slower drift on into the home.
    static let driftAt = 0.6
    static let driftFor = 0.6
    // A blink shuts the eyes to this share of their height. Per page: the first one, and how often after.
    static let shut = 0.14
    static let blinkFor = 0.18
    static let blinks: [(first: Double, every: Double)] = [(1.25, 3.4), (2.3, 3.1), (3.6, 3.3), (1.5, 3.2)]
    // In the home they look left, then right.
    static let glance = 6.0
    static let glanceAt = 1.15
    static let glanceTurn = 0.12
    static let glanceHold = 0.25
    // Surprise at a tap on the button: they stretch, start upwards, and only then shrink.
    static let stretch = 34.0 / 26.0
    static let stretchIn = 0.15
    static let flyAfter = 0.5
    static let shrinkAfter = 0.6
    static let stretchOut = 0.25
    // Pleased with the last page: two arcs for a moment.
    static let smileAt = 0.15
    static let smileIn = 0.12
    static let smileFor = 0.4
    // A "no" for Later, a look down at a switch that flips, and a look towards a finger.
    static let shakeBy = 7.0
    static let shakeFor = 0.6
    static let shakeTurns = 2.0
    static let lookDownBy = 3.0
    static let lookDownIn = 0.2
    static let lookDownFor = 0.9
    static let lookDownOut = 0.25
    static let lookBy = 4.0
    static let lookReach = 160.0
    static let lookIn = 0.15
    static let lookOut = 0.4

    // The chat leaves upwards and comes back from below.
    static let chatOut = 0.3
    static let chatLift = 24.0
    static let chatAfter = 0.15
    static let chatIn = 0.6
    static let chatRise = 28.0
    // The ask: the message, the button and Later rise in one after the other; then the home grows round the eyes.
    static let askAt = 0.9
    static let askIn = 0.4
    static let askGap = 0.08
    static let askRise = 12.0
    static let askOut = 0.25
    static let houseAt = 1.7
    static let houseIn = 0.5
    static let houseFrom = 0.6
    // The home: a house round the eyes' place, with rain falling inside it. The rain repeats every
    // `rainFall.period` points, which takes two seconds: a loop is a whole number of them.
    static let houseBox = (width: 170.0, above: 105.0, below: 85.0, roof: 56.0, round: 10.0)
    static let rainFall = (speed: 20.0, period: 40.0, pitch: 16.0, step: 5.0, head: 1.1, taper: 0.15)
    static let rainPhase = [0.0, 22, 9, 31, 15, 36, 4, 26, 12, 33]
    static let rainStreak = [4, 3, 5, 3, 4, 5, 3, 4, 5, 3]
    // The switches: the phone's outline draws itself from the notch, then its content comes in.
    static let switchesAt = 1.0
    static let outlineFor = 0.6
    static let itemGap = 0.05
    static let itemIn = 0.3
    static let itemRise = 6.0
    static let leave = 0.3
    // A knob slides over with a small overshoot. "All" flips the rows one after the other.
    static let knobSpring = (stiffness: 300.0, damping: 22.0)
    static let knobFor = 0.25
    static let stagger = 0.09
    static let labelFade = 0.2
    /// Every switch on: the last page follows this long after the last knob has landed.
    static let doneAfter = 0.4
    // The last page: the switches shrink into the middle, the check pops and draws itself.
    static let shrink = 0.7
    static let shrinkFor = 0.3
    static let popAt = 0.15
    static let popFrom = 0.5
    static let pop = (stiffness: 200.0, damping: 13.0)
    static let popIn = 0.15
    static let drawAt = 0.3
    static let drawFor = 0.3
    static let labelAt = 0.45
    static let labelIn = 0.3
    // A tapped button gives a little.
    static let pressTo = 0.96
    static let pressIn = 0.08
    static let pressOut = 0.24
    // Reduced motion: a page fades out, the next one in, each as it looks when everything has arrived.
    static let fade = 0.3
    static let settled = 100.0

    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double {
        let u = clamp01((t - from) / (to - from))
        return u * u * (3 - 2 * u)
    }

    static func easeOut(_ t: Double) -> Double { 1 - (1 - t) * (1 - t) * (1 - t) }

    static func easeInOut(_ t: Double) -> Double { t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2 }

    /// How far a spring of mass 1 has come from 0 to 1 after `t` seconds; it overshoots a little.
    static func spring(_ t: Double, _ spring: (stiffness: Double, damping: Double)) -> Double {
        guard t > 0 else { return 0 }
        let w = spring.stiffness.squareRoot()
        let z = spring.damping / (2 * w)
        let wd = w * (1 - z * z).squareRoot()
        return 1 - exp(-z * w * t) * (cos(wd * t) + (z * w / wd) * sin(wd * t))
    }

    /// What is drawn at `now`: the stage and the seconds into it, and how much of the pages shows.
    static func seen(_ stage: Stage, _ now: Double, still: Bool) -> (from: Int, to: Int, e: Double, opacity: Double) {
        let e = now - stage.t0
        if !still { return (stage.from, stage.to, e, 1) }
        let page = e < fade ? stage.from : stage.to
        return (page, page, settled, e < fade * 2 ? abs(e - fade) / fade : 1)
    }

    /// Whether the page takes taps yet, `e` seconds into its stage.
    static func ready(_ to: Int, _ e: Double) -> Bool { e >= readyAfter[to] }

    /// How far the eyes are shut, 0 to 1.
    static func blink(_ e: Double, first: Double, every: Double) -> Double {
        if e < first { return 0 }
        let u = (e - first).truncatingRemainder(dividingBy: every) / blinkFor
        return u >= 1 ? 0 : 1 - abs(2 * u - 1)
    }

    /// The eyes: where their middle is (`dx` off the screen's), their size, the gap between their
    /// centres, how far their glow reaches, how much of the large halo shows, their height as a share
    /// of their width, and how much they are a smile. `top`, `hover` and `ask` are the heights of
    /// their three places. `still` leaves out everything they do by themselves.
    static func eyes(
        _ from: Int, _ to: Int, _ e: Double, still: Bool, top: Double, hover: Double, ask: Double
    ) -> (dx: Double, y: Double, size: Double, gap: Double, glow: Double, halo: Double, tall: Double, smile: Double) {
        var large = 0.0
        var y = top
        if to == askPage {
            large = spring(e, travel)
            y = top + (hover - top) * large + (ask - hover) * easeInOut(clamp01((e - driftAt) / driftFor))
        } else if from == askPage {
            let startled = to == switchesPage
            large = 1 - spring(e - (startled ? shrinkAfter : 0), travel)
            y = ask + (top - ask) * spring(e - (startled ? flyAfter : 0), travel)
        }
        var tall = 1.0
        var dx = 0.0
        var smile = 0.0
        if !still {
            tall = 1 - (1 - shut) * blink(e, first: blinks[to].first, every: blinks[to].every)
            if to == askPage {
                let right = glanceAt + glanceHold
                let back = right + glanceHold
                dx = glance * (2 * smooth(e, right, right + glanceTurn) - smooth(e, glanceAt, glanceAt + glanceTurn) - smooth(e, back, back + glanceTurn))
            }
            if from == askPage, to == switchesPage {
                tall *= 1 + (stretch - 1) * smooth(e, 0, stretchIn) * (1 - smooth(e, shrinkAfter, shrinkAfter + stretchOut))
            }
            if to == donePage {
                smile = smooth(e, smileAt, smileAt + smileIn) * (1 - smooth(e, smileAt + smileFor, smileAt + smileFor + smileIn))
            }
        }
        return (
            dx, y,
            smallEyes.size + (largeEyes.size - smallEyes.size) * large,
            smallEyes.gap + (largeEyes.gap - smallEyes.gap) * large,
            smallEyes.glow + (largeEyes.glow - smallEyes.glow) * large,
            clamp01(large), tall, smile
        )
    }

    /// The eyes' "no", `t` seconds after Later was tapped: how far to the side they are.
    static func shake(_ t: Double) -> Double {
        if t < 0 || t > shakeFor { return 0 }
        let u = t / shakeFor
        return shakeBy * sin(2 * .pi * shakeTurns * u) * (1 - u)
    }

    /// How far down the eyes look, `t` seconds after a switch was flipped.
    static func lookDown(_ t: Double) -> Double {
        lookDownBy * smooth(t, 0, lookDownIn) * (1 - smooth(t, lookDownFor, lookDownFor + lookDownOut))
    }

    /// How far the eyes look towards a finger that is `d` points to one side of them.
    static func toward(_ d: Double) -> Double { lookBy * max(-1, min(1, d / lookReach)) }

    /// How much of that look shows, `t` seconds after the finger came down (`held`) or left.
    static func follow(held: Bool, _ t: Double) -> Double { held ? smooth(t, 0, lookIn) : 1 - smooth(t, 0, lookOut) }

    /// The chat: how much shows, and how far under its place it is.
    static func chat(_ from: Int, _ to: Int, _ e: Double) -> (opacity: Double, shift: Double) {
        if to == chatPage {
            let u = clamp01((e - chatAfter) / chatIn)
            return (u, chatRise * (1 - easeOut(u)))
        }
        if from == chatPage {
            let u = clamp01(e / chatOut)
            return (1 - u, -chatLift * u * u)
        }
        return (0, 0)
    }

    /// A part of the ask (0 the message, 1 the button, 2 Later): how much shows, and how far under its place it is.
    static func ask(_ from: Int, _ to: Int, _ e: Double, part: Int) -> (opacity: Double, shift: Double) {
        if to == askPage {
            let u = clamp01((e - askAt - Double(part) * askGap) / askIn)
            return (u, askRise * (1 - easeOut(u)))
        }
        return (from == askPage ? 1 - clamp01(e / askOut) : 0, 0)
    }

    /// The home round the eyes: how much shows, and its scale.
    static func house(_ from: Int, _ to: Int, _ e: Double) -> (opacity: Double, scale: Double) {
        if to == askPage {
            let u = clamp01((e - houseAt) / houseIn)
            return (u, houseFrom + (1 - houseFrom) * easeOut(u))
        }
        return (from == askPage ? 1 - clamp01(e / askOut) : 0, 1)
    }

    /// How far the rain has fallen at `now`, on the screen's clock.
    static func rain(_ now: Double, still: Bool) -> Double {
        still ? 0 : (now * rainFall.speed).truncatingRemainder(dividingBy: rainFall.period)
    }

    /// The phone's outline: how much shows, and how much of it is drawn.
    static func outline(_ from: Int, _ to: Int, _ e: Double) -> (opacity: Double, drawn: Double) {
        if to == switchesPage { return (1, easeInOut(clamp01((e - switchesAt) / outlineFor))) }
        if to == donePage { return (1, 1) }
        return (from == switchesPage || from == donePage ? 1 - clamp01(e / leave) : 0, 1)
    }

    /// A thing on the switches' page, `index` in `itemOrder`: how much shows, how far under its place it is, and its scale about the check mark.
    static func item(_ from: Int, _ to: Int, _ e: Double, _ index: Int) -> (opacity: Double, rise: Double, scale: Double) {
        if to == switchesPage {
            let u = clamp01((e - switchesAt - Double(index) * itemGap) / itemIn)
            return (u, itemRise * (1 - easeOut(u)), 1)
        }
        if from != switchesPage { return (0, 0, 1) }
        let u = clamp01(e / shrinkFor)
        return (1 - u, 0, to == donePage ? 1 + (shrink - 1) * u * u : 1)
    }

    /// The button under the phone, which stays for the last page: how much shows.
    static func pill(_ from: Int, _ to: Int, _ e: Double) -> Double {
        if to == switchesPage { return clamp01((e - switchesAt - Double(itemOrder.pill) * itemGap) / itemIn) }
        if to == donePage { return 1 }
        return from == switchesPage || from == donePage ? 1 - clamp01(e / leave) : 0
    }

    /// The check mark: how much shows, its scale, how much of the stroke is drawn, and its label.
    static func check(_ from: Int, _ to: Int, _ e: Double) -> (opacity: Double, scale: Double, drawn: Double, label: Double, rise: Double) {
        if to == donePage {
            let label = clamp01((e - labelAt) / labelIn)
            return (
                smooth(e, popAt, popAt + popIn),
                popFrom + (1 - popFrom) * spring(e - popAt, pop),
                easeOut(clamp01((e - drawAt) / drawFor)),
                label,
                itemRise * (1 - easeOut(label))
            )
        }
        let left = from == donePage ? 1 - clamp01(e / leave) : 0
        return (left, 1, 1, left, 0)
    }

    /// The switches: which are on, when each was last flipped, whether all are on and since when, when the last page is due, and the last touch of any.
    struct Switches: Equatable {
        var on: [Bool]
        var at: [Double]
        var all: Bool
        var allAt: Double
        var doneAt: Double
        var flippedAt: Double
    }

    static let flipAllOn = -1
    static let flipAllOff = -2

    static func switchesOff() -> Switches {
        Switches(
            on: Array(repeating: false, count: count), at: Array(repeating: -settled, count: count),
            all: false, allAt: -settled, doneAt: .infinity, flippedAt: -settled
        )
    }

    /// The switches after a flip of row `which`, or of all of them, at `at`. A knob that is still sliding is left alone.
    static func flip(_ s: Switches, _ which: Int, at: Double) -> Switches {
        if at < (which < 0 ? s.at.max() ?? 0 : s.at[which]) + knobFor { return s }
        var on = s.on
        var times = s.at
        for i in 0..<count {
            let next = which == flipAllOn ? true : which == flipAllOff ? false : i == which ? !on[i] : on[i]
            if next != on[i] {
                on[i] = next
                times[i] = at + (which < 0 ? Double(i) * stagger : 0)
            }
        }
        let all = on.allSatisfy { $0 }
        return Switches(
            on: on, at: times, all: all,
            allAt: all == s.all ? s.allAt : at,
            doneAt: all ? (times.max() ?? at) + knobFor + doneAfter : .infinity,
            flippedAt: at
        )
    }

    /// Where a switch's knob is, 0 off to 1 on, `t` seconds after its flip.
    static func knob(on: Bool, _ t: Double, still: Bool) -> Double {
        let p = still ? (t >= 0 ? 1 : 0) : spring(t, knobSpring)
        return on ? p : 1 - p
    }

    /// How much the first row reads "Turn off all".
    static func allOn(_ s: Switches, _ now: Double) -> Double {
        let u = smooth(now - s.allAt, 0, labelFade)
        return s.all ? u : 1 - u
    }

    /// A button's scale, `t` seconds after it was tapped.
    static func pressed(_ t: Double) -> Double {
        1 - (1 - pressTo) * smooth(t, 0, pressIn) * (1 - smooth(t, pressIn, pressIn + pressOut))
    }

    /// What a tap at `point` means on a page. `floor` is the height the buttons stand on.
    static func hit(_ page: Int, _ point: CGPoint, _ size: CGSize, floor: Double) -> Int {
        let side = abs(point.x - size.width / 2)
        let y = point.y
        let middle = size.height / 2
        if page == chatPage { return tapChat }
        if page == askPage {
            if side <= size.width / 2 - row.side, y >= floor - row.bottom - row.height, y <= floor - row.bottom { return tapStart }
            if side <= later.reach, abs(y - (floor - later.bottom)) <= later.height / 2 { return tapLater }
            return tapNone
        }
        if side <= size.width / 2 - pillBox.side, y >= floor - pillBox.bottom - pillBox.height, y <= floor - pillBox.bottom { return tapPill }
        if page == switchesPage, side <= list.width / 2 {
            if y >= middle + all.top, y <= middle + all.top + all.height { return tapAll }
            let index = Int(((y - middle - list.top + list.pitch / 2) / list.pitch).rounded(.down))
            if index >= 0, index < count { return tapSwitch + index }
        }
        return tapNone
    }

    // The drawings. Each stack turns the same numbers into its own kind of path.

    /// The home's corners round the eyes' place, clockwise from the roof's tip; y grows downwards.
    static let houseCorners = [
        CGPoint(x: 0, y: -houseBox.above),
        CGPoint(x: houseBox.width / 2, y: houseBox.roof - houseBox.above),
        CGPoint(x: houseBox.width / 2, y: houseBox.below),
        CGPoint(x: -houseBox.width / 2, y: houseBox.below),
        CGPoint(x: -houseBox.width / 2, y: houseBox.roof - houseBox.above),
    ]

    /// The home's outline: every corner is cut back along both of its sides and curved.
    static let housePath: Path = {
        let n = houseCorners.count
        var path = Path()
        for i in 0..<n {
            let p = houseCorners[i]
            let before = houseCorners[(i + n - 1) % n]
            let after = houseCorners[(i + 1) % n]
            let a = houseBox.round / hypot(before.x - p.x, before.y - p.y)
            let b = houseBox.round / hypot(after.x - p.x, after.y - p.y)
            let cut = CGPoint(x: p.x + (before.x - p.x) * a, y: p.y + (before.y - p.y) * a)
            if i == 0 { path.move(to: cut) } else { path.addLine(to: cut) }
            path.addQuadCurve(to: CGPoint(x: p.x + (after.x - p.x) * b, y: p.y + (after.y - p.y) * b), control: p)
        }
        path.closeSubpath()
        return path
    }()

    /// The rain's drops round the eyes' place: streaks in columns, with a period to spare above the home.
    static let rainPath: Path = {
        var path = Path()
        let columns = rainPhase.count
        for column in 0..<columns {
            let x = (Double(column) - Double(columns - 1) / 2) * rainFall.pitch
            var y = rainPhase[column] - houseBox.above - rainFall.period
            while y < houseBox.below + rainFall.step * Double(rainStreak[column]) {
                for drop in 0..<rainStreak[column] {
                    let r = rainFall.head - Double(drop) * rainFall.taper
                    path.addEllipse(in: CGRect(x: x - r, y: y - Double(drop) * rainFall.step - r, width: r * 2, height: r * 2))
                }
                y += rainFall.period
            }
        }
        return path
    }()

    /// The right half of the phone's outline, from the middle of its notch out and down to `bottom`.
    static func outlinePath(middle: Double, top: Double, bottom: Double) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: middle, y: top + phone.depth))
        path.addLine(to: CGPoint(x: middle + phone.notch - phone.fillet, y: top + phone.depth))
        path.addQuadCurve(to: CGPoint(x: middle + phone.notch, y: top + phone.depth - phone.fillet), control: CGPoint(x: middle + phone.notch, y: top + phone.depth))
        path.addQuadCurve(to: CGPoint(x: middle + phone.notch + phone.fillet, y: top), control: CGPoint(x: middle + phone.notch, y: top))
        path.addLine(to: CGPoint(x: middle + phone.half - phone.radius, y: top))
        path.addQuadCurve(to: CGPoint(x: middle + phone.half, y: top + phone.radius), control: CGPoint(x: middle + phone.half, y: top))
        path.addLine(to: CGPoint(x: middle + phone.half, y: bottom))
        return path
    }

    /// A heart in a box from -1 to 1: where it starts, and its curves as two control points and an end.
    static let heartStart = CGPoint(x: 0, y: 0.9)
    static let heartCurves: [[Double]] = [
        [-0.25, 0.68, -1, 0.25, -1, -0.3],
        [-1, -0.68, -0.72, -0.9, -0.45, -0.9],
        [-0.25, -0.9, -0.08, -0.78, 0, -0.62],
        [0.08, -0.78, 0.25, -0.9, 0.45, -0.9],
        [0.72, -0.9, 1, -0.68, 1, -0.3],
        [1, 0.25, 0.25, 0.68, 0, 0.9],
    ]

    static let heartPath: Path = {
        var path = Path()
        path.move(to: heartStart)
        for c in heartCurves {
            path.addCurve(to: CGPoint(x: c[4], y: c[5]), control1: CGPoint(x: c[0], y: c[1]), control2: CGPoint(x: c[2], y: c[3]))
        }
        path.closeSubpath()
        return path
    }()

    /// The check mark's three corners round its middle, and its stroke.
    static let checkCorners = [CGPoint(x: -17, y: 0), CGPoint(x: -6, y: 11), CGPoint(x: 17, y: -12)]
    static let checkStroke = 2.5

    static let checkPath: Path = {
        var path = Path()
        path.addLines(checkCorners)
        return path
    }()

    /// A smiling eye, one unit wide: an arc from corner to corner over a control point, and its stroke.
    static let smileArc = [CGPoint(x: -0.5, y: 0.18), CGPoint(x: 0, y: -0.55), CGPoint(x: 0.5, y: 0.18)]
    static let smileStroke = 0.26

    static let smilePath: Path = {
        var path = Path()
        path.move(to: smileArc[0])
        path.addQuadCurve(to: smileArc[2], control: smileArc[1])
        return path
    }()
}
