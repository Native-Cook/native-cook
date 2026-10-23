import CoreText
import SwiftUI
import UIKit

/// A big bold word standing on a pastel floor, each letter a rigid body on springs: one pulls it
/// back over its slot, one stands it upright, and links to its neighbours chain the word together.
/// Drag a letter and it follows with a lag, swinging, the ones beside it tugged along; let go and
/// it flies, lands, bounces and springs home. Flick up from the floor and every letter jumps.
/// Double tap for the next word: the letters shrink and turn away, and new ones drop in.
struct SpringLetters: View {
    /// Drags and throws letters, flicks the floor and changes the word, on a loop. Off by default.
    var autoPlay = false
    var hint = "Drag a letter · flick the floor · double tap"

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = SpringLettersEngine()
    /// True while a drag is down; SwiftUI resets it when the drag ends or is cancelled.
    @GestureState private var touching = false

    private typealias S = SpringLettersStyle

    var body: some View {
        // The block draws under the system bars and keeps its parts clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Color(hex: S.wallTop).ignoresSafeArea())
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let letters = SpringLettersGlyphs.shared(width: size.width)
        // JavaScript's Math.round.
        let floorY = Foundation.floor(size.height * S.floorAt + 0.5)
        let setup = SpringLettersSetup(
            width: size.width,
            floor: floorY,
            ceiling: insets.top,
            looping: autoPlay && !reduceMotion,
            still: reduceMotion
        )
        return ZStack(alignment: .topLeading) {
            // The wall and the floor never move: drawn once.
            Canvas { ctx, canvasSize in
                SpringLettersPainter.drawStage(&ctx, size: canvasSize, floor: floorY, letterHeight: letters.size * 0.72)
            }

            TimelineView(.animation) { timeline in
                let sim = engine.step(to: timeline.date, letters: letters, setup: setup)
                // The sim is one object mutated in place: its clock is captured too, so SwiftUI sees
                // a new canvas every frame and redraws it.
                let time = sim.time
                Canvas { ctx, _ in
                    _ = time
                    SpringLettersPainter.drawLive(&ctx, sim: sim, letters: letters, still: reduceMotion)
                }
            }

            Text(hint)
                .font(.custom("AvenirNext-DemiBold", fixedSize: 13))
                .kerning(0.2)
                .foregroundStyle(Color(hex: S.hint))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
                .frame(width: max(0, size.width), height: max(0, size.height - insets.bottom - 28), alignment: .bottom)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            // One recogniser for RN's pan (from the first touch) running beside a double tap.
            DragGesture(minimumDistance: 0)
                .updating($touching) { _, state, _ in state = true }
                .onChanged { engine.touchMoved($0, letters: letters) }
                .onEnded { engine.touchEnded($0, letters: letters) }
        )
        .onChange(of: touching) { _, now in
            // A drag the system cancelled (a swipe from the edge, an alert) never reports its end;
            // after a normal end (onEnded has run) this does nothing.
            if !now { engine.touchCancelled() }
        }
        .accessibilityElement()
        .accessibilityLabel("A word of letters on springs. Drag a letter, flick the floor, or double tap for the next word.")
    }
}

/// What the screen gives the physics: its walls, floor and ceiling, and whether it plays itself.
struct SpringLettersSetup: Equatable {
    var width: Double
    var floor: Double
    var ceiling: Double
    var looping: Bool
    var still: Bool
}

/// Owns the simulation, steps it against the display clock, and turns touches into its drags,
/// flicks and double taps.
@MainActor
final class SpringLettersEngine {
    private let sim = SpringLettersSim(looping: false, still: false)
    private var setup: SpringLettersSetup?
    private var lastDate: Date?
    private var answeredGrabs = 0
    private var answeredFlicks = 0
    private var answeredSwaps = 0
    // The touch under way, and the last tap (for a double tap).
    private var touchStart: Date?
    private var touchAt: CGPoint?
    private var lastTapEnd: Date?
    private let selection = UISelectionFeedbackGenerator()
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let light = UIImpactFeedbackGenerator(style: .light)

    // A tap: lifted within this long, having moved less than this far (pt). Two make a double tap
    // when the second comes down within `doubleTapDelay` of the first lifting.
    private static let tapDuration = 0.5
    private static let tapSlop = 20.0
    private static let doubleTapDelay = 0.28

    func step(to date: Date, letters: SpringLettersGlyphs, setup next: SpringLettersSetup) -> SpringLettersSim {
        if setup != next {
            setup = next
            sim.looping = next.looping
            sim.still = next.still
            sim.clock = 0
            sim.floor = next.floor
            sim.left = 0
            sim.right = next.width
            sim.ceiling = next.ceiling
            sim.placeWord(0, letters.boxes, letters.gap, drop: false)
        }
        let dt = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        sim.step(dt, letters.boxes, letters.gap)
        if sim.userGrabs > answeredGrabs { selection.selectionChanged() }
        if sim.userFlicks > answeredFlicks { medium.impactOccurred() }
        if sim.userSwaps > answeredSwaps { light.impactOccurred() }
        answeredGrabs = sim.userGrabs
        answeredFlicks = sim.userFlicks
        answeredSwaps = sim.userSwaps
        return sim
    }

    func touchMoved(_ value: DragGesture.Value, letters: SpringLettersGlyphs) {
        // A new drag while the last one never reported its end: let go of that one first.
        if let at = touchAt, at != value.startLocation { cancelNow() }
        if touchStart == nil {
            touchStart = value.time
            touchAt = value.startLocation
            selection.prepare()
            sim.fingerDown(value.startLocation.x, value.startLocation.y)
            if value.location == value.startLocation { return }
        }
        sim.fingerMove(value.location.x, value.location.y, value.velocity.width, value.velocity.height)
    }

    /// The drag may have been cancelled without an end. Checked a moment later, once a normal
    /// `onEnded` has surely run: if the same drag is still open, the letter is let go.
    func touchCancelled() {
        guard let start = touchStart else { return }
        Task { @MainActor [weak self] in
            guard let self, self.touchStart == start else { return }
            self.cancelNow()
        }
    }

    private func cancelNow() {
        guard touchStart != nil else { return }
        sim.fingerUp(0, 0)
        touchStart = nil
        touchAt = nil
        lastTapEnd = nil
    }

    func touchEnded(_ value: DragGesture.Value, letters: SpringLettersGlyphs) {
        defer {
            touchStart = nil
            touchAt = nil
        }
        sim.fingerUp(value.velocity.width, value.velocity.height)
        guard let start = touchStart else { return }
        let travel = hypot(value.translation.width, value.translation.height)
        let tap = value.time.timeIntervalSince(start) <= Self.tapDuration && travel < Self.tapSlop
        guard tap else {
            lastTapEnd = nil
            return
        }
        if let previous = lastTapEnd, start.timeIntervalSince(previous) <= Self.doubleTapDelay {
            lastTapEnd = nil
            sim.doubleTap(value.location.x, value.location.y, letters.boxes, letters.gap)
        } else {
            lastTapEnd = value.time
        }
    }
}

/// A pastel stage: a lilac wall, a peach floor, candy letters with a chunky extruded side.
enum SpringLettersStyle {
    static let wallTop: UInt32 = 0xefe8fb
    static let wallLow: UInt32 = 0xe4daf6
    static let floorNear: UInt32 = 0xf9dccf
    static let floorFar: UInt32 = 0xfdeee6
    static let hint: UInt32 = 0x9a88ab
    static let faces: [UInt32] = [0xff6b5b, 0xffa13a, 0xffcf3d, 0x3fc98d, 0x4b8cff, 0xa26cff, 0xff7fb3, 0x2fc4d6]
    // The extruded side darkens towards this plum.
    static let sideShade: UInt32 = 0x3b1d48
    static let shadow: UInt32 = 0x5b3a6b

    // Type: a heavy condensed sans from the system, sized so the widest word fits.
    static let fontName = "AvenirNextCondensed-Heavy"
    static let maxSize = 150.0
    static let sideMargin = 22.0
    // Gap between letters at rest (share of the type size).
    static let gap = 0.05
    // The extruded side: layers, and each layer's step down and to the right (pt).
    static let depth = 8
    static let stepX = 0.75
    static let stepY = 1.05
    // The floor line, as a share of the screen's height, and the wall's foot just behind it.
    static let floorAt = 0.6
    static let horizonBehind = 0.36
}

/// Outlines of every letter of every word, from the font, and the boxes the physics needs.
final class SpringLettersGlyphs {
    let size: Double
    let gap: Double
    // Per word: each letter's outline, the middle of its outline, and its box.
    let paths: [[Path]]
    let centres: [[Double]]
    let boxes: [[Double]]

    @MainActor private static var cache: (width: Double, glyphs: SpringLettersGlyphs)?

    @MainActor
    static func shared(width: Double) -> SpringLettersGlyphs {
        if let cache, cache.width == width { return cache.glyphs }
        let glyphs = SpringLettersGlyphs(width: width)
        cache = (width, glyphs)
        return glyphs
    }

    /// The outline of one letter, its baseline on y = 0 and y pointing down, like Skia's `MakeFromText`.
    private static func outline(_ letter: Character, _ font: CTFont) -> CGPath {
        var chars = Array(String(letter).utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, &chars, &glyphs, chars.count)
        var flip = CGAffineTransform(scaleX: 1, y: -1)
        return CTFontCreatePathForGlyph(font, glyphs[0], &flip) ?? CGMutablePath()
    }

    private init(width: Double) {
        typealias S = SpringLettersStyle
        let words = SpringLettersPhysics.words
        let probe = CTFontCreateWithName(S.fontName as CFString, 100, nil)
        var widest = 0.0
        for word in words {
            var total = S.gap * 100 * Double(word.count - 1)
            for letter in word { total += Self.outline(letter, probe).boundingBoxOfPath.width }
            widest = max(widest, total)
        }
        // Never a zero, negative or infinite size, even before the first layout.
        let size = widest > 0 ? max(8, min(S.maxSize, (100 * (width - S.sideMargin * 2)) / widest)) : S.maxSize
        let font = CTFontCreateWithName(S.fontName as CFString, size, nil)
        var paths: [[Path]] = []
        var centres: [[Double]] = []
        var boxes: [[Double]] = []
        for word in words {
            var p: [Path] = []
            var c: [Double] = []
            var b: [Double] = []
            for letter in word {
                let path = Self.outline(letter, font)
                let r = path.boundingBoxOfPath
                p.append(Path(path))
                c.append(contentsOf: [r.midX, r.midY])
                b.append(contentsOf: [r.width, r.height])
            }
            paths.append(p)
            centres.append(c)
            boxes.append(b)
        }
        self.size = size
        gap = size * S.gap
        self.paths = paths
        self.centres = centres
        self.boxes = boxes
    }
}

enum SpringLettersPainter {
    private typealias S = SpringLettersStyle

    /// The stage: wall, the soft shadow where wall meets floor, and the floor. Painted once.
    static func drawStage(_ ctx: inout GraphicsContext, size: CGSize, floor: Double, letterHeight: Double) {
        let width = size.width
        let height = size.height
        let horizon = floor - letterHeight * S.horizonBehind
        func linear(_ colors: [Color], _ y0: Double, _ y1: Double) -> GraphicsContext.Shading {
            .linearGradient(Gradient(colors: colors), startPoint: CGPoint(x: 0, y: y0), endPoint: CGPoint(x: 0, y: y1))
        }
        ctx.fill(Path(CGRect(x: 0, y: 0, width: width, height: horizon)), with: linear([Color(hex: S.wallTop), Color(hex: S.wallLow)], 0, horizon))
        ctx.fill(Path(CGRect(x: 0, y: horizon, width: width, height: height - horizon)), with: linear([Color(hex: S.floorFar), Color(hex: S.floorNear)], horizon, height))
        // A soft fold of shadow where the wall meets the floor, and a line of light on the floor's edge.
        ctx.fill(
            Path(CGRect(x: 0, y: horizon - 26, width: width, height: 26)),
            with: linear([Color.rgba(91, 58, 107, 0), Color.rgba(91, 58, 107, 0.12)], horizon - 26, horizon)
        )
        ctx.fill(Path(CGRect(x: 0, y: horizon, width: width, height: 1)), with: .color(.white.opacity(0.7)))
        // A pool of light on the floor under the word, squashed into an ellipse.
        var pool = ctx
        pool.scaleBy(x: 1, y: 0.35)
        let centre = CGPoint(x: width / 2, y: (floor + 30) / 0.35)
        let r = width * 0.7
        pool.fill(
            Path(ellipseIn: CGRect(x: centre.x - r, y: centre.y - r, width: r * 2, height: r * 2)),
            with: .radialGradient(Gradient(colors: [.white.opacity(0.45), .white.opacity(0)]), center: centre, startRadius: 0, endRadius: r)
        )
    }

    /// Two colours mixed per channel and rounded, like the RN `mix`.
    static func mix(_ a: UInt32, _ b: UInt32, _ t: Double) -> Color {
        func channel(_ shift: UInt32) -> Double {
            let x = Double((a >> shift) & 0xff)
            let y = Double((b >> shift) & 0xff)
            return Foundation.floor(x + (y - x) * t + 0.5) / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: 1)
    }

    /// The soft shadow a letter casts on the floor: tighter and darker the closer it is.
    static func drawShadow(_ ctx: inout GraphicsContext, x: Double, halfW: Double, lift: Double, floor: Double, scale: Double, alpha: Double) {
        let far = min(1, max(0, lift) / 320)
        let w = halfW * scale * (1.05 - far * 0.35)
        ctx.drawLayer { layer in
            layer.addFilter(.blur(radius: 5 + far * 14))
            layer.fill(
                Path(ellipseIn: CGRect(x: x + 5 - w, y: floor - 4, width: w * 2, height: 12 + far * 6)),
                with: .color(Color(hex: S.shadow, opacity: 0.26 * (1 - far * 0.8) * alpha))
            )
        }
        if lift < 14 {
            // Contact: a tight dark line where the letter meets the floor.
            ctx.drawLayer { layer in
                layer.addFilter(.blur(radius: 2.5))
                layer.fill(
                    Path(ellipseIn: CGRect(x: x - halfW * scale * 0.9, y: floor - 2, width: halfW * scale * 1.8 + 6, height: 5)),
                    with: .color(Color(hex: S.shadow, opacity: 0.22 * (1 - lift / 14) * alpha))
                )
            }
        }
    }

    /// One candy letter: an extruded side in deepening shades, then the face, lit from the top
    /// left with a gloss along its upper edges and a shade along its lower ones.
    static func drawLetter(_ ctx: inout GraphicsContext, path: Path, cx: Double, cy: Double, x: Double, y: Double, angle: Double, scale: Double, alpha: Double, face: UInt32, halfH: Double) {
        func placed(_ dx: Double, _ dy: Double) -> GraphicsContext {
            var c = ctx
            c.translateBy(x: x + dx, y: y + dy)
            c.rotate(by: .radians(angle))
            c.scaleBy(x: scale, y: scale)
            c.translateBy(x: -cx, y: -cy)
            return c
        }
        for k in stride(from: S.depth, through: 1, by: -1) {
            var side = placed(Double(k) * S.stepX, Double(k) * S.stepY)
            side.opacity = alpha
            side.fill(path, with: .color(mix(face, S.sideShade, 0.38 + (0.22 * Double(k)) / Double(S.depth))))
        }
        var c = placed(0, 0)
        let top = cy - halfH
        var faceCtx = c
        faceCtx.opacity = alpha
        faceCtx.fill(
            path,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: mix(face, 0xffffff, 0.28), location: 0),
                    .init(color: mix(face, 0xffffff, 0), location: 0.55),
                    .init(color: mix(face, S.sideShade, 0.08), location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: top),
                endPoint: CGPoint(x: 0, y: top + halfH * 2)
            )
        )
        c.clip(to: path)
        var shade = c
        shade.translateBy(x: 3, y: 3.5)
        shade.drawLayer { layer in
            layer.addFilter(.blur(radius: 3))
            layer.stroke(path, with: .color(mix(face, S.sideShade, 0.55).opacity(0.35 * alpha)), style: StrokeStyle(lineWidth: 7, lineJoin: .round))
        }
        var gloss = c
        gloss.translateBy(x: -2, y: -2.4)
        gloss.drawLayer { layer in
            layer.addFilter(.blur(radius: 1.6))
            layer.stroke(path, with: .color(.white.opacity(0.55 * alpha)), style: StrokeStyle(lineWidth: 3.5, lineJoin: .round))
        }
    }

    static func drawLive(_ ctx: inout GraphicsContext, sim s: SpringLettersSim, letters: SpringLettersGlyphs, still: Bool) {
        // Shadows first, so no letter is darkened by another's.
        for i in 0..<s.count where s.wait[i] <= 0 {
            drawShadow(&ctx, x: s.x[i], halfW: s.halfW[i], lift: s.floor - (s.y[i] + s.halfH[i]), floor: s.floor, scale: 1, alpha: s.still ? min(1, s.arrive / 0.3) : 1)
        }
        if s.leaveAge >= 0 {
            let boxes = letters.boxes[s.leftWord]
            for i in 0..<s.leftCount {
                let e = s.leaving(i)
                drawShadow(&ctx, x: s.lx[i], halfW: boxes[i * 2] / 2, lift: s.floor - s.ly[i] - boxes[i * 2 + 1] / 2, floor: s.floor, scale: 1 - 0.5 * e, alpha: 1 - e)
            }
        }

        // The word leaving: each letter shrinks, turns and fades, one after another.
        if s.leaveAge >= 0 {
            for i in 0..<s.leftCount {
                let e = s.leaving(i)
                if e >= 1 { continue }
                let ease = e * e
                let turn = still ? 0 : (i % 2 == 0 ? -1 : 1) * 0.9 * ease
                let shrink = still ? 1 : 1 - 0.55 * ease
                let rise = still ? 0 : 46 * ease
                drawLetter(
                    &ctx,
                    path: letters.paths[s.leftWord][i],
                    cx: letters.centres[s.leftWord][i * 2],
                    cy: letters.centres[s.leftWord][i * 2 + 1],
                    x: s.lx[i],
                    y: s.ly[i] - rise,
                    angle: s.la[i] + turn,
                    scale: shrink,
                    alpha: 1 - e,
                    face: S.faces[i % S.faces.count],
                    halfH: letters.boxes[s.leftWord][i * 2 + 1] / 2
                )
            }
        }

        // The word, the held letter drawn last so it is on top.
        let shown = s.still ? min(1, s.arrive / 0.3) : 1
        for pass in 0..<2 {
            for i in 0..<s.count {
                if (i == s.held) != (pass == 1) { continue }
                if s.wait[i] > 0 { continue }
                drawLetter(
                    &ctx,
                    path: letters.paths[s.word][i],
                    cx: letters.centres[s.word][i * 2],
                    cy: letters.centres[s.word][i * 2 + 1],
                    x: s.x[i],
                    y: s.y[i],
                    angle: s.a[i],
                    scale: 1,
                    alpha: shown,
                    face: S.faces[i % S.faces.count],
                    halfH: s.halfH[i]
                )
            }
        }

        // The fingertip, real or scripted, and the ring of a double tap.
        if s.down {
            let touch = Path(ellipseIn: CGRect(x: s.fx - 22, y: s.fy - 22, width: 44, height: 44))
            ctx.fill(touch, with: .color(.white.opacity(0.38)))
            ctx.stroke(touch, with: .color(.white.opacity(0.8)), lineWidth: 1.5)
        }
        if s.tapAge >= 0 {
            let t = s.tapAge / 0.6
            let r = 18 + 50 * (1 - (1 - t) * (1 - t))
            ctx.stroke(
                Path(ellipseIn: CGRect(x: s.tapX - r, y: s.tapY - r, width: r * 2, height: r * 2)),
                with: .color(.white.opacity(0.85 * (1 - t))),
                lineWidth: 2
            )
        }
    }
}
