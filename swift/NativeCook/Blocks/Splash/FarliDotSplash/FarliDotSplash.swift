import SwiftUI
import UIKit

/// A wordmark becomes a falling dot, then launches an aperture into a travel feed.
struct FarliDotSplash: View {
    static let loop = 8.0
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var pausedAt: Date?
    @State private var heldTime = 0.0
    @State private var releasedAt: Date?
    @State private var touching = false
    @State private var launchOnRelease = false
    @State private var passedLaunch = false
    @State private var didScrub = false
    @State private var homeImage: Image?

    private func phase(_ now: Date) -> Double {
        if let previewTime { return max(0, previewTime).truncatingRemainder(dividingBy: Self.loop) }
        if reducedMotion { return 4.8 }
        if touching { return heldTime }
        if let releasedAt {
            let elapsed = max(0, now.timeIntervalSince(releasedAt))
            let held = launchOnRelease ? min(4.1, 2.45 + elapsed) : heldTime
            if elapsed < 2 { return held }
            if elapsed < 2.6 { return held * (1 - Self.smooth((elapsed - 2) / 0.6)) }
            return autoPlay ? (elapsed - 2.6).truncatingRemainder(dividingBy: Self.loop) : 0
        }
        return autoPlay ? max(0, now.timeIntervalSince(epoch)).truncatingRemainder(dividingBy: Self.loop) : 0
    }

    var body: some View {
        GeometryReader { geometry in
            TimelineView(.animation(paused: reducedMotion || previewTime != nil || scenePhase != .active || (!autoPlay && releasedAt == nil && !touching))) { clock in
                Canvas { context, size in
                    let t = phase(clock.date)
                    context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Self.orange))
                    let scale = min(size.width / 393, size.height / 852)
                    context.translateBy(x: (size.width - 393 * scale) / 2, y: (size.height - 852 * scale) / 2)
                    context.scaleBy(x: scale, y: scale)
                    Self.draw(context, t: t, home: homeImage)
                }
            }
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .onChanged { value in
                    guard !reducedMotion, previewTime == nil else { return }
                    if !touching {
                        heldTime = phase(Date())
                        releasedAt = nil
                        launchOnRelease = false
                        touching = true
                        passedLaunch = false
                        didScrub = false
                    }
                    if hypot(value.translation.width, value.translation.height) > 8 {
                        didScrub = true
                        heldTime = min(7.3, max(0, value.location.x / geometry.size.width * 7.3))
                        if heldTime >= 2.82 && !passedLaunch {
                            passedLaunch = true
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        }
                    }
                }
                .onEnded { value in
                    guard !reducedMotion, previewTime == nil else { return }
                    launchOnRelease = !didScrub
                    if launchOnRelease { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
                    touching = false
                    releasedAt = Date()
                })
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(reducedMotion ? "Farli travel home. Find your somewhere." : "Farli splash and travel preview.")
            .accessibilityValue("The quiet side of everywhere. Explore the valley. A little closer: Amber Canyon, 12 local adventures. Moss Coast, 8 slow escapes.")
            .accessibilityHint(reducedMotion ? "" : "Drag to scrub the splash, or tap to launch the travel feed.")
            .accessibilityIdentifier("farli-dot-splash")
            .accessibilityActions {
                if !reducedMotion {
                    Button("Launch travel feed") { launchOnRelease = true; releasedAt = Date() }
                }
            }
            .onAppear { if homeImage == nil { homeImage = Self.renderHome() } }
            .onChange(of: scenePhase) { old, next in
                if old == .active && next != .active { pausedAt = Date() }
                if next == .active, let pauseStart = pausedAt {
                    let duration = Date().timeIntervalSince(pauseStart)
                    epoch = epoch.addingTimeInterval(duration)
                    if let release = releasedAt { releasedAt = release.addingTimeInterval(duration) }
                    pausedAt = nil
                }
            }
            .task(id: releasedAt) {
                guard let release = releasedAt, scenePhase == .active else { return }
                let remaining = max(0, 2.6 - Date().timeIntervalSince(release))
                try? await Task.sleep(for: .seconds(remaining))
                guard !Task.isCancelled, scenePhase == .active, releasedAt == release else { return }
                heldTime = 0; launchOnRelease = false; releasedAt = nil; epoch = Date()
            }
        }
        .ignoresSafeArea()
        .preferredColorScheme(.light)
    }

    private static let orange = color(0xF04A2D)
    private static let paper = color(0xFFFDF7)
    private static let ink = color(0x222B28)
    private static let secondary = color(0x6E7770)
    private static func color(_ hex: UInt32) -> Color {
        Color(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255)
    }
    private static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    private static func smooth(_ x: Double) -> Double { let u = clamp(x); return u * u * (3 - 2 * u) }
    private static func out(_ x: Double) -> Double { let u = 1 - clamp(x); return 1 - u * u * u }
    private static func mix(_ a: Double, _ b: Double, _ u: Double) -> Double { a + (b - a) * u }
    private static func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double = 0) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r)
    }
    private static func text(_ value: String, _ x: Double, _ y: Double, _ size: Double, _ color: Color, _ weight: Font.Weight = .regular, anchor: UnitPoint = .leading, context: GraphicsContext) {
        context.draw(Text(value).font(.system(size: size, weight: weight)).foregroundColor(color), at: CGPoint(x: x, y: y), anchor: anchor)
    }
    private static let glyphs = Array("FARLI").map(String.init)
    private static let glyphWidths = glyphs.map { ($0 as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: 36, weight: .bold)]).width }
    private static let glyphSlots: [Double] = {
        let total = glyphWidths.reduce(0, +) + 6
        var x = 196.5 - total / 2
        return glyphWidths.map { width in defer { x += width + 1.5 }; return x + width / 2 }
    }()

    private static func mark(_ context: GraphicsContext, scale: Double, opacity: Double) {
        guard opacity > 0 else { return }
        var c = context
        c.opacity = opacity
        c.translateBy(x: 196.5, y: 426)
        c.scaleBy(x: scale, y: scale)
        var path = Path()
        path.move(to: CGPoint(x: -17, y: 22))
        path.addLine(to: CGPoint(x: -17, y: -11))
        path.addQuadCurve(to: CGPoint(x: -7, y: -21), control: CGPoint(x: -17, y: -21))
        path.addLine(to: CGPoint(x: 19, y: -21))
        c.stroke(path, with: .color(paper), style: StrokeStyle(lineWidth: 10, lineCap: .round, lineJoin: .round))
        var arrow = Path()
        arrow.move(to: CGPoint(x: 7, y: -33))
        arrow.addLine(to: CGPoint(x: 20, y: -21))
        arrow.addLine(to: CGPoint(x: 7, y: -9))
        c.stroke(arrow, with: .color(paper), style: StrokeStyle(lineWidth: 10, lineCap: .round, lineJoin: .round))
        c.fill(Path(ellipseIn: CGRect(x: -22, y: 13, width: 10, height: 10)), with: .color(orange))
    }

    private static func draw(_ context: GraphicsContext, t: Double, home: Image?) {
        let returning = smooth((t - 6.95) / 0.35)
        let markAlpha = t < 6.95 ? 1 - smooth((t - 0.9) / 0.45) : returning
        mark(context, scale: t < 6.95 ? 1 - 0.55 * smooth((t - 0.9) / 0.45) : mix(0.45, 1, returning), opacity: markAlpha)
        if t > 0.98 && t < 2.45 {
            for i in 0..<glyphs.count {
                let enter = out((t - 0.98 - Double(i) * 0.035) / 0.38)
                let exit = smooth((t - 1.95 - Double(i) * 0.025) / 0.39)
                var c = context
                c.opacity = enter * (1 - exit)
                c.translateBy(x: mix(196.5, glyphSlots[i], enter) * (1 - exit) + 196.5 * exit, y: 426)
                c.scaleBy(x: mix(0.65, 1, enter) * (1 - 0.82 * exit), y: mix(0.65, 1, enter) * (1 - 0.82 * exit))
                text(glyphs[i], 0, 0, 36, paper, .bold, anchor: .center, context: c)
            }
        }
        var dotY = 426.0, radius = 5.0, stretch = 1.0
        if t >= 2.45 && t < 2.82 {
            let u = clamp((t - 2.45) / 0.37)
            dotY = mix(426, 586, u * u)
            stretch = 1 + 0.7 * sin(.pi * u) - 0.28 * pow(u, 8)
        } else if t >= 2.82 && t < 3.38 {
            let u = out((t - 2.82) / 0.56)
            dotY = mix(586, 92, u)
            radius = mix(5, 11, u)
            let launch = clamp((t - 2.82) / 0.56)
            stretch = 0.72 + 0.28 * smooth(launch) + 0.6 * sin(.pi * launch)
        } else if t >= 6.5 {
            let u = smooth((t - 6.5) / 0.45)
            dotY = mix(92, 426, u)
            radius = mix(11, 5, u) * (1 - returning)
        }
        if t >= 2.25 && t < 3.38 || t >= 6.5 && t < 7.3 {
            radius *= smooth((t - 2.25) / 0.2)
            context.fill(Path(ellipseIn: CGRect(x: 196.5 - radius / sqrt(stretch), y: dotY - radius * stretch, width: 2 * radius / sqrt(stretch), height: 2 * radius * stretch)), with: .color(paper))
        }
        if t >= 3.38 && t < 6.5 {
            let open = out((t - 3.38) / 0.67) * (1 - smooth((t - 5.7) / 0.8))
            let radius = mix(11, 950, open)
            var c = context
            c.clip(to: Path(ellipseIn: CGRect(x: 196.5 - radius, y: 92 - radius, width: radius * 2, height: radius * 2)))
            c.fill(rect(-1000, -1000, 2393, 2852), with: .color(paper))
            if let home {
                c.draw(home, in: CGRect(x: 0, y: 0, width: 393, height: 852))
            } else { drawHome(c) }
        }
        var hint = context
        hint.opacity = t < 1.2 ? 1 - smooth((t - 0.9) / 0.3) : smooth((t - 7.25) / 0.3)
        text("Drag to explore", 196.5, 776, 11, paper.opacity(0.72), .medium, anchor: .center, context: hint)
    }

    @MainActor private static func renderHome() -> Image? {
        let renderer = ImageRenderer(content: Canvas { c, _ in drawHome(c) }.frame(width: 393, height: 852))
        renderer.scale = 3
        return renderer.uiImage.map { Image(uiImage: $0) }
    }

    private static func drawHome(_ c: GraphicsContext) {
        c.fill(rect(0, 0, 393, 852), with: .color(paper))
        text("farli", 24, 87, 26, ink, .bold, context: c)
        var sparkle = Path()
        sparkle.move(to: CGPoint(x: 355, y: 74)); sparkle.addQuadCurve(to: CGPoint(x: 366, y: 85), control: CGPoint(x: 355, y: 85))
        sparkle.addQuadCurve(to: CGPoint(x: 355, y: 96), control: CGPoint(x: 355, y: 85))
        sparkle.addQuadCurve(to: CGPoint(x: 344, y: 85), control: CGPoint(x: 355, y: 85))
        sparkle.addQuadCurve(to: CGPoint(x: 355, y: 74), control: CGPoint(x: 355, y: 85))
        c.fill(sparkle, with: .color(orange))
        c.fill(rect(24, 112, 345, 46, 23), with: .color(color(0xF1F2EA)))
        c.stroke(Path(ellipseIn: CGRect(x: 40, y: 128, width: 11, height: 11)), with: .color(secondary), lineWidth: 1.5)
        var search = Path(); search.move(to: CGPoint(x: 50, y: 138)); search.addLine(to: CGPoint(x: 54, y: 142))
        c.stroke(search, with: .color(secondary), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
        text("Where will you wander?", 65, 135, 13, secondary, context: c)
        text("Find your", 24, 195, 32, ink, .bold, context: c)
        text("somewhere.", 24, 232, 32, ink, .bold, context: c)
        text("Small detours. Big memories.", 24, 264, 13, secondary, context: c)
        landscape(c)
        text("A little closer", 24, 584, 21, ink, .semibold, context: c)
        text("View all", 369, 584, 11, orange, .semibold, anchor: .trailing, context: c)
        mini(c, x: 24, coast: false)
        mini(c, x: 202, coast: true)
        text("Amber Canyon", 24, 756, 14, ink, .semibold, context: c)
        text("Moss Coast", 202, 756, 14, ink, .semibold, context: c)
        text("12 local adventures", 24, 777, 10, secondary, context: c)
        text("8 slow escapes", 202, 777, 10, secondary, context: c)
        c.fill(rect(0, 794, 393, 58), with: .color(paper))
        c.fill(rect(24, 794, 345, 0.75), with: .color(color(0xE6E8E0)))
        c.stroke(Path(ellipseIn: CGRect(x: 58, y: 803, width: 14, height: 14)), with: .color(orange), lineWidth: 1.6)
        var needle = Path(); needle.move(to: CGPoint(x: 61, y: 813)); needle.addLine(to: CGPoint(x: 69, y: 807)); needle.addLine(to: CGPoint(x: 66, y: 815)); needle.closeSubpath()
        c.fill(needle, with: .color(orange))
        var heart = Path(); heart.move(to: CGPoint(x: 196.5, y: 817)); heart.addCurve(to: CGPoint(x: 189, y: 807), control1: CGPoint(x: 180, y: 807), control2: CGPoint(x: 190, y: 798)); heart.addCurve(to: CGPoint(x: 204, y: 807), control1: CGPoint(x: 197, y: 800), control2: CGPoint(x: 205, y: 800)); heart.addQuadCurve(to: CGPoint(x: 196.5, y: 817), control: CGPoint(x: 207, y: 811))
        c.stroke(heart, with: .color(secondary), style: StrokeStyle(lineWidth: 1.5, lineJoin: .round))
        c.stroke(rect(321, 803, 14, 14, 3), with: .color(secondary), lineWidth: 1.5)
        c.stroke(rect(325, 800, 6, 5, 2), with: .color(secondary), lineWidth: 1.5)
    }

    // Local photographs are decoded once, then flattened into the cached home image.
    private static let photos: [Image?] = ["hero", "canyon", "coast"].map { name in
        guard let url = Bundle.main.url(forResource: "FarliDotSplash-\(name)", withExtension: "jpg"),
              let image = UIImage(contentsOfFile: url.path) else { return nil }
        return Image(uiImage: image.preparingForDisplay() ?? image)
    }

    private static func landscape(_ context: GraphicsContext) {
        var c = context
        c.clip(to: rect(24, 286, 345, 262, 22))
        if let photo = photos[0] { c.draw(photo, in: CGRect(x: 24, y: 286, width: 345, height: 262)) }
        c.fill(rect(24, 286, 345, 96), with: .linearGradient(Gradient(colors: [.black.opacity(0.22), .clear]), startPoint: CGPoint(x: 24, y: 286), endPoint: CGPoint(x: 24, y: 382)))
        c.fill(rect(24, 386, 345, 162), with: .linearGradient(Gradient(colors: [.clear, color(0x173E36).opacity(0.78)]), startPoint: CGPoint(x: 24, y: 386), endPoint: CGPoint(x: 24, y: 548)))
        text("THE LONG WAY HOME", 43, 310, 9, paper, .semibold, context: c)
        text("The quiet side", 43, 474, 24, paper, .bold, context: c)
        text("of everywhere.", 43, 502, 24, paper, .bold, context: c)
        text("Explore the valley  ↗", 43, 530, 11, paper.opacity(0.92), .medium, context: c)
    }

    private static func mini(_ context: GraphicsContext, x: Double, coast: Bool) {
        var c = context
        c.clip(to: rect(x, 610, 167, 127, 16))
        if let photo = photos[coast ? 2 : 1] { c.draw(photo, in: CGRect(x: x, y: 610, width: 167, height: 127)) }
        c.fill(Path(ellipseIn: CGRect(x: x + 133, y: 620, width: 24, height: 24)), with: .color(paper.opacity(0.95)))
        text("↗", x + 145, 632, 15, ink, .medium, anchor: .center, context: c)
    }
}
