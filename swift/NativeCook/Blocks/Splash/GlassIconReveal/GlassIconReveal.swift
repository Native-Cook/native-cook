import SwiftUI
import UIKit

// Stride's launch card, faithfully ported from the React Native reference.
// 7 s loop: clear glass 0–1.2; corner flood 1.2–2.6 (0→300 pt, cubic in/out);
// runner trim 1.8–3.0 (0→1, cubic in/out); head 2.9–3.3 (back out, k=1.8);
// glow 2.4–3.4 (cubic in/out); sheen 4.2–5.0; drain 5.8–6.6 (cubic in/out).
// A tap restarts at 1.2 s, exactly as RN; reduced motion starts at the lit 4 s pose.
struct GlassIconReveal: View {
    static let loop: Double = 7
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    @State private var appeared = false
    @State private var epoch = Date()
    @State private var accumulated = 0.0
    @State private var loopOrigin = 0.0
    @State private var retainedClock = 4.0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(width: geometry.size.width + safe.leading + safe.trailing,
                              height: geometry.size.height + safe.top + safe.bottom)
            TimelineView(.animation(paused: !appeared || scene != .active || reduced || !autoPlay)) { timeline in
                let frame = GlassIconRevealMotion.Frame(time: clock(at: timeline.date))
                Canvas(opaque: true, colorMode: .nonLinear) { context, _ in
                    GlassIconRevealArt.draw(context, size: size, safe: safe, frame: frame)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Now available. Stride · Running coach. Five stars. 4.9 · 12k ratings. Couch to 5K in eight weeks, at your own pace.")
                .overlay(alignment: .topLeading) {
                    Color.clear
                        .frame(width: 200, height: 200)
                        .contentShape(Rectangle())
                        .onTapGesture { lightIcon() }
                        .accessibilityLabel("Stride app icon. Tap to light it.")
                        .accessibilityAddTraits(.isButton)
                        .accessibilityAction { lightIcon() }
                        .offset(x: size.width / 2 - 100, y: size.height * 0.47 - 100)
                }
                .overlay(alignment: .bottom) {
                    Color.clear.frame(height: 58)
                        .padding(.horizontal, 24)
                        .padding(.bottom, safe.bottom + 14 + 12 + GlassIconRevealType.note.size.height)
                        .accessibilityLabel("Get it free")
                        .accessibilityAddTraits(.isButton)
                        .allowsHitTesting(false)
                }
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .background(GlassIconRevealArt.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            _ = GlassIconRevealType.title
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
                retainedClock = clock(at: now, ignoringReduced: true)
                accumulated = elapsed(at: now, ignoringReduced: true)
            }
            epoch = now
        }
    }

    private func elapsed(at date: Date, ignoringReduced: Bool = false) -> Double {
        accumulated + (appeared && scene == .active && (!reduced || ignoringReduced)
            ? max(0, date.timeIntervalSince(epoch)) : 0)
    }

    private func clock(at date: Date, ignoringReduced: Bool = false) -> Double {
        if reduced && !ignoringReduced { return retainedClock }
        guard autoPlay else { return 4 }
        return GlassIconRevealMotion.clock(elapsed(at: date, ignoringReduced: ignoringReduced) - loopOrigin)
    }

    private func lightIcon() {
        guard autoPlay && !reduced else { return }
        loopOrigin = elapsed(at: .now) - 1.2
        haptic.impactOccurred()
        haptic.prepare()
    }
}

private enum GlassIconRevealMotion {
    static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double {
        min(1, max(0, (t - a) / (b - a)))
    }
    static func cubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func back(_ x: Double) -> Double {
        1 + 2.8 * pow(x - 1, 3) + 1.8 * pow(x - 1, 2)
    }
    static func clock(_ t: Double) -> Double {
        (t.truncatingRemainder(dividingBy: GlassIconReveal.loop) + GlassIconReveal.loop)
            .truncatingRemainder(dividingBy: GlassIconReveal.loop)
    }
    struct Frame {
        let drain: Double
        let flood: Double
        let trim: Double
        let head: Double
        let glow: Double
        let sheen: Double
        let sheenOpacity: Double

        init(time t: Double) {
            drain = 1 - cubic(segment(t, 5.8, 6.6))
            flood = 300 * cubic(segment(t, 1.2, 2.6)) * drain
            trim = cubic(segment(t, 1.8, 3))
            head = 11 * max(0, back(segment(t, 2.9, 3.3))) * drain
            glow = cubic(segment(t, 2.4, 3.4)) * drain
            sheen = segment(t, 4.2, 5)
            sheenOpacity = sheen > 0 && sheen < 1 ? 0.5 : 0
        }
    }
}

private enum GlassIconRevealArt {
    static let background = color(5, 7, 12)
    // Skia interpolates straight RGBA. Dense stops keep SwiftUI's premultiplied
    // interpolation from darkening the glass and shifting the blue rim.
    private static let glassGradient = Gradient(stops: (0...32).map { index in
        let u = Double(index) / 32
        return .init(color: color(120 - 60 * u, 140 - 65 * u, 170 - 60 * u, 0.32 + 0.08 * u), location: u)
    })
    private static let rimGradient = Gradient(stops: (0...64).map { index in
        let u = Double(index) / 64
        if u <= 0.5 { return .init(color: .white.opacity(0.55 - 0.94 * u), location: u) }
        let v = (u - 0.5) * 2
        return .init(color: color(255 - 95 * v, 255 - 65 * v, 255, 0.08 + 0.52 * v), location: u)
    })
    private static func color(_ r: Double, _ g: Double, _ b: Double, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: r / 255, green: g / 255, blue: b / 255, opacity: alpha)
    }
    private static func circle(_ x: Double, _ y: Double, _ radius: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
    }
    // Skia uses Gaussian sigma; this matches the existing ports' 3× blur calibration.
    private static func blur(_ sigma: Double) -> Double { sigma / 0.92 }

    static func draw(_ context: GraphicsContext, size: CGSize, safe: EdgeInsets, frame f: GlassIconRevealMotion.Frame) {
        let cx = size.width / 2, cy = size.height * 0.47
        let x = cx - 100, y = cy - 100
        let icon = Path(roundedRect: CGRect(x: x, y: y, width: 200, height: 200), cornerRadius: 50, style: .circular)
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(background))
        backdrop(context, size: size, frame: f)

        // RN's canvas is transparent over its View background. Its backdrop layer
        // composites the blurred translucent blobs over their original pixels.
        // Keep that layer transparent here too; an opaque fill would dim the glass.
        var glass = context
        glass.clip(to: icon)
        glass.drawLayer { layer in
            layer.addFilter(.blur(radius: blur(16)))
            backdrop(layer, size: size, frame: f)
        }
        glass.fill(icon, with: .linearGradient(glassGradient,
                   startPoint: CGPoint(x: x, y: y), endPoint: CGPoint(x: x + 200, y: y + 200)))

        var flood = context
        flood.clip(to: icon)
        flood.opacity = f.drain
        flood.drawLayer { group in
            if f.flood > 0 {
                var disc = group
                disc.addFilter(.blur(radius: blur(6)))
                disc.fill(circle(x, y + 200, f.flood), with: .linearGradient(
                    Gradient(colors: [color(106, 92, 255), color(91, 140, 255), color(98, 198, 255)]),
                    startPoint: CGPoint(x: x, y: y + 200), endPoint: CGPoint(x: x + 200, y: y)))
            }
            if f.sheenOpacity > 0 {
                var sheen = group
                sheen.opacity = f.sheenOpacity
                sheen.addFilter(.blur(radius: blur(14)))
                sheen.translateBy(x: cx, y: cy)
                sheen.rotate(by: .radians(0.35))
                sheen.translateBy(x: -cx, y: -cy)
                sheen.fill(Path(CGRect(x: x - 120 + 440 * f.sheen, y: y - 40, width: 50, height: 280)),
                           with: .color(.white.opacity(0.9)))
            }
        }

        var runner = context
        runner.opacity = f.drain
        runner.drawLayer { layer in
            var ink = layer
            ink.translateBy(x: cx - 4, y: cy + 4)
            if f.trim > 0 {
                ink.stroke(GlassIconRevealRunner.path(to: f.trim), with: .color(.white.opacity(0.95)),
                           style: StrokeStyle(lineWidth: 17, lineCap: .round, lineJoin: .round))
            }
            if f.head > 0 { layer.fill(circle(cx + 42, cy - 52, f.head), with: .color(.white)) }
        }

        let rim = Path(roundedRect: CGRect(x: x + 0.75, y: y + 0.75, width: 198.5, height: 198.5), cornerRadius: 49, style: .circular)
        context.stroke(rim, with: .linearGradient(rimGradient,
                       startPoint: CGPoint(x: x, y: y), endPoint: CGPoint(x: x + 200, y: y + 200)), lineWidth: 1.5)
        copy(context, size: size, safe: safe, glow: f.glow)
    }

    private static func backdrop(_ context: GraphicsContext, size: CGSize, frame f: GlassIconRevealMotion.Frame) {
        let cx = size.width / 2, cy = size.height * 0.47
        let x = cx - 100, y = cy - 100
        if f.glow > 0 {
            var glow = context
            glow.opacity = f.glow
            glow.fill(circle(cx, cy, 200 * (0.6 + 0.35 * f.glow)), with: .radialGradient(
                Gradient(colors: [color(80, 120, 255, 0.75), color(80, 120, 255, 0)]),
                center: CGPoint(x: cx, y: cy), startRadius: 0, endRadius: 200))
        }
        var blobs = context
        blobs.addFilter(.blur(radius: blur(30)))
        blobs.fill(circle(x + 30, y + 180, 60), with: .color(color(90, 110, 200, 0.35)))
        blobs.fill(circle(x + 170, y + 30, 50), with: .color(color(120, 140, 180, 0.25)))
    }

    private static func copy(_ context: GraphicsContext, size: CGSize, safe: EdgeInsets, glow: Double) {
        let cx = size.width / 2
        let titleTop = size.height * 0.17
        GlassIconRevealType.title.draw(context, centerX: cx, top: titleTop)
        GlassIconRevealType.name.draw(context, centerX: cx, top: titleTop + GlassIconRevealType.title.size.height + 6,
                                     opacity: 0.55 + 0.45 * glow)
        let noteTop = size.height - safe.bottom - 14 - GlassIconRevealType.note.size.height
        let buttonTop = noteTop - 12 - 58
        let ratingTop = buttonTop - 16 - GlassIconRevealType.rating.size.height
        GlassIconRevealType.rating.draw(context, centerX: cx, top: ratingTop, opacity: 0.75)
        context.fill(Path(roundedRect: CGRect(x: 24, y: buttonTop, width: size.width - 48, height: 58), cornerRadius: 29, style: .circular),
                     with: .color(.white))
        GlassIconRevealType.action.draw(context, centerX: cx, top: buttonTop + (58 - GlassIconRevealType.action.size.height) / 2)
        GlassIconRevealType.note.draw(context, centerX: cx, top: noteTop, opacity: 0.55)
    }
}

private enum GlassIconRevealType {
    static let title = Raster("Now available", size: 34, weight: .black, kern: -1)
    static let name = Raster("Stride · Running coach", size: 17, weight: .bold)
    static let rating = Raster("★★★★★  4.9 · 12k ratings", size: 15, weight: .bold)
    static let action = Raster("Get it free", size: 17, weight: .bold, ink: UIColor(red: 5 / 255, green: 7 / 255, blue: 12 / 255, alpha: 1))
    static let note = Raster("Couch to 5K in eight weeks, at your own pace.", size: 14, weight: .regular)

    struct Raster {
        let image: UIImage
        let size: CGSize
        init(_ text: String, size pointSize: CGFloat, weight: UIFont.Weight, kern: CGFloat? = nil, ink: UIColor = .white) {
            let font = UIFont.systemFont(ofSize: pointSize, weight: weight)
            var attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: ink]
            if let kern { attributes[.kern] = kern }
            let string = NSAttributedString(string: text, attributes: attributes)
            let measured = string.size()
            let scale: CGFloat = 3
            size = CGSize(width: ceil(measured.width * scale) / scale, height: ceil(font.lineHeight * scale) / scale)
            let format = UIGraphicsImageRendererFormat()
            format.scale = scale
            image = UIGraphicsImageRenderer(size: size, format: format).image { _ in string.draw(at: .zero) }
        }
        func draw(_ context: GraphicsContext, centerX: Double, top: Double, opacity: Double = 1) {
            var ink = context
            ink.opacity = opacity
            ink.draw(Image(uiImage: image), in: CGRect(x: centerX - size.width / 2, y: top, width: size.width, height: size.height))
        }
    }
}

private enum GlassIconRevealRunner {
    // Arc-length samples let the trim travel across BOTH contours, as Skia does.
    private struct Curve {
        let start: CGPoint, c1: CGPoint, c2: CGPoint, end: CGPoint
        let newContour: Bool
        let lengths: [Double]
        var length: Double { lengths.last ?? 0 }

        init(_ p: [Double], newContour: Bool = false) {
            start = CGPoint(x: p[0], y: p[1]); c1 = CGPoint(x: p[2], y: p[3])
            c2 = CGPoint(x: p[4], y: p[5]); end = CGPoint(x: p[6], y: p[7])
            self.newContour = newContour
            var samples = [0.0], previous = start, total = 0.0
            for i in 1...100 {
                let t = Double(i) / 100, u = 1 - t
                let a = u * u * u, b = 3 * u * u * t
                let c = 3 * u * t * t, d = t * t * t
                let px: Double = a * p[0] + b * p[2] + c * p[4] + d * p[6]
                let py: Double = a * p[1] + b * p[3] + c * p[5] + d * p[7]
                let point = CGPoint(x: px, y: py)
                total += hypot(point.x - previous.x, point.y - previous.y)
                samples.append(total); previous = point
            }
            lengths = samples
        }
        func parameter(at distance: Double) -> Double {
            guard let i = lengths.firstIndex(where: { $0 >= distance }), i > 0 else { return 0 }
            return (Double(i - 1) + (distance - lengths[i - 1]) / (lengths[i] - lengths[i - 1])) / 100
        }
        func append(to path: inout Path, fraction t: Double) {
            func mix(_ a: CGPoint, _ b: CGPoint) -> CGPoint {
                CGPoint(x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t)
            }
            let a = mix(start, c1), b = mix(c1, c2), c = mix(c2, end)
            let d = mix(a, b), e = mix(b, c)
            path.addCurve(to: mix(d, e), control1: a, control2: d)
        }
    }
    private static let curves = [
        Curve([-52, -30, -30, -40, 0, -42, 18, -30], newContour: true),
        Curve([18, -30, 34, -18, 14, 0, -4, 6]),
        Curve([-4, 6, 14, 14, 34, 26, 44, 46]),
        Curve([-4, 6, -18, 22, -36, 34, -54, 34], newContour: true)
    ]
    private static let total = curves.reduce(0) { $0 + $1.length }
    static func path(to trim: Double) -> Path {
        var path = Path(), remaining = total * trim
        for curve in curves {
            guard remaining > 0 else { break }
            if curve.newContour { path.move(to: curve.start) }
            curve.append(to: &path, fraction: remaining >= curve.length ? 1 : curve.parameter(at: remaining))
            remaining -= curve.length
        }
        return path
    }
}

#Preview {
    GlassIconReveal(autoPlay: true)
}
