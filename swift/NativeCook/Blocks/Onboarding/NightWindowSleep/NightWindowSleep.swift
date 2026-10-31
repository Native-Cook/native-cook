import SwiftUI
import UIKit

// A faithful port of the React Native Lull welcome screen, drawn in full-screen points.
// 9 s loop: day 0–1.2; sunset 1.2–4.2 (p 0→.5); nightfall 4.2–6 (p .5→1);
// night 6–7.6; dawn 7.6–9 (p 1→0). Every transition uses ease-in-out sine.
// The tag swings ±7° every 3 s. A horizontal drag changes p by .004/pt, holds
// for 2 s after release, then resumes at the nearest .1 s sample on the sunset side.
struct NightWindowSleep: View {
    static let loop: Double = 9
    var autoPlay = false

    private typealias M = NightWindowSleepMotion
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    @GestureState private var fingerDown = false
    @State private var appeared = false
    @State private var epoch = Date()
    @State private var accumulated = 0.0
    @State private var loopOrigin = 0.0
    @State private var manual = false
    @State private var touching = false
    @State private var panActivated = false
    @State private var grabbed = 0.0
    @State private var heldPhase = 0.0
    @State private var idlePhase = 0.0
    @State private var released = 0.0
    @State private var touches = 0
    @State private var retained = M.Frame(phase: 1, clock: 0)
    @State private var haptic = UISelectionFeedbackGenerator()

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(width: geometry.size.width + safe.leading + safe.trailing,
                              height: geometry.size.height + safe.top + safe.bottom)
            TimelineView(.animation(paused: !appeared || scene != .active || reduced)) { timeline in
                let current = frame(at: timeline.date)
                Canvas(opaque: true, colorMode: .nonLinear) { context, _ in
                    NightWindowSleepArt.draw(context, size: size, safe: safe, frame: current)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Meet Lull. A window to wind down by. A window from day to night. Drag across it to change the hour.")
                .overlay(alignment: .bottom) {
                    Color.clear.frame(height: 58)
                        .padding(.horizontal, 24)
                        .padding(.bottom, safe.bottom + 14 + 12 + NightWindowSleepType.note.size.height)
                        .accessibilityLabel("Set my bedtime")
                        .accessibilityAddTraits(.isButton)
                        .allowsHitTesting(false)
                }
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(pan, including: reduced ? .none : .all)
            .ignoresSafeArea()
        }
        .background(M.color(20, 39, 92).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            _ = NightWindowSleepType.header
            epoch = .now
            appeared = true
            haptic.prepare()
        }
        .onDisappear {
            accumulated = elapsed(at: .now)
            appeared = false
        }
        .onChange(of: scene) { old, _ in
            let now = Date()
            if old == .active && !reduced && appeared {
                accumulated += max(0, now.timeIntervalSince(epoch))
            }
            epoch = now
        }
        .onChange(of: reduced) { _, value in
            let now = Date()
            if value {
                retained = frame(at: now, ignoringReduced: true)
                accumulated = elapsed(at: now, ignoringReduced: true)
            }
            epoch = now
        }
        .onChange(of: fingerDown) { _, down in
            if !down && touching {
                Task { @MainActor in
                    await Task.yield()
                    if !fingerDown && touching { finishPan() }
                }
            }
        }
        .task(id: touches) {
            guard manual, !touching else { return }
            while elapsed(at: .now) - released <= M.hold {
                do { try await Task.sleep(for: .milliseconds(50)) } catch { return }
            }
            guard !Task.isCancelled, !touching else { return }
            loopOrigin = resumedOrigin
            idlePhase = heldPhase
            manual = false
        }
    }

    private func elapsed(at date: Date, ignoringReduced: Bool = false) -> Double {
        accumulated + (appeared && scene == .active && (!reduced || ignoringReduced)
            ? max(0, date.timeIntervalSince(epoch)) : 0)
    }

    private var resumedOrigin: Double {
        released + M.hold - M.closestTime(to: heldPhase)
    }

    private func frame(at date: Date, ignoringReduced: Bool = false) -> M.Frame {
        if reduced && !ignoringReduced { return retained }
        let now = elapsed(at: date, ignoringReduced: ignoringReduced)
        let holding = manual && (touching || now - released <= M.hold)
        let origin = manual && !holding ? resumedOrigin : loopOrigin
        let t = M.clock(now - origin)
        let p = holding ? heldPhase : (autoPlay ? M.phase(at: t) : (manual ? heldPhase : idlePhase))
        return .init(phase: p, clock: t)
    }

    private var pan: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($fingerDown) { _, down, _ in down = true }
            .onChanged { value in
                guard !reduced else { return }
                if !touching {
                    grabbed = frame(at: .now).phase
                    heldPhase = grabbed
                    touching = true
                    manual = true
                    panActivated = false
                    touches += 1
                    haptic.selectionChanged()
                    haptic.prepare()
                }
                if hypot(value.translation.width, value.translation.height) >= 4 { panActivated = true }
                if panActivated { heldPhase = M.clamp(grabbed + value.translation.width * 0.004) }
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

private enum NightWindowSleepMotion {
    static let hold = 2.0
    static let ww = 236.0, wh = 300.0
    struct Frame { var phase: Double; var clock: Double }
    struct RGB { var r: Double; var g: Double; var b: Double }
    static let day = [RGB(r: 62, g: 123, b: 255), RGB(r: 159, g: 211, b: 255)]
    static let dusk = [RGB(r: 59, g: 63, b: 154), RGB(r: 255, g: 154, b: 77)]
    static let night = [RGB(r: 8, g: 12, b: 52), RGB(r: 32, g: 40, b: 112)]
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func sine(_ x: Double) -> Double { -(cos(.pi * x) - 1) / 2 }
    static func clock(_ time: Double) -> Double {
        let period = NightWindowSleep.loop
        return (time.truncatingRemainder(dividingBy: period) + period).truncatingRemainder(dividingBy: period)
    }
    static func phase(at t: Double) -> Double {
        if t < 4.2 { return 0.5 * sine(segment(t, 1.2, 4.2)) }
        if t < 7.6 { return 0.5 + 0.5 * sine(segment(t, 4.2, 6)) }
        return 1 - sine(segment(t, 7.6, 9))
    }
    static func closestTime(to p: Double) -> Double {
        var best = 0.0, distance = 9.0
        for sample in 0...76 {
            let t = Double(sample) * 0.1
            let d = abs(phase(at: t) - p)
            if d < distance { best = t; distance = d }
        }
        return best
    }
    static func hash(_ n: Int) -> Double {
        let x = sin(Double(n) * 127.1 + 311.7) * 43758.5453
        return x - floor(x)
    }
    static func mix(_ a: RGB, _ b: RGB, _ u: Double) -> RGB {
        RGB(r: a.r + (b.r - a.r) * u, g: a.g + (b.g - a.g) * u, b: a.b + (b.b - a.b) * u)
    }
    static func sky(_ p: Double) -> [RGB] {
        let a = p < 0.5 ? day : dusk, b = p < 0.5 ? dusk : night
        let u = p < 0.5 ? p / 0.5 : (p - 0.5) / 0.5
        return [mix(a[0], b[0], u), mix(a[1], b[1], u)]
    }
    static func color(_ r: Double, _ g: Double, _ b: Double, alpha: Double = 1) -> Color {
        Color(.sRGB, red: r.rounded() / 255, green: g.rounded() / 255, blue: b.rounded() / 255, opacity: clamp(alpha))
    }
    static func color(_ value: RGB, alpha: Double = 1) -> Color { color(value.r, value.g, value.b, alpha: alpha) }
    static func hex(_ value: UInt32, alpha: Double = 1) -> Color {
        color(Double((value >> 16) & 255), Double((value >> 8) & 255), Double(value & 255), alpha: alpha)
    }
}

private enum NightWindowSleepArt {
    private typealias M = NightWindowSleepMotion
    private typealias T = NightWindowSleepType
    private static func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> Path {
        Path(CGRect(x: x, y: y, width: w, height: h))
    }
    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }
    private static func window(x: Double, y: Double) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: x, y: y + M.ww / 2))
        path.addArc(center: CGPoint(x: x + M.ww / 2, y: y + M.ww / 2), radius: M.ww / 2,
                    startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false)
        path.addLine(to: CGPoint(x: x + M.ww, y: y + M.wh - 18))
        path.addQuadCurve(to: CGPoint(x: x + M.ww - 18, y: y + M.wh), control: CGPoint(x: x + M.ww, y: y + M.wh))
        path.addLine(to: CGPoint(x: x + 18, y: y + M.wh))
        path.addQuadCurve(to: CGPoint(x: x, y: y + M.wh - 18), control: CGPoint(x: x, y: y + M.wh))
        path.closeSubpath()
        return path
    }
    private static func radial(_ c: inout GraphicsContext, x: Double, y: Double, radius: Double,
                               inner: Color, outer: Color, gradient: Gradient? = nil) {
        c.fill(circle(x, y, radius), with: .radialGradient(gradient ?? Gradient(colors: [inner, outer]),
               center: CGPoint(x: x, y: y), startRadius: 0, endRadius: radius))
    }

    // Skia interpolates unpremultiplied RGBA; explicit stops preserve the warm transparent
    // edge when SwiftUI interpolates premultiplied colors.
    private static let daySunGlow = Gradient(stops: (0...32).map { index in
        let u = Double(index) / 32
        return Gradient.Stop(color: Color(.sRGB, red: 1, green: (255 - 65 * u) / 255,
                                         blue: (230 - 140 * u) / 255, opacity: 0.8 * (1 - u)), location: u)
    })

    static func draw(_ original: GraphicsContext, size: CGSize, safe: EdgeInsets, frame f: NightWindowSleepMotion.Frame) {
        var c = original
        let cx = size.width / 2, cy = size.height * 0.45
        let p = f.phase, sky = M.sky(p), night = M.clamp((p - 0.55) / 0.35)
        let x = cx - M.ww / 2, y = cy - M.wh / 2, horizon = y + M.wh * 0.72
        c.fill(Path(CGRect(origin: .zero, size: size)), with: .color(M.color(sky[0].r * 0.32, sky[0].g * 0.32, sky[0].b * 0.32 + 10)))

        // The RN canvas is above the heading, so the spill also lights its lower edge.
        var textY = safe.top + 16
        for (index, text) in T.header.enumerated() {
            if index == 2 { textY += 4 }
            text.draw(&c, centerX: cx, top: textY)
            textY += text.size.height
        }
        radial(&c, x: cx, y: cy, radius: M.wh, inner: M.color(sky[1], alpha: 0.35 * (1 - night) + 0.12), outer: M.color(sky[1], alpha: 0))

        let path = window(x: x, y: y)
        var scene = c
        scene.clip(to: path)
        scene.fill(rect(x, y, M.ww, M.wh), with: .linearGradient(Gradient(colors: [M.color(sky[0]), M.color(sky[1])]),
                   startPoint: CGPoint(x: 0, y: y), endPoint: CGPoint(x: 0, y: y + M.wh * 0.75)))
        for i in 0..<28 {
            let sx = x + M.hash(i) * M.ww, sy = y + M.hash(i + 40) * M.wh * 0.6
            scene.fill(circle(sx, sy, 0.8 + 1.2 * M.hash(i + 120)), with: .color(.white.opacity(night * (0.4 + 0.6 * M.hash(i + 80)))))
        }
        let sunY = y + M.wh * 0.2 + (horizon - (y + M.wh * 0.2) + 30) * M.clamp(p / 0.55)
        if p < 0.6 {
            radial(&scene, x: cx, y: sunY, radius: 90,
                   inner: p > 0.3 ? M.color(255, 190, 90, alpha: 0.85) : M.color(255, 255, 230, alpha: 0.8),
                   outer: M.color(255, 190, 90, alpha: 0), gradient: p > 0.3 ? nil : daySunGlow)
            scene.fill(circle(cx, sunY, 15), with: .color(p > 0.3 ? M.hex(0xFFD27A) : .white))
        }
        let moon = M.clamp((p - 0.7) / 0.3)
        if moon > 0 {
            let mx = cx + 52, my = horizon - 20 - 120 * moon
            radial(&scene, x: mx, y: my, radius: 50, inner: M.color(200, 210, 255, alpha: 0.4 * moon), outer: M.color(200, 210, 255, alpha: 0))
            var crescent = scene
            crescent.clip(to: circle(mx, my, 13))
            var outsideCut = rect(x, y, M.ww, M.wh)
            outsideCut.addPath(circle(mx + 6, my - 4, 11))
            crescent.clip(to: outsideCut, style: FillStyle(eoFill: true))
            crescent.fill(circle(mx, my, 13), with: .color(M.hex(0xF4F1FF, alpha: moon)))
        }
        let haze = max(0, 1 - abs(p - 0.48) / 0.22)
        scene.fill(rect(x, horizon - 50, M.ww, 50), with: .linearGradient(
            Gradient(colors: [M.color(255, 140, 80, alpha: 0), M.color(255, 140, 80, alpha: 0.7 * haze)]),
            startPoint: CGPoint(x: 0, y: horizon - 50), endPoint: CGPoint(x: 0, y: horizon)))
        let city = M.color(M.mix(.init(r: 40, g: 52, b: 90), .init(r: 6, g: 8, b: 22), M.clamp(p * 1.4)))
        var bx = x - 6
        for b in 0..<11 {
            let bw = 18 + 20 * M.hash(b + 200), bh = 24 + 70 * M.hash(b + 300)
            scene.fill(rect(bx, horizon - bh + 30, bw, bh + M.wh), with: .color(city))
            for row in 0..<6 {
                for column in 0..<2 {
                    let n = b * 31 + row * 7 + column
                    if M.hash(n + 500) > 0.45 { continue }
                    let wy = horizon - bh + 38 + Double(row) * 11
                    if wy > y + M.wh - 6 { continue }
                    let alpha = M.clamp((night - M.hash(n + 700) * 0.6) / 0.4)
                    scene.fill(rect(bx + 4 + Double(column) * (bw / 2 - 2), wy, 4, 5), with: .color(M.hex(0xFFD889, alpha: alpha)))
                }
            }
            bx += bw + 2
        }
        c.stroke(path, with: .color(M.hex(0xE9E4F6)), lineWidth: 14)
        c.fill(rect(cx - 4, y + 6, 8, M.wh - 6), with: .color(M.hex(0xE9E4F6)))
        c.fill(rect(x, y + M.wh * 0.5 - 4, M.ww, 8), with: .color(M.hex(0xE9E4F6)))
        c.drawLayer { shadow in
            shadow.addFilter(.blur(radius: 8))
            shadow.fill(rect(x - 20, y + M.wh + 8, M.ww + 40, 8), with: .color(M.color(0, 0, 20, alpha: 0.25)))
        }
        c.fill(Path(roundedRect: CGRect(x: x - 22, y: y + M.wh - 2, width: M.ww + 44, height: 14), cornerRadius: 4), with: .color(M.hex(0xF4F0FB)))
        c.stroke(path, with: .color(M.color(10, 12, 40, alpha: 0.45 * night)), lineWidth: 14)

        var tag = c
        tag.translateBy(x: cx, y: cy + M.wh / 2 + 10)
        tag.rotate(by: .degrees(7 * sin(f.clock / 3 * .pi * 2)))
        tag.fill(rect(-1, 0, 2, 70), with: .color(M.hex(0xF2E66B)))
        let tagRect = CGRect(x: -75, y: 70, width: 150, height: 40)
        tag.fill(Path(roundedRect: tagRect, cornerRadius: 14), with: .color(M.color(30, 30, 30, alpha: 0.85)))
        tag.stroke(Path(roundedRect: tagRect.insetBy(dx: 1, dy: 1), cornerRadius: 13), with: .color(M.hex(0xF2E66B)), lineWidth: 2)
        let fade = M.clamp((p - 0.45) / 0.15)
        var wake = tag; wake.opacity = 1 - fade
        T.wake.draw(&wake, centerX: 0, top: 70 + (40 - T.wake.size.height) / 2)
        var bedtime = tag; bedtime.opacity = fade
        T.bedtime.draw(&bedtime, centerX: 0, top: 70 + (40 - T.bedtime.size.height) / 2)

        let footerTop = size.height - safe.bottom - 14 - T.note.size.height - 12 - 58
        c.fill(Path(roundedRect: CGRect(x: 24, y: footerTop, width: size.width - 48, height: 58), cornerRadius: 29), with: .color(M.hex(0xF2E66B)))
        T.action.draw(&c, centerX: cx, top: footerTop + (58 - T.action.size.height) / 2)
        T.note.draw(&c, centerX: cx, top: footerTop + 58 + 12)
    }
}

// TextKit uses the same native font metrics as React Native. Render each line once.
private enum NightWindowSleepType {
    struct Sprite {
        var image: Image
        var size: CGSize
        func draw(_ c: inout GraphicsContext, centerX: CGFloat, top: CGFloat) {
            c.draw(image, in: CGRect(x: centerX - size.width / 2, y: top, width: size.width, height: size.height))
        }
    }
    static let header = [text("Meet", size: 15, weight: .semibold, alpha: 0.65),
                         text("Lull", size: 46, weight: .black, tracking: -1.5),
                         text("A window to wind down by.", size: 16, alpha: 0.7)]
    static let wake = text("Wake 07:00", size: 16, weight: .heavy, hex: 0xF2E66B)
    static let bedtime = text("Bedtime 22:30", size: 16, weight: .heavy, hex: 0xF2E66B)
    static let action = text("Set my bedtime", size: 17, weight: .heavy, hex: 0x1A1A1A)
    static let note = text("Lull follows your local sunset.", size: 14, alpha: 0.6)

    private static func text(_ string: String, size: CGFloat, weight: UIFont.Weight = .regular,
                             hex: UInt32 = 0xFFFFFF, alpha: Double = 1, tracking: CGFloat = 0) -> Sprite {
        let font = UIFont.systemFont(ofSize: size, weight: weight)
        var attributes: [NSAttributedString.Key: Any] = [
            .font: font,
            .foregroundColor: UIColor(red: Double((hex >> 16) & 255) / 255,
                                      green: Double((hex >> 8) & 255) / 255,
                                      blue: Double(hex & 255) / 255, alpha: alpha)
        ]
        if tracking != 0 { attributes[.kern] = tracking }
        let storage = NSTextStorage(string: string, attributes: attributes)
        let layout = NSLayoutManager()
        layout.usesFontLeading = false
        let container = NSTextContainer(size: CGSize(width: 2000, height: 200))
        container.lineFragmentPadding = 0
        container.maximumNumberOfLines = 1
        container.lineBreakMode = .byClipping
        layout.addTextContainer(container)
        storage.addLayoutManager(layout)
        let range = layout.glyphRange(for: container)
        let bounds = layout.usedRect(for: container)
        let format = UIGraphicsImageRendererFormat()
        format.scale = UIScreen.main.scale
        let dimensions = CGSize(width: ceil(bounds.width * format.scale) / format.scale,
                                height: ceil(bounds.height * format.scale) / format.scale)
        let rendered = UIGraphicsImageRenderer(size: dimensions, format: format).image { _ in
            layout.drawGlyphs(forGlyphRange: range, at: .zero)
        }
        return Sprite(image: Image(uiImage: rendered), size: dimensions)
    }
}

#Preview { NightWindowSleep(autoPlay: true) }
