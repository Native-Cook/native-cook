import SwiftUI
import UIKit

// Halo's neon glass tiles follow the React Native reference's geometry and 9 s clock.
// 0–1.2: small ring; 1.2–2.4: swell and tip; 2.4–4.2: orbit the phrases;
// 4.2–5.1: gather; 5.1–6.2: dots; 6.2–6.6: upward arrow; 6.6–7.8: hold;
// 7.8–9: reopen. A 4 pt pan freezes the phase and adds dx/120 radians.
// After a 2 s hold, resume the phase and decay extra spin as exp(-6t) to 0.002.
struct GlassIconOrbit: View {
    static let loop: Double = 9
    var brand = "halo"
    var action = "Get started"
    var autoPlay = false

    private typealias M = GlassIconOrbitMotion
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @GestureState private var fingerDown = false
    @State private var epoch = Date()
    @State private var accumulated = 0.0
    @State private var running = false
    @State private var loopOrigin = 0.0
    @State private var touching = false
    @State private var heldClock = 0.0
    @State private var extraSpin = 0.0
    @State private var previousX = 0.0
    @State private var released: Double?
    @State private var touches = 0
    @State private var haptic = UISelectionFeedbackGenerator()

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(width: geometry.size.width + safe.leading + safe.trailing,
                              height: geometry.size.height + safe.top + safe.bottom)
            ZStack(alignment: .topLeading) {
                Color.black
                TimelineView(.animation(paused: !running || (!autoPlay && !touching && released == nil))) { timeline in
                    let frame = frame(at: elapsed(at: timeline.date))
                    Canvas(colorMode: .nonLinear) { context, _ in
                        GlassIconOrbitArt.draw(context, size: size, clock: frame.clock, spin: frame.spin)
                    }
                    .accessibilityLabel("A ring of app icons. Drag to spin it.")
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .allowsHitTesting(false)

                // RN's footer sits above the pan surface; dragging it does not spin the ring.
                Color.clear
                    .frame(width: size.width, height: max(0, size.height - safe.bottom - 176))
                    .contentShape(Rectangle())
                    .gesture(pan, including: reduced ? .none : .all)
                GlassIconOrbitCopy(brand: brand, action: action, safe: safe)
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                    .allowsHitTesting(false)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .background(Color.black.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            epoch = .now
            running = scenePhase == .active && !reduced
            haptic.prepare()
        }
        .onDisappear { updateRunning(false) }
        .onChange(of: scenePhase) { _, phase in updateRunning(phase == .active && !reduced) }
        .onChange(of: reduced) { _, value in updateRunning(scenePhase == .active && !value) }
        .onChange(of: fingerDown) { _, down in
            // A cancelled gesture also releases, matching RN's onFinalize.
            if !down && touching {
                Task { @MainActor in
                    await Task.yield()
                    if !fingerDown && touching { finishPan() }
                }
            }
        }
        .task(id: touches) {
            guard !touching, let released else { return }
            let decayDuration = abs(extraSpin) > 0.002 ? log(abs(extraSpin) / 0.002) / 6 : 0
            while elapsed(at: .now) < released + 2 + decayDuration {
                do { try await Task.sleep(for: .milliseconds(50)) } catch { return }
            }
            guard !Task.isCancelled, !touching else { return }
            let now = elapsed(at: .now)
            loopOrigin = now - frame(at: now).clock
            self.released = nil
            extraSpin = 0
        }
    }

    private func updateRunning(_ value: Bool) {
        let now = Date()
        accumulated = elapsed(at: now)
        epoch = now
        running = value
    }
    private func elapsed(at date: Date) -> Double {
        accumulated + (running ? max(0, date.timeIntervalSince(epoch)) : 0)
    }
    private func frame(at now: Double) -> (clock: Double, spin: Double, extra: Double) {
        if reduced { return (7, 0, 0) }
        var clock = autoPlay ? M.clock(now - loopOrigin) : 0
        var extra = 0.0
        if touching {
            clock = heldClock
            extra = extraSpin
        } else if let released {
            let resume = max(0, now - released - 2)
            clock = autoPlay ? M.clock(heldClock + resume) : 0
            extra = extraSpin * exp(-6 * resume)
            if resume > 0 && abs(extra) < 0.002 { extra = 0 }
        }
        return (clock, 2 * .pi * clock / Self.loop + extra, extra)
    }
    private var pan: some Gesture {
        DragGesture(minimumDistance: 4)
            .updating($fingerDown) { _, down, _ in down = true }
            .onChanged { value in
                guard !reduced else { return }
                if !touching {
                    let current = frame(at: elapsed(at: .now))
                    heldClock = current.clock
                    extraSpin = current.extra
                    released = nil
                    previousX = 0
                    touching = true
                    touches += 1
                    haptic.selectionChanged()
                    haptic.prepare()
                }
                extraSpin += (value.translation.width - previousX) / 120
                previousX = value.translation.width
            }
            .onEnded { _ in finishPan() }
    }
    private func finishPan() {
        guard touching else { return }
        touching = false
        released = elapsed(at: .now)
        touches += 1
    }
}

private enum GlassIconOrbitMotion {
    struct Stage {
        var radius: Double; var tilt: Double; var ringAlpha: Double; var centre: Double; var arrow: Double
    }
    static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double { min(1, max(0, (t - a) / (b - a))) }
    static func clock(_ t: Double) -> Double {
        let p = GlassIconOrbit.loop
        return (t.truncatingRemainder(dividingBy: p) + p).truncatingRemainder(dividingBy: p)
    }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func back(_ x: Double) -> Double { 1 + 2.6 * pow(x - 1, 3) + 1.6 * pow(x - 1, 2) }
    static func stage(at t: Double) -> Stage {
        let swell = easeInOut(segment(t, 1.2, 2.4))
        let gather = pow(segment(t, 4.2, 5.1), 3)
        let reopen = easeOut(segment(t, 7.8, 9))
        return Stage(radius: gather < 1 ? 92 + 158 * swell * (1 - gather) - 92 * gather : 92 * reopen,
                     tilt: 68 * swell * (1 - gather), ringAlpha: gather < 1 ? 1 - gather : reopen,
                     centre: back(segment(t, 4.5, 5.1)) * (1 - easeInOut(segment(t, 7.8, 8.5))),
                     arrow: easeInOut(segment(t, 6.2, 6.6)))
    }
    static func color(_ hex: UInt32, alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
    }
}

private enum GlassIconOrbitArt {
    private typealias M = GlassIconOrbitMotion
    private struct Icon { var a: UInt32; var b: UInt32; var rimA: UInt32; var rimB: UInt32 }
    private static let icons: [Icon] = [
        .init(a: 0x3CF0B4, b: 0x2D7BFF, rimA: 0x8A5CFF, rimB: 0x3DA2FF),
        .init(a: 0xE9FFF6, b: 0x47C9FF, rimA: 0x6E7BFF, rimB: 0xC05CFF),
        .init(a: 0xFFE15A, b: 0xFF6AD5, rimA: 0x7A5CFF, rimB: 0x3DA2FF),
        .init(a: 0x4DF5C8, b: 0x2D9BFF, rimA: 0x3DA2FF, rimB: 0x8A5CFF),
        .init(a: 0xFF7BD8, b: 0x4D7BFF, rimA: 0xC05CFF, rimB: 0x3DA2FF),
        .init(a: 0xFFB03A, b: 0x4D6BFF, rimA: 0x8A5CFF, rimB: 0xFF8A3D),
        .init(a: 0xFF8AD0, b: 0x8A6BFF, rimA: 0xFF8A3D, rimB: 0x8A5CFF),
        .init(a: 0xC9B8FF, b: 0x6B4DFF, rimA: 0xFF8A3D, rimB: 0x6E7BFF),
    ]
    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }
    private static func path(_ draw: (inout Path) -> Void) -> Path { var p = Path(); draw(&p); return p }
    private struct Part { var path: Path; var stroke = false; var pupil = false }
    // Canonical 64 pt SVG geometry is cached once; per-frame work only transforms it.
    private static let parts: [[Part]] = [
        [Part(path: Path(roundedRect: CGRect(x: 12, y: 18, width: 40, height: 30), cornerRadius: 6), stroke: true),
         Part(path: path { p in
             p.move(to: .init(x: 14, y: 46)); p.addLine(to: .init(x: 27, y: 32))
             p.addLine(to: .init(x: 35, y: 40)); p.addLine(to: .init(x: 41, y: 34)); p.addLine(to: .init(x: 50, y: 46)); p.closeSubpath()
         }), Part(path: circle(40, 27, 4))],
        [Part(path: path { p in
            p.move(to: .init(x: 10, y: 32)); p.addCurve(to: .init(x: 54, y: 32), control1: .init(x: 19, y: 19), control2: .init(x: 45, y: 19))
            p.addCurve(to: .init(x: 10, y: 32), control1: .init(x: 45, y: 45), control2: .init(x: 19, y: 45)); p.closeSubpath()
        }), Part(path: circle(32, 32, 8), pupil: true)],
        [Part(path: path { p in
            p.move(to: .init(x: 27, y: 44)); p.addLine(to: .init(x: 27, y: 20))
            p.addLine(to: .init(x: 45, y: 16)); p.addLine(to: .init(x: 45, y: 40))
        }, stroke: true), Part(path: circle(22, 44, 6)), Part(path: circle(40, 40, 6))],
        [Part(path: circle(29, 29, 12), stroke: true), Part(path: path { p in
            p.move(to: .init(x: 38, y: 38)); p.addLine(to: .init(x: 48, y: 48))
        }, stroke: true)],
        [Part(path: path { p in
            p.move(to: .init(x: 32, y: 10))
            p.addCurve(to: .init(x: 54, y: 32), control1: .init(x: 34, y: 26), control2: .init(x: 38, y: 30))
            p.addCurve(to: .init(x: 32, y: 54), control1: .init(x: 38, y: 34), control2: .init(x: 34, y: 38))
            p.addCurve(to: .init(x: 10, y: 32), control1: .init(x: 30, y: 38), control2: .init(x: 26, y: 34))
            p.addCurve(to: .init(x: 32, y: 10), control1: .init(x: 26, y: 30), control2: .init(x: 30, y: 26)); p.closeSubpath()
        })],
        [Part(path: path { p in
            p.move(to: .init(x: 13, y: 38))
            p.addCurve(to: .init(x: 28, y: 34), control1: .init(x: 19, y: 20), control2: .init(x: 26, y: 21))
            p.addCurve(to: .init(x: 40, y: 32), control1: .init(x: 30, y: 47), control2: .init(x: 37, y: 46))
            p.addCurve(to: .init(x: 51, y: 31), control1: .init(x: 43, y: 23), control2: .init(x: 48, y: 25))
        }, stroke: true)],
        [Part(path: path { p in
            p.move(to: .init(x: 32, y: 49))
            p.addCurve(to: .init(x: 24, y: 19), control1: .init(x: 13, y: 36), control2: .init(x: 13, y: 19))
            p.addCurve(to: .init(x: 32, y: 26), control1: .init(x: 28, y: 19), control2: .init(x: 31, y: 22))
            p.addCurve(to: .init(x: 40, y: 19), control1: .init(x: 33, y: 22), control2: .init(x: 36, y: 19))
            p.addCurve(to: .init(x: 32, y: 49), control1: .init(x: 51, y: 19), control2: .init(x: 51, y: 36)); p.closeSubpath()
        })],
        [Part(path: path { p in
            p.move(to: .init(x: 14, y: 22))
            p.addRelativeArc(center: .init(x: 20, y: 22), radius: 6, startAngle: .degrees(180), delta: .degrees(90))
            p.addLine(to: .init(x: 44, y: 16))
            p.addRelativeArc(center: .init(x: 44, y: 22), radius: 6, startAngle: .degrees(270), delta: .degrees(90))
            p.addLine(to: .init(x: 50, y: 36))
            p.addRelativeArc(center: .init(x: 44, y: 36), radius: 6, startAngle: .degrees(0), delta: .degrees(90))
            p.addLine(to: .init(x: 30, y: 42)); p.addLine(to: .init(x: 22, y: 49))
            p.addLine(to: .init(x: 22, y: 42)); p.addLine(to: .init(x: 20, y: 42))
            p.addRelativeArc(center: .init(x: 20, y: 36), radius: 6, startAngle: .degrees(90), delta: .degrees(90)); p.closeSubpath()
        })],
    ]

    static func draw(_ context: GraphicsContext, size: CGSize, clock t: Double, spin: Double) {
        let st = M.stage(at: t), cx = size.width / 2, cy = size.height * 0.45
        let radius = 150 + st.radius * 0.6
        context.fill(circle(cx, cy, radius), with: .radialGradient(Gradient(stops: [
            .init(color: M.color(0x5032FF, alpha: 0.55), location: 0),
            .init(color: M.color(0x2814A0, alpha: 0.18), location: 0.55),
            .init(color: .clear, location: 1),
        ]), center: .init(x: cx, y: cy), startRadius: 0, endRadius: radius))
        if st.ringAlpha > 0.01 {
            let tilt = st.tilt * .pi / 180
            let items = icons.indices.map { index -> (index: Int, x: Double, y: Double, scale: Double, z: Double) in
                let theta = spin + Double(index) * .pi * 2 / 8
                let x = st.radius * cos(theta), y0 = st.radius * sin(theta)
                let y = y0 * cos(tilt), z = y0 * sin(tilt)
                let scale = 520 / (520 - z)
                return (index, cx + x * scale, cy + y * scale, scale, z)
            }.sorted { $0.z < $1.z }
            for item in items {
                let s = 68 * item.scale
                icon(context, index: item.index, x: item.x - s / 2, y: item.y - s / 2, size: s, alpha: st.ringAlpha)
            }
        }
        centre(context, cx: cx, cy: cy, size: st.centre, arrow: st.arrow, time: t)
        let idea = M.segment(t, 1.6, 2.2) * (1 - M.segment(t, 3.1, 3.4))
        let need = M.segment(t, 3.3, 3.6) * (1 - M.segment(t, 4.2, 4.6))
        GlassIconOrbitWords.idea.draw(context, x: cx, y: cy, alpha: idea)
        GlassIconOrbitWords.need.draw(context, x: cx, y: cy, alpha: need)
    }

    private static func tile(_ context: GraphicsContext, x: Double, y: Double, size s: Double,
                             rimA: UInt32, rimB: UInt32, alpha: Double) {
        let shape = Path(roundedRect: CGRect(x: x, y: y, width: s, height: s), cornerRadius: s * 0.28)
        var c = context; c.opacity *= alpha
        var glow = c; glow.addFilter(.blur(radius: s * 0.12))
        glow.fill(shape, with: .color(M.color(rimA, alpha: 0.35)))
        c.fill(shape, with: .linearGradient(Gradient(colors: [M.color(0x1A1826), M.color(0x07070C)]),
                                          startPoint: .init(x: x, y: y), endPoint: .init(x: x, y: y + s)))
        c.fill(shape, with: .radialGradient(Gradient(colors: [M.color(rimB, alpha: 0.5), .clear]),
                                          center: .init(x: x + s * 0.5, y: y + s * 1.1), startRadius: 0, endRadius: s * 0.7))
        c.stroke(shape, with: .linearGradient(Gradient(colors: [M.color(rimA), .white.opacity(0.15), M.color(rimB)]),
                                             startPoint: .init(x: x, y: y), endPoint: .init(x: x + s, y: y + s)), lineWidth: 1.6)
    }
    private static func icon(_ context: GraphicsContext, index: Int, x: Double, y: Double, size s: Double, alpha: Double) {
        let icon = icons[index]
        tile(context, x: x, y: y, size: s, rimA: icon.rimA, rimB: icon.rimB, alpha: alpha)
        var c = context; c.opacity *= alpha
        c.translateBy(x: x, y: y); c.scaleBy(x: s / 64, y: s / 64)
        let gradient = GraphicsContext.Shading.linearGradient(Gradient(colors: [M.color(icon.a), M.color(icon.b)]),
                                                             startPoint: .init(x: 12, y: 12), endPoint: .init(x: 52, y: 52))
        for part in parts[index] {
            if part.stroke { c.stroke(part.path, with: gradient, style: StrokeStyle(lineWidth: 5, lineCap: .round)) }
            else { c.fill(part.path, with: part.pupil ? .color(M.color(0x0A0A12)) : gradient) }
        }
    }
    private static func centre(_ context: GraphicsContext, cx: Double, cy: Double, size: Double, arrow: Double, time t: Double) {
        guard size > 0.01 else { return }
        let s = 92 * size, k = s / 92
        context.fill(circle(cx, cy, s * 1.6), with: .radialGradient(
            Gradient(colors: [M.color(0x463CFF, alpha: 0.7), M.color(0x463CFF, alpha: 0)]),
            center: .init(x: cx, y: cy), startRadius: 0, endRadius: s * 1.6))
        tile(context, x: cx - s / 2, y: cy - s / 2, size: s, rimA: 0xFF8A3D, rimB: 0x5B5CFF, alpha: 1)
        if arrow < 1 {
            for index in 0..<3 {
                let bounce = t > 5.1 && t < 6.2 ? max(0, sin((t - 5.1 - Double(index) * 0.12) * .pi * 2.8)) * 7 : 0
                let x = cx + Double(index - 1) * 16 * k * (1 - arrow)
                let y = cy + 6 * k - bounce * k - (index == 1 ? 18 * arrow * k : 0)
                context.fill(circle(x, y, 4.2 * k), with: .color(.white.opacity(1 - arrow)))
            }
        }
        if arrow > 0 {
            let arrowPath = path { p in
                p.move(to: .init(x: cx, y: cy + 20 * k)); p.addLine(to: .init(x: cx, y: cy - 20 * k))
                p.move(to: .init(x: cx - 16 * k * arrow, y: cy - 4 * k))
                p.addLine(to: .init(x: cx, y: cy - 20 * k)); p.addLine(to: .init(x: cx + 16 * k * arrow, y: cy - 4 * k))
            }
            context.stroke(arrowPath, with: .color(.white.opacity(arrow)), style: StrokeStyle(lineWidth: 5 * k, lineCap: .round))
        }
    }
}

// Cache the two word overlays with TextKit's native metrics, rather than rasterizing per frame.
private enum GlassIconOrbitWords {
    struct Sprite {
        var image: Image
        var size: CGSize
        func draw(_ context: GraphicsContext, x: Double, y: Double, alpha: Double) {
            guard alpha > 0 else { return }
            var c = context; c.opacity *= alpha
            c.draw(image, in: CGRect(x: x - size.width / 2, y: y - size.height / 2, width: size.width, height: size.height))
        }
    }
    static let idea = sprite("Any idea")
    static let need = sprite("Any need")
    private static func sprite(_ text: String) -> Sprite {
        let storage = NSTextStorage(string: text, attributes: [
            .font: UIFont.systemFont(ofSize: 38, weight: .semibold), .foregroundColor: UIColor.white, .kern: -0.8,
        ])
        let layout = NSLayoutManager(); layout.usesFontLeading = false
        let container = NSTextContainer(size: CGSize(width: 1000, height: 100))
        container.lineFragmentPadding = 0
        layout.addTextContainer(container); storage.addLayoutManager(layout)
        let range = layout.glyphRange(for: container), bounds = layout.usedRect(for: container)
        let format = UIGraphicsImageRendererFormat(); format.scale = UIScreen.main.scale
        let size = CGSize(width: ceil(bounds.width * format.scale) / format.scale,
                          height: ceil(bounds.height * format.scale) / format.scale)
        let image = UIGraphicsImageRenderer(size: size, format: format).image { _ in layout.drawGlyphs(forGlyphRange: range, at: .zero) }
        return Sprite(image: Image(uiImage: image), size: size)
    }
}

#Preview { GlassIconOrbit(autoPlay: true) }
