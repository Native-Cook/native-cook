import SwiftUI

/// The numbers, the layout and the timeline of Scatter Grid Album, the same in the three stacks.
/// Every pose is a pure function of the clock, the layout and the times of the events.
enum ScatterGridAlbumMotion {
    /// One loop of the autoPlay, in seconds. Every drift period divides it, so the loop has no jump.
    static let loop = 8.0
    // The autoPlay's timeline, in seconds from the start of a loop: open and drifting, folded into the
    // card, the heart tapped on and off, unfolded, open again.
    static let foldTap = 1.2
    static let heartOnTap = 3.0
    static let heartOffTap = 4.6
    static let unfoldTap = 5.6
    /// Seconds after the last touch before the autoPlay starts again (unfolding first if folded).
    static let userHold = 4.0
    /// The autoPlay waits for the photos, but never longer than this after the block appears.
    static let preloadWait = 3.0

    // MARK: Layout (points; the reference screen is 393 × 852, everything follows the safe area and width)

    /// A photo lying open, before its tilt: 3:4, a little taller than wide, as in the design.
    static let photoWidth = 132.0
    static let photoHeight = 176.0
    /// Corner radius of a photo open and in its cell (continuous corners); the frame morphs between.
    static let photoRadiusOpen = 18.0
    static let photoRadiusCell = 15.0
    /// The band the open photos lie in: from this far under the safe top to this far over the safe bottom.
    static let scatterTop = 70.0
    static let scatterBottom = 10.0

    /// Where a photo lies open: its centre as shares of the screen's width and of the band's height,
    /// its tilt in degrees, and its drift.
    struct Slot {
        let x: Double
        let y: Double
        let tilt: Double
        /// Drift: periods in seconds (each divides `loop`) and where each sine starts (radians).
        let periodX: Double
        let periodY: Double
        let periodTilt: Double
        let phase: Double
    }

    /// The eight photos in grid order (row by row, skipping the centre). Each lies open on the side of
    /// the screen its cell is on, as in the design: the second one (top middle cell) is the one tucked in
    /// the middle, behind its neighbours; the seventh (bottom middle) lies right of centre, low.
    static let slots: [Slot] = [
        Slot(x: 0.24, y: 0.135, tilt: -4, periodX: 8, periodY: 4, periodTilt: 8, phase: 0.0),
        Slot(x: 0.50, y: 0.315, tilt: -1.5, periodX: 4, periodY: 8, periodTilt: 8, phase: 2.1),
        Slot(x: 0.69, y: 0.155, tilt: 4, periodX: 8, periodY: 4, periodTilt: 4, phase: 4.0),
        Slot(x: 0.235, y: 0.48, tilt: 3.5, periodX: 4, periodY: 8, periodTilt: 8, phase: 1.3),
        Slot(x: 0.78, y: 0.46, tilt: -3.5, periodX: 8, periodY: 4, periodTilt: 8, phase: 5.2),
        Slot(x: 0.26, y: 0.775, tilt: -5.5, periodX: 8, periodY: 8, periodTilt: 4, phase: 3.3),
        Slot(x: 0.61, y: 0.635, tilt: -5, periodX: 4, periodY: 4, periodTilt: 8, phase: 0.7),
        Slot(x: 0.715, y: 0.86, tilt: 4, periodX: 8, periodY: 4, periodTilt: 8, phase: 4.6),
    ]
    /// Back to front: the tucked one first, then the rest in order (so the sky lies over the dog, etc.).
    static let drawOrder = [1, 0, 2, 3, 4, 5, 6, 7]
    /// Drift amplitudes: points each way and degrees each way.
    static let driftX = 2.0
    static let driftY = 3.0
    static let driftTilt = 0.6

    /// The folded card: a square this far in from each side, its centre at this share of the screen height;
    /// its corners round like the outer cells' outer corners, so they line up.
    static let cardSide = 20.0
    static let cardCenter = 0.46
    static let cardRadius = 15.0
    /// The 3×3 grid on the card: no outer padding; the columns are equal, the middle row taller (as in the
    /// design, so the name has room), the top and bottom rows equal.
    static let gapX = 16.0
    static let gapY = 12.0
    static let middleRowShare = 0.43

    /// The heading open: the year's top this far under the safe top, the name's line box `nameGap` under it.
    static let yearTop = 10.0
    static let yearSize = 11.0
    static let yearLine = 14.0
    static let nameSize = 52.0
    static let nameLine = 60.0
    static let nameTracking = -1.0
    static let nameGap = 7.0
    /// The heading folded: the name at most this size (smaller when it must, to fit the middle cell with
    /// `nameInset` each side), its centre this far from the cell's centre, the year's centre that far.
    static let nameFoldedSize = 32.0
    static let nameInset = 4.0
    static let nameFoldedDY = -6.0
    static let yearFoldedSize = 10.0
    static let yearFoldedDY = 15.0

    /// The icon row: centres this far under the card, `iconSpacing` (share of the card side) apart,
    /// centred on the card.
    static let iconDrop = 46.0
    static let iconSpacing = 0.215
    static let iconSize = 22.0
    static let iconStroke = 1.6
    /// How far an icon rises as it comes in.
    static let iconRise = 8.0

    // MARK: Motion

    struct Spring {
        let mass: Double
        let stiffness: Double
        let damping: Double
    }

    /// A photo flying between its open place and its cell: lands with a slight overshoot.
    static let photoSpring = Spring(mass: 1, stiffness: 210, damping: 24)
    /// The gradient's frame and the heading: settles without a visible bounce.
    static let cardSpring = Spring(mass: 1, stiffness: 180, damping: 26)
    /// A pressed icon coming back up.
    static let release = Spring(mass: 1, stiffness: 380, damping: 20)

    /// Seconds between photos leaving, by distance from the card's centre (nearest first when folding,
    /// farthest first when unfolding).
    static let stagger = 0.018
    /// The year over the name fades out over this share of the heading's travel; the one under it
    /// in the card fades in over the last share.
    static let yearOutShare = 0.25
    static let yearInShare = 0.3
    /// The gradient's edges go soft while it moves (as in the design's frames): up to this blur radius,
    /// at the middle of its travel.
    static let feather = 8.0

    // Icons: each comes in over `iconIn`, `iconStep` after the one before, the first `iconDelay` after the
    // fold; on an unfold they all leave over `iconOut`, and the photos and the card start back
    // `unfoldDelay` after the tap (the design's card is already moving while its icons vanish).
    static let iconDelay = 0.35
    static let iconStep = 0.04
    static let iconIn = 0.25
    static let iconOut = 0.12
    static let unfoldDelay = 0.05

    // The heart: a pop (an impulse on a spring: scale 1 → 1.35 → a small dip → 1), a ring of six dots,
    // and the fill (in fast, out over `heartOff`).
    static let popSpring = Spring(mass: 1, stiffness: 400, damping: 12)
    static let popPeak = 0.35
    static let heartIn = 0.06
    static let heartOff = 0.2
    static let burstDots = 6
    static let burstFor = 0.4
    /// Dots travel from this radius (just inside the heart's edge) out to the other.
    static let burstFrom = 6.0
    static let burstTo = 20.0
    static let burstDot = 3.5

    /// Presses on the icons: down over `pressIn` to `pressScale`, back on `release`.
    static let pressIn = 0.1
    static let pressScale = 0.85

    /// Reduced motion: fold and unfold are a cross-fade of this length; nothing drifts, pops or bursts.
    static let fade = 0.25

    /// Seconds from a fold or an unfold until everything is still.
    static let settle = unfoldDelay + stagger * 7 + 1.0

    static let easeOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))

    static let never = -1e6

    // MARK: Events

    /// Every channel's progress (0 open, 1 folded) when the last fold or unfold began.
    struct Snapshot: Equatable {
        var photos: [Double]
        var card: Double
        var icons: [Double]

        static let open = Snapshot(photos: Array(repeating: 0, count: 8), card: 0, icons: Array(repeating: 0, count: 4))
        static let folded = Snapshot(photos: Array(repeating: 1, count: 8), card: 1, icons: Array(repeating: 1, count: 4))
    }

    /// Everything that happened, as times on the clock in seconds.
    struct Events: Equatable {
        var folded = false
        var at = never
        /// Where every channel was at `at`: a fold or an unfold starts from what is on screen.
        var from = Snapshot.open
        var heartOn = false
        var heartAt = never
        var downAt = Array(repeating: never, count: 4)
        var upAt = Array(repeating: never, count: 4)
    }

    /// The time of an event at `offset` in a loop: this loop's if it has happened, else the last loop's.
    private static func last(_ base: Double, _ tau: Double, _ offset: Double) -> Double {
        tau >= offset ? base + offset : base + offset - loop
    }

    /// The events of the autoPlay at `now` for a loop that started at `start`. Each fold or unfold
    /// starts from rest, since the one before it has long settled.
    static func autoEvents(now: Double, start: Double) -> Events {
        let tau = (now - start).truncatingRemainder(dividingBy: loop)
        let base = now - tau
        let fold = last(base, tau, foldTap)
        let unfold = last(base, tau, unfoldTap)
        let on = last(base, tau, heartOnTap)
        let off = last(base, tau, heartOffTap)
        var e = Events()
        e.folded = fold > unfold
        e.at = max(fold, unfold)
        e.from = e.folded ? .open : .folded
        e.heartOn = on > off
        e.heartAt = max(on, off)
        return e
    }

    // MARK: Curves

    static func clamp01(_ value: Double) -> Double { min(1, max(0, value)) }

    static func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    /// A spring let go from 0 towards 1 at rest, `t` seconds later (the closed form, so every stack agrees).
    static func spring(_ t: Double, _ s: Spring) -> Double {
        guard t > 0 else { return 0 }
        let w0 = (s.stiffness / s.mass).squareRoot()
        let zeta = s.damping / (2 * (s.stiffness * s.mass).squareRoot())
        if zeta >= 1 { return 1 - exp(-w0 * t) * (1 + w0 * t) }
        let wd = w0 * (1 - zeta * zeta).squareRoot()
        return 1 - exp(-zeta * w0 * t) * (cos(wd * t) + (zeta * w0 / wd) * sin(wd * t))
    }

    /// A spring struck at rest, `t` seconds later: up to 1 at its peak, back through a small dip to 0.
    static func impulse(_ t: Double, _ s: Spring) -> Double {
        guard t > 0 else { return 0 }
        let w0 = (s.stiffness / s.mass).squareRoot()
        let zeta = s.damping / (2 * (s.stiffness * s.mass).squareRoot())
        let wd = w0 * (1 - zeta * zeta).squareRoot()
        let peakT = atan(wd / (zeta * w0)) / wd
        let peak = exp(-zeta * w0 * peakT) * sin(wd * peakT)
        return exp(-zeta * w0 * t) * sin(wd * t) / peak
    }

    // MARK: Progress (0 open, 1 folded; a photo overshoots 1 a little as it lands)

    /// Where a fold or an unfold takes a channel that started at `from`, through `curve` (0 → 1).
    private static func toward(_ e: Events, from: Double, _ curve: Double) -> Double {
        lerp(from, e.folded ? 1 : 0, curve)
    }

    /// Reduced motion: the cross-fade between the two layouts.
    static func fadeAmount(_ e: Events, now: Double) -> Double {
        toward(e, from: e.from.card, clamp01((now - e.at) / fade))
    }

    /// Photo `i`, whose turn (by distance from the card's centre, nearest first) is `rank`.
    static func photo(_ i: Int, rank: Int, _ e: Events, now: Double) -> Double {
        let delay = e.folded ? stagger * Double(rank) : unfoldDelay + stagger * Double(slots.count - 1 - rank)
        return toward(e, from: e.from.photos[i], spring(now - e.at - delay, photoSpring))
    }

    /// The gradient's frame and the heading.
    static func card(_ e: Events, now: Double) -> Double {
        toward(e, from: e.from.card, spring(now - e.at - (e.folded ? 0 : unfoldDelay), cardSpring))
    }

    /// Icon `k`: its opacity, and how far it has risen.
    static func icon(_ k: Int, _ e: Events, now: Double) -> Double {
        let t = now - e.at
        if e.folded {
            return toward(e, from: e.from.icons[k], easeOut.value(at: clamp01((t - iconDelay - iconStep * Double(k)) / iconIn)))
        }
        return toward(e, from: e.from.icons[k], clamp01(t / iconOut))
    }

    /// What is on screen now, for the next fold or unfold to start from.
    static func snapshot(_ e: Events, ranks: [Int], now: Double, still: Bool) -> Snapshot {
        if still {
            let x = fadeAmount(e, now: now)
            return Snapshot(photos: Array(repeating: x, count: slots.count), card: x, icons: Array(repeating: x, count: 4))
        }
        return Snapshot(
            photos: slots.indices.map { photo($0, rank: ranks[$0], e, now: now) },
            card: card(e, now: now),
            icons: (0..<4).map { icon($0, e, now: now) }
        )
    }

    /// The order photos leave in: by distance of their open place from the card's centre, nearest first.
    static func ranks(open: [CGPoint], card: CGPoint) -> [Int] {
        let order = open.indices.sorted { hypot(open[$0].x - card.x, open[$0].y - card.y) < hypot(open[$1].x - card.x, open[$1].y - card.y) }
        var ranks = Array(repeating: 0, count: open.count)
        for (rank, i) in order.enumerated() { ranks[i] = rank }
        return ranks
    }

    // MARK: Drift

    /// A photo's drift at `t` seconds of the drift clock: x and y in points, tilt in degrees.
    static func drift(_ slot: Slot, t: Double) -> (x: Double, y: Double, tilt: Double) {
        (
            driftX * sin(2 * .pi * t / slot.periodX + slot.phase),
            driftY * sin(2 * .pi * t / slot.periodY + slot.phase * 1.7),
            driftTilt * sin(2 * .pi * t / slot.periodTilt + slot.phase * 0.6)
        )
    }

    // MARK: Layout and poses

    /// Where everything goes on a screen of this size and safe area.
    struct Layout {
        let width: Double
        let height: Double
        /// The photos' centres lying open (before drift), in grid order.
        let open: [CGPoint]
        let card: CGRect
        /// The eight cells, row by row, skipping the middle one.
        let cells: [CGRect]
        let middle: CGRect
        /// The centre of the name's line box open and folded, and its scale folded.
        let nameOpen: CGPoint
        let nameFolded: CGPoint
        let nameFoldedScale: Double
        /// The centre of the year under the name in the card.
        let yearFolded: CGPoint
        let icons: [CGPoint]
        /// Each photo's turn when folding (nearest the card's centre first).
        let ranks: [Int]
    }

    /// `nameWidth` is the name's width set at `nameSize` with `nameTracking`.
    static func layout(width: Double, height: Double, top: Double, bottom: Double, nameWidth: Double) -> Layout {
        let bandTop = top + scatterTop
        let band = height - bottom - scatterBottom - bandTop
        let open = slots.map { CGPoint(x: $0.x * width, y: bandTop + $0.y * band) }

        let side = width - cardSide * 2
        let card = CGRect(x: cardSide, y: height * cardCenter - side / 2, width: side, height: side)
        let column = (side - gapX * 2) / 3
        let rows = side - gapY * 2
        let edgeRow = rows * (1 - middleRowShare) / 2
        let heights = [edgeRow, rows * middleRowShare, edgeRow]
        var grid: [CGRect] = []
        var y = card.minY
        for r in 0..<3 {
            for c in 0..<3 {
                grid.append(CGRect(x: card.minX + Double(c) * (column + gapX), y: y, width: column, height: heights[r]))
            }
            y += heights[r] + gapY
        }
        let middle = grid[4]
        let cells = grid.enumerated().filter { $0.offset != 4 }.map(\.element)

        let nameOpen = CGPoint(x: width / 2, y: top + yearTop + yearLine + nameGap + nameLine / 2)
        let scale = min(nameFoldedSize / nameSize, (column - nameInset * 2) / max(1, nameWidth))
        let icons = (0..<4).map { k in
            CGPoint(x: card.midX + (Double(k) - 1.5) * iconSpacing * side, y: card.maxY + iconDrop)
        }
        return Layout(
            width: width,
            height: height,
            open: open,
            card: card,
            cells: cells,
            middle: middle,
            nameOpen: nameOpen,
            nameFolded: CGPoint(x: middle.midX, y: middle.midY + nameFoldedDY),
            nameFoldedScale: scale,
            yearFolded: CGPoint(x: middle.midX, y: middle.midY + yearFoldedDY),
            icons: icons,
            ranks: ranks(open: open, card: CGPoint(x: card.midX, y: card.midY))
        )
    }

    struct PhotoPose {
        var center: CGPoint
        var width: Double
        var height: Double
        var radius: Double
        var tilt: Double
        /// 1 open, 0 in its cell: scales the photo's shadow.
        var lift: Double
    }

    /// Photo `i` at progress `u` (0 open, 1 in its cell), drifting by `drift` while open.
    static func photoPose(_ i: Int, u: Double, layout: Layout, driftT: Double?) -> PhotoPose {
        let slot = slots[i]
        let cell = layout.cells[i]
        let away = 1 - clamp01(u)
        var o = layout.open[i]
        var tilt = slot.tilt
        if let t = driftT {
            let d = drift(slot, t: t)
            o.x += d.x * away
            o.y += d.y * away
            tilt += d.tilt * away
        }
        return PhotoPose(
            center: CGPoint(x: lerp(o.x, cell.midX, u), y: lerp(o.y, cell.midY, u)),
            width: max(1, lerp(photoWidth, cell.width, u)),
            height: max(1, lerp(photoHeight, cell.height, u)),
            radius: lerp(photoRadiusOpen, photoRadiusCell, clamp01(u)),
            tilt: lerp(tilt, 0, u),
            lift: away
        )
    }

    struct CardPose {
        var rect: CGRect
        var radius: Double
        var feather: Double
        /// 0 open (no shadow: it fills the screen), 1 folded.
        var shadow: Double
    }

    /// The gradient's frame at progress `c`: the whole screen open, the card folded.
    static func cardPose(c: Double, layout: Layout) -> CardPose {
        let card = layout.card
        let rect = CGRect(
            x: lerp(0, card.minX, c),
            y: lerp(0, card.minY, c),
            width: lerp(layout.width, card.width, c),
            height: lerp(layout.height, card.height, c)
        )
        let k = clamp01(c)
        return CardPose(rect: rect, radius: cardRadius * k, feather: feather * 4 * k * (1 - k), shadow: k)
    }

    struct HeadingPose {
        var name: CGPoint
        var scale: Double
        /// The year over the name (open) and under it (folded).
        var yearTop: CGPoint
        var yearTopOpacity: Double
        var yearCell: CGPoint
        var yearCellOpacity: Double
    }

    /// The name and the year at progress `c`.
    static func headingPose(c: Double, layout: Layout) -> HeadingPose {
        let name = CGPoint(x: lerp(layout.nameOpen.x, layout.nameFolded.x, c), y: lerp(layout.nameOpen.y, layout.nameFolded.y, c))
        let scale = lerp(1, layout.nameFoldedScale, c)
        // The top year rides above the name, the cell's year below it, each at its own place at its end.
        let above = (nameLine / 2 + nameGap + yearLine / 2) * scale
        let below = layout.yearFolded.y - layout.nameFolded.y
        return HeadingPose(
            name: name,
            scale: scale,
            yearTop: CGPoint(x: name.x, y: name.y - above),
            yearTopOpacity: 1 - clamp01(c / yearOutShare),
            yearCell: CGPoint(x: name.x, y: name.y + below),
            yearCellOpacity: clamp01((c - (1 - yearInShare)) / yearInShare)
        )
    }

    // MARK: Heart

    /// How filled the heart is.
    static func heartFill(_ e: Events, now: Double) -> Double {
        let t = now - e.heartAt
        return e.heartOn ? clamp01(t / heartIn) : 1 - clamp01(t / heartOff)
    }

    /// The heart's scale: the pop after it is filled.
    static func heartScale(_ e: Events, now: Double, still: Bool) -> Double {
        guard e.heartOn, !still else { return 1 }
        return 1 + popPeak * impulse(now - e.heartAt, popSpring)
    }

    /// The burst's progress (0 → 1 over `burstFor`), or nil when there is none.
    static func burst(_ e: Events, now: Double, still: Bool) -> Double? {
        let t = now - e.heartAt
        guard e.heartOn, !still, t >= 0, t < burstFor else { return nil }
        return t / burstFor
    }

    /// An icon's scale while pressed and as it springs back.
    static func pressScale(_ k: Int, _ e: Events, now: Double) -> Double {
        let down = e.downAt[k]
        let up = e.upAt[k]
        let press = down > up
            ? easeOut.value(at: clamp01((now - down) / pressIn))
            : easeOut.value(at: clamp01((up - down) / pressIn)) * (1 - spring(now - up, release))
        return 1 - (1 - pressScale) * press
    }

    static func anyPressed(_ e: Events) -> Bool {
        zip(e.downAt, e.upAt).contains { $0 > $1 }
    }
}
