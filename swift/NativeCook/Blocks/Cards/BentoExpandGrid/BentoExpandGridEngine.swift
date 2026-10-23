import SwiftUI
import UIKit

/// The numbers of the block, the same as the React Native version's.
enum BentoExpandGridMetrics {
    // The grid: margins and gaps in pt, the corner radius of a tile, the title block above it.
    static let margin = 16.0
    static let gap = 10.0
    static let tileRadius = 22.0
    static let titleBlock = 92.0
    // Tiles narrower than this set their caption smaller, on two lines.
    static let narrow = 130.0
    // Photos: the grid and the page's header share one copy; the pair inside the page is smaller.
    static let headerPx = 1200
    static let pairPx = 600

    // The page: its header takes `headerShare` of the screen; the sheet below overlaps it by
    // `overlap` and the header slides up at `parallax` of the scroll, so the sheet glides over it.
    static let headerShare = 0.48
    static let overlap = 28.0
    static let parallax = 0.45
    // A stat or quote face is drawn at its page size and scaled down into its tile, so its type
    // stays sharp: up to `faceGrow` times the tile's size, never wider than the screen.
    static let faceGrow = 1.75

    // Opening: the tile's frame grows to the screen on a spring, its corners closing to 0. The
    // other tiles are pushed away from it by `push` pt, shrink by `shrink` and fade, nearest
    // first, each starting `stagger` later. The page's sections rise `revealRise` pt as they fade
    // in, one by one.
    static let openSpring = (duration: 0.8, damping: 0.88)
    static let closeSpring = (duration: 0.68, damping: 0.9)
    static let push = 44.0
    static let shrink = 0.1
    static let stagger = 0.07
    static let revealFrom = 0.42
    static let revealStep = 0.06
    static let revealSpan = 0.3
    static let revealRise = 18.0
    // The sheet's paper comes in quickly, early in the flight, so it never sits half-seen over a
    // dark tile.
    static let sheetFrom = 0.22
    static let sheetTo = 0.42
    // The header fills the frame until `headerGiveFrom` of the way, and is down to its own height
    // by `headerGiveTo`, the sheet sliding up under it; so a closing page never shows an empty card.
    static let headerGiveFrom = 0.2
    static let headerGiveTo = 0.8
    static let fadeTime = 0.22

    // Closing by hand: the page follows a drag down, shrinking by up to `dismissShrink` over
    // `dismissSpan` of the screen and rounding its corners to `dismissRadius`, the grid peeking
    // back in behind it. Past `dismissDistance` or `dismissSpeed` it closes into its tile.
    static let dismissSpan = 0.5
    static let dismissShrink = 0.34
    static let dismissRadius = 36.0
    static let dismissDistance = 110.0
    static let dismissSpeed = 800.0
    static let followX = 0.6
    static let followY = 0.75
    static let peek = 0.8
    static let peekSpan = 0.25
    static let dragBack = (duration: 0.42, damping: 0.82)
    // The drag down starts after this much travel, and never once the finger has gone this far sideways.
    static let dismissSlop = 10.0
    static let dismissFailX = 24.0
    // Press feedback on a tile.
    static let pressScale = 0.97
    static let pressTime = 0.12

    // autoPlay loop (seconds): the big photo opens, its page scrolls down and back, it is dragged
    // down and closes into its tile; the stat opens, scrolls, closes from its button; a rest.
    static let loop = 14.0
    static let openA = 1.0
    static let scrollA = 2.3
    static let backA = 3.9
    static let dragA = 5.0
    static let closeA = 5.5
    static let openB = 7.1
    static let scrollB = 8.5
    static let backB = 10.0
    static let closeB = 11.0
    static let noteA = 0
    static let noteB = 1
    static let scrollDepth = 240.0
    static let scrollTime = 1.1
    static let backTime = 0.8
    static let dragPull = 150.0
    // After a touch the loop waits this long; longer while a page is open, so it can be read.
    static let userHold = 3.0
    static let pageHold = 7.0

    static let easeInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.77, y: 0), endControlPoint: UnitPoint(x: 0.175, y: 1))
    // UIScrollView's normal deceleration, per millisecond.
    static let deceleration = 0.998
}

func bentoExpandGridClamp(_ x: Double) -> Double { min(1, max(0, x)) }

func bentoExpandGridSmooth(_ from: Double, _ to: Double, _ x: Double) -> Double {
    let u = bentoExpandGridClamp((x - from) / (to - from))
    return u * u * (3 - 2 * u)
}

func bentoExpandGridMix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

/// The page's frame: between its tile (p = 0) and the screen (p = 1), shrunk by a drag down.
struct BentoExpandGridFrame {
    var rect: CGRect
    var radius: Double
    /// How much the drag down has shrunk the page, 1 when it has not.
    var scale: Double
}

/// For one open tile, how another tile is pushed: a unit vector away from it and how late it starts.
struct BentoExpandGridPush {
    var x: Double
    var y: Double
    var delay: Double
}

/// Downloads a photo once and keeps it decoded.
enum BentoExpandGridPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    static func load(_ address: String) async -> UIImage? {
        if let hit = cache.object(forKey: address as NSString) { return hit }
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        let ready = await image.byPreparingForDisplay() ?? image
        cache.setObject(ready, forKey: address as NSString)
        return ready
    }
}

/// The grid's and the page's state, stepped once a frame like the RN frame callback. Nothing in
/// it is observed: the timeline redraws every frame anyway.
@MainActor
final class BentoExpandGridEngine {
    private typealias M = BentoExpandGridMetrics

    private(set) var notes: [BentoExpandGridNote] = []
    private(set) var width = 0.0
    private(set) var height = 0.0
    private(set) var top = 0.0
    private(set) var bottom = 0.0
    private(set) var rects: [CGRect] = []
    private(set) var plan: [[BentoExpandGridPush]] = []
    // The page: 0 in its tile, 1 full screen; which tile is open (nil for none).
    private(set) var open = BentoExpandGridDriven(0)
    private(set) var active: Int?
    private(set) var closing = false
    private(set) var dragX = BentoExpandGridDriven(0)
    private(set) var dragY = BentoExpandGridDriven(0)
    private(set) var dragging = false
    // The page's scroll, its fling speed and how far it can go.
    private(set) var scrollY = 0.0
    private var scrollSpeed = 0.0
    private var maxScroll = 0.0
    private var autoScroll = BentoExpandGridDriven(0)
    private(set) var pressed: [Int: BentoExpandGridDriven] = [:]
    private(set) var elapsed = 0.0
    private(set) var images: [String: (image: UIImage, at: Double)] = [:]
    // autoPlay.
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var auto: Bool?
    private(set) var reduced = false
    private var last: Date?
    // The touch on the page: undecided, scrolling, dragging the page down, or neither.
    private enum PageTouch { case none, scroll, dismiss }
    private var pageTouch = PageTouch.none
    private var dismissFailed = false
    private var lastTranslation = 0.0

    private let lightImpact = UIImpactFeedbackGenerator(style: .light)

    func configure(notes: [BentoExpandGridNote], autoPlay: Bool, reducedMotion: Bool) {
        if self.notes.isEmpty { self.notes = notes }
        reduced = reducedMotion
        let next = autoPlay && !reducedMotion
        if auto != next {
            auto = next
            looping = next
            clock = 0
            hold = 0
        }
    }

    func layout(width: Double, height: Double, top: Double, bottom: Double) {
        if width == self.width && height == self.height && top == self.top && bottom == self.bottom { return }
        self.width = width
        self.height = height
        self.top = top
        self.bottom = bottom
        // The tiles on the grid, from the space between the title block and the bottom edge.
        let gridTop = top + M.titleBlock
        let gridBottom = height - bottom - M.margin
        let columns = Double(BentoExpandGridNote.columns)
        let rows = Double(BentoExpandGridNote.gridRows)
        let colW = (width - M.margin * 2 - M.gap * (columns - 1)) / columns
        let rowH = (gridBottom - gridTop - M.gap * (rows - 1)) / rows
        rects = notes.map { n in
            CGRect(
                x: M.margin + Double(n.col) * (colW + M.gap),
                y: gridTop + Double(n.row) * (rowH + M.gap),
                width: Double(n.cols) * colW + Double(n.cols - 1) * M.gap,
                height: Double(n.rows) * rowH + Double(n.rows - 1) * M.gap
            )
        }
        // For every pair of tiles: which way the second is pushed when the first opens and how
        // late it starts, nearest first.
        plan = rects.map { from in
            let away = rects.map { to -> (x: Double, y: Double, d: Double) in
                let dx = to.midX - from.midX
                let dy = to.midY - from.midY
                let d = hypot(dx, dy) == 0 ? 1 : hypot(dx, dy)
                return (dx / d, dy / d, d)
            }
            var delay = Array(repeating: 0.0, count: rects.count)
            for (rank, item) in away.enumerated().sorted(by: { $0.element.d < $1.element.d }).enumerated() {
                delay[item.offset] = Double(max(0, rank - 1)) * M.stagger
            }
            return away.enumerated().map { BentoExpandGridPush(x: $1.x, y: $1.y, delay: delay[$0]) }
        }
    }

    var headerHeight: Double { (height * M.headerShare).rounded() }

    // MARK: Photos

    func photoArrived(_ image: UIImage, for address: String) {
        if images[address] == nil { images[address] = (image, elapsed) }
    }

    func image(_ id: String, width: Int) -> (image: UIImage, at: Double)? {
        images[BentoExpandGridNote.photoURL(id, width: width)]
    }

    // MARK: Frame

    func step(to date: Date) {
        defer { last = date }
        guard let last else { return }
        let dt = min(date.timeIntervalSince(last), 0.064)
        if dt <= 0 || width == 0 { return }
        elapsed += dt
        loop(dt)

        if open.step(dt) && closing && open.value == 0 { finishClose() }
        _ = dragX.step(dt)
        _ = dragY.step(dt)
        for key in pressed.keys { _ = pressed[key]?.step(dt) }
        // The loop scrolls the open page itself.
        if autoScroll.isMoving {
            _ = autoScroll.step(dt)
            if hold <= 0 && active != nil {
                scrollY = min(max(autoScroll.value, 0), maxScroll)
                scrollSpeed = 0
            }
        }
        // A scroll let go glides on and slows the way a scroll view does.
        if pageTouch != .scroll && scrollSpeed != 0 {
            scrollY += scrollSpeed * dt
            scrollSpeed *= pow(M.deceleration, dt * 1000)
            if scrollY <= 0 || scrollY >= maxScroll {
                scrollY = min(max(scrollY, 0), maxScroll)
                scrollSpeed = 0
            }
            if abs(scrollSpeed) < 4 { scrollSpeed = 0 }
        }
    }

    private func loop(_ dt: Double) {
        if hold > 0 {
            if !dragging { hold -= dt }
            if hold <= 0 {
                // The loop starts over from the grid: a page left open closes first.
                clock = 0
                if looping { closeNote(user: false, velocity: 0) }
            }
            return
        }
        if !looping { return }
        let before = clock
        let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
        clock = t
        func passed(_ at: Double) -> Bool {
            (before < at && t >= at) || (t < before && (at > before || at <= t))
        }
        let curve = M.easeInOut
        if passed(M.openA) { openNote(M.noteA, user: false) }
        if passed(M.openB) { openNote(M.noteB, user: false) }
        if passed(M.scrollA) || passed(M.scrollB) {
            autoScroll.timing(to: M.scrollDepth, duration: M.scrollTime) { curve.value(at: $0) }
        }
        if passed(M.backA) || passed(M.backB) {
            autoScroll.timing(to: 0, duration: M.backTime) { curve.value(at: $0) }
        }
        if passed(M.dragA) && active != nil {
            dragY.timing(to: M.dragPull, duration: 0.46) { curve.value(at: $0) }
            dragX.timing(to: -14, duration: 0.46) { curve.value(at: $0) }
        }
        if passed(M.closeA) { closeNote(user: false, velocity: 1.2) }
        if passed(M.closeB) { closeNote(user: false, velocity: 0) }
    }

    // MARK: Page

    func openNote(_ index: Int, user: Bool) {
        if active != nil || index < 0 || index >= notes.count { return }
        active = index
        closing = false
        open.set(0)
        dragX.set(0)
        dragY.set(0)
        scrollY = 0
        scrollSpeed = 0
        autoScroll.set(0)
        if user {
            hold = M.pageHold
            lightImpact.impactOccurred()
        }
        if reduced {
            open.timing(to: 1, duration: M.fadeTime)
        } else {
            open.spring(to: 1, duration: M.openSpring.duration, dampingRatio: M.openSpring.damping)
        }
    }

    private func closeNote(user: Bool, velocity: Double) {
        if active == nil || closing { return }
        closing = true
        if reduced {
            open.timing(to: 0, duration: M.fadeTime)
        } else {
            open.spring(to: 0, duration: M.closeSpring.duration, dampingRatio: M.closeSpring.damping, velocity: -velocity)
        }
        if user { lightImpact.impactOccurred() }
    }

    private func finishClose() {
        active = nil
        closing = false
        dragX.set(0)
        dragY.set(0)
    }

    private func touched() {
        hold = active != nil ? M.pageHold : M.userHold
    }

    func close() {
        touched()
        closeNote(user: true, velocity: 0)
    }

    func tapTile(_ index: Int) {
        touched()
        openNote(index, user: true)
    }

    func press(_ index: Int, down: Bool) {
        var value = pressed[index] ?? BentoExpandGridDriven(1)
        value.timing(to: down ? M.pressScale : 1, duration: down ? M.pressTime : M.pressTime * 1.6)
        pressed[index] = value
    }

    func pressScale(_ index: Int) -> Double { pressed[index]?.value ?? 1 }

    func sheetMeasured(_ sheetHeight: Double) {
        // The scroll view is the screen tall, its content the sheet below the header's overlap.
        maxScroll = max(0, headerHeight - M.overlap + sheetHeight - height)
        scrollY = min(scrollY, maxScroll)
    }

    // One drag on the page both scrolls it and, from the top, pulls it down: the scroll follows
    // the finger; once the finger has gone 10 pt down with the page at its top, the page follows.
    func pageDragChanged(translation: CGSize) {
        guard active != nil, !closing else { return }
        if pageTouch == .none {
            pageTouch = .scroll
            dismissFailed = false
            lastTranslation = 0
            scrollSpeed = 0
            touched()
        }
        if abs(translation.width) > M.dismissFailX && pageTouch == .scroll { dismissFailed = true }
        if pageTouch == .scroll {
            if !dismissFailed && translation.height >= M.dismissSlop && scrollY <= 2 {
                pageTouch = .dismiss
                dragging = true
                touched()
            } else {
                scrollY = min(max(scrollY - (translation.height - lastTranslation), 0), maxScroll)
                lastTranslation = translation.height
                return
            }
        }
        if reduced { return }
        dragX.set(translation.width)
        dragY.set(max(0, translation.height))
    }

    func pageDragEnded(translation: CGSize, velocity raw: CGSize) {
        defer {
            pageTouch = .none
            dragging = false
        }
        guard active != nil, !closing else { return }
        // A non-finite speed would carry NaN into the page's frame (a CALayer geometry exception).
        let velocity = CGSize(width: raw.width.isFinite ? raw.width : 0, height: raw.height.isFinite ? raw.height : 0)
        if pageTouch == .scroll {
            scrollSpeed = -velocity.height
            return
        }
        if pageTouch != .dismiss { return }
        if translation.height > M.dismissDistance || velocity.height > M.dismissSpeed {
            closeNote(user: true, velocity: max(0, velocity.height) / (height * 0.6))
        } else {
            dragX.spring(to: 0, duration: M.dragBack.duration, dampingRatio: M.dragBack.damping, velocity: velocity.width)
            dragY.spring(to: 0, duration: M.dragBack.duration, dampingRatio: M.dragBack.damping, velocity: velocity.height)
        }
    }

    /// The system took the touch away (no onEnded): a page pulled down goes back, and the next
    /// touch starts afresh.
    func pageDragCancelled() {
        guard pageTouch != .none else { return }
        if pageTouch == .dismiss && active != nil && !closing {
            dragX.spring(to: 0, duration: M.dragBack.duration, dampingRatio: M.dragBack.damping)
            dragY.spring(to: 0, duration: M.dragBack.duration, dampingRatio: M.dragBack.damping)
        }
        pageTouch = .none
        dragging = false
    }

    // MARK: Geometry

    func pull() -> Double { bentoExpandGridClamp(dragY.value / (height * M.dismissSpan)) }

    /// How far the grid has come back in behind a page dragged down, 0…1.
    func peek() -> Double { bentoExpandGridClamp(dragY.value / (height * M.peekSpan)) }

    /// The page's frame for the tile at `index`.
    func frame(for index: Int) -> BentoExpandGridFrame {
        let full = CGRect(x: 0, y: 0, width: width, height: height)
        let tile = reduced ? full : rects[index]
        let p = min(1, open.value)
        let pull = pull()
        let s = 1 - M.dismissShrink * pull
        let fw = width * s
        let fh = height * s
        let fx = (width - fw) / 2 + dragX.value * M.followX
        let fy = max(0, dragY.value) * M.followY
        let fr = M.dismissRadius * bentoExpandGridClamp(pull * 4)
        return BentoExpandGridFrame(
            rect: CGRect(
                x: bentoExpandGridMix(tile.minX, fx, p),
                y: bentoExpandGridMix(tile.minY, fy, p),
                width: max(1, bentoExpandGridMix(tile.width, fw, p)),
                height: max(1, bentoExpandGridMix(tile.height, fh, p))
            ),
            radius: max(0, bentoExpandGridMix(M.tileRadius, fr, p)),
            scale: s
        )
    }

    /// How big a stat or quote face is drawn, relative to its tile.
    func grow(_ index: Int) -> Double { min(M.faceGrow, width / rects[index].width) }

    /// A tile's push while another is open: opacity, offset and scale.
    func tileState(_ index: Int) -> (alpha: Double, x: Double, y: Double, scale: Double) {
        guard let k = active else { return (1, 0, 0, 1) }
        if k == index { return (0, 0, 0, 1) }
        let move = plan[k][index]
        let p = bentoExpandGridClamp(open.value) * (1 - M.peek * peek())
        let maxDelay = M.stagger * Double(plan.count - 2)
        let q = bentoExpandGridSmooth(move.delay, move.delay + 1 - maxDelay, p)
        let push = reduced ? 0 : M.push * q
        return (1 - q, move.x * push, move.y * push, reduced ? 1 : 1 - M.shrink * q)
    }

    /// The title block fades as a page covers it, and back as the page is dragged down.
    var titleAlpha: Double {
        active == nil ? 1 : 1 - bentoExpandGridClamp(open.value) * (1 - M.peek * peek())
    }
}
