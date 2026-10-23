import CoreText
import SwiftUI
import UIKit

// MARK: - Metrics

enum ParticleWordMorphMetrics {
    // The words and their two colours each, used when the caller passes none.
    static let words = ["SPARK", "DRIFT", "BLOOM", "ORBIT", "GLOW"]
    static let palettes: [(UInt32, UInt32)] = [
        (0xffb36b, 0xff5f7e),
        (0x6be4ff, 0x4f7dff),
        (0xff8ad8, 0xa77bff),
        (0xc9b8ff, 0x6fa8ff),
        (0x9dffcb, 0xe8ff7a),
    ]

    // The night behind the words: deep navy, lifted in the middle and darker at the corners.
    static let night: UInt32 = 0x060a1c
    static let nightGlow: UInt32 = 0x152255
    static let nightEdge: UInt32 = 0x020309

    // Particles: how many, the grid step (pt) they are sampled from inside each word's glyphs, and the
    // sprite each one is drawn with (a soft dot `sprite` wide, drawn at spriteScale × its size).
    static let count = 2600
    static let sampleStep = 2.1
    static let sprite = 16.0
    static let spriteScale = 0.2
    // A word is set to fit wordMargin pt in from each side (so a scattered word still clears the
    // edges), at most wordMax pt tall, its middle at wordAt of the screen height.
    static let wordMargin = 44.0
    static let wordMax = 128.0
    static let wordAt = 0.46

    // A morph: each particle travels for `travel` s, starting up to `spread` s late (left to right,
    // with a little noise), along an arc that bows `swirl` pt off the straight line in a direction set
    // by a slowly turning field. Idle particles shimmer by `shimmer` pt.
    static let travel = 1.1
    static let spread = 0.5
    static let swirl = 70.0
    static let shimmer = 0.7
    static let easeInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.65, y: 0), endControlPoint: UnitPoint(x: 0.35, y: 1))
    // Colours shift to the next word's over colorTime s; a halo of glowBlur pt in the word's colour.
    static let colorTime = 1.2
    static let glowBlur = 5.0
    // The word indicator: the words themselves in small tracked capitals, labelGap pt apart, the one
    // on screen lit and underlined by a hairline (markHeight pt) that glides to the next word as it
    // forms, its width following the words' widths. The mark rides one critically damped spring (about
    // 0.9 s, close to the particles' own flight). The labels' middle sits rowAbove pt above the bottom
    // safe area, the hint's bottom hintAbove pt above that.
    static let labelGap = 22.0
    static let labelSize = 11.0
    static let markHeight = 1.5
    static let markBelow = 16.0
    static let rowAbove = 44.0
    static let hintAbove = 26.0
    static let dim = 0.36
    // Tracking of the labels (pt); it follows the last letter too, so the mark leaves it out.
    static let tracking = 2.4
    static let markSpring = (stiffness: 60.0, damping: 2 * 60.0.squareRoot())
    static let ink: UInt32 = 0xeef1ff
    // Edges kept clear around the labels at the top and sides, in pt.
    static let edge = 28.0

    // A finger pushes particles within `reach` pt away, harder the closer they are (`push` pt/s² at
    // the finger); each one is held to its place by a spring that brings it back with a small
    // overshoot once the finger moves on.
    static let reach = 92.0
    static let push = 5200.0
    static let stiffness = 46.0
    static let damping = 7.5
    // The finger's strength fades in and out over this long (s), so a touch never jolts the field.
    static let fingerEase = 0.12

    // autoPlay (seconds): each word holds, then flows into the next every wordEvery s, starting at
    // firstMorph. While the third word holds, a ghost finger passes through it.
    static let loop = 16.0
    static let wordEvery = 3.2
    static let firstMorph = 1.2
    static let ghostFrom = 6.1
    static let ghostTime = 1.1
    // After a touch the loop waits this long, then carries on from the word on screen.
    static let userHold = 4.0
}

// MARK: - Sampling

/// A small deterministic random generator (the React Native version's), so every run samples the
/// same particles.
struct ParticleWordMorphRandom {
    private var s: UInt32
    init(_ seed: UInt32) { s = seed }
    mutating func next() -> Double {
        s = s &* 1664525 &+ 1013904223
        return Double(s) / 4294967296
    }
}

enum ParticleWordMorphSampler {
    private typealias M = ParticleWordMorphMetrics

    static func font(_ size: Double) -> CTFont {
        CTFontCreateWithName("AvenirNext-Heavy" as CFString, size, nil)
    }

    private static func glyphs(_ word: String, _ font: CTFont) -> ([CGGlyph], [CGSize]) {
        let chars = Array(word.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count)
        var advances = [CGSize](repeating: .zero, count: glyphs.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &advances, glyphs.count)
        return (glyphs, advances)
    }

    /// `word`'s outline set from the origin on a baseline at y = 0, glyph by glyph at their advances.
    static func outline(_ word: String, _ font: CTFont) -> CGPath {
        let (glyphs, advances) = glyphs(word, font)
        let path = CGMutablePath()
        var pen = 0.0
        for (i, glyph) in glyphs.enumerated() {
            if let shape = CTFontCreatePathForGlyph(font, glyph, nil) {
                path.addPath(shape, transform: CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: pen, ty: 0))
            }
            pen += advances[i].width
        }
        return path
    }

    /// The width of `word`'s ink as Skia measures it: each glyph's outline rounded out to whole
    /// points plus one on every side, joined across the word.
    static func measure(_ word: String, _ font: CTFont) -> Double {
        let (glyphs, advances) = glyphs(word, font)
        var pen = 0.0
        var left = Double.infinity
        var right = -Double.infinity
        for (i, glyph) in glyphs.enumerated() {
            if let shape = CTFontCreatePathForGlyph(font, glyph, nil) {
                let ink = shape.boundingBoxOfPath
                if !ink.isNull && !ink.isEmpty {
                    left = min(left, pen + ink.minX.rounded(.down) - 1)
                    right = max(right, pen + ink.maxX.rounded(.up) + 1)
                }
            }
            pen += advances[i].width
        }
        return right > left ? right - left : 0
    }

    /// Points filling `word`'s glyphs, `count` of them, centred at (cx, cy). They are sorted left to
    /// right, so particle i lands at about the same place in every word's reading order and the words
    /// flow sideways into each other rather than crossing chaotically.
    static func sample(_ word: String, size: Double, cx: Double, cy: Double, seed: UInt32) -> (xs: [Double], ys: [Double]) {
        // No word at all: every particle waits at the middle.
        guard !word.isEmpty, size.isFinite, size > 0, cx.isFinite, cy.isFinite else {
            return ([Double](repeating: cx.isFinite ? cx : 0, count: M.count), [Double](repeating: cy.isFinite ? cy : 0, count: M.count))
        }
        let path = outline(word, font(size))
        let ink = path.boundingBoxOfPath
        // A word without ink (spaces only) has an empty box at the origin, like Skia's.
        let box = ink.isNull || !ink.width.isFinite || !ink.height.isFinite ? CGRect.zero : ink
        let dx = cx - (box.minX + box.width / 2)
        let dy = cy - (box.minY + box.height / 2)
        var rand = ParticleWordMorphRandom(seed)
        var inside: [(Double, Double)] = []
        var y = Double(box.minY)
        while y <= box.minY + box.height {
            var x = Double(box.minX)
            while x <= box.minX + box.width {
                let jx = x + (rand.next() - 0.5) * M.sampleStep * 0.8
                let jy = y + (rand.next() - 0.5) * M.sampleStep * 0.8
                if path.contains(CGPoint(x: jx, y: jy), using: .winding) { inside.append((jx + dx, jy + dy)) }
                x += M.sampleStep
            }
            y += M.sampleStep
        }
        // Shuffle, then take `count` (repeating with a nudge if a word is small).
        var i = inside.count - 1
        while i > 0 {
            let j = Int((rand.next() * Double(i + 1)).rounded(.down))
            inside.swapAt(i, j)
            i -= 1
        }
        // A word with nothing to sample (a missing glyph) collapses to its middle.
        if inside.isEmpty { inside.append((cx, cy)) }
        var picked: [(Double, Double)] = []
        picked.reserveCapacity(M.count)
        for k in 0..<M.count {
            let (x, y) = inside[k % inside.count]
            if k >= inside.count {
                let nx = (rand.next() - 0.5) * 1.6
                let ny = (rand.next() - 0.5) * 1.6
                picked.append((x + nx, y + ny))
            } else {
                picked.append((x, y))
            }
        }
        // A stable sort, like the engine the React Native version runs on.
        let order = picked.indices.sorted { a, b in
            let ka = picked[a].0 + picked[a].1 * 0.18
            let kb = picked[b].0 + picked[b].1 * 0.18
            return ka != kb ? ka < kb : a < b
        }
        return (order.map { picked[$0].0 }, order.map { picked[$0].1 })
    }
}

// MARK: - The engine

/// Everything one frame draws.
struct ParticleWordMorphFrame {
    var xs: [Double]
    var ys: [Double]
    var colors: (Color, Color)
    var glow: Color
    // The indicator: the words, each label's left edge and width, the labels' middle on screen, and
    // the mark (in words, 0 … count − 1).
    var words: [String]
    var labelX: [Double]
    var labelW: [Double]
    var rowY: Double
    var mark: Double
}

/// The particles, stepped once a frame like the RN frame callback, and the finger that pushes them.
@MainActor
final class ParticleWordMorphEngine {
    private typealias M = ParticleWordMorphMetrics

    // Per-particle constants: its start delay, size, shimmer phase and swirl direction.
    let delay: [Double]
    let scale: [Double]
    let phase: [Double]
    let turn: [Double]

    private var targets: [(xs: [Double], ys: [Double])] = []
    private var targetsKey: (Double, Double, [String])?
    // Where each particle travels from, and when its trip began; the finger's push; where it is drawn.
    private var fromX: [Double] = []
    private var fromY: [Double] = []
    private var word = 0
    private var start = -10.0
    private var ox = [Double](repeating: 0, count: M.count)
    private var oy = [Double](repeating: 0, count: M.count)
    private var vx = [Double](repeating: 0, count: M.count)
    private var vy = [Double](repeating: 0, count: M.count)
    private var px: [Double] = []
    private var py: [Double] = []

    private var elapsed = 0.0
    private var clock = 0.0
    private var looping = false
    private var hold = 0.0
    private var configuredAuto: Bool?
    private var lastDate: Date?
    // The finger (or the ghost): where, and how strongly it pushes (0…1, eased).
    private var fingerX = 0.0
    private var fingerY = 0.0
    private var fingerOn = 0.0
    private var fingerTarget = 0.0
    // Colours: the palette being left, the one arriving, and when the change began.
    private var colorFrom = 0
    private var colorTo = 0
    private var colorStart = -10.0
    private var shown = 0
    private var size = CGSize.zero
    // The indicator's mark, in words, and its speed; the labels' places on screen, for taps on them.
    private var mark = 0.0
    private var markSpeed = 0.0
    private var reducedMotion = false
    private var rowY = 0.0
    private var labelX: [Double] = []
    private var labelW: [Double] = []
    private var labelKey: (Double, [String])?

    // Touch tracking for the tap that forms the next word.
    private var touchStart: Date?
    private var touchOrigin = CGPoint.zero
    private var touchTravel = 0.0

    var onWord: ((Int) -> Void)?

    init() {
        var rand = ParticleWordMorphRandom(7)
        var delay: [Double] = []
        var scale: [Double] = []
        var phase: [Double] = []
        var turn: [Double] = []
        for i in 0..<M.count {
            delay.append(min(1, max(0, Double(i) / Double(M.count) + (rand.next() - 0.5) * 0.18)) * M.spread)
            scale.append(M.spriteScale * (0.55 + rand.next() * 0.75))
            phase.append(rand.next() * Double.pi * 2)
            turn.append(rand.next() < 0.5 ? -1 : 1)
        }
        self.delay = delay
        self.scale = scale
        self.phase = phase
        self.turn = turn
    }

    /// Every word's particle targets, sampled once per screen size; a new size puts every particle
    /// straight onto the first word.
    private func prepare(_ size: CGSize, words: [String]) {
        if let key = targetsKey, key.0 == size.width, key.1 == size.height, key.2 == words { return }
        targetsKey = (size.width, size.height, words)
        let probe = ParticleWordMorphSampler.font(100)
        var widest = 1.0
        for w in words { widest = max(widest, ParticleWordMorphSampler.measure(w, probe)) }
        let fontSize = max(12, min(M.wordMax, 100 * max(40, size.width - M.wordMargin * 2) / widest))
        targets = words.enumerated().map { k, w in
            ParticleWordMorphSampler.sample(w, size: fontSize, cx: size.width / 2, cy: size.height * M.wordAt, seed: UInt32(17 + k * 101))
        }
        word = 0
        fromX = targets.first?.xs ?? []
        fromY = targets.first?.ys ?? []
        px = fromX
        py = fromY
        start = -10
        ox = [Double](repeating: 0, count: M.count)
        oy = ox
        vx = ox
        vy = ox
        shown = 0
        colorFrom = 0
        colorTo = 0
        mark = 0
        markSpeed = 0
    }

    /// Each label's width (with its tracking, which iOS sets after the last letter too) and left edge,
    /// the row centred on the screen.
    private func layoutLabels(_ width: Double, words: [String]) {
        if let key = labelKey, key.0 == width, key.1 == words { return }
        labelKey = (width, words)
        let font = UIFont(name: "AvenirNext-DemiBold", size: M.labelSize) ?? .systemFont(ofSize: M.labelSize, weight: .semibold)
        labelW = words.map { NSAttributedString(string: $0, attributes: [.font: font, .kern: M.tracking]).size().width }
        let total = labelW.reduce(0, +) + M.labelGap * Double(max(0, words.count - 1))
        var x = (width - total) / 2
        labelX = labelW.map { w in
            defer { x += w + M.labelGap }
            return x
        }
    }

    /// True once every word has a full set of targets and the particles have a place.
    private var ready: Bool {
        !targets.isEmpty && targets.allSatisfy { $0.xs.count == M.count && $0.ys.count == M.count }
            && px.count == M.count && py.count == M.count && fromX.count == M.count && fromY.count == M.count
    }

    /// Starts every particle towards word `next` from wherever it is now.
    private func morph(to next: Int) {
        guard ready, next >= 0, next < targets.count else { return }
        for i in 0..<M.count {
            // The trip starts from the particle's place on its path, without the finger's push.
            fromX[i] = px[i] - ox[i]
            fromY[i] = py[i] - oy[i]
        }
        word = next
        start = elapsed
        colorFrom = colorTo
        colorTo = next
        colorStart = elapsed
        shown = next
    }

    private static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }

    /// One step of a unit-mass spring towards `to`: returns the new position and speed.
    private static func springStep(_ x: Double, _ v: Double, to: Double, _ spring: (stiffness: Double, damping: Double), _ dt: Double) -> (Double, Double) {
        let speed = v + (spring.stiffness * (to - x) - spring.damping * v) * dt
        return (x + speed * dt, speed)
    }

    private static func passed(_ before: Double, _ now: Double, _ at: Double) -> Bool {
        (before < at && now >= at) || (now < before && (at > before || at <= now))
    }

    func step(to date: Date, size: CGSize, bottomInset: Double, words wordsIn: [String], palettes palettesIn: [(UInt32, UInt32)], autoPlay: Bool, reducedMotion: Bool) -> ParticleWordMorphFrame? {
        guard size.width > 0, size.height > 0, size.width.isFinite, size.height.isFinite else { return nil }
        let words = wordsIn.isEmpty ? M.words : wordsIn
        let palettes = palettesIn.isEmpty ? M.palettes : palettesIn
        self.size = size
        self.reducedMotion = reducedMotion
        prepare(size, words: words)
        rowY = size.height - (bottomInset.isFinite ? bottomInset : 0) - M.rowAbove
        layoutLabels(size.width, words: words)
        let auto = autoPlay && !reducedMotion
        if configuredAuto != auto {
            configuredAuto = auto
            looping = auto
            clock = 0
            hold = 0
        }
        let dt = lastDate.map { min(max(date.timeIntervalSince($0), 0), 0.048) } ?? 0.016
        lastDate = date
        if dt > 0, ready { advance(dt, reducedMotion: reducedMotion) }

        let shift = Self.clamp01((elapsed - colorStart) / M.colorTime)
        let a = palettes[colorFrom % palettes.count]
        let b = palettes[colorTo % palettes.count]
        return ParticleWordMorphFrame(
            xs: px, ys: py,
            colors: (Self.mixColor(a.0, b.0, shift), Self.mixColor(a.1, b.1, shift)),
            glow: Self.mixColor(a.1, b.1, shift),
            words: words, labelX: labelX, labelW: labelW, rowY: rowY, mark: mark
        )
    }

    /// Reanimated's interpolateColor: channels mixed in a 2.2 gamma's linear space, then rounded.
    static func mixColor(_ a: UInt32, _ b: UInt32, _ t: Double) -> Color {
        func channel(_ shift: UInt32) -> Double {
            let ca = pow(Double((a >> shift) & 0xff) / 255, 2.2)
            let cb = pow(Double((b >> shift) & 0xff) / 255, 2.2)
            return (pow(ca + (cb - ca) * t, 1 / 2.2) * 255).rounded() / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0))
    }

    private func advance(_ dt: Double, reducedMotion: Bool) {
        let now = elapsed + dt
        elapsed = now
        let count = targets.count

        var ghost = false
        if hold > 0 {
            hold -= dt
            // The loop picks up from the word on screen, so it never jumps.
            if hold <= 0 { clock = Double(shown) * M.wordEvery }
        } else if looping {
            let before = clock
            let t = (before + dt).truncatingRemainder(dividingBy: M.loop)
            clock = t
            for k in 0..<count where Self.passed(before, t, M.firstMorph + Double(k) * M.wordEvery) {
                morph(to: (k + 1) % count)
            }
            if t >= M.ghostFrom && t <= M.ghostFrom + M.ghostTime {
                // The ghost finger's path across the third word: left to right, a little uphill, eased.
                let p = M.easeInOut.value(at: Self.clamp01((t - M.ghostFrom) / M.ghostTime))
                fingerX = size.width * (0.1 + 0.8 * p)
                fingerY = size.height * (M.wordAt + 0.05 - 0.08 * p + 0.03 * sin(p * Double.pi * 2))
                ghost = true
            }
        }
        // The indicator's mark follows the word on screen.
        let goal = Double(shown)
        if reducedMotion {
            mark = goal
            markSpeed = 0
        } else {
            let (m, v) = Self.springStep(mark, markSpeed, to: goal, M.markSpring, dt)
            mark = m.isFinite ? m : goal
            markSpeed = v.isFinite ? v : 0
        }

        let want = ghost ? 1 : fingerTarget
        let on = fingerOn + (want - fingerOn) * Self.clamp01(dt / M.fingerEase)
        fingerOn = reducedMotion ? 0 : on

        let fx = fingerX
        let fy = fingerY
        let push = fingerOn
        let to = targets.indices.contains(word) ? targets[word] : targets[0]
        let since = now - start
        for i in 0..<M.count {
            let tx = to.xs[i]
            let ty = to.ys[i]
            var bx = tx
            var by = ty
            let p = Self.clamp01((since - delay[i]) / M.travel)
            if p < 1 {
                let sx = fromX[i]
                let sy = fromY[i]
                if reducedMotion {
                    bx = p < 0.5 ? sx : tx
                    by = p < 0.5 ? sy : ty
                } else {
                    let e = M.easeInOut.value(at: p)
                    bx = sx + (tx - sx) * e
                    by = sy + (ty - sy) * e
                    // Bow off the straight line along a direction from a slowly turning field.
                    let angle = sin(bx * 0.009 + now * 0.5) * 2.2 + cos(by * 0.011 - now * 0.4) * 2.2 + phase[i] * 0.35
                    let bow = sin(Double.pi * e) * M.swirl * turn[i] * (0.6 + 0.4 * sin(phase[i] * 3))
                    bx += cos(angle) * bow
                    by += sin(angle) * bow
                }
            }
            if !reducedMotion {
                bx += sin(now * 1.7 + phase[i]) * M.shimmer
                by += cos(now * 1.3 + phase[i] * 1.3) * M.shimmer
            }

            // The finger's push, and the spring that returns each particle.
            var ax = -M.stiffness * ox[i] - M.damping * vx[i]
            var ay = -M.stiffness * oy[i] - M.damping * vy[i]
            if push > 0.01 {
                let ddx = bx + ox[i] - fx
                let ddy = by + oy[i] - fy
                let d = (ddx * ddx + ddy * ddy).squareRoot() + 0.001
                if d < M.reach {
                    let k = (1 - d / M.reach) * (1 - d / M.reach) * M.push * push
                    ax += ddx / d * k
                    ay += ddy / d * k
                }
            }
            vx[i] += ax * dt
            vy[i] += ay * dt
            ox[i] += vx[i] * dt
            oy[i] += vy[i] * dt
            px[i] = bx + ox[i]
            py[i] = by + oy[i]
        }
    }

    // MARK: Touch

    func touchChanged(_ value: DragGesture.Value) {
        // A new touch (also after one the system cancelled, which never ended).
        if touchStart == nil || value.startLocation != touchOrigin {
            touchStart = value.time
            touchOrigin = value.startLocation
            touchTravel = 0
        }
        touchTravel = max(touchTravel, (value.translation.width * value.translation.width + value.translation.height * value.translation.height).squareRoot())
        hold = M.userHold
        fingerX = value.location.x
        fingerY = value.location.y
        fingerTarget = 1
    }

    /// The touch is over, ended or cancelled: the finger stops pushing.
    func touchCancelled() {
        fingerTarget = 0
    }

    func touchEnded(_ value: DragGesture.Value) {
        fingerTarget = 0
        let began = touchStart ?? value.time
        touchStart = nil
        // A short touch that stayed put is a tap: the next word starts to form.
        guard value.time.timeIntervalSince(began) <= 0.3, touchTravel <= 12, ready else { return }
        hold = M.userHold
        var next = (shown + 1) % targets.count
        // A tap on the indicator picks that word.
        let x = value.location.x
        let y = value.location.y
        if x.isFinite, y.isFinite, abs(y - rowY) < 22 {
            for k in 0..<min(targets.count, labelX.count, labelW.count) where labelW[k] > 0
                && x > labelX[k] - M.labelGap / 2 && x < labelX[k] + labelW[k] + M.labelGap / 2 {
                if k == shown { return }
                next = k
                break
            }
        }
        morph(to: next)
        onWord?(next)
    }
}
