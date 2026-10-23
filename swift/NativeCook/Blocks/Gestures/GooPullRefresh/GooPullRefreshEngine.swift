import SwiftUI
import UIKit

enum GooPullRefreshMetrics {
    // The page, the header, the text greys and the one accent: the liquid of the refresh drop.
    static let page: UInt32 = 0xf4f2ee
    static let header: UInt32 = 0xffffff
    static let ink: UInt32 = 0x1b1a18
    static let muted: UInt32 = 0x8c877f
    static let chip: UInt32 = 0xefece7
    static let avatar: UInt32 = 0xe6e1d9
    static let photoBlank: UInt32 = 0xe7e3dc
    static let accent: UInt32 = 0xff5a2c

    // Layout, in pt: the header under the safe area, the feed's margins and each tile's parts.
    // Tiles are a photo of the column's width (its height from the shape) over `textH` of text.
    static let headerH: CGFloat = 102
    static let listTop: CGFloat = 12
    static let side: CGFloat = 12
    static let colGap: CGFloat = 10
    static let rowGap: CGFloat = 18
    static let textH: CGFloat = 46

    // The liquid lives behind the header's bottom edge: a reservoir whose meniscus sags out under
    // the edge as the feed is pulled, on a springy surface.
    static let gooBlur = 4.5
    static let resRX = 30.0
    static let resRY = 14.0
    static let menRest = -9.0
    static let menPull = 9.0
    static let menFull = 40.0
    static let menStiffness = 380.0
    static let menDamping = 13.0
    // Pulling draws a drop out of it on a neck of beads that thins until it snaps.
    static let dropTravel = 0.62
    static let beadR = 5.0
    static let dropR = 18.0
    static let neckBeads = 10
    static let neckWide = 11.0
    static let neckThin = 3.4
    static let snapPull = 108.0
    // After the snap: the neck melts, the drop gets a push and springs to the middle of the gap,
    // stretching with its speed and jiggling.
    static let neckCut = 26.0
    static let snapKick = 260.0
    static let dropStiffness = 260.0
    static let dropDamping = 18.0
    static let stretchPerSpeed = 1.0 / 1800
    static let jiggle = 0.16
    static let jiggleHz = 7.0
    static let jiggleDecay = 6.0
    // Refreshing: a gooey loader of a core and three droplets that breathe out and back as the
    // trio turns, then merge into one drop that the header draws back in.
    static let holdOpen = 100.0
    static let coreR = 13.0
    static let satR = 9.0
    static let orbit = 30.0
    static let pulse = 1.1
    static let turn = 250.0
    static let spreadIn = 0.3
    static let refreshTime = 1.5
    static let mergeTime = 0.35
    static let mergedR = 17.0
    static let returnStiffness = 200.0
    static let returnDamping = 20.0
    static let regrow = 0.5

    // Scrolling: iOS-like deceleration, resistance past either end over `rubberSpan` of the screen.
    static let deceleration = 2.1
    static let edgeStiffness = 180.0
    static let edgeDamping = 26.8
    static let rubberSpan = 0.5
    static let dragSlop = 2.0
    // The new row's arrival: a spring on how many rows are still hidden above the feed.
    static let revealStiffness = 70.0
    static let revealDamping = 15.0

    // autoPlay (seconds): pull, refresh, read down the feed, back to the top, rest.
    static let loop = 9.0
    static let pullFrom = 0.6
    static let pullTo = 1.8
    static let pullRaw = 300.0
    static let downFrom = 5.3
    static let downTo = 6.9
    static let downBy = 1100.0
    static let swapAt = 7.0
    static let upFrom = 7.2
    static let upTo = 8.4
    static let userHold = 3.5

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    static func easeOut(_ u: Double) -> Double { 1 - pow(1 - clamp01(u), 3) }
    static func easeInOut(_ u: Double) -> Double {
        let x = clamp01(u)
        return x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    /// iOS-style resistance: `x` pt past an edge shows as at most `span`.
    static func rubber(_ x: Double, _ span: Double) -> Double { (1 - 1 / ((x * 0.55) / span + 1)) * span }

    /// The finger travel that `rubber` turned into `r`.
    static func unrubber(_ r: Double, _ span: Double) -> Double {
        let k = min(0.999, r / span)
        return (span / 0.55) * (1 / (1 - k) - 1)
    }

    /// How far the hidden rows reach down a column when `h` of them (a fraction mid-arrival) are hidden.
    static func hiddenReach(_ prefix: [Double], _ h: Double) -> Double {
        let n = prefix.count - 1
        guard n > 0 else { return 0 }
        let x = min(Double(n), max(0, h))
        let k = min(n - 1, Int(x.rounded(.down)))
        return prefix[k] + (prefix[k + 1] - prefix[k]) * (x - Double(k))
    }

    /// Where the drop rests in a gap `gap` tall: in its middle, riding up under the header as it closes.
    static func dropAim(_ bar: Double, _ gap: Double) -> Double { bar + min(gap / 2, gap - holdOpen / 2) }
}

/// The drop's phases.
enum GooPullRefreshPhase { case idle, free, loading, merging, returning }

/// One frame to draw.
struct GooPullRefreshFrame {
    var scroll: Double
    var head: Double
    var phase: GooPullRefreshPhase
    var phaseTime: Double
    var men: Double
    var dropY: Double
    var dropV: Double
    var dropR: Double
    var neck: Double
    var jiggleAge: Double
    var spin: Double
    var grow: Double
}

/// The feed's size, which the motion needs.
struct GooPullRefreshStage {
    var height: Double
    var barBottom: Double
    var bottomInset: Double
    /// Total height of the taller column, hidden rows included.
    var total: Double
    /// How far down a column the first `k` hidden rows reach, for k = 0…hidden.
    var prefix: [Double]
    var hidden: Int
    var autoPlay: Bool
    var reducedMotion: Bool
    var onRefresh: (() -> Void)?
}

/// The feed's state, stepped once a frame like the RN frame callback, and the drag that pulls it.
@MainActor
final class GooPullRefreshEngine {
    private typealias M = GooPullRefreshMetrics
    private enum Mode { case drag, free, script }

    // The feed's offset: 0 at the top, more when pulled down, less when scrolled.
    private var scroll = 0.0
    private var velocity = 0.0
    private var mode = Mode.free
    private var dragFrom = 0.0
    // How far the feed may rest below the header: 0, or `holdOpen` while refreshing.
    private var ceiling = 0.0
    // How many rows are still hidden above the feed, and where that is heading.
    private var head: Double?
    private var headTarget = 0.0
    private var headVelocity = 0.0

    private var phase = GooPullRefreshPhase.idle
    private var phaseTime = 0.0
    private var men = M.menRest
    private var menV = 0.0
    private var dropY = 0.0
    private var dropV = 0.0
    private var dropR = M.dropR
    private var neck = 0.0
    private var jiggleAge = 10.0
    private var spin = 0.0
    private var grow = 1.0
    private var byUser = false

    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var segment = -1
    private var segFrom = 0.0
    private var lastDate: Date?
    private var lastAuto: Bool?
    private var stage = GooPullRefreshStage(height: 0, barBottom: 0, bottomInset: 0, total: 0, prefix: [0], hidden: 0, autoPlay: false, reducedMotion: false)
    // The touch under way.
    private var panning = false

    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let light = UIImpactFeedbackGenerator(style: .light)

    func step(to date: Date, stage: GooPullRefreshStage) -> GooPullRefreshFrame {
        self.stage = stage
        if head == nil {
            head = Double(stage.hidden)
            headTarget = Double(stage.hidden)
        }
        if lastAuto != stage.autoPlay {
            lastAuto = stage.autoPlay
            looping = stage.autoPlay
            clock = 0
            hold = 0
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        let dt = min(raw, 0.064)
        if dt > 0 && stage.height > 0 { advance(dt) }
        return GooPullRefreshFrame(
            scroll: scroll, head: head ?? 0, phase: phase, phaseTime: phaseTime, men: men, dropY: dropY, dropV: dropV,
            dropR: dropR, neck: neck, jiggleAge: jiggleAge, spin: spin, grow: grow
        )
    }

    /// The lowest the feed can scroll, from how many rows are showing.
    private func floorOf(_ h: Double) -> Double {
        let content = M.listTop + stage.total - M.hiddenReach(stage.prefix, h) + stage.bottomInset + 16
        return min(0, stage.height - stage.barBottom - content)
    }

    /// Where a finger's travel puts the feed, with resistance past either end.
    private func bounded(_ raw: Double) -> Double {
        let span = stage.height * M.rubberSpan
        let top = ceiling
        let bottom = floorOf(head ?? 0)
        if raw > top { return top + M.rubber(raw - top, span) }
        if raw < bottom { return bottom - M.rubber(bottom - raw, span) }
        return raw
    }

    /// The neck breaks: the drop hangs free, jiggling, and the meniscus springs back behind the edge.
    private func snap(_ pull: Double) {
        phase = .free
        phaseTime = 0
        dropY = stage.barBottom + pull * M.dropTravel
        dropV = stage.reducedMotion ? 0 : M.snapKick
        dropR = M.dropR
        neck = 1
        jiggleAge = stage.reducedMotion ? 10 : 0
        if byUser {
            medium.impactOccurred()
            stage.onRefresh?()
        }
    }

    /// The finger lets go: a free drop starts the refresh and the feed waits under the header.
    private func release(_ v: Double) {
        velocity = v
        mode = .free
        if phase == .free {
            phase = .loading
            phaseTime = 0
            ceiling = M.holdOpen
        }
    }

    /// Puts the arrived rows back above the feed while they are out of sight, so the loop can repeat.
    private func restock() {
        let h = head ?? 0
        let hidden = stage.hidden
        if h >= Double(hidden) - 0.01 || stage.prefix.count <= hidden { return }
        let bar = stage.barBottom
        // The last of them must be above the header: nothing on screen may move.
        let lowest = bar + M.listTop + scroll + stage.prefix[hidden] - M.rowGap - M.hiddenReach(stage.prefix, h)
        if lowest > bar - 4 { return }
        scroll += stage.prefix[hidden] - M.hiddenReach(stage.prefix, h)
        head = Double(hidden)
        headTarget = Double(hidden)
        headVelocity = 0
    }

    private func advance(_ dt: Double) {
        let bar = stage.barBottom
        let reduced = stage.reducedMotion

        // The script stands in for a finger.
        if mode != .drag && hold > 0 {
            hold -= dt
            if hold <= 0 && looping {
                restock()
                clock = abs(scroll) > 4 ? M.upFrom : 0
                segment = -1
            }
        } else if looping && mode != .drag {
            let before = clock
            let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            let active = t >= M.pullFrom && t < M.pullTo ? 0 : t >= M.downFrom && t < M.downTo ? 1 : t >= M.upFrom && t < M.upTo ? 2 : -1
            if active != segment {
                if segment == 0 {
                    release(0)
                } else if segment >= 1 {
                    velocity = 0
                    mode = .free
                }
                if active >= 0 {
                    byUser = false
                    segFrom = scroll
                    mode = .script
                }
                segment = active
            }
            if before < M.swapAt && t >= M.swapAt { restock() }
            if active >= 0 {
                let next: Double
                switch active {
                case 0: next = bounded(M.pullRaw * M.easeInOut((t - M.pullFrom) / (M.pullTo - M.pullFrom)))
                case 1: next = segFrom - M.downBy * M.easeOut((t - M.downFrom) / (M.downTo - M.downFrom))
                default: next = segFrom * (1 - M.easeInOut((t - M.upFrom) / (M.upTo - M.upFrom)))
                }
                velocity = (next - scroll) / dt
                scroll = next
            }
        }

        // The feed coasts, and springs back from past either end.
        if mode == .free {
            let top = ceiling
            let bottom = floorOf(head ?? 0)
            let s = scroll
            var v = velocity
            if s > top + 0.01 || s < bottom - 0.01 {
                let edge = s > top ? top : bottom
                v += (M.edgeStiffness * (edge - s) - M.edgeDamping * v) * dt
                var next = s + v * dt
                if (s > top && next < top) || (s < bottom && next > bottom) {
                    next = edge
                    v = 0
                }
                scroll = reduced ? edge : next
            } else {
                v *= exp(-M.deceleration * dt)
                if abs(v) < 4 { v = 0 }
                scroll = s + v * dt
            }
            velocity = v
        }

        // The new row arrives on a spring.
        let h = head ?? 0
        let hv = headVelocity + (M.revealStiffness * (headTarget - h) - M.revealDamping * headVelocity) * dt
        headVelocity = hv
        head = reduced ? headTarget : h + hv * dt

        // The liquid.
        let p = phase
        phaseTime += dt
        let gap = max(0, scroll)
        if p == .idle && gap >= M.snapPull && mode != .free && grow >= 1 { snap(gap) }

        // The meniscus follows the pull while attached, sags to meet a returning drop, else rests.
        var menGoal = M.menRest
        if p == .idle {
            menGoal = M.mix(M.menRest, M.menPull, M.clamp01(gap / M.menFull) * grow)
        } else if p == .returning {
            menGoal = M.menPull
        }
        menV += (M.menStiffness * (menGoal - men) - M.menDamping * menV) * dt
        men = reduced ? menGoal : men + menV * dt

        if p != .idle {
            // The drop hangs in the middle of the gap, riding up under the header as the gap
            // closes; returning, it is drawn up behind the header's edge.
            let aim = p == .returning ? bar - M.mergedR - 10 : M.dropAim(bar, gap)
            let k = p == .returning ? M.returnStiffness : M.dropStiffness
            let c = p == .returning ? M.returnDamping : M.dropDamping
            dropV += (k * (aim - dropY) - c * dropV) * dt
            dropY = reduced ? aim : dropY + dropV * dt
            neck *= exp(-M.neckCut * dt)
            jiggleAge += dt
        }
        if p == .loading || p == .merging { spin = (spin + M.turn * dt).truncatingRemainder(dividingBy: 360) }
        switch p {
        case .loading:
            dropR = M.mix(M.dropR, M.coreR, M.easeOut(phaseTime / M.spreadIn))
            if phaseTime >= M.refreshTime {
                phase = .merging
                phaseTime = 0
            }
        case .merging:
            dropR = M.mix(M.coreR, M.mergedR, M.easeInOut(phaseTime / M.mergeTime))
            if phaseTime >= M.mergeTime {
                // The new row is in: the header draws the drop back in.
                phase = .returning
                phaseTime = 0
                dropV = 0
            }
        case .returning where dropY + dropR < bar + men - 2:
            // Swallowed: the feed closes and the new row slides in on top.
            phase = .idle
            phaseTime = 0
            grow = 0
            ceiling = 0
            headTarget = max(0, headTarget.rounded() - 1)
            if mode == .free && velocity > 0 { velocity = 0 }
            if byUser { light.impactOccurred() }
        default:
            break
        }
        if phase == .idle && grow < 1 { grow = M.clamp01(grow + dt / M.regrow) }
    }

    // MARK: Gestures

    func touchMoved(_ value: DragGesture.Value) {
        if !panning {
            guard hypot(value.translation.width, value.translation.height) >= M.dragSlop else { return }
            panning = true
            hold = M.userHold
            byUser = true
            segment = -1
            // Caught where it is; past an edge, the finger takes up the stretch it would have made.
            let s = scroll
            let span = stage.height * M.rubberSpan
            let top = ceiling
            let bottom = floorOf(head ?? 0)
            dragFrom = s > top ? top + M.unrubber(s - top, span) : s < bottom ? bottom - M.unrubber(bottom - s, span) : s
            mode = .drag
        }
        guard mode == .drag else { return }
        hold = M.userHold
        scroll = bounded(dragFrom + value.translation.height)
    }

    func touchEnded(_ value: DragGesture.Value) {
        defer { panning = false }
        guard panning, mode == .drag else { return }
        hold = M.userHold
        release(stage.reducedMotion ? 0 : value.velocity.height)
    }
}

/// Downloads a photo once and keeps it decoded.
enum GooPullRefreshPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    static func cached(_ address: String) -> UIImage? { cache.object(forKey: address as NSString) }

    static func load(_ address: String) async -> UIImage? {
        if let hit = cached(address) { return hit }
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
