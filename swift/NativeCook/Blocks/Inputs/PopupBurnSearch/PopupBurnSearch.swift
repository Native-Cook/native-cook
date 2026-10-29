import SwiftUI
import UIKit

/*
 Popup Burn Search — SupperSeek 99, an invented retro "ask the kitchen" search on a deep-blue desktop.

 Look: a black slanted search bar with a cream search button, a red comic starburst behind it, white answer
 pop-ups with lilac title bars, a dock with the app's name and a clock. All drawn in code.
 Hero: the answer pop-ups pile up, then burn away bottom first: a jagged burn front climbs each window, the
 paper is gone below it, a char rim and scorch sit above it and the rest darkens, a four-layer comic flame
 crown rides the front and boils at 12 fps, embers and ash rise, and a warm glow lights the desktop.
 Supporting: the query types with a caret, the starburst springs in, the cursor glides in and clicks with a
 squish, windows scale in with an overshoot and a settling jitter, the status line counts the answers down.

 autoPlay: loop = 9 s (PopupBurnSearchModel); rest on the empty bar → type → burst → click → six windows →
 burn → select-all and clear → rest. Every frame is a function of loop time; frame 9 s equals frame 0.
 Touch: tap the search button to spawn the windows, tap a window to burn it, long-press to burn them all.
 A touch takes over where the loop is; 2 s after the last touch the rest burns, the query clears, the loop restarts.
 Reduced motion: no loop; the query, burst and six windows stand still; taps fade windows out and back in.
*/
struct PopupBurnSearch: View {
    var autoPlay = false

    static let loop = PopupBurnSearchModel.loop
    typealias M = PopupBurnSearchModel

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var driver = PopupBurnSearchDriver()
    @State private var live = false
    @State private var token = 0
    @State private var downAt: Date?
    @State private var longFired = false

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            stage(size: size, top: insets.top, bottom: insets.bottom)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(PopupBurnSearchArt.color(0x12306A).ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private func stage(size: CGSize, top: CGFloat, bottom: CGFloat) -> some View {
        let w = max(1, size.width), h = max(1, size.height)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return TimelineView(.animation(paused: !auto && !live)) { timeline in
            let now = timeline.date.timeIntervalSinceReferenceDate
            let f = driver.frame(at: now, auto: auto, reduced: reduced)
            Canvas { c, _ in
                PopupBurnSearchArt.draw(&c, f: f, w: w, h: h, top: top, bottom: bottom)
            }
            .frame(width: w, height: h)
        }
        .contentShape(Rectangle())
        .gesture(touch(w: w, h: h, auto: auto))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("SupperSeek 99. What should I cook tonight? Six answers.")
        .accessibilityAddTraits(.isButton)
        .accessibilityHint("Search shows the answers; tap one to burn it, or long-press to burn them all.")
        .onChange(of: auto) { driver.reset() }
        .onChange(of: reduced) { driver.reset() }
    }

    // MARK: Touch

    private func wake() {
        live = true
        token += 1
        let mine = token
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 9_000_000_000)
            if token == mine { live = false }
        }
    }

    private func touch(w: CGFloat, h: CGFloat, auto: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                guard downAt == nil else { return }
                let start = Date()
                downAt = start
                longFired = false
                driver.takeOver(at: start.timeIntervalSinceReferenceDate, auto: auto)
                wake()
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 450_000_000)
                    guard downAt == start, !longFired else { return }
                    longFired = true
                    let now = Date.timeIntervalSinceReferenceDate
                    driver.takeOver(at: now, auto: auto)
                    if driver.burnAll(at: now) { Self.impact() }
                }
            }
            .onEnded { v in
                let held = longFired
                downAt = nil
                let now = Date.timeIntervalSinceReferenceDate
                driver.takeOver(at: now, auto: auto)
                wake()
                guard !held, hypot(v.translation.width, v.translation.height) < 10 else { return }
                if M.buttonHit(v.location, w: w, h: h) {
                    if driver.search(at: now) { Self.impact() }
                } else if driver.burn(at: v.location, now: now, w: w, h: h) {
                    Self.impact()
                }
            }
    }

    static func impact() {
        let g = UIImpactFeedbackGenerator(style: .light)
        g.impactOccurred()
    }
}

// MARK: Drawing

enum PopupBurnSearchArt {
    typealias M = PopupBurnSearchModel

    static func color(_ hex: Int, _ a: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255, opacity: a)
    }

    static func poly(_ pts: [CGPoint]) -> Path {
        var p = Path()
        guard let first = pts.first else { return p }
        p.move(to: first)
        for q in pts.dropFirst() { p.addLine(to: q) }
        p.closeSubpath()
        return p
    }

    static func line(_ pts: [CGPoint]) -> Path {
        var p = Path()
        guard let first = pts.first else { return p }
        p.move(to: first)
        for q in pts.dropFirst() { p.addLine(to: q) }
        return p
    }

    static let mono = Font.custom("Menlo-Bold", size: 10)

    static func draw(_ c: inout GraphicsContext, f: PopupBurnSearchFrame, w: CGFloat, h: CGFloat,
                     top: CGFloat, bottom: CGFloat) {
        background(&c, w: w, h: h)
        dots(&c, w: w, h: h)
        glows(&c, f: f, w: w, h: h)
        header(&c, w: w, top: top)
        dock(&c, w: w, h: h, bottom: bottom)
        searchBar(&c, f: f, w: w, h: h)
        for i in 0 ..< 6 { window(&c, i, f: f, w: w, h: h) }
        if !f.reduced {
            for i in 0 ..< 6 { flames(&c, i, f: f, w: w, h: h) }
        }
        cursor(&c, f: f, w: w, h: h)
    }

    static func background(_ c: inout GraphicsContext, w: CGFloat, h: CGFloat) {
        let r = CGRect(x: 0, y: 0, width: w, height: h)
        c.fill(Path(r), with: .linearGradient(
            Gradient(stops: [.init(color: color(0x0B1C3A), location: 0),
                             .init(color: color(0x1C4890), location: 0.55),
                             .init(color: color(0x3F86E2), location: 1)]),
            startPoint: .zero, endPoint: CGPoint(x: w, y: h)))
        c.fill(Path(r), with: .radialGradient(
            Gradient(colors: [color(0x6FB6FF, 0.35), color(0x6FB6FF, 0)]),
            center: CGPoint(x: w * 0.95, y: h * 0.3), startRadius: 0, endRadius: w * 0.8))
    }

    /// A faint desktop dot grid, 24 pt apart.
    static func dots(_ c: inout GraphicsContext, w: CGFloat, h: CGFloat) {
        var p = Path()
        var y: CGFloat = 12
        while y < h {
            var x: CGFloat = 12
            while x < w {
                p.addRect(CGRect(x: x - 0.75, y: y - 0.75, width: 1.5, height: 1.5))
                x += 24
            }
            y += 24
        }
        c.fill(p, with: .color(color(0xFFFFFF, 0.07)))
    }

    static func glows(_ c: inout GraphicsContext, f: PopupBurnSearchFrame, w: CGFloat, h: CGFloat) {
        guard !f.reduced else { return }
        for i in 0 ..< 6 where f.burnAge[i] >= 0 && f.burnAge[i] < M.burnLength {
            let a = f.burnAge[i]
            let r = M.windowRect(i, w: w, h: h)
            let g = M.flameGrow(a)
            let cy = min(max(M.burnFront(a, rect: r), r.minY), r.maxY)
            let center = CGPoint(x: r.midX, y: cy)
            c.fill(Path(ellipseIn: CGRect(x: center.x - 150, y: center.y - 150, width: 300, height: 300)),
                   with: .radialGradient(Gradient(colors: [color(0xFF7A1A, 0.28 * g), color(0xFF7A1A, 0)]),
                                         center: center, startRadius: 0, endRadius: 150))
        }
    }

    static let pixelIcon = [".###...", "#...#..", "#...#..", "#...#..", ".###...", "....##.", ".....##"]

    static func header(_ c: inout GraphicsContext, w: CGFloat, top: CGFloat) {
        let y = top + 22
        for (row, s) in pixelIcon.enumerated() {
            for (col, ch) in s.enumerated() where ch == "#" {
                c.fill(Path(CGRect(x: 20 + CGFloat(col) * 2, y: y - 7 + CGFloat(row) * 2, width: 2, height: 2)),
                       with: .color(color(0xFFE03D)))
            }
        }
        c.draw(Text("SupperSeek 99").font(.system(size: 15, weight: .bold)).foregroundColor(.white),
               at: CGPoint(x: 42, y: y), anchor: .leading)
        c.draw(Text("v1.0").font(.custom("Menlo-Bold", size: 11)).foregroundColor(color(0xFFFFFF, 0.55)),
               at: CGPoint(x: w - 20, y: y), anchor: .trailing)
    }

    static func dock(_ c: inout GraphicsContext, w: CGFloat, h: CGFloat, bottom: CGFloat) {
        let r = CGRect(x: 16, y: h - bottom - 46, width: w - 32, height: 36)
        c.fill(Path(roundedRect: r, cornerRadius: 4), with: .color(color(0x0A1838, 0.6)))
        c.fill(Path(CGRect(x: r.minX + 4, y: r.minY, width: r.width - 8, height: 1)), with: .color(color(0xFFFFFF, 0.12)))
        let box = CGRect(x: r.minX + 12, y: r.midY - 5, width: 10, height: 10)
        c.stroke(Path(box), with: .color(color(0xFFFFFF, 0.8)), lineWidth: 1.5)
        c.fill(Path(box.insetBy(dx: 3, dy: 3)), with: .color(color(0xFFE03D)))
        let label = Font.custom("Menlo-Bold", size: 11)
        c.draw(Text("SUPPERSEEK").font(label).foregroundColor(color(0xFFFFFF, 0.8)),
               at: CGPoint(x: r.minX + 30, y: r.midY), anchor: .leading)
        c.draw(Text("7:42 PM").font(label).foregroundColor(color(0xFFFFFF, 0.8)),
               at: CGPoint(x: r.maxX - 12, y: r.midY), anchor: .trailing)
    }

    static func searchBar(_ c: inout GraphicsContext, f: PopupBurnSearchFrame, w: CGFloat, h: CGFloat) {
        let cx = w / 2, y = M.barY(h: h), bx = M.buttonX(w: w)

        c.draw(Text("Ask the kitchen.").font(.system(size: 34, weight: .heavy)).foregroundColor(.white),
               at: CGPoint(x: cx - 164, y: y - 72), anchor: .leading)

        // Starburst behind the button.
        if f.burstScale > 0.001 {
            var b = c
            b.translateBy(x: bx + 4, y: y + 2)
            b.rotate(by: .degrees(f.burstAngle))
            b.scaleBy(x: f.burstScale, y: f.burstScale)
            b.fill(poly(M.burstPoints(q: f.burstQ)), with: .color(color(0xEF3B55)))
        }

        // Bar body.
        let body = poly([CGPoint(x: cx - 166, y: y - 16), CGPoint(x: cx + 104, y: y - 16),
                         CGPoint(x: cx + 112, y: y + 16), CGPoint(x: cx - 158, y: y + 16)])
        c.fill(body, with: .color(color(0x08080C)))

        // Query, select-all highlight and caret.
        let x0 = cx - 148
        let typed = String(M.query.prefix(f.typed))
        let text = Text(typed).font(.system(size: 11, weight: .heavy)).kerning(0.3)
        let resolved = c.resolve(text.foregroundColor(f.selected ? color(0x0B1A3A) : .white))
        let tw = typed.isEmpty ? 0 : resolved.measure(in: CGSize(width: 1000, height: 100)).width
        if f.selected && tw > 0 {
            c.fill(Path(CGRect(x: x0 - 3, y: y - 9, width: tw + 6, height: 18)), with: .color(color(0xFBF0D2)))
        }
        if !typed.isEmpty { c.draw(resolved, at: CGPoint(x: x0, y: y), anchor: .leading) }
        if f.caret {
            c.fill(Path(CGRect(x: x0 + tw + 3, y: y - 6.5, width: 2, height: 13)), with: .color(.white))
        }

        // Button: dips and darkens on the click.
        let dip = CGFloat(2 * f.press)
        let by = y + dip
        let button = poly([CGPoint(x: bx - 37, y: by - 24), CGPoint(x: bx + 25, y: by - 24),
                           CGPoint(x: bx + 37, y: by + 24), CGPoint(x: bx - 25, y: by + 24)])
        c.fill(button, with: .color(color(0xFBF0D2)))
        if f.press > 0 { c.fill(button, with: .color(color(0x000000, 0.12 * f.press))) }
        c.stroke(button, with: .color(color(0x08080C)), lineWidth: 1)
        let ring = CGRect(x: bx - 2 - 6.5, y: by - 2 - 6.5, width: 13, height: 13)
        c.stroke(Path(ellipseIn: ring), with: .color(color(0x121212)), lineWidth: 2.2)
        c.stroke(line([CGPoint(x: bx + 2.8, y: by + 2.8), CGPoint(x: bx + 7, y: by + 7)]),
                 with: .color(color(0x121212)), style: StrokeStyle(lineWidth: 2.6, lineCap: .round))

        // Status line.
        c.draw(Text(M.statuses[f.status]).font(mono).foregroundColor(color(0xFFFFFF, 0.72)),
               at: CGPoint(x: cx - 160, y: y + 40), anchor: .leading)
    }

    static func window(_ c: inout GraphicsContext, _ i: Int, f: PopupBurnSearchFrame, w: CGFloat, h: CGFloat) {
        let s = f.spawnAge[i], a = f.burnAge[i]
        guard s >= 0, a < M.burnLength else { return }
        let r0 = M.windowRect(i, w: w, h: h)
        var alpha = 1.0, scale = 1.0
        var off = CGPoint.zero
        if f.reduced {
            alpha = M.seg(s, 0, 0.25) * (a >= 0 ? 1 - M.seg(a, 0, 0.3) : 1)
        } else {
            alpha = M.seg(s, 0, 0.06)
            scale = 0.55 + 0.45 * M.easeOutBack(M.seg(s, 0, 0.26))
            let settle = 1 - M.easeOutCubic(M.seg(s, 0, 0.3))
            off = CGPoint(x: (M.hash(Double(i) * 5.1 + 1) - 0.5) * 20 * settle,
                          y: (M.hash(Double(i) * 5.1 + 2) - 0.5) * 16 * settle)
        }
        guard alpha > 0.001 else { return }
        let burning = !f.reduced && a >= 0
        let edge = burning ? M.edgePoints(i, a: a, rect: r0) : []
        c.drawLayer { l in
            l.opacity = alpha
            l.translateBy(x: r0.midX + off.x, y: r0.midY + off.y)
            l.scaleBy(x: scale, y: scale)
            l.translateBy(x: -r0.midX, y: -r0.midY)
            if burning {
                var clip = [CGPoint(x: r0.minX - 2, y: r0.minY - 2), CGPoint(x: r0.maxX + 14, y: r0.minY - 2)]
                clip.append(contentsOf: edge.reversed())
                l.clip(to: poly(clip))
            }
            let r = r0
            l.fill(Path(r.offsetBy(dx: 5, dy: 5)), with: .color(color(0x071226, 0.4)))
            l.fill(Path(r), with: .color(color(0xFBFBFE)))
            let bar = CGRect(x: r.minX, y: r.minY, width: r.width, height: M.titleH)
            l.fill(Path(bar), with: .color(color(0x6C63E6)))
            l.fill(Path(CGRect(x: r.minX, y: r.minY, width: r.width, height: 1)), with: .color(color(0x9A94FF)))
            titleButtons(&l, r: r)
            for (k, s) in M.answers[i].enumerated() {
                l.draw(Text(s).font(.system(size: 26)).foregroundColor(color(0x111118)),
                       at: CGPoint(x: r.minX + 16, y: r.minY + M.titleH + 30 + CGFloat(k) * 30), anchor: .leading)
            }
            l.stroke(Path(r), with: .color(color(0x1B1B3A, 0.4)), lineWidth: 1)
            if burning {
                // Scorch above the edge, a hard char rim on it, and the rest of the paper charring to black.
                let u = M.easeInOutSine(M.seg(a, 0.15, 1.6))
                let front = M.burnFront(a, rect: r)
                let reach = CGFloat(26 + 30 * u)
                l.fill(Path(CGRect(x: r.minX, y: front - reach, width: r.width, height: reach + 20)),
                       with: .linearGradient(
                           Gradient(colors: [color(0x6B3A16, 0), color(0x4A2410, 0.9)]),
                           startPoint: CGPoint(x: 0, y: front - reach), endPoint: CGPoint(x: 0, y: front)))
                let char = 0.85 * M.seg(a, 0.7, 1.5)
                if char > 0 { l.fill(Path(r), with: .color(color(0x120A06, char))) }
                l.stroke(line(edge), with: .color(color(0x1A0D06)), style: StrokeStyle(lineWidth: 14, lineJoin: .miter))
            }
        }
        if burning {
            let inside = edge.filter { $0.x >= r0.minX - 1 && $0.x <= r0.maxX + 1 && $0.y > r0.minY - 1 }
            if inside.count > 1 {
                c.stroke(line(inside.map { CGPoint(x: $0.x, y: $0.y + 1) }), with: .color(color(0xFF8C1A)),
                         style: StrokeStyle(lineWidth: 2.5, lineJoin: .round))
            }
        }
    }

    static func titleButtons(_ c: inout GraphicsContext, r: CGRect) {
        let ink = color(0x1B1B3A)
        for k in 0 ..< 3 {
            let b = CGRect(x: r.maxX - 4 - 10 - CGFloat(2 - k) * 12, y: r.minY + 4, width: 10, height: 10)
            c.fill(Path(b), with: .color(.white))
            c.stroke(Path(b.insetBy(dx: 0.5, dy: 0.5)), with: .color(ink), lineWidth: 1)
            switch k {
            case 0:
                c.fill(Path(CGRect(x: b.minX + 2.5, y: b.maxY - 3.5, width: 5, height: 1.2)), with: .color(ink))
            case 1:
                c.stroke(Path(CGRect(x: b.minX + 2.5, y: b.minY + 2.5, width: 5, height: 5)), with: .color(ink), lineWidth: 1)
            default:
                var x = Path()
                x.move(to: CGPoint(x: b.minX + 2.5, y: b.minY + 2.5)); x.addLine(to: CGPoint(x: b.maxX - 2.5, y: b.maxY - 2.5))
                x.move(to: CGPoint(x: b.maxX - 2.5, y: b.minY + 2.5)); x.addLine(to: CGPoint(x: b.minX + 2.5, y: b.maxY - 2.5))
                c.stroke(x, with: .color(ink), lineWidth: 1.2)
            }
        }
    }

    static let flameColors = [0xA80F28, 0xE8203A, 0xFF8C1A, 0xFFE03D]

    static func flames(_ c: inout GraphicsContext, _ i: Int, f: PopupBurnSearchFrame, w: CGFloat, h: CGFloat) {
        let a = f.burnAge[i]
        guard f.spawnAge[i] >= 0, a >= 0, a < M.burnLength else { return }
        let r = M.windowRect(i, w: w, h: h)
        if M.flameGrow(a) > 0.01 {
            for l in 0 ..< 4 {
                var path = poly(M.flameContour(i, layer: l, a: a, rect: r))
                if l == 0 { path = path.offsetBy(dx: 3, dy: 3) }
                c.fill(path, with: .color(color(flameColors[l])))
            }
        }
        for p in M.embers(i, a: a, rect: r) {
            var e = c
            e.translateBy(x: p.x, y: p.y)
            if p.angle != 0 { e.rotate(by: .radians(p.angle)) }
            e.fill(Path(CGRect(x: -p.size / 2, y: -p.size / 2, width: p.size, height: p.size)),
                   with: .color(color(p.color, p.alpha)))
        }
    }

    static let arrow: [CGPoint] = [CGPoint(x: 0, y: 0), CGPoint(x: 0, y: 17), CGPoint(x: 4.2, y: 13.2),
                                   CGPoint(x: 7, y: 19.6), CGPoint(x: 9.8, y: 18.4), CGPoint(x: 7.1, y: 12.2),
                                   CGPoint(x: 12.4, y: 12.2)]

    static func cursor(_ c: inout GraphicsContext, f: PopupBurnSearchFrame, w: CGFloat, h: CGFloat) {
        guard f.cursorAlpha > 0.001 else { return }
        var k = c
        k.opacity = f.cursorAlpha
        k.translateBy(x: M.buttonX(w: w) + f.cursor.x, y: M.barY(h: h) + f.cursor.y)
        k.scaleBy(x: 1.4 * f.cursorSquish, y: 1.4 * f.cursorSquish)
        let p = poly(arrow)
        k.fill(p, with: .color(.white))
        k.stroke(p, with: .color(color(0x08080C)), style: StrokeStyle(lineWidth: 1.6 / 1.4, lineJoin: .round))
    }
}
