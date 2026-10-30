import SwiftUI
import UIKit
import CoreText

// Matches the React Native reference, including its sampled woven band.
// 8 s: rest 0–0.6, inhale to 3, hold to 4.6, exhale to 7.2, rest to 8.
struct NoodleNetOrbit: View {
    var brand = "unwind"
    var pattern = "4 · 7 · 8"
    var round = "ROUND 2 OF 4"
    var session = "Evening wind-down · 6 min"
    var action = "End session"
    var autoPlay = false

    static let loop: Double = 8
    private static let inward = 64.0
    private static let bulge = 12.0
    private static let beadGap = 9.0
    private static let restAngle = -Double.pi / 2
    private static let hold = 2.0
    private static let returnDuration = 0.8

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    @State private var origin = Date()
    @State private var pausedAt: Date?
    @State private var pausedDuration = 0.0
    @State private var loopOrigin = 0.0
    @State private var touching = false
    @State private var released: Double?
    @State private var userAngle = -Double.pi / 2
    @State private var userPress = 0.0
    @State private var touches = 0

    private enum Palette {
        static let background = Color(.sRGB, red: 13.0 / 255, green: 11.0 / 255, blue: 10.0 / 255)
        static let lift = Color(.sRGB, red: 30.0 / 255, green: 25.0 / 255, blue: 21.0 / 255)
        static let cream = Color(.sRGB, red: 243.0 / 255, green: 237.0 / 255, blue: 228.0 / 255)
        static let dim = cream.opacity(0.5)
        static let faint = cream.opacity(0.18)
        static let bead = Color(.sRGB, red: 1, green: 59.0 / 255, blue: 47.0 / 255)
        static let warm = Color(.sRGB, red: 1, green: 122.0 / 255, blue: 61.0 / 255)
    }

    private enum Typeface {
        static let registered: Void = {
            for face in ["Regular", "Italic"] {
                if let url = Bundle.main.url(forResource: "NoodleNetOrbit-InstrumentSerif-\(face)", withExtension: "ttf") {
                    CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
                }
            }
        }()

        static func serif(_ size: CGFloat, italic: Bool = false) -> Font {
            _ = registered
            return .custom("InstrumentSerif-\(italic ? "Italic" : "Regular")", fixedSize: size)
        }
    }

    private struct Frame {
        var angle: Double
        var press: Double
        var time: Double
        var phase: Double
    }

    private static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    private static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    private static func cubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    private static func smooth(_ x: Double) -> Double { let u = clamp(x); return u * u * (3 - 2 * u) }

    // Like RN's active frame callback, this clock excludes time spent in the background.
    private func elapsed(at date: Date) -> Double {
        max(0, (pausedAt ?? date).timeIntervalSince(origin) - pausedDuration)
    }

    private func frame(at date: Date) -> Frame {
        guard !reduced else { return Frame(angle: Self.restAngle, press: 0, time: 0, phase: 0) }
        let now = elapsed(at: date)
        if touching {
            return Frame(angle: userAngle, press: userPress, time: 0, phase: 4 * .pi * (now - loopOrigin) / Self.loop)
        }
        var start = loopOrigin
        if let released {
            let since = now - released
            if since < Self.hold + Self.returnDuration {
                let k = Self.cubic(Self.clamp((since - Self.hold) / Self.returnDuration))
                let delta = atan2(sin(Self.restAngle - userAngle), cos(Self.restAngle - userAngle))
                // RN resets its loop origin on every release frame, so the weave phase is zero.
                return Frame(angle: userAngle + delta * k, press: userPress * (1 - k), time: 0, phase: 0)
            }
            start = released + Self.hold + Self.returnDuration
        }
        let duration = max(0, now - start)
        let t = autoPlay ? duration.truncatingRemainder(dividingBy: Self.loop) : 0
        let angle = Self.restAngle + .pi * (1 - cos(.pi * Self.seg(t, 0.6, 7.2)))
        let inhale = Self.cubic(Self.seg(t, 0.6, 3))
        let exhale = Self.cubic(Self.seg(t, 4.6, 7.2))
        let tremble = sin(Self.seg(t, 3, 4.6) * .pi) * sin(t * 19) * 3 / Self.inward
        return Frame(angle: angle, press: inhale * (1 - exhale) + tremble, time: t, phase: 4 * .pi * duration / Self.loop)
    }

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let w = geo.size.width + safe.leading + safe.trailing
            let h = geo.size.height + safe.top + safe.bottom
            let radius = min(w * 0.42, 166)
            let centre = CGPoint(x: w / 2, y: h * 0.47)
            TimelineView(.animation(paused: reduced || scene != .active || (!autoPlay && released == nil && !touching))) { timeline in
                let f = frame(at: timeline.date)
                let hold = Self.smooth((f.time - 3) / 0.3)
                let out = Self.smooth((f.time - 4.6) / 0.3)
                let inhale = Self.smooth((f.time - 7.2) / 0.3)
                ZStack(alignment: .topLeading) {
                    Canvas { context, _ in
                        context.fill(Path(CGRect(x: 0, y: 0, width: w, height: h)), with: .radialGradient(
                            Gradient(colors: [Palette.lift, Palette.background]),
                            center: centre, startRadius: 0, endRadius: h * 0.6
                        ))
                        drawNet(&context, centre: centre, radius: radius, frame: f)
                    }
                    .accessibilityLabel("Breathing circle. Drag the red bead to pull the net.")

                    HStack(spacing: 0) {
                        Text(brand)
                            .font(Typeface.serif(30, italic: true))
                            .frame(height: 36)
                            // Match RN's fixed line box, which is shorter than the font's metrics.
                            .offset(y: -4.0 / 3)
                        Spacer(minLength: 0)
                        Text(pattern)
                            .font(.system(size: 13, weight: .semibold))
                            .tracking(1)
                            // RN's border adds a point to each side of its horizontal padding.
                            .padding(.horizontal, 15)
                            .frame(height: 30)
                            .overlay(Capsule().strokeBorder(Palette.faint, lineWidth: 1))
                    }
                    .foregroundStyle(Palette.cream)
                    .frame(width: w - 48, height: 36)
                    .offset(x: 24, y: safe.top + 8)
                    .allowsHitTesting(false)

                    Text(round)
                        .font(.system(size: 12, weight: .semibold))
                        .tracking(3)
                        .foregroundStyle(Palette.dim)
                        .frame(width: w, height: 16)
                        .offset(y: centre.y - radius - 56)
                        .allowsHitTesting(false)

                    ZStack {
                        word("Inhale", opacity: touching ? 0 : 1 - hold + inhale)
                        word("Hold", opacity: touching ? 1 : hold * (1 - out))
                        word("Exhale", opacity: touching ? 0 : out * (1 - inhale))
                    }
                    .frame(width: w, height: 68)
                    .offset(y: centre.y - 34)
                    .allowsHitTesting(false)

                    VStack(spacing: 14) {
                        HStack(spacing: 8) {
                            ForEach(0..<4) { k in
                                RoundedRectangle(cornerRadius: 2)
                                    .fill(k < 2 ? Palette.cream : Palette.faint)
                                    .frame(width: 28, height: 3)
                            }
                        }
                        Text(session)
                            .font(Typeface.serif(20))
                            .foregroundStyle(Palette.dim)
                            .frame(height: 26)
                    }
                    .frame(width: w - 48)
                    .offset(x: 24, y: centre.y + radius + 36)
                    .allowsHitTesting(false)

                    Text(action)
                        .font(.system(size: 15, weight: .semibold))
                        .tracking(0.3)
                        .foregroundStyle(Palette.cream)
                        .padding(.horizontal, 35)
                        .frame(height: 52)
                        .overlay(Capsule().strokeBorder(Palette.faint, lineWidth: 1))
                        .position(x: w / 2, y: h - safe.bottom - 18 - 26)
                        .accessibilityAddTraits(.isButton)
                }
                .frame(width: w, height: h, alignment: .topLeading)
                .contentShape(Rectangle())
                .gesture(drag(centre: centre, radius: radius))
            }
            .ignoresSafeArea()
        }
        .background(Palette.background)
        .preferredColorScheme(.dark)
        .onAppear { origin = .now }
        .onChange(of: scene) { _, phase in
            if phase == .active {
                if let pausedAt { pausedDuration += Date().timeIntervalSince(pausedAt) }
                pausedAt = nil
            } else if pausedAt == nil {
                pausedAt = .now
            }
        }
        .task(id: touches) { await finishRelease() }
    }

    private func word(_ text: String, opacity: Double) -> some View {
        Text(text)
            .font(Typeface.serif(58, italic: true))
            .foregroundStyle(Palette.cream)
            .frame(height: 68)
            // RN clips the italic left overhang to the text's measured advance width.
            .clipped()
            .opacity(opacity)
            .offset(y: 8 * (1 - opacity) - 10.0 / 3)
    }

    private func drag(centre: CGPoint, radius: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                guard !reduced else { return }
                if !touching {
                    let now = elapsed(at: .now)
                    if let released { loopOrigin = min(now, released + Self.hold + Self.returnDuration) }
                    touching = true
                    released = nil
                    touches += 1
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }
                let dx = value.location.x - centre.x
                let dy = value.location.y - centre.y
                userAngle = atan2(dy, dx)
                userPress = min(1.15, max(0, (radius - Self.beadGap - hypot(dx, dy)) / Self.inward))
            }
            .onEnded { _ in
                guard !reduced else { return }
                touching = false
                released = elapsed(at: .now)
                touches += 1
            }
    }

    private func finishRelease() async {
        guard let release = released, !touching else { return }
        let end = release + Self.hold + Self.returnDuration
        // Only clear state after settling; no state is written by the animation timeline.
        while elapsed(at: .now) < end {
            do { try await Task.sleep(for: .seconds(max(0, min(0.2, end - elapsed(at: .now))))) }
            catch { return }
        }
        guard !Task.isCancelled, !touching else { return }
        loopOrigin = end
        released = nil
    }

    private func bandPoint(centre: CGPoint, radius: Double, angle: Double, half: Double,
                           inward: Double, bulge: Double, s: Double, q: Double) -> CGPoint {
        let envelope = pow(sin(.pi * Self.clamp(s)), 2)
        let inner = radius - inward * envelope
        let outer = radius + bulge * envelope
        let phi = angle - half + 2 * half * s
        let r = inner + (outer - inner) * q
        return CGPoint(x: centre.x + cos(phi) * r, y: centre.y + sin(phi) * r)
    }

    private func drawNet(_ context: inout GraphicsContext, centre: CGPoint, radius: Double, frame: Frame) {
        let p = max(0, frame.press)
        let half = 0.55 + 0.25 * min(1, p)
        let inward = Self.inward * p
        let bulge = Self.bulge * p
        let breath = min(1, p)
        let warmRadius = radius * (0.7 + 0.3 * breath)
        context.drawLayer { warm in
            warm.opacity = 0.25 + 0.75 * breath
            warm.fill(Path(ellipseIn: CGRect(x: centre.x - warmRadius, y: centre.y - warmRadius,
                                            width: warmRadius * 2, height: warmRadius * 2)), with: .radialGradient(
                Gradient(stops: [.init(color: Palette.warm.opacity(0.30), location: 0),
                                 .init(color: Palette.warm.opacity(0.08), location: 0.6),
                                 .init(color: Palette.warm.opacity(0), location: 1)]),
                center: centre, startRadius: 0, endRadius: radius
            ))
        }

        func point(_ s: Double, _ q: Double) -> CGPoint {
            bandPoint(centre: centre, radius: radius, angle: frame.angle, half: half,
                      inward: inward, bulge: bulge, s: s, q: q)
        }
        var rim = Path()
        for i in 0...160 {
            let phi = frame.angle + half + (2 * .pi - 2 * half) * Double(i) / 160
            let pt = CGPoint(x: centre.x + cos(phi) * radius, y: centre.y + sin(phi) * radius)
            if i == 0 { rim.move(to: pt) } else { rim.addLine(to: pt) }
        }
        for edge in 0..<2 {
            for i in 0...60 {
                let pt = point(Double(i) / 60, Double(edge))
                if i == 0 { rim.move(to: pt) } else { rim.addLine(to: pt) }
            }
        }
        context.stroke(rim, with: .color(Palette.cream), style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))

        let show = Self.smooth(p / 0.25)
        if show > 0.01 {
            var weave = Path()
            for k in 1..<5 {
                let base = Double(k) / 5
                for i in 0...60 {
                    let s = Double(i) / 60
                    let q = Self.clamp(base + 0.17 * sin(.pi * 3 * s + Double(k) * 1.7 + frame.phase))
                    let pt = point(s, q)
                    if i == 0 { weave.move(to: pt) } else { weave.addLine(to: pt) }
                }
            }
            for j in 0..<8 {
                let s0 = (Double(j) + 0.5) / 8
                for i in 0...20 {
                    let q = Double(i) / 20
                    let s = s0 + 0.035 * sin(.pi * 2 * q + Double(j) * 1.1 + frame.phase)
                    let pt = point(s, q)
                    if i == 0 { weave.move(to: pt) } else { weave.addLine(to: pt) }
                }
            }
            context.stroke(weave, with: .color(Palette.cream.opacity(0.85 * show)), style: StrokeStyle(lineWidth: 1.2, lineCap: .round))
        }

        let beadRadius = radius - Self.beadGap - inward
        let bead = CGPoint(x: centre.x + cos(frame.angle) * beadRadius, y: centre.y + sin(frame.angle) * beadRadius)
        context.drawLayer { glow in
            glow.opacity = 0.45 + 0.35 * breath
            glow.addFilter(.blur(radius: 10 + 8 * breath))
            glow.fill(Path(ellipseIn: CGRect(x: bead.x - 10, y: bead.y - 10, width: 20, height: 20)), with: .color(Palette.bead))
        }
        context.fill(Path(ellipseIn: CGRect(x: bead.x - 7, y: bead.y - 7, width: 14, height: 14)), with: .color(Palette.bead))
    }
}
