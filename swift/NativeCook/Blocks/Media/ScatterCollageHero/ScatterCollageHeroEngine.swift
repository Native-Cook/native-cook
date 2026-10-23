import SwiftUI
import UIKit

/// The numbers of the block, the same as the React Native version's.
enum ScatterCollageHeroMetrics {
    // Photo widths in pixels: a tile is drawn at most ~200 pt wide, the hero fills the screen.
    static let tilePx = 480
    static let heroPx = 1000

    // The collage is a field of photos flying slowly towards the viewer. Each tile lives on a
    // cycle: it is born small near the vanishing point, grows by `growth` times as it comes closer,
    // moving out along its own direction, and leaves past the edges, while a new one is born behind
    // it. Growth is exponential in time, so the zoom feels even rather than speeding up.
    static let growth = 5.0
    // Where the field opens from, as shares of the screen: a little below the middle.
    static let vanishX = 0.5
    static let vanishY = 0.52
    // How far a tile's centre is from the vanishing point at the end of its cycle, as shares of the
    // screen's width and height (times a per-tile reach of 0.84…1.16).
    static let spreadX = 0.92
    static let spreadY = 0.6
    // A tile's longer side at the end of its cycle, in pt on a 402 pt wide screen (times 0.82…1.18).
    static let baseSize = 236.0
    // A tile stays hidden for the first share of its cycle, fades in until `fadeIn`, and fades out
    // over the last share, so the far, tiny ones never clutter the middle.
    static let fadeFrom = 0.14
    static let fadeIn = 0.36
    static let fadeOut = 0.86
    // Corner radius of a tile: a floor plus a share of its shorter side.
    static let radiusMin = 3.0
    static let radiusShare = 0.05
    // Each photo fades in over this many seconds once it has loaded.
    static let photoFade = 0.4
    // Successive tiles are a golden angle apart, so the field never lines up.
    static let golden = 2.399963

    // Opening a tile: it flies out of the collage and grows to fill the screen on a spring, its
    // corners closing to 0, while the other tiles fade out. Closing reverses it into the tile's slot.
    static let openSpring = (duration: 0.7, damping: 0.86)
    static let closeSpring = (duration: 0.64, damping: 0.9)
    static let fadeTime = 0.24
    // The hero's copy rises in, line by line, over the last part of the flight.
    static let revealFrom = 0.66
    static let revealStep = 0.04
    static let revealSpan = 0.14
    static let revealRise = 22.0
    // While a hero is dragged down, the collage fades back in behind it, up to `peek`, fully so
    // once the finger has pulled `peekSpan` of the screen's height.
    static let peek = 0.7
    static let peekSpan = 0.3
    // The full-size photo fades over the small one over this many seconds once it arrives.
    static let sharpFade = 0.3
    // Full-size photos kept in memory.
    static let sharpKeep = 10
    // At most this many heroes open at once: the visible tiles, read top to bottom.
    static let maxPages = 8

    // Paging between heroes: one screen per hero, the photo lagging its frame by `parallax` of the
    // distance and the copy by `copyParallax`, so the next hero pushes the last one out in layers.
    static let parallax = 0.42
    static let copyParallax = 0.3
    static let pageSpring = (duration: 0.52, damping: 0.88)
    static let pageThrow = 0.22
    static let rubber = 0.28

    // Dragging a hero down shrinks it towards a card that follows the finger; past
    // `dismissDistance` or `dismissSpeed` it closes into its slot, otherwise it springs back.
    static let dismissSpan = 0.55
    static let dismissShrink = 0.3
    static let dismissDistance = 120.0
    static let dismissSpeed = 750.0
    static let dismissRadius = 44.0
    static let dragBack = (duration: 0.42, damping: 0.82)

    // The chips under the collage: the active one's pill slides, the other photos dim.
    static let chipSpring = (duration: 0.42, damping: 0.8)
    static let filterTime = 0.36
    static let dimmed = 0.16

    // autoPlay loop (seconds). The collage flies; a tile opens; the hero moves to the next one; it
    // is dragged down and closes into that tile's slot; the collage flies on. The flight slows to a
    // stop while a hero is open, so its slot waits for it. Each tile completes exactly one cycle
    // per loop, so the last frame of the loop meets the first.
    static let loop = 15.0
    static let openAt = 3.2
    static let pageAt = 6.3
    static let dragAt = 8.6
    static let dismissAt = 9.0
    static let stopAt = 3.2
    static let stopTime = 0.6
    static let goAt = 9.15
    static let goTime = 0.85
    static let dragPull = 150.0
    // After a touch the loop waits this long before it starts over; longer while a hero is open.
    static let userHold = 3.5
    static let heroHold = 7.0

    static let easeInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.77, y: 0), endControlPoint: UnitPoint(x: 0.175, y: 1))

    // The hero's bottom stack, from the bottom edge up: thumbnails, the Start row, the copy.
    static let thumbHeight = 84.0
    static let thumbGap = 10.0
    static let side = 22.0
    static let rowHeight = 48.0
    static func stackBottom(_ bottom: Double) -> Double { bottom + 12 }
    static func rowBottom(_ bottom: Double) -> Double { stackBottom(bottom) + thumbHeight + 24 }
    static func copyBottom(_ bottom: Double) -> Double { rowBottom(bottom) + rowHeight + 22 }

    /// Seconds of flight the loop has run by time t: the flight stops for the hero and starts again.
    static func flown(_ t: Double) -> Double {
        t - rampArea(t, at: stopAt, span: stopTime) + rampArea(t, at: goAt, span: goTime)
    }

    /// One full cycle of a tile takes the flight time of one loop.
    static let cycle = flown(loop)

    /// Integral of a smoothstep that rises from 0 at `at` to 1 over `span` seconds, up to time t.
    private static func rampArea(_ t: Double, at: Double, span: Double) -> Double {
        if t <= at { return 0 }
        if t >= at + span { return span / 2 + (t - at - span) }
        let u = (t - at) / span
        return span * (u * u * u - (u * u * u * u) / 2)
    }
}

func scatterCollageHeroClamp(_ x: Double) -> Double { min(1, max(0, x)) }

/// `Int(x.rounded())` that never traps: NaN and infinities (a pager thrown by a bad velocity) give 0.
func scatterCollageHeroRound(_ x: Double) -> Int {
    guard x.isFinite else { return 0 }
    return Int(min(max(x, -1e6), 1e6).rounded())
}

func scatterCollageHeroSmooth(_ from: Double, _ to: Double, _ x: Double) -> Double {
    let u = scatterCollageHeroClamp((x - from) / (to - from))
    return u * u * (3 - 2 * u)
}

/// How much of a line of the hero's copy has risen in, by its place in the stagger.
func scatterCollageHeroReveal(_ open: Double, _ order: Int) -> Double {
    typealias M = ScatterCollageHeroMetrics
    let from = M.revealFrom + Double(order) * M.revealStep
    return scatterCollageHeroSmooth(from, from + M.revealSpan, open)
}

/// The fixed part of each tile: when it is born, which way it travels, its size and shape.
struct ScatterCollageHeroTile {
    var phase: Double
    var angle: Double
    var reach: Double
    var width: Double
    var height: Double

    static func make(_ sessions: [ScatterCollageHeroSession]) -> [ScatterCollageHeroTile] {
        sessions.enumerated().map { i, s in
            let size = 0.82 + 0.36 * noise(i, 2)
            let (w, h): (Double, Double) = switch s.shape {
            case .portrait: (0.78, 1.04)
            case .landscape: (1.08, 0.78)
            case .square: (0.92, 0.92)
            }
            return ScatterCollageHeroTile(
                phase: Double(i) / Double(sessions.count),
                angle: Double(i) * ScatterCollageHeroMetrics.golden + 0.5,
                reach: 0.84 + 0.32 * noise(i, 1),
                width: w * size,
                height: h * size
            )
        }
    }

    private static func noise(_ i: Int, _ k: Int) -> Double {
        let x = sin(Double(i) * 127.1 + Double(k) * 311.7) * 43758.5453
        return x - floor(x)
    }

    /// Where the tile is on the screen for a given flight (0…1 is one cycle).
    func at(_ travel: Double, width: Double, height: Double) -> ScatterCollageHeroGeo {
        typealias M = ScatterCollageHeroMetrics
        let raw = phase + travel
        let u = raw - floor(raw)
        let s = pow(M.growth, u - 1)
        let base = M.baseSize * (width / 402) * s
        let w = base * self.width
        let h = base * self.height
        let cx = width * M.vanishX + cos(angle) * width * M.spreadX * reach * s
        let cy = height * M.vanishY + sin(angle) * height * M.spreadY * reach * s
        return ScatterCollageHeroGeo(
            x: cx - w / 2, y: cy - h / 2, w: w, h: h,
            r: M.radiusMin + M.radiusShare * min(w, h),
            u: u,
            alpha: scatterCollageHeroSmooth(M.fadeFrom, M.fadeIn, u) * (1 - scatterCollageHeroSmooth(M.fadeOut, 1, u))
        )
    }
}

struct ScatterCollageHeroGeo {
    var x, y, w, h, r, u, alpha: Double
}

struct ScatterCollageHeroStage: Equatable {
    var width = 0.0
    var height = 0.0
    var top = 0.0
    var bottom = 0.0
}

/// A photo that has arrived, and when (on the engine's clock), so it can fade in.
struct ScatterCollageHeroLoaded {
    var image: UIImage
    var at: Double
}

/// Downloads a photo once and keeps it decoded.
enum ScatterCollageHeroPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    static func load(_ address: String) async -> UIImage? {
        if let hit = cache.object(forKey: address as NSString) { return hit }
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        // Decoded off the main thread, so the first frame that draws it does not stall.
        let ready = await image.byPreparingForDisplay() ?? image
        cache.setObject(ready, forKey: address as NSString)
        return ready
    }
}

/// The collage's and the hero's state, stepped once a frame like the RN frame callback. Nothing
/// in it is observed: the timeline redraws every frame anyway.
@MainActor
final class ScatterCollageHeroEngine {
    private typealias M = ScatterCollageHeroMetrics

    private(set) var sessions: [ScatterCollageHeroSession] = []
    private(set) var tiles: [ScatterCollageHeroTile] = []
    private(set) var stage = ScatterCollageHeroStage()
    // How far the field has flown, in cycles (0…1); a clock for the photo fades.
    private(set) var travel = 0.0
    private(set) var elapsed = 0.0
    private(set) var low: [ScatterCollageHeroLoaded?] = []
    // Full-size copies for the heroes, fetched when a hero opens.
    private(set) var high: [ScatterCollageHeroLoaded?] = []
    // The hero: 0 in its slot, 1 full screen. `page` is the hero in front, `ids` the open list.
    private(set) var open = ScatterCollageHeroDriven(0)
    private(set) var page = ScatterCollageHeroDriven(0)
    private(set) var dragX = ScatterCollageHeroDriven(0)
    private(set) var dragY = ScatterCollageHeroDriven(0)
    private(set) var ids: [Int] = []
    private(set) var heroOn = false
    private(set) var closing = false
    /// The heroes whose copy and controls are mounted, or nil while the collage shows.
    private(set) var hero: [Int]?
    // The pan's direction once it is clear: 1 across (paging), 2 down (closing).
    private var axis = 0
    private var panFrom = 0.0
    // The chips' filter, faded from one category to the next.
    private(set) var chip = 0
    private var catFrom = 0
    private var catTo = 0
    private(set) var catMix = ScatterCollageHeroDriven(1)
    private(set) var pillX = ScatterCollageHeroDriven(0)
    private(set) var pillW = ScatterCollageHeroDriven(0)
    private var chipBoxes: [Int: CGRect] = [:]
    // autoPlay.
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var auto: Bool?
    private(set) var reduced = false
    private var last: Date?

    var onStart: ((ScatterCollageHeroSession) -> Void)?

    private let lightImpact = UIImpactFeedbackGenerator(style: .light)
    private let mediumImpact = UIImpactFeedbackGenerator(style: .medium)
    private let selection = UISelectionFeedbackGenerator()

    func configure(sessions: [ScatterCollageHeroSession], autoPlay: Bool, reducedMotion: Bool) {
        if tiles.isEmpty || sessions.count != self.sessions.count {
            self.sessions = sessions
            tiles = ScatterCollageHeroTile.make(sessions)
            low = Array(repeating: nil, count: sessions.count)
            high = Array(repeating: nil, count: sessions.count)
        }
        reduced = reducedMotion
        let next = autoPlay && !reducedMotion
        if auto != next {
            auto = next
            looping = next
            clock = 0
            hold = 0
        }
    }

    // MARK: Photos

    func photoArrived(_ image: UIImage, at index: Int) {
        guard index < low.count else { return }
        low[index] = ScatterCollageHeroLoaded(image: image, at: elapsed)
    }

    /// Fetches the full-size photos of the heroes that just opened, the tapped one first, and lets
    /// go of the ones furthest back once more than `sharpKeep` are held.
    private func sharpen(_ list: [Int], first: Int) {
        let order = [list[first]] + list.enumerated().filter { $0.offset != first }.map(\.element)
        for index in order where high[index] == nil {
            let address = ScatterCollageHeroSession.photoURL(sessions[index].photo, width: M.heroPx)
            Task { @MainActor [weak self] in
                guard let image = await ScatterCollageHeroPhotos.load(address), let self, index < self.high.count else { return }
                self.high[index] = ScatterCollageHeroLoaded(image: image, at: self.elapsed)
                var count = self.high.reduce(0) { $0 + ($1 == nil ? 0 : 1) }
                for k in self.high.indices where count > M.sharpKeep {
                    if self.high[k] != nil && !list.contains(k) {
                        self.high[k] = nil
                        count -= 1
                    }
                }
            }
        }
    }

    // MARK: Frame

    func step(to date: Date, stage next: ScatterCollageHeroStage) {
        stage = next
        defer { last = date }
        guard let last else { return }
        let dt = min(date.timeIntervalSince(last), 0.064)
        if dt <= 0 || stage.width == 0 { return }
        elapsed += dt

        if hold > 0 {
            hold -= dt
            if hold <= 0 {
                clock = 0
                // A hero left open goes back to its slot before the loop starts over.
                if looping && heroOn && axis == 0 { closeHero(user: false, velocity: 0) }
            }
        }
        if looping && hold <= 0 {
            let before = clock
            let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            let step = t >= before ? M.flown(t) - M.flown(before) : M.flown(M.loop) - M.flown(before) + M.flown(t)
            travel = (travel + step / M.cycle).truncatingRemainder(dividingBy: 1)
            func passed(_ at: Double) -> Bool {
                (before < at && t >= at) || (t < before && (at > before || at <= t))
            }
            if passed(M.openAt) { openTile(showcase(), user: false) }
            if passed(M.pageAt) && heroOn && ids.count > 1 {
                let k = scatterCollageHeroRound(page.value)
                goTo(k + 1 < ids.count ? k + 1 : k - 1, user: false, velocity: 0)
            }
            if passed(M.dragAt) && heroOn {
                let curve = M.easeInOut
                dragY.timing(to: M.dragPull, duration: 0.38) { curve.value(at: $0) }
                dragX.timing(to: -12, duration: 0.38) { curve.value(at: $0) }
            }
            if passed(M.dismissAt) { closeHero(user: false, velocity: 1.4) }
        } else if !reduced {
            // By hand the field flies whenever no hero covers it.
            travel = (travel + dt * (1 - scatterCollageHeroClamp(open.value)) / M.cycle).truncatingRemainder(dividingBy: 1)
        }

        if open.step(dt) && closing && open.value == 0 { finishClose() }
        _ = page.step(dt)
        _ = dragX.step(dt)
        _ = dragY.step(dt)
        _ = catMix.step(dt)
        _ = pillX.step(dt)
        _ = pillW.step(dt)
    }

    // MARK: Collage

    /// The category's weight on a tile: 1 when it is shown, `dimmed` when filtered out.
    private func shown(_ index: Int, _ category: Int) -> Double {
        category == 0 || ScatterCollageHeroSession.categories[category] == sessions[index].category ? 1 : M.dimmed
    }

    /// How strongly each tile is drawn this frame, from the chips' filter.
    func weight(_ index: Int) -> Double {
        let a = shown(index, catFrom)
        return a + (shown(index, catTo) - a) * catMix.value
    }

    func geo(_ index: Int) -> ScatterCollageHeroGeo {
        tiles[index].at(travel, width: stage.width, height: stage.height)
    }

    /// The tiles fully on screen, read top to bottom, that a hero can page through.
    private func heroList(_ must: Int) -> [Int] {
        let s = stage
        var found: [(i: Int, y: Double, area: Double)] = []
        for i in tiles.indices {
            let g = geo(i)
            let inside = g.x >= 6 && g.x + g.w <= s.width - 6 && g.y >= s.top + 6 && g.y + g.h <= s.height - s.bottom - 6
            if i == must || (inside && g.alpha > 0.9 && shown(i, catTo) == 1) {
                found.append((i, g.y + g.h / 2, g.w * g.h))
            }
        }
        // The largest few, always with the tapped one, then read top to bottom.
        found.sort { $0.area > $1.area }
        var kept = Array(found.prefix(M.maxPages))
        if !kept.contains(where: { $0.i == must }), let tapped = found.first(where: { $0.i == must }) {
            kept[kept.count - 1] = tapped
        }
        kept.sort { $0.y < $1.y }
        return kept.map(\.i)
    }

    /// The tile the loop opens: a mid-sized one near the middle, fully on screen.
    private func showcase() -> Int {
        let s = stage
        var best = 0
        var score = Double.infinity
        for i in tiles.indices {
            let g = geo(i)
            let inside = g.x >= 12 && g.x + g.w <= s.width - 12 && g.y >= s.top + 40 && g.y + g.h <= s.height - s.bottom - 90
            if !inside || g.alpha < 0.95 { continue }
            let off = abs(g.x + g.w / 2 - s.width * 0.5) + abs(g.y + g.h / 2 - s.height * 0.46) - g.w * 0.8
            if off < score {
                score = off
                best = i
            }
        }
        return best
    }

    // MARK: Hero

    /// The hero in front, as an index into `ids`.
    var front: Int {
        max(0, min(ids.count - 1, scatterCollageHeroRound(page.value)))
    }

    private func openTile(_ index: Int, user: Bool) {
        if heroOn { return }
        let list = heroList(index)
        let first = list.firstIndex(of: index) ?? 0
        ids = list
        page.set(Double(first))
        dragX.set(0)
        dragY.set(0)
        closing = false
        heroOn = true
        open.set(0)
        if reduced {
            open.timing(to: 1, duration: M.fadeTime)
        } else {
            open.spring(to: 1, duration: M.openSpring.duration, dampingRatio: M.openSpring.damping)
        }
        hero = list
        sharpen(list, first: first)
        if user { lightImpact.impactOccurred() }
    }

    private func closeHero(user: Bool, velocity: Double) {
        if !heroOn || closing { return }
        closing = true
        if reduced {
            open.timing(to: 0, duration: M.fadeTime)
        } else {
            open.spring(to: 0, duration: M.closeSpring.duration, dampingRatio: M.closeSpring.damping, velocity: -velocity)
        }
        if user { lightImpact.impactOccurred() }
    }

    private func finishClose() {
        heroOn = false
        closing = false
        dragX.set(0)
        dragY.set(0)
        hero = nil
    }

    private func goTo(_ target: Int, user: Bool, velocity raw: Double) {
        guard !ids.isEmpty else { return }
        let velocity = raw.isFinite ? raw : 0
        let to = min(ids.count - 1, max(0, target))
        let before = scatterCollageHeroRound(page.value)
        if reduced {
            page.timing(to: Double(to), duration: M.fadeTime)
        } else {
            page.spring(to: Double(to), duration: M.pageSpring.duration, dampingRatio: M.pageSpring.damping, velocity: velocity)
        }
        if user && to != before { selection.selectionChanged() }
    }

    private func touched() {
        hold = heroOn ? M.heroHold : M.userHold
    }

    // MARK: Touch

    func tap(at point: CGPoint) {
        if heroOn { return }
        touched()
        // The nearest tile under the finger.
        var hit = -1
        var nearest = -1.0
        for i in tiles.indices {
            let g = geo(i)
            if g.alpha < 0.4 || shown(i, catTo) < 1 { continue }
            if point.x >= g.x && point.x <= g.x + g.w && point.y >= g.y && point.y <= g.y + g.h && g.u > nearest {
                nearest = g.u
                hit = i
            }
        }
        if hit >= 0 {
            openTile(hit, user: true)
            // Held longer now that a hero is open, so it can be read before the loop takes it back.
            touched()
        }
    }

    private var panning = false

    func panChanged(translation: CGSize) {
        if !heroOn || closing { return }
        if !panning {
            panning = true
            touched()
            axis = 0
            page.set(page.value)
            panFrom = page.value
        }
        if axis == 0 {
            axis = abs(translation.width) > abs(translation.height) ? 1 : 2
            dragX.set(dragX.value)
            dragY.set(dragY.value)
        }
        if axis == 1 {
            // Past the first or the last hero the pager gives way less and less.
            let n = Double(ids.count)
            var p = panFrom - (stage.width > 0 ? translation.width / stage.width : 0)
            if p < 0 { p *= M.rubber }
            if p > n - 1 { p = n - 1 + (p - (n - 1)) * M.rubber }
            page.set(p)
        } else {
            dragX.set(translation.width * 0.7)
            dragY.set(translation.height > 0 ? translation.height : translation.height * 0.18)
        }
    }

    func panEnded(translation: CGSize, velocity raw: CGSize) {
        defer {
            panning = false
            axis = 0
        }
        if !panning || !heroOn || closing { return }
        touched()
        // A non-finite speed would make the pager NaN, and Int() of it traps.
        let velocity = CGSize(width: raw.width.isFinite ? raw.width : 0, height: raw.height.isFinite ? raw.height : 0)
        if axis == 1 {
            let v = stage.width > 0 ? -velocity.width / stage.width : 0
            let from = scatterCollageHeroRound(panFrom)
            let aim = scatterCollageHeroRound(page.value + v * M.pageThrow)
            goTo(min(from + 1, max(from - 1, aim)), user: true, velocity: v)
        } else if translation.height > M.dismissDistance || velocity.height > M.dismissSpeed {
            closeHero(user: true, velocity: max(0, velocity.height) / (stage.height * 0.5))
        } else {
            dragX.spring(to: 0, duration: M.dragBack.duration, dampingRatio: M.dragBack.damping, velocity: velocity.width * 0.7)
            dragY.spring(to: 0, duration: M.dragBack.duration, dampingRatio: M.dragBack.damping, velocity: velocity.height)
        }
    }

    /// The system took the touch away (no onEnded): settle on a hero, put a dragged one back, and
    /// let the next pan start afresh.
    func panCancelled() {
        guard panning else { return }
        if heroOn && !closing {
            if axis == 1 {
                goTo(scatterCollageHeroRound(page.value), user: false, velocity: 0)
            } else if axis == 2 {
                dragX.spring(to: 0, duration: M.dragBack.duration, dampingRatio: M.dragBack.damping)
                dragY.spring(to: 0, duration: M.dragBack.duration, dampingRatio: M.dragBack.damping)
            }
        }
        panning = false
        axis = 0
    }

    func close() {
        touched()
        closeHero(user: true, velocity: 0)
    }

    func pick(_ k: Int) {
        touched()
        goTo(k, user: true, velocity: 0)
    }

    func start() {
        touched()
        mediumImpact.impactOccurred()
        guard let hero, !hero.isEmpty else { return }
        let k = min(hero.count - 1, max(0, scatterCollageHeroRound(page.value)))
        onStart?(sessions[hero[k]])
    }

    // MARK: Chips

    func chipLaidOut(_ index: Int, frame: CGRect) {
        chipBoxes[index] = frame
        if index == chip {
            pillX.set(frame.minX)
            pillW.set(frame.width)
        }
    }

    func chooseChip(_ index: Int) {
        touched()
        if index == chip { return }
        chip = index
        selection.selectionChanged()
        catFrom = catTo
        catTo = index
        catMix.set(0)
        let curve = M.easeInOut
        catMix.timing(to: 1, duration: M.filterTime) { curve.value(at: $0) }
        if let box = chipBoxes[index] {
            if reduced {
                pillX.set(box.minX)
                pillW.set(box.width)
            } else {
                pillX.spring(to: box.minX, duration: M.chipSpring.duration, dampingRatio: M.chipSpring.damping)
                pillW.spring(to: box.width, duration: M.chipSpring.duration, dampingRatio: M.chipSpring.damping)
            }
        }
    }

    // MARK: Drawing

    /// The hero's frame: between the tile's slot (open = 0) and the screen (open = 1). A drag down
    /// turns the screen into a card that shrinks and follows the finger.
    func heroRect(slot: ScatterCollageHeroGeo) -> (rect: CGRect, radius: Double) {
        let width = stage.width
        let height = stage.height
        let o = open.value
        let dx = dragX.value
        let dy = dragY.value
        let pull = scatterCollageHeroClamp(dy / (height * M.dismissSpan))
        let s = 1 - M.dismissShrink * pull
        let fw = width * s
        let fh = height * s
        let fx = (width - fw) / 2 + dx
        let fy = (height - fh) / 2 + dy * 0.62
        let fr = M.dismissRadius * scatterCollageHeroClamp(pull * 3)
        let rect = CGRect(
            x: slot.x + (fx - slot.x) * o,
            y: slot.y + (fy - slot.y) * o,
            width: max(1, slot.w + (fw - slot.w) * o),
            height: max(1, slot.h + (fh - slot.h) * o)
        )
        return (rect, max(0, slot.r + (fr - slot.r) * scatterCollageHeroClamp(o)))
    }
}
