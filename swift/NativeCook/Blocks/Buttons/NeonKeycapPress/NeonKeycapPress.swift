import CoreText
import SwiftUI
import UIKit

// Faithful port of the React Native Glowpad reference. Each 2.4-second phase presses
// Play, AI, then Moon. The finger glides for 0.35 s; the key sinks to 0.92 at 0.5 s
// and recovers by 0.75 s. Three clipped rings start at 0.4/0.58/0.76 s. The glow
// rises at 0.45–0.75 s, holds until 1.6 s, and fades by 2.3 s. A tap starts the
// same curve at 0.35 s, hides the ghost, and restarts autoplay after 2.4 s.
struct NeonKeycapPress: View {
    var title = "Your glow pad"
    var autoPlay = false

    static let loop: Double = 7.2

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var origin = Date()
    @State private var tappedAt: Date?
    @State private var tappedKey = 0
    @State private var touchCount = 0
    @State private var inactiveAt: Date?
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    private typealias M = NeonKeycapPressMotion
    private typealias P = NeonKeycapPressPalette

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(
                width: geometry.size.width + safe.leading + safe.trailing,
                height: geometry.size.height + safe.top + safe.bottom
            )
            TimelineView(.animation(paused: reduceMotion || scenePhase != .active || (!autoPlay && tappedAt == nil))) { timeline in
                let state = frame(at: timeline.date, size: size)
                ZStack(alignment: .topLeading) {
                    P.background
                    Canvas { context, canvasSize in
                        NeonKeycapPressDrawing.draw(context: &context, size: canvasSize, state: state)
                    }
                    .accessibilityLabel("Nine neon keys. Tap one to press it.")
                    header(safe: safe)
                    footer(safe: safe, key: state.key, glow: state.glow)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(SpatialTapGesture().onEnded { event in
                guard !reduceMotion else { return }
                let start = CGPoint(x: size.width / 2 - M.span / 2, y: size.height * 0.48 - M.span / 2)
                // The reference intentionally includes the inter-key gaps in each cell.
                let column = Int(floor((event.location.x - start.x) / M.step))
                let row = Int(floor((event.location.y - start.y) / M.step))
                guard (0...2).contains(column), (0...2).contains(row) else { return }
                tappedKey = row * 3 + column
                tappedAt = .now
                touchCount += 1
                haptic.impactOccurred()
            })
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear {
            origin = .now
            haptic.prepare()
        }
        .task(id: NeonKeycapPressTimer(touch: touchCount, active: scenePhase == .active, tap: tappedAt)) {
            guard scenePhase == .active, inactiveAt == nil, let tap = tappedAt else { return }
            let remaining = max(0, 2.4 - Date.now.timeIntervalSince(tap))
            do { try await Task.sleep(for: .seconds(remaining)) } catch { return }
            guard !Task.isCancelled, scenePhase == .active, tappedAt == tap else { return }
            tappedAt = nil
            origin = .now
        }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active {
                if inactiveAt == nil { inactiveAt = .now }
            } else if let pausedAt = inactiveAt {
                let pause = Date.now.timeIntervalSince(pausedAt)
                origin = origin.addingTimeInterval(pause)
                if let tap = tappedAt { tappedAt = tap.addingTimeInterval(pause) }
                inactiveAt = nil
            }
        }
    }

    private func frame(at date: Date, size: CGSize) -> M.Frame {
        guard !reduceMotion else { return .rest }
        if let tap = tappedAt {
            let press = M.press(at: max(0, date.timeIntervalSince(tap)) + 0.35)
            return M.Frame(key: tappedKey, sink: press.sink, glow: press.glow, ripple: press.ripple, finger: nil)
        }
        guard autoPlay else {
            return M.Frame(key: touchCount > 0 ? tappedKey : 4, sink: 1, glow: 0, ripple: -1, finger: nil)
        }
        return M.frame(at: max(0, date.timeIntervalSince(origin)), size: size)
    }

    private func header(safe: EdgeInsets) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text("Glowpad")
                    .font(.system(size: 18, weight: .heavy))
                    .foregroundStyle(.white)
                Spacer()
                Text("Connected")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(P.rim)
            }
            .frame(height: 34)
            Text(title)
                .font(.system(size: 36, weight: .black))
                .tracking(-1)
                .foregroundStyle(.white)
                .frame(height: 40)
                .offset(y: -5.0 / 3)
        }
        .padding(.horizontal, 24)
        .padding(.top, safe.top + 8)
        .allowsHitTesting(false)
    }

    private func footer(safe: EdgeInsets, key: Int, glow: Double) -> some View {
        VStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 6) {
                Text("LAST ACTION")
                    .font(.system(size: 13, weight: .bold))
                    .tracking(1)
                    .foregroundStyle(.white.opacity(0.55))
                    .offset(y: 1.0 / 3)
                Text(M.actions[key])
                    .font(.system(size: 22, weight: .heavy))
                    .foregroundStyle(.white)
                    .opacity(0.55 + 0.45 * M.clamp(glow * 1.5))
                    .offset(y: 6 * (1 - M.clamp(glow * 3)))
                    .frame(maxWidth: .infinity, alignment: .topLeading)
                    .frame(height: 30, alignment: .topLeading)
            }
            .padding(18)
            .padding(1)
            .background(RoundedRectangle(cornerRadius: 22, style: .circular).fill(P.neon.opacity(0.1)))
            .overlay(RoundedRectangle(cornerRadius: 22, style: .circular).strokeBorder(P.rim.opacity(0.3), lineWidth: 1))
            Text("Tap a key to run its macro")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white.opacity(0.5))
        }
        .padding(.horizontal, 20)
        .padding(.bottom, safe.bottom + 18)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        .allowsHitTesting(false)
    }
}

private struct NeonKeycapPressTimer: Hashable {
    let touch: Int
    let active: Bool
    let tap: Date?
}

private enum NeonKeycapPressPalette {
    static func color(_ rgb: UInt32, alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((rgb >> 16) & 255) / 255,
              green: Double((rgb >> 8) & 255) / 255, blue: Double(rgb & 255) / 255, opacity: alpha)
    }
    static let background = color(0x07020F)
    static let room = color(0x3A1670)
    static let neon = color(0xB266FF)
    static let rim = color(0xC59BFF)
}

private enum NeonKeycapPressMotion {
    static let key: CGFloat = 92
    static let step: CGFloat = 108
    static let span: CGFloat = 308
    static let presses = [4, 1, 6]
    static let actions = ["Send the draft", "Ask the assistant", "Boost mode on", "Start dictation",
                          "Play focus mix", "Save to favourites", "Night mode on", "Star this thread", "New note"]

    struct Frame {
        var key: Int
        var sink: Double
        var glow: Double
        var ripple: Double
        var finger: CGPoint?
        static let rest = Frame(key: 4, sink: 1, glow: 0, ripple: -1, finger: nil)
    }

    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func ease(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }

    static func press(at x: Double) -> (sink: Double, glow: Double, ripple: Double) {
        guard x >= 0, x <= 2.4 else { return (1, 0, -1) }
        let sink = x < 0.35 ? 1 : x < 0.5 ? 1 - 0.08 * seg(x, 0.35, 0.5) : 0.92 + 0.08 * seg(x, 0.5, 0.75)
        let glow = seg(x, 0.45, 0.75) * (1 - ease(seg(x, 1.6, 2.3)))
        return (sink, glow, x - 0.4)
    }

    static func centre(_ i: Int, size: CGSize) -> CGPoint {
        CGPoint(x: size.width / 2 - span / 2 + key / 2 + CGFloat(i % 3) * step,
                y: size.height * 0.48 - span / 2 + key / 2 + CGFloat(i / 3) * step)
    }

    static func frame(at elapsed: Double, size: CGSize) -> Frame {
        let t = elapsed.truncatingRemainder(dividingBy: NeonKeycapPress.loop)
        let phase = min(2, Int(t / 2.4))
        let x = t - Double(phase) * 2.4
        let key = presses[phase]
        let previous = centre(presses[(phase + 2) % 3], size: size)
        let target = centre(key, size: size)
        let move = ease(seg(x, 0, 0.35))
        let finger = CGPoint(x: previous.x + (target.x - previous.x) * move + 14,
                             y: previous.y + (target.y - previous.y) * move + 22)
        let p = press(at: x)
        return Frame(key: key, sink: p.sink, glow: p.glow, ripple: p.ripple, finger: finger)
    }
}

private enum NeonKeycapPressDrawing {
    typealias M = NeonKeycapPressMotion
    typealias P = NeonKeycapPressPalette

    static func draw(context: inout GraphicsContext, size: CGSize, state: M.Frame) {
        let centre = CGPoint(x: size.width / 2, y: size.height * 0.48)
        var room = context
        room.opacity = 0.35 + 0.65 * state.glow
        room.fill(Path(CGRect(origin: .zero, size: size)), with: .radialGradient(
            Gradient(colors: [P.room, P.background]), center: centre, startRadius: 0, endRadius: size.width * 1.1
        ))

        for i in 0..<9 {
            let active = i == state.key
            let glow = active ? state.glow : 0
            let sink = active ? state.sink : 1
            let ripple = active ? state.ripple : -1
            let c = M.centre(i, size: size)
            let side = M.key * sink
            let rect = CGRect(x: c.x - side / 2, y: c.y - side / 2, width: side, height: side)
            let shape = Path(roundedRect: rect, cornerRadius: 22 * sink, style: .circular)

            context.drawLayer { halo in
                halo.addFilter(.blur(radius: 10 + 16 * glow))
                halo.stroke(shape, with: .color(P.neon.opacity(0.35 + 0.55 * glow)), lineWidth: 6 + 6 * glow)
            }
            context.fill(shape, with: .linearGradient(
                Gradient(colors: [P.color(0x7846DC, alpha: 0.38), P.color(0x32146E, alpha: 0.55)]),
                startPoint: CGPoint(x: c.x, y: rect.minY), endPoint: CGPoint(x: c.x, y: rect.maxY)
            ))
            if glow > 0.001 || ripple >= 0 {
                var pressed = context
                pressed.clip(to: shape)
                var fill = pressed
                fill.opacity = glow * 0.85
                fill.fill(shape, with: .radialGradient(
                    Gradient(stops: [.init(color: P.color(0xFF9CF5), location: 0),
                                     .init(color: P.color(0xC04BFF), location: 0.5),
                                     .init(color: P.color(0x6A2BD8), location: 1)]),
                    center: CGPoint(x: c.x, y: c.y + 10), startRadius: 0, endRadius: side * 0.75
                ))
                for ring in 0..<3 {
                    let age = ripple - Double(ring) * 0.18
                    guard age >= 0, age <= 1 else { continue }
                    let radius = 80 * sqrt(age)
                    let circle = Path(ellipseIn: CGRect(x: c.x - radius, y: c.y + 8 - radius, width: radius * 2, height: radius * 2))
                    pressed.drawLayer { layer in
                        layer.addFilter(.blur(radius: 2))
                        layer.stroke(circle, with: .color(P.color(0xFFE3FF, alpha: (1 - age) * 0.9)), lineWidth: 5 * (1 - age) + 1)
                    }
                }
            }
            context.stroke(shape, with: .color(glow > 0.3 ? P.color(0xFFD6FF) : P.rim), lineWidth: 2)

            var icon = context
            icon.opacity = 0.85 + 0.15 * glow
            icon.translateBy(x: c.x, y: c.y)
            if i == 1 {
                // Match Skia's rounded glyph-width measurement, rather than outline centering.
                icon.translateBy(x: -5.0 / 3, y: 11)
                icon.fill(ai, with: .color(.white))
            } else {
                icon.scaleBy(x: 22 * sink, y: 22 * sink)
                icon.fill(icons[i], with: .color(.white))
                if i == 3 {
                    // The microphone stand is fully white in the reference.
                    icon.opacity = 1
                    icon.stroke(micStand, with: .color(.white), style: StrokeStyle(lineWidth: 0.16, lineCap: .round))
                }
            }
        }
        if let finger = state.finger {
            let circle = Path(ellipseIn: CGRect(x: finger.x - 18, y: finger.y - 18, width: 36, height: 36))
            context.fill(circle, with: .color(.white.opacity(0.45)))
            context.stroke(circle, with: .color(.white.opacity(0.85)), lineWidth: 2)
        }
    }

    // Cache the glyph outlines once; UIFont bridges directly to CTFont without losing its face.
    private static let ai: Path = {
        let font = (UIFont(name: "Helvetica-Bold", size: 30) ?? .boldSystemFont(ofSize: 30)) as CTFont
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: "AI", attributes: [.font: font]))
        let outline = CGMutablePath()
        for run in CTLineGetGlyphRuns(line) as? [CTRun] ?? [] {
            let count = CTRunGetGlyphCount(run)
            var glyphs = [CGGlyph](repeating: 0, count: count)
            var positions = [CGPoint](repeating: .zero, count: count)
            CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &glyphs)
            CTRunGetPositions(run, CFRange(location: 0, length: 0), &positions)
            for i in 0..<count {
                var transform = CGAffineTransform(translationX: positions[i].x, y: 0).scaledBy(x: 1, y: -1)
                if let path = CTFontCreatePathForGlyph(font, glyphs[i], &transform) { outline.addPath(path) }
            }
        }
        return Path(outline).offsetBy(dx: -outline.boundingBoxOfPath.width / 2, dy: 0)
    }()

    private static let micStand = Path { p in
        p.move(to: CGPoint(x: 0.6, y: 0.05))
        p.addCurve(to: CGPoint(x: 0, y: 0.6), control1: CGPoint(x: 0.6, y: 0.3537566), control2: CGPoint(x: 0.33137085, y: 0.6))
        p.addCurve(to: CGPoint(x: -0.6, y: 0.05), control1: CGPoint(x: -0.33137085, y: 0.6), control2: CGPoint(x: -0.6, y: 0.3537566))
        p.move(to: CGPoint(x: 0, y: 0.6))
        p.addLine(to: CGPoint(x: 0, y: 0.95))
    }

    private static let icons: [Path] = (0..<9).map { i in
        Path { p in
            switch i {
            case 0:
                p.move(to: CGPoint(x: -1, y: -0.1))
                for point in [CGPoint(x: 1, y: -0.9), CGPoint(x: 0.35, y: 0.95), CGPoint(x: 0.05, y: 0.2)] { p.addLine(to: point) }
                p.closeSubpath()
            case 2:
                p.move(to: CGPoint(x: 0.2, y: -1))
                for point in [CGPoint(x: -0.55, y: 0.12), CGPoint(x: -0.02, y: 0.12), CGPoint(x: -0.25, y: 1), CGPoint(x: 0.55, y: -0.15), CGPoint(x: 0.02, y: -0.15)] { p.addLine(to: point) }
                p.closeSubpath()
            case 3:
                p.addRoundedRect(in: CGRect(x: -0.32, y: -1, width: 0.64, height: 1.2), cornerSize: CGSize(width: 0.32, height: 0.32), style: .circular)
            case 4:
                p.move(to: CGPoint(x: -0.55, y: -0.85))
                p.addLine(to: CGPoint(x: 0.85, y: 0))
                p.addLine(to: CGPoint(x: -0.55, y: 0.85))
                p.closeSubpath()
            case 5:
                p.move(to: CGPoint(x: 0, y: 0.85))
                p.addCurve(to: CGPoint(x: 0, y: -0.45), control1: CGPoint(x: -1.1, y: 0.1), control2: CGPoint(x: -0.9, y: -0.95))
                p.addCurve(to: CGPoint(x: 0, y: 0.85), control1: CGPoint(x: 0.9, y: -0.95), control2: CGPoint(x: 1.1, y: 0.1))
                p.closeSubpath()
            case 6:
                p.move(to: CGPoint(x: 0.2, y: -0.95))
                p.addCurve(to: CGPoint(x: -0.35, y: 0.8), control1: CGPoint(x: -0.75, y: -0.85), control2: CGPoint(x: -1, y: 0.35))
                p.addCurve(to: CGPoint(x: 0.95, y: 0.35), control1: CGPoint(x: 0.15, y: 1.1), control2: CGPoint(x: 0.75, y: 0.85))
                p.addCurve(to: CGPoint(x: 0.2, y: -0.95), control1: CGPoint(x: 0.3, y: 0.55), control2: CGPoint(x: -0.35, y: 0.05))
                p.closeSubpath()
            case 7:
                for vertex in 0..<10 {
                    let angle = -Double.pi / 2 + Double(vertex) * .pi / 5
                    let radius = vertex.isMultiple(of: 2) ? 1.0 : 0.45
                    let point = CGPoint(x: cos(angle) * radius, y: sin(angle) * radius)
                    if vertex == 0 { p.move(to: point) } else { p.addLine(to: point) }
                }
                p.closeSubpath()
            default:
                p.addRect(CGRect(x: -0.12, y: -0.85, width: 0.24, height: 1.7))
                p.addRect(CGRect(x: -0.85, y: -0.12, width: 1.7, height: 0.24))
            }
        }
    }
}
