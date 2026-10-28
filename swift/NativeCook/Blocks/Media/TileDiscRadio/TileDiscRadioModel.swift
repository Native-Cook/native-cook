import Foundation

/*
 Tile Disc Radio — stations, tile layouts, the change timeline and the driver (no drawing here).

 A station is a palette and a 4 × 4 tile layout. A cell code is kind × 16 + mask: kind 0 empty, 1 full,
 2 top + bottom halves, 3 left + right halves, 4…7 one half only (top, right, bottom, left); the mask
 holds the tile's big ("petal") corners, TL 1, TR 2, BR 4, BL 8, clockwise.

 Layouts are written as they look on screen. The disc keeps turning a quarter turn per change, so after
 k quarter turns its local cell (c, r) shows screen cell rot^k(c, r), with its kind and corners turned
 too: a station's resting disc looks the same whatever k is, and the loop closes after three changes.
*/

struct TileDiscRadioStation {
    let name: String
    let call: String
    let freq: Double
    let show: String
    let vol: String
    let listening: String
    let frame: UInt32
    let bgTile: UInt32
    let line: UInt32
    let tile: UInt32
}

/// One tile of a cell, in cell units (0…1), with its big corners.
struct TileDiscRadioTile {
    let x: Double
    let y: Double
    let w: Double
    let h: Double
    let mask: Int
}

/// A station change (or, with dir 0, the disc springing back to rest after a short drag).
struct TileDiscRadioChange {
    var from = 0
    var to = 0
    /// Quarter turns of the disc before the change.
    var kFrom = 0
    /// +1 next (clockwise), −1 previous, 0 none.
    var dir = 0
    /// Start time (absolute in the driver, loop time in the autoPlay script).
    var t0 = -1e9
    /// Disc angle in degrees when the change starts.
    var a0 = 0.0
    /// Leans back 4° before turning (a tap); a change thrown by a drag starts at once.
    var antic = true
    /// The transport glyph that was pressed: −1 previous, +1 next, 0 none.
    var press = 0

    var kTo: Int { kFrom + dir }
}

struct TileDiscRadioFrame {
    var ch = TileDiscRadioChange()
    /// Seconds since the change started (large once settled).
    var ct = 99.0
    /// Disc angle in degrees.
    var angle = 0.0
    var playing = true
    /// A clock for the live dot (loop time in autoPlay).
    var clock = 0.0
    var reduced = false
}

enum TileDiscRadioModel {
    static let loop = 9.0
    /// Loop times at which autoPlay presses "next".
    static let script = [1.5, 4.3, 7.1]
    /// A change has settled after this long; rows are still sliding until `busy`.
    static let done = 1.8
    static let busy = 1.0
    static let idleBeforeBack = 2.0

    static let stations = [
        TileDiscRadioStation(name: "Lumen Sounds", call: "KVRA", freq: 91.7, show: "Night Shift with Ora Lind",
                             vol: "VOL.13", listening: "2.4k",
                             frame: 0x8A08FD, bgTile: 0xFC57E1, line: 0xFC1E53, tile: 0xFDB400),
        TileDiscRadioStation(name: "Field Notes", call: "KOAL", freq: 98.3, show: "Low Tide with Remi Okafor",
                             vol: "VOL.14", listening: "1.8k",
                             frame: 0x007A42, bgTile: 0x8CF21E, line: 0x253DF2, tile: 0x1CC8FD),
        TileDiscRadioStation(name: "Ember Hours", call: "KWEN", freq: 104.5, show: "Morning Drift with Ines Varga",
                             vol: "VOL.15", listening: "3.1k",
                             frame: 0xF2511B, bgTile: 0xFFA98A, line: 0x2A1FD1, tile: 0xFFF0D6),
    ]

    /// Disc layouts, row by row as seen on screen.
    static let discLayouts: [[Int]] = [
        [0x00, 0x18, 0x13, 0x12,
         0x00, 0x19, 0x10, 0x36,
         0x00, 0x18, 0x14, 0x29,
         0x12, 0x2C, 0x16, 0x18],
        [0x18, 0x14, 0x13, 0x12,
         0x11, 0x10, 0x10, 0x16,
         0x6C, 0x68, 0x26, 0x6C,
         0x19, 0x10, 0x33, 0x14],
        [0x12, 0x19, 0x16, 0x18,
         0x36, 0x00, 0x13, 0x10,
         0x10, 0x56, 0x1C, 0x29,
         0x13, 0x18, 0x10, 0x16],
    ]

    /// The background behind the disc (the same for every station; only its colour changes).
    static let bgLayout = [
        0x19, 0x13, 0x13, 0x16,
        0x18, 0x10, 0x10, 0x34,
        0x29, 0x10, 0x10, 0x16,
        0x19, 0x1C, 0x1C, 0x14,
    ]

    // MARK: Tiles

    static func tiles(_ code: Int) -> [TileDiscRadioTile] {
        let kind = code >> 4
        let m = code & 15
        switch kind {
        case 1: return [TileDiscRadioTile(x: 0, y: 0, w: 1, h: 1, mask: m)]
        case 2: return [TileDiscRadioTile(x: 0, y: 0, w: 1, h: 0.5, mask: m & 3),
                        TileDiscRadioTile(x: 0, y: 0.5, w: 1, h: 0.5, mask: m & 12)]
        case 3: return [TileDiscRadioTile(x: 0, y: 0, w: 0.5, h: 1, mask: m & 9),
                        TileDiscRadioTile(x: 0.5, y: 0, w: 0.5, h: 1, mask: m & 6)]
        case 4: return [TileDiscRadioTile(x: 0, y: 0, w: 1, h: 0.5, mask: m)]
        case 5: return [TileDiscRadioTile(x: 0.5, y: 0, w: 0.5, h: 1, mask: m)]
        case 6: return [TileDiscRadioTile(x: 0, y: 0.5, w: 1, h: 0.5, mask: m)]
        case 7: return [TileDiscRadioTile(x: 0, y: 0, w: 0.5, h: 1, mask: m)]
        default: return []
        }
    }

    /// The code of local cell (c, r) of a station's disc after k quarter turns clockwise.
    static func localCode(_ station: Int, _ k: Int, _ c: Int, _ r: Int) -> Int {
        let q = ((k % 4) + 4) % 4
        var sc = c
        var sr = r
        for _ in 0..<q {
            let nc = 3 - sr
            sr = sc
            sc = nc
        }
        let code = discLayouts[station][sr * 4 + sc]
        var kind = code >> 4
        let m = code & 15
        var mask = 0
        for b in 0..<4 where m & (1 << ((b + q) % 4)) != 0 {
            mask |= 1 << b
        }
        if kind == 2 || kind == 3 {
            if q % 2 == 1 { kind = kind == 2 ? 3 : 2 }
        } else if kind >= 4 {
            // A half seen on screen at side s sits at local side s − q.
            kind = 4 + ((kind - 4 - q) % 4 + 4) % 4
        }
        return kind << 4 | mask
    }

    // MARK: Curves

    static func clamp01(_ x: Double) -> Double { min(max(x, 0), 1) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInCubic(_ x: Double) -> Double { x * x * x }
    static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func easeInOut(_ x: Double) -> Double { 0.5 - 0.5 * cos(Double.pi * x) }

    /// A spring's remaining distance (1 → 0) from rest, released with no speed.
    static func springDecay(_ t: Double, response: Double, damping: Double) -> Double {
        let w = 2 * Double.pi / response
        let zw = damping * w
        let wd = w * sqrt(1 - damping * damping)
        return exp(-zw * t) * (cos(wd * t) + zw / wd * sin(wd * t))
    }

    // MARK: Timeline of a change (ct = seconds since it started)

    static func angle(_ ch: TileDiscRadioChange, _ ct: Double) -> Double {
        let a1 = Double(ch.kTo) * 90
        if ch.dir == 0 {
            if ct >= done { return a1 }
            return a1 + (ch.a0 - a1) * springDecay(ct, response: 0.45, damping: 0.8)
        }
        if ch.antic {
            let pre = ch.a0 - 4 * Double(ch.dir)
            if ct < 0.12 { return lerp(ch.a0, pre, easeOutCubic(max(0, ct) / 0.12)) }
            let t = ct - 0.12
            if t >= done - 0.12 { return a1 }
            return a1 + (pre - a1) * springDecay(t, response: 0.8, damping: 0.7)
        }
        if ct >= done { return a1 }
        return a1 + (ch.a0 - a1) * springDecay(max(0, ct), response: 0.8, damping: 0.7)
    }

    /// How far local row r has run its conveyor (0 → 1).
    static func rowP(_ ct: Double, _ r: Int) -> Double {
        let s = 0.10 + 0.06 * Double(r)
        return easeInOutCubic(seg(ct, s, s + 0.72))
    }

    static func gapMix(_ ct: Double) -> Double { easeInOut(seg(ct, 0.30, 0.70)) }
    static func pageMix(_ ct: Double) -> Double { easeInOut(seg(ct, 0.20, 0.80)) }
    static func freqMix(_ ct: Double) -> Double { easeInOutCubic(seg(ct, 0.10, 1.00)) }

    static func bgMix(_ ct: Double, _ i: Int, _ j: Int, _ dir: Int) -> Double {
        let s = 0.15 + 0.045 * Double((dir >= 0 ? i : 3 - i) + j)
        return easeInOut(seg(ct, s, s + 0.30))
    }

    static func wordsOut(_ ct: Double) -> Double { 1 - easeInCubic(seg(ct, 0.05, 0.20)) }
    static func wordIn(_ ct: Double, _ n: Int) -> Double {
        let s = 0.70 + 0.05 * Double(n)
        return easeOutCubic(seg(ct, s, s + 0.30))
    }

    static func titleOut(_ ct: Double) -> Double { easeInCubic(seg(ct, 0, 0.25)) }
    static func titleIn(_ ct: Double) -> Double { easeOutCubic(seg(ct, 0.45, 0.85)) }

    /// Scale of the pressed transport glyph.
    static func press(_ ct: Double) -> Double {
        if ct < 0 || ct >= 0.4 { return 1 }
        if ct < 0.08 { return 1 - 0.14 * easeOutCubic(ct / 0.08) }
        return 0.86 + 0.14 * easeOutCubic((ct - 0.08) / 0.32)
    }

    /// Reduced motion: one 0.3 s crossfade for everything.
    static func fade(_ ct: Double) -> Double { easeInOut(seg(ct, 0, 0.3)) }

    // MARK: autoPlay

    /// The scripted change in effect at loop time tau, and the time since it started.
    static func autoChange(_ tau: Double) -> (TileDiscRadioChange, Double) {
        var i = -1
        for n in 0..<script.count where tau >= script[n] { i = n }
        if i < 0 { return (TileDiscRadioChange(), 99) }
        let ch = TileDiscRadioChange(from: i, to: (i + 1) % 3, kFrom: i, dir: 1, t0: script[i],
                                     a0: Double(i) * 90, antic: true, press: 1)
        return (ch, tau - script[i])
    }

    static func frame(_ ch: TileDiscRadioChange, _ ct: Double, clock: Double, playing: Bool, reduced: Bool) -> TileDiscRadioFrame {
        var f = TileDiscRadioFrame()
        f.ch = ch
        f.ct = min(ct, 99)
        f.angle = reduced ? Double(ch.kTo) * 90 : angle(ch, ct)
        f.clock = clock
        f.playing = playing
        f.reduced = reduced
        return f
    }
}

/// Plays the script, takes over on touch and goes home 2 s after the last one.
final class TileDiscRadioDriver {
    typealias M = TileDiscRadioModel

    private var autoStart: Double?
    private var manual = false
    private var ch = TileDiscRadioChange()
    private var dragging = false
    private var dragBase = 0.0
    private var dragAngle = 0.0
    private var armed = 0
    private var lastTouch = -1e9
    private var returning = false
    private var playing = true

    func reset() {
        autoStart = nil
        manual = false
        ch = TileDiscRadioChange()
        dragging = false
        returning = false
        playing = true
        lastTouch = -1e9
    }

    /// True while something still moves after a touch (keeps the timeline running without autoPlay).
    func live(at now: Double) -> Bool {
        dragging || now - ch.t0 < M.done + 0.1 || now - lastTouch < 0.5
    }

    func frame(at now: Double, auto: Bool, reduced: Bool) -> TileDiscRadioFrame {
        if auto && manual && !dragging {
            let ct = now - ch.t0
            if returning {
                if ct >= M.done {
                    manual = false
                    returning = false
                    autoStart = now
                }
            } else if now >= lastTouch + M.idleBeforeBack && ct >= M.done {
                playing = true
                if ch.to == 0 {
                    manual = false
                    autoStart = now
                } else {
                    start(dir: ch.to == 1 ? -1 : 1, at: now, a0: M.angle(ch, ct), antic: true, press: 0)
                    returning = true
                }
            }
        }
        if auto && !manual {
            if autoStart == nil { autoStart = now }
            let tau = (now - autoStart!).truncatingRemainder(dividingBy: M.loop)
            let (c, ct) = M.autoChange(tau)
            return M.frame(c, ct, clock: tau, playing: true, reduced: reduced)
        }
        if dragging {
            let rest = TileDiscRadioChange(from: ch.to, to: ch.to, kFrom: ch.kTo, dir: 0, t0: -1e9,
                                           a0: dragAngle, antic: false, press: 0)
            var f = M.frame(rest, 99, clock: now, playing: playing, reduced: reduced)
            f.angle = dragAngle
            return f
        }
        return M.frame(ch, now - ch.t0, clock: now, playing: playing, reduced: reduced)
    }

    /// Takes over from the loop where it is.
    private func capture(at now: Double, auto: Bool) {
        lastTouch = now
        returning = false
        guard !manual else { return }
        manual = true
        if auto, let s = autoStart {
            let tau = (now - s).truncatingRemainder(dividingBy: M.loop)
            var (c, ct) = M.autoChange(tau)
            c.t0 = now - ct
            ch = c
        }
    }

    private func isBusy(at now: Double) -> Bool {
        ch.dir != 0 && now - ch.t0 < M.busy
    }

    private func start(dir: Int, at now: Double, a0: Double, antic: Bool, press: Int) {
        ch = TileDiscRadioChange(from: ch.to, to: (ch.to + dir + 3) % 3, kFrom: ch.kTo, dir: dir, t0: now,
                                 a0: a0, antic: antic, press: press)
    }

    /// −1 previous, +1 next, 0 play / pause. False when the disc is still busy.
    func tap(_ button: Int, at now: Double, auto: Bool, reduced: Bool) -> Bool {
        capture(at: now, auto: auto)
        if button == 0 {
            playing.toggle()
            return true
        }
        if isBusy(at: now) { return false }
        start(dir: button, at: now, a0: M.angle(ch, now - ch.t0), antic: !reduced, press: button)
        return true
    }

    func beginDrag(at now: Double, auto: Bool) -> Bool {
        capture(at: now, auto: auto)
        if isBusy(at: now) { return false }
        dragging = true
        dragBase = M.angle(ch, now - ch.t0)
        // The disc is now at rest on ch.to; fold the change into a settled one.
        ch = TileDiscRadioChange(from: ch.to, to: ch.to, kFrom: ch.kTo, dir: 0, t0: -1e9, a0: dragBase, antic: false, press: 0)
        dragAngle = dragBase
        armed = 0
        return true
    }

    /// Turns the disc with the finger; returns true when it crosses the change threshold (a detent).
    func drag(_ dx: Double, radius: Double, at now: Double) -> Bool {
        lastTouch = now
        var deg = -dx / radius * 180 / Double.pi
        let a = abs(deg)
        if a > 60 { deg = (60 + (a - 60) * 0.35) * (deg < 0 ? -1 : 1) }
        dragAngle = dragBase + deg
        let off = dragAngle - Double(ch.kTo) * 90
        let side = off > 22 ? 1 : (off < -22 ? -1 : 0)
        defer { armed = side }
        return side != 0 && side != armed
    }

    /// Releases the disc: a change if it was turned or thrown far enough, else it springs back.
    func endDrag(velocity vx: Double, at now: Double) -> Bool {
        guard dragging else { return false }
        dragging = false
        lastTouch = now
        let off = dragAngle - Double(ch.kTo) * 90
        var dir = 0
        if off > 22 || (vx < -600 && off > 0) { dir = 1 }
        else if off < -22 || (vx > 600 && off < 0) { dir = -1 }
        if dir != 0 {
            start(dir: dir, at: now, a0: dragAngle, antic: false, press: 0)
            return true
        }
        ch = TileDiscRadioChange(from: ch.to, to: ch.to, kFrom: ch.kTo, dir: 0, t0: now, a0: dragAngle, antic: false, press: 0)
        return false
    }
}
