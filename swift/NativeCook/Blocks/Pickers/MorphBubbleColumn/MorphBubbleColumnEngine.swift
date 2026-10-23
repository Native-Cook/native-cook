import SwiftUI
import UIKit

// MARK: - Metrics

enum MorphBubbleColumnMetrics {
    // Colours: a white screen with a faint dot grid, near-black ink; graphite shows in the rail's notch.
    static let page: UInt32 = 0x1d1d1b
    static let card: UInt32 = 0xf4f4f0
    static let gridDot: UInt32 = 0xc9c9c2
    static let ink: UInt32 = 0x141414
    static let muted: UInt32 = 0x8b8b84
    static let onPage: UInt32 = 0xecece6
    static let gridStep = 22.0

    // The card is the whole screen, edge to edge (the display's own corners round it). A notch cut
    // into its right edge holds the tool rail (pt).
    static let cardInset = 0.0
    static let cardRadius = 0.0
    static let notchAt = 0.56
    static let notchHeight = 128.0
    static let notchDepth = 34.0
    static let notchRadius = 13.0

    // The column: its middle sits this share of the card from the left; a bubble's unit size is this
    // share of the card's width, scaled by the zoom slider between zoomMin and zoomMax.
    static let columnX = 0.57
    static let unit = 0.21
    static let gap = 14.0
    static let zoomMin = 0.92
    static let zoomMax = 1.08
    // Each bubble sits a little off the column's axis, as in a hand-set column; the selected one centres.
    static let nudge = [0.08, -0.06, 0.07, -0.08, 0.05]
    // The selected bubble: a rounded square this many units wide and tall, with corners of this share
    // of half its side and a squircle shoulder of this exponent (2 is a circle's arc).
    static let bigW = 1.5
    static let bigH = 1.42
    static let bigR = 0.4
    static let bigN = 3.6

    // Selection: a number from 0 (top site) to the last site, on a spring. Under a finger it moves one
    // site per focusDrag pt and rubber-bands past the ends; on release the finger's speed carries it
    // focusThrow s further before it settles on the nearest site.
    static let focusDrag = 92.0
    static let focusRubber = 0.3
    static let focusThrow = 0.16
    static let handStiffness = 210.0
    static let handDamping = 2 * 0.82 * handStiffness.squareRoot()
    // When the loop or a tap moves it: a calmer, critically damped spring (about 0.8 s).
    static let calmStiffness = 70.0
    static let calmDamping = 2 * calmStiffness.squareRoot()

    // First appearance: every bubble blooms from a dot, top to bottom.
    static let dot = 0.1
    static let enterDelay = 0.2
    static let enterStagger = 0.11
    static let bloomOmega = 9.0
    static let bloomZeta = 0.8

    // Opening: a spring on 0 (in the column) … 1 (the whole card). A drag down by dismissShare of the
    // screen closes it fully; a release faster than dismissSpeed pt/s, or below closeBelow, closes it.
    static let openStiffness = 95.0
    static let openDamping = 17.5
    static let dismissShare = 0.6
    static let dismissSpeed = 650.0
    static let closeBelow = 0.8
    static let rubber = 0.22
    // The other bubbles sink back a little while one opens.
    static let sink = 0.08

    // Once open: the heading types typeStep s a character after typeDelay s, the reading counts for
    // countTime s after countDelay s, and the cursor blinks at cursorHz.
    static let typeDelay = 0.7
    static let typeStep = 0.06
    static let countDelay = 1.6
    static let countTime = 1.6
    static let cursorHz = 1.6
    // The reading is set in dot-matrix figures: pixels `pixel` pt apart, with this share of each left
    // as the gap between them.
    static let pixel = 8.0
    static let pixelGap = 0.24
    // The open photo zooms in with the slider: 1 + zoomPhoto × slider.
    static let zoomPhoto = 0.2

    // The zoom slider: rest value, and the spring that glides it when not held.
    static let sliderRest = 0.5
    static let sliderStiffness = 26.0
    static let sliderDamping = 10.2
    static let sliderSteps = 8.0

    // autoPlay (s): the selection walks down two sites, the third opens and is read while the slider
    // zooms in and out, then it closes and the selection walks back to the top, where the loop rests.
    static let loop = 18.0
    static let steps: [(Double, Int)] = [(1.2, 1), (2.7, 2), (14.2, 1), (15.6, 0)]
    static let openAt = 4.3
    static let zoomInAt = 8.6
    static let zoomInTo = 0.82
    static let zoomOutAt = 10.8
    static let closeAt = 12.4
    static let autoSite = 2
    // After a touch the loop waits this long; longer when the touch opened a photo, so it can be read.
    static let userHold = 4.0
    static let openHold = 8.0

    // Photos fade in over imageFade s as they arrive. Bubbles and the open card show the same portrait
    // crop, sized for each, so the sharp copy cross-fades over the thumbnail without a shift.
    static let imageFade = 0.35
    static let thumbSize = "w=400&h=880"
    static let sharpSize = "w=828&h=1820"

    static let easeOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))

    static func card(_ room: MorphBubbleColumnStage) -> CGRect {
        CGRect(x: cardInset, y: cardInset, width: room.width - 2 * cardInset, height: room.height - 2 * cardInset)
    }

    static func barTop(_ room: MorphBubbleColumnStage) -> Double {
        room.height - max(room.bottom + 4, 14) - 56
    }

    /// The slider track's ends and its touch band on screen.
    static func track(_ room: MorphBubbleColumnStage) -> (x0: Double, x1: Double, y: Double, top: Double, bottom: Double) {
        let top = barTop(room)
        return (cardInset + 10 + 112, room.width - cardInset - 10 - 22, top + 24, top, top + 56)
    }
}

// MARK: - Sites

/// One survey site down the column.
struct MorphBubbleColumnSite {
    /// Unsplash photo id (a portrait photo reads best, as it opens to the whole card).
    var id: String
    /// The site's name beside its bubble.
    var name: String
    /// Its position, shown under the name while the site is selected.
    var coords: String
    /// The heading that types itself once the photo is open, already broken into lines.
    var heading: [String]
    /// The reading that counts up in big dot-matrix figures.
    var value: Int
    /// Two short lines under the reading; the last one gets trailing dots.
    var caption: [String]
    /// A short readout under the target ring.
    var tag: String
    /// Where the target ring sits on the open photo, as shares of the card.
    var ring: (Double, Double)
    /// Text colour over the open photo.
    var ink: UInt32
    /// Flat colour shown until the photo arrives.
    var tint: UInt32

    static let samples: [MorphBubbleColumnSite] = [
        .init(id: "photo-1618336174403-79bfae755448", name: "GLACIER FRONT", coords: "64.02°N 16.21°W", heading: ["MEASURE", "ICE FRONT:", "74 M"], value: 74, caption: ["TRACKING_CALVING_", "FACE"], tag: "FRONT SKAFT-1", ring: (0.6, 0.42), ink: 0xffffff, tint: 0x9fb9cc),
        .init(id: "photo-1520811288788-6f2e3da049e0", name: "RED STRATA", coords: "36.86°N 111.37°W", heading: ["SAMPLE", "STRATA", "LAYER: 07"], value: 36, caption: ["MAPPING_RIDGE_", "LINES"], tag: "GRID K-12", ring: (0.62, 0.44), ink: 0xffffff, tint: 0x9a5a3a),
        .init(id: "photo-1524799526615-766a9833dec0", name: "DUNE FIELD", coords: "23.40°N 12.85°E", heading: ["CALCULATE", "AZIMUTH:", "345°"], value: 80, caption: ["ANALYZING_SURFACE_", "BIOME"], tag: "TARGET LUNA-2", ring: (0.51, 0.4), ink: 0xffffff, tint: 0x8fa9b8),
        .init(id: "photo-1686861453864-9b2a22a97fc9", name: "CRATER RIM", coords: "0.67°N 23.47°E", heading: ["MEASURE", "CRATER", "DEPTH: 2.4KM"], value: 12, caption: ["SCANNING_CRATER_", "FIELD"], tag: "RIM MARE-09", ring: (0.58, 0.46), ink: 0xffffff, tint: 0x6a6a68),
        .init(id: "photo-1576059620175-9b608f96797b", name: "ICE VAULT", coords: "64.40°N 16.80°W", heading: ["TRACE", "MELT RATE:", "0.8 M/DAY"], value: 24, caption: ["SOUNDING_ICE_", "CAVE"], tag: "VAULT SKAFT-3", ring: (0.5, 0.5), ink: 0xffffff, tint: 0x6f9cc0),
    ]
}

struct MorphBubbleColumnStage: Equatable {
    var width = 0.0
    var height = 0.0
    var top = 0.0
    var bottom = 0.0
}

/// A bubble this frame: its box, corners (share of half its shorter side), corner exponent, bloom
/// scale and how selected it is (0…1).
struct MorphBubbleColumnBox {
    var x, y, w, h, r, n, scale, big: Double
}

struct MorphBubbleColumnLoaded {
    let image: UIImage
    let at: Double
}

/// Everything one frame draws.
struct MorphBubbleColumnFrame {
    var stage: MorphBubbleColumnStage
    var now: Double
    var open: Double
    var openSite: Int
    var boxes: [MorphBubbleColumnBox]
    var thumbs: [MorphBubbleColumnLoaded?]
    var sharps: [MorphBubbleColumnLoaded?]
    var face: MorphBubbleColumnLoaded?
    var slider: Double
    var shownFor: Double
    var reducedMotion: Bool
}

// MARK: - Motion helpers

enum MorphBubbleColumnMath {
    private typealias M = MorphBubbleColumnMetrics

    static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    /// True when the loop clock went from `before` to `now` across `at`, wrapping at the loop.
    static func passed(_ before: Double, _ now: Double, _ at: Double) -> Bool {
        (before < at && now >= at) || (now < before && (at > before || at <= now))
    }

    /// The size multiplier of a bubble `t` s after it starts to bloom from a dot.
    static func bloomScale(_ t: Double) -> Double {
        if t <= 0 { return M.dot }
        let wd = M.bloomOmega * (1 - M.bloomZeta * M.bloomZeta).squareRoot()
        let decay = exp(-M.bloomZeta * M.bloomOmega * t)
        return 1 - (1 - M.dot) * decay * (cos(wd * t) + (M.bloomZeta * M.bloomOmega / wd) * sin(wd * t))
    }

    /// One semi-implicit Euler step of a spring, as the RN frame callback steps it.
    static func spring(_ value: inout Double, _ velocity: inout Double, _ target: Double, _ dt: Double, _ k: Double, _ c: Double) {
        velocity += (k * (target - value) - c * velocity) * dt
        value += velocity * dt
    }

    /// JavaScript's Math.round: halves round up.
    static func jsRound(_ v: Double) -> Double { (v + 0.5).rounded(.down) }

    /// A whole site index from a selection value, clamped to the column (NaN-safe).
    static func site(_ v: Double, count: Int) -> Int {
        guard v.isFinite, count > 0 else { return 0 }
        return Int(min(Double(count - 1), max(0, jsRound(v))))
    }

    /// Where each bubble sits this frame. A bubble is as much "selected" as the selection number is
    /// close to it, and grows from a circle into the big rounded square by that much. The column is
    /// laid out from those sizes, each bubble a gap below the one above, so a growing bubble pushes its
    /// neighbours instead of overlapping them; the whole column stays centred between the clock and the
    /// bar, and shrinks to fit if it would ever outgrow that room.
    static func columnBoxes(count: Int, focus: Double, room: MorphBubbleColumnStage, zoom: Double, since: Double, still: Bool) -> [MorphBubbleColumnBox] {
        let card = M.card(room)
        let unit = card.width * M.unit * mix(M.zoomMin, M.zoomMax, zoom)
        var boxes: [MorphBubbleColumnBox] = []
        var total = 0.0
        for s in 0..<max(0, count) {
            let big = clamp01(1 - abs(Double(s) - focus))
            // Corners ease more than size, so a half-selected bubble still reads as round.
            let soft = big * big * (3 - 2 * big)
            let scale = still ? 1 : bloomScale(since - M.enterDelay - Double(s) * M.enterStagger)
            boxes.append(MorphBubbleColumnBox(
                x: 0, y: 0,
                w: mix(1, M.bigW, big) * unit * scale,
                h: mix(1, M.bigH, big) * unit * scale,
                r: mix(1, M.bigR, soft),
                n: mix(2, M.bigN, soft),
                scale: scale,
                big: big
            ))
            total += mix(1, M.bigH, big) * unit + (s > 0 ? M.gap : 0)
        }
        let top = max(room.top, 50) + 92
        let bottom = M.barTop(room) - 18
        let fit = min(1, (bottom - top) / max(1, total))
        var y = (top + bottom) / 2 - total * fit / 2
        for s in boxes.indices {
            let slotH = mix(1, M.bigH, boxes[s].big) * unit * fit
            boxes[s].w *= fit
            boxes[s].h *= fit
            let cx = card.minX + card.width * M.columnX + M.nudge[s % M.nudge.count] * unit * (1 - boxes[s].big)
            boxes[s].x = cx - boxes[s].w / 2
            // A blooming bubble grows from the middle of its slot.
            boxes[s].y = y + (slotH - boxes[s].h) / 2
            y += slotH + M.gap * fit
        }
        return boxes
    }
}

// MARK: - The engine

/// The column's state, stepped once a frame like the RN frame callback, and the gestures that move it.
@MainActor
final class MorphBubbleColumnEngine {
    private typealias M = MorphBubbleColumnMetrics
    private typealias X = MorphBubbleColumnMath

    var stage = MorphBubbleColumnStage()
    private var count = 0
    private var boxes: [MorphBubbleColumnBox] = []
    // The selection: 0 (top site) … count − 1, its velocity and target; true while a finger set it.
    private var focus = 0.0
    private var focusVelocity = 0.0
    private var focusTarget = 0.0
    private var focusFrom = 0.0
    private var byHand = false
    private var lastFocusStep = 0
    // 0 in the column … 1 the whole card, its velocity and target; the site that opens.
    private var open = 0.0
    private var openVelocity = 0.0
    private var openTarget = 0.0
    private var openSite = -1
    private var openFrom = 0.0
    // Seconds since the open photo was asked to open.
    private var shownFor = 0.0
    // What a drag moves: 0 nothing, 1 the open photo, 2 the slider, 3 the selection.
    private var dragMode = 0
    private var slider = M.sliderRest
    private var sliderVelocity = 0.0
    private var sliderTarget = M.sliderRest
    private var lastStep = Int((M.sliderRest * M.sliderSteps).rounded())
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private(set) var elapsed = 0.0
    private var lastDate: Date?
    private var configuredAuto: Bool?
    private var reducedMotion = false

    private var thumbs: [MorphBubbleColumnLoaded?] = []
    private var sharps: [MorphBubbleColumnLoaded?] = []
    private var face: MorphBubbleColumnLoaded?

    // Touch tracking: a pan starts after 4 pt of travel; a touch lifted within 400 ms that never got
    // that far is a tap.
    private var touchStart: Date?
    private var panning = false

    /// Called when a site opens (its index, and whether a finger opened it).
    var onOpened: ((Int, Bool) -> Void)?
    var onCloseByHand: (() -> Void)?
    /// A selection tick: the slider passed a step, a dragged selection passed a site, or a tap moved it.
    var onTick: (() -> Void)?

    // MARK: Photos

    func resetPhotos(count: Int) {
        thumbs = Array(repeating: nil, count: count)
        sharps = Array(repeating: nil, count: count)
    }

    func thumbArrived(_ image: UIImage, at index: Int) {
        guard thumbs.indices.contains(index) else { return }
        thumbs[index] = MorphBubbleColumnLoaded(image: image, at: elapsed)
    }

    func faceArrived(_ image: UIImage) {
        face = MorphBubbleColumnLoaded(image: image, at: elapsed)
    }

    func hasSharp(_ index: Int) -> Bool {
        sharps.indices.contains(index) && sharps[index] != nil
    }

    func sharpArrived(_ image: UIImage, index: Int) {
        guard sharps.indices.contains(index), sharps[index] == nil else { return }
        sharps[index] = MorphBubbleColumnLoaded(image: image, at: elapsed)
    }

    // MARK: Script

    /// Sends the selection to `index` on the calm spring.
    private func select(_ index: Int) {
        focusTarget = Double(min(count - 1, max(0, index)))
        byHand = false
        if reducedMotion {
            focus = focusTarget
            focusVelocity = 0
        }
    }

    private func openFocused(user: Bool) {
        guard count > 0 else { return }
        let index = X.site(focusTarget, count: count)
        openSite = index
        openTarget = 1
        shownFor = 0
        onOpened?(index, user)
    }

    private func close(_ velocity: Double) {
        openTarget = 0
        openVelocity = velocity
    }

    // MARK: Frame

    func step(to date: Date, stage room: MorphBubbleColumnStage, count: Int, autoPlay: Bool, reducedMotion: Bool) -> MorphBubbleColumnFrame {
        stage = room
        self.count = count
        self.reducedMotion = reducedMotion
        let auto = autoPlay && !reducedMotion
        if configuredAuto != auto {
            configuredAuto = auto
            looping = auto
            clock = 0
            hold = 0
        }
        let dt = lastDate.map { min(max(date.timeIntervalSince($0), 0), 0.064) } ?? 0.016
        lastDate = date
        if dt > 0, count > 0 { advance(dt) }
        return MorphBubbleColumnFrame(
            stage: room, now: elapsed, open: open, openSite: openSite, boxes: boxes,
            thumbs: thumbs, sharps: sharps, face: face, slider: slider, shownFor: shownFor, reducedMotion: reducedMotion
        )
    }

    private func advance(_ dt: Double) {
        elapsed += dt
        let isOpen = openSite >= 0

        if hold > 0 {
            hold -= dt
            if hold <= 0 && looping {
                // The loop takes over again from its resting state: closed, the top site selected.
                clock = 0
                if isOpen { close(0) }
                sliderTarget = M.sliderRest
                select(0)
            }
        } else if looping {
            let before = clock
            let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            for step in M.steps where X.passed(before, t, step.0) { select(step.1) }
            if X.passed(before, t, M.openAt) && !isOpen && count > M.autoSite {
                select(M.autoSite)
                openFocused(user: false)
            }
            if X.passed(before, t, M.zoomInAt) { sliderTarget = M.zoomInTo }
            if X.passed(before, t, M.zoomOutAt) { sliderTarget = M.sliderRest }
            if X.passed(before, t, M.closeAt) && isOpen { close(0) }
        }

        if dragMode != 3 {
            let k = byHand ? M.handStiffness : M.calmStiffness
            let c = byHand ? M.handDamping : M.calmDamping
            X.spring(&focus, &focusVelocity, focusTarget, dt, k, c)
            if !focus.isFinite { focus = focusTarget }
            if !focusVelocity.isFinite { focusVelocity = 0 }
        }

        if dragMode != 1 {
            if reducedMotion {
                // Without spatial motion the photo only fades in and out.
                let gap = openTarget - open
                open += (gap > 0 ? 1 : gap < 0 ? -1 : 0) * min(abs(gap), dt / 0.25)
                openVelocity = 0
            } else {
                X.spring(&open, &openVelocity, openTarget, dt, M.openStiffness, M.openDamping)
            }
            if openTarget == 0 && open < 0.002 && abs(openVelocity) < 0.05 {
                open = 0
                openVelocity = 0
                openSite = -1
            }
        }
        if openSite >= 0 { shownFor += dt }

        if dragMode != 2 {
            X.spring(&slider, &sliderVelocity, sliderTarget, dt, M.sliderStiffness, M.sliderDamping)
        }
        // A tick each time a dragged selection passes a site, or the slider passes a step by hand.
        let stepNow = Int((slider * M.sliderSteps).rounded())
        if stepNow != lastStep && dragMode == 2 { onTick?() }
        lastStep = stepNow
        let siteNow = X.site(focus, count: count)
        if siteNow != lastFocusStep && dragMode == 3 { onTick?() }
        lastFocusStep = siteNow

        boxes = X.columnBoxes(count: count, focus: focus, room: stage, zoom: slider, since: elapsed, still: reducedMotion)
    }

    // MARK: Touch

    private func onSlider(_ x: Double, _ y: Double) -> Bool {
        let t = M.track(stage)
        return x > t.x0 - 18 && x < t.x1 + 18 && y > t.top && y < t.bottom
    }

    private func setSlider(from x: Double) {
        let t = M.track(stage)
        // The track is never narrower than a point, so a value never comes out NaN.
        let value = X.clamp01((x - t.x0) / max(1, t.x1 - t.x0))
        slider = value
        sliderTarget = value
        sliderVelocity = 0
    }

    func touchChanged(_ value: DragGesture.Value) {
        if touchStart == nil {
            touchStart = value.time
            panning = false
        }
        let dx = value.translation.width
        let dy = value.translation.height
        if !panning {
            guard dx * dx + dy * dy >= 16 else { return }
            panning = true
            panStart(value.startLocation)
        }
        panUpdate(x: value.location.x, translationY: dy)
    }

    func touchEnded(_ value: DragGesture.Value) {
        let start = touchStart ?? value.time
        touchStart = nil
        if panning {
            panning = false
            panEnd(velocityY: value.velocity.height)
        } else if value.time.timeIntervalSince(start) <= 0.4 {
            tap(x: value.location.x, y: value.location.y)
        }
    }

    /// The system took the touch away (onEnded never fires for that): whatever the drag held is let
    /// go, and a dragged selection settles on the nearest site.
    func touchCancelled() {
        guard touchStart != nil else { return }
        touchStart = nil
        guard panning else { return }
        panning = false
        if dragMode == 3 {
            focusTarget = Double(X.site(focus, count: count))
            byHand = true
        }
        dragMode = 0
    }

    private func panStart(_ start: CGPoint) {
        hold = openSite >= 0 ? M.openHold : M.userHold
        if onSlider(start.x, start.y) {
            dragMode = 2
            setSlider(from: start.x)
            return
        }
        if openSite >= 0 {
            if openTarget == 1 {
                dragMode = 1
                openFrom = open
            } else {
                dragMode = 0
            }
            return
        }
        // Anywhere on the column screen, a drag moves the selection with the finger.
        dragMode = 3
        focusFrom = focus
        focusVelocity = 0
    }

    private func panUpdate(x: Double, translationY: Double) {
        switch dragMode {
        case 2:
            setSlider(from: x)
        case 1:
            let distance = max(1, stage.height * M.dismissShare)
            let p = openFrom - translationY / distance
            open = p > 1 ? 1 + (p - 1) * M.rubber : max(0, p)
        case 3:
            let raw = focusFrom + translationY / M.focusDrag
            let top = Double(max(0, count - 1))
            // Past either end the selection gives way less and less.
            let over = raw < 0 ? raw : raw > top ? raw - top : 0
            let give = M.focusRubber * over / (M.focusRubber + abs(over))
            focus = raw < 0 ? give : raw > top ? top + give : raw
        default:
            break
        }
    }

    private func panEnd(velocityY: Double) {
        let mode = dragMode
        dragMode = 0
        if mode == 1 {
            let distance = max(1, stage.height * M.dismissShare)
            // The finger's speed carries into the spring, in opens per second.
            let velocity = -velocityY / distance
            if velocityY > M.dismissSpeed || (open < M.closeBelow && velocityY > -M.dismissSpeed) {
                close(velocity)
                onCloseByHand?()
            } else {
                openTarget = 1
                openVelocity = velocity
            }
        } else if mode == 3 {
            // The finger's speed, in sites per second, is handed to the spring; a flick carries on.
            let velocity = velocityY.isFinite ? velocityY / M.focusDrag : 0
            focusTarget = Double(X.site(focus + velocity * M.focusThrow, count: count))
            focusVelocity = reducedMotion ? 0 : velocity
            byHand = true
            if reducedMotion { focus = focusTarget }
        }
    }

    private func tap(x: Double, y: Double) {
        if onSlider(x, y) {
            hold = openSite >= 0 ? M.openHold : M.userHold
            let t = M.track(stage)
            sliderTarget = X.clamp01((x - t.x0) / max(1, t.x1 - t.x0))
            onTick?()
            return
        }
        guard openSite < 0 else { return }
        for (s, b) in boxes.enumerated() {
            // A little slop around the small bubbles keeps them easy to hit.
            let pad = max(0, (44 - min(b.w, b.h)) / 2)
            if x > b.x - pad && x < b.x + b.w + pad && y > b.y - pad && y < b.y + b.h + pad {
                if s == X.site(focusTarget, count: count) && abs(focus - Double(s)) < 0.35 {
                    hold = M.openHold
                    openFocused(user: true)
                } else {
                    hold = M.userHold
                    select(s)
                    onTick?()
                }
                return
            }
        }
    }
}
