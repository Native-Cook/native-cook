import SwiftUI
import UIKit

/*
 Inline Pill Reveal — the welcome screen of an invented slow-travel stays brand ("slowfield").

 Look: a flat terracotta screen with "Slow mornings," in Outfit, the brand mark and Skip at the
 top, page dots at the bottom. Hero: a caret blinks between the two words, then a pill opens in
 that gap and pushes the words apart by its real width until they slide off both edges; the pill
 drifts to the centre and grows into a full-bleed window (capsule corners → 0). Inside it is a
 sunrise over layered hills drawn in code (InlinePillRevealArt.swift): as the pill grows the hills
 rise into frame and the sun climbs over the ridge. "made yours" rises over the scene, then a
 Find your stay button; the page dots move on. It holds, then plays back to the text line.

 autoPlay: loop = 8 s (InlinePillRevealModel.timeline); frame 8 s equals frame 0.
 Touch: drag sideways on the line (or anywhere once open) to scrub the reveal; release snaps open
 or closed with the finger's speed; tap toggles. 2 s after the last touch it eases closed and the
 loop starts again.
 Reduced motion: no loop; the revealed screen shows still, a tap crossfades to the text line.
*/
struct InlinePillReveal: View {
    var autoPlay = false

    typealias M = InlinePillRevealModel
    typealias A = InlinePillRevealArt

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var driver = InlinePillRevealDriver()
    @State private var live = false
    @State private var token = 0
    @State private var grabbing = false

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(A.color(M.page).ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let w = max(1, size.width), h = max(1, size.height)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return TimelineView(.animation(paused: !auto && !live)) { timeline in
            let now = timeline.date.timeIntervalSinceReferenceDate
            let frame = driver.frame(at: now, auto: auto, reduced: reduced)
            let shown = reduced ? driver.shownAmount(at: now) : 1
            Canvas { c, _ in
                c.fill(Path(CGRect(x: 0, y: 0, width: w, height: h)), with: .color(A.color(M.page)))
                if reduced {
                    var a = c
                    a.opacity = 1 - shown
                    Self.draw(&a, w: w, h: h, top: insets.top, bottom: insets.bottom, f: M.reveal(0, t: 0))
                    var b = c
                    b.opacity = shown
                    Self.draw(&b, w: w, h: h, top: insets.top, bottom: insets.bottom, f: frame)
                } else {
                    Self.draw(&c, w: w, h: h, top: insets.top, bottom: insets.bottom, f: frame)
                }
                A.grain(&c, w, h, 0.045)
            }
            .frame(width: w, height: h)
        }
        .contentShape(Rectangle())
        .gesture(touch(w: w, h: h, auto: auto, reduced: reduced))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Slow mornings, made yours. Find your stay.")
        .accessibilityAddTraits(.isButton)
        .accessibilityHint("Opens or closes the morning view.")
        .onChange(of: auto) { driver.reset() }
        .onChange(of: reduced) { driver.reset() }
    }

    // MARK: Touch

    private func wake() {
        live = true
        token += 1
        let mine = token
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 3_500_000_000)
            if token == mine && !driver.dragging { live = false }
        }
    }

    private func touch(w: CGFloat, h: CGFloat, auto: Bool, reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                let now = Date.timeIntervalSinceReferenceDate
                if reduced { return }
                if !grabbing {
                    guard driver.accepts(y: v.startLocation.y, cy: h / 2, now: now, auto: auto) else { return }
                    grabbing = true
                    driver.begin(at: now, auto: auto)
                }
                if abs(v.translation.width) > 6 { driver.drag(v.translation.width, width: w, at: now) }
                wake()
            }
            .onEnded { v in
                let now = Date.timeIntervalSinceReferenceDate
                let moved = hypot(v.translation.width, v.translation.height)
                if reduced {
                    if moved < 10 {
                        driver.toggleReduced(at: now)
                        Self.impact()
                        wake()
                    }
                    return
                }
                guard grabbing else { return }
                grabbing = false
                let vx = v.velocity.width
                if driver.end(dx: v.translation.width, vx: vx, tap: moved < 8, width: w, at: now) { Self.impact() }
                wake()
            }
    }

    static func impact() { UIImpactFeedbackGenerator(style: .light).impactOccurred() }

    // MARK: Drawing

    static func text(_ s: String, _ name: String, _ size: CGFloat, tracking: CGFloat, _ hex: UInt32, _ a: Double = 1) -> Text {
        Text(s).font(.custom(name, fixedSize: size)).tracking(tracking).foregroundColor(A.color(hex, a))
    }

    /// Draws resolved text with its baseline at `y`, starting at `x`.
    static func put(_ c: inout GraphicsContext, _ r: GraphicsContext.ResolvedText, x: CGFloat, baseline y: CGFloat) {
        let size = r.measure(in: CGSize(width: 2000, height: 400))
        let base = r.firstBaseline(in: size)
        c.draw(r, in: CGRect(x: x, y: y - base, width: size.width + 4, height: size.height))
    }

    static func width(_ r: GraphicsContext.ResolvedText) -> CGFloat {
        r.measure(in: CGSize(width: 2000, height: 400)).width
    }

    static func draw(_ c: inout GraphicsContext, w: CGFloat, h: CGFloat, top: CGFloat, bottom: CGFloat, f: InlinePillRevealFrame) {
        let cy = h / 2
        let baseline = cy + M.baselineDrop
        let left = c.resolve(text(M.line1[0], M.regular, M.fontSize, tracking: M.tracking, M.cream))
        let right = c.resolve(text(M.line1[1], M.regular, M.fontSize, tracking: M.tracking, M.cream))
        let whole = c.resolve(text(M.line1.joined(separator: " "), M.regular, M.fontSize, tracking: M.tracking, M.cream))
        let wl = width(left), wr = width(right)
        let space = max(6, width(whole) - wl - wr)
        let startX = (w - width(whole)) / 2
        let gapX = startX + wl + space / 2

        // The pill: inline (width from the gap, drifting to the centre), then full bleed.
        let o = f.open
        let inlineW = M.lerp(0, w - 2 * M.pillMargin, o)
        let inlineX = M.lerp(gapX, w / 2, o)
        let inlineH = M.lerp(M.caretH, M.pillH, M.easeOut(M.seg(o, 0, 0.2)))
        let inline = CGRect(x: inlineX - inlineW / 2, y: cy - inlineH / 2, width: inlineW, height: inlineH)
        let full = f.full
        let rect = CGRect(x: M.lerp(inline.minX, 0, full), y: M.lerp(inline.minY, 0, full),
                          width: M.lerp(inline.width, w, full), height: M.lerp(inline.height, h, full))

        // Words ride the pill's edges.
        let restHalf = space / 2 + M.caretPart * CGFloat(f.part)
        let half = M.lerp(restHalf, M.wordGap, M.easeOut(M.seg(o, 0, 0.15)))
        let leftEnd = (o > 0 ? rect.minX : gapX) - half
        let rightStart = (o > 0 ? rect.maxX : gapX) + half

        if o > 0 {
            let drawn = CGRect(x: rect.midX - max(rect.width, M.caretW) / 2, y: rect.minY,
                               width: max(rect.width, M.caretW), height: rect.height)
            let radius = min(drawn.width, drawn.height) / 2 * CGFloat(1 - full)
            let shape = Path(roundedRect: drawn, cornerRadius: radius, style: .continuous)
            let lift = 1 - full
            if lift > 0.01 {
                var amb = c
                amb.addFilter(.blur(radius: 11))
                amb.fill(shape.offsetBy(dx: 0, dy: 12), with: .color(A.color(0x3A1A10, 0.20 * lift)))
                var contact = c
                contact.addFilter(.blur(radius: 1.5))
                contact.fill(shape.offsetBy(dx: 0, dy: 2), with: .color(A.color(0x3A1A10, 0.18 * lift)))
            }
            var s = c
            if full < 1 { s.clip(to: shape) }
            A.scene(&s, w: w, h: h, cy: cy, open: o, full: full, t: f.t)
            if lift > 0.01 {
                // The window is recessed: a soft inner shadow along its rim.
                var rim = s
                rim.addFilter(.blur(radius: 4))
                rim.stroke(shape, with: .color(A.color(0x2A1208, 0.22 * lift)), lineWidth: 6)
            }
            if lift > 0.01 {
                c.stroke(Path(roundedRect: drawn.insetBy(dx: 0.5, dy: 0.5), cornerRadius: max(0, radius - 0.5), style: .continuous),
                         with: .linearGradient(Gradient(colors: [A.color(0xFFFFFF, 0.24 * lift), A.color(0xFFFFFF, 0)]),
                                               startPoint: CGPoint(x: 0, y: drawn.minY), endPoint: CGPoint(x: 0, y: drawn.minY + 20)),
                         lineWidth: 1)
            }
        }

        put(&c, left, x: leftEnd - wl, baseline: baseline)
        put(&c, right, x: rightStart, baseline: baseline)

        if f.caret > 0.001 {
            c.fill(Path(roundedRect: CGRect(x: gapX - M.caretW / 2, y: cy - M.caretH / 2, width: M.caretW, height: M.caretH),
                        cornerRadius: M.caretW / 2), with: .color(A.color(M.cream, f.caret)))
        }

        // Second line over the scene.
        let made = c.resolve(text(M.line2[0], M.regular, M.fontSize, tracking: M.tracking, M.cream))
        let yours = c.resolve(text(M.line2[1], M.regular, M.fontSize, tracking: M.tracking, M.cream))
        let line2 = c.resolve(text(M.line2.joined(separator: " "), M.regular, M.fontSize, tracking: M.tracking, M.cream))
        let l2Start = (w - width(line2)) / 2
        let l2Space = max(6, width(line2) - width(made) - width(yours))
        let xs = [l2Start, l2Start + width(made) + l2Space]
        for (i, r) in [made, yours].enumerated() {
            let a = f.wordIn[i] * (1 - f.wordOut[i])
            if a < 0.001 { continue }
            let dy = 18 * CGFloat(1 - f.wordIn[i]) + 10 * CGFloat(f.wordOut[i])
            var shadow = c
            shadow.opacity = a * 0.55
            shadow.addFilter(.blur(radius: 10))
            put(&shadow, c.resolve(text(M.line2[i], M.regular, M.fontSize, tracking: M.tracking, 0x2A1410)), x: xs[i], baseline: baseline + dy + 2)
            var t = c
            t.opacity = a
            put(&t, r, x: xs[i], baseline: baseline + dy)
        }

        chrome(&c, w: w, h: h, top: top, bottom: bottom, f: f)
    }

    /// Brand mark, Skip, page dots and the call to action.
    static func chrome(_ c: inout GraphicsContext, w: CGFloat, h: CGFloat, top: CGFloat, bottom: CGFloat, f: InlinePillRevealFrame) {
        let markY = top + 24
        // Sun mark: a half sun on a horizon line.
        var sun = Path()
        sun.addArc(center: CGPoint(x: 32, y: markY + 3), radius: 7, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false)
        sun.closeSubpath()
        c.fill(sun, with: .color(A.color(M.cream)))
        c.fill(Path(roundedRect: CGRect(x: 22, y: markY + 5, width: 20, height: 1.8), cornerRadius: 0.9), with: .color(A.color(M.cream)))
        let brand = c.resolve(text(M.brand, M.medium, 18, tracking: 0.2, M.cream))
        put(&c, brand, x: 48, baseline: markY + 6)
        let skip = c.resolve(text(M.skip, M.regular, 15, tracking: 0, M.cream, 0.8))
        put(&c, skip, x: w - 24 - width(skip), baseline: markY + 5)

        // Page dots.
        let dotY = h - bottom - 26
        let act = [1 - f.dots, f.dots, 0]
        let widths = act.map { 6 + 14 * CGFloat($0) }
        let total = widths.reduce(0, +) + 16
        var x = (w - total) / 2
        for i in 0 ..< 3 {
            c.fill(Path(roundedRect: CGRect(x: x, y: dotY - 3, width: widths[i], height: 6), cornerRadius: 3),
                   with: .color(A.color(M.cream, 0.45 + 0.55 * act[i])))
            x += widths[i] + 8
        }

        // Call to action.
        let a = f.ctaIn * (1 - f.ctaOut)
        if a > 0.001 {
            let dy = 16 * CGFloat(1 - f.ctaIn) + 10 * CGFloat(f.ctaOut)
            let rect = CGRect(x: 24, y: h - bottom - 84 - 27 + dy, width: w - 48, height: 54)
            let shape = Path(roundedRect: rect, cornerRadius: 27, style: .continuous)
            var k = c
            k.opacity = a
            var shadow = k
            shadow.addFilter(.blur(radius: 12))
            shadow.fill(shape.offsetBy(dx: 0, dy: 6), with: .color(A.color(0x140805, 0.25)))
            k.fill(shape, with: .color(A.color(M.cream)))
            let label = k.resolve(text(M.cta, M.medium, 17, tracking: 0, M.ink))
            let lw = width(label)
            let gx = rect.midX - (lw + 10 + 14) / 2
            put(&k, label, x: gx, baseline: rect.midY + 6)
            let ax = gx + lw + 10, ay = rect.midY
            var arrow = Path()
            arrow.move(to: CGPoint(x: ax, y: ay))
            arrow.addLine(to: CGPoint(x: ax + 14, y: ay))
            arrow.move(to: CGPoint(x: ax + 9, y: ay - 5))
            arrow.addLine(to: CGPoint(x: ax + 14, y: ay))
            arrow.addLine(to: CGPoint(x: ax + 9, y: ay + 5))
            k.stroke(arrow, with: .color(A.color(M.ink)), style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
        }
    }
}
