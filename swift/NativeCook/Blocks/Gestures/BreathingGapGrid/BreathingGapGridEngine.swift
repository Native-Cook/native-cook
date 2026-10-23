import SwiftUI
import UIKit

enum BreathingGapGridMetrics {
    // The grid: columns of square cells `pitch` apart, centred on the screen. Closed up there are
    // four, `pitchShare` of the width apart so the outer two run half off the edges, and a tile fills
    // `dense` of its cell. Opened out the grid zooms out to `airyColumns` whole columns, a tile fills
    // `airy` of its cell, and the margins at both edges are exactly as wide as the gaps between the tiles.
    static let pitchShare = 0.3
    static let dense = 0.88
    static let airyColumns = 8.0
    static let airy = 0.56
    // A pinch in by this factor goes all the way from closed to open.
    static let pinchRange = 2.4
    // Past either end a pinch keeps following at this share of its pull, never further than `overshoot`.
    static let rubber = 0.25
    static let overshoot = 0.15
    // Each column drifts upward at its own speed, in cells per second, from its own phase; the eight
    // values repeat across the columns.
    static let drift = [0.22, 0.34, 0.27, 0.38, 0.3, 0.24, 0.36, 0.29]
    static let phase = [0.1, 0.62, 0.35, 0.83, 0.48, 0.2, 0.71, 0.04]
    // The type tiles are set at `typeSize` and scaled so their widest line fills `typeFill` of the tile.
    static let typeSize: CGFloat = 100
    static let typeFill = 0.62
    // A photo's lower half darkens towards its bottom edge by up to this alpha.
    static let shadeFrom = 0.5
    static let shadeStrength = Double(0x47) / 255

    // Arrival: the tiles grow in from `seed` of their size, rippling out from the middle of the screen
    // over `enterSpread` s, each over `enterEach` s; the drift starts `burst` times faster and settles
    // to its own speed over `burstTime` s. A photo fades in over `imageFade` s once it has loaded.
    static let enterDelay = 0.08
    static let enterSpread = 0.7
    static let enterEach = 0.65
    static let seed = 0.55
    static let burst = 5.0
    static let burstTime = 1.8
    static let imageFade = 0.45
    static let chromeDelay = 0.45
    static let chromeTime = 0.6

    // The caption's lane: as the gap opens, the rows within `partRange` of the middle part by up to
    // `part` pt either side, and a tile whose edge comes within `clear` pt of the line fades out over
    // a further `clearSoft` pt.
    static let captionAt = 0.5
    static let part = 26.0
    static let partRange = 150.0
    static let partSoft = 18.0
    static let clear = 6.0
    static let clearSoft = 18.0
    static let laneHeight = 120.0
    static let laneStrength = Double(0xb3) / 255
    // The caption's letters arrive one after another once the gap is `captionFrom` open, each rising
    // `letterRise` pt out of a `letterBlur` pt blur; rules then draw out from it to the margins.
    static let captionFrom = 0.35
    static let letterSpread = 0.6
    static let letterRise = 12.0
    static let letterBlur = 5.0
    static let captionSize: CGFloat = 13
    static let captionTracking = 3.2
    static let ruleGap = 16.0
    static let ruleMargin = 20.0

    // Snapping the gap after a pinch: the pinch's speed carries it this far ahead, in seconds.
    static let pinchThrow = 0.15
    static let gapSpring = (duration: 0.52, dampingRatio: 0.86)
    // A dragged wall keeps gliding, losing this share of its speed every millisecond.
    static let glideDecay = 0.997

    // An open tile: its side margin, and where its centre sits as a share of the screen.
    static let openMargin = 24.0
    static let openAt = 0.42
    static let openSpring = (duration: 0.52, dampingRatio: 0.88)
    // Behind an open tile the wall blurs by up to `wallBlur` and dims by `wallDim`, and a glow in the
    // tile's own colour spreads `glowBlur` pt below it.
    static let wallBlur = 10.0
    static let wallDim = 0.8
    static let glow = 0.5
    static let glowBlur = 34.0
    static let glowDrop = 20.0
    // Dragging an open tile: it follows the finger, shrinking by up to `dragShrink` over
    // `shrinkShare` of the screen's height pulled down, and resisting at `liftRubber` when pushed up.
    // The wall comes back by `awayRelief` as the tile is pulled `awayShare` of the height away.
    static let dragShrink = 0.45
    static let shrinkShare = 0.6
    static let liftRubber = 0.3
    static let awayShare = 0.4
    static let awayRelief = 0.75
    static let dismissDistance = 110.0
    static let dismissSpeed = 650.0
    static let dragSpring = (duration: 0.46, dampingRatio: 0.9)
    // The open tile's note and title sit `detailGap` pt below it and rise `captionRise` pt into place.
    static let detailGap = 22.0
    static let captionRise = 16.0
    // The wall darkens towards the top and bottom edges, so the labels there stay legible.
    static let edgeFade: CGFloat = 96
    static let edgeStrength = Double(0xe6) / 255

    // autoPlay loop (seconds), with the columns always drifting: the gap opens, closes, a tile opens
    // and closes again.
    static let loop = 11.0
    static let openGapAt = 1.4
    static let closeGapAt = 4.8
    static let openTileAt = 6.6
    static let closeTileAt = 8.8
    static let gapTime = 1.2
    // After a touch the loop waits this long before it starts over.
    static let userHold = 3.0
    static let panSlop: CGFloat = 6
    static let tapMaxDuration = 0.4

    static let none = -1000

    // Skia's blurs are Gaussian sigmas in points; a SwiftUI blur radius spreads about half as far.
    static func blurRadius(_ sigma: Double) -> CGFloat { sigma * 2 }
}

/// The grid's spacing for a gap `spread` open (0 closed up, 1 opened out).
struct BreathingGapGridLayout {
    private typealias M = BreathingGapGridMetrics

    var width: Double
    var height: Double
    var pitch: Double
    var side: Double
    var time: Double
    var scroll: Double
    var part: Double

    init(width: Double, height: Double, spread: Double, time: Double, scroll: Double) {
        let g = min(1 + M.overshoot, max(-M.overshoot, spread))
        let dense = width * M.pitchShare
        let airy = width / (M.airyColumns + 1 - M.airy)
        // Zooming evenly: the pitch changes by the same factor for every step of the gap.
        let pitch = dense * pow(airy / dense, g)
        let p = min(1, max(0, g))
        self.width = width
        self.height = height
        self.pitch = pitch
        side = pitch * (M.dense + (M.airy - M.dense) * g)
        self.time = time
        self.scroll = scroll
        part = p * p * (3 - 2 * p)
    }

    static func lane(_ c: Int) -> Int {
        let n = M.drift.count
        return ((c % n) + n) % n
    }

    /// Which tile fills cell (c, r): a fixed shuffle. With the 87 tiles here, the nearest repeat of
    /// any tile is at least eight cells away, further than the grid shows when closed up.
    static func tileAt(_ c: Int, _ r: Int, _ n: Int) -> Int {
        let k = c * 3 + r * 8
        return ((k % n) + n) % n
    }

    /// The middle of cell (c, r): its column drifts and scrolls in cells; rows part around the caption.
    func cellCentre(_ c: Int, _ r: Int) -> CGPoint {
        let m = Self.lane(c)
        let x = width / 2 + (Double(c) - 1.5) * pitch
        let y = height / 2 + (Double(r) + M.phase[m] - time * M.drift[m] + scroll) * pitch
        let d = y - height * M.captionAt
        let reach = max(0, 1 - abs(d) / M.partRange)
        return CGPoint(x: x, y: y + (d / (abs(d) + M.partSoft)) * M.part * part * reach * reach)
    }

    /// How many columns have their middle on screen.
    var columnsShown: Int { 2 * Int(floor(width / 2 / pitch + 0.5)) }
}

struct BreathingGapGridPhoto {
    var image: UIImage
    /// The block's clock when it arrived, for its fade.
    var at: Double
}

/// One frame to draw.
struct BreathingGapGridFrame {
    var layout: BreathingGapGridLayout
    var spread: Double
    var elapsed: Double
    var open: Double
    var openC: Int
    var openR: Int
    var dragX: Double
    var dragY: Double
    var chrome: Double
    var photos: [BreathingGapGridPhoto?]
    /// The larger copy of the open tile, and which tile it belongs to.
    var sharp: (photo: BreathingGapGridPhoto, index: Int)?
}

struct BreathingGapGridSettings {
    var tiles: [BreathingGapGridTile]
    var autoPlay: Bool
    var reducedMotion: Bool
    var onOpen: ((BreathingGapGridTile) -> Void)?
}

/// The wall's state, stepped once a frame like the RN frame callback, and the gestures that move it.
@MainActor
final class BreathingGapGridEngine {
    private typealias M = BreathingGapGridMetrics
    private typealias L = BreathingGapGridLayout

    private var width = 0.0
    private var height = 0.0
    // Seconds since the wall first appeared.
    private var elapsed = 0.0
    private var photos: [BreathingGapGridPhoto?] = []
    private var sharp: (photo: BreathingGapGridPhoto, index: Int)?
    // 0 closed up … 1 opened out.
    private var spread = BreathingGapGridValue(0)
    private var pinchFrom = 0.0
    private var pinching = false
    // Seconds of drift so far; it slows to a stop while a tile is open.
    private var time = 0.0
    // The user's own drag of the wall, in cells, and its glide after release in cells per second.
    private var scroll = 0.0
    private var glide = 0.0
    private var dragFrom = 0.0
    private var dragPitch = 1.0
    // 0 shut, 1 open, and the cell it applies to.
    private var open = BreathingGapGridValue(0)
    private var openC = M.none
    private var openR = 0
    private var isOpen = false
    // How far the open tile has been pulled from its place, in pt.
    private var dragX = BreathingGapGridValue(0)
    private var dragY = BreathingGapGridValue(0)
    private var liftFromX = 0.0
    private var liftFromY = 0.0
    private var dragsTile = false
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var lastDate: Date?
    private var lastAuto: Bool?
    private var lastColumns = 0
    // The touch under way: when it began, whether it became a pan, and whether a second finger
    // turned it into a pinch.
    private var touchStart: Date?
    private var touchOrigin = CGPoint.zero
    private var panning = false
    private var pinchTouch = false
    // A second finger can end the one-finger drag without its end ever being reported, so a new
    // touch is told apart by where it started, and a pinch still under way by this.
    private var magnifying = false

    private let selection = UISelectionFeedbackGenerator()
    private let impact = UIImpactFeedbackGenerator(style: .light)

    // MARK: Photos

    func resetPhotos(count: Int) {
        photos = Array(repeating: nil, count: count)
    }

    func photoArrived(_ image: UIImage, at index: Int) {
        guard photos.indices.contains(index) else { return }
        photos[index] = BreathingGapGridPhoto(image: image, at: elapsed)
    }

    // MARK: Frame

    func step(to date: Date, width: Double, height: Double, settings: BreathingGapGridSettings) -> BreathingGapGridFrame {
        self.width = width
        self.height = height
        let count = settings.tiles.count
        if photos.count != count { photos = Array(repeating: nil, count: count) }
        if lastAuto != settings.autoPlay {
            lastAuto = settings.autoPlay
            looping = settings.autoPlay
            clock = 0
            hold = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(raw, 0.064)
        if dt > 0 && width > 0 {
            advance(dt: dt, settings: settings)
        }

        let layout = L(width: width, height: height, spread: spread.value, time: time, scroll: scroll)
        // A selection tick each time a pinch brings another pair of columns into view.
        let columns = width > 0 ? L(width: width, height: height, spread: spread.value, time: 0, scroll: 0).columnsShown : 0
        if lastColumns != 0 && columns != lastColumns && pinching { selection.selectionChanged() }
        lastColumns = columns

        let p = min(1, max(0, (elapsed - M.chromeDelay) / M.chromeTime))
        return BreathingGapGridFrame(
            layout: layout,
            spread: spread.value,
            elapsed: elapsed,
            open: open.value,
            openC: openC,
            openR: openR,
            dragX: dragX.value,
            dragY: dragY.value,
            chrome: BreathingGapGridCurve.easeOut(p),
            photos: photos,
            sharp: sharp
        )
    }

    private func advance(dt: Double, settings: BreathingGapGridSettings) {
        let now = elapsed + dt
        elapsed = now
        spread.step(dt: dt, now: now)
        open.step(dt: dt, now: now)
        dragX.step(dt: dt, now: now)
        dragY.step(dt: dt, now: now)
        if !settings.reducedMotion {
            // The drift arrives fast and settles to its own pace; it slows to a stop under an open tile.
            let p = min(1, now / M.burstTime)
            let burst = 1 + (M.burst - 1) * (1 - p) * (1 - p)
            time += dt * burst * (1 - min(max(open.value, 0), 1))
        }
        if glide != 0 {
            let pitch = L(width: width, height: height, spread: spread.value, time: 0, scroll: 0).pitch
            scroll += glide * dt
            let next = glide * pow(M.glideDecay, dt * 1000)
            glide = abs(next * pitch) < 4 ? 0 : next
        }
        if hold > 0 {
            if !isOpen { hold -= dt }
            if hold <= 0 { clock = 0 }
            return
        }
        guard looping else { return }
        let before = clock
        let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
        clock = t
        func passed(_ at: Double) -> Bool {
            (before < at && t >= at) || (t < before && (at > before || at <= t))
        }
        if passed(M.openGapAt) { setGap(1, velocity: 0, timed: true, settings: settings) }
        if passed(M.closeGapAt) { setGap(0, velocity: 0, timed: true, settings: settings) }
        if passed(M.openTileAt) {
            // The photo in the second column nearest where an open tile sits.
            let l = L(width: width, height: height, spread: spread.value, time: time, scroll: scroll)
            let near = Int(floor((height * M.openAt - l.cellCentre(1, 0).y) / l.pitch + 0.5))
            var row = near
            for step in [0, 1, -1, 2, -2] where settings.tiles[L.tileAt(1, near + step, settings.tiles.count)].isPhoto {
                row = near + step
                break
            }
            openCell(1, row, user: false, settings: settings)
        }
        if passed(M.closeTileAt) && isOpen { settleOpen(0, settings: settings) }
    }

    // MARK: Motion

    private func setGap(_ to: Double, velocity: Double, timed: Bool, settings: BreathingGapGridSettings) {
        if settings.reducedMotion {
            spread.timing(to: to, duration: 0.2, curve: BreathingGapGridCurve.standard, now: elapsed)
        } else if timed {
            spread.timing(to: to, duration: M.gapTime, curve: BreathingGapGridCurve.easeInOut, now: elapsed)
        } else {
            spread.spring(to: to, duration: M.gapSpring.duration, dampingRatio: M.gapSpring.dampingRatio, velocity: velocity)
        }
    }

    private func settleOpen(_ to: Double, settings: BreathingGapGridSettings) {
        isOpen = to == 1
        if settings.reducedMotion {
            open.timing(to: to, duration: 0.2, curve: BreathingGapGridCurve.easeOut, now: elapsed)
        } else {
            open.spring(to: to, duration: M.openSpring.duration, dampingRatio: M.openSpring.dampingRatio)
        }
    }

    // The open tile returns to its place, carrying the finger's speed.
    private func settleDrag(vx: Double, vy: Double, settings: BreathingGapGridSettings) {
        if settings.reducedMotion {
            dragX.timing(to: 0, duration: 0.2, curve: BreathingGapGridCurve.easeOut, now: elapsed)
            dragY.timing(to: 0, duration: 0.2, curve: BreathingGapGridCurve.easeOut, now: elapsed)
            return
        }
        dragX.spring(to: 0, duration: M.dragSpring.duration, dampingRatio: M.dragSpring.dampingRatio, velocity: vx)
        dragY.spring(to: 0, duration: M.dragSpring.duration, dampingRatio: M.dragSpring.dampingRatio, velocity: vy)
    }

    private func openCell(_ c: Int, _ r: Int, user: Bool, settings: BreathingGapGridSettings) {
        openC = c
        openR = r
        dragX.set(0)
        dragY.set(0)
        settleOpen(1, settings: settings)
        let index = L.tileAt(c, r, settings.tiles.count)
        let tile = settings.tiles[index]
        if case let .photo(_, full?, _) = tile.kind, sharp?.index != index {
            Task { @MainActor [weak self] in
                guard let image = await BreathingGapGridPhotos.load(full, cached: false) else { return }
                guard let self else { return }
                self.sharp = (BreathingGapGridPhoto(image: image, at: self.elapsed), index)
            }
        }
        guard user else { return }
        impact.impactOccurred()
        settings.onOpen?(tile)
    }

    private func touched() {
        hold = M.userHold
    }

    // MARK: Gestures

    func pinchChanged(scale: Double, settings: BreathingGapGridSettings) {
        magnifying = true
        if !pinchTouch {
            pinchTouch = true
            // A second finger turns a drag of the wall into a pinch, as a one-finger pan gives way.
            if panning && !dragsTile { scroll = dragFrom }
            if isOpen { return }
            touched()
            pinching = true
            pinchFrom = spread.value
        }
        guard pinching, !isOpen else { return }
        let next = pinchFrom - log(max(scale, 0.01)) / log(M.pinchRange)
        // Past either end it follows at a quarter of the pull.
        spread.set(next > 1 ? 1 + (next - 1) * M.rubber : next < 0 ? next * M.rubber : next)
    }

    func pinchEnded(scale: Double, velocity: Double, settings: BreathingGapGridSettings) {
        defer {
            pinching = false
            magnifying = false
        }
        guard pinching, !isOpen else { return }
        // The pinch's speed in gap per second.
        let speed = -velocity / (log(M.pinchRange) * max(scale, 0.01))
        let ahead = spread.value + speed * M.pinchThrow
        setGap(ahead > 0.5 ? 1 : 0, velocity: speed, timed: false, settings: settings)
        impact.impactOccurred()
    }

    func touchMoved(_ value: DragGesture.Value, settings: BreathingGapGridSettings) {
        if touchStart == nil || value.startLocation != touchOrigin {
            touchStart = value.time
            touchOrigin = value.startLocation
            panning = false
            pinchTouch = magnifying
        }
        if pinchTouch { return }
        if !panning {
            guard hypot(value.translation.width, value.translation.height) >= M.panSlop else { return }
            panning = true
            panStart(settings: settings)
        }
        if dragsTile {
            dragX.set(liftFromX + value.translation.width)
            let y = liftFromY + value.translation.height
            dragY.set(y > 0 ? y : y * M.liftRubber)
            return
        }
        scroll = dragFrom + value.translation.height / dragPitch
    }

    func touchEnded(_ value: DragGesture.Value, settings: BreathingGapGridSettings) {
        defer {
            touchStart = nil
            panning = false
            pinchTouch = false
        }
        if pinchTouch { return }
        if panning {
            if dragsTile {
                let vy = value.velocity.height
                let shut = vy > M.dismissSpeed || (dragY.value > M.dismissDistance && vy > -M.dismissSpeed / 3)
                if shut {
                    settleOpen(0, settings: settings)
                    impact.impactOccurred()
                }
                settleDrag(vx: value.velocity.width, vy: vy, settings: settings)
                return
            }
            if !settings.reducedMotion { glide = value.velocity.height / dragPitch }
            return
        }
        guard let start = touchStart, value.time.timeIntervalSince(start) <= M.tapMaxDuration, width > 0 else { return }
        tap(at: value.location, settings: settings)
    }

    private func panStart(settings: BreathingGapGridSettings) {
        touched()
        glide = 0
        if isOpen || open.value > 0.5 {
            // Caught on its way back, the tile is held where it is.
            dragsTile = true
            if !isOpen { settleOpen(1, settings: settings) }
            liftFromX = dragX.value
            liftFromY = dragY.value
            return
        }
        dragsTile = false
        dragFrom = scroll
        dragPitch = L(width: width, height: height, spread: spread.value, time: 0, scroll: 0).pitch
    }

    private func tap(at point: CGPoint, settings: BreathingGapGridSettings) {
        touched()
        glide = 0
        if isOpen {
            settleOpen(0, settings: settings)
            settleDrag(vx: 0, vy: 0, settings: settings)
            return
        }
        // The cell under the finger: the column by x, then whichever nearby row is closest.
        let l = L(width: width, height: height, spread: spread.value, time: time, scroll: scroll)
        let c = Int(floor((point.x - width / 2) / l.pitch + 1.5 + 0.5))
        let guess = Int(floor((point.y - l.cellCentre(c, 0).y) / l.pitch + 0.5))
        var best = guess
        var off = Double.infinity
        for r in (guess - 1)...(guess + 1) {
            let d = abs(l.cellCentre(c, r).y - point.y)
            if d < off {
                off = d
                best = r
            }
        }
        if off > l.side / 2 + (l.pitch - l.side) / 2 { return }
        openCell(c, best, user: true, settings: settings)
    }
}

// MARK: - Photos

/// Downloads a photo once and keeps it decoded, cropped to its middle square with the tile's veil
/// already laid over it, so the wall only has to place images. The small copies are cached for
/// every tile that shows them; the large copy of an open tile is not kept, so only one is in memory.
enum BreathingGapGridPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    static func load(_ address: String, cached: Bool = true) async -> UIImage? {
        if let hit = cache.object(forKey: address as NSString) { return hit }
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        // Baked off the main thread, so the first frame that draws it does not stall.
        let ready = await Task.detached(priority: .userInitiated) { bake(image) }.value
        if cached { cache.setObject(ready, forKey: address as NSString) }
        return ready
    }

    /// The middle square of the photo, its lower half darkening towards the bottom edge.
    private static func bake(_ image: UIImage) -> UIImage {
        let w = image.size.width * image.scale
        let h = image.size.height * image.scale
        let side = min(w, h)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { context in
            image.draw(in: CGRect(x: (side - w) / 2, y: (side - h) / 2, width: w, height: h))
            let veil = CGGradient(
                colorsSpace: CGColorSpace(name: CGColorSpace.sRGB),
                colors: [UIColor(white: 0, alpha: 0).cgColor, UIColor(white: 0, alpha: BreathingGapGridMetrics.shadeStrength).cgColor] as CFArray,
                locations: [0, 1]
            )!
            context.cgContext.drawLinearGradient(
                veil,
                start: CGPoint(x: 0, y: side * BreathingGapGridMetrics.shadeFrom),
                end: CGPoint(x: 0, y: side),
                options: [.drawsBeforeStartLocation, .drawsAfterEndLocation]
            )
        }
    }
}

/// The type tiles set once as images, so the wall draws them like the photos.
enum BreathingGapGridTypeImages {
    private static let cache = NSCache<NSString, UIImage>()
    static let side: CGFloat = 400

    static func image(lines: [String], ink: UInt32, paper: UInt32) -> UIImage {
        let key = "\(lines.joined(separator: "|"))-\(ink)-\(paper)"
        if let hit = cache.object(forKey: key as NSString) { return hit }
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let image = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { context in
            let cg = context.cgContext
            cg.setFillColor(breathingGapGridUIColor(paper).cgColor)
            cg.fill(CGRect(x: 0, y: 0, width: side, height: side))
            BreathingGapGridTypeImages.set(lines: lines, ink: ink, in: cg, side: side)
        }
        cache.setObject(image, forKey: key as NSString)
        return image
    }

    /// The lines centred in a `side` square, scaled so the widest fills 62% of it.
    static func set(lines: [String], ink: UInt32, in cg: CGContext, side: CGFloat) {
        let m = BreathingGapGridMetrics.self
        let font = BreathingGapGridPainter.typeFont
        let widths = lines.map { BreathingGapGridPainter.advance($0, font) }
        let widest = widths.max() ?? 1
        let lineHeight = m.typeSize * 0.86
        let block = lineHeight * Double(lines.count)
        let scale = min(side * m.typeFill / widest, side * m.typeFill / block)
        cg.saveGState()
        cg.translateBy(x: side / 2, y: side / 2)
        cg.scaleBy(x: scale, y: scale)
        for (i, line) in lines.enumerated() {
            let baseline = -block / 2 + lineHeight * Double(i + 1) - m.typeSize * 0.14
            let text = NSAttributedString(string: line, attributes: [.font: font, .foregroundColor: breathingGapGridUIColor(ink)])
            let ctLine = CTLineCreateWithAttributedString(text)
            cg.saveGState()
            // Core Text draws upward from its baseline; flip about it.
            cg.translateBy(x: -widths[i] / 2, y: baseline)
            cg.scaleBy(x: 1, y: -1)
            cg.textPosition = .zero
            CTLineDraw(ctLine, cg)
            cg.restoreGState()
        }
        cg.restoreGState()
    }
}

private func breathingGapGridUIColor(_ hex: UInt32) -> UIColor {
    UIColor(
        red: CGFloat((hex >> 16) & 0xff) / 255,
        green: CGFloat((hex >> 8) & 0xff) / 255,
        blue: CGFloat(hex & 0xff) / 255,
        alpha: 1
    )
}
