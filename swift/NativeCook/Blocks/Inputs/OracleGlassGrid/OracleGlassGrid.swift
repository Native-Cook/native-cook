import QuartzCore
import SwiftUI
import UIKit

/*
 Oracle Glass Grid — the "ask the cards" screen of Sibyl, an invented oracle app.

 Look: a live lava gradient (purple, maroon, deep green, teal; OracleGlassGrid.metal) behind a 3 × 3 grid of
 frosted glass cards, each with a thin glowing line icon drawn in code that shimmers (rays ripple, stars
 twinkle, the sun breathes), under "What answers do you seek?" and above an "ASK FOR ME" pill.
 Hero: the chosen card turns solid white while the other eight gather into a stack behind it; the stack rides
 to the centre and the others vanish; the white card flips about Y until it is edge-on, a glowing line that
 shrinks into a caret, and the caret opens sideways into the question field "Tap to type". The return plays
 it backwards and the grid re-deals from the stack with a small overshoot.

 autoPlay: loop = 10 s (OracleGlassGridModel.frame); picks Career, types "Should I take the leap?", folds back.
 Frame 10 s equals frame 0.
 Touch: tap any card to open it into the field; tap the back arrow or swipe down to fold it back. A touch
 during the story takes it over where it is. 2 s after the last touch an open field folds back and, at rest,
 the loop starts again.
 Reduced motion: no loop, lava and icons still; a tap crossfades between the grid and the field.
*/
struct OracleGlassGrid: View {
    var autoPlay = false

    static let loop = OracleGlassGridModel.loop
    typealias M = OracleGlassGridModel

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var driver = OracleGlassGridDriver()
    @State private var live = false
    @State private var token = 0

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(M.color(0x1D3A2B).ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let w = max(1, size.width), h = max(1, size.height)
        let top = insets.top, bottom = insets.bottom
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let grid = M.gridCenter(w: w, h: h, top: top, bottom: bottom)
        let field = M.fieldCenter(w: w, h: h)
        return TimelineView(.animation(paused: !auto && !live)) { timeline in
            let now = timeline.date.timeIntervalSinceReferenceDate
            let f = driver.frame(at: now, auto: auto, reduced: reduced)
            let cards = Self.cards(f, grid: grid, field: field)
            ZStack(alignment: .topLeading) {
                Rectangle()
                    .fill(.white)
                    .frame(width: w, height: h)
                    .colorEffect(ShaderLibrary.oracleGlassGridLava(.float2(Float(w), Float(h)),
                                                                   .float(Float(2 * Double.pi * f.clock / M.loop))))
                Canvas { c, _ in Self.drawHalo(&c, f: f, cards: cards) }
                    .frame(width: w, height: h)
                ForEach(cards.order, id: \.self) { i in
                    let card = cards.items[i]
                    OracleGlassGridCard(index: i, frame: f, white: i == f.chosen ? f.pick : 0,
                                        iconDim: card.iconDim, shade: i == f.chosen ? f.flip : 0,
                                        sheen: Self.sheenOffset(f.sheen, slot: M.slot(i, grid: grid), grid: grid))
                        .frame(width: M.cardW, height: M.cardH)
                        .scaleEffect(card.scale)
                        .projectionEffect(Self.flip(degrees: card.rotation))
                        .opacity(card.alpha)
                        .position(card.center)
                }
                Canvas { c, _ in
                    Self.drawField(&c, f: f, center: field, w: w)
                    Self.drawChrome(&c, f: f, w: w, h: h, top: top, bottom: bottom)
                }
                .frame(width: w, height: h)
                .allowsHitTesting(false)
            }
            .frame(width: w, height: h, alignment: .topLeading)
        }
        .contentShape(Rectangle())
        .gesture(touch(grid: grid, top: top, auto: auto, reduced: reduced))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Sibyl. What answers do you seek? Nine topic cards.")
        .accessibilityAddTraits(.isButton)
        .accessibilityHint("Opens a card into the question field.")
        .onChange(of: auto) { driver.reset() }
        .onChange(of: reduced) { driver.reset() }
    }

    // MARK: Card placement

    struct Placed {
        var center: CGPoint
        var scale: CGFloat
        var alpha: Double
        var rotation: Double
        var iconDim: Double
    }

    struct Layout {
        var items: [Placed]
        var order: [Int]
    }

    static func cards(_ f: OracleGlassGridFrame, grid: CGPoint, field: CGPoint) -> Layout {
        let c = f.chosen
        let chosenSlot = M.slot(c, grid: grid)
        let group = CGPoint(x: M.lerp(chosenSlot.x, field.x, f.center), y: M.lerp(chosenSlot.y, field.y, f.center))
        let groupScale = M.lerp(1, M.centerScale, f.center)
        var items: [Placed] = []
        for i in 0 ..< 9 {
            let slot = M.slot(i, grid: grid)
            if i == c {
                let s = groupScale * CGFloat(1 - 0.05 * f.press)
                let hidden = f.flip >= 0.999
                items.append(Placed(center: group, scale: s, alpha: hidden ? 0 : f.cardAlpha,
                                    rotation: 90 * f.flip, iconDim: 1))
            } else {
                let k = CGFloat(min(f.ranks[i], 3))
                let g = f.gather[i]
                let spread = groupScale * CGFloat(1 - f.vanish)
                let target = CGPoint(x: group.x + 9 * k * spread, y: group.y - 7 * k * spread)
                let s = M.lerp(1, groupScale * (1 - 0.03 * k), g) * CGFloat(1 - 0.08 * f.vanish)
                items.append(Placed(center: CGPoint(x: M.lerp(slot.x, target.x, g), y: M.lerp(slot.y, target.y, g)),
                                    scale: s, alpha: (1 - f.vanish) * f.cardAlpha, rotation: 0,
                                    iconDim: 1 - 0.5 * min(max(g, 0), 1)))
            }
        }
        // Farthest first, the chosen card on top.
        let order = (0 ..< 9).sorted { a, b in
            let ra = a == c ? 99 : -f.ranks[a], rb = b == c ? 99 : -f.ranks[b]
            return ra < rb
        }
        return Layout(items: items, order: order)
    }

    /// The flip about the card's vertical axis, seen from a camera 600 pt away.
    static func flip(degrees: Double) -> ProjectionTransform {
        guard degrees > 0.01 else { return ProjectionTransform() }
        let cx = M.cardW / 2, cy = M.cardH / 2
        var m = CATransform3DMakeTranslation(-cx, -cy, 0)
        m = CATransform3DConcat(m, CATransform3DMakeRotation(CGFloat(degrees * .pi / 180), 0, 1, 0))
        var p = CATransform3DIdentity
        p.m34 = -1 / M.camera
        m = CATransform3DConcat(m, p)
        m = CATransform3DConcat(m, CATransform3DMakeTranslation(cx, cy, 0))
        return ProjectionTransform(m)
    }

    /// Where the grid's diagonal light band crosses this card, in the card's own diagonal coordinate.
    static func sheenOffset(_ s: Double, slot: CGPoint, grid: CGPoint) -> CGFloat? {
        guard s > 0 && s < 1 else { return nil }
        let span: CGFloat = 820
        let band = -span / 2 + span * CGFloat(s)          // along x + 0.5 y, from the grid's centre
        let local = (slot.x - grid.x) + 0.5 * (slot.y - grid.y)
        return band - local
    }

    // MARK: Touch

    private func wake() {
        live = true
        token += 1
        let mine = token
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 8_000_000_000)
            if token == mine { live = false }
        }
    }

    private func touch(grid: CGPoint, top: CGFloat, auto: Bool, reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onEnded { v in
                let now = Date.timeIntervalSinceReferenceDate
                let moved = hypot(v.translation.width, v.translation.height)
                let swipeDown = v.translation.height > 60 && abs(v.translation.height) > abs(v.translation.width)
                let backHit = v.location.x < 72 && abs(v.location.y - (top + 24)) < 30
                if reduced {
                    if driver.reducedOpen {
                        if swipeDown || (moved < 10 && backHit) {
                            driver.setReduced(open: false, chosen: nil, at: now)
                            Self.impact()
                        }
                    } else if moved < 10, let i = Self.cardAt(v.location, grid: grid) {
                        driver.setReduced(open: true, chosen: i, at: now)
                        Self.impact()
                    }
                    wake()
                    return
                }
                if driver.isRest(at: now, auto: auto) {
                    if moved < 10, let i = Self.cardAt(v.location, grid: grid) {
                        _ = driver.pick(i, at: now)
                        Self.impact()
                    } else {
                        driver.takeOver(at: now, auto: auto)
                    }
                } else {
                    driver.takeOver(at: now, auto: auto)
                    if swipeDown || (moved < 10 && backHit) {
                        if driver.close(at: now) { Self.impact() }
                    }
                }
                wake()
            }
    }

    static func cardAt(_ p: CGPoint, grid: CGPoint) -> Int? {
        for i in 0 ..< 9 {
            let s = M.slot(i, grid: grid)
            if abs(p.x - s.x) <= M.cardW / 2 + M.gap / 2 && abs(p.y - s.y) <= M.cardH / 2 + M.gap / 2 { return i }
        }
        return nil
    }

    static func impact() { UIImpactFeedbackGenerator(style: .light).impactOccurred() }

    // MARK: Drawing

    static func text(_ s: String, size: CGFloat, weight: Font.Weight, italic: Bool = false, tracking: CGFloat = 0,
                     _ color: Color) -> Text {
        var font = Font.system(size: size, weight: weight)
        if italic { font = font.italic() }
        return Text(s).font(font).tracking(tracking).foregroundColor(color)
    }

    static func width(_ r: GraphicsContext.ResolvedText) -> CGFloat {
        r.measure(in: CGSize(width: 2000, height: 400)).width
    }

    /// Draws resolved text centred on `x`, its cap middle on `y`.
    static func putCentered(_ c: inout GraphicsContext, _ r: GraphicsContext.ResolvedText, x: CGFloat, y: CGFloat,
                            capHalf: CGFloat) {
        let size = r.measure(in: CGSize(width: 2000, height: 400))
        let base = r.firstBaseline(in: size)
        c.draw(r, in: CGRect(x: x - size.width / 2, y: y + capHalf - base, width: size.width + 2, height: size.height))
    }

    static func drawHalo(_ c: inout GraphicsContext, f: OracleGlassGridFrame, cards: Layout) {
        guard f.halo > 0.01 else { return }
        let p = cards.items[f.chosen].center
        let r = M.cardH * cards.items[f.chosen].scale * 1.05
        c.fill(Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: 2 * r, height: 2 * r)),
               with: .radialGradient(Gradient(colors: [M.color(M.white, 0.42 * f.halo), M.color(M.white, 0)]),
                                     center: p, startRadius: 0, endRadius: r))
    }

    static func drawField(_ c: inout GraphicsContext, f: OracleGlassGridFrame, center p: CGPoint, w: CGFloat) {
        guard f.flip > 0.5 || f.reducedField, f.fieldAlpha > 0.001 else { return }
        var g = c
        g.opacity = f.fieldAlpha
        let o = f.open
        let fieldW = w - 2 * M.fieldMargin
        if o > 0.001 {
            let fw = M.lerp(M.caretW, fieldW, o)
            let fh = M.lerp(M.caretH, M.fieldH, M.easeOut(min(max(o * 2, 0), 1)))
            let rect = CGRect(x: p.x - fw / 2, y: p.y - fh / 2, width: fw, height: fh)
            let shape = Path(roundedRect: rect, cornerRadius: fh / 2)
            let a = M.seg(o, 0, 0.12)
            g.fill(shape, with: .color(M.color(M.white, 0.07 * a)))
            var glow = g
            glow.addFilter(.blur(radius: 6))
            glow.stroke(shape, with: .color(M.color(M.white, 0.45 * a)), lineWidth: 2.5)
            g.stroke(shape, with: .color(M.color(M.white, 0.92 * a)), lineWidth: 1.4)
            let inner = rect.insetBy(dx: 3.5, dy: 3.5)
            if inner.width > 8 {
                g.stroke(Path(roundedRect: inner, cornerRadius: inner.height / 2),
                         with: .color(M.color(M.white, 0.30 * M.seg(o, 0.3, 0.8))), lineWidth: 0.8)
            }
            if f.rings > 0.001 {
                let r = 4.5 * M.lerp(0.4, 1, f.rings)
                for x in [rect.minX, rect.maxX] {
                    let ring = Path(ellipseIn: CGRect(x: x - r, y: p.y - r, width: 2 * r, height: 2 * r))
                    g.fill(ring, with: .color(M.color(0x6E5A78, 0.9 * f.rings)))
                    g.stroke(ring, with: .color(M.color(M.white, 0.95 * f.rings)), lineWidth: 1.2)
                }
            }
        }

        // Text, placeholder and the caret.
        let font17 = { (s: String, a: Double) in text(s, size: 17, weight: .semibold, M.color(M.white, a)) }
        var caretX = p.x
        if f.typed > 0 {
            let s = String(M.sample.prefix(f.typed))
            let r = g.resolve(font17(s, 1))
            let tw = width(r)
            if f.textAlpha > 0.001 {
                var t = g
                t.opacity = f.textAlpha
                putCentered(&t, r, x: p.x, y: p.y, capHalf: 6)
            }
            caretX = M.lerp(p.x, p.x + tw / 2 + 3, f.textAlpha)
        } else {
            let r = g.resolve(font17(M.placeholder, 0.62))
            if f.placeholder > 0.001 {
                var t = g
                t.opacity = f.placeholder
                putCentered(&t, r, x: p.x, y: p.y, capHalf: 6)
            }
            caretX = M.lerp(p.x, p.x - width(r) / 2 - 6, f.toPlaceholder)
        }

        let lineA = f.lineAlpha * f.caret
        if lineA > 0.001 {
            let lh = M.lerp(M.cardH * M.centerScale, M.caretH, f.line)
            let rect = CGRect(x: caretX - M.caretW / 2, y: p.y - lh / 2, width: M.caretW, height: lh)
            let shape = Path(roundedRect: rect, cornerRadius: M.caretW / 2)
            // While it is the edge-on card, the line blooms; as a caret it keeps a smaller glow.
            let bloom = lineA * (1 - M.seg(o, 0, 0.6))
            if bloom > 0.001 {
                let br = CGRect(x: caretX - 34, y: p.y - 34, width: 68, height: 68)
                g.fill(Path(ellipseIn: br), with: .radialGradient(
                    Gradient(colors: [M.color(M.white, 0.30 * bloom), M.color(M.white, 0)]),
                    center: CGPoint(x: caretX, y: p.y), startRadius: 0, endRadius: 34))
            }
            var glow = g
            glow.addFilter(.blur(radius: 5))
            glow.fill(Path(roundedRect: rect.insetBy(dx: -1.5, dy: 0), cornerRadius: 2),
                      with: .color(M.color(M.white, (0.95 - 0.45 * M.seg(o, 0.5, 1)) * lineA)))
            g.fill(shape, with: .color(M.color(M.white, lineA)))
        }
    }

    static func drawChrome(_ c: inout GraphicsContext, f: OracleGlassGridFrame, w: CGFloat, h: CGFloat,
                           top: CGFloat, bottom: CGFloat) {
        let y = top + 24
        let white = M.color(M.white, 0.95)
        // Sibyl's mark: a ring with a four-point star and an orbiting dot.
        let mark = Path(ellipseIn: CGRect(x: w / 2 - 15, y: y - 15, width: 30, height: 30))
        var glow = c
        glow.addFilter(.blur(radius: 4))
        glow.stroke(mark, with: .color(M.color(M.white, 0.55)), lineWidth: 2)
        c.stroke(mark, with: .color(white), lineWidth: 1.4)
        c.fill(M.path("M\(w / 2) \(y - 8)L\(w / 2 + 1.9) \(y - 1.9)L\(w / 2 + 8) \(y)L\(w / 2 + 1.9) \(y + 1.9)L\(w / 2) \(y + 8)L\(w / 2 - 1.9) \(y + 1.9)L\(w / 2 - 8) \(y)L\(w / 2 - 1.9) \(y - 1.9)Z"),
               with: .color(white))
        c.fill(Path(ellipseIn: CGRect(x: w / 2 + 8.5 - 1.6, y: y - 8.5 - 1.6, width: 3.2, height: 3.2)), with: .color(white))

        // Info and More.
        for (i, x) in [w - 66, w - 34].enumerated() {
            c.stroke(Path(ellipseIn: CGRect(x: x - 12, y: y - 12, width: 24, height: 24)),
                     with: .color(M.color(M.white, 0.9)), lineWidth: 1.3)
            if i == 0 {
                c.fill(Path(ellipseIn: CGRect(x: x - 1.1, y: y - 6.1, width: 2.2, height: 2.2)), with: .color(white))
                c.fill(Path(roundedRect: CGRect(x: x - 0.8, y: y - 2.4, width: 1.6, height: 8), cornerRadius: 0.8),
                       with: .color(white))
            } else {
                for dx in [-5.0, 0, 5.0] {
                    c.fill(Path(ellipseIn: CGRect(x: x + dx - 1.2, y: y - 1.2, width: 2.4, height: 2.4)), with: .color(white))
                }
            }
        }

        // Back arrow (field only).
        if f.titleField > 0.001 {
            var a = Path()
            a.move(to: CGPoint(x: 41, y: y))
            a.addLine(to: CGPoint(x: 23, y: y))
            a.move(to: CGPoint(x: 30, y: y - 7))
            a.addLine(to: CGPoint(x: 23, y: y))
            a.addLine(to: CGPoint(x: 30, y: y + 7))
            c.stroke(a, with: .color(M.color(M.white, 0.95 * f.titleField)),
                     style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
        }

        // Titles.
        let ty = top + 86
        if f.titleGrid > 0.001 {
            let r = c.resolve(text(M.titleGrid, size: 20, weight: .semibold, M.color(M.white, 0.96)))
            var t = c
            t.opacity = f.titleGrid
            putCentered(&t, r, x: w / 2, y: ty + f.titleGridY, capHalf: 7)
        }
        if f.titleField > 0.001 {
            let r = c.resolve(text(M.titleField, size: 20, weight: .semibold, M.color(M.white, 0.96)))
            var t = c
            t.opacity = f.titleField
            putCentered(&t, r, x: w / 2, y: ty + f.titleFieldY, capHalf: 7)
        }

        // ASK FOR ME.
        let askA = min(max(f.askAlpha, 0), 1)
        if askA > 0.001 {
            var k = c
            k.opacity = askA
            let ay = h - bottom - 44
            let rect = CGRect(x: w / 2 - M.askW / 2, y: ay - M.askH / 2, width: M.askW, height: M.askH)
            let shape = Path(roundedRect: rect, cornerRadius: M.askH / 2)
            k.fill(shape, with: .color(M.color(M.white, 0.15)))
            k.stroke(shape, with: .color(M.color(M.white, 0.20)), lineWidth: 1)
            let r = k.resolve(text(M.ask, size: 12, weight: .bold, tracking: 1.4, M.color(M.white, 0.95)))
            putCentered(&k, r, x: w / 2 + 0.7, y: ay, capHalf: 4.3)
            let nudge = 1.6 * CGFloat(sin(2 * .pi * 5 * f.clock / M.loop))
            for side in [-1.0, 1.0] {
                let s = CGFloat(side)
                let cx = w / 2 + s * (82 - nudge)
                var ch = Path()
                ch.move(to: CGPoint(x: cx + s * 2, y: ay - 4))
                ch.addLine(to: CGPoint(x: cx - s * 2, y: ay))
                ch.addLine(to: CGPoint(x: cx + s * 2, y: ay + 4))
                k.stroke(ch, with: .color(M.color(M.white, 0.8)),
                         style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
                let dx = w / 2 + s * 64
                k.fill(Path(ellipseIn: CGRect(x: dx - 1.6, y: ay - 1.6, width: 3.2, height: 3.2)),
                       with: .color(M.color(M.white, 0.8)))
            }
        }
    }
}

/// One frosted glass card: glass (or white) face, its line icon and its label.
struct OracleGlassGridCard: View {
    typealias M = OracleGlassGridModel

    let index: Int
    let frame: OracleGlassGridFrame
    let white: Double
    let iconDim: Double
    let shade: Double
    let sheen: CGFloat?

    var body: some View {
        Canvas { c, size in
            let rect = CGRect(origin: .zero, size: size)
            let shape = Path(roundedRect: rect, cornerRadius: M.cardR)
            let glass = 1 - white
            // Face.
            c.fill(shape, with: .color(M.color(M.white, 0.10 * glass)))
            c.fill(shape, with: .linearGradient(
                Gradient(colors: [M.color(M.white, 0.16 * glass), M.color(M.white, 0)]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height * 0.45)))
            if white > 0.001 {
                c.fill(shape, with: .color(M.color(M.cardWhite, white)))
            }
            if let s = sheen {
                // A diagonal band of light crossing the grid (direction x + 0.5 y).
                let d = CGVector(dx: 1 / 1.25, dy: 0.5 / 1.25)
                let mid = CGPoint(x: size.width / 2, y: size.height / 2)
                let c0 = CGPoint(x: mid.x + d.dx * (s - 70), y: mid.y + d.dy * (s - 70))
                let c1 = CGPoint(x: mid.x + d.dx * (s + 70), y: mid.y + d.dy * (s + 70))
                c.fill(shape, with: .linearGradient(
                    Gradient(stops: [.init(color: M.color(M.white, 0), location: 0),
                                     .init(color: M.color(M.white, 0.16 * glass), location: 0.5),
                                     .init(color: M.color(M.white, 0), location: 1)]),
                    startPoint: c0, endPoint: c1))
            }
            c.stroke(Path(roundedRect: rect.insetBy(dx: 0.6, dy: 0.6), cornerRadius: M.cardR - 0.6),
                     with: .color(M.color(M.white, 0.80 + 0.2 * white)), lineWidth: 1.2)
            if glass > 0.001 {
                c.stroke(Path(roundedRect: rect.insetBy(dx: 2, dy: 2), cornerRadius: M.cardR - 2),
                         with: .color(M.color(M.white, 0.18 * glass)), lineWidth: 0.6)
            }

            // Icon.
            let ink = mix(M.white, M.ink, white)
            let marks = M.icons[index]
            let paths = M.iconPaths[index]
            var icon = c
            icon.translateBy(x: size.width / 2, y: size.height / 2 - M.iconLift)
            icon.scaleBy(x: M.iconScale, y: M.iconScale)
            icon.translateBy(x: -28, y: -28)
            if glass > 0.001 {
                var glow = icon
                glow.addFilter(.blur(radius: 3.2))
                for (m, p) in zip(marks, paths) where m.glow {
                    let a = M.markAlpha(m, clock: frame.clock) * iconDim * glass * 0.9
                    if m.fill { glow.fill(p, with: .color(M.color(M.white, a))) }
                    glow.stroke(p, with: .color(M.color(M.white, a)), lineWidth: 2.4)
                }
            }
            let style = StrokeStyle(lineWidth: 1.3, lineCap: .round, lineJoin: .round)
            for (m, p) in zip(marks, paths) {
                let a = (m.k > 0 ? M.markAlpha(m, clock: frame.clock) * glass + m.dim * white : m.dim) * iconDim
                if m.fill { icon.fill(p, with: .color(M.color(ink, a))) } else {
                    icon.stroke(p, with: .color(M.color(ink, a)), style: style)
                }
            }

            // Label.
            let lines = M.labels[index]
            for (j, line) in lines.enumerated() {
                let r = c.resolve(OracleGlassGrid.text(line, size: 12, weight: .semibold, italic: true,
                                                       M.color(ink, iconDim > 0.99 ? 1 : 0.6 + 0.4 * iconDim)))
                let baseline = size.height - 13 - 13.5 * CGFloat(lines.count - 1 - j)
                let sz = r.measure(in: CGSize(width: 400, height: 100))
                let base = r.firstBaseline(in: sz)
                c.draw(r, in: CGRect(x: (size.width - sz.width) / 2, y: baseline - base, width: sz.width + 2, height: sz.height))
            }

            // Turning away: the face darkens and a specular band slides across it.
            if shade > 0.001 {
                c.fill(shape, with: .color(M.color(0x000000, 0.25 * shade)))
                let x = size.width * CGFloat(1.2 - 1.6 * shade)
                c.fill(shape, with: .linearGradient(
                    Gradient(stops: [.init(color: M.color(M.white, 0), location: 0),
                                     .init(color: M.color(M.white, 0.45 * sin(.pi * min(shade * 1.2, 1))), location: 0.5),
                                     .init(color: M.color(M.white, 0), location: 1)]),
                    startPoint: CGPoint(x: x - 30, y: 0), endPoint: CGPoint(x: x + 30, y: 0)))
            }
        }
    }

    private func mix(_ a: UInt32, _ b: UInt32, _ t: Double) -> UInt32 {
        func ch(_ v: UInt32, _ s: UInt32) -> Double { Double((v >> s) & 0xFF) }
        func m(_ s: UInt32) -> UInt32 { UInt32((ch(a, s) + (ch(b, s) - ch(a, s)) * t).rounded()) << s }
        return m(16) | m(8) | m(0)
    }
}
