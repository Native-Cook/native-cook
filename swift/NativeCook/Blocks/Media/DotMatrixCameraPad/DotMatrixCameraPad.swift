import SwiftUI
import UIKit

/*
 Dot Matrix Camera Pad — a manual camera screen whose controls are hardware-like pieces: a rubbery
 exposure wheel that turns into a glowing 11 × 8 dot-matrix pad (shutter across, ISO up), an A/M
 switch beside it, a warmth wheel, and a viewfinder showing a night street that answers every
 setting: exposure and ISO brighten it, slow shutters stretch the cars' lights into trails, high
 ISO adds grain, warmth tints it.

 Look: black screen; viewfinder (16 pt margins, 22 pt corners, white corner brackets) over the top
 62 %; lens chips 13 / 24 / 77 MM (amber #F4C02A when chosen) at its foot. Readouts (label 12 pt,
 mono value 15 pt) above two modules: a 48 × 86 drum wheel (8 pt ridges on a cylinder, an amber
 centre line) with a 26 × 58 A/M pill (orange #F04A12 knob), and the same for warmth. A glossy
 orange #EE4313 shutter in a white ring, a gallery thumbnail and a chevron button at the bottom.

 Pad: dots 1.3 pt, swelling to 3.9 pt and brightening near the hot spot (Gaussian, σ 16 pt), the
 hot spot's row and column lit as a crosshair, the chosen dot 3.2 pt warm white, a 46 pt orange
 glow and a faint finger ring while pressed. Wheel ⇄ pad: the capsule widens 48 → 120 pt and its
 corners 15 → 18 pt, the drum fades over the first half and the dots ripple in from the left over
 the second.

 autoPlay timeline (loop = 10 s), every step easeInOut (cubic) unless noted:
   0.6 – 1.8   exposure wheel turns +0.0 → +0.6 (the street brightens)
   2.3 – 2.55  A → M;  2.4 – 3.0 the wheel opens into the pad
   3.1 – 3.4   the finger lands on 1/60 · ISO 400 (glow in)
   3.4 – 4.6   slides to 1/8 · ISO 100 (long light trails), holds to 5.0
   5.0 – 6.4   arcs to 1/2000 · ISO 6400 (frozen cars, grain), 6.4 – 7.2 back to 1/60 · 400
   7.2 – 7.6   lifts (glow out);  7.5 – 7.75 M → A;  7.6 – 8.2 the pad closes into the wheel
   8.2 – 9.0   exposure back to +0.0
   9.2         the shutter: press 0.25 s, the viewfinder blinks (0.36 s), the thumbnail pops
   9.6 – 10    rest (same frame as 0)
 The cars cross a whole number of times per loop, and the grain is a hash of the frame number.

 Touch: drag a wheel up or down (8 pt per step: 0.1 stop or 100 K) with a tick haptic per step;
 tap an A/M switch to swap wheel and pad (0.25 s knob, 0.6 s morph); drag on the pad to place
 the hot spot (selection haptic per cell; glow in 0.15 s, out 0.4 s on release); tap a lens chip
 to zoom (0.45 s); tap the shutter to take a shot. 2 s after the last touch everything eases back
 to rest over 0.7 s and the loop starts again.
 Reduced motion: no loop; the street is still (no traffic, fixed grain); changes jump, and a shot
 only blinks.
 */

struct DotMatrixCameraPad: View {
    var autoPlay = false

    static let loop = DotMatrixCameraPadModel.loop
    typealias M = DotMatrixCameraPadModel
    typealias S = DotMatrixCameraPadState

    struct Tween {
        var from: Double, to: Double, at: Double, dur: Double
        init(_ v: Double) { from = v; to = v; at = 0; dur = 1 }
        init(from: Double, to: Double, at: Double, dur: Double) { self.from = from; self.to = to; self.at = at; self.dur = dur }
        func value(_ now: Double) -> Double { from + (to - from) * M.easeInOut((now - at) / dur) }
    }

    struct Manual {
        var ev = 0.0
        var warmth = Tween(5200)
        var knob = Tween(0), morph = Tween(0), knobR = Tween(0), glow = Tween(0), zoom = Tween(1)
        var px = M.restX, py = M.restY
        var captureAt: Double? = nil
    }

    enum Zone: Equatable { case none, wheelL, pad, toggleL, wheelR, toggleR, shutter, chip(Int) }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var appeared = 0.0
    @State private var autoStart: Double? = nil
    @State private var returning: (at: Double, from: S)? = nil
    @State private var manual = Manual()
    @State private var zone: Zone? = nil
    /// Keeps the clock running for a shot's blink under reduced motion.
    @State private var blinking = false
    @State private var dragStart = (ev: 0.0, warmth: 0.0)
    @State private var lastStep = 0
    @State private var resume: Task<Void, Never>? = nil
    @State private var tick = UISelectionFeedbackGenerator()
    @State private var thump = UIImpactFeedbackGenerator(style: .medium)

    private static func now() -> Double { Date.timeIntervalSinceReferenceDate }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let layout = M.Layout(w: size.width, h: size.height, top: insets.top, bottom: insets.bottom)
            TimelineView(.animation(paused: reduceMotion && zone == nil && !blinking)) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let s = shown(now)
                let clock = reduceMotion ? 0 : now - appeared
                ZStack(alignment: .topLeading) {
                    Canvas { ctx, _ in
                        DotMatrixCameraPadDraw.screen(&ctx, layout: layout, s: s, clock: clock)
                    }
                    .frame(width: size.width, height: size.height)
                    .contentShape(Rectangle())
                    .gesture(drag(layout: layout))
                    .accessibilityElement()
                    .accessibilityLabel(accessibility(s))
                    labels(layout: layout, s: s)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .ignoresSafeArea()
        }
        .background(Color.black.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            appeared = Self.now()
            tick.prepare()
            if autoPlay && !reduceMotion { autoStart = appeared }
        }
        .onDisappear { resume?.cancel() }
    }

    private func accessibility(_ s: S) -> String {
        let shutter = M.shutterLabels[Int((s.px * 10).rounded())]
        let iso = M.isoLabels[Int((s.py * 7).rounded())]
        return "Camera. Exposure \(M.evLabel(s.ev)), shutter \(shutter), ISO \(iso), warmth \(Int(s.warmth)) kelvin"
    }

    // MARK: State

    private func shown(_ now: Double) -> S {
        if let autoStart, now >= autoStart, !reduceMotion { return M.state(at: now - autoStart) }
        if let r = returning { return S.mix(r.from, S(), M.easeInOut((now - r.at) / 0.7)) }
        let m = manual
        var s = S(ev: m.ev, warmth: m.warmth.value(now), knob: m.knob.value(now), morph: m.morph.value(now),
                  knobR: m.knobR.value(now), px: m.px, py: m.py, glow: m.glow.value(now), zoom: m.zoom.value(now))
        if let c = m.captureAt {
            let d = now - c
            if !reduceMotion {
                if d >= 0 && d < 0.25 { s.press = sin(Double.pi * d / 0.25) }
                if d >= 0.05 && d < 0.5 { s.pop = 0.82 + 0.18 * M.easeOutBack((d - 0.05) / 0.4) }
            }
            if d >= 0.04 && d < 0.4 { s.flash = 0.85 * pow(1 - (d - 0.04) / 0.36, 2) }
        }
        return s
    }

    private func takeOver(_ now: Double) {
        resume?.cancel()
        let s = shown(now)
        var m = Manual()
        m.ev = s.ev
        m.warmth = Tween(s.warmth)
        m.knob = Tween(s.knob)
        m.morph = Tween(s.morph)
        m.knobR = Tween(s.knobR)
        m.glow = Tween(s.glow)
        m.zoom = Tween(s.zoom)
        m.px = s.px
        m.py = s.py
        m.captureAt = manual.captureAt
        manual = m
        autoStart = nil
        returning = nil
    }

    private func scheduleResume() {
        resume?.cancel()
        guard autoPlay, !reduceMotion else { return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            let now = Self.now()
            returning = (now, shown(now))
            autoStart = now + 0.7
        }
    }

    private func tween(from: Double, to: Double, at now: Double, dur: Double) -> Tween {
        Tween(from: from, to: to, at: now, dur: reduceMotion ? 0.001 : dur)
    }

    // MARK: Touch

    private func zone(at p: CGPoint, layout: M.Layout, s: S) -> Zone {
        func hit(_ r: CGRect, _ pad: Double = 8) -> Bool { r.insetBy(dx: -pad, dy: -pad).contains(p) }
        if hit(layout.leftToggle(s.morph), 6) { return .toggleL }
        if hit(layout.rightToggle, 6) { return .toggleR }
        if hit(layout.leftCapsule(s.morph)) { return s.morph >= 0.5 ? .pad : .wheelL }
        if hit(layout.rightCapsule) { return .wheelR }
        let sc = layout.shutterCenter
        if hit(CGRect(x: sc.x - 42, y: sc.y - 32, width: 84, height: 64)) { return .shutter }
        for i in 0..<3 where hit(CGRect(x: layout.chipX(i) - 24, y: layout.chipsY - 18, width: 48, height: 36), 0) { return .chip(i) }
        return .none
    }

    private func padPoint(_ p: CGPoint, layout: M.Layout) -> (Double, Double) {
        let r = layout.leftCapsule(1).insetBy(dx: 10, dy: 10)
        return (M.clamp01((p.x - r.minX) / r.width), M.clamp01((r.maxY - p.y) / r.height))
    }

    private func drag(layout: M.Layout) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Self.now()
                if zone == nil {
                    takeOver(now)
                    let z = zone(at: value.startLocation, layout: layout, s: shown(now))
                    zone = z
                    dragStart = (manual.ev, manual.warmth.value(now))
                    switch z {
                    case .wheelL: lastStep = Int((manual.ev * 10).rounded())
                    case .wheelR: lastStep = Int((dragStart.warmth / 100).rounded())
                    case .pad:
                        manual.glow = tween(from: manual.glow.value(now), to: 1, at: now, dur: 0.15)
                        lastStep = -1
                    default: break
                    }
                }
                let dy = value.translation.height
                switch zone {
                case .wheelL:
                    manual.ev = min(2, max(-2, dragStart.ev - dy / 80))
                    step(Int((manual.ev * 10).rounded()))
                case .wheelR:
                    let w = min(9000, max(2500, dragStart.warmth - dy / 8 * 100))
                    manual.warmth = Tween(w)
                    if manual.knobR.to < 1 { manual.knobR = tween(from: manual.knobR.value(now), to: 1, at: now, dur: 0.25) }
                    step(Int((w / 100).rounded()))
                case .pad:
                    let (x, y) = padPoint(value.location, layout: layout)
                    manual.px = x
                    manual.py = y
                    step(Int((x * 10).rounded()) * 10 + Int((y * 7).rounded()))
                default: break
                }
            }
            .onEnded { _ in
                let now = Self.now()
                let z = zone
                zone = nil
                switch z {
                case .wheelL: manual.ev = (manual.ev * 10).rounded() / 10
                case .wheelR: manual.warmth = Tween((manual.warmth.to / 100).rounded() * 100)
                case .pad: manual.glow = tween(from: manual.glow.value(now), to: 0, at: now, dur: 0.4)
                case .toggleL:
                    let to: Double = manual.knob.to < 0.5 ? 1 : 0
                    manual.knob = tween(from: manual.knob.value(now), to: to, at: now, dur: 0.25)
                    manual.morph = tween(from: manual.morph.value(now), to: to, at: now + (reduceMotion ? 0 : 0.1), dur: 0.6)
                    thump.impactOccurred(intensity: 0.7)
                case .toggleR:
                    let to: Double = manual.knobR.to < 0.5 ? 1 : 0
                    manual.knobR = tween(from: manual.knobR.value(now), to: to, at: now, dur: 0.25)
                    if to == 0 { manual.warmth = tween(from: manual.warmth.value(now), to: 5200, at: now, dur: 0.5) }
                    thump.impactOccurred(intensity: 0.7)
                case .shutter:
                    manual.captureAt = now
                    thump.impactOccurred(intensity: 1)
                    blinking = true
                    Task { @MainActor in
                        try? await Task.sleep(for: .seconds(0.6))
                        blinking = false
                    }
                case .chip(let i):
                    manual.zoom = tween(from: manual.zoom.value(now), to: M.lenses[i].zoom, at: now, dur: 0.45)
                    tick.selectionChanged()
                default: break
                }
                scheduleResume()
            }
    }

    private func step(_ v: Int) {
        if v != lastStep {
            lastStep = v
            tick.selectionChanged()
        }
    }

    // MARK: Text

    private func readout(_ label: String, _ value: String, amber: Bool) -> some View {
        VStack(spacing: 4) {
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(.white.opacity(0.85))
            Text(value)
                .font(.system(size: 15, weight: .semibold, design: .monospaced))
                .foregroundStyle(amber ? Color(red: M.amber.r, green: M.amber.g, blue: M.amber.b) : .white)
        }
        .lineLimit(1)
        .fixedSize()
    }

    @ViewBuilder
    private func labels(layout: M.Layout, s: S) -> some View {
        let cap = layout.leftCapsule(s.morph)
        let y = layout.readoutY + 18
        let wheelA = 1 - M.smoothstep(0, 0.5, s.morph)
        let padA = M.smoothstep(0.5, 1, s.morph)
        let col = Int((s.px * 10).rounded()), row = Int((s.py * 7).rounded())
        Group {
            readout("Exposure", M.evLabel(s.ev), amber: false)
                .opacity(wheelA)
                .position(x: cap.midX, y: y)
            readout("Shutter", M.shutterLabels[min(10, max(0, col))], amber: true)
                .opacity(padA)
                .position(x: cap.minX + 30, y: y)
            readout("ISO", M.isoLabels[min(7, max(0, row))], amber: true)
                .opacity(padA)
                .position(x: cap.minX + 92, y: y)
            readout("Warmth", "\(Int(s.warmth.rounded()))K", amber: s.knobR > 0.5)
                .position(x: layout.rightCapsule.midX, y: y)
            // Top bar.
            HStack(spacing: 20) {
                DotMatrixCameraPadIcon.bolt.frame(width: 14, height: 20)
                DotMatrixCameraPadIcon.grid.frame(width: 18, height: 18)
                Spacer()
                Text("HD").font(.system(size: 13, weight: .semibold, design: .monospaced))
                Text("30 FPS").font(.system(size: 13, weight: .semibold, design: .monospaced))
            }
            .foregroundStyle(.white.opacity(0.9))
            .padding(.horizontal, 24)
            .frame(width: layout.w, height: 28)
            .position(x: layout.w / 2, y: layout.top + 22)
            // Lens chips.
            ForEach(0..<3, id: \.self) { i in
                let on = abs(s.zoom - M.lenses[i].zoom) < 0.05
                let color = on ? Color(red: 0.957, green: 0.753, blue: 0.165) : .white
                VStack(spacing: 0) {
                    Text(M.lenses[i].label).font(.system(size: 14, weight: .semibold, design: .monospaced))
                    Text("MM").font(.system(size: 8, weight: .semibold, design: .monospaced))
                }
                .foregroundStyle(color)
                .position(x: layout.chipX(i), y: layout.chipsY)
            }
            DotMatrixCameraPadIcon.person
                .frame(width: 16, height: 18)
                .foregroundStyle(.white)
                .position(x: layout.chipX(3), y: layout.chipsY)
        }
        .allowsHitTesting(false)
    }
}

/// Small line icons drawn as paths, so every stack draws the same ones.
enum DotMatrixCameraPadIcon {
    static var bolt: some View {
        Canvas { ctx, sz in
            var p = Path()
            let w = sz.width, h = sz.height
            p.move(to: CGPoint(x: w * 0.62, y: 0))
            p.addLine(to: CGPoint(x: 0, y: h * 0.58))
            p.addLine(to: CGPoint(x: w * 0.48, y: h * 0.58))
            p.addLine(to: CGPoint(x: w * 0.38, y: h))
            p.addLine(to: CGPoint(x: w, y: h * 0.42))
            p.addLine(to: CGPoint(x: w * 0.52, y: h * 0.42))
            p.closeSubpath()
            ctx.stroke(p, with: .color(.white.opacity(0.9)), style: StrokeStyle(lineWidth: 1.6, lineJoin: .round))
        }
    }

    static var grid: some View {
        Canvas { ctx, sz in
            var p = Path()
            for k in 1...2 {
                let f = Double(k) / 3
                p.move(to: CGPoint(x: sz.width * f, y: 0)); p.addLine(to: CGPoint(x: sz.width * f, y: sz.height))
                p.move(to: CGPoint(x: 0, y: sz.height * f)); p.addLine(to: CGPoint(x: sz.width, y: sz.height * f))
            }
            ctx.stroke(p, with: .color(.white.opacity(0.9)), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
        }
    }

    static var person: some View {
        Canvas { ctx, sz in
            let w = sz.width, h = sz.height
            var p = Path(ellipseIn: CGRect(x: w * 0.28, y: 0, width: w * 0.44, height: w * 0.44))
            p.move(to: CGPoint(x: 0, y: h))
            p.addCurve(to: CGPoint(x: w, y: h), control1: CGPoint(x: 0, y: h * 0.45), control2: CGPoint(x: w, y: h * 0.45))
            ctx.stroke(p, with: .color(.white), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
        }
    }
}

// MARK: Drawing

enum DotMatrixCameraPadDraw {
    typealias M = DotMatrixCameraPadModel
    typealias S = DotMatrixCameraPadState

    static func rgb(_ r: Double, _ g: Double, _ b: Double, _ a: Double = 1) -> Color { Color(red: r, green: g, blue: b, opacity: a) }

    static func screen(_ ctx: inout GraphicsContext, layout l: M.Layout, s: S, clock: Double) {
        ctx.fill(Path(CGRect(x: 0, y: 0, width: l.w, height: l.h)), with: .color(.black))
        // Viewfinder.
        let vf = l.viewfinder
        var v = ctx
        v.clip(to: Path(roundedRect: vf, cornerRadius: 22, style: .continuous))
        street(&v, rect: vf, s: s, clock: clock)
        brackets(&ctx, vf)
        // Behind the lens chips.
        ctx.fill(Path(roundedRect: CGRect(x: l.w / 2 - 122, y: l.chipsY - 22, width: 244, height: 44), cornerRadius: 22),
                 with: .color(.black.opacity(0.32)))

        // Left module.
        let cap = l.leftCapsule(s.morph)
        let corner = 15 + 3 * M.clamp01(s.morph)
        let wheelA = 1 - M.smoothstep(0, 0.5, s.morph)
        capsule(&ctx, cap, corner: corner, glass: s.morph)
        if wheelA > 0.01 { drum(&ctx, cap, offset: s.ev * 10, amber: true, alpha: wheelA) }
        if s.morph > 0.5 {
            var pc = ctx
            pc.clip(to: Path(roundedRect: cap, cornerRadius: corner, style: .continuous))
            pad(&pc, cap, s: s)
        }
        toggle(&ctx, l.leftToggle(s.morph), knob: s.knob)
        // Right module.
        capsule(&ctx, l.rightCapsule, corner: 15, glass: 0)
        drum(&ctx, l.rightCapsule, offset: (s.warmth - 5200) / 100, amber: s.knobR > 0.5, alpha: 1)
        toggle(&ctx, l.rightToggle, knob: s.knobR)

        // Bottom row.
        shutter(&ctx, center: l.shutterCenter, press: s.press)
        var g = ctx
        let gr = l.gallery
        g.translateBy(x: gr.midX, y: gr.midY)
        g.scaleBy(x: s.pop, y: s.pop)
        g.translateBy(x: -gr.midX, y: -gr.midY)
        g.clip(to: Path(roundedRect: gr, cornerRadius: 10, style: .continuous))
        street(&g, rect: CGRect(x: gr.minX - 6, y: gr.minY - 10, width: 60, height: 72), s: S(), clock: 0)
        g.stroke(Path(roundedRect: gr, cornerRadius: 10, style: .continuous), with: .color(.white.opacity(0.18)), lineWidth: 1)
        let cr = l.chevron
        ctx.fill(Path(ellipseIn: cr), with: .color(rgb(0.11, 0.11, 0.12)))
        var chev = Path()
        chev.move(to: CGPoint(x: cr.midX - 7, y: cr.midY + 3))
        chev.addLine(to: CGPoint(x: cr.midX, y: cr.midY - 4))
        chev.addLine(to: CGPoint(x: cr.midX + 7, y: cr.midY + 3))
        ctx.stroke(chev, with: .color(.white), style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
    }

    static func brackets(_ ctx: inout GraphicsContext, _ r: CGRect) {
        let i = 12.0, a = 18.0
        var p = Path()
        for (x, y, sx, sy) in [(r.minX + i, r.minY + i, 1.0, 1.0), (r.maxX - i, r.minY + i, -1.0, 1.0),
                               (r.minX + i, r.maxY - i, 1.0, -1.0), (r.maxX - i, r.maxY - i, -1.0, -1.0)] {
            p.move(to: CGPoint(x: x, y: y + sy * a))
            p.addLine(to: CGPoint(x: x, y: y))
            p.addLine(to: CGPoint(x: x + sx * a, y: y))
        }
        ctx.stroke(p, with: .color(.white.opacity(0.85)), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
    }

    /// The module's body: a rubber capsule, turning into dark glass as it becomes the pad.
    static func capsule(_ ctx: inout GraphicsContext, _ r: CGRect, corner: Double, glass: Double) {
        let path = Path(roundedRect: r, cornerRadius: corner, style: .continuous)
        ctx.fill(path, with: .linearGradient(Gradient(stops: [.init(color: rgb(0.07, 0.07, 0.08), location: 0),
                                                              .init(color: rgb(0.17, 0.17, 0.18), location: 0.5),
                                                              .init(color: rgb(0.07, 0.07, 0.08), location: 1)]),
                                             startPoint: CGPoint(x: r.midX, y: r.minY), endPoint: CGPoint(x: r.midX, y: r.maxY)))
        if glass > 0.01 {
            ctx.fill(path, with: .linearGradient(Gradient(colors: [rgb(0.20, 0.17, 0.14, glass), rgb(0.06, 0.06, 0.07, glass)]),
                                                 startPoint: CGPoint(x: r.minX, y: r.minY), endPoint: CGPoint(x: r.maxX, y: r.maxY)))
        }
        ctx.stroke(path, with: .color(.white.opacity(0.08)), lineWidth: 1)
    }

    /// Ridges on a drum: 8 pt apart along the cylinder, foreshortened and fading towards its rim.
    static func drum(_ ctx: inout GraphicsContext, _ r: CGRect, offset: Double, amber: Bool, alpha: Double) {
        let radius = r.height / 2 * 1.05
        let base = Int(floor(offset))
        for k in (base - 9)...(base + 9) {
            let y = (Double(k) - offset) * 8
            let theta = y / radius
            if abs(theta) > 1.45 { continue }
            let c = cos(theta)
            let yy = r.midY + sin(theta) * radius * 0.92
            let major = k % 5 == 0
            let inset = major ? 8.0 : 12.0
            var p = Path()
            p.move(to: CGPoint(x: r.minX + inset, y: yy))
            p.addLine(to: CGPoint(x: r.maxX - inset, y: yy))
            ctx.stroke(p, with: .color(.white.opacity(alpha * (major ? 0.3 + 0.6 * c : 0.12 + 0.42 * c))),
                       style: StrokeStyle(lineWidth: (major ? 1.6 : 1.2) * (0.4 + 0.6 * c), lineCap: .round))
        }
        var mid = Path()
        mid.move(to: CGPoint(x: r.minX + 7, y: r.midY))
        mid.addLine(to: CGPoint(x: r.maxX - 7, y: r.midY))
        let color = amber ? rgb(M.amber.r, M.amber.g, M.amber.b, alpha) : Color.white.opacity(0.8 * alpha)
        ctx.stroke(mid, with: .color(color), style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
        // A sheen along the drum's top.
        ctx.fill(Path(roundedRect: r.insetBy(dx: 3, dy: 3), cornerRadius: 12),
                 with: .linearGradient(Gradient(colors: [.white.opacity(0.07 * alpha), .white.opacity(0)]),
                                       startPoint: CGPoint(x: r.midX, y: r.minY), endPoint: CGPoint(x: r.midX, y: r.midY)))
    }

    /// The dot matrix: 11 columns of shutter speeds by 8 rows of ISO.
    static func pad(_ ctx: inout GraphicsContext, _ r: CGRect, s: S) {
        let reveal = M.clamp01((s.morph - 0.5) * 2)
        let inner = r.insetBy(dx: 10, dy: 10)
        let sx = inner.width / Double(M.cols - 1), sy = inner.height / Double(M.rows - 1)
        let hx = inner.minX + s.px * inner.width, hy = inner.maxY - s.py * inner.height
        let ci = Int((s.px * 10).rounded()), ri = Int((s.py * 7).rounded())
        let glow = s.glow
        if glow > 0.01 {
            ctx.fill(Path(ellipseIn: CGRect(x: hx - 46, y: hy - 46, width: 92, height: 92)),
                     with: .radialGradient(Gradient(colors: [rgb(0.98, 0.45, 0.10, 0.45 * glow * reveal), rgb(0.98, 0.45, 0.10, 0)]),
                                           center: CGPoint(x: hx, y: hy), startRadius: 0, endRadius: 46))
        }
        for c in 0..<M.cols {
            // Columns ripple in from the left.
            let a = M.smoothstep(0, 1, reveal * 1.6 - Double(c) / Double(M.cols - 1) * 0.6)
            if a < 0.01 { continue }
            for rr in 0..<M.rows {
                let x = inner.minX + Double(c) * sx, y = inner.maxY - Double(rr) * sy
                let d2 = (x - hx) * (x - hx) + (y - hy) * (y - hy)
                let g = exp(-d2 / (2 * 16 * 16)) * glow
                let line = (c == ci || rr == ri) ? 0.16 + 0.3 * glow : 0
                var radius = 1.3 + 2.6 * g + (line > 0 ? 0.3 : 0)
                var color = rgb(0.96, 0.60, 0.18, a * min(1, 0.2 + line + 0.8 * g))
                if c == ci && rr == ri {
                    radius = 3.2 + 0.7 * glow
                    color = rgb(1, 0.86, 0.58, a * (0.75 + 0.25 * glow))
                }
                ctx.fill(Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: 2 * radius, height: 2 * radius)), with: .color(color))
            }
        }
        if glow > 0.01 {
            ctx.stroke(Path(ellipseIn: CGRect(x: hx - 15, y: hy - 15, width: 30, height: 30)),
                       with: .color(.white.opacity(0.28 * glow * reveal)), lineWidth: 1.5)
        }
    }

    static func toggle(_ ctx: inout GraphicsContext, _ r: CGRect, knob: Double) {
        ctx.fill(Path(roundedRect: r, cornerRadius: 13), with: .color(rgb(0.11, 0.11, 0.12)))
        let ky = r.minY + 15 + 28 * M.clamp01(knob)
        ctx.fill(Path(ellipseIn: CGRect(x: r.midX - 11, y: ky - 11, width: 22, height: 22)), with: .color(rgb(0.94, 0.29, 0.07)))
        let k = M.clamp01(knob)
        for (i, letter) in ["A", "M"].enumerated() {
            let on = i == 0 ? 1 - k : k
            let text = Text(letter).font(.system(size: 11, weight: .bold, design: .monospaced))
                .foregroundColor(.white.opacity(0.5 + 0.5 * on))
            ctx.draw(text, at: CGPoint(x: r.midX, y: r.minY + 15 + 28 * Double(i)), anchor: .center)
        }
    }

    static func shutter(_ ctx: inout GraphicsContext, center c: CGPoint, press: Double) {
        var g = ctx
        let k = 1 - 0.08 * press
        g.translateBy(x: c.x, y: c.y)
        g.scaleBy(x: k, y: k)
        let ring = CGRect(x: -42, y: -32, width: 84, height: 64)
        g.stroke(Path(roundedRect: ring.insetBy(dx: 1.5, dy: 1.5), cornerRadius: 23, style: .continuous), with: .color(.white), lineWidth: 3)
        let body = CGRect(x: -35, y: -25, width: 70, height: 50)
        let path = Path(roundedRect: body, cornerRadius: 17, style: .continuous)
        g.fill(path, with: .linearGradient(Gradient(colors: [rgb(1.0, 0.38, 0.14), rgb(0.88, 0.22, 0.05)]),
                                           startPoint: CGPoint(x: 0, y: body.minY), endPoint: CGPoint(x: 0, y: body.maxY)))
        g.fill(Path(roundedRect: CGRect(x: -27, y: -21, width: 54, height: 16), cornerRadius: 8),
               with: .linearGradient(Gradient(colors: [.white.opacity(0.28), .white.opacity(0)]),
                                     startPoint: CGPoint(x: 0, y: -21), endPoint: CGPoint(x: 0, y: -5)))
        g.fill(path, with: .color(.black.opacity(0.18 * press)))
    }

    // MARK: The night street

    static func street(_ ctx: inout GraphicsContext, rect r: CGRect, s: S, clock: Double) {
        var c = ctx
        c.translateBy(x: r.midX, y: r.midY)
        c.scaleBy(x: s.zoom, y: s.zoom)
        c.translateBy(x: -r.midX, y: -r.midY)
        let w = r.width, h = r.height
        func P(_ u: Double, _ v: Double) -> CGPoint { CGPoint(x: r.minX + u * w, y: r.minY + v * h) }
        func R(_ u: Double, _ v: Double, _ uw: Double, _ vh: Double) -> CGRect { CGRect(x: r.minX + u * w, y: r.minY + v * h, width: uw * w, height: vh * h) }
        let shutter = M.shutter(s.px)
        let iso = M.iso(s.py)

        // Sky, a glow over the city.
        c.fill(Path(R(-0.4, -0.4, 1.8, 1.8)), with: .linearGradient(
            Gradient(colors: [rgb(0.04, 0.05, 0.13), rgb(0.12, 0.08, 0.20), rgb(0.30, 0.14, 0.22)]),
            startPoint: P(0, 0), endPoint: P(0, M.horizon)))
        // Buildings with lit windows.
        for (b, bld) in M.buildings.enumerated() {
            let (x0, bw, bh) = bld
            let top = M.horizon - bh
            c.fill(Path(R(x0, top, bw, bh + 0.05)), with: .color(rgb(0.05, 0.05, 0.10)))
            var i = 0
            var wy = top + 0.03
            while wy < M.horizon - 0.03 {
                var wx = x0 + 0.018
                var j = 0
                while wx < x0 + bw - 0.025 {
                    let hsh = M.hash(b, i, j)
                    if hsh > 0.58 {
                        let cool = M.hash(j, b, i) > 0.82
                        c.fill(Path(R(wx, wy, 0.018, 0.022)),
                               with: .color(cool ? rgb(0.65, 0.82, 1.0, 0.75) : rgb(1.0, 0.76, 0.42, 0.55 + 0.4 * hsh)))
                    }
                    wx += 0.034
                    j += 1
                }
                wy += 0.046
                i += 1
            }
        }
        // Pavement and road.
        c.fill(Path(R(-0.4, M.horizon, 1.8, 0.8)), with: .color(rgb(0.06, 0.06, 0.08)))
        c.fill(Path(R(-0.4, M.horizon + 0.035, 1.8, 0.008)), with: .color(rgb(0.18, 0.16, 0.20)))
        var dash = -0.4
        while dash < 1.4 {
            c.fill(Path(R(dash, 0.806, 0.06, 0.006)), with: .color(rgb(0.55, 0.52, 0.45, 0.45)))
            dash += 0.12
        }
        // Street lamps: poles, bokeh, and their streaks on the wet road.
        for (lx, ly) in M.lamps {
            c.fill(Path(R(lx - 0.003, ly, 0.006, M.horizon + 0.04 - ly)), with: .color(rgb(0.12, 0.11, 0.15)))
            c.fill(Path(R(lx - 0.03, M.horizon + 0.05, 0.06, 0.3)), with: .linearGradient(
                Gradient(colors: [rgb(1, 0.70, 0.36, 0.22), rgb(1, 0.70, 0.36, 0)]),
                startPoint: P(0, M.horizon + 0.05), endPoint: P(0, M.horizon + 0.35)))
            let glowR = 0.075 * w
            c.fill(Path(ellipseIn: CGRect(x: P(lx, ly).x - glowR, y: P(lx, ly).y - glowR, width: 2 * glowR, height: 2 * glowR)),
                   with: .radialGradient(Gradient(colors: [rgb(1, 0.74, 0.40, 0.65), rgb(1, 0.6, 0.3, 0)]),
                                         center: P(lx, ly), startRadius: 0, endRadius: glowR))
            c.fill(Path(ellipseIn: CGRect(x: P(lx, ly).x - 3, y: P(lx, ly).y - 3, width: 6, height: 6)), with: .color(rgb(1, 0.95, 0.82)))
        }
        // Cars, their lights dragged into trails by slow shutters.
        let trail0 = shutter * 14
        for (lane, n, phase) in M.cars {
            let dir = lane == 0 ? 1.0 : -1.0
            var f = (n * clock / M.loop + phase).truncatingRemainder(dividingBy: 1)
            if f < 0 { f += 1 }
            let x = lane == 0 ? -0.2 + 1.4 * f : 1.2 - 1.4 * f
            let y = M.laneY[lane]
            // Each car's trail is its speed times the shutter time (exaggerated 14 ×).
            let trail = min(0.8, trail0 * n * 1.4 / M.loop)
            let ghost = 1 - 0.75 * M.smoothstep(0.02, 0.2, shutter)
            let bodyLen = 0.16
            let back = x - dir * bodyLen
            let left = min(x, back)
            c.fill(Path(roundedRect: R(left, y - 0.034, bodyLen, 0.036), cornerRadius: 5), with: .color(rgb(0.12, 0.12, 0.16, ghost)))
            c.fill(Path(roundedRect: R(left + (dir > 0 ? 0.035 : 0.045), y - 0.062, bodyLen - 0.08, 0.032), cornerRadius: 6),
                   with: .color(rgb(0.10, 0.10, 0.14, ghost)))
            c.fill(Path(roundedRect: R(left + (dir > 0 ? 0.042 : 0.052), y - 0.056, bodyLen - 0.094, 0.02), cornerRadius: 3),
                   with: .color(rgb(0.30, 0.30, 0.38, 0.6 * ghost)))
            // Headlight at the front (white), tail light at the back (red).
            for (lx, col, width) in [(x, (1.0, 0.95, 0.85), 3.4), (back, (1.0, 0.16, 0.10), 3.0)] {
                let start = P(lx, y - 0.012)
                let end = P(lx - dir * trail, y - 0.012)
                var p = Path()
                p.move(to: end)
                p.addLine(to: start)
                let shade = GraphicsContext.Shading.linearGradient(
                    Gradient(colors: [rgb(col.0, col.1, col.2, 0), rgb(col.0, col.1, col.2, 0.95)]), startPoint: end, endPoint: start)
                let glowShade = GraphicsContext.Shading.linearGradient(
                    Gradient(colors: [rgb(col.0, col.1, col.2, 0), rgb(col.0, col.1, col.2, 0.28)]), startPoint: end, endPoint: start)
                c.stroke(p, with: glowShade, style: StrokeStyle(lineWidth: width * 3, lineCap: .round))
                c.stroke(p, with: shade, style: StrokeStyle(lineWidth: width, lineCap: .round))
            }
        }
        // Exposure: darken below the resting brightness, lift above it.
        let e = min(3, max(-3, M.exposure(s)))
        let full = Path(R(-0.4, -0.4, 1.8, 1.8))
        if e < 0 {
            c.fill(full, with: .color(.black.opacity(1 - pow(2, e * 0.8))))
        } else if e > 0 {
            var add = c
            add.blendMode = .plusLighter
            let k = 1 - pow(2, -e * 0.8)
            add.fill(full, with: .color(rgb(0.42 * k, 0.36 * k, 0.40 * k)))
        }
        // Warmth.
        let t = min(1, max(-1, (s.warmth - 5200) / 2600))
        if t > 0 { c.fill(full, with: .color(rgb(1, 0.55, 0.2, 0.16 * t))) }
        if t < 0 { c.fill(full, with: .color(rgb(0.25, 0.5, 1, 0.16 * -t))) }
        // Grain, a hash of the frame number (24 a second).
        let count = Int(iso / 12800 * 700)
        let frame = Int((clock * 24).rounded(.down)) % 240
        for i in 0..<count {
            let gx = M.hash(frame, i, 7), gy = M.hash(i, frame, 3)
            let bright = M.hash(frame, 11, i) > 0.5
            c.fill(Path(R(gx, gy, 1.8 / w, 1.8 / h)), with: .color(bright ? .white.opacity(0.36) : .black.opacity(0.45)))
        }
        // Vignette and the shot's blink.
        ctx.fill(Path(r), with: .radialGradient(Gradient(colors: [.black.opacity(0), .black.opacity(0.45)]),
                                                center: CGPoint(x: r.midX, y: r.midY), startRadius: r.height * 0.3, endRadius: r.height * 0.8))
        if s.flash > 0.001 { ctx.fill(Path(r), with: .color(.black.opacity(s.flash))) }
    }
}
