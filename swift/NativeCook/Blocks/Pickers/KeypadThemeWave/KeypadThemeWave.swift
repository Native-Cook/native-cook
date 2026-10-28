import SwiftUI
import UIKit

/*
 Keypad Theme Wave — the theme step of an invented calculator app's onboarding ("tallo").

 Look: a cream page; at the top a preview card of the calculator (display "0", a 4 × 5 keypad of round keys
 and the app's wordmark); under it a strip of swatches, each a tiny striped thumbnail of one theme, whose
 centred swatch is bigger, framed in white and is the selection; then "Customize your calculator", a line
 of help and a dark Continue button. Everything is drawn in code (KeypadThemeWaveModel.swift holds the ten
 palettes and the timing).

 Hero: when the selection changes, the new theme sweeps across the preview. The card's colour is revealed
 by a circle from the corner the new swatch came from, and the keys follow on anti-diagonals from the same
 corner, 60 ms apart: each key drains its old colour into its centre, stands for a beat as a hollow ring in
 the new theme's line colour, then the new fill blooms out of the centre with a small overshoot.

 autoPlay: loop = 10 s. Rest on Classic; the strip glides to Marigold (1.0 s), Lilac (3.2 s) and Mint
 (5.2 s), each sending a wave from the right, then sweeps back five swatches to Classic (7.2 s), whose
 wave comes from the left; rest from 8.5 s. Every frame is a function of the loop time.
 Touch: drag or flick the strip, it snaps to the centred swatch and sends a wave; tap a swatch to pick it;
 tap the keys to calculate (the delete tag removes a digit). 2 s after the last touch the display clears,
 the strip glides home to Classic and the loop restarts.
 Reduced motion: no loop; a change crossfades the card and keys in 0.25 s.
*/
struct KeypadThemeWave: View {
    /// Plays the 10 s theme loop until touched.
    var autoPlay = false

    typealias M = KeypadThemeWaveModel

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var driver = KeypadThemeWaveDriver()
    @State private var live = false
    @State private var token = 0
    @State private var grab: KeypadThemeWaveGrab = .none

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Self.color(Self.page).ignoresSafeArea())
        .preferredColorScheme(.light)
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let g = KeypadThemeWaveGeo(w: max(1, size.width), h: max(1, size.height), top: insets.top, bottom: insets.bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return TimelineView(.animation(paused: !auto && !live)) { timeline in
            let now = timeline.date.timeIntervalSinceReferenceDate
            let scene = driver.frame(at: now, auto: auto, reduced: reduced)
            Canvas { c, _ in
                Self.draw(&c, g, scene)
            }
            .frame(width: size.width, height: size.height)
        }
        .contentShape(Rectangle())
        .gesture(touch(g, auto: auto, reduced: reduced))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Customize your calculator. Theme \(M.themes[driver.applied].name).")
        .accessibilityAction(named: "Next theme") { step(1, auto: auto, reduced: reduced) }
        .accessibilityAction(named: "Previous theme") { step(-1, auto: auto, reduced: reduced) }
        .onChange(of: auto) { driver.reset() }
    }

    // MARK: Touch

    private func wake() {
        live = true
        token += 1
        let mine = token
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            if token == mine && !driver.dragging { live = false }
        }
    }

    private func step(_ by: Int, auto: Bool, reduced: Bool) {
        let now = Date.timeIntervalSinceReferenceDate
        if driver.tapSwatch(by, at: now, auto: auto, reduced: reduced) { Self.impact() }
        wake()
    }

    private func touch(_ g: KeypadThemeWaveGeo, auto: Bool, reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                let now = Date.timeIntervalSinceReferenceDate
                if case .none = grab {
                    grab = g.hit(v.startLocation)
                    switch grab {
                    case .strip:
                        driver.beginDrag(at: now, auto: auto, reduced: reduced)
                    case let .key(r, c):
                        driver.pressDown(r, c, at: now, auto: auto)
                    case .delete:
                        driver.touch(at: now, auto: auto)
                    default:
                        break
                    }
                    wake()
                }
                if case .strip = grab {
                    if driver.drag(v.translation.width, at: now) { Self.tick() }
                    wake()
                }
            }
            .onEnded { v in
                let now = Date.timeIntervalSinceReferenceDate
                let moved = hypot(v.translation.width, v.translation.height)
                switch grab {
                case .strip:
                    if moved < 8 {
                        driver.cancelDrag(at: now)
                        let d = g.swatchOffset(v.location.x, scroll: driver.scroll(at: now, reduced: reduced))
                        if d != 0, driver.tapSwatch(d, at: now, auto: auto, reduced: reduced) { Self.impact() }
                    } else if driver.endDrag(v.velocity.width, at: now, reduced: reduced) {
                        Self.impact()
                    }
                case let .key(r, c):
                    if moved < 14 {
                        driver.pressKey(r, c, at: now)
                        Self.soft()
                    }
                case .delete:
                    if moved < 14 {
                        driver.backspace(at: now)
                        Self.soft()
                    }
                default:
                    break
                }
                grab = .none
                wake()
            }
    }

    static func impact() { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    static func tick() { UISelectionFeedbackGenerator().selectionChanged() }
    static func soft() { UIImpactFeedbackGenerator(style: .soft).impactOccurred(intensity: 0.6) }

    // MARK: Colours and copy

    static let page: UInt32 = 0xFAF6F0
    static let ink: UInt32 = 0x2A2622
    static let muted: UInt32 = 0x77716A

    static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255, opacity: a)
    }

    static func color(_ c: (Double, Double, Double), _ a: Double = 1) -> Color {
        Color(.sRGB, red: c.0, green: c.1, blue: c.2, opacity: a)
    }

    static let title = ["Customize", "your calculator"]
    static let subtitle = "Swipe the strip to pick colors you love."
    static let cta = "Continue"

    // MARK: Drawing

    static func draw(_ c: inout GraphicsContext, _ g: KeypadThemeWaveGeo, _ s: KeypadThemeWaveScene) {
        c.fill(Path(CGRect(x: 0, y: 0, width: g.w, height: g.h)), with: .color(color(page)))
        card(&c, g, s)
        keys(&c, g, s)
        strip(&c, g, s)
        copy(&c, g)
    }

    static func text(_ s: String, _ size: CGFloat, _ weight: Font.Weight, tracking: CGFloat = 0) -> Text {
        Text(s).font(.system(size: size, weight: weight)).tracking(tracking)
    }

    /// The card: its shadow, the base theme and a revealed circle for each change.
    static func card(_ c: inout GraphicsContext, _ g: KeypadThemeWaveGeo, _ s: KeypadThemeWaveScene) {
        let shape = Path(roundedRect: g.cardRect, cornerRadius: 26, style: .continuous)
        var amb = c
        amb.addFilter(.blur(radius: 15))
        amb.fill(shape.offsetBy(dx: 0, dy: 14), with: .color(.black.opacity(0.08)))
        var contact = c
        contact.addFilter(.blur(radius: 1.5))
        contact.fill(shape.offsetBy(dx: 0, dy: 1), with: .color(.black.opacity(0.06)))

        var k = c
        k.clip(to: shape)
        cardFace(&k, g, s, M.themes[s.base])
        for ch in s.changes where s.now >= ch.at {
            let tau = s.now - ch.at
            let theme = M.themes[ch.to]
            if s.reduced {
                var l = k
                l.opacity = M.easeInOutCubic(min(1, tau / M.reducedDur))
                cardFace(&l, g, s, theme)
                continue
            }
            let p = M.reveal(tau)
            if p <= 0 { continue }
            let o = g.corner(ch.dir)
            let r = p * g.revealMax
            let disc = Path(ellipseIn: CGRect(x: o.x - r, y: o.y - r, width: 2 * r, height: 2 * r))
            var l = k
            l.clip(to: disc)
            cardFace(&l, g, s, theme)
            // A soft light band just inside the moving edge, gone as the reveal completes.
            let rim = 1 - M.seg(tau, M.revealDur * 0.6, M.revealDur)
            if rim > 0 && r > 6 {
                var b = l
                b.addFilter(.blur(radius: 5))
                let rr = r - 6
                b.stroke(Path(ellipseIn: CGRect(x: o.x - rr, y: o.y - rr, width: 2 * rr, height: 2 * rr)),
                         with: .color(.white.opacity(0.22 * rim)), lineWidth: 8)
            }
        }
    }

    /// One theme's card: background, display and wordmark.
    static func cardFace(_ c: inout GraphicsContext, _ g: KeypadThemeWaveGeo, _ s: KeypadThemeWaveScene, _ t: KeypadThemeWaveTheme) {
        c.fill(Path(g.cardRect), with: .color(color(t.bg)))
        let ink = color(t.display)
        let size: CGFloat = s.display.count > 7 ? 34 : 40
        var d = c.resolve(text(s.display, size, .medium, tracking: -0.5))
        d.shading = .color(ink)
        c.draw(d, at: CGPoint(x: g.displayRight, y: g.displayY), anchor: .trailing)
        // The delete tag: a label shape pointing left with a small cross.
        let tx = g.cx + 134, ty = g.displayY
        var tag = Path()
        tag.move(to: CGPoint(x: tx - 10, y: ty))
        tag.addLine(to: CGPoint(x: tx - 5, y: ty - 6.5))
        tag.addLine(to: CGPoint(x: tx + 8, y: ty - 6.5))
        tag.addQuadCurve(to: CGPoint(x: tx + 9.5, y: ty - 5), control: CGPoint(x: tx + 9.5, y: ty - 6.5))
        tag.addLine(to: CGPoint(x: tx + 9.5, y: ty + 5))
        tag.addQuadCurve(to: CGPoint(x: tx + 8, y: ty + 6.5), control: CGPoint(x: tx + 9.5, y: ty + 6.5))
        tag.addLine(to: CGPoint(x: tx - 5, y: ty + 6.5))
        tag.closeSubpath()
        c.stroke(tag, with: .color(color(t.display, 0.45)), style: StrokeStyle(lineWidth: 1.4, lineJoin: .round))
        var x = Path()
        x.move(to: CGPoint(x: tx - 1, y: ty - 2.6))
        x.addLine(to: CGPoint(x: tx + 4.2, y: ty + 2.6))
        x.move(to: CGPoint(x: tx + 4.2, y: ty - 2.6))
        x.addLine(to: CGPoint(x: tx - 1, y: ty + 2.6))
        c.stroke(x, with: .color(color(t.display, 0.45)), style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
        // The wordmark in the keypad's empty cell.
        let p = g.key(3, 0)
        var w1 = c.resolve(text("tallo", 13, .heavy, tracking: -0.3))
        w1.shading = .color(color(t.display, 0.45))
        c.draw(w1, at: CGPoint(x: p.x, y: p.y - 4), anchor: .center)
        var w2 = c.resolve(text("CALC", 7.5, .semibold, tracking: 1.6))
        w2.shading = .color(color(t.display, 0.4))
        c.draw(w2, at: CGPoint(x: p.x + 0.8, y: p.y + 8), anchor: .center)
    }

    static func labelFont(_ s: String, _ role: Int) -> Text {
        if s == "AC" { return text(s, 16, .semibold) }
        if role == 2 { return text(s, 23, .medium) }
        if role == 1 { return text(s, 18, .medium) }
        return text(s, 21, .medium)
    }

    static func keys(_ c: inout GraphicsContext, _ g: KeypadThemeWaveGeo, _ s: KeypadThemeWaveScene) {
        let R = M.keyRadius
        let circle = Path(ellipseIn: CGRect(x: -R, y: -R, width: 2 * R, height: 2 * R))
        func disc(_ r: Double) -> Path { Path(ellipseIn: CGRect(x: -r, y: -r, width: 2 * r, height: 2 * r)) }
        for r in 0..<4 {
            for col in 0..<5 where M.isKey(r, col) {
                let role = M.role(r, col)
                let st = M.keyState(s, r, col)
                let from = M.themes[st.from]
                let to = M.themes[st.to]
                let l = M.look(st.u, reduced: s.reduced)
                let pr = M.press(s.now - s.presses[r * 5 + col])
                let p = g.key(r, col)
                var k = c
                k.translateBy(x: p.x, y: p.y)
                let sc = l.scale * (1 - 0.09 * pr)
                k.scaleBy(x: sc, y: sc)
                let label = k.resolve(labelFont(M.labels[r][col], role))
                func put(_ hex: UInt32, _ a: Double) {
                    guard a > 0.004 else { return }
                    var t = label
                    t.shading = .color(color(hex, a))
                    k.draw(t, at: .zero, anchor: .center)
                }
                if s.reduced {
                    k.fill(circle, with: .color(color(M.mixHex(from.fill(role), to.fill(role), l.mix))))
                    put(from.ink(role), 1 - l.mix)
                    put(to.ink(role), l.mix)
                } else {
                    if l.oldR > 0.2 { k.fill(disc(l.oldR), with: .color(color(from.fill(role)))) }
                    put(from.ink(role), l.oldLabel)
                    if l.ring > 0.004 {
                        k.stroke(disc(R - 0.7), with: .color(color(to.ring, l.ring)), lineWidth: 1.4)
                    }
                    put(to.ring, l.ringLabel)
                    if l.newR > 0.2 { k.fill(disc(l.newR), with: .color(color(to.fill(role)))) }
                    put(to.ink(role), l.newLabel)
                }
                if pr > 0.004 {
                    k.fill(circle, with: .color(color(to.ink(role), 0.16 * pr)))
                }
            }
        }
    }

    /// The swatch strip: an endless row of striped thumbnails, the centred one larger and framed.
    static func strip(_ c: inout GraphicsContext, _ g: KeypadThemeWaveGeo, _ s: KeypadThemeWaveScene) {
        var k = c
        k.clip(to: Path(CGRect(x: g.cardX, y: g.stripY - 40, width: g.cardW, height: 80)))
        var items: [(i: Int, d: Double)] = []
        let n = Double(M.count)
        for i in 0..<M.count {
            let d0 = M.wrap(Double(i) - s.scroll)
            for d in [d0 - n, d0, d0 + n] where abs(M.swatchX(d)) < g.cardW / 2 + 24 {
                items.append((i, d))
            }
        }
        items.sort { abs($0.d) > abs($1.d) }
        for it in items {
            let a = min(abs(it.d), 1)
            let big = 1 - a
            let w = 32 + 12 * big
            let h = 30 + 12 * big
            let rad = 5 + 7 * big
            let x = g.cx + M.swatchX(it.d)
            let rect = CGRect(x: x - w / 2, y: g.stripY - h / 2, width: w, height: h)
            let shape = Path(roundedRect: rect, cornerRadius: rad, style: .continuous)
            if big > 0.01 {
                var sh = k
                sh.addFilter(.blur(radius: 6))
                sh.fill(shape.offsetBy(dx: 0, dy: 3), with: .color(.black.opacity(0.2 * big)))
            }
            let t = M.themes[it.i]
            var sw = k
            sw.clip(to: shape)
            let bands: [(UInt32, Double)] = [(t.bg, 0.34), (t.op, 0.22), (t.fn, 0.22), (t.digit, 0.22)]
            var y = rect.minY
            for (hex, f) in bands {
                sw.fill(Path(CGRect(x: rect.minX, y: y, width: w, height: h * f + 0.5)), with: .color(color(hex)))
                y += h * f
            }
            if big > 0.01 {
                k.stroke(Path(roundedRect: rect.insetBy(dx: 1.5, dy: 1.5), cornerRadius: rad - 1.5, style: .continuous),
                         with: .color(color(0xFFFFFF, big)), lineWidth: 3)
            }
        }
        // The ends fade into the page.
        let fade = 30.0
        let pageC = color(page)
        let clear = color(page, 0)
        k.fill(Path(CGRect(x: g.cardX, y: g.stripY - 40, width: fade, height: 80)),
               with: .linearGradient(Gradient(colors: [pageC, clear]), startPoint: CGPoint(x: g.cardX, y: 0),
                                     endPoint: CGPoint(x: g.cardX + fade, y: 0)))
        let right = g.cardX + g.cardW
        k.fill(Path(CGRect(x: right - fade, y: g.stripY - 40, width: fade, height: 80)),
               with: .linearGradient(Gradient(colors: [clear, pageC]), startPoint: CGPoint(x: right - fade, y: 0),
                                     endPoint: CGPoint(x: right, y: 0)))
    }

    static func copy(_ c: inout GraphicsContext, _ g: KeypadThemeWaveGeo) {
        c.draw(text(title[0], 32, .bold, tracking: -0.6).foregroundColor(color(ink)),
               at: CGPoint(x: g.cardX + 2, y: g.titleY), anchor: .leading)
        c.draw(text(title[1], 32, .bold, tracking: -0.6).foregroundColor(color(ink)),
               at: CGPoint(x: g.cardX + 2, y: g.titleY + 41), anchor: .leading)
        c.draw(text(subtitle, 15, .regular).foregroundColor(color(muted)),
               at: CGPoint(x: g.cardX + 2, y: g.titleY + 84), anchor: .leading)
        let pill = CGRect(x: g.cardX, y: g.buttonY - 27, width: g.cardW, height: 54)
        var sh = c
        sh.addFilter(.blur(radius: 10))
        sh.fill(Path(roundedRect: pill.offsetBy(dx: 0, dy: 6), cornerRadius: 27), with: .color(.black.opacity(0.14)))
        c.fill(Path(roundedRect: pill, cornerRadius: 27), with: .color(color(ink)))
        c.draw(text(cta, 17, .semibold).foregroundColor(color(page)), at: CGPoint(x: g.cx, y: g.buttonY), anchor: .center)
    }
}

// MARK: Geometry

enum KeypadThemeWaveGrab {
    case none, strip, key(Int, Int), delete, other
}

struct KeypadThemeWaveGeo {
    typealias M = KeypadThemeWaveModel
    let w: Double
    let h: Double
    let top: Double
    let bottom: Double

    var cx: Double { w / 2 }
    var cardX: Double { 24 }
    var cardW: Double { w - 48 }
    var cardTop: Double { top + 45 }
    var cardH: Double { 352 }
    var cardRect: CGRect { CGRect(x: cardX, y: cardTop, width: cardW, height: cardH) }
    var displayY: Double { cardTop + 52 }
    var displayRight: Double { cx + 117 }
    var stripY: Double { cardTop + cardH + 64 }
    var titleY: Double { stripY + 98 }
    var buttonY: Double { h - max(bottom, 16) - 41 }
    var revealMax: Double { hypot(cardW, cardH) + 24 }

    func key(_ r: Int, _ c: Int) -> CGPoint {
        CGPoint(x: cx + Double(c - 2) * M.pitch, y: cardTop + 124 + Double(r) * M.pitch)
    }

    /// The card corner a change's colour comes from.
    func corner(_ dir: Int) -> CGPoint {
        CGPoint(x: dir > 0 ? cardX + cardW : cardX, y: cardTop + cardH)
    }

    func hit(_ p: CGPoint) -> KeypadThemeWaveGrab {
        if abs(p.y - stripY) < 34 && p.x > cardX - 8 && p.x < cardX + cardW + 8 { return .strip }
        for r in 0..<4 {
            for c in 0..<5 where M.isKey(r, c) {
                let k = key(r, c)
                if hypot(p.x - k.x, p.y - k.y) < M.pitch / 2 { return .key(r, c) }
            }
        }
        if hypot(p.x - (cx + 134), p.y - displayY) < 22 { return .delete }
        return .other
    }

    /// Which swatch (in steps from the centred one) sits under x.
    func swatchOffset(_ x: Double, scroll: Double) -> Int {
        let base = scroll.rounded()
        var best = 0
        var bestDist = Double.infinity
        for k in -6...6 {
            let d = Double(k) + base - scroll
            let dist = abs(cx + M.swatchX(d) - x)
            if dist < bestDist {
                bestDist = dist
                best = k
            }
        }
        return best
    }
}

// MARK: Driver

/// Turns the clock and the finger into scenes.
final class KeypadThemeWaveDriver {
    typealias M = KeypadThemeWaveModel

    private var autoStart: Double?
    private(set) var manual = false
    private(set) var dragging = false
    private var lastTouch = -1e9
    private var returnAt: Double?

    private var base = M.home
    private var changes: [KeypadThemeWaveChange] = []

    // The strip: a spring from (anchorX, anchorV) at anchorAt towards target, or the finger.
    private var target = Double(M.home)
    private var anchorAt = -1e9
    private var anchorX = 0.0
    private var anchorV = 0.0
    private var dragFrom = 0.0
    private var dragDx = 0.0
    private var detent = 0
    private var reducedStrip = false

    private var calc = KeypadThemeWaveCalc()
    private var presses = [Double](repeating: -1e9, count: 20)

    static let idleBeforeHome = 2.0
    static let homeDur = 1.4

    var applied: Int { changes.last?.to ?? base }

    func reset() {
        autoStart = nil
        manual = false
        dragging = false
        returnAt = nil
        base = M.home
        changes = []
        target = Double(M.home)
        anchorAt = -1e9
        anchorX = 0
        anchorV = 0
        calc.clear()
        presses = [Double](repeating: -1e9, count: 20)
    }

    func scroll(at now: Double, reduced: Bool) -> Double {
        if dragging { return dragFrom - dragDx / M.swatchPitch }
        let tau = now - anchorAt
        if reduced { return target + anchorX * (1 - M.easeInOutCubic(M.seg(tau, 0, 0.2))) }
        return target + M.spring(anchorX, anchorV, tau)
    }

    private func velocity(at now: Double, reduced: Bool) -> Double {
        if dragging || reduced { return 0 }
        return M.springVelocity(anchorX, anchorV, now - anchorAt)
    }

    func frame(at now: Double, auto: Bool, reduced: Bool) -> KeypadThemeWaveScene {
        if auto && manual && !dragging {
            if returnAt == nil && now >= lastTouch + Self.idleBeforeHome { goHome(at: now, reduced: reduced) }
            if let r = returnAt, now >= r + Self.homeDur {
                manual = false
                returnAt = nil
                autoStart = now
                base = M.home
                changes = []
            }
        }
        if auto && !manual {
            if autoStart == nil { autoStart = now }
            let t = (now - autoStart!).truncatingRemainder(dividingBy: M.loop)
            return M.autoScene(t)
        }
        // Changes that are complete everywhere become the base.
        while let first = changes.first, now > first.at + M.settle {
            base = first.to
            changes.removeFirst()
        }
        var s = KeypadThemeWaveScene()
        s.now = now
        s.base = base
        s.changes = changes
        s.scroll = scroll(at: now, reduced: reduced)
        s.display = calc.display
        s.presses = presses
        s.reduced = reduced
        return s
    }

    /// The finger takes over from autoPlay: carry on from what the loop shows now.
    func touch(at now: Double, auto: Bool) {
        lastTouch = now
        returnAt = nil
        guard auto && !manual else { return }
        manual = true
        let start = autoStart ?? now
        let cycles = ((now - start) / M.loop).rounded(.down)
        let loopStart = start + cycles * M.loop
        let t = now - loopStart
        base = M.home
        // A move that has started keeps its wave, even if the wave starts a moment later.
        changes = M.autoChanges.filter { $0.at - M.waveAfterMove <= t }.map {
            KeypadThemeWaveChange(at: $0.at + loopStart, to: $0.to, dir: $0.dir)
        }
        let a = M.autoScroll(t)
        target = a.target
        anchorAt = now
        anchorX = a.s - a.target
        anchorV = a.v
    }

    private func send(_ to: Int, dir: Int, at now: Double) -> Bool {
        if to == applied { return false }
        changes.append(KeypadThemeWaveChange(at: now, to: to, dir: dir >= 0 ? 1 : -1))
        return true
    }

    private func settle(to t: Double, at now: Double, reduced: Bool) {
        let s = scroll(at: now, reduced: reduced)
        let v = velocity(at: now, reduced: reduced)
        target = t
        anchorAt = now
        anchorX = s - t
        anchorV = v
    }

    func beginDrag(at now: Double, auto: Bool, reduced: Bool) {
        touch(at: now, auto: auto)
        dragFrom = scroll(at: now, reduced: reduced)
        dragDx = 0
        detent = Int(dragFrom.rounded())
        dragging = true
    }

    /// Follows the finger; true when a swatch crosses the centre.
    func drag(_ dx: Double, at now: Double) -> Bool {
        lastTouch = now
        dragDx = dx
        let d = Int((dragFrom - dx / M.swatchPitch).rounded())
        if d != detent {
            detent = d
            return true
        }
        return false
    }

    func cancelDrag(at now: Double) {
        guard dragging else { return }
        let s = dragFrom - dragDx / M.swatchPitch
        dragging = false
        target = s.rounded()
        anchorAt = now
        anchorX = s - target
        anchorV = 0
    }

    /// Snaps to the swatch the flick lands on; true when that sends a wave.
    func endDrag(_ vx: Double, at now: Double, reduced: Bool) -> Bool {
        guard dragging else { return false }
        lastTouch = now
        let s = dragFrom - dragDx / M.swatchPitch
        let v = max(-40, min(40, -vx / M.swatchPitch))
        dragging = false
        var t = (s + v * 0.2).rounded()
        t = max(s.rounded() - 4, min(s.rounded() + 4, t))
        target = t
        anchorAt = now
        anchorX = s - t
        anchorV = reduced ? 0 : v
        let dir = t > dragFrom ? 1 : t < dragFrom ? -1 : (v >= 0 ? 1 : -1)
        return send(M.mod(Int(t)), dir: dir, at: now)
    }

    /// Picks the swatch `by` steps from the centred one; true when that sends a wave.
    func tapSwatch(_ by: Int, at now: Double, auto: Bool, reduced: Bool) -> Bool {
        touch(at: now, auto: auto)
        let s = scroll(at: now, reduced: reduced)
        let t = s.rounded() + Double(by)
        settle(to: t, at: now, reduced: reduced)
        return send(M.mod(Int(t)), dir: by >= 0 ? 1 : -1, at: now)
    }

    func pressDown(_ r: Int, _ c: Int, at now: Double, auto: Bool) {
        touch(at: now, auto: auto)
        presses[r * 5 + c] = now
    }

    func pressKey(_ r: Int, _ c: Int, at now: Double) {
        lastTouch = now
        calc.press(M.labels[r][c])
    }

    func backspace(at now: Double) {
        lastTouch = now
        calc.backspace()
    }

    /// Clear the display and glide the strip home to Classic.
    private func goHome(at now: Double, reduced: Bool) {
        returnAt = now
        calc.clear()
        let s = scroll(at: now, reduced: reduced)
        let r = s.rounded()
        let k = r - Double(M.mod(Int(r))) + Double(M.home)
        var best = k
        for cand in [k - Double(M.count), k + Double(M.count)] where abs(cand - s) < abs(best - s) { best = cand }
        settle(to: best, at: now, reduced: reduced)
        _ = send(M.home, dir: best >= s ? 1 : -1, at: now)
    }
}
