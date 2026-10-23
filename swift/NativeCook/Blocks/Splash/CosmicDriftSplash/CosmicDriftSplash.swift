import SwiftUI
import UIKit

/*
 Cosmic Drift Splash: the launch of Orrery, an invented calm web browser.

 Timeline of one loop (Self.loop = 11 s), every frame derived from t:
   0.00–1.00  rest: the end card. Graphite backdrop, faint stars, the Orrery mark (a planet,
              two orbits and a moon), "orrery", the tagline, a teal "Get started" pill, a close ×.
   1.00–1.45  the copy, the pill and the × fade out (the wordmark blurs away).
   1.05–1.60  the mark slides down to the centre of the screen.
   1.40–2.00  the backdrop sinks from graphite to near black.
   1.60–2.10  the camera flies into the mark: it grows 4× and fades.
   1.90–4.10  fly-through: 56 glossy spheres (pre-rendered sprites, six palettes, six blur levels
              for depth of field) and fourteen sparkling dust streaks stream past a camera moving at
              4.2 units/s and rolling at 0.32 rad/s around a vanishing point up and right of centre.
   4.10–5.50  the field recedes: depth grows ×7 (+6), the roll speeds up into a spiral with faint arc
              trails, the vanishing point returns to the centre; everything is gone by 5.70.
   5.70–5.85  a white dot appears in the starfield; 5.85–6.35 it swells into a wobbling blob.
   6.35–6.95  the blob shrinks into the planet while the outer (6.38–6.90) and inner (6.50–7.00)
              orbits draw themselves from their middles; 6.85–7.15 the moon pops in; the mark
              turns from −20° to 0°.
   7.50–8.20  the backdrop lightens back to graphite; 7.90–8.60 the mark rises to its place.
   8.10–8.90  the wordmark blurs into focus; 8.40–9.10 tagline; 8.50–9.20 pill; 8.70–9.20 ×.
   9.20–11.0  rest (the same end card as at 0).

 Touch: a horizontal drag scrubs the film (a screen width is 5 s) and a vertical one steers the
 vanishing point; a tap skips to the end card. Two seconds after the finger lifts the film runs
 to the nearest rest (forward from 6 s on, backward before) over 0.8 s and the loop restarts.
 Reduced motion: the end card, still.

 First draw: the sprites are built once per app run (CosmicDriftSplashSprites), and the loop holds its resting
 frame until they exist and every sprite, plus the fly-through and the mark's morph at a few moments, has been
 drawn under that frame (warm), and two frames in a row have come quickly (CosmicDriftSplashWarmUp, 1.5 s at
 most), so the fly-through does not stall on its first frames while textures upload. Time 0 is the first frame
 after that.
*/
struct CosmicDriftSplash: View {
    static let loop = 11.0
    var autoPlay = false
    var previewTime: Double? = nil

    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    /// When the loop's time 0 is: set by a replay; until then the warm-up decides it.
    @State private var epoch: Date?
    @State private var warmUp = CosmicDriftSplashWarmUp()
    @State private var heldTime = 0.0
    @State private var scrubFrom = 0.0
    @State private var steer = CGSize.zero
    @State private var releasedAt: Date?
    @State private var touching = false

    private static let restTime = 9.6

    private func time(_ now: Date) -> Double {
        if let previewTime { return previewTime.truncatingRemainder(dividingBy: Self.loop) }
        if reducedMotion { return Self.restTime }
        if touching { return heldTime }
        if let releasedAt {
            let elapsed = now.timeIntervalSince(releasedAt)
            if elapsed < 2 { return heldTime }
            if elapsed < 2.8 {
                let target = heldTime >= 6 ? Self.loop : 0
                return heldTime + (target - heldTime) * Self.easeInOut((elapsed - 2) / 0.8)
            }
            return autoPlay ? (elapsed - 2.8).truncatingRemainder(dividingBy: Self.loop) : 0
        }
        guard autoPlay, let start = epoch ?? warmUp.start else { return 0 }
        return max(0, now.timeIntervalSince(start)).truncatingRemainder(dividingBy: Self.loop)
    }

    private func steering(_ now: Date) -> CGSize {
        if touching { return steer }
        guard let releasedAt else { return .zero }
        let elapsed = now.timeIntervalSince(releasedAt)
        let k = elapsed < 2 ? 1 : 1 - Self.easeInOut((elapsed - 2) / 0.8)
        return CGSize(width: steer.width * k, height: steer.height * k)
    }

    var body: some View {
        GeometryReader { geometry in
            let insets = geometry.safeAreaInsets
            TimelineView(.animation(paused: reducedMotion || previewTime != nil || scenePhase != .active || (!autoPlay && releasedAt == nil && !touching))) { clock in
                let now = clock.date
                Canvas { context, size in
                    if epoch == nil && previewTime == nil && !reducedMotion, let sprites = CosmicDriftSplashSprites.shared.images,
                       warmUp.warming(at: now) {
                        // Still on the resting frame: draw what the loop will draw; the frame's opaque backdrop covers it.
                        Self.warm(context, size: size, insets: insets, sprites: sprites)
                    }
                    Self.draw(context, size: size, insets: insets, t: time(now), steer: steering(now))
                }
            }
            .ignoresSafeArea()
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .onChanged { value in
                    guard !reducedMotion, previewTime == nil else { return }
                    if !touching {
                        let current = time(Date())
                        scrubFrom = current < 9.2 ? current : 0
                        heldTime = current
                        touching = true
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    }
                    let width = max(1, geometry.size.width)
                    heldTime = min(Self.loop - 0.01, max(0, scrubFrom + Double(value.translation.width / width) * 5))
                    steer = CGSize(width: max(-60, min(60, value.translation.width * 0.15)),
                                   height: max(-140, min(140, value.translation.height * 0.4)))
                }
                .onEnded { value in
                    guard !reducedMotion, previewTime == nil else { return }
                    if abs(value.translation.width) < 8 && abs(value.translation.height) < 8 {
                        heldTime = 9.3
                        steer = .zero
                        UIImpactFeedbackGenerator(style: .soft).impactOccurred()
                    }
                    touching = false
                    releasedAt = Date()
                })
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Orrery. A quieter way to explore the web. Get started. Drag to fly through the planets, tap to skip.")
            .accessibilityAction(named: "Replay splash") { epoch = Date(); releasedAt = nil }
        }
        .background(Color(red: 0.075, green: 0.094, blue: 0.102).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .task { _ = await CosmicDriftSplashSprites.shared.ready() }
    }

    // MARK: Curves

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(1, max(0, (t - a) / (b - a))) }
    static func easeInOut(_ v: Double) -> Double {
        let u = min(1, max(0, v)); return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }
    static func easeOut(_ v: Double) -> Double { let u = min(1, max(0, v)); return 1 - pow(1 - u, 3) }
    static func easeIn(_ v: Double) -> Double { let u = min(1, max(0, v)); return u * u * u }
    static func easeOutBack(_ v: Double, _ s: Double = 1.6) -> Double {
        let u = min(1, max(0, v)) - 1; return 1 + u * u * ((s + 1) * u + s)
    }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }

    /// 1 on the end card, 0 while it is away: out over [a, b], back in over [c, d].
    static func card(_ t: Double, _ a: Double, _ b: Double, _ c: Double, _ d: Double, out: (Double) -> Double = easeInOut, back: (Double) -> Double = easeInOut) -> Double {
        if t < c { return 1 - out(seg(t, a, b)) }
        return back(seg(t, c, d))
    }

    // MARK: Frame

    static let graphite = SIMD3<Double>(0.075, 0.094, 0.102)
    static let night = SIMD3<Double>(0.012, 0.016, 0.018)
    static let ink = Color(red: 0.945, green: 0.949, blue: 0.937)

    static func draw(_ context: GraphicsContext, size: CGSize, insets: EdgeInsets, t: Double, steer: CGSize) {
        let w = size.width, h = size.height
        let lit = card(t, 1.4, 2.0, 7.5, 8.2)
        let bg = night + (graphite - night) * lit
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Color(red: bg.x, green: bg.y, blue: bg.z)))
        // A soft pool of light behind the mark on the end card.
        if lit > 0.01 {
            let glow = Gradient(colors: [Color.white.opacity(0.035 * lit), .clear])
            context.fill(Path(ellipseIn: CGRect(x: w / 2 - w * 0.9, y: h * 0.42 - w * 0.9, width: w * 1.8, height: w * 1.8)),
                         with: .radialGradient(glow, center: CGPoint(x: w / 2, y: h * 0.42), startRadius: 0, endRadius: w * 0.9))
        }
        drawStars(context, w: w, h: h, t: t, visibility: 0.42 + 0.58 * (1 - lit))
        drawField(context, w: w, h: h, t: t, steer: steer)
        drawMark(context, w: w, h: h, t: t)
        drawCard(context, w: w, h: h, insets: insets, t: t)
    }

    /// Every sprite at its pixel size, and the frame at the moments that first use each kind of drawing.
    static func warm(_ context: GraphicsContext, size: CGSize, insets: EdgeInsets, sprites: [[Image]]) {
        let side = Double(CosmicDriftSplashArt.side) / 3
        for (p, levels) in sprites.enumerated() {
            for (l, image) in levels.enumerated() {
                var c = context
                c.opacity = 0.5
                c.draw(image, in: CGRect(x: Double(l) * 20, y: Double(p) * 20, width: side, height: side))
            }
        }
        for t in [1.2, 1.8, 2.0, 2.6, 3.4, 4.3, 4.9, 5.4, 5.8, 6.1, 6.6, 7.0, 8.3, 8.7] {
            draw(context, size: size, insets: insets, t: t, steer: .zero)
        }
    }

    static func drawStars(_ context: GraphicsContext, w: Double, h: Double, t: Double, visibility: Double) {
        // Stars are bucketed by brightness so a frame fills eight paths, not 260.
        var buckets = [Path](repeating: Path(), count: 8)
        var blue = [Path](repeating: Path(), count: 8)
        for s in CosmicDriftSplashArt.stars {
            var b = s.brightness
            if s.twinkles { b *= 0.55 + 0.45 * sin(2 * .pi * s.rate * t / loop + s.phase) }
            let k = min(7, max(0, Int(b * 8)))
            let r = s.size
            let rect = CGRect(x: s.u * w - r, y: s.v * h - r, width: r * 2, height: r * 2)
            if s.blue { blue[k].addEllipse(in: rect) } else { buckets[k].addEllipse(in: rect) }
        }
        for k in 0..<8 {
            let a = (Double(k) + 0.5) / 8 * visibility
            context.fill(buckets[k], with: .color(Color(red: 0.93, green: 0.95, blue: 1).opacity(a)))
            context.fill(blue[k], with: .color(Color(red: 0.62, green: 0.74, blue: 1).opacity(a)))
        }
    }

    // MARK: Fly-through

    struct Projected { let x, y, radius, depth, alpha, blur: Double; let palette: Int; let angle, distance: Double }

    static func camera(_ t: Double) -> Double {
        let tf = t - 1.9
        if tf < 2.2 { return 4.2 * tf }
        let s = min(tf - 2.2, 1.4)
        return 9.24 + 4.2 * (s - s * s / 2.8)
    }
    static func recede(_ t: Double) -> Double { easeInOut(seg(t, 4.1, 5.5)) }
    static func roll(_ t: Double) -> Double { 0.32 * (t - 1.9) + 4.2 * recede(t) }

    static func drawField(_ context: GraphicsContext, w: Double, h: Double, t: Double, steer: CGSize) {
        guard t > 1.85, t < 5.75, let sprites = CosmicDriftSplashSprites.shared.images else { return }
        let art = CosmicDriftSplashArt.self
        let show = smoothstep(seg(t, 1.9, 2.6)) * (1 - smoothstep(seg(t, 5.05, 5.7)))
        let cam = camera(t), e = recede(t), phi = roll(t)
        let spin = (roll(t + 0.01) - phi) / 0.01
        let centring = easeInOut(seg(t, 4.0, 5.4))
        let vx = w / 2 + w * 0.10 * (1 - centring) + steer.width
        let vy = h / 2 - h * 0.08 * (1 - centring) + steer.height
        let f = 0.85 * w

        var items: [Projected] = []
        for s in art.spheres {
            let d0 = s.z - cam + 1.2
            if d0 < 0.25 { continue } // passed the camera: it never comes back
            let d = d0 * (1 + 6 * e) + 6 * e
            if d < 0.25 || d > 16 { continue }
            let a = s.angle + phi
            let x = vx + f * s.distance * cos(a) / d, y = vy + f * s.distance * sin(a) / d
            let r = f * s.radius / d
            let reach = r * art.spriteSpan / 2
            if x + reach < 0 || x - reach > w || y + reach < 0 || y - reach > h { continue }
            let fog = 1 - art.smoothstep(6, 14, d), near = art.smoothstep(0.25, 0.7, d0)
            let focus = 2.2
            var blur = d < focus ? 0.12 * (focus / d - 1) : 0.05 * (d / focus - 1)
            blur = min(0.17, blur + 0.06 * e)
            items.append(Projected(x: x, y: y, radius: r, depth: d, alpha: show * fog * near, blur: blur,
                                   palette: s.palette, angle: atan2(y - vy, x - vx), distance: hypot(x - vx, y - vy)))
        }
        items.sort { $0.depth > $1.depth }

        // Arc trails while the field spirals away.
        if e > 0.02 {
            let sweep = min(1.6, spin * 0.35)
            for p in items where p.distance > p.radius {
                var arc = Path()
                arc.addArc(center: CGPoint(x: vx, y: vy), radius: p.distance, startAngle: .radians(p.angle - sweep), endAngle: .radians(p.angle), clockwise: false)
                context.stroke(arc, with: .color(art.palettes[p.palette].trail.opacity(0.18 * p.alpha * min(1, e * 3))),
                               style: StrokeStyle(lineWidth: max(1, min(p.radius * 0.9, 10)), lineCap: .round))
            }
        }

        for p in items where p.alpha > 0.005 {
            let level = blurLevel(p.blur)
            let lo = Int(level), hi = min(art.blurLevels.count - 1, lo + 1), k = level - Double(lo)
            let half = p.radius * art.spriteSpan
            let rect = CGRect(x: p.x - half, y: p.y - half, width: half * 2, height: half * 2)
            var c = context
            c.opacity = p.alpha
            c.draw(sprites[p.palette][lo], in: rect)
            if k > 0.01 && hi != lo {
                c.opacity = p.alpha * k
                c.draw(sprites[p.palette][hi], in: rect)
            }
        }

        // Dust glints: bucketed by brightness, with a faint halo on the brightest.
        var dots = [Path](repeating: Path(), count: 6)
        var halos = Path()
        for s in art.specks {
            let d0 = s.z - cam + 1.2
            if d0 < 0.3 { continue }
            let d = d0 * (1 + 6 * e) + 6 * e
            if d < 0.3 || d > 16 { continue }
            let c = cos(phi), sn = sin(phi)
            let rx = s.x * c - s.y * sn, ry = s.x * sn + s.y * c
            let x = vx + f * rx / d, y = vy + f * ry / d
            if x < -4 || x > w + 4 || y < -4 || y > h + 4 { continue }
            let tw = pow(0.5 + 0.5 * sin(2 * .pi * s.rate * t / loop + s.phase), 3)
            let a = (0.35 + 0.65 * tw) * (1 - art.smoothstep(6, 14, d)) * art.smoothstep(0.3, 0.8, d0)
            let r = min(2.6, max(0.5, f * 0.009 * s.size / d))
            let k = min(5, Int(a * 6))
            dots[k].addEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
            if a > 0.8 { halos.addEllipse(in: CGRect(x: x - r * 3, y: y - r * 3, width: r * 6, height: r * 6)) }
        }
        context.fill(halos, with: .color(.white.opacity(0.12 * show)))
        for k in 0..<6 { context.fill(dots[k], with: .color(.white.opacity((Double(k) + 0.5) / 6 * show))) }
    }

    static func smoothstep(_ u: Double) -> Double { u * u * (3 - 2 * u) }

    /// Continuous index into `blurLevels` for a blur given as a fraction of the diameter.
    static func blurLevel(_ blur: Double) -> Double {
        let levels = CosmicDriftSplashArt.blurLevels
        for i in 0..<(levels.count - 1) where blur <= levels[i + 1] {
            return Double(i) + (blur - levels[i]) / (levels[i + 1] - levels[i])
        }
        return Double(levels.count - 1)
    }

    // MARK: Mark

    static func drawMark(_ context: GraphicsContext, w: Double, h: Double, t: Double) {
        let art = CosmicDriftSplashArt.self
        let unit = 0.92 // points per mark unit: a 92 pt mark
        let rise = card(t, 1.05, 1.6, 7.9, 8.6)
        let cy = mix(h * 0.5, h * 0.405, rise)
        let planet = CGPoint(x: w / 2 + art.visualOffset.x * unit, y: cy + art.visualOffset.y * unit)
        let white = Color(red: 0.96, green: 0.96, blue: 0.95)

        var scale = 1.0, alpha = 1.0, turn = 0.0
        var blob = 0.0, wobble = 0.0
        var orbit = [1.0, 1.0], moon = 1.0
        if t < 5.7 {
            if t > 2.1 { return }
            scale = 1 + 3 * easeIn(seg(t, 1.6, 2.1))
            alpha = 1 - seg(t, 1.75, 2.1)
        } else {
            let dot = easeOut(seg(t, 5.7, 5.85)) * 5
            let swell = easeOutBack(seg(t, 5.85, 6.35), 1.2)
            let shrink = easeInOut(seg(t, 6.35, 6.95))
            let full = 44.0
            blob = t < 5.85 ? dot : mix(mix(5, full, swell), art.planetRadius * unit, shrink)
            wobble = 0.07 * sin(.pi * seg(t, 5.85, 6.7))
            orbit = [easeOut(seg(t, 6.38, 6.90)), easeOut(seg(t, 6.50, 7.00))]
            moon = easeOutBack(seg(t, 6.85, 7.15), 2.2)
            turn = -0.35 * (1 - easeOut(seg(t, 6.35, 7.2)))
        }
        guard alpha > 0.002 else { return }

        context.drawLayer { layer in
            layer.opacity = alpha
            layer.translateBy(x: planet.x, y: planet.y)
            layer.scaleBy(x: scale, y: scale)
            layer.rotate(by: .radians(turn))
            // Orbits and moon in mark units.
            var m = layer
            m.scaleBy(x: unit, y: unit)
            let style = StrokeStyle(lineWidth: art.stroke, lineCap: .round)
            m.stroke(art.orbitPath(art.orbits[1], progress: orbit[0]), with: .color(white), style: style)
            m.stroke(art.orbitPath(art.orbits[0], progress: orbit[1]), with: .color(white), style: style)
            if moon > 0.001 {
                let a = art.moonAngle * .pi / 180, r = art.moonRadius * moon
                let c = CGPoint(x: art.orbits[1].radius * cos(a), y: art.orbits[1].radius * sin(a))
                m.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)), with: .color(white))
            }
            if t < 5.7 {
                let r = art.planetRadius
                m.fill(Path(ellipseIn: CGRect(x: -r, y: -r, width: r * 2, height: r * 2)), with: .color(white))
            } else if blob > 0.01 {
                // The blob: a circle whose radius wobbles in three and five lobes while it swells.
                var path = Path()
                for i in 0...64 {
                    let a = Double(i) / 64 * 2 * .pi
                    let rr = blob * (1 + wobble * (0.6 * sin(3 * a + 9 * t) + 0.4 * sin(5 * a - 7 * t)))
                    let p = CGPoint(x: rr * cos(a), y: rr * sin(a))
                    if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
                }
                path.closeSubpath()
                layer.fill(path, with: .color(white))
            }
        }
    }

    // MARK: End card

    static func drawCard(_ context: GraphicsContext, w: Double, h: Double, insets: EdgeInsets, t: Double) {
        let art = CosmicDriftSplashArt.self
        let word = card(t, 1.0, 1.45, 8.1, 8.9, out: easeIn, back: easeOut)
        let tag = card(t, 1.0, 1.4, 8.4, 9.1, out: easeIn, back: easeOut)
        let cta = card(t, 1.0, 1.35, 8.5, 9.2, out: easeIn, back: easeOut)
        let close = card(t, 1.0, 1.3, 8.7, 9.2, out: easeIn, back: easeOut)
        let markY = h * 0.405

        if word > 0.002 {
            context.drawLayer { layer in
                layer.opacity = word
                let blur = 14 * (1 - word)
                if blur > 0.2 { layer.addFilter(.blur(radius: blur)) }
                let text = Text("orrery").font(.custom(art.fontName, size: 46)).tracking(0.5).foregroundColor(ink)
                layer.translateBy(x: w / 2, y: markY + 76)
                layer.scaleBy(x: 1 + 0.06 * (1 - word), y: 1 + 0.06 * (1 - word))
                layer.draw(text, at: .zero, anchor: .center)
            }
        }
        if tag > 0.002 {
            context.drawLayer { layer in
                layer.opacity = tag
                let blur = 6 * (1 - tag)
                if blur > 0.2 { layer.addFilter(.blur(radius: blur)) }
                let text = Text("A quieter way to explore the web.").font(.custom(art.fontName, size: 15))
                    .foregroundColor(Color(red: 0.55, green: 0.59, blue: 0.60))
                layer.draw(text, at: CGPoint(x: w / 2, y: markY + 120 + 6 * (1 - tag)), anchor: .center)
            }
        }
        if cta > 0.002 {
            var c = context
            c.opacity = cta
            let height = 54.0, side = 24.0
            let y = h - max(insets.bottom, 16) - 10 - height + 10 * (1 - cta)
            let rect = CGRect(x: side, y: y, width: w - side * 2, height: height)
            let pill = Path(roundedRect: rect, cornerRadius: height / 2)
            // The pill comes up from a dim teal to its full colour.
            let dim = SIMD3<Double>(0.11, 0.29, 0.31), teal = SIMD3<Double>(0.149, 0.651, 0.718)
            let col = dim + (teal - dim) * cta
            c.fill(pill, with: .color(Color(red: col.x, green: col.y, blue: col.z)))
            c.fill(Path(roundedRect: rect.insetBy(dx: 1, dy: 1), cornerRadius: height / 2 - 1).offsetBy(dx: 0, dy: 0),
                   with: .linearGradient(Gradient(colors: [.white.opacity(0.14), .clear]), startPoint: CGPoint(x: 0, y: rect.minY), endPoint: CGPoint(x: 0, y: rect.midY)))
            c.draw(Text("Get started").font(.system(size: 17, weight: .semibold)).foregroundColor(Color(red: 0.03, green: 0.10, blue: 0.11)),
                   at: CGPoint(x: rect.midX, y: rect.midY), anchor: .center)
        }
        if close > 0.002 {
            var c = context
            c.opacity = close
            let r = 17.0
            let center = CGPoint(x: w - 20 - r, y: insets.top + 10 + r)
            c.fill(Path(ellipseIn: CGRect(x: center.x - r, y: center.y - r, width: r * 2, height: r * 2)), with: .color(.white.opacity(0.07)))
            var cross = Path()
            let s = 5.5
            cross.move(to: CGPoint(x: center.x - s, y: center.y - s)); cross.addLine(to: CGPoint(x: center.x + s, y: center.y + s))
            cross.move(to: CGPoint(x: center.x + s, y: center.y - s)); cross.addLine(to: CGPoint(x: center.x - s, y: center.y + s))
            c.stroke(cross, with: .color(.white.opacity(0.75)), style: StrokeStyle(lineWidth: 1.7, lineCap: .round))
        }
    }
}

/// Holds the loop on its resting frame while the sprites are drawn out of sight and uploaded, until two frames in a row
/// have come within 50 ms (nothing left to upload); the frame after that is the loop's time 0. A reference, so it changes
/// per frame without updating the view.
final class CosmicDriftSplashWarmUp {
    private(set) var start: Date?
    private var first: CFTimeInterval?
    private var last: CFTimeInterval?
    private var frames = 0
    private var quick = 0

    /// Called once a frame before the loop has started. Returns whether this frame draws the sprites out of sight
    /// (the second to fourth do: the first shows the resting frame at once); the loop starts on the first frame after
    /// two in a row came within 50 ms, or after 1.5 s at most.
    func warming(at date: Date) -> Bool {
        if start != nil { return false }
        let now = CACurrentMediaTime()
        quick = last.map { now - $0 < 0.05 } == true ? quick + 1 : 0
        last = now
        if first == nil { first = now }
        frames += 1
        if frames > 4 && (quick >= 2 || now - (first ?? now) > 1.5) { start = date; return false }
        return frames >= 2 && frames <= 4
    }
}
