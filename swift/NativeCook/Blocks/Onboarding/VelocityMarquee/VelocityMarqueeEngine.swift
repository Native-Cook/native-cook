import CoreText
import SwiftUI
import UIKit

/// The numbers of the block, the same as the React Native version's.
enum VelocityMarqueeMetrics {
    // A tile before its photo arrives.
    static let tilePaper = Color(hex: 0xe3ded3)

    // Page. The side margin (pt); the brand line sits `headerTop` below the safe top and is
    // `headerHeight` tall. The rows fill the space between it and the text, `heroGap` clear of each.
    static let margin = 24.0
    static let minTop = 20.0
    static let headerTop = 6.0
    static let headerHeight = 26.0
    static let heroGap = 18.0
    // The text block sits `footGap` above the safe bottom.
    static let footGap = 4.0
    // The paper's grain: a `grainSize`² tile of seeded noise, each texel `grainTexel` pt, with dark
    // and light specks up to these opacities. Drawn once.
    static let grainSize = 128
    static let grainTexel = 0.5
    static let grainDark = 0.06
    static let grainLight = 0.07
    static let grainSeed: UInt32 = 20_261_112

    // Rows. They lie at `tilt` degrees, rising to the right, `rowPitch` pt apart centre to centre.
    // The three rows grow or shrink together with the screen, within these scales.
    static let tilt = -8.0
    static let rowHeight = 96.0
    static let rowPitch = 112.0
    static let minHeroScale = 0.72
    static let maxHeroScale = 1.2
    // Tile widths by orientation, the gap between tiles and their corner radius, in pt.
    static let portraitWidth = 72.0
    static let landscapeWidth = 134.0
    static let tileGap = 12.0
    static let tileRadius = 18.0
    // Photos are fetched at about three times their drawn width.
    static let portraitPx = 240
    static let landscapePx = 420
    // Each photo fades in over this many seconds once it has loaded.
    static let photoFade = 0.45

    // Speed. Rows drift at `drift` pt/s (row one leftwards, the way the pages go); a drag moves
    // them with the finger and a release throws them at the finger's speed, which eases back to
    // the drift on a critically damped spring of natural frequency `omega` (1/s):
    // v(t) = v0 (1 + ωt) e^(−ωt).
    static let drift = 40.0
    static let omega = 3.2
    static let maxThrow = 6000.0
    // A vertical drag moves the rows too, at this share, so a drag in any direction throws them.
    static let verticalShare = 0.4
    // The finger's speed while dragging is smoothed over about this many seconds.
    static let dragSmooth = 0.06

    // The lean follows the speed on an underdamped spring, so tiles overshoot a little and wobble
    // upright when the rows slow down. Skew is `leanPer` degrees per pt/s, up to `leanMax`; tiles
    // stretch along the row by `stretchPer` per pt/s, up to `stretchMax`, and thin a third as much.
    static let leanStiffness = 140.0
    static let leanDamping = 11.8
    static let leanPer = 0.0055
    static let leanMax = 16.0
    static let stretchPer = 0.00004
    static let stretchMax = 0.12

    // Pages. A release that has moved `pageDistance` of the width sideways, or is going faster
    // than `pageSpeed` pt/s sideways, turns the page. The page settles on Reanimated's spring of
    // 560 ms at a damping ratio of 1, which works out to a critically damped spring of natural
    // frequency `pageOmega` (1/s) when it starts at rest.
    static let pageDistance = 0.18
    static let pageSpeed = 450.0
    static let pageOmega = 14.7236
    // Under reduced motion the page fades across in `pageFade` s instead.
    static let pageFade = 0.32
    // The text of a page slides `textShift` pt with the page and is gone by `textGone` of a page
    // away, so the old line has left before the new one arrives.
    static let textShift = 28.0
    static let textGone = 0.46
    // The page dots: `dot` pt round, `activeWidth` wide for the current page, `dotGap` apart.
    static let dot = 6.0
    static let activeWidth = 20.0
    static let dotGap = 6.0
    // Type: the headline and its line, each in boxes of fixed line height, at most this many lines.
    static let titleSize = 30.0
    static let titleLine = 35.0
    static let titleLines = 2
    static let titleKerning = -0.4
    static let bodySize = 16.0
    static let bodyLine = 22.0
    static let bodyLines = 3
    static let textGap = 10.0
    // The foot, top to bottom: the dots, the text, the button and the sign-in line.
    static let dotsBelow = 22.0
    static let textBelow = 32.0
    static let buttonHeight = 56.0
    static let signInAbove = 4.0
    static let signInHeight = 44.0
    // A press on the button squeezes it on a spring (stiffness, damping) and lets it spring back.
    static let pressScale = 0.97
    static let pressStiffness = 420.0
    static let pressDamping = 26.0

    // Entrance. The rows fade in over `heroFade` s while rising `heroRise` pt, the brand line with
    // them; the dots, the text, the button and the sign-in line follow, starting `enterDelay` s
    // after the rows and `stagger` s apart. Each fades in over `enterFade` s while rising `enterRise` pt.
    static let heroFade = 0.9
    static let heroRise = 14.0
    static let enterDelay = 0.22
    static let stagger = 0.09
    static let enterFade = 0.6
    static let enterRise = 10.0

    // autoPlay loop (seconds): the page rests, then a swipe to the next page at each `swipes`
    // time: a `push` s push of the rows to about `fling` pt/s (leftwards, as a finger swiping to
    // the next page throws them), released into the same spring as a hand's throw, the page
    // turning on release over `pageTurn` s. Three swipes carry the pages round to where they
    // started. The fling's peak is set a little either side of `fling` so the rows travel a whole
    // number of row lengths per loop and close it seamlessly.
    static let loop = 15.0
    static let swipes = [3.2, 8.2, 13.2]
    static let fling = 1400.0
    static let push = 0.28
    static let pageTurn = 0.6
    // After a touch the loop waits this long, then starts over from where things are.
    static let userHold = 3.0

    // Fonts (PostScript names).
    static let semibold = "AvenirNext-DemiBold"
    static let regular = "AvenirNext-Regular"

    /// A fling's speed over time, as a share of its peak: a smooth push, then the release spring.
    static func flingShape(_ s: Double) -> Double {
        if s <= 0 { return 0 }
        if s <= push {
            let u = s / push
            return u * u * (3 - 2 * u)
        }
        let u = s - push
        return (1 + omega * u) * exp(-omega * u)
    }

    /// How far a fling of peak 1 has carried the rows after s seconds (the integral of flingShape).
    static func flingArea(_ s: Double) -> Double {
        if s <= 0 { return 0 }
        if s <= push {
            let u = s / push
            return push * (u * u * u - (u * u * u * u) / 2)
        }
        let u = s - push
        return push / 2 + 2 / omega - exp(-omega * u) * (2 / omega + u)
    }

    /// A strong ease-out, for the page turning in the script.
    static func easeOut(_ u: Double) -> Double {
        let v = min(max(u, 0), 1)
        return 1 - (1 - v) * (1 - v) * (1 - v)
    }

    /// Where the scripted loop's page has got to by time t: one more page per swipe.
    static func scriptedPage(_ t: Double) -> Double {
        swipes.reduce(0) { $0 + easeOut((t - $1 - push) / pageTurn) }
    }

    /// Reanimated's default timing curve, an ease-in-out quad.
    static func inOutQuad(_ t: Double) -> Double {
        t < 0.5 ? 2 * t * t : 1 - (-2 * t + 2) * (-2 * t + 2) / 2
    }

    /// x mod m, always in [0, m).
    static func wrap(_ x: Double, _ m: Double) -> Double {
        guard m > 0, x.isFinite else { return 0 }
        let r = x.truncatingRemainder(dividingBy: m)
        return r < 0 ? r + m : r
    }

    /// How far page `index` is from the place `p`, the short way round `count` pages.
    static func pageDistance(_ p: Double, _ index: Int, _ count: Int) -> Double {
        let n = Double(max(1, count))
        return wrap(p - Double(index) + n / 2, n) - n / 2
    }

    /// JavaScript's Math.round: halves go up.
    static func round(_ x: Double) -> Double { (x + 0.5).rounded(.down) }
}

/// Each row's tiles laid end to end, and the length after which the row repeats.
struct VelocityMarqueeRow {
    struct Tile {
        var offset: Double
        var width: Double
        var index: Int
    }

    var tiles: [Tile]
    var length: Double
    var dir: Double

    static func make(_ rows: [[VelocityMarqueePhoto]]) -> [VelocityMarqueeRow] {
        typealias M = VelocityMarqueeMetrics
        var index = 0
        var made: [VelocityMarqueeRow] = []
        for photos in rows {
            var x = 0.0
            let tiles = photos.map { photo -> Tile in
                let tile = Tile(offset: x, width: photo.portrait ? M.portraitWidth : M.landscapeWidth, index: index)
                x += tile.width + M.tileGap
                index += 1
                return tile
            }
            if !tiles.isEmpty {
                made.append(VelocityMarqueeRow(tiles: tiles, length: x, dir: made.count % 2 == 0 ? 1 : -1))
            }
        }
        return made
    }
}

/// A paragraph broken into lines the way Core Text sets it at a width, so each line can be put in
/// a box of the React Native line height (SwiftUI cannot set lines closer than the font's own).
@MainActor
enum VelocityMarqueeLines {
    private static var cache: [String: [String]] = [:]

    static func split(_ text: String, font name: String, size: Double, kerning: Double, width: Double, limit: Int) -> [String] {
        let key = "\(name)|\(size)|\(kerning)|\(width)|\(limit)|\(text)"
        if let hit = cache[key] { return hit }
        guard width > 0, !text.isEmpty else { return [text] }
        let font = CTFontCreateWithName(name as CFString, size, nil)
        let attributed = NSAttributedString(string: text, attributes: [
            NSAttributedString.Key(kCTFontAttributeName as String): font,
            NSAttributedString.Key(kCTKernAttributeName as String): kerning,
        ])
        let setter = CTFramesetterCreateWithAttributedString(attributed)
        let path = CGPath(rect: CGRect(x: 0, y: 0, width: width, height: 10_000), transform: nil)
        let frame = CTFramesetterCreateFrame(setter, CFRange(location: 0, length: 0), path, nil)
        let source = text as NSString
        var lines: [String] = []
        for line in (CTFrameGetLines(frame) as? [CTLine]) ?? [] {
            let range = CTLineGetStringRange(line)
            let part = source.substring(with: NSRange(location: range.location, length: range.length))
            lines.append(part.trimmingCharacters(in: .whitespacesAndNewlines))
        }
        if lines.isEmpty { lines = [text] }
        if lines.count > limit {
            // The rest is cut, with an ellipsis, as numberOfLines does.
            let kept = Array(lines.prefix(limit))
            lines = kept.dropLast() + [(kept.last ?? "") + "…"]
        }
        cache[key] = lines
        return lines
    }
}

/// The paper's grain, a tile of seeded noise made once (mulberry32, the same numbers as the other
/// ports): dark and light specks in premultiplied RGBA.
enum VelocityMarqueeGrain {
    static let image: CGImage? = {
        typealias M = VelocityMarqueeMetrics
        let size = M.grainSize
        var s = M.grainSeed
        func next() -> Double {
            s = s &+ 0x6D2B_79F5
            var t = (s ^ (s >> 15)) &* (1 | s)
            t = (t &+ ((t ^ (t >> 7)) &* (61 | t))) ^ t
            return Double(t ^ (t >> 14)) / 4_294_967_296
        }
        var bytes = [UInt8](repeating: 0, count: size * size * 4)
        for i in 0..<(size * size) {
            let n = next()
            let dark = n < 0.5
            let strength = dark ? (0.5 - n) * 2 * M.grainDark : (n - 0.5) * 2 * M.grainLight
            let a = UInt8(min(255, max(0, (strength * 255).rounded())))
            let c: UInt8 = dark ? 0 : a
            bytes[i * 4] = c
            bytes[i * 4 + 1] = c
            bytes[i * 4 + 2] = c
            bytes[i * 4 + 3] = a
        }
        guard let provider = CGDataProvider(data: Data(bytes) as CFData) else { return nil }
        return CGImage(
            width: size, height: size, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: size * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
            provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent
        )
    }()
}

/// Downloads a photo once and keeps it decoded.
enum VelocityMarqueePhotos {
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

/// A number moved by Reanimated-style timings: a delay, then a curve from wherever it was.
struct VelocityMarqueeTimed {
    private(set) var value: Double
    private var from = 0.0
    private var to = 0.0
    private var time = 0.0
    private var duration = 0.0
    private var wait = 0.0
    private var curve: (Double) -> Double = { $0 }
    private var running = false

    init(_ value: Double) { self.value = value }

    mutating func animate(to target: Double, duration: Double, delay: Double = 0, curve: @escaping (Double) -> Double) {
        from = value
        to = target
        time = 0
        wait = delay
        self.duration = duration
        self.curve = curve
        running = true
    }

    mutating func step(_ dt: Double) {
        guard running else { return }
        if wait > 0 {
            wait -= dt
            if wait > 0 { return }
            // The timing starts from the value as it is when the delay ends.
            from = value
        }
        time += dt
        let p = min(1, time / max(duration, 0.0001))
        value = from + (to - from) * curve(p)
        if p >= 1 { running = false }
    }
}

/// Everything one frame draws and lays out.
struct VelocityMarqueeFrame {
    var travel: Double
    var lean: Double
    var elapsed: Double
    var page: Double
    var hero: Double
    /// The brand line's, the dots', the text's, the button's and the sign-in line's entrance (0 … 1).
    var enter: [Double]
}

/// The motion, stepped once a frame like the RN frame callback, and the drag that throws it.
/// Nothing in it is observed: the timeline redraws every frame anyway.
@MainActor
final class VelocityMarqueeEngine {
    private typealias M = VelocityMarqueeMetrics

    private(set) var rows: [VelocityMarqueeRow] = []
    private(set) var reference = 1.0
    // The script's fling: the peak nearest `M.fling` that, with the drift, carries the rows a whole
    // number of row lengths per loop. Each swipe also counts the tail of its own fling from the
    // loop before, so the speed is the same either side of the seam.
    private var fling = 0.0
    private var configuredFor: [Int]?
    // Distance travelled, in pt of the reference row (row one moves with it), and the speed above
    // the drift (pt/s). The drift and every swipe to the next page run leftwards: negative.
    private(set) var travel = 0.0
    private(set) var boost = 0.0
    // The lean spring's position and speed, following `boost`.
    private(set) var lean = 0.0
    private var leanSpeed = 0.0
    private var dragging = false
    private(set) var elapsed = 0.0
    private(set) var loaded: [(image: UIImage, at: Double)?] = []
    // The release spring on `boost`: its starting speed and how long it has run, or nil.
    private var release: (from: Double, time: Double)?
    // The page as a place: 1.5 is halfway from the second page to the third. The script runs it
    // round past the last page, so it is read the short way round.
    private(set) var page = 0.0
    private var count = 1
    // The page's own animation after a hand turns it: a spring (or, under reduced motion, a fade)
    // from where it was to the page it lands on.
    private var pageMove: (from: Double, to: Double, time: Double)?
    // autoPlay: the loop's clock, the travel and the page it started from, and the pause after a touch.
    private var clock = 0.0
    private var from = 0.0
    private var pageFrom = 0.0
    private var looping = false
    private var hold = 0.0
    private var auto: Bool?
    private(set) var reduced = false
    private var last: Date?
    // The drag: whether it has passed its 2 pt slop, and the last translation seen.
    private var active = false
    private var lastTranslation = CGSize.zero
    // The entrance, started once the screen has a size.
    private var hero = VelocityMarqueeTimed(0)
    private var enter = (0..<5).map { _ in VelocityMarqueeTimed(0) }
    private var entered = false

    var onPage: ((Int) -> Void)?
    private let selection = UISelectionFeedbackGenerator()

    func configure(rows photos: [[VelocityMarqueePhoto]], pages: Int, autoPlay: Bool, reducedMotion: Bool) {
        let shape = photos.map(\.count)
        if configuredFor != shape {
            configuredFor = shape
            rows = VelocityMarqueeRow.make(photos)
            // Rows of different lengths travel at speeds in proportion, so all of them repeat together.
            reference = max(1, rows.reduce(0) { $0 + $1.length } / Double(max(1, rows.count)))
            let area = M.swipes.reduce(0) { $0 + M.flingArea(2 * M.loop - $1) }
            let laps = max(1, M.round((M.drift * M.loop + M.fling * area) / reference))
            fling = max(0, (laps * reference - M.drift * M.loop) / max(0.001, area))
            loaded = Array(repeating: nil, count: shape.reduce(0, +))
        }
        count = max(1, pages)
        reduced = reducedMotion
        let next = autoPlay && !reducedMotion
        if auto != next {
            auto = next
            looping = next
            clock = 0
            from = travel
            pageFrom = restingPage()
            hold = 0
        }
    }

    /// The page the place is nearest, as an index.
    private func restingPage() -> Double {
        let n = Double(count)
        return M.round(M.wrap(page, n)).truncatingRemainder(dividingBy: n)
    }

    func photoArrived(_ image: UIImage, at index: Int) {
        guard loaded.indices.contains(index) else { return }
        loaded[index] = (image, elapsed)
    }

    /// The rows rise and fade in once everything has a place; the foot follows them in.
    func enterOnce() {
        guard !entered else { return }
        entered = true
        let easeOut: (Double) -> Double = { Ease.out.value(at: $0) }
        hero.animate(to: 1, duration: M.heroFade, curve: easeOut)
        for i in enter.indices {
            let delay = i == 0 ? 0 : M.enterDelay + Double(i - 1) * M.stagger
            enter[i].animate(to: 1, duration: M.enterFade, delay: delay, curve: easeOut)
        }
    }

    /// Where the scripted loop has carried the rows by time t, and how fast above the drift.
    private func scripted(_ t: Double) -> (travel: Double, boost: Double) {
        var moved = -M.drift * t
        var speed = 0.0
        for at in M.swipes {
            moved -= fling * (M.flingArea(t - at) + M.flingArea(t + M.loop - at) - M.flingArea(M.loop - at))
            speed -= fling * (M.flingShape(t - at) + M.flingShape(t + M.loop - at))
        }
        return (moved, speed)
    }

    func step(to date: Date) -> VelocityMarqueeFrame {
        if let last {
            let dt = min(date.timeIntervalSince(last), 0.064)
            if dt > 0 { advance(dt) }
        }
        last = date
        return VelocityMarqueeFrame(travel: travel, lean: lean, elapsed: elapsed, page: page, hero: hero.value, enter: enter.map(\.value))
    }

    private func advance(_ dt: Double) {
        elapsed += dt
        // The release spring: critically damped, so boost = v0 (1 + ωt) e^(−ωt).
        if var spring = release {
            spring.time += dt
            let w = M.omega
            let value = spring.from * (1 + w * spring.time) * exp(-w * spring.time)
            let velocity = -spring.from * w * w * spring.time * exp(-w * spring.time)
            if abs(value) < 0.01 && abs(velocity) < 2 {
                boost = 0
                release = nil
            } else {
                boost = value
                release = spring
            }
        }
        // The page a hand turned: a critically damped spring from rest, or a fade under reduced motion.
        if var move = pageMove {
            move.time += dt
            if reduced {
                let p = min(1, move.time / M.pageFade)
                page = move.from + (move.to - move.from) * M.inOutQuad(p)
                pageMove = p >= 1 ? nil : move
            } else {
                let u = M.pageOmega * move.time
                let left = (1 + u) * exp(-u)
                if left < 1e-4 {
                    page = move.to
                    pageMove = nil
                } else {
                    page = move.to + (move.from - move.to) * left
                    pageMove = move
                }
            }
        }
        let drift = reduced ? 0 : -M.drift
        if hold > 0 && !dragging {
            hold -= dt
            if hold <= 0 {
                // The loop starts over from wherever the hand left the rows and the page.
                clock = 0
                from = travel
                pageFrom = restingPage()
            }
        }
        if looping && hold <= 0 && !dragging {
            let t = clock + dt
            if t >= M.loop {
                // One loop carries every row a whole number of lengths and the page round to where
                // it started, so this is the same picture as t = 0.
                from += scripted(M.loop).travel
                clock = t - M.loop
            } else {
                clock = t
            }
            let now = scripted(clock)
            travel = from + now.travel
            boost = now.boost
            release = nil
            pageMove = nil
            page = M.wrap(pageFrom + M.scriptedPage(clock), Double(count))
        } else if !dragging {
            travel += (drift + boost) * dt
        } else {
            // The finger moves the rows itself (see the drag); the drift carries on under it.
            travel += drift * dt
        }
        // Everything repeats every reference length, so the distance is kept small without a seam.
        let laps = (travel / reference / 64).rounded(.towardZero) * 64
        if laps != 0 && laps.isFinite {
            travel -= laps * reference
            from -= laps * reference
        }
        if !travel.isFinite { travel = 0 }
        if !boost.isFinite { boost = 0 }
        if !page.isFinite { page = 0 }
        // The lean chases the speed on its spring; under reduced motion nothing leans.
        let target = reduced ? 0 : boost
        let accel = M.leanStiffness * (target - lean) - M.leanDamping * leanSpeed
        leanSpeed += accel * dt
        lean += leanSpeed * dt
        if !lean.isFinite || !leanSpeed.isFinite {
            lean = 0
            leanSpeed = 0
        }
        hero.step(dt)
        for i in enter.indices { enter[i].step(dt) }
    }

    // MARK: Pages

    /// Turns to the page `step` away from the one shown, if there is one, with a tick.
    func turn(_ step: Int) {
        // Read the short way round, so a page caught mid-way back to the first settles without
        // passing through the others.
        let p = M.wrap(page + 0.5, Double(count)) - 0.5
        let current = min(max(M.round(p), 0), Double(count - 1))
        let next = min(max(current + Double(step), 0), Double(count - 1))
        page = p
        pageMove = (p, next, 0)
        if next != current {
            selection.selectionChanged()
            let index = Int(next)
            let report = onPage
            // Reported on the next turn of the main loop: this can run while a frame is drawn.
            DispatchQueue.main.async { report?(index) }
        }
    }

    // MARK: Touch

    func dragChanged(translation: CGSize, velocity: CGSize) {
        if !dragging {
            // Catching the rows stops the throw under the finger; the lean relaxes on its spring.
            dragging = true
            hold = M.userHold
            boost = 0
            release = nil
            active = false
            lastTranslation = .zero
        }
        if !active {
            if hypot(translation.width, translation.height) < 2 { return }
            active = true
        }
        let changeX = translation.width - lastTranslation.width
        let changeY = translation.height - lastTranslation.height
        lastTranslation = translation
        let along = changeX - changeY * M.verticalShare
        guard along.isFinite else { return }
        // Each row moves by the finger in its own direction; travel is measured on row one.
        travel += along
        let speed = velocity.width - velocity.height * M.verticalShare
        guard speed.isFinite else { return }
        let k = min(max(1 / 60 / M.dragSmooth, 0), 1)
        boost += (min(max(speed, -M.maxThrow), M.maxThrow) - boost) * k
    }

    func dragEnded(translation: CGSize, velocity: CGSize, width: Double) {
        if active {
            let raw = velocity.width - velocity.height * M.verticalShare
            let speed = raw.isFinite ? min(max(raw, -M.maxThrow), M.maxThrow) : 0
            // The throw carries the finger's speed and settles back to the drift on the spring.
            boost = reduced ? 0 : speed
            release = reduced ? nil : (speed, 0)
            // A sideways swipe turns the page: leftwards to the next one.
            let dx = translation.width.isFinite ? translation.width : 0
            let dy = translation.height.isFinite ? translation.height : 0
            let vx = velocity.width.isFinite ? velocity.width : 0
            let w = max(1, width)
            let sideways = abs(dx) > abs(dy)
            if sideways && (dx < -w * M.pageDistance || vx < -M.pageSpeed) {
                turn(1)
            } else if sideways && (dx > w * M.pageDistance || vx > M.pageSpeed) {
                turn(-1)
            }
        }
        dragging = false
        active = false
        hold = M.userHold
    }
}
