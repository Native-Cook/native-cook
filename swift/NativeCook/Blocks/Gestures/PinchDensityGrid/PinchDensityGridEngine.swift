import SwiftUI
import UIKit

/// One plate of the herbarium: an Unsplash photo id, its name and a line under it.
struct PinchDensityGridPlate {
    var id: String
    var name: String
    var note: String

    static func uri(_ id: String, width: Int) -> String {
        "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(width)&q=80"
    }

    private static func plate(_ id: String, _ name: String, _ note: String) -> Self {
        Self(id: "photo-\(id)", name: name, note: note)
    }

    // Leaves, bouquets and tulips, dealt so neighbours differ.
    static let samples: [Self] = [
        plate("1503149779833-1de50ebe5f8a", "Shade Leaf", "Monstera deliciosa"),
        plate("1582794543139-8ac9cb0f7b11", "Blush", "Paeonia lactiflora"),
        plate("1554494583-c4e1649bfe71", "Open Sky", "Tulipa, red"),
        plate("1491147334573-44cbb4602074", "Pinstripe", "Calathea orbifolia"),
        plate("1572454591674-2739f30d8c40", "Apricot", "Garden roses"),
        plate("1586968295564-92fd7572718b", "On Cedar", "Tulipa, pink"),
        plate("1446071103084-c257b5f70672", "Rosette", "Echeveria"),
        plate("1561181286-d3fee7d55364", "Glass Vase", "Tulipa, mixed"),
        plate("1587316830148-c9b01df2da38", "Peach Light", "Tulipa, apricot"),
        plate("1598764557991-b9f211b73b81", "Split Leaf", "Monstera deliciosa"),
        plate("1457089328109-e5d9bd499191", "Ember Posy", "Ranunculus, anemone"),
        plate("1488928741225-2aaf732c96cc", "Field Rows", "Tulipa, mixed"),
        plate("1477554193778-9562c28588c0", "Rubber Fig", "Ficus elastica"),
        plate("1567696153798-9111f9cd3d0d", "Kraft Wrap", "Sunflower, eucalyptus"),
        plate("1614791199038-6869a104fe5f", "Single Stem", "Tulipa"),
        plate("1543459176-4426b37223ba", "Understory", "Monstera and fern"),
        plate("1533616688419-b7a585564566", "Marmalade", "Roses and stock"),
        plate("1525898939552-346875c89d92", "Soft Focus", "Tulipa, spring"),
        plate("1525498128493-380d1990a112", "White Room", "Monstera, potted"),
        plate("1589095181425-c038b3871b6a", "Handed Over", "Spray roses"),
        plate("1520763185298-1b434c919102", "Pink Cup", "Tulipa, pink"),
        plate("1530049478161-0780526964f4", "Night Frond", "Monstera deliciosa"),
        plate("1487530811176-3780de880c2d", "Dusk Bouquet", "Roses, wax flower"),
        plate("1559150180-2467f804f9bf", "Open Field", "Tulipa, mixed"),
        plate("1493552152660-f915ab47ae9d", "Window Fig", "Ficus elastica"),
        plate("1685613858397-64f79a0f3603", "Market Stall", "Carnations, daisies"),
        plate("1518701005037-d53b1f67bb1c", "Mint", "Tulipa, white"),
        plate("1525923838299-2312b60f6d69", "Palm Fan", "Chamaedorea"),
        plate("1531120364508-a6b656c3e78d", "Meadow", "Sunflower, cornflower"),
        plate("1561181226-e8a7edd504c6", "Water Glass", "Tulipa, pink"),
        plate("1605449670493-ca1d812d0488", "First Leaf", "Monstera, young"),
        plate("1615385639736-362b69696227", "Paper Cone", "Tulipa, red"),
        plate("1468327768560-75b778cbb551", "Flame", "Tulipa, flamed"),
        plate("1604866830513-d54766457f45", "Olive Pot", "Monstera, potted"),
        plate("1583228858294-6745cb25969e", "Pale Gift", "Roses, gypsophila"),
        plate("1586554978186-deffc54a0a5c", "Shadow", "Tulipa, pink"),
    ]
}

enum PinchDensityGridMetrics {
    static let paper: UInt32 = 0xeeece4
    static let ink: UInt32 = 0x1e2a20
    static let tileTone: UInt32 = 0xdcdccf

    // Every plate is fetched small for the dense grids (a 5 column tile is ~80 pt, a 3 column one
    // ~125 pt); the ones on screen at 2 or 1 columns are fetched again at `sharpWidth` px. Sharp
    // copies are kept for the plates within `sharpKeep` of the screen and dropped beyond.
    static let thumbWidth = 420
    static let sharpWidth = 1080
    static let sharpAhead = 2
    static let sharpKeep = 6

    /// A rung of the column ladder: the margin and gap (pt), the corner radius (pt), the tile's
    /// height over its width, and the room left under it for the label.
    struct Rung {
        var cols: Int
        var margin: Double
        var gap: Double
        var radius: Double
        var aspect: Double
        var label: Double
    }

    // The column ladder a pinch walks along, densest first.
    static let ladder = [
        Rung(cols: 5, margin: 2, gap: 2, radius: 3, aspect: 1.25, label: 0),
        Rung(cols: 3, margin: 10, gap: 6, radius: 8, aspect: 1.25, label: 0),
        Rung(cols: 2, margin: 16, gap: 12, radius: 12, aspect: 1.25, label: 44),
        Rung(cols: 1, margin: 24, gap: 26, radius: 18, aspect: 1.25, label: 56),
    ]
    static let last = ladder.count - 1
    static let logCols = ladder.map { log(Double($0.cols)) }
    // The rung the grid opens on, and the two a double tap toggles between.
    static let startRung = 1.0
    static let oneColumn = 3
    static let threeColumns = 1

    // A pinch moves along the ladder in log columns: spreading the fingers by a factor s divides the
    // column count by s^`pinchGain`, so 5 → 1 takes a spread of about 3.4×.
    static let pinchGain = 1.3
    // Past either end the pinch keeps following with resistance, never further than `overpinch`
    // rungs, which grow or shrink the whole grid about the fingers by `overpinchScale` per rung.
    static let overpinch = 0.3
    static let overpinchScale = 0.32
    // The tiles follow the fingers sideways during a pinch: a share of the anchor tile's drift, plus
    // the fingers' own movement, with resistance, never past `sidewaysMax` pt.
    static let sidewaysFollow = 0.5
    static let sidewaysMax = 60.0
    // Release: the pinch is thrown `project` s ahead at its speed before the nearest rung is chosen,
    // moving the landing rung by at most `projectMax`.
    static let project = 0.14
    static let projectMax = 0.6
    static let settleSpring = (duration: 0.52, dampingRatio: 0.8)
    static let scrollSpring = (duration: 0.52, dampingRatio: 1.0)
    // Scrolling past either end gives less the further it is pulled, never more than `rubberMax` pt.
    static let rubberMax = 90.0

    // Labels fade in between these rungs (2 columns reads them in full) and grow by `labelGrow` at 1.
    static let labelFrom = 1.35
    static let labelTo = 1.95
    static let labelGrow = 0.25
    static let imageFade = 0.35

    // autoPlay: a scripted pinch walks the ladder, each move taking `span` s on an ease-in-out, from
    // the 3 column grid back to it, every `loop` s. Two touch marks show the fingers while it moves.
    static let loop = 12.0
    static let script: [(at: Double, span: Double, to: Double)] = [(1.4, 1.2, 2), (3.8, 1.2, 3), (6.6, 1.7, 0), (9.8, 1.2, 1)]
    // Where the loop stands at rest on each rung, for picking it up after a touch.
    static let scriptRest = [8.3, 0, 2.6, 5.0]
    static let userHold = 3.0
    // The anchored plate the script pinches over (its middle stays under the fingers).
    static let scriptAnchor = 7
    static let touchRadius = 21.0
    static let touchFade = 0.25

    // The header: the title band is `header` pt below the safe area, then the grid begins.
    static let header = 78.0
    static let edgeFade = 22.0

    static func clamp(_ value: Double, _ lo: Double, _ hi: Double) -> Double { min(hi, max(lo, value)) }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    /// Resistance past an edge: the further it is pulled, the less it gives, never past `max`.
    static func rubber(_ offset: Double, _ max: Double) -> Double {
        let sign: Double = offset < 0 ? -1 : 1
        return sign * max * (1 - 1 / (abs(offset) / max + 1))
    }

    /// `value` kept within lo..hi, with the part beyond either edge given back with resistance.
    static func rubberClamp(_ value: Double, _ lo: Double, _ hi: Double, _ max: Double) -> Double {
        if value < lo { return lo + rubber(value - lo, max) }
        if value > hi { return hi + rubber(value - hi, max) }
        return value
    }

    static func easeInOut(_ t: Double) -> Double { t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2 }
    /// Reanimated's default timing curve, ease in and out (quadratic).
    static func quadInOut(_ t: Double) -> Double { t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2 }
}

/// A plate's frame: x, y, width, height and corner radius.
struct PinchDensityGridFrame {
    var x = 0.0, y = 0.0, w = 0.0, h = 0.0, r = 0.0
}

/// The screen the grid lies on: its size, and where the grid starts and how much room it leaves below.
struct PinchDensityGridStage {
    var width = 0.0
    var height = 0.0
    var top = 0.0
    var bottom = 0.0
}

/// Everything a frame of the grid depends on besides the plate.
struct PinchDensityGridView {
    var stage: PinchDensityGridStage
    var z: Double
    var scroll: Double
    var sideways: Double
    var focalX: Double
    var focalY: Double
}

enum PinchDensityGridLayout {
    private typealias M = PinchDensityGridMetrics

    /// The lower rung, and how far towards the next one, for a place `z` on the ladder.
    static func split(_ z: Double) -> (k: Int, f: Double) {
        let zc = M.clamp(z, 0, Double(M.last))
        let k = min(Int(floor(zc)), M.last - 1)
        return (k, zc - Double(k))
    }

    /// Plate `i`'s frame on rung `k`, in content coordinates (before scrolling).
    static func slot(_ k: Int, _ i: Int, width: Double, top: Double) -> PinchDensityGridFrame {
        let r = M.ladder[k]
        let w = (width - r.margin * 2 - r.gap * Double(r.cols - 1)) / Double(r.cols)
        let h = w * r.aspect
        return PinchDensityGridFrame(
            x: r.margin + Double(i % r.cols) * (w + r.gap),
            y: top + Double(i / r.cols) * (h + r.label + r.gap),
            w: w, h: h, r: r.radius
        )
    }

    /// Plate `i`'s frame at `z`, between the two rungs on either side, in content coordinates.
    static func between(_ z: Double, _ i: Int, width: Double, top: Double) -> PinchDensityGridFrame {
        let (k, f) = split(z)
        let a = slot(k, i, width: width, top: top)
        let b = slot(k + 1, i, width: width, top: top)
        return PinchDensityGridFrame(x: M.mix(a.x, b.x, f), y: M.mix(a.y, b.y, f), w: M.mix(a.w, b.w, f), h: M.mix(a.h, b.h, f), r: M.mix(a.r, b.r, f))
    }

    /// The grid's full height on rung `k`, header and bottom margin included.
    static func gridLength(_ k: Int, _ n: Int, _ s: PinchDensityGridStage) -> Double {
        let r = M.ladder[k]
        let w = (s.width - r.margin * 2 - r.gap * Double(r.cols - 1)) / Double(r.cols)
        let rows = (n + r.cols - 1) / r.cols
        return s.top + Double(rows) * (w * r.aspect + r.label + r.gap) - r.gap + s.bottom
    }

    /// How far the grid can scroll at `z`.
    static func maxScroll(_ z: Double, _ n: Int, _ s: PinchDensityGridStage) -> Double {
        let (k, f) = split(z)
        return max(0, M.mix(gridLength(k, n, s), gridLength(k + 1, n, s), f) - s.height)
    }

    /// Where on the ladder a pinch is, from its log column count, with resistance past either end.
    static func rungForLog(_ logCols: Double) -> Double {
        let l = M.logCols
        if logCols >= l[0] {
            let over = (logCols - l[0]) / (l[0] - l[1])
            return -M.rubber(over, M.overpinch)
        }
        if logCols <= l[M.last] {
            let over = (l[M.last] - logCols) / (l[M.last - 1] - l[M.last])
            return Double(M.last) + M.rubber(over, M.overpinch)
        }
        for k in 0..<M.last where logCols <= l[k] && logCols >= l[k + 1] {
            return Double(k) + (l[k] - logCols) / (l[k] - l[k + 1])
        }
        return Double(M.last)
    }

    static func logForRung(_ z: Double) -> Double {
        let (k, f) = split(z)
        return M.mix(M.logCols[k], M.logCols[k + 1], f)
    }

    /// How many rungs one unit of log columns is worth around `z`.
    static func rungsPerLog(_ z: Double) -> Double {
        let (k, _) = split(z)
        return 1 / (M.logCols[k] - M.logCols[k + 1])
    }

    /// The column count at `z`, for the touch marks' spread.
    static func columnsAt(_ z: Double) -> Double { exp(logForRung(z)) }

    /// Plate `i`'s frame on screen: between the rungs, scrolled, slid sideways, and grown past the ends.
    static func onScreen(_ i: Int, _ v: PinchDensityGridView) -> PinchDensityGridFrame {
        var out = between(v.z, i, width: v.stage.width, top: v.stage.top)
        out.x += v.sideways
        out.y -= v.scroll
        let over = v.z < 0 ? v.z : v.z > Double(M.last) ? v.z - Double(M.last) : 0
        if over != 0 {
            let s = 1 + over * M.overpinchScale
            out.x = v.focalX + (out.x - v.focalX) * s
            out.y = v.focalY + (out.y - v.focalY) * s
            out.w *= s
            out.h *= s
        }
        return out
    }
}

struct PinchDensityGridPhoto {
    var image: UIImage
    /// The block's clock when it arrived, for its fade.
    var at: Double
}

/// One frame to draw.
struct PinchDensityGridScene {
    var view: PinchDensityGridView
    var now: Double
    var thumbs: [PinchDensityGridPhoto?]
    var sharp: [PinchDensityGridPhoto?]
    var alpha: Double
    /// The scripted pinch's clock while it plays, for its touch marks; nil when it does not.
    var scriptClock: Double?
}

struct PinchDensityGridSettings {
    var plates: [PinchDensityGridPlate]
    var autoPlay: Bool
    var reducedMotion: Bool
    var onColumnsChange: ((Int) -> Void)?
}

/// The grid's state, stepped once a frame like the RN frame callback, and the gestures that move it.
@MainActor
final class PinchDensityGridEngine {
    private typealias M = PinchDensityGridMetrics
    private typealias L = PinchDensityGridLayout

    private var stage = PinchDensityGridStage()
    private var count = 0
    private var now = 0.0
    private var thumbs: [PinchDensityGridPhoto?] = []
    private var sharp: [PinchDensityGridPhoto?] = []
    private var plates: [PinchDensityGridPlate] = []
    // Where the grid is on the ladder (0 = 5 columns … 3 = 1 column).
    private var z = PinchDensityGridValue(M.startRung)
    // The anchor: a point on one plate (in shares of its frame) that sits at screen height `focalY`.
    // Scrolling moves `focalY`; a pinch re-anchors under the fingers, so the plate stays under them.
    private var anchor = M.scriptAnchor
    private var anchorU = 0.5
    private var anchorV = 0.5
    private var focalX = 0.0
    private var focalY = PinchDensityGridValue(0)
    private var sideways = PinchDensityGridValue(0)
    private var ready = false
    // Gesture bookkeeping.
    private var pinching = false
    private var pinchLog = 0.0
    private var pinchFrom = (x: 0.0, y: 0.0, focalY: 0.0, anchorX: 0.0, sideways: 0.0)
    private var panFrom = 0.0
    // Under reduced motion the grid fades out, changes and fades in.
    private var fade = PinchDensityGridValue(1)
    private var fadedChange: (z: Double, focalY: Double)?
    // The self-playing loop.
    private var looping = false
    private var clock = 0.0
    private var hold = 0.0
    private var scriptFocal = -1.0
    private var wanted = (first: -1, last: -1)
    private var requested = Set<Int>()
    private var lastDate: Date?
    private var lastAuto: Bool?

    private let impact = UIImpactFeedbackGenerator(style: .light)

    // MARK: Photos

    func reset(plates: [PinchDensityGridPlate]) {
        self.plates = plates
        count = plates.count
        thumbs = Array(repeating: nil, count: count)
        sharp = Array(repeating: nil, count: count)
        requested = []
    }

    func thumbArrived(_ image: UIImage, at index: Int) {
        guard thumbs.indices.contains(index) else { return }
        thumbs[index] = PinchDensityGridPhoto(image: image, at: now)
    }

    /// Fetches the sharp copies of the plates on screen, and lets go of those far from it.
    private func wantSharp(first: Int, last: Int) {
        let keepFrom = first - M.sharpKeep
        let keepTo = last + M.sharpKeep
        for i in requested where i < keepFrom || i > keepTo {
            requested.remove(i)
            if sharp.indices.contains(i) { sharp[i] = nil }
        }
        let from = max(0, first - M.sharpAhead)
        let to = min(count - 1, last + M.sharpAhead)
        guard from <= to else { return }
        for i in from...to where !requested.contains(i) {
            requested.insert(i)
            let address = PinchDensityGridPlate.uri(plates[i].id, width: M.sharpWidth)
            Task { @MainActor [weak self] in
                let image = await PinchDensityGridPhotos.load(address, cached: false)
                guard let self else { return }
                guard let image, self.requested.contains(i), self.sharp.indices.contains(i) else {
                    if image == nil { self.requested.remove(i) }
                    return
                }
                self.sharp[i] = PinchDensityGridPhoto(image: image, at: self.now)
            }
        }
    }

    // MARK: Frame

    private func view() -> PinchDensityGridView {
        let zNow = z.value
        // The anchor point's height in content coordinates, and so the scroll that puts it at `focalY`.
        let frame = L.between(zNow, anchor, width: stage.width, top: stage.top)
        let raw = frame.y + frame.h * anchorV - focalY.value
        return PinchDensityGridView(
            stage: stage,
            z: zNow,
            scroll: M.rubberClamp(raw, 0, L.maxScroll(zNow, count, stage), M.rubberMax),
            sideways: sideways.value,
            focalX: focalX,
            focalY: focalY.value
        )
    }

    /// The `focalY` that keeps the anchor where it is at rung `to`, moved so the grid stays in range.
    private func focalFor(_ to: Double, _ focal: Double) -> Double {
        let frame = L.between(to, anchor, width: stage.width, top: stage.top)
        let y = frame.y + frame.h * anchorV
        return y - M.clamp(y - focal, 0, L.maxScroll(to, count, stage))
    }

    /// Re-anchors on the plate under (or nearest to) a point on screen, without moving anything.
    private func anchorAt(_ px: Double, _ py: Double) {
        let v = view()
        var best = 0
        var bestDistance = Double.greatestFiniteMagnitude
        var u = 0.5
        var w = 0.5
        for i in 0..<count {
            let f = L.onScreen(i, v)
            let dx = max(f.x - px, 0, px - f.x - f.w)
            let dy = max(f.y - py, 0, py - f.y - f.h)
            let d = dx * dx + dy * dy
            if d < bestDistance {
                bestDistance = d
                best = i
                u = (px - f.x) / f.w
                w = (py - f.y) / f.h
            }
        }
        // The new anchor's scroll has to match what is on screen now.
        let target = L.between(v.z, best, width: v.stage.width, top: v.stage.top)
        anchor = best
        anchorU = u
        anchorV = w
        focalX = px
        focalY.set(target.y + target.h * w - v.scroll)
    }

    /// Springs to rung `to`, keeping the anchor in place and the grid in range.
    private func settle(_ to: Int, velocity: Double, user: Bool, settings: PinchDensityGridSettings) {
        let focal = focalFor(Double(to), focalY.value)
        if settings.reducedMotion {
            fade.timing(to: 0, duration: 0.14, curve: M.quadInOut, now: now)
            fadedChange = (Double(to), focal)
        } else {
            let s = M.settleSpring
            let c = M.scrollSpring
            z.spring(to: Double(to), duration: s.duration, dampingRatio: s.dampingRatio, velocity: velocity)
            focalY.spring(to: focal, duration: c.duration, dampingRatio: c.dampingRatio)
            sideways.spring(to: 0, duration: c.duration, dampingRatio: c.dampingRatio)
        }
        if user {
            impact.impactOccurred()
            settings.onColumnsChange?(M.ladder[to].cols)
        }
    }

    private func touched() {
        hold = M.userHold
        scriptFocal = -1
    }

    func step(to date: Date, stage: PinchDensityGridStage, settings: PinchDensityGridSettings) -> PinchDensityGridScene {
        self.stage = stage
        if count != settings.plates.count { reset(plates: settings.plates) }
        if lastAuto != settings.autoPlay {
            lastAuto = settings.autoPlay
            looping = settings.autoPlay
            clock = 0
            hold = 0
            scriptFocal = -1
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(raw, 0.064)
        if dt > 0 && stage.width > 0 { advance(dt: dt) }
        return PinchDensityGridScene(
            view: view(),
            now: now,
            thumbs: thumbs,
            sharp: sharp,
            alpha: fade.value,
            scriptClock: looping && hold <= 0 ? clock : nil
        )
    }

    private func advance(dt: Double) {
        now += dt
        z.step(dt: dt, now: now)
        focalY.step(dt: dt, now: now)
        sideways.step(dt: dt, now: now)
        fade.step(dt: dt, now: now)
        if let change = fadedChange, !fade.isAnimating {
            // Faded out: change, then fade back in.
            fadedChange = nil
            z.set(change.z)
            focalY.set(change.focalY)
            sideways.set(0)
            fade.timing(to: 1, duration: 0.2, curve: M.quadInOut, now: now)
        }
        if !ready {
            // At rest on the first rung, scrolled to the top, anchored on the scripted plate.
            let frame = L.between(M.startRung, M.scriptAnchor, width: stage.width, top: stage.top)
            anchor = M.scriptAnchor
            anchorU = 0.5
            anchorV = 0.5
            focalX = frame.x + frame.w / 2
            focalY.set(frame.y + frame.h / 2)
            ready = true
        }

        // The script: z follows the eased moves, and the anchor stays at the height it started at,
        // unless that would scroll the grid out of range.
        if hold > 0 {
            hold -= dt
            if hold <= 0 { clock = M.scriptRest[Int(M.clamp(z.value.rounded(.toNearestOrAwayFromZero), 0, Double(M.last)))] }
        } else if looping && !pinching {
            let t = (clock + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            if scriptFocal < 0 { scriptFocal = focalY.value }
            var rung = M.startRung
            for step in M.script where t >= step.at {
                let from = rung
                rung = step.to
                if t < step.at + step.span { rung = M.mix(from, step.to, M.easeInOut((t - step.at) / step.span)) }
            }
            z.set(rung)
            focalY.set(focalFor(rung, scriptFocal))
        }

        // Which plates are on screen, for the sharp copies, reported only when it changes.
        let v = view()
        if v.z > M.labelFrom {
            var first = -1
            var last = -1
            for i in 0..<count {
                let f = L.onScreen(i, v)
                if f.y + f.h < 0 || f.y > stage.height { continue }
                if first < 0 { first = i }
                last = i
            }
            if first != wanted.first || last != wanted.last {
                wanted = (first, last)
                if first >= 0 { wantSharp(first: first, last: last) }
            }
        }
    }

    // MARK: Gestures

    func pinchBegan(focal: CGPoint) {
        touched()
        pinching = true
        z.stop()
        focalY.stop()
        sideways.stop()
        anchorAt(focal.x, focal.y)
        let frame = L.between(z.value, anchor, width: stage.width, top: stage.top)
        pinchLog = L.logForRung(M.clamp(z.value, 0, Double(M.last)))
        pinchFrom = (focal.x, focal.y, focalY.value, frame.x + frame.w * anchorU, sideways.value)
    }

    func pinchChanged(scale: Double, focal: CGPoint) {
        guard pinching else { return }
        let from = pinchFrom
        let rung = L.rungForLog(pinchLog - M.pinchGain * log(max(scale, 0.01)))
        z.set(rung)
        focalX = focal.x
        focalY.set(from.focalY + (focal.y - from.y))
        // The plate under the fingers drifts sideways as the columns change; follow part of it.
        let frame = L.between(rung, anchor, width: stage.width, top: stage.top)
        let drift = from.anchorX - (frame.x + frame.w * anchorU)
        sideways.set(from.sideways + M.rubber(drift * M.sidewaysFollow + (focal.x - from.x), M.sidewaysMax))
    }

    func pinchEnded(scale: Double, velocity: Double, settings: PinchDensityGridSettings) {
        guard pinching else { return }
        touched()
        pinching = false
        // Throwing the pinch carries on along the ladder; it lands on the rung it was heading for.
        let zNow = z.value
        // Spreading (scale rising) lowers the log column count, which moves up the ladder.
        // UIPinchGestureRecognizer reports a NaN velocity for a pinch lifted at once; no throw then.
        let raw = M.pinchGain * (velocity / max(scale, 0.01)) * L.rungsPerLog(zNow)
        let speed = raw.isFinite ? M.clamp(raw, -8, 8) : 0
        let to = Int(M.clamp((zNow + M.clamp(speed * M.project, -M.projectMax, M.projectMax)).rounded(.toNearestOrAwayFromZero), 0, Double(M.last)))
        settle(to, velocity: speed, user: true, settings: settings)
    }

    func panBegan() {
        if pinching { return }
        touched()
        focalY.stop()
        panFrom = focalY.value
    }

    func panChanged(translationY: Double) {
        if pinching { return }
        focalY.set(panFrom + translationY)
    }

    func panEnded(velocityY: Double) {
        if pinching { return }
        touched()
        // The anchor's height that scrolls the grid to its end, and to its top.
        let lo = focalFor(z.value, -1e6)
        let hi = focalFor(z.value, 1e6)
        focalY.decay(velocity: velocityY, clamp: (lo, hi), rubberBandFactor: 0.7)
    }

    func doubleTapped(at point: CGPoint, settings: PinchDensityGridSettings) {
        touched()
        z.stop()
        focalY.stop()
        anchorAt(point.x, point.y)
        let rung = Int(M.clamp(z.value.rounded(.toNearestOrAwayFromZero), 0, Double(M.last)))
        settle(rung == M.oneColumn ? M.threeColumns : M.oneColumn, velocity: 0, user: true, settings: settings)
    }
}

// MARK: - Photos

/// Downloads a photo and decodes it off the main thread, so the first frame that draws it does not
/// stall. The small copies are cached; the sharp ones are not, so only those near the screen stay in memory.
enum PinchDensityGridPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    static func load(_ address: String, cached: Bool = true) async -> UIImage? {
        if let hit = cache.object(forKey: address as NSString) { return hit }
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        let ready = await Task.detached(priority: .userInitiated) { image.preparingForDisplay() ?? image }.value
        if cached { cache.setObject(ready, forKey: address as NSString) }
        return ready
    }
}

// MARK: - Motion

/// Reanimated's `withSpring` with a duration and a damping ratio, stepped a frame at a time: the
/// stiffness is solved so the spring's energy falls to the threshold after 1.5 × the duration, and
/// each frame moves along the exact solution from where the last one left it.
struct PinchDensityGridSpring {
    let target: Double
    private(set) var velocity: Double
    private let dampingRatio: Double
    private let omega0: Double
    private let omega1: Double
    private let stiffness: Double
    private let initialEnergy: Double
    private static let energyThreshold = 6e-9

    init(from value: Double, to target: Double, velocity: Double, carried: Double, duration: Double, dampingRatio: Double) {
        self.target = target
        self.dampingRatio = dampingRatio
        var v = carried + velocity
        // Velocity that would carry the first frame away from the target is dropped.
        if (target > value && v < 0) || (target < value && v > 0) { v = 0 }
        self.velocity = v
        let x0 = value - target
        let zeta = dampingRatio
        let settling = duration * 1.5
        func energy(_ x: Double, _ v: Double, _ k: Double) -> Double { 0.5 * k * x * x + 0.5 * v * v }
        func difference(_ k: Double) -> Double {
            let w = sqrt(k) * zeta
            let xt = (x0 + (v + x0 * w) * settling) * exp(-w * settling)
            let vt = (x0 + (v + x0 * w) * settling) * exp(-w * settling) * -w + (v + x0 * w) * exp(-w * settling)
            return energy(xt, vt, k) / energy(x0, v, k) - Self.energyThreshold
        }
        // Bisection, as Reanimated solves it.
        var low = Double.ulpOfOne
        var high = 8e3
        let rising = difference(high) >= difference(low)
        var k = (low + high) / 2
        var left = 100
        while abs(difference(k)) > Self.energyThreshold * 1e-3 && left > 0 {
            left -= 1
            if (difference(k) < 0) == rising { low = k } else { high = k }
            k = (low + high) / 2
        }
        if !k.isFinite { k = 170 }
        stiffness = k
        omega0 = sqrt(k)
        omega1 = zeta < 1 ? omega0 * sqrt(1 - zeta * zeta) : 0
        initialEnergy = energy(x0, v, k)
    }

    /// Moves `value` on by `dt` seconds; true once the spring has come to rest on its target.
    mutating func step(_ value: inout Double, dt: Double) -> Bool {
        if initialEnergy == 0 || !initialEnergy.isFinite {
            value = target
            return true
        }
        let zeta = dampingRatio
        let x0 = value - target
        let v0 = velocity
        if zeta < 1 {
            let sin1 = sin(omega1 * dt)
            let cos1 = cos(omega1 * dt)
            let envelope = exp(-zeta * omega0 * dt)
            let frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
            value = target + frag
            velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
        } else {
            let envelope = exp(-omega0 * dt)
            value = target + envelope * (x0 + (v0 + omega0 * x0) * dt)
            velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * dt) + envelope * (v0 + omega0 * x0)
        }
        let now = 0.5 * stiffness * (target - value) * (target - value) + 0.5 * velocity * velocity
        if now / initialEnergy <= Self.energyThreshold {
            value = target
            velocity = 0
            return true
        }
        return false
    }
}

/// A number driven like a Reanimated shared value: set outright, or animated by a spring, a timing
/// curve or a decay with rubber-banded bounds, stepped by the block's frame clock.
struct PinchDensityGridValue {
    private enum Motion {
        case spring(PinchDensityGridSpring)
        case timing(from: Double, to: Double, start: Double, duration: Double, curve: (Double) -> Double)
        case decay(velocity: Double, elapsed: Double, lo: Double, hi: Double, factor: Double, springing: Bool)
    }

    private(set) var value: Double
    private var motion: Motion?

    init(_ value: Double) {
        self.value = value
    }

    var isAnimating: Bool { motion != nil }

    mutating func set(_ value: Double) {
        self.value = value
        motion = nil
    }

    /// Stops whatever is moving the value, where it is.
    mutating func stop() {
        motion = nil
    }

    mutating func spring(to target: Double, duration: Double, dampingRatio: Double, velocity: Double = 0) {
        var carried = 0.0
        if case let .spring(running) = motion { carried = running.velocity }
        motion = .spring(PinchDensityGridSpring(
            from: value, to: target, velocity: velocity, carried: carried,
            duration: duration, dampingRatio: dampingRatio
        ))
    }

    mutating func timing(to target: Double, duration: Double, curve: @escaping (Double) -> Double, now: Double) {
        motion = .timing(from: value, to: target, start: now, duration: duration, curve: curve)
    }

    /// Reanimated's `withDecay` with `rubberBandEffect`: the speed fades (deceleration 0.998), and
    /// past either bound it is pulled back by `factor` of the overshoot every frame.
    mutating func decay(velocity: Double, clamp: (Double, Double), rubberBandFactor: Double) {
        motion = .decay(velocity: velocity, elapsed: 0, lo: clamp.0, hi: clamp.1, factor: rubberBandFactor, springing: false)
    }

    mutating func step(dt: Double, now: Double) {
        guard let motion else { return }
        switch motion {
        case .spring(var spring):
            var next = value
            let done = spring.step(&next, dt: dt)
            value = next
            self.motion = done ? nil : .spring(spring)
        case let .timing(from, to, start, duration, curve):
            let p = min(1, max(0, (now - start) / duration))
            value = from + (to - from) * curve(p)
            if p >= 1 { self.motion = nil }
        case let .decay(velocity, elapsed, lo, hi, factor, springing):
            let ms = dt * 1000
            let total = elapsed + ms
            let nearest = abs(value - lo) < abs(value - hi) ? lo : hi
            let derivative = value < lo || value > hi ? value - nearest : 0
            let v = velocity * exp(-(1 - 0.998) * total * 0.1) - derivative * factor
            if abs(derivative) > 0.1 {
                value += v * ms / 1000
                self.motion = .decay(velocity: v, elapsed: total, lo: lo, hi: hi, factor: factor, springing: true)
            } else if springing {
                value = nearest
                self.motion = nil
            } else if abs(v) < 1 {
                self.motion = nil
            } else {
                value += v * ms / 1000
                self.motion = .decay(velocity: v, elapsed: total, lo: lo, hi: hi, factor: factor, springing: false)
            }
        }
    }
}
