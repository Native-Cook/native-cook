import CoreText
import SwiftUI
import UIKit

/// Floating Letters Stir — the opening screen of a meditation app. "let it settle" floats on a pale pool as
/// separate letters. A finger stirs the water: letters near its path are carried along, turn and drift, rings run
/// out from the stroke and lift each letter as they pass, and then every letter eases home on its own spring
/// until the phrase reads again.
///
/// The water is one Metal pass (FloatingLettersStir.metal: depth gradient, caustics, rings, grain). Over it one
/// `Canvas` draws the letters from glyph outlines measured at runtime (SF Rounded bold): a soft shadow on the pool
/// floor, a contact shadow, the bright rim where water clings to a floating thing, and the letter. The script and
/// the live simulation are in FloatingLettersStirMotion.swift.
///
/// The self-playing loop is 10 s and every frame of it is a function of the loop time. A drag (or a tap) anywhere
/// takes over; 2 s after the finger lifts the letters spring home and the loop starts again at t = 0. Under Reduce
/// Motion the phrase rests, the water is still and touch does nothing.
struct FloatingLettersStir: View {
    var autoPlay: Bool = true
    /// The phrase, one string per line.
    var lines: [String] = ["let it", "settle"]

    static let loop: Double = FloatingLettersStirMotion.loop

    private typealias M = FloatingLettersStirMotion

    private static let caption = "Stir the water, then let it settle."
    private static let ink = Color(red: 0x17 / 255, green: 0x3F / 255, blue: 0x47 / 255)

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = FloatingLettersStirModel()

    private var film: Bool { autoPlay && !reduceMotion }

    var body: some View {
        GeometryReader { outer in
            let ins = outer.safeAreaInsets
            GeometryReader { full in
                TimelineView(.animation(minimumInterval: nil, paused: reduceMotion)) { tl in
                    screen(tl.date, size: full.size, ins: ins)
                }
            }
            .ignoresSafeArea()
        }
        .background(Color(red: 0xC5 / 255, green: 0xE3 / 255, blue: 0xE1 / 255).ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear { model.restart(Date()) }
    }

    // MARK: - Screen

    private func screen(_ date: Date, size: CGSize, ins: EdgeInsets) -> some View {
        let W = size.width, H = size.height
        let L = model.layout(lines: lines, caption: Self.caption, W: W, H: H, insetTop: ins.top)
        let f = model.frame(date.timeIntervalSinceReferenceDate, L, film: film, reduced: reduceMotion)
        let u: [Float] = ([W, H, L.geo.cx, L.geo.cy, 2 * .pi * f.amb / M.loop, f.touch.x, f.touch.y, f.touch.z,
                           Double(UIScreen.main.scale)] + f.rips).map { Float($0) }

        return ZStack(alignment: .topLeading) {
            Rectangle()
                .fill(ShaderLibrary.floatingLettersStirWater(.floatArray(u)))
                .frame(width: W, height: H)
                .accessibilityHidden(true)

            Canvas { ctx, _ in draw(&ctx, L, f, W: W) }
                .frame(width: W, height: H)
                .accessibilityElement()
                .accessibilityAddTraits(.isHeader)
                .accessibilityLabel("\(lines.joined(separator: " ")). Stir the water with a finger and the letters drift, then settle.")

            chrome(ins)
                .frame(width: W, height: H)
                .allowsHitTesting(false)
        }
        .frame(width: W, height: H, alignment: .topLeading)
        .contentShape(Rectangle())
        .gesture(stir(L), including: reduceMotion ? .none : .all)
    }

    private func stir(_ L: FloatingLettersStirLayout) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .onChanged { v in
                let fast = model.touch(x: v.location.x, y: v.location.y, vx: v.velocity.width, vy: v.velocity.height,
                                       now: Date().timeIntervalSinceReferenceDate, L, film: film)
                if fast { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
            }
            .onEnded { _ in model.lift() }
    }

    // MARK: - Letters

    private func draw(_ ctx: inout GraphicsContext, _ L: FloatingLettersStirLayout, _ f: FloatingLettersStirModel.Frame, W: Double) {
        // The line under the phrase is drawn on the water, so drifting letters pass over it, not under.
        var line = ctx
        line.translateBy(x: (W - L.captionWidth) / 2, y: L.bottom + L.size * 0.34 + 17)
        line.fill(L.caption, with: .color(Self.ink.opacity(0.62)))

        var letters = Path()
        var floor = Path()
        for i in L.glyphs.indices {
            let p = f.poses[i]
            let X = L.homes[i].x + p.dx, Y = L.homes[i].y + p.dy
            letters.addPath(Path(L.glyphs[i]), transform: Self.xform(X, Y, p, L.px[i], L.py[i]))
            floor.addPath(Path(L.glyphs[i]), transform: Self.xform(X + 4 + p.sx, Y + 11 + p.sy, p, L.px[i], L.py[i]))
        }

        // Shadow on the pool floor, shifted by the slope of a passing ring (refraction).
        var a = ctx
        a.addFilter(.blur(radius: 7))
        a.fill(floor, with: .color(Color(red: 20 / 255, green: 74 / 255, blue: 86 / 255).opacity(0.2)))
        // Contact shadow.
        var b = ctx
        b.addFilter(.blur(radius: 2))
        b.translateBy(x: 0, y: 2.5)
        b.fill(letters, with: .color(Color(red: 12 / 255, green: 52 / 255, blue: 62 / 255).opacity(0.3)))
        // The rim where water clings.
        var c = ctx
        c.addFilter(.blur(radius: 0.9))
        c.stroke(letters, with: .color(.white.opacity(0.6)), style: StrokeStyle(lineWidth: 3.2, lineJoin: .round))
        ctx.fill(letters, with: .color(Self.ink))

        // The ghost finger of the script.
        if f.ghost.z > 0.001 {
            let r = 17 * (1.25 - 0.25 * f.ghost.z)
            let disc = Path(ellipseIn: CGRect(x: f.ghost.x - r, y: f.ghost.y - r, width: r * 2, height: r * 2))
            var g = ctx
            g.opacity = f.ghost.z
            var soft = g
            soft.addFilter(.blur(radius: 1.5))
            soft.fill(disc, with: .color(.white.opacity(0.42)))
            g.stroke(disc, with: .color(.white.opacity(0.85)), lineWidth: 1.2)
        }
    }

    /// Glyph outline (y up, pen at the origin) → screen: the pivot (px, py) lands on (X, Y), turned and scaled.
    private static func xform(_ X: Double, _ Y: Double, _ p: FloatingLettersStirModel.GlyphPose, _ px: Double, _ py: Double) -> CGAffineTransform {
        CGAffineTransform(translationX: X, y: Y)
            .rotated(by: p.rot)
            .scaledBy(x: p.scale, y: p.scale)
            .translatedBy(x: -px, y: -py)
            .scaledBy(x: 1, y: -1)
    }

    // MARK: - Wordmark, session, button

    private func chrome(_ ins: EdgeInsets) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Circle()
                    .strokeBorder(Self.ink.opacity(0.78), lineWidth: 1.5)
                    .frame(width: 18, height: 18)
                    .overlay(Circle().fill(Self.ink.opacity(0.78)).frame(width: 6, height: 6))
                Text("Stillwater")
                    .font(.system(size: 16, weight: .semibold))
                    .kerning(0.1)
                    .foregroundStyle(Self.ink.opacity(0.82))
            }
            .padding(.top, ins.top + 18)

            Spacer(minLength: 0)

            VStack(spacing: 14) {
                Text("Evening unwind · 6 min")
                    .font(.system(size: 14, weight: .medium).monospacedDigit())
                    .foregroundStyle(Self.ink.opacity(0.6))
                Text("Begin")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Color(red: 0xF2 / 255, green: 0xF8 / 255, blue: 0xF6 / 255))
                    .frame(minWidth: 0, maxWidth: 420)
                    .frame(height: 54)
                    .background(
                        Capsule().fill(LinearGradient(stops: [
                            .init(color: Color(red: 0x2C / 255, green: 0x6C / 255, blue: 0x77 / 255), location: 0),
                            .init(color: Color(red: 0x1F / 255, green: 0x53 / 255, blue: 0x5D / 255), location: 0.55),
                            .init(color: Self.ink, location: 1),
                        ], startPoint: .top, endPoint: .bottom))
                        .shadow(color: Color(red: 13 / 255, green: 50 / 255, blue: 58 / 255).opacity(0.32), radius: 1, y: 1)
                        .shadow(color: Color(red: 13 / 255, green: 50 / 255, blue: 58 / 255).opacity(0.22), radius: 15, y: 14)
                    )
                    .overlay(
                        // Inset highlight along the top edge.
                        Capsule().fill(.white.opacity(0.22))
                            .mask(
                                Capsule().fill(.black)
                                    .overlay(Capsule().offset(y: 1).blendMode(.destinationOut))
                                    .compositingGroup()
                            )
                    )
            }
            .padding(.horizontal, 24)
            .padding(.bottom, ins.bottom + 22)
        }
    }
}

// MARK: - Layout

/// The phrase measured once per screen size: glyph outlines, where each rests, and what the scripted stir does to it.
struct FloatingLettersStirLayout {
    var key = ""
    /// Outline of each glyph, y up, pen position at the origin.
    var glyphs: [CGPath] = []
    /// Pivot of each glyph relative to its pen position (y down).
    var px: [Double] = []
    var py: [Double] = []
    var homes: [FloatingLettersStirMotion.Home] = []
    var pushes: [FloatingLettersStirMotion.Push] = []
    var geo = FloatingLettersStirMotion.Geo(cx: 0, cy: 0, hw: 0, hh: 0, W: 0, H: 0)
    var size = 0.0
    var bottom = 0.0
    var caption = Path()
    var captionWidth = 0.0

    private static let maxSize = 96.0
    private static let side = 34.0
    private static let lineGap = 1.04
    private static let tracking = 0.02
    /// A glyph turns about the middle of its lowercase body: this far above the baseline, in em.
    private static let pivotUp = 0.3

    /// SF Rounded, bold, at its natural optical size (measured against the React Native build, whose Skia face
    /// for the rounded family has these advances). `as CTFont` keeps the system face; a lookup by name answers
    /// Times New Roman.
    private static func rounded(_ size: Double) -> CTFont {
        let base = UIFont.systemFont(ofSize: size, weight: .bold)
        let font = base.fontDescriptor.withDesign(.rounded).map { UIFont(descriptor: $0, size: size) } ?? base
        return font as CTFont
    }

    /// The system font as the React Native build draws its caption (Skia): the 12 pt face scaled to `size` with
    /// its optical size held at 12, so the tracking UIKit bakes into each point size is left out.
    private static func plain(_ size: Double) -> CTFont {
        let base = UIFont.systemFont(ofSize: 12, weight: .regular) as CTFont
        let optical = CTFontDescriptorCreateWithAttributes([kCTFontOpticalSizeAttribute: 12] as CFDictionary)
        return CTFontCreateCopyWithAttributes(base, size, nil, optical)
    }

    /// The line under the phrase, as one outline (y down) whose pen starts at the origin, and its advance.
    static func captionPath(_ text: String) -> (path: Path, width: Double) {
        let font = plain(17)
        let m = measure(font, text)
        let cg = CGMutablePath()
        var pen = 0.0
        for (g, adv) in zip(m.glyphs, m.widths) {
            var t = CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: pen, ty: 0)
            if let glyph = CTFontCreatePathForGlyph(font, g, &t) { cg.addPath(glyph) }
            pen += adv
        }
        return (Path(cg), pen)
    }

    private static func measure(_ font: CTFont, _ text: String) -> (glyphs: [CGGlyph], widths: [Double]) {
        let chars = Array(text.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count)
        var adv = [CGSize](repeating: .zero, count: chars.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &adv, chars.count)
        return (glyphs, adv.map { Double($0.width) })
    }

    static func make(lines: [String], caption: String, W: Double, H: Double, insetTop: Double, key: String) -> FloatingLettersStirLayout {
        typealias M = FloatingLettersStirMotion
        var size = min(maxSize, W * 0.245)
        var font = rounded(size)
        func widestLine() -> Double {
            lines.map { measure(font, $0).widths.reduce(0, +) + size * tracking * Double($0.utf16.count - 1) }.max() ?? 0
        }
        var widest = widestLine()
        if widest > W - side * 2 {
            size = floor(size * (W - side * 2) / widest)
            font = rounded(size)
            widest = widestLine()
        }
        let gap = size * lineGap
        let block = gap * Double(lines.count - 1)
        let cy = max(insetTop + 96 + block / 2 + size * 0.5, H * 0.4)
        let first = cy - block / 2 + size * pivotUp

        var out = FloatingLettersStirLayout()
        out.key = key
        for (l, line) in lines.enumerated() {
            let m = measure(font, line)
            let chars = Array(line.utf16)
            var x = (W - (m.widths.reduce(0, +) + size * tracking * Double(chars.count - 1))) / 2
            let baseline = first + gap * Double(l)
            for k in chars.indices {
                if chars[k] != 32, let path = CTFontCreatePathForGlyph(font, m.glyphs[k], nil) {
                    out.glyphs.append(path)
                    out.px.append(m.widths[k] / 2)
                    out.py.append(-size * pivotUp)
                    out.homes.append(M.Home(x: x + m.widths[k] / 2, y: baseline - size * pivotUp))
                }
                x += m.widths[k] + size * tracking
            }
        }
        out.geo = M.Geo(cx: W / 2, cy: cy, hw: min(W / 2 - 30, widest / 2 + 10), hh: block / 2 + size * 0.46, W: W, H: H)
        out.pushes = M.makePushes(out.homes, out.geo)
        out.size = size
        out.bottom = first + block + size * 0.3
        (out.caption, out.captionWidth) = captionPath(caption)
        return out
    }
}

// MARK: - Clocks and the live water

/// Holds the clocks and, under a finger, the live simulation. A plain reference type on purpose: the screen is
/// drawn from the timeline's date, and nothing here invalidates the view.
final class FloatingLettersStirModel {
    private typealias M = FloatingLettersStirMotion

    struct GlyphPose { var dx = 0.0, dy = 0.0, rot = 0.0, scale = 1.0, sx = 0.0, sy = 0.0 }
    struct Frame {
        var amb = 0.0
        var poses: [GlyphPose] = []
        /// `ripples` × [x, y, age, strength].
        var rips: [Double] = []
        /// x, y, presence.
        var ghost = SIMD3<Double>(0, 0, 0)
        var touch = SIMD3<Double>(0, 0, 0)
    }

    private static let idle = 2.0
    private static let spawnEvery = 56.0
    private static let tickSpeed = 900.0
    private static let shadowRefract = 5.0

    private var cached = FloatingLettersStirLayout()
    private var origin = Date().timeIntervalSinceReferenceDate
    private var loopStart = Date().timeIntervalSinceReferenceDate
    private var last = 0.0
    private var touching = false
    private var sim = M.Sim(0)
    private var finger = M.Finger()
    private var fingerAt = 0.0
    private var down = false
    private var press = 0.0
    private var idleFor = 0.0
    /// Live rings: `ripples` × [x, y, born (s), strength].
    private var liveRips = [Double](repeating: 0, count: M.ripples * 4)
    private var ripIndex = 0
    private var spawnedAt = 0.0
    private var spawnX = 0.0, spawnY = 0.0
    private var ticked = false

    func restart(_ date: Date) {
        origin = date.timeIntervalSinceReferenceDate
        loopStart = origin
        touching = false
    }

    func layout(lines: [String], caption: String, W: Double, H: Double, insetTop: Double) -> FloatingLettersStirLayout {
        let key = "\(lines.joined(separator: "\n"))|\(caption)|\(W)|\(H)|\(insetTop)"
        if cached.key != key { cached = .make(lines: lines, caption: caption, W: W, H: H, insetTop: insetTop, key: key) }
        return cached
    }

    private func clock(_ now: Double) -> Double {
        max(0, now - loopStart).truncatingRemainder(dividingBy: M.loop)
    }

    /// Everything the screen draws at `now`.
    func frame(_ now: Double, _ L: FloatingLettersStirLayout, film: Bool, reduced: Bool) -> Frame {
        let dt = min(max(0, now - last), 0.05)
        last = now
        // A finger that rests sends no updates: its last velocity must not keep pushing.
        if finger.on && now - fingerAt > 0.08 { finger.vx = 0; finger.vy = 0 }
        press += ((finger.on ? 1 : 0) - press) * min(1, dt * 14)

        if touching && sim.x.count == L.homes.count {
            if !finger.on { idleFor += dt }
            let settle = idleFor > Self.idle
            let steps = dt > 1.0 / 90 ? 2 : 1
            for _ in 0..<steps { M.stepSim(&sim, L.homes, finger, L.geo, dt / Double(steps), settle: settle) }
            // Back to the script once the letters are home and the last ring has died: t = 0 is this rest.
            if settle && M.simEnergy(sim) < 0.3 && now - spawnedAt > M.rippleLife {
                touching = false
                loopStart = now
            }
        } else if touching {
            touching = false
            loopStart = now
        }

        var f = Frame()
        f.amb = reduced ? 0 : max(0, now - origin).truncatingRemainder(dividingBy: M.loop)
        let t = clock(now)
        if touching {
            f.rips = [Double](repeating: 0, count: M.ripples * 4)
            for k in 0..<M.ripples {
                let age = now - liveRips[k * 4 + 2]
                if liveRips[k * 4 + 3] > 0 && age < M.rippleLife {
                    f.rips[k * 4] = liveRips[k * 4]
                    f.rips[k * 4 + 1] = liveRips[k * 4 + 1]
                    f.rips[k * 4 + 2] = age
                    f.rips[k * 4 + 3] = liveRips[k * 4 + 3]
                }
            }
            f.touch = SIMD3(finger.x, finger.y, press)
        } else {
            f.rips = M.autoRipples(t, L.geo, on: film)
            if film {
                let p = M.stirPos(t, L.geo)
                f.ghost = SIMD3(p.x, p.y, M.ghostAt(t))
            }
            f.touch = f.ghost
        }

        f.poses.reserveCapacity(L.homes.count)
        for i in L.homes.indices {
            var o = M.Pose()
            if touching {
                o = M.Pose(dx: sim.x[i], dy: sim.y[i], rot: sim.r[i], scale: 1)
            } else if film {
                o = M.autoOffset(t, i, L.pushes[i])
            }
            let sw = M.swellAt(f.amb, i)
            let rp = M.ripplesAt(L.homes[i].x + o.dx, L.homes[i].y + o.dy, f.rips)
            f.poses.append(GlyphPose(dx: o.dx, dy: o.dy + sw.dy - M.bobLift * rp.h, rot: o.rot + sw.tilt + M.bobTilt * rp.gx,
                                     scale: o.scale, sx: Self.shadowRefract * rp.gx, sy: Self.shadowRefract * rp.gy))
        }
        return f
    }

    // MARK: Touch: the finger is the stir

    private func spawn(_ x: Double, _ y: Double, _ strength: Double, _ now: Double) {
        liveRips[ripIndex * 4] = x
        liveRips[ripIndex * 4 + 1] = y
        liveRips[ripIndex * 4 + 2] = now
        liveRips[ripIndex * 4 + 3] = strength
        ripIndex = (ripIndex + 1) % M.ripples
        spawnedAt = now
        spawnX = x
        spawnY = y
    }

    private func takeOver(_ now: Double, _ L: FloatingLettersStirLayout, film: Bool) {
        if !touching {
            var s = M.Sim(L.homes.count)
            let t = clock(now)
            if film {
                // Start from where the script has the letters, moving as they were.
                let back = 1.0 / 60
                for i in L.homes.indices {
                    let o = M.autoOffset(t, i, L.pushes[i])
                    let p = M.autoOffset(max(0, t - back), i, L.pushes[i])
                    s.x[i] = o.dx; s.y[i] = o.dy; s.r[i] = o.rot
                    s.vx[i] = (o.dx - p.dx) / back
                    s.vy[i] = (o.dy - p.dy) / back
                    s.vr[i] = (o.rot - p.rot) / back
                }
            }
            sim = s
            // The script's rings stay on the water: re-date them on the live clock.
            let scripted = M.autoRipples(t, L.geo, on: film)
            for k in 0..<M.ripples {
                liveRips[k * 4] = scripted[k * 4]
                liveRips[k * 4 + 1] = scripted[k * 4 + 1]
                liveRips[k * 4 + 2] = now - scripted[k * 4 + 2]
                liveRips[k * 4 + 3] = scripted[k * 4 + 3]
            }
            ripIndex = 0
            touching = true
        }
        idleFor = 0
    }

    /// A touch sample. Returns true once per stroke, when it first passes the haptic's speed.
    func touch(x: Double, y: Double, vx: Double, vy: Double, now: Double, _ L: FloatingLettersStirLayout, film: Bool) -> Bool {
        if !down {
            down = true
            takeOver(now, L, film: film)
            finger = M.Finger(x: x, y: y, vx: 0, vy: 0, on: true)
            fingerAt = now
            spawn(x, y, 0.7, now)
            ticked = false
            return false
        }
        finger = M.Finger(x: x, y: y, vx: vx, vy: vy, on: true)
        fingerAt = now
        idleFor = 0
        let speed = hypot(vx, vy)
        if hypot(x - spawnX, y - spawnY) >= Self.spawnEvery { spawn(x, y, 0.45 + 0.55 * M.seg(speed, 100, 1100), now) }
        if !ticked && speed > Self.tickSpeed {
            ticked = true
            return true
        }
        return false
    }

    func lift() {
        down = false
        finger.vx = 0
        finger.vy = 0
        finger.on = false
        idleFor = 0
    }
}
