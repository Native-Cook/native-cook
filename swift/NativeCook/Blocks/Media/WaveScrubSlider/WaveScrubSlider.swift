import SwiftUI
import UIKit

/*
 Wave Scrub Slider — a photo editor's contrast control drawn as a living wave.

 Look: a dusk lake (drawn in code) fills the top 60 % of the screen; a near-black panel
 (#0E0E0E, top corners 24) slides over its foot with an orange toolbar (back, undo, redo, more,
 SHARE), a strip of seven tools with Contrast selected, and a card (#161616, corners 18) holding
 a perforated grille, the label CONTRAST, the value, and the slider. The slider is one line:
 left of the thumb an orange (#F39A1C) wave with a soft glow, right of it a grey (#4A4A4A)
 ripple. The waves tighten towards the right (2 to 15 cycles per track width) and tighten more
 as the value rises; the orange part's height grows with the value up to 15 pt (it rises from
 the line over the first 30 % of the track), the grey ripple is 1.2 + 5 × value pt; both meet
 the line at the thumb (a 14 × 42 pill). The crests always travel right at 1 cycle a second, so
 the line wiggles even when the thumb is still; at 0 only the faint grey ripple is left.
 The photo is graded live: contrast 1 → 1.45, saturation 1 → 1.3.

 autoPlay timeline (loop = 8 s; the scripted finger, eased in and out between keys):
   0.0 – 0.6   rest at 0: a flat line and the grey ripple.
   0.6 – 2.2   up to +62.
   2.2 – 2.9   hold (the wave keeps travelling).
   2.9 – 3.9   up to +100.
   3.9 – 5.0   hold.
   5.0 – 6.6   back to 0.
   6.6 – 8.0   rest. The drift is a whole number of cycles per loop, so 8.0 is the frame at 0.0.

 Touch: press anywhere on the card and drag; the thumb glides to the finger (0.18 s) and then
 follows it, grows 12 % and gets an orange halo; a selection haptic at each wave cycle passed.
 2 s after release the value eases back to 0 (0.8 s) and the loop starts again (on a whole
 second of the drift, so the wave never jumps).
 Reduced motion: no loop and no drift; the screen shows +60 still, and dragging still works.
 */

struct WaveScrubSlider: View {
    var autoPlay = false

    static let loop = WaveScrubSliderModel.loop
    typealias M = WaveScrubSliderModel
    typealias A = WaveScrubSliderArt

    static let orange = A.rgb(0xF39A1C)
    static let reducedValue = 0.6

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// The drift's t = 0; the loop always starts a whole number of seconds after it.
    @State private var origin = Date.now
    @State private var autoStart: Date? = nil
    @State private var manual = 0.0
    @State private var glide: (date: Date, from: Double)? = nil
    @State private var returning: (date: Date, from: Double)? = nil
    @State private var dragging = false
    @State private var touched = false
    @State private var lastTick = 0
    @State private var resume: Task<Void, Never>? = nil
    @State private var haptic = UISelectionFeedbackGenerator()

    struct Layout {
        var w: Double, h: Double
        var rowTop: Double, cardTop: Double, cardX: Double, cardW: Double, stripTop: Double, row1: Double
        var panelTop: Double, photoH: Double
        static let cardH = 128.0
        var trackX0: Double { cardX + 22 }
        var trackW: Double { cardW - 44 }
        var waveY: Double { cardTop + 82 }

        init(size: CGSize, insets: EdgeInsets) {
            w = size.width; h = size.height
            rowTop = h - max(insets.bottom, 16) - 48
            let cardBottom = rowTop - 20
            cardTop = cardBottom - Self.cardH
            cardX = 16; cardW = w - 32
            stripTop = cardTop - 20 - 52
            row1 = stripTop - 12 - 16
            panelTop = row1 - 30
            photoH = panelTop + 24
        }
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let l = Layout(size: size, insets: insets)
            ZStack(alignment: .topLeading) {
                Self.ink.frame(width: size.width, height: size.height)
                TimelineView(.animation(paused: reduceMotion && !dragging && glide == nil)) { timeline in
                    let v = value(at: timeline.date)
                    let phase = reduceMotion ? 0 : timeline.date.timeIntervalSince(origin) * M.drift
                    ZStack(alignment: .topLeading) {
                        photo(l, value: v)
                        panel(l)
                        grille(l)
                        wave(l, value: v, phase: phase)
                        header(l, value: v)
                        thumb(l, value: v)
                    }
                }
                touchArea(l)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear {
            haptic.prepare()
            origin = .now
            manual = reduceMotion ? Self.reducedValue : 0
            if autoPlay && !reduceMotion { autoStart = origin }
        }
        .onDisappear { resume?.cancel() }
    }

    static let ink = A.rgb(0x0E0E0E)

    // MARK: Time

    private func value(at date: Date) -> Double {
        if let autoStart, date >= autoStart, !reduceMotion {
            let t = date.timeIntervalSince(autoStart).truncatingRemainder(dividingBy: Self.loop)
            return M.scripted(t)
        }
        if let r = returning {
            return r.from * (1 - M.easeInOut(date.timeIntervalSince(r.date) / 0.8))
        }
        if let g = glide {
            let u = date.timeIntervalSince(g.date) / 0.18
            if u < 1 { return manual + (g.from - manual) * pow(1 - u, 3) }
        }
        return manual
    }

    private func touchArea(_ l: Layout) -> some View {
        Color.clear
            .contentShape(Rectangle())
            .frame(width: l.cardW, height: Layout.cardH)
            .offset(x: l.cardX, y: l.cardTop)
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { g in
                        let now = Date.now
                        let target = min(max((g.location.x - 22) / l.trackW, 0), 1)
                        if !touched {
                            touched = true
                            resume?.cancel()
                            let current = value(at: now)
                            autoStart = nil
                            returning = nil
                            dragging = true
                            lastTick = Int(floor(M.cycles(current, value: current)))
                            if abs(target - current) * l.trackW > 12 { glide = (now, current) } else { glide = nil }
                        }
                        manual = target
                        let tick = Int(floor(M.cycles(target, value: target)))
                        if tick != lastTick {
                            lastTick = tick
                            haptic.selectionChanged()
                        }
                    }
                    .onEnded { _ in
                        touched = false
                        dragging = false
                        scheduleResume()
                    }
            )
            .accessibilityElement()
            .accessibilityLabel("Contrast")
            .accessibilityValue(M.label(manual))
            .accessibilityAdjustableAction { direction in
                manual = min(max(manual + (direction == .increment ? 0.1 : -0.1), 0), 1)
            }
    }

    private func scheduleResume() {
        resume?.cancel()
        guard autoPlay, !reduceMotion else { return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            let now = Date.now
            returning = (now, manual)
            glide = nil
            // Restart on a whole second of the drift.
            autoStart = origin.addingTimeInterval(ceil(now.timeIntervalSince(origin) + 0.8))
            manual = 0
        }
    }

    // MARK: Drawing

    private func photo(_ l: Layout, value v: Double) -> some View {
        let m = M.grade(v)
        return Canvas { ctx, _ in
            var g = ctx
            var cm = ColorMatrix()
            cm.r1 = Float(m[0]); cm.r2 = Float(m[1]); cm.r3 = Float(m[2]); cm.r5 = Float(m[4])
            cm.g1 = Float(m[5]); cm.g2 = Float(m[6]); cm.g3 = Float(m[7]); cm.g5 = Float(m[9])
            cm.b1 = Float(m[10]); cm.b2 = Float(m[11]); cm.b3 = Float(m[12]); cm.b5 = Float(m[14])
            g.addFilter(.colorMatrix(cm))
            g.drawLayer { lc in A.photo(&lc, w: l.w, h: l.photoH) }
            // The status bar reads on a light shade at the top.
            ctx.fill(Path(CGRect(x: 0, y: 0, width: l.w, height: 120)),
                     with: .linearGradient(Gradient(colors: [.black.opacity(0.4), .black.opacity(0)]),
                                           startPoint: .zero, endPoint: CGPoint(x: 0, y: 120)))
        }
        .frame(width: l.w, height: l.photoH)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// The panel's still parts: its sheet, the toolbar, the tool strip, the card and the bottom bar.
    private func panel(_ l: Layout) -> some View {
        Canvas { ctx, _ in
            let sheet = Path(roundedRect: CGRect(x: 0, y: l.panelTop, width: l.w, height: l.h - l.panelTop + 40),
                             cornerRadius: 24, style: .continuous)
            ctx.fill(sheet, with: .color(Self.ink))
            // Grabber.
            ctx.fill(Path(roundedRect: CGRect(x: l.w / 2 - 18, y: l.panelTop + 8, width: 36, height: 4), cornerRadius: 2),
                     with: .color(.white.opacity(0.14)))
            // Toolbar.
            A.draw(.back, in: &ctx, at: CGPoint(x: 28, y: l.row1), color: Self.orange)
            A.draw(.undo, in: &ctx, at: CGPoint(x: 70, y: l.row1), color: Self.orange)
            A.draw(.redo, in: &ctx, at: CGPoint(x: 108, y: l.row1), color: Self.orange.opacity(0.35))
            A.draw(.more, in: &ctx, at: CGPoint(x: l.w - 104, y: l.row1), color: Self.orange)
            // Tool strip.
            let tools: [A.Icon] = [.auto, .light, .contrast, .color, .vignette, .grain, .sharpen]
            let cell = (l.w - 32) / Double(tools.count)
            for (i, t) in tools.enumerated() {
                let c = CGPoint(x: 16 + cell * (Double(i) + 0.5), y: l.stripTop + 22)
                if i == 2 {
                    ctx.fill(Path(roundedRect: CGRect(x: c.x - 22, y: c.y - 22, width: 44, height: 44), cornerRadius: 12, style: .continuous),
                             with: .color(A.rgb(0x242424)))
                }
                A.draw(t, in: &ctx, at: c, color: i == 2 ? .white : .white.opacity(0.38), size: 22)
                if i == 1 {
                    ctx.fill(Path(ellipseIn: CGRect(x: c.x - 2, y: l.stripTop + 46, width: 4, height: 4)), with: .color(Self.orange))
                }
            }
            // Card.
            ctx.fill(Path(roundedRect: CGRect(x: l.cardX, y: l.cardTop, width: l.cardW, height: Layout.cardH), cornerRadius: 18, style: .continuous),
                     with: .color(A.rgb(0x161616)))
            // Bottom bar.
            let bar: [A.Icon] = [.crop, .brush, .dial, .layers, .sky]
            let bc = (l.w - 32) / Double(bar.count)
            for (i, t) in bar.enumerated() {
                A.draw(t, in: &ctx, at: CGPoint(x: 16 + bc * (Double(i) + 0.5), y: l.rowTop + 22),
                       color: i == 2 ? .white : .white.opacity(0.45))
            }
            ctx.fill(Path(ellipseIn: CGRect(x: 16 + bc * 2.5 - 2, y: l.rowTop + 42, width: 4, height: 4)), with: .color(.white))
        }
        .frame(width: l.w, height: l.h)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// The card's perforated grille: dots every 8 pt, fading out at the card's edges.
    private func grille(_ l: Layout) -> some View {
        Canvas { ctx, size in
            var y = 6.0
            while y < size.height {
                var x = 6.0 + (Int(y / 8) % 2 == 0 ? 0 : 4)
                while x < size.width {
                    let edge = min(x, size.width - x, y, size.height - y)
                    let a = 0.1 * M.smoothstep(0, 28, edge)
                    ctx.fill(Path(ellipseIn: CGRect(x: x - 1.1, y: y - 1.1, width: 2.2, height: 2.2)), with: .color(.white.opacity(a)))
                    x += 8
                }
                y += 8
            }
        }
        .frame(width: l.cardW, height: Layout.cardH - 34)
        .offset(x: l.cardX, y: l.cardTop + 34)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func wave(_ l: Layout, value v: Double, phase: Double) -> some View {
        let thumb = v * l.trackW
        return Canvas { ctx, _ in
            var active = Path(), rest = Path()
            let step = 1.5
            var x = 0.0
            active.move(to: CGPoint(x: l.trackX0, y: l.waveY - M.height(0, value: v, phase: phase, width: l.trackW)))
            while x < thumb {
                x = min(x + step, thumb)
                active.addLine(to: CGPoint(x: l.trackX0 + x, y: l.waveY - M.height(x / l.trackW, value: v, phase: phase, width: l.trackW)))
            }
            rest.move(to: CGPoint(x: l.trackX0 + thumb, y: l.waveY - M.height(thumb / l.trackW, value: v, phase: phase, width: l.trackW)))
            while x < l.trackW {
                x = min(x + step, l.trackW)
                rest.addLine(to: CGPoint(x: l.trackX0 + x, y: l.waveY - M.height(x / l.trackW, value: v, phase: phase, width: l.trackW)))
            }
            let round = StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round)
            ctx.stroke(rest, with: .color(A.rgb(0x4A4A4A)), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
            if thumb > 0.5 {
                ctx.drawLayer { g in
                    g.addFilter(.blur(radius: 6))
                    g.stroke(active, with: .color(Self.orange.opacity(0.55)), style: StrokeStyle(lineWidth: 4, lineCap: .round))
                }
                ctx.stroke(active, with: .color(Self.orange), style: round)
            }
        }
        .frame(width: l.w, height: l.h)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func header(_ l: Layout, value v: Double) -> some View {
        ZStack(alignment: .topLeading) {
            Text("SHARE")
                .font(.system(size: 13, weight: .bold))
                .tracking(1.6)
                .foregroundStyle(Self.orange)
                .frame(width: 80, height: 32, alignment: .trailing)
                .offset(x: l.w - 20 - 80, y: l.row1 - 16)
            HStack {
                Text("CONTRAST")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(1.4)
                    .foregroundStyle(A.rgb(0x8C8C8C))
                Spacer()
                Text(M.label(v))
                    .font(.system(size: 13, weight: .medium, design: .monospaced))
                    .foregroundStyle(v > 0.004 ? Self.orange : .white.opacity(0.7))
            }
            .frame(width: l.cardW - 36, height: 20)
            .offset(x: l.cardX + 18, y: l.cardTop + 14)
            // The tool strip's dot under Contrast: on once the value leaves 0.
            Circle()
                .fill(Self.orange)
                .frame(width: 4, height: 4)
                .opacity(v > 0.004 ? 1 : 0)
                .offset(x: 16 + (l.w - 32) / 7 * 2.5 - 2, y: l.stripTop + 46)
        }
        .frame(width: l.w, height: l.h, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func thumb(_ l: Layout, value v: Double) -> some View {
        ZStack {
            Circle()
                .fill(RadialGradient(colors: [Self.orange.opacity(0.35), Self.orange.opacity(0)], center: .center, startRadius: 0, endRadius: 34))
                .frame(width: 68, height: 68)
                .opacity(dragging ? 1 : 0)
            RoundedRectangle(cornerRadius: 7, style: .continuous)
                .fill(A.rgb(0x1E1E1E))
                .overlay(RoundedRectangle(cornerRadius: 7, style: .continuous).strokeBorder(.white.opacity(0.28), lineWidth: 1))
                .frame(width: 14, height: 42)
                .shadow(color: .black.opacity(0.55), radius: 6, y: 2)
            Capsule().fill(Self.orange).frame(width: 3, height: 24)
        }
        .scaleEffect(dragging ? 1.12 : 1)
        .animation(.spring(response: 0.3, dampingFraction: 0.6), value: dragging)
        .frame(width: 68, height: 68)
        .offset(x: l.trackX0 + v * l.trackW - 34, y: l.waveY - 34)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
