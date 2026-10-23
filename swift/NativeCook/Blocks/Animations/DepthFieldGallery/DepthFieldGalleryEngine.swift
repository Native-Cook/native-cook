import SwiftUI
import UIKit

enum DepthFieldGalleryMetrics {
    // The field: `field` cards through a tunnel `depth` units long that repeats behind itself, a
    // unit being about a card's width. Every `heroEvery`-th card floats near the middle as a stop
    // for the camera; the rest are scattered `spreadX` by `spreadY` units around them, clear of the
    // middle.
    static let field = 160
    static let heroEvery = 4
    static let depth = 64.0
    static let heroX = 0.8
    static let heroY = 1.3
    static let spreadX = 3.0
    static let spreadY = 4.8
    static let clearX = 1.0
    static let clearY = 1.7
    // Each card is turned by up to these angles (radians) and sways a little as it floats.
    static let yaw = 0.14
    static let pitch = 0.1
    static let roll = 0.035
    static let sway = 0.03
    // The lens: its focal length as a share of the screen's width. A card the camera stops at fills
    // `frameShare` of the width, a little off the middle. Cards fade in between `far` and `farFade`
    // units away and out between `nearFade` and `near`.
    static let focal = 0.95
    static let frameShare = 0.62
    static let frameOffX = 0.07
    static let frameOffY = 0.05
    static let far = 12.0
    static let farFade = 9.0
    static let near = 0.35
    static let nearFade = 0.8

    // The self-playing reel: the camera flies on to the next stop ahead over `flight` s on an
    // ease-in-out curve, arriving `drift` units short of it, then drifts in the rest of the way
    // over `hold` s; every `turnEvery` stops the lights turn over on the way.
    static let flight = 0.95
    static let hold = 0.9
    static let drift = 0.2
    static let driftSide = 0.06
    static let turnEvery = 6
    // Arrival: the camera starts `arrive` units back and flies in over `arriveTime` s.
    static let arrive = 3.0
    static let arriveTime = 1.5
    // By hand: a drag flies `dragRate` units per pt and pans `panRate` units per pt; on release the
    // camera flies to the stop nearest where the drag was heading `throwAhead` s ahead.
    static let dragRate = 0.014
    static let panRate = 0.006
    static let throwAhead = 0.25
    static let snapSpring = (duration: 0.75, dampingRatio: 0.9)
    static let flySpring = (duration: 0.95, dampingRatio: 0.92)
    static let userHold = 3.0
    static let panSlop = 4.0
    static let tapMaxDuration = 0.4
    static let reducedTime = 0.25

    // Turning the lights over by hand: the new scene spreads from the switch over `wipeTime` s.
    static let wipeTime = 0.9
    static let edgeFade = 64.0
    static let chromeDelay = 0.5
    static let chromeTime = 0.6
    static let captionIn = 0.35
    static let captionRise = 8.0
    static let imageFade = 0.4
    // How many full-size photos are kept: the card in view, the next one and the last two.
    static let largeKeep = 4
    // The photos' prints are a little harder than the originals.
    static let contrast = 1.2

    // The two scenes, and the inks of the cards.
    static let lightBg: UInt32 = 0xffffff
    static let lightInk: UInt32 = 0x111111
    static let darkBg: UInt32 = 0x0e0e0e
    static let darkInk: UInt32 = 0xf0f0ea
    static let cardInk: UInt32 = 0x1c1c1c
    static let markInk: UInt32 = 0x0b0b0b
    static let dotInk: UInt32 = 0xf4f4f0

    /// The switch, bottom right; the new scene spreads from its middle.
    static func switchCentre(width: Double, height: Double, bottom: Double) -> CGPoint {
        CGPoint(x: width - 20 - 22, y: height - bottom - 30)
    }

    /// How far the spreading scene has to reach to cover the screen.
    static func reach(width: Double, height: Double, bottom: Double) -> Double {
        let s = switchCentre(width: width, height: height, bottom: bottom)
        return hypot(max(s.x, width - s.x), max(s.y, height - s.y))
    }

    static func clamp01(_ value: Double) -> Double { min(1, max(0, value)) }

    static func mod(_ value: Double, _ by: Double) -> Double {
        let r = value.truncatingRemainder(dividingBy: by)
        return (r + by).truncatingRemainder(dividingBy: by)
    }
}

/// Where one card floats in the field. Sizes are in units, angles in radians.
struct DepthFieldGalleryPlacement {
    var x: Double
    var y: Double
    var z: Double
    var width: Double
    var aspect: Double
    var yaw: Double
    var pitch: Double
    var roll: Double
    var card: Int
    var hero: Bool
    var frameX: Double
    var frameY: Double
}

struct DepthFieldGalleryCamera {
    var x: Double
    var y: Double
    var z: Double
}

/// A card the camera sees: the perspective matrix that maps its face (in hundredths of a unit
/// about its middle) onto the screen, row by row for points (u, v, 1); its corners there; and how
/// faded it is.
struct DepthFieldGalleryShown {
    var index: Int
    var depth: Double
    var matrix: [Double]
    var quad: [Double]
    var alpha: Double

    /// The matrix as SwiftUI wants it, for a face laid out `unit` points per unit from its top left.
    /// ProjectionTransform multiplies row vectors, so the matrix is transposed, after moving the
    /// face's points into hundredths of a unit about its middle.
    func projection(unit: Double, placement p: DepthFieldGalleryPlacement) -> ProjectionTransform {
        let s = 100 / unit
        let hw = p.width * 50
        let hh = p.width * p.aspect * 50
        let a = matrix
        var t = ProjectionTransform()
        t.m11 = a[0] * s
        t.m21 = a[1] * s
        t.m31 = a[2] - hw * a[0] - hh * a[1]
        t.m12 = a[3] * s
        t.m22 = a[4] * s
        t.m32 = a[5] - hw * a[3] - hh * a[4]
        t.m13 = a[6] * s
        t.m23 = a[7] * s
        t.m33 = a[8] - hw * a[6] - hh * a[7]
        return t
    }
}

/// The field, the lens and the camera's framing, as the RN version computes them.
enum DepthFieldGalleryField {
    private typealias M = DepthFieldGalleryMetrics

    /// A small seeded generator (mulberry32), so the field is the same every time.
    struct Random {
        private var s: UInt32

        init(_ seed: UInt32) {
            s = seed
        }

        mutating func next() -> Double {
            s = s &+ 0x6d2b_79f5
            var t = s
            t = (t ^ (t >> 15)) &* (t | 1)
            t ^= t &+ ((t ^ (t >> 7)) &* (t | 61))
            return Double(t ^ (t >> 14)) / 4_294_967_296
        }
    }

    private static func gcd(_ a: Int, _ b: Int) -> Int { b == 0 ? a : gcd(b, a % b) }

    /// The first step from `from` up that walks through all `n` cards before it repeats one.
    static func coprime(_ n: Int, _ from: Int) -> Int {
        var step = from
        while gcd(step, n) != 1 { step += 1 }
        return step
    }

    static func scatter(_ cards: [DepthFieldGalleryCard]) -> [DepthFieldGalleryPlacement] {
        guard !cards.isEmpty else { return [] }
        var r = Random(11)
        let heroStep = coprime(cards.count, 7)
        let restStep = coprime(cards.count, 5)
        return (0..<M.field).map { i in
            let hero = i % M.heroEvery == 0
            // The stops take every card in turn; the cards around them are dealt from a different shuffle.
            let card = hero ? ((i / M.heroEvery) * heroStep) % cards.count : (i * restStep + 3) % cards.count
            let size = cards[card].size
            var x = (r.next() * 2 - 1) * M.heroX
            var y = (r.next() * 2 - 1) * M.heroY
            if !hero {
                repeat {
                    x = (r.next() * 2 - 1) * M.spreadX
                    y = (r.next() * 2 - 1) * M.spreadY
                } while abs(x) < M.clearX && abs(y) < M.clearY
            }
            // In the order the RN object literal draws its numbers.
            let z = (Double(i) + r.next() * 0.4) * M.depth / Double(M.field)
            let width = size.width * (hero ? 1 : 0.55 + r.next() * 0.5)
            let yaw = (r.next() * 2 - 1) * M.yaw
            let pitch = (r.next() * 2 - 1) * M.pitch
            let roll = (r.next() * 2 - 1) * M.roll
            let frameX = (r.next() * 2 - 1) * M.frameOffX
            let frameY = (r.next() * 2 - 1) * M.frameOffY
            return DepthFieldGalleryPlacement(
                x: x, y: y, z: z, width: width, aspect: size.aspect,
                yaw: yaw, pitch: pitch, roll: roll, card: card, hero: hero,
                frameX: frameX, frameY: frameY
            )
        }
    }

    /// Where the camera stands to frame a card: far enough back for it to fill `frameShare` of the
    /// width, a little off the middle, at the copy of the tunnel nearest `near` (or the next one ahead).
    static func framing(_ p: DepthFieldGalleryPlacement, width: Double, height: Double, near: Double, ahead: Bool) -> DepthFieldGalleryCamera {
        let distance = M.focal * p.width / M.frameShare
        let gap = p.z - distance - near
        return DepthFieldGalleryCamera(
            x: p.x - p.frameX * distance / M.focal,
            y: p.y - p.frameY * height / width * (distance / M.focal),
            z: near + (ahead ? M.mod(gap, M.depth) : M.mod(gap + M.depth / 2, M.depth) - M.depth / 2)
        )
    }

    /// Every card the camera can see, far to near.
    static func frame(_ field: [DepthFieldGalleryPlacement], cam: DepthFieldGalleryCamera, width: Double, height: Double, now: Double, sway: Double) -> [DepthFieldGalleryShown] {
        var shown: [DepthFieldGalleryShown] = []
        let f = width * M.focal
        for (i, p) in field.enumerated() {
            let depth = M.mod(p.z - cam.z, M.depth)
            if depth < M.near || depth > M.far { continue }
            let wave = sway * sin(now * 0.7 + Double(i) * 1.7)
            let cx = p.x - cam.x
            let cy = p.y - cam.y + wave * M.sway
            let cz = depth
            let yaw = p.yaw + wave * 0.03
            // The card's axes after it turns, and the plane they span projected through the lens.
            let cY = cos(yaw)
            let sY = sin(yaw)
            let cP = cos(p.pitch)
            let sP = sin(p.pitch)
            let cR = cos(p.roll)
            let sR = sin(p.roll)
            let k = 1.0 / 100
            let ax = (cY * cR + sY * sP * sR) * k
            let ay = cP * sR * k
            let az = (-sY * cR + cY * sP * sR) * k
            let bx = (-cY * sR + sY * sP * cR) * k
            let by = cP * cR * k
            let bz = (sY * sR + cY * sP * cR) * k
            let mx = width / 2
            let my = height / 2
            let matrix = [
                (mx * az + f * ax) / cz,
                (mx * bz + f * bx) / cz,
                (mx * cz + f * cx) / cz,
                (my * az + f * ay) / cz,
                (my * bz + f * by) / cz,
                (my * cz + f * cy) / cz,
                az / cz,
                bz / cz,
                1,
            ]
            let w = p.width * 50
            let h = p.width * p.aspect * 50
            var quad: [Double] = []
            quad.reserveCapacity(8)
            var behind = false
            for (u, v) in [(-w, -h), (w, -h), (w, h), (-w, h)] {
                let z = matrix[6] * u + matrix[7] * v + 1
                if z <= 0.05 { behind = true }
                quad.append((matrix[0] * u + matrix[1] * v + matrix[2]) / z)
                quad.append((matrix[3] * u + matrix[4] * v + matrix[5]) / z)
            }
            if behind { continue }
            let left = min(quad[0], quad[2], quad[4], quad[6])
            let right = max(quad[0], quad[2], quad[4], quad[6])
            let top = min(quad[1], quad[3], quad[5], quad[7])
            let bottom = max(quad[1], quad[3], quad[5], quad[7])
            if right < 0 || left > width || bottom < 0 || top > height { continue }
            let alpha = M.clamp01((M.far - depth) / (M.far - M.farFade)) * M.clamp01((depth - M.near) / (M.nearFade - M.near))
            shown.append(DepthFieldGalleryShown(index: i, depth: depth, matrix: matrix, quad: quad, alpha: alpha))
        }
        // JS's sort is stable, as Swift's is not promised to be; ties keep field order.
        return shown.enumerated()
            .sorted { $0.element.depth != $1.element.depth ? $0.element.depth > $1.element.depth : $0.offset < $1.offset }
            .map(\.element)
    }

    /// Whether a point lies inside a convex quad given corner by corner.
    static func inside(_ quad: [Double], _ x: Double, _ y: Double) -> Bool {
        var sign = 0.0
        for i in 0..<4 {
            let j = (i + 1) % 4
            let cross = (quad[j * 2] - quad[i * 2]) * (y - quad[i * 2 + 1]) - (quad[j * 2 + 1] - quad[i * 2 + 1]) * (x - quad[i * 2])
            if cross == 0 { continue }
            if sign == 0 {
                sign = cross > 0 ? 1 : -1
            } else if (cross > 0 ? 1 : -1) != sign {
                return false
            }
        }
        return true
    }
}

/// A photo as it arrived, printed, with the block's clock when it did, for its fade.
struct DepthFieldGalleryLoaded {
    var image: UIImage
    var at: Double
}

/// Everything one frame draws.
struct DepthFieldGalleryFrame {
    var shown: [DepthFieldGalleryShown]
    var elapsed: Double
    /// 0 light … 1 dark.
    var theme: Double
    var chrome: Double
    var focus: Int
    var lastFocus: Int
    var focusAt: Double
    var photos: [DepthFieldGalleryLoaded?]
    /// The full-size copies, by card.
    var sharp: [(card: Int, loaded: DepthFieldGalleryLoaded)]
}

struct DepthFieldGallerySettings {
    var cards: [DepthFieldGalleryCard]
    var autoPlay: Bool
    var reducedMotion: Bool
    var accent: UInt32
    var onFocus: ((DepthFieldGalleryCard) -> Void)?
}

/// The camera and the scene, stepped once a frame like the RN frame callback, and the gestures
/// that move them.
@MainActor
final class DepthFieldGalleryEngine {
    private typealias M = DepthFieldGalleryMetrics
    private typealias F = DepthFieldGalleryField

    private(set) var field: [DepthFieldGalleryPlacement] = []
    // The cards the camera stops at.
    private var stops: [Int] = []
    private var cardCount = -1
    private var width = 0.0
    private var height = 0.0
    private var bottom = 0.0
    // Seconds since the field first appeared.
    private var elapsed = 0.0
    private var photos: [DepthFieldGalleryLoaded?] = []
    private var sharp: [(card: Int, loaded: DepthFieldGalleryLoaded)] = []
    // The cards whose full-size copy is on its way.
    private var fetching = Set<Int>()
    // The camera, in units; its depth runs on for ever and the field repeats around it.
    private var camX = DepthFieldGalleryValue(0)
    private var camY = DepthFieldGalleryValue(0)
    private var camZ = DepthFieldGalleryValue(0)
    private var fromX = 0.0
    private var fromZ = 0.0
    // The card the camera is on, and when it got there, for the caption; and the one before it.
    private var focus = 0
    private var focusAt = 0.0
    private var lastFocus = 0
    // 0 light, 1 dark, and where it is headed.
    private var theme = DepthFieldGalleryValue(0)
    private var darkOn = false
    // The montage: time into the current shot, and how many shots so far.
    private var shotClock = 0.0
    private var shots = 0
    private var looping = false
    private var hold = 0.0
    private var started = false
    private var lastDate: Date?
    private var lastAuto: Bool?
    private var settings = DepthFieldGallerySettings(cards: [], autoPlay: false, reducedMotion: false, accent: 0xd8f05a)
    // The touch under way: when and where it began, and whether it became a pan.
    private var touchStart: Date?
    private var touchOrigin = CGPoint.zero
    private var panning = false

    private let selection = UISelectionFeedbackGenerator()
    private let impact = UIImpactFeedbackGenerator(style: .light)

    init(dark: Bool) {
        darkOn = dark
        theme.set(dark ? 1 : 0)
    }

    /// Scatters the field for these cards, once.
    func prepare(_ cards: [DepthFieldGalleryCard]) {
        guard cardCount != cards.count else { return }
        cardCount = cards.count
        field = F.scatter(cards)
        stops = field.indices.filter { field[$0].hero }
        photos = Array(repeating: nil, count: cards.count)
        sharp = []
        fetching = []
        focus = stops.first ?? 0
        lastFocus = focus
    }

    // MARK: Photos

    func photoArrived(_ image: UIImage, card: Int) {
        guard photos.indices.contains(card) else { return }
        photos[card] = DepthFieldGalleryLoaded(image: image, at: elapsed)
    }

    // Fetches the full-size copies of these cards' photos, keeping only the last few.
    private func sharpen(_ wanted: [Int]) {
        let cards = settings.cards
        let accent = settings.accent
        for card in wanted {
            guard cards.indices.contains(card), let full = cards[card].photo?.full else { continue }
            if fetching.contains(card) || sharp.contains(where: { $0.card == card }) { continue }
            fetching.insert(card)
            let print = cards[card].print
            let count = cards.count
            Task { @MainActor [weak self] in
                let image = await DepthFieldGalleryPhotos.load(full, print: print, accent: accent, cached: false)
                guard let self else { return }
                self.fetching.remove(card)
                guard let image, self.cardCount == count else { return }
                self.sharp.removeAll { $0.card == card }
                self.sharp.append((card, DepthFieldGalleryLoaded(image: image, at: self.elapsed)))
                if self.sharp.count > M.largeKeep { self.sharp.removeFirst(self.sharp.count - M.largeKeep) }
            }
        }
    }

    // The card at a stop and the one at the stop after it.
    private func sharpenAt(_ index: Int) {
        let k = stops.firstIndex(of: index)
        let after = k.map { stops[($0 + 1) % stops.count] } ?? index
        sharpen([field[index].card, field[after].card])
    }

    // MARK: Frame

    func step(to date: Date, width: Double, height: Double, bottom: Double, settings: DepthFieldGallerySettings) -> DepthFieldGalleryFrame {
        self.settings = settings
        self.width = width
        self.height = height
        self.bottom = bottom
        prepare(settings.cards)
        if lastAuto != settings.autoPlay {
            lastAuto = settings.autoPlay
            looping = settings.autoPlay
            shotClock = 0
            hold = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(raw, 0.064)
        if dt > 0 && width > 0 && !field.isEmpty {
            advance(dt: dt)
        }

        let cam = DepthFieldGalleryCamera(x: camX.value, y: camY.value, z: camZ.value)
        let shown = width > 0 ? F.frame(field, cam: cam, width: width, height: height, now: elapsed, sway: settings.reducedMotion ? 0 : 1) : []
        // The labels come in a moment after the field, like `withDelay` before a timing.
        let chromeP = M.clamp01((elapsed - M.chromeDelay) / M.chromeTime)
        return DepthFieldGalleryFrame(
            shown: shown,
            elapsed: elapsed,
            theme: theme.value,
            chrome: DepthFieldGalleryCurve.easeOut(chromeP),
            focus: focus,
            lastFocus: lastFocus,
            focusAt: focusAt,
            photos: photos,
            sharp: sharp
        )
    }

    private func advance(dt: Double) {
        let now = elapsed + dt
        elapsed = now
        camX.step(dt: dt, now: now)
        camY.step(dt: dt, now: now)
        camZ.step(dt: dt, now: now)
        theme.step(dt: dt, now: now)
        if !started {
            // Arrival: the camera starts well back and flies in on the first card.
            started = true
            let first = stops[0]
            let to = F.framing(field[first], width: width, height: height, near: 0, ahead: true)
            camX.set(to.x)
            camY.set(to.y)
            camZ.set(to.z - (settings.reducedMotion ? 0 : M.arrive))
            glideTo(first, flight: M.arriveTime)
            sharpenAt(first)
            shotClock = -M.arriveTime
            return
        }
        if hold > 0 {
            hold -= dt
            if hold <= 0 { shotClock = 0 }
            return
        }
        guard looping else { return }
        let t = shotClock + dt
        if t < M.hold {
            shotClock = t
            return
        }
        shotClock = -M.flight
        shots += 1
        if shots % M.turnEvery == 0 { turn(user: false) }
        glideTo(nextStop(), flight: M.flight)
    }

    // MARK: The camera

    private func setFocus(_ index: Int) {
        if index == focus { return }
        sharpenAt(index)
        lastFocus = focus
        focusAt = elapsed
        focus = index
    }

    private func turn(user: Bool) {
        darkOn.toggle()
        theme.timing(to: darkOn ? 1 : 0, duration: settings.reducedMotion ? 0.25 : M.wipeTime, curve: DepthFieldGalleryCurve.easeInOut, now: elapsed)
        if user { selection.selectionChanged() }
    }

    private func settle(to: DepthFieldGalleryCamera) {
        camX.timing(to: to.x, duration: M.reducedTime, curve: DepthFieldGalleryCurve.easeOut, now: elapsed)
        camY.timing(to: to.y, duration: M.reducedTime, curve: DepthFieldGalleryCurve.easeOut, now: elapsed)
        camZ.timing(to: to.z, duration: M.reducedTime, curve: DepthFieldGalleryCurve.easeOut, now: elapsed)
    }

    // Sends the camera to frame a card on a spring, carrying the finger's speed.
    private func flyTo(_ index: Int, spring: (duration: Double, dampingRatio: Double), vx: Double, vz: Double) {
        let to = F.framing(field[index], width: width, height: height, near: camZ.value, ahead: false)
        setFocus(index)
        if settings.reducedMotion {
            settle(to: to)
            return
        }
        camX.spring(to: to.x, duration: spring.duration, dampingRatio: spring.dampingRatio, velocity: vx)
        camY.spring(to: to.y, duration: spring.duration, dampingRatio: spring.dampingRatio)
        camZ.spring(to: to.z, duration: spring.duration, dampingRatio: spring.dampingRatio, velocity: vz)
    }

    // The reel: the camera flies on to a card, arriving a little short and to one side, and drifts
    // in the rest of the way while it holds.
    private func glideTo(_ index: Int, flight: Double) {
        let to = F.framing(field[index], width: width, height: height, near: camZ.value, ahead: true)
        let side = sin(Double(index) * 12.9898) * M.driftSide
        setFocus(index)
        if settings.reducedMotion {
            settle(to: to)
            return
        }
        let fly = DepthFieldGalleryCurve.easeInOut
        let drift = DepthFieldGalleryCurve.outQuad
        let driftTime = M.hold + 0.2
        camX.sequence([(to.x - side, flight, fly), (to.x, driftTime, drift)], now: elapsed)
        camY.timing(to: to.y, duration: flight, curve: fly, now: elapsed)
        camZ.sequence([(to.z - M.drift, flight, fly), (to.z, driftTime, drift)], now: elapsed)
    }

    // The next stop ahead of the camera.
    private func nextStop() -> Int {
        var best = stops[0]
        var gap = Double.infinity
        for index in stops {
            let ahead = F.framing(field[index], width: width, height: height, near: camZ.value, ahead: true).z - camZ.value
            if ahead > M.drift * 2 && ahead < gap {
                gap = ahead
                best = index
            }
        }
        return best
    }

    private func touched() {
        hold = M.userHold
    }

    // MARK: Gestures

    // One recogniser stands in for the race of a one-finger pan (after 4 pt of travel) and a tap
    // (a touch lifted within 400 ms that never travelled that far).
    func touchMoved(_ value: DragGesture.Value) {
        guard started else { return }
        if touchStart == nil || value.startLocation != touchOrigin {
            touchStart = value.time
            touchOrigin = value.startLocation
            panning = false
        }
        if !panning {
            guard hypot(value.translation.width, value.translation.height) >= M.panSlop else { return }
            panning = true
            touched()
            // Caught wherever the camera is.
            camX.set(camX.value)
            camY.set(camY.value)
            camZ.set(camZ.value)
            fromX = camX.value
            fromZ = camZ.value
        }
        // Up flies on, down flies back; sideways pans, the cards following the finger.
        camZ.set(fromZ - value.translation.height * M.dragRate)
        camX.set(fromX - value.translation.width * M.panRate)
    }

    func touchEnded(_ value: DragGesture.Value) {
        defer {
            touchStart = nil
            panning = false
        }
        guard started else { return }
        if panning {
            panEnded(velocity: value.velocity)
            return
        }
        guard let start = touchStart, value.time.timeIntervalSince(start) <= M.tapMaxDuration, width > 0 else { return }
        tap(at: value.location)
    }

    private func panEnded(velocity: CGSize) {
        touched()
        let vz = -velocity.height * M.dragRate
        let vx = -velocity.width * M.panRate
        // The stop nearest where the drag was heading.
        let aimZ = camZ.value + vz * M.throwAhead
        let aimX = camX.value + vx * M.throwAhead
        var best = stops[0]
        var score = Double.infinity
        for index in stops {
            let to = F.framing(field[index], width: width, height: height, near: aimZ, ahead: false)
            let off = abs(to.z - aimZ) + abs(to.x - aimX) * 0.5
            if off < score {
                score = off
                best = index
            }
        }
        flyTo(best, spring: M.snapSpring, vx: vx, vz: vz)
        focused(best)
    }

    private func tap(at point: CGPoint) {
        touched()
        let s = M.switchCentre(width: width, height: height, bottom: bottom)
        if hypot(point.x - s.x, point.y - s.y) < 30 {
            turn(user: true)
            return
        }
        // The nearest card under the finger.
        let cam = DepthFieldGalleryCamera(x: camX.value, y: camY.value, z: camZ.value)
        let shown = F.frame(field, cam: cam, width: width, height: height, now: elapsed, sway: settings.reducedMotion ? 0 : 1)
        for item in shown.reversed() where item.alpha > 0.3 && F.inside(item.quad, point.x, point.y) {
            flyTo(item.index, spring: M.flySpring, vx: 0, vz: 0)
            impact.impactOccurred()
            focused(item.index)
            return
        }
    }

    private func focused(_ index: Int) {
        settings.onFocus?(settings.cards[field[index].card])
    }

    /// Turns the lights over, for the accessibility action.
    func turnByHand() {
        turn(user: true)
    }
}
