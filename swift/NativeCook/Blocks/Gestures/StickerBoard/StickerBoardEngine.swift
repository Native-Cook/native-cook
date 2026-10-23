import QuartzCore
import SwiftUI
import UIKit

enum StickerBoardMetrics {
    // Length of the self-playing loop, in seconds. It starts and ends with every sticker at home.
    static let loop = 15.0
    // Moves in the loop: which sticker, when it is picked up and dropped (s), and where to, as an
    // offset from home in points of a 402 pt wide screen, a turn in degrees and a scale.
    struct Move {
        let index: Int
        let at: Double
        let end: Double
        let dx: Double
        let dy: Double
        let turn: Double
        let scale: Double
    }
    static let moves: [Move] = [
        Move(index: 0, at: 0.6, end: 2.2, dx: 150, dy: 250, turn: 18, scale: 1),
        Move(index: 1, at: 3.0, end: 4.6, dx: -170, dy: 165, turn: -26, scale: 1.28),
        Move(index: 0, at: 9.7, end: 11.1, dx: 0, dy: 0, turn: 0, scale: 1),
        Move(index: 1, at: 11.4, end: 12.8, dx: 0, dy: 0, turn: 0, scale: 1),
    ]
    // The peel in the loop: the sticker, when it is pressed, when the corner lifts, when the finger
    // has pulled the corner past the middle, when it has carried it near the bin, and when it lets go.
    static let peelIndex = 5
    static let peelPress = 5.0
    static let peelStart = 5.45
    static let peelPulled = 6.9
    static let peelCarried = 8.0
    static let peelDrop = 8.5
    // How far past the middle the scripted finger pulls the corner, as a share of the corner's distance.
    static let peelReach = 0.7
    // The scripted carry ends this far off the bin's hover point (pt), inside its reach, so the
    // magnet visibly pulls the sticker in the last bit.
    static let carryTo = (54.0, -56.0)
    // A carried sticker travels on an arc this far off the straight line (pt).
    static let arc = 40.0
    // After a touch the loop waits this long (s), then tidies every sticker home over `tidyTime` s.
    static let userHold = 3.5
    static let tidyTime = 0.9
    static let tidyRest = 0.3

    static let designWidth = 402.0
    // The white die-cut border around every sticker (pt).
    static let border: CGFloat = 7
    // Picked up: grows by `liftGrow` and its shadow falls further and softer.
    static let liftGrow = 0.06
    static let liftTime = 0.12
    // A sticker picked up from under others does not jump over them: a copy stays where it was in
    // the pile while the one on top fades in over `riseTime` s on an ease-in-out curve, so it
    // surfaces through the stickers that covered it as it lifts.
    static let riseTime = 0.3
    static let shadowNear = [1.0, 2.0, 2.2]
    static let shadowFar = [6.0, 16.0, 11.0]
    // Dropped: it keeps a little of its speed (time constant in s) while it falls for `fallTime` s,
    // then slaps down with a squash that springs out.
    static let glide = 0.09
    static let fallTime = 0.16
    static let maxThrow = 1500.0
    static let squash = 0.085
    static let squashStiffness = 480.0
    static let squashDamping = 19.0
    // While carried it swings with the finger's speed: degrees per pt/s, up to `swayMax`.
    static let swayPerSpeed = 0.012
    static let swayMax = 10.0
    // Pinch limits, rubber-banded beyond.
    static let minScale = 0.55
    static let maxScale = 2.2
    // Holding this long (s) lifts a corner instead; the corner follows the finger, and pulled
    // further than `detach` times its distance from the middle the sticker comes off.
    static let longPress = 0.42
    static let panSlop = 4.0
    // Frames a touch that ended is given for one of the gestures to start before it counts as a tap.
    static let pendingFrames = 3
    static let detach = 1.25
    static let flattenTime = 0.22
    static let detachedLift = 1.4
    static let follow = 0.05
    // The bin notices a loose sticker within `approach` pt of the point over its mouth and opens its
    // lid the nearer it comes; within `reach` pt it pulls the sticker in (let go of beyond
    // `reachOut` times that).
    static let approach = 300.0
    static let reach = 92.0
    static let reachOut = 1.3
    // Pulled in, the sticker moves this share of the way to the mouth and shrinks by `magnetShrink`.
    static let magnetPull = 0.82
    static let magnetShrink = 0.3
    static let magnetStiffness = 360.0
    static let magnetDamping = 19.0
    // A flick towards the bin from within `flingRange` pt, faster than `flingSpeed` pt/s and within
    // about 25° of it, throws the sticker away too.
    static let flingSpeed = 800.0
    static let flingRange = 460.0
    static let flingAim = 0.9
    // Thrown away: it crumples into a ball of `ball` pt over `crumple` s while it moves over the
    // mouth, then drops in on a short arc over `drop` s.
    static let crumple = 0.34
    static let drop = 0.3
    static let tossEnd = crumple + drop
    static let ball = 16.0
    static let ballHop = 16.0
    static let ballSpin = 170.0
    // The bin's springs (stiffness, damping): rising, the lid, its slam and bounce, leaning and
    // growing towards a sticker, the squash and wobble when the lid slams, and how long it stays up.
    static let show = (300.0, 21.0)
    static let lid = (240.0, 15.0)
    static let slam = (1500.0, 14.0)
    static let lidBounce = 0.32
    static let lean = (260.0, 20.0)
    static let leanMax = 9.0
    static let grow = (300.0, 20.0)
    static let growNear = 0.07
    static let growMagnet = 0.05
    static let binSquash = (520.0, 12.0)
    static let binSquashKick = 2.6
    static let wobble = (220.0, 5.0)
    static let wobbleKick = 70.0
    static let linger = 0.9
    // A sticker thrown away comes back home after `respawn` s, slapped back on over `spawnTime` s.
    static let respawn = 4.0
    static let spawnTime = 0.22
    static let imageFade = 0.3
}

/** One sticker's live state. Positions in screen points, turns in degrees. */
struct StickerBoardLive {
    var x = 0.0
    var y = 0.0
    var turn = 0.0
    var scale = 1.0
    var vx = 0.0
    var vy = 0.0
    var sway = 0.0
    var lift = 0.0
    var held = false
    var byUser = false
    var falling = false
    var squash = 0.0
    var squashSpeed = 0.0
    // Seconds since it was thrown away (−1 on the board), and where, how turned and how big it was.
    var gone = -1.0
    var goneX = 0.0
    var goneY = 0.0
    var goneTurn = 0.0
    var goneScale = 1.0
    var goneByUser = false
    // Seconds since it was slapped back on (−1 for none).
    var spawn = -1.0
    // Rising through the pile: 0…1 (1 settled on top), and the sticker its old copy is drawn under.
    var rise = 1.0
    var riseUnder = -1
    // Where the tidy-up starts it from.
    var fromX = 0.0
    var fromY = 0.0
    var fromTurn = 0.0
    var fromScale = 1.0
}

/// The peeled corner: the sticker, its corner (sticker units), the finger (screen), and more.
struct StickerBoardPeel {
    var index = -1
    var cx = 0.0
    var cy = 0.0
    var fx = 0.0
    var fy = 0.0
    var detached = false
    // Seconds since the flap began to lie flat again (−1 for not), and the corner it starts from.
    var flatten = -1.0
    var flatX = 0.0
    var flatY = 0.0
    var anchorX = 0.0
    var anchorY = 0.0
    var byUser = false
}

struct StickerBoardStage: Equatable {
    var width: Double
    var height: Double
    var top: Double
    var bottom: Double
}

struct StickerBoardSettings {
    var stickers: [StickerBoardSticker]
    var shapes: [StickerBoardShape]
    /// autoPlay, and not under reduced motion.
    var auto: Bool
    var reducedMotion: Bool
}

struct StickerBoardPhoto {
    var image: UIImage
    var at: Double
}

/// One frame to draw.
struct StickerBoardFrame {
    var stage: StickerBoardStage
    var live: [StickerBoardLive]
    var order: [Int]
    var peel: StickerBoardPeel
    var bin: StickerBoardBinState
    var elapsed: Double
    var photos: [StickerBoardPhoto?]
}

enum StickerBoardMath {
    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func easeInOut(_ p: Double) -> Double { p < 0.5 ? 4 * p * p * p : 1 - pow(-2 * p + 2, 3) / 2 }
    static func easeOut(_ p: Double) -> Double { 1 - pow(1 - p, 3) }

    static func easeOutBack(_ p: Double) -> Double {
        let c = 1.7
        let q = p - 1
        return 1 + (c + 1) * q * q * q + c * q * q
    }

    /// One step of a damped spring, in small sub-steps so a long frame cannot blow it up.
    static func spring(_ x: Double, _ v: Double, _ target: Double, _ k: (Double, Double), _ dt: Double) -> (Double, Double) {
        let steps = max(1, Int((dt * 240).rounded(.up)))
        let h = dt / Double(steps)
        var nx = x
        var nv = v
        for _ in 0..<steps {
            nv += (-k.0 * (nx - target) - k.1 * nv) * h
            nx += nv * h
        }
        if !nx.isFinite || !nv.isFinite { return (target, 0) }
        return (nx, nv)
    }

    static func rubber(_ x: Double, _ lo: Double, _ hi: Double, _ band: Double) -> Double {
        if x < lo { return lo - band * (1 - 1 / ((lo - x) / band + 1)) }
        if x > hi { return hi + band * (1 - 1 / ((x - hi) / band + 1)) }
        return x
    }

    /// A screen point in a sticker's own units.
    static func toLocal(_ s: StickerBoardLive, _ k: Double, _ x: Double, _ y: Double) -> (Double, Double) {
        let a = -s.turn * .pi / 180
        let dx = x - s.x
        let dy = y - s.y
        let scale = max(0.01, k * s.scale)
        return ((dx * cos(a) - dy * sin(a)) / scale, (dx * sin(a) + dy * cos(a)) / scale)
    }

    /// A point of a sticker's own units on the screen.
    static func toScreen(_ s: StickerBoardLive, _ k: Double, _ x: Double, _ y: Double) -> (Double, Double) {
        let a = s.turn * .pi / 180
        let scale = k * s.scale
        return (s.x + (x * cos(a) - y * sin(a)) * scale, s.y + (x * sin(a) + y * cos(a)) * scale)
    }

    /// Moves sticker `i` to the top of the pile.
    static func raise(_ order: [Int], _ i: Int) -> [Int] {
        order.filter { $0 != i } + [i]
    }

    /// The sticker right above `i` in the pile, or −1 when `i` is already on top.
    static func above(_ order: [Int], _ i: Int) -> Int {
        guard let at = order.firstIndex(of: i), at < order.count - 1 else { return -1 }
        return order[at + 1]
    }
}

// MARK: - The engine

/// The board's state, stepped once a frame like the RN frame callback, and a stand-in for the
/// React Native gesture set: a long-press pan that peels, racing a pan, a pinch and a rotation that
/// run together on the sticker under the fingers.
@MainActor
final class StickerBoardEngine {
    private typealias M = StickerBoardMetrics
    private typealias P = StickerBoardMath

    private var live: [StickerBoardLive] = []
    private var order: [Int] = []
    private var peel = StickerBoardPeel()
    // The sticker under the fingers, whether a drag, pinch, turn or peel began, and a release
    // waiting a few frames to see whether any of them did.
    private var active = -1
    private var started = false
    private var pending = 0
    // A peel the finger is still holding.
    private var peeling = false
    private var dragFrom = (0.0, 0.0)
    private var turnFrom = 0.0
    private var scaleFrom = 1.0
    private var bin = StickerBoardBinState()
    private var script = 0.0
    private var hold = 0.0
    private var tidy = -1.0
    private var scriptFinger = (0.0, 0.0)
    private var elapsed = 0.0
    private var lastDate: Date?
    private var lastAuto: Bool?
    private var photos: [StickerBoardPhoto?] = []
    private var stage = StickerBoardStage(width: 0, height: 0, top: 0, bottom: 0)
    private var settings: StickerBoardSettings?

    private let lightImpact = UIImpactFeedbackGenerator(style: .light)
    private let mediumImpact = UIImpactFeedbackGenerator(style: .medium)
    private let rigidImpact = UIImpactFeedbackGenerator(style: .rigid)
    private let notice = UINotificationFeedbackGenerator()
    private let selection = UISelectionFeedbackGenerator()

    private enum Thud { case drop, lift, rip, snap, slam }

    private func thud(_ kind: Thud) {
        switch kind {
        case .slam: notice.notificationOccurred(.success)
        case .snap: selection.selectionChanged()
        case .drop: mediumImpact.impactOccurred()
        case .rip: rigidImpact.impactOccurred()
        case .lift: lightImpact.impactOccurred()
        }
    }

    func resetPhotos(count: Int) {
        photos = Array(repeating: nil, count: count)
    }

    func photoArrived(_ image: UIImage, at index: Int) {
        guard photos.indices.contains(index) else { return }
        photos[index] = StickerBoardPhoto(image: image, at: elapsed)
    }

    private var k: Double { stage.width / M.designWidth }

    /// Home of sticker `i` on the screen.
    private func homeOf(_ i: Int) -> (Double, Double) {
        guard let stickers = settings?.stickers, stickers.indices.contains(i) else { return (stage.width / 2, stage.height / 2) }
        return (stickers[i].home.x * stage.width, stage.top + stickers[i].home.y * (stage.height - stage.top - stage.bottom))
    }

    func step(to date: Date, stage room: StickerBoardStage, settings: StickerBoardSettings) -> StickerBoardFrame {
        self.settings = settings
        let count = settings.stickers.count
        if photos.count != count { photos = Array(repeating: nil, count: count) }
        if room != stage || live.count != count {
            // Laid out (again): every sticker at home, like the React Native layout effect.
            stage = room
            live = settings.stickers.enumerated().map { i, s in
                var l = StickerBoardLive()
                (l.x, l.y) = homeOf(i)
                l.turn = s.turn
                return l
            }
            order = Array(0..<count)
            peel = StickerBoardPeel()
            active = -1
            peeling = false
            bin = StickerBoardBinState()
        }
        if lastAuto != settings.auto {
            lastAuto = settings.auto
            script = 0
            hold = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 0.016
        lastDate = date
        let dt = min(raw, 0.064)
        if dt > 0 && stage.width > 0 { advance(dt: dt, settings: settings) }
        return StickerBoardFrame(stage: stage, live: live, order: order, peel: peel, bin: bin, elapsed: elapsed, photos: photos)
    }

    // MARK: Actions

    /// The point over the bin's mouth where a sticker is pulled in, with the bin standing up straight.
    private var hoverPoint: (Double, Double) {
        (stage.width / 2, stage.height - stage.bottom - (StickerBoardBinArt.floor + StickerBoardBinArt.body + StickerBoardBinArt.hover) * k)
    }

    /// The top sticker under a point, or −1.
    private func hit(_ x: Double, _ y: Double) -> Int {
        guard let shapes = settings?.shapes else { return -1 }
        for i in order.reversed() where live.indices.contains(i) && shapes.indices.contains(i) {
            let s = live[i]
            if s.gone >= 0 { continue }
            let p = P.toLocal(s, k, x, y)
            if shapes[i].outline.contains(CGPoint(x: p.0, y: p.1)) { return i }
        }
        return -1
    }

    /// Puts sticker `i` on top of the pile, rising through the ones that covered it.
    private func bringUp(_ i: Int) {
        guard live.indices.contains(i) else { return }
        let under = P.above(order, i)
        if under < 0 { return }
        order = P.raise(order, i)
        // Still rising from before: the copy stays where it was, under the same sticker.
        if live[i].rise >= 1 { live[i].riseUnder = under }
        live[i].rise = 0
    }

    /// Picks up sticker `i` and puts it on top of the pile.
    @discardableResult
    private func pickUp(_ i: Int, byUser: Bool) -> Int {
        if !live.indices.contains(i) { return -1 }
        active = i
        started = false
        bringUp(i)
        live[i].held = true
        live[i].byUser = byUser
        live[i].falling = false
        live[i].vx = 0
        live[i].vy = 0
        return i
    }

    /// Picks up the sticker under a finger, once per touch.
    private func grab(_ x: Double, _ y: Double, byUser: Bool) {
        if active >= 0 { return }
        pickUp(hit(x, y), byUser: byUser)
    }

    /// Lets go of a carried sticker: it keeps a little speed, falls, and slaps down.
    private func release(_ i: Int, _ vx: Double, _ vy: Double) {
        if !live.indices.contains(i) { return }
        let reduced = settings?.reducedMotion ?? false
        if live[i].held {
            var s = live[i]
            s.held = false
            s.falling = true
            var tx = reduced || !vx.isFinite ? 0 : max(-M.maxThrow, min(M.maxThrow, vx))
            var ty = reduced || !vy.isFinite ? 0 : max(-M.maxThrow, min(M.maxThrow, vy))
            // Aim the glide so the sticker lands on the board.
            let endX = max(16, min(stage.width - 16, s.x + tx * M.glide))
            let endY = max(stage.top + 16, min(stage.height - stage.bottom - 16, s.y + ty * M.glide))
            tx = (endX - s.x) / M.glide
            ty = (endY - s.y) / M.glide
            s.vx = tx
            s.vy = ty
            live[i] = s
        }
        if active == i { active = -1 }
    }

    /// Throws sticker `i` away: from where it hangs now it crumples into a ball and drops in the bin.
    private func toss(_ i: Int, byUser: Bool) {
        guard live.indices.contains(i) else { return }
        let reduced = settings?.reducedMotion ?? false
        var s = live[i]
        let grow = reduced ? 1 : 1 + M.liftGrow * min(s.lift, 1.4)
        s.held = false
        s.falling = false
        s.gone = 0
        s.goneX = s.x
        s.goneY = s.y
        s.goneTurn = s.turn + s.sway
        s.goneScale = s.scale * grow * (1 - M.magnetShrink * P.clamp01(bin.magnet))
        s.goneByUser = byUser
        s.vx = 0
        s.vy = 0
        live[i] = s
    }

    private func startPeel(_ i: Int, _ x: Double, _ y: Double, byUser: Bool) {
        guard let shapes = settings?.shapes, live.indices.contains(i), shapes.indices.contains(i) else { return }
        let s = live[i]
        // The corner: the point of the sticker's reach in the direction of the finger.
        let p = P.toLocal(s, k, x, y)
        let len = max(1, hypot(p.0, p.1))
        let reach = shapes[i].extent * 0.9
        let cx = p.0 / len * reach
        let cy = p.1 / len * reach
        let corner = P.toScreen(s, k, cx, cy)
        peel = StickerBoardPeel(index: i, cx: cx, cy: cy, fx: corner.0, fy: corner.1, detached: false, flatten: -1, flatX: cx, flatY: cy, anchorX: 0, anchorY: 0, byUser: byUser)
        peeling = true
        started = true
    }

    /// Lets go of a peel. Off and held over the bin, or flicked at it, it is thrown away; off
    /// elsewhere, it is dropped; still on, the flap lies back down.
    private func releasePeel(_ vx: Double, _ vy: Double) {
        let p = peel
        let i = p.index
        if !live.indices.contains(i) || !peeling { return }
        peeling = false
        if p.detached {
            let h = hoverPoint
            let tx = p.fx + p.anchorX
            let ty = p.fy + p.anchorY
            let dx = h.0 - tx
            let dy = h.1 - ty
            let dist = min(hypot(dx, dy), hypot(h.0 - p.fx, h.1 - p.fy))
            let speed = vx.isFinite && vy.isFinite ? hypot(vx, vy) : 0
            let aimed = speed > M.flingSpeed && dist < M.flingRange * k && dist > 1 && (vx * dx + vy * dy) / (speed * dist) > M.flingAim
            if bin.snapped || aimed { toss(i, byUser: p.byUser) } else { release(i, vx, vy) }
            peel = StickerBoardPeel()
        } else {
            let f = P.toLocal(live[i], k, p.fx, p.fy)
            peel.flatten = 0
            peel.flatX = f.0
            peel.flatY = f.1
        }
        if active == i { active = -1 }
    }

    /// A touch takes over from the loop: anything it was carrying is put down.
    private func touched() {
        if settings?.auto == true && hold <= 0 {
            for i in live.indices where live[i].held && !live[i].byUser && i != peel.index { release(i, 0, 0) }
            if peel.index >= 0 && !peel.byUser { releasePeel(0, 0) }
            tidy = -1
        }
        hold = M.userHold
    }

    // MARK: Frame

    private func advance(dt: Double, settings: StickerBoardSettings) {
        let reduced = settings.reducedMotion
        let stickers = settings.stickers
        elapsed += dt
        let k = self.k

        checkLongPress()

        // A touch that ended without becoming a drag, pinch, turn or peel puts its sticker down.
        // The gestures settle in no fixed order, so this waits a few frames for one of them to start.
        if pending > 0 {
            pending -= 1
            if pending == 0 && !started && active >= 0 && peel.index < 0 { release(active, 0, 0) }
        }

        // The loop, or the tidy-up before it starts again.
        if settings.auto {
            if hold > 0 {
                if active < 0 && peel.index < 0 { hold -= dt }
                if hold <= 0 { tidy = 0 }
            } else if tidy >= 0 {
                let t = tidy + dt
                if tidy == 0 {
                    for i in live.indices {
                        live[i].fromX = live[i].x
                        live[i].fromY = live[i].y
                        live[i].fromTurn = live[i].turn
                        live[i].fromScale = live[i].scale
                        if live[i].gone >= 0 { live[i].gone = max(live[i].gone, M.respawn) }
                    }
                }
                let p = P.easeInOut(P.clamp01(t / M.tidyTime))
                for i in live.indices {
                    var s = live[i]
                    if s.gone >= 0 || s.spawn >= 0 { continue }
                    let home = homeOf(i)
                    let moved = hypot(s.fromX - home.0, s.fromY - home.1) > 1 || abs(s.fromTurn - stickers[i].turn) > 0.5 || abs(s.fromScale - 1) > 0.01
                    if !moved { continue }
                    // The tidy-up carries it now, so it is put down at the end like the loop's own moves.
                    if t - dt <= 0 {
                        s.held = true
                        s.byUser = false
                    }
                    s.x = s.fromX + (home.0 - s.fromX) * p
                    s.y = s.fromY + (home.1 - s.fromY) * p
                    s.turn = s.fromTurn + (stickers[i].turn - s.fromTurn) * p
                    s.scale = s.fromScale + (1 - s.fromScale) * p
                    live[i] = s
                }
                if t >= M.tidyTime && tidy < M.tidyTime {
                    for i in live.indices where live[i].held && !live[i].byUser { release(i, 0, 0) }
                }
                if t >= M.tidyTime + M.tidyRest {
                    tidy = -1
                    script = 0
                    // The loop starts again with an empty bin.
                    bin.resetCount()
                } else {
                    tidy = t
                }
            } else {
                runScript(dt: dt, stickers: stickers)
            }
        }

        // The peel: a lifted corner comes off once pulled far enough; a released flap lies back down.
        if peel.index >= live.count { peel = StickerBoardPeel() }
        if live.indices.contains(peel.index) && peel.byUser && !peeling && peel.flatten < 0 {
            // A user's peel whose finger has gone is let go of, so the board can never be left
            // holding a flap.
            let i = peel.index
            if peel.detached {
                peel = StickerBoardPeel()
                release(i, 0, 0)
            } else {
                let f = P.toLocal(live[i], k, peel.fx, peel.fy)
                peel.flatten = 0
                peel.flatX = f.0
                peel.flatY = f.1
            }
            if active == i { active = -1 }
        }
        if live.indices.contains(peel.index) && settings.shapes.indices.contains(peel.index) {
            let p = peel
            let i = p.index
            let s = live[i]
            if p.flatten >= 0 {
                let f = p.flatten + dt
                if f >= M.flattenTime {
                    if p.detached {
                        peel.flatten = -1
                        peel.flatX = p.cx
                        peel.flatY = p.cy
                    } else {
                        peel = StickerBoardPeel()
                        release(i, 0, 0)
                    }
                } else {
                    peel.flatten = f
                }
            } else if !p.detached {
                let f = P.toLocal(s, k, p.fx, p.fy)
                if hypot(f.0 - p.cx, f.1 - p.cy) > M.detach * settings.shapes[i].extent {
                    peel.detached = true
                    peel.flatten = 0
                    peel.flatX = f.0
                    peel.flatY = f.1
                    peel.anchorX = s.x - p.fx
                    peel.anchorY = s.y - p.fy
                    if p.byUser { thud(.rip) }
                }
            }
        }
        let q = peel

        // A loose sticker near the bin: how near, whether the bin has it, and its pull on it.
        let hover = hoverPoint
        let loose = live.indices.contains(q.index) && q.detached
        let tx = loose ? q.fx + q.anchorX : hover.0
        let ty = loose ? q.fy + q.anchorY : hover.1
        // Near when either the sticker or the finger holding its corner is near.
        let dist = loose ? min(hypot(tx - hover.0, ty - hover.1), hypot(q.fx - hover.0, q.fy - hover.1)) : 0
        let near = loose ? P.clamp01((M.approach * k - dist) / ((M.approach - M.reach) * k)) : 0
        let wasSnapped = bin.snapped
        if !loose { bin.snapped = false } else if dist < M.reach * k { bin.snapped = true } else if dist > M.reach * M.reachOut * k { bin.snapped = false }
        if bin.snapped && !wasSnapped && q.byUser { thud(.snap) }
        if reduced {
            bin.magnet = bin.snapped ? 1 : 0
            bin.magnetV = 0
        } else {
            (bin.magnet, bin.magnetV) = P.spring(bin.magnet, bin.magnetV, bin.snapped ? 1 : 0, (M.magnetStiffness, M.magnetDamping), dt)
        }
        let magnet = loose ? bin.magnet : 0

        // Every sticker: carried ones lift and follow; dropped ones glide, fall and slap down; thrown
        // ones crumple, drop in and come back later.
        var landed = false
        var ballIn = 0
        var ballByUser = false
        var tossing = false
        for i in live.indices {
            var s = live[i]
            defer { live[i] = s }
            if s.rise < 1 {
                s.rise = min(1, s.rise + dt / M.riseTime)
                if s.rise >= 1 { s.riseUnder = -1 }
            }
            if s.gone >= 0 {
                let before = s.gone
                s.gone += dt
                if before < M.tossEnd && s.gone >= M.tossEnd {
                    ballIn += 1
                    if s.goneByUser { ballByUser = true }
                } else if s.gone < M.tossEnd {
                    tossing = true
                }
                if s.gone >= M.respawn {
                    let home = homeOf(i)
                    s.gone = -1
                    s.x = home.0
                    s.y = home.1
                    s.turn = stickers.indices.contains(i) ? stickers[i].turn : 0
                    s.scale = 1
                    s.lift = 1
                    s.sway = 0
                    s.held = false
                    s.falling = false
                    s.spawn = 0
                    s.squash = 0
                    s.squashSpeed = 0
                }
                continue
            }
            if s.spawn >= 0 {
                s.spawn += dt
                s.lift = 1 - P.clamp01(s.spawn / M.spawnTime)
                if s.spawn >= M.spawnTime {
                    s.spawn = -1
                    s.lift = 0
                    s.squash = reduced ? 0 : M.squash
                    s.squashSpeed = 0
                }
                continue
            }
            let peeled = q.index == i
            if peeled && q.detached {
                // Off the board: it follows the finger, held by the point it was pulled from, and is
                // pulled towards the bin's mouth once the bin has it.
                let gx = tx + (hover.0 - tx) * M.magnetPull * magnet
                let gy = ty + (hover.1 - ty) * M.magnetPull * magnet
                let follow = 1 - exp(-dt / M.follow)
                s.vx = (gx - s.x) * follow / dt
                s.x += (gx - s.x) * follow
                s.y += (gy - s.y) * follow
                s.lift += (M.detachedLift - s.lift) * (1 - exp(-dt / M.liftTime))
            } else if s.held {
                let target = peeled ? 0.15 : 1
                s.lift += (target - s.lift) * (1 - exp(-dt / M.liftTime))
            } else if s.falling {
                if !reduced {
                    s.x += s.vx * dt
                    s.y += s.vy * dt
                    let keep = exp(-dt / M.glide)
                    s.vx *= keep
                    s.vy *= keep
                }
                s.lift = max(0, s.lift - dt / M.fallTime)
                if s.lift <= 0 {
                    s.falling = false
                    s.squash = reduced ? 0 : M.squash
                    s.squashSpeed = 0
                    s.vx = 0
                    s.vy = 0
                    if s.byUser { landed = true }
                }
            }
            // It swings with its speed while carried, and settles when put down.
            let swayTo = s.held || (peeled && q.detached) ? max(-M.swayMax, min(M.swayMax, s.vx * M.swayPerSpeed)) : 0
            s.sway += (swayTo - s.sway) * (1 - exp(-dt / 0.12))
            if !s.sway.isFinite { s.sway = 0 }
            if s.squash != 0 || s.squashSpeed != 0 {
                (s.squash, s.squashSpeed) = P.spring(s.squash, s.squashSpeed, 0, (M.squashStiffness, M.squashDamping), dt)
                if abs(s.squash) < 0.0005 && abs(s.squashSpeed) < 0.01 {
                    s.squash = 0
                    s.squashSpeed = 0
                }
            }
        }
        if landed { thud(.drop) }

        // The bin: up while a corner is lifted or a ball is on its way; it leans towards a loose
        // sticker, grows a little, opens its lid as the sticker nears, and slams it once the last
        // ball is in.
        if ballIn > 0 {
            bin.landed = true
            bin.pending += ballIn
            if ballByUser { bin.byUser = true }
        }
        if bin.landed && !tossing {
            bin.landed = false
            bin.slam = true
            bin.slamHit = false
        }
        if bin.slam {
            if reduced {
                bin.lid = 0
                bin.lidV = -1
            } else {
                (bin.lid, bin.lidV) = P.spring(bin.lid, bin.lidV, 0, M.slam, dt)
            }
            if bin.lid <= 0 && bin.lidV <= 0 {
                bin.lid = 0
                bin.lidV = reduced ? 0 : -bin.lidV * M.lidBounce
                if !bin.slamHit {
                    bin.slamHit = true
                    if bin.byUser { thud(.slam) }
                    bin.byUser = false
                    bin.prev = bin.count
                    bin.count = (bin.count + max(1, bin.pending)) % 100
                    bin.pending = 0
                    bin.roll = reduced ? 1 : 0
                    bin.linger = M.linger
                    if !reduced {
                        bin.squashV += M.binSquashKick
                        bin.wobbleV += bin.count % 2 == 0 ? M.wobbleKick : -M.wobbleKick
                    }
                }
            }
            if bin.slamHit && bin.lid < 0.004 && abs(bin.lidV) < 0.06 {
                bin.slam = false
                bin.lid = 0
                bin.lidV = 0
            }
        } else {
            let open = tossing || bin.landed || bin.snapped ? 1 : pow(near, 1.3) * 0.85
            if reduced {
                bin.lid = open
                bin.lidV = 0
            } else {
                (bin.lid, bin.lidV) = P.spring(bin.lid, bin.lidV, open, M.lid, dt)
                if bin.lid < 0 {
                    bin.lid = 0
                    bin.lidV = 0
                }
            }
        }
        if !bin.slam && bin.linger > 0 { bin.linger = max(0, bin.linger - dt) }
        let up: Double = q.index >= 0 || tossing || bin.landed || bin.slam || bin.linger > 0 ? 1 : 0
        if reduced {
            bin.show += (up - bin.show) * (1 - exp(-dt / 0.12))
            bin.showV = 0
        } else {
            (bin.show, bin.showV) = P.spring(bin.show, bin.showV, up, M.show, dt)
            let lean = near * max(-1, min(1, ((tx + q.fx) / 2 - hover.0) / (120 * k))) * M.leanMax
            (bin.lean, bin.leanV) = P.spring(bin.lean, bin.leanV, loose ? lean : 0, M.lean, dt)
            (bin.grow, bin.growV) = P.spring(bin.grow, bin.growV, 1 + M.growNear * near + M.growMagnet * magnet, M.grow, dt)
            let sq = P.spring(bin.squash, bin.squashV, 0, M.binSquash, dt)
            bin.squash = max(-0.2, min(0.25, sq.0))
            bin.squashV = sq.1
            let wb = P.spring(bin.wobble, bin.wobbleV, 0, M.wobble, dt)
            bin.wobble = max(-12, min(12, wb.0))
            bin.wobbleV = wb.1
        }
        if bin.roll < 1 { bin.roll = min(1, bin.roll + dt / StickerBoardBinArt.rollTime) }
    }

    private func runScript(dt: Double, stickers: [StickerBoardSticker]) {
        let k = self.k
        let was = script
        var t = was + dt
        if t >= M.loop {
            t -= M.loop
            bin.resetCount()
        }
        func crossed(_ at: Double) -> Bool { t >= was ? (was < at && t >= at) : (was < at || t >= at) }
        for (m, move) in M.moves.enumerated() where live.indices.contains(move.index) && stickers.indices.contains(move.index) {
            if crossed(move.at) {
                bringUp(move.index)
                live[move.index].held = true
                live[move.index].byUser = false
                live[move.index].falling = false
            }
            if t >= move.at && t <= move.end {
                // Where the sticker was before this move: its last move's end, or home.
                var fx = 0.0, fy = 0.0, fTurn = 0.0, fScale = 1.0
                for q in 0..<m where M.moves[q].index == move.index {
                    fx = M.moves[q].dx
                    fy = M.moves[q].dy
                    fTurn = M.moves[q].turn
                    fScale = M.moves[q].scale
                }
                let p = P.easeInOut((t - move.at) / (move.end - move.at))
                let home = homeOf(move.index)
                let ex = (move.dx - fx) * k
                let ey = (move.dy - fy) * k
                let len = max(1, hypot(ex, ey))
                let bulge = sin(Double.pi * p) * M.arc * k
                live[move.index].x = home.0 + fx * k + ex * p + (-ey / len) * bulge
                live[move.index].y = home.1 + fy * k + ey * p + (ex / len) * bulge
                live[move.index].turn = stickers[move.index].turn + fTurn + (move.turn - fTurn) * p
                live[move.index].scale = fScale + (move.scale - fScale) * p
            }
            if crossed(move.end) { release(move.index, 0, 0) }
        }
        // The peel: press, lift the corner, pull it off, carry it over the bin, let go.
        guard live.indices.contains(M.peelIndex), stickers.indices.contains(M.peelIndex) else {
            script = t
            return
        }
        if crossed(M.peelPress) {
            if live[M.peelIndex].gone < 0 && active < 0 { pickUp(M.peelIndex, byUser: false) }
        }
        if crossed(M.peelStart) && active == M.peelIndex {
            let s = live[M.peelIndex]
            let def = stickers[M.peelIndex]
            let corner = P.toScreen(s, k, def.size.width, def.size.height)
            startPeel(M.peelIndex, corner.0, corner.1, byUser: false)
        }
        let p = peel
        if p.index == M.peelIndex && !p.byUser {
            if t >= M.peelStart && t < M.peelPulled {
                // The finger pulls the corner back across the sticker, past its middle.
                let e = P.easeInOut((t - M.peelStart) / (M.peelPulled - M.peelStart))
                var s = live[M.peelIndex]
                let lx = p.cx + (-M.peelReach * p.cx - p.cx) * e
                let ly = p.cy + (-M.peelReach * p.cy - p.cy) * e
                if p.detached {
                    let home = homeOf(M.peelIndex)
                    s.x = home.0
                    s.y = home.1
                    s.turn = stickers[M.peelIndex].turn
                    s.scale = 1
                }
                let f = P.toScreen(s, k, lx, ly)
                peel.fx = f.0
                peel.fy = f.1
            }
            if crossed(M.peelPulled) { scriptFinger = (p.fx, p.fy) }
            if t >= M.peelPulled && t < M.peelDrop && p.detached {
                // Carried on a curve to just off the bin's mouth, where the magnet takes it.
                let e = P.easeInOut(P.clamp01((t - M.peelPulled) / (M.peelCarried - M.peelPulled)))
                let a = scriptFinger
                let h = hoverPoint
                let bx = h.0 + M.carryTo.0 * k - p.anchorX
                let by = h.1 + M.carryTo.1 * k - p.anchorY
                let cx = (a.0 + bx) / 2 + 40 * k
                let cy = min(a.1, by) - 60 * k
                let u = 1 - e
                peel.fx = u * u * a.0 + 2 * u * e * cx + e * e * bx
                peel.fy = u * u * a.1 + 2 * u * e * cy + e * e * by
            }
            if crossed(M.peelDrop) { releasePeel(0, 0) }
        }
        script = t
    }

    // MARK: Touches

    // The fingers on the board, in the order they came down.
    private var fingers: [(id: Int, point: CGPoint)] = []
    private var downTime = 0.0
    // The averaged pan: where its centroid began, the offset that keeps it from jumping when a
    // finger is added or lifted, and recent samples for its speed.
    private var panOrigin = CGPoint.zero
    private var panOffset = CGPoint.zero
    private var samples: [(t: Double, x: Double, y: Double)] = []
    private var panActive = false
    private var twoActive = false
    private var liftActive = false
    private var liftFailed = false
    private var twoFrom = (distance: 1.0, angle: 0.0)

    private var centroid: CGPoint {
        guard !fingers.isEmpty else { return .zero }
        var x = 0.0, y = 0.0
        for f in fingers {
            x += f.point.x
            y += f.point.y
        }
        return CGPoint(x: x / Double(fingers.count), y: y / Double(fingers.count))
    }

    private var translation: CGPoint {
        let c = centroid
        return CGPoint(x: c.x - panOrigin.x + panOffset.x, y: c.y - panOrigin.y + panOffset.y)
    }

    private var velocity: (Double, Double) {
        guard let first = samples.first, let last = samples.last, last.t - first.t > 0.001 else { return (0, 0) }
        return ((last.x - first.x) / (last.t - first.t), (last.y - first.y) / (last.t - first.t))
    }

    private func sample() {
        let now = CACurrentMediaTime()
        let t = translation
        samples.append((now, t.x, t.y))
        samples.removeAll { now - $0.t > 0.1 }
    }

    /// Keeps the averaged translation where it was across a change in the number of fingers.
    private func rebase(_ change: () -> Void) {
        let before = translation
        change()
        let c = centroid
        panOffset = CGPoint(x: before.x - (c.x - panOrigin.x), y: before.y - (c.y - panOrigin.y))
        samples.removeAll()
        sample()
    }

    private func twoFingers() -> (distance: Double, angle: Double, mid: CGPoint)? {
        guard fingers.count >= 2 else { return nil }
        let a = fingers[0].point
        let b = fingers[1].point
        return (max(1, hypot(b.x - a.x, b.y - a.y)), atan2(b.y - a.y, b.x - a.x), CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2))
    }

    func touchesBegan(_ touches: [(Int, CGPoint)]) {
        for (id, point) in touches {
            if fingers.isEmpty {
                // A new gesture: each of the gestures begins on the sticker under the finger.
                fingers = [(id, point)]
                downTime = CACurrentMediaTime()
                panOrigin = point
                panOffset = .zero
                samples.removeAll()
                sample()
                panActive = false
                twoActive = false
                liftActive = false
                liftFailed = false
                touched()
                grab(point.x, point.y, byUser: true)
            } else {
                rebase { fingers.append((id, point)) }
            }
        }
        if fingers.count >= 2 && !twoActive && !liftActive {
            // The pinch and the rotation begin; the peel can no longer win.
            liftFailed = true
            if let two = twoFingers() {
                touched()
                grab(two.mid.x, two.mid.y, byUser: true)
                twoActive = true
                started = true
                twoFrom = (two.distance, two.angle)
                if live.indices.contains(active) {
                    scaleFrom = live[active].scale
                    turnFrom = live[active].turn
                }
            }
        }
    }

    func touchesMoved(_ touches: [(Int, CGPoint)]) {
        for (id, point) in touches {
            if let at = fingers.firstIndex(where: { $0.id == id }) { fingers[at].point = point }
        }
        guard !fingers.isEmpty else { return }
        sample()
        let t = translation
        let v = velocity
        if liftActive {
            let f = fingers[0].point
            if peel.index >= 0 && peel.byUser && (peel.flatten < 0 || peel.detached) {
                peel.fx = f.x
                peel.fy = f.y
            }
            return
        }
        if !panActive && hypot(t.x, t.y) >= M.panSlop {
            panActive = true
            liftFailed = true
            started = true
            if live.indices.contains(active) && peel.index < 0 { dragFrom = (live[active].x, live[active].y) }
        }
        if panActive, live.indices.contains(active), peel.index < 0 {
            live[active].x = P.rubber(dragFrom.0 + t.x, 12, stage.width - 12, 40)
            live[active].y = P.rubber(dragFrom.1 + t.y, stage.top + 12, stage.height - stage.bottom - 12, 40)
            live[active].vx = v.0
            live[active].vy = v.1
        }
        if twoActive, live.indices.contains(active), peel.index < 0, let two = twoFingers() {
            live[active].scale = P.rubber(scaleFrom * two.distance / twoFrom.distance, M.minScale, M.maxScale, 0.25)
            live[active].turn = turnFrom + (two.angle - twoFrom.angle) * 180 / .pi
        }
    }

    func touchesEnded(_ touches: [(Int, CGPoint)]) {
        let v = velocity
        for (id, point) in touches {
            if let at = fingers.firstIndex(where: { $0.id == id }) { fingers[at].point = point }
        }
        let ids = Set(touches.map(\.0))
        if fingers.count - fingers.filter({ ids.contains($0.id) }).count > 0 {
            rebase { fingers.removeAll { ids.contains($0.id) } }
            if fingers.count < 2 && twoActive {
                // The pinch ends: past a limit, the scale springs back inside it as the sticker lands.
                twoActive = false
                if live.indices.contains(active) { live[active].scale = max(M.minScale, min(M.maxScale, live[active].scale)) }
            }
            return
        }
        fingers.removeAll()
        if twoActive {
            twoActive = false
            if live.indices.contains(active) { live[active].scale = max(M.minScale, min(M.maxScale, live[active].scale)) }
        }
        if liftActive {
            liftActive = false
            if peel.byUser { releasePeel(v.0, v.1) }
            if peel.byUser { releasePeel(0, 0) }
            touched()
            return
        }
        // The pan ends (with its speed if it was moving), then every gesture is finalised.
        if panActive && peel.index < 0 { release(active, v.0, v.1) }
        if started && peel.index < 0 { release(active, 0, 0) }
        touched()
        pending = M.pendingFrames
        panActive = false
    }

    /// The long-press pan wins once the finger has been held still long enough.
    private func checkLongPress() {
        guard fingers.count == 1, !liftActive, !liftFailed, !panActive else { return }
        guard CACurrentMediaTime() - downTime >= M.longPress else { return }
        liftActive = true
        // The other gestures are cancelled: the pan is finalised.
        touched()
        pending = M.pendingFrames
        let f = fingers[0].point
        if active >= 0 && peel.index < 0 {
            startPeel(active, f.x, f.y, byUser: true)
            thud(.lift)
        }
    }
}
