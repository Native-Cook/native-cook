import CoreText
import SwiftUI
import UIKit

// The redesigned habit picker: six glossy 120-ray icons, focus-tinted screen,
// Bricolage Headline names and question, and an icon strip above the deep-colour action.
// Geometry, palette, fonts and clock match the React Native reference.
// Loop (7.2 s): six 1.2 s steps, each holding 0.55 s then morphing for 0.65 s
// with ease-in-out cubic. Each morph turns 2π and scales by 1 − 0.22 sin(πu).
// Dragging uses 90 pt per icon; release snaps in 0.35 s, holds for 2 s, then
// resumes the loop from the selected icon. Reduced motion freezes the icon.
struct SpinMorphIcons: View {
    static let loop: Double = 7.2

    var step = "STEP 2 OF 3"
    var question = "What do you want\nto build first?"
    var action = "Choose this focus"
    var autoPlay = false

    private static let stepDuration = 1.2
    private static let morphStart = 0.55
    private static let rays = 120
    private static let iconSize = 96.0
    private static let miniSize = 13.0
    private static let perIcon = 90.0
    private static let snap = 0.35
    private static let hold = 2.0

    private struct Focus {
        var name: String
        var line: String
        var color: UInt32
        var deep: UInt32
        var tint: UInt32
    }

    private static let focuses = [
        Focus(name: "Hydrate", line: "Eight glasses, one at a time.", color: 0x19B5AE, deep: 0x0B5D59, tint: 0xDDF4F2),
        Focus(name: "Grow", line: "Ten minutes of something new.", color: 0x3DB66A, deep: 0x1D5E35, tint: 0xE2F3E7),
        Focus(name: "Energy", line: "Move before you sit down.", color: 0xFF9F1C, deep: 0x8A4B00, tint: 0xFFF0DA),
        Focus(name: "Rest", line: "Lights out by eleven.", color: 0x8F7BF2, deep: 0x3F2F9C, tint: 0xECE8FD),
        Focus(name: "Care", line: "Check in with someone you love.", color: 0xFF5A64, deep: 0x9C1C27, tint: 0xFFE5E7),
        Focus(name: "Focus", line: "One task, phone face down.", color: 0x3D7BFF, deep: 0x153D9C, tint: 0xE2EBFF)
    ]

    private enum Typeface {
        static let headline = "BricolageHeadline-Regular"
        static let body = "Inter-Medium"
        static let registered: Void = {
            for name in ["BricolageHeadline", "Inter-Medium"] {
                if let url = Bundle.main.url(forResource: "SpinMorphIcons-\(name)", withExtension: "ttf") {
                    CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
                }
            }
        }()

        static func font(_ name: String, size: CGFloat) -> UIFont {
            _ = registered
            return UIFont(name: name, size: size) ?? .systemFont(ofSize: size)
        }
    }

    // Match RN's TextKit layout: no font leading or text-container padding, fixed
    // paragraph line heights and a baseline offset only when leading is positive.
    private struct NativeText: UIViewRepresentable {
        var text: String
        var face: String
        var size: CGFloat
        var color: UIColor
        var lineHeight: CGFloat? = nil
        var tracking: CGFloat = 0
        var alignment: NSTextAlignment = .left
        var centerVertically = false

        final class TextView: UIView {
            let storage = NSTextStorage()
            let layout = NSLayoutManager()
            let container = NSTextContainer(size: .zero)
            var centerVertically = false

            override init(frame: CGRect) {
                super.init(frame: frame)
                isOpaque = false
                backgroundColor = .clear
                isAccessibilityElement = true
                container.lineFragmentPadding = 0
                container.lineBreakMode = .byClipping
                layout.usesFontLeading = false
                layout.addTextContainer(container)
                storage.addLayoutManager(layout)
            }

            required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

            override func layoutSubviews() {
                super.layoutSubviews()
                container.size = bounds.size
                setNeedsDisplay()
            }

            override func draw(_ rect: CGRect) {
                layout.ensureLayout(for: container)
                let range = layout.glyphRange(for: container)
                let y = centerVertically ? (bounds.height - layout.usedRect(for: container).height) / 2 : 0
                layout.drawGlyphs(forGlyphRange: range, at: CGPoint(x: 0, y: y))
            }
        }

        func makeUIView(context: Context) -> TextView { TextView(frame: .zero) }

        func updateUIView(_ view: TextView, context: Context) {
            let font = Typeface.font(face, size: size)
            let paragraph = NSMutableParagraphStyle()
            paragraph.alignment = alignment
            var attributes: [NSAttributedString.Key: Any] = [
                .font: font, .foregroundColor: color, .kern: tracking,
                .paragraphStyle: paragraph
            ]
            if let lineHeight {
                paragraph.minimumLineHeight = lineHeight
                paragraph.maximumLineHeight = lineHeight
                if lineHeight >= font.lineHeight {
                    attributes[.baselineOffset] = (lineHeight - font.lineHeight) / 2
                }
            }
            let value = NSAttributedString(string: text, attributes: attributes)
            if !view.storage.isEqual(to: value) {
                view.storage.setAttributedString(value)
                view.setNeedsDisplay()
            }
            view.centerVertically = centerVertically
            view.accessibilityLabel = text
        }
    }

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.displayScale) private var displayScale
    @Environment(\.scenePhase) private var scenePhase
    @GestureState private var gestureTouching = false
    // Only events change state. Every drawing is sampled from this pausable clock.
    @State private var accumulated = 0.0
    @State private var anchor = Date()
    @State private var running = false
    @State private var origin = 0.0
    @State private var frozenIndex = 0.0
    @State private var touching = false
    @State private var manual = false
    @State private var activated = false
    @State private var grabbed = 0.0
    @State private var dragged = 0.0
    @State private var released = 0.0
    @State private var releaseFrom = 0.0
    @State private var snapTo = 0.0
    @State private var lastStep = 0
    @State private var touchID = 0
    @State private var haptic = UISelectionFeedbackGenerator()

    private struct SettleKey: Equatable {
        var touchID: Int
        var running: Bool
    }

    private static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    private static func cubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    private static func smooth(_ x: Double) -> Double {
        let u = clamp01(x)
        return u * u * (3 - 2 * u)
    }
    private static func wrap(_ index: Double) -> Double {
        let n = Double(focuses.count)
        return (index.truncatingRemainder(dividingBy: n) + n).truncatingRemainder(dividingBy: n)
    }
    private static func rounded(_ index: Double) -> Double {
        // JavaScript's Math.round rounds negative half values toward positive infinity.
        floor(index + 0.5)
    }
    private static func indexAt(_ t: Double) -> Double {
        let k = floor(t / stepDuration)
        let local = t - k * stepDuration
        return k + cubic(clamp01((local - morphStart) / (stepDuration - morphStart)))
    }

    private func elapsed(at date: Date) -> Double {
        accumulated + (running ? max(0, date.timeIntervalSince(anchor)) : 0)
    }

    private func index(at now: Double) -> Double {
        if touching { return dragged }
        if manual {
            let since = max(0, now - released)
            if since < Self.snap {
                return releaseFrom + (snapTo - releaseFrom) * Self.cubic(since / Self.snap)
            }
            if since <= Self.snap + Self.hold { return snapTo }
            let resumed = Self.wrap(snapTo) * Self.stepDuration + since - Self.snap - Self.hold
            return autoPlay ? Self.indexAt(resumed.truncatingRemainder(dividingBy: Self.loop)) : 0
        }
        let t = max(0, now - origin).truncatingRemainder(dividingBy: Self.loop)
        return autoPlay ? Self.indexAt(t) : 0
    }

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + safe.leading + safe.trailing,
                              height: geo.size.height + safe.top + safe.bottom)
            let center = CGPoint(x: size.width / 2, y: size.height * 0.44)
            let labelTop = center.y + 150
            let footerTop = size.height - safe.bottom - 16 - 56
            let stripY = footerTop - 46
            let footerBounds = CGRect(x: 24, y: footerTop, width: size.width - 48,
                                      height: size.height - footerTop)
            TimelineView(.animation(paused: !running || touching || (!autoPlay && !manual))) { frame in
                let value = reduced ? frozenIndex : index(at: elapsed(at: frame.date))
                let deep = Self.blend(value, key: \.deep)
                ZStack(alignment: .topLeading) {
                    Self.color(Self.blend(value, key: \.tint))

                    header(width: size.width - 48, color: Self.uiColor(deep))
                        .offset(x: 24, y: safe.top + 14)
                        .allowsHitTesting(false)

                    Canvas { context, _ in
                        drawIcon(context, center: center, index: value)
                        drawStrip(context, index: value, centerX: center.x, y: stripY)
                    }
                    .accessibilityLabel("Focus icon. Swipe to change.")
                    .accessibilityValue(Self.focuses[Int(Self.wrap(Self.rounded(value)))].name)

                    labels(index: value, width: size.width - 40)
                        .offset(x: 20, y: labelTop)
                        .allowsHitTesting(false)

                    NativeText(text: action, face: Typeface.body, size: 17,
                               color: .white, alignment: .center, centerVertically: true)
                        .frame(width: size.width - 48, height: 56)
                        .background(Self.color(deep), in: Capsule())
                        .offset(x: 24, y: footerTop)
                        .accessibilityAddTraits(.isButton)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(pan(excluding: footerBounds))
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear {
            accumulated = 0
            origin = 0
            anchor = .now
            running = scenePhase == .active && !reduced
            haptic.prepare()
        }
        .onChange(of: scenePhase) { _, value in setClockRunning(value == .active && !reduced) }
        .onChange(of: reduced) { _, value in
            if value { frozenIndex = index(at: elapsed(at: .now)) }
            setClockRunning(scenePhase == .active && !value)
        }
        .onChange(of: gestureTouching) { _, value in if !value { endDrag() } }
        .task(id: SettleKey(touchID: touchID, running: running)) { await settle() }
    }

    private func header(width: CGFloat, color: UIColor) -> some View {
        let stepHeight = ceil(Typeface.font(Typeface.body, size: 12).lineHeight * displayScale) / displayScale
        return VStack(alignment: .leading, spacing: 10) {
            NativeText(text: step, face: Typeface.body, size: 12,
                       color: color.withAlphaComponent(0.7), tracking: 2)
                .frame(height: stepHeight)
            NativeText(text: question, face: Typeface.headline, size: 32,
                       color: color, lineHeight: 36, tracking: -0.6)
                .frame(height: 72)
        }
        .frame(width: width, alignment: .topLeading)
    }

    private func labels(index value: Double, width: CGFloat) -> some View {
        ZStack(alignment: .top) {
            ForEach(Self.focuses.indices, id: \.self) { k in
                let focus = Self.focuses[k]
                let distance = abs(Self.wrap(value) - Double(k))
                let d = min(distance, Double(Self.focuses.count) - distance)
                let opacity = 1 - Self.smooth(d / 0.45)
                VStack(spacing: 2) {
                    NativeText(text: focus.name, face: Typeface.headline, size: 64,
                               color: Self.uiColor(focus.deep), lineHeight: 72,
                               tracking: -1.5, alignment: .center)
                        .frame(height: 72)
                    NativeText(text: focus.line, face: Typeface.body, size: 16,
                               color: Self.uiColor(focus.deep).withAlphaComponent(0.8),
                               lineHeight: 22, alignment: .center)
                        .frame(height: 22)
                }
                .frame(width: width)
                .scaleEffect(0.94 + 0.06 * opacity)
                .offset(y: 14 * (1 - opacity))
                .opacity(opacity)
                .accessibilityHidden(opacity < 0.5)
            }
        }
        .frame(width: width, height: 100, alignment: .top)
    }

    private static func outline(index: Double, scale: Double, spin: Double, center: CGPoint) -> Path {
        let base = floor(index)
        let u = index - base
        let a = profiles[Int(wrap(base))]
        let b = profiles[Int(wrap(base + 1))]
        var path = Path()
        for k in 0..<rays {
            let r = (a[k] * (1 - u) + b[k] * u) * scale
            let angle = Double(k) / Double(rays) * .pi * 2 - .pi / 2 + spin
            let point = CGPoint(x: center.x + cos(angle) * r, y: center.y + sin(angle) * r)
            if k == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath()
        return path
    }

    // Skia's BlurStyle.Solid retains the opaque source inside its blurred silhouette.
    // Composite that union first, then apply alpha once to the whole layer.
    private static func solidBlur(_ path: Path, color: Color, radius: Double, context: GraphicsContext) {
        context.drawLayer { layer in
            var blur = layer
            blur.addFilter(.blur(radius: radius))
            blur.fill(path, with: .color(color))
            layer.fill(path, with: .color(color))
        }
    }

    private func drawIcon(_ context: GraphicsContext, center: CGPoint, index: Double) {
        let u = index - floor(index)
        let scale = Self.iconSize * (1 - 0.22 * sin(.pi * u))
        let path = Self.outline(index: index, scale: scale, spin: .pi * 2 * u, center: center)
        let color = Self.blend(index, key: \.color)
        var shadow = context
        shadow.translateBy(x: 0, y: 18)
        shadow.opacity = 0.32
        Self.solidBlur(path, color: Self.color(Self.blend(index, key: \.deep)), radius: 18, context: shadow)

        let gradient = Gradient(stops: [
            .init(color: Self.color(Self.mixColor(color, 0xFFFFFF, 0.35)), location: 0),
            .init(color: Self.color(color), location: 0.5),
            .init(color: Self.color(Self.mixColor(color, 0x000000, 0.18)), location: 1)
        ])
        context.fill(path, with: .linearGradient(gradient,
            startPoint: CGPoint(x: center.x - Self.iconSize, y: center.y - Self.iconSize),
            endPoint: CGPoint(x: center.x + Self.iconSize, y: center.y + Self.iconSize)))

        var shine = context
        shine.clip(to: path)
        shine.opacity = 0.32
        let ellipse = Path(ellipseIn: CGRect(x: center.x - Self.iconSize * 0.75,
                                           y: center.y - Self.iconSize * 0.85,
                                           width: Self.iconSize * 0.9, height: Self.iconSize * 0.55))
        Self.solidBlur(ellipse, color: .white, radius: 14, context: shine)
    }

    private func drawStrip(_ context: GraphicsContext, index: Double, centerX: Double, y: Double) {
        for k in Self.focuses.indices {
            let focus = Self.focuses[k]
            let distance = abs(Self.wrap(index) - Double(k))
            let d = min(distance, Double(Self.focuses.count) - distance)
            let selected = 1 - Self.smooth(d / 0.6)
            let center = CGPoint(x: centerX + (Double(k) - 2.5) * 50, y: y)
            if selected > 0.01 {
                let radius = 23 * (0.7 + 0.3 * selected)
                context.fill(Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius,
                                                    width: 2 * radius, height: 2 * radius)),
                             with: .color(.white.opacity(selected)))
            }
            let path = Self.outline(index: Double(k), scale: Self.miniSize * (1 + 0.35 * selected),
                                    spin: 0, center: center)
            context.fill(path, with: .color(Self.color(selected > 0.5 ? focus.color : focus.deep)
                .opacity(0.3 + 0.7 * selected)))
        }
    }

    private static func mixColor(_ a: UInt32, _ b: UInt32, _ u: Double) -> UInt32 {
        func channel(_ shift: UInt32) -> UInt32 {
            let x = Double((a >> shift) & 255)
            let y = Double((b >> shift) & 255)
            return UInt32(floor(x * (1 - u) + y * u + 0.5))
        }
        return (channel(16) << 16) | (channel(8) << 8) | channel(0)
    }

    private static func blend(_ index: Double, key: KeyPath<Focus, UInt32>) -> UInt32 {
        let base = floor(index)
        return mixColor(focuses[Int(wrap(base))][keyPath: key],
                        focuses[Int(wrap(base + 1))][keyPath: key], index - base)
    }

    private static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: 1)
    }

    private static func uiColor(_ hex: UInt32) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 255) / 255,
                green: CGFloat((hex >> 8) & 255) / 255, blue: CGFloat(hex & 255) / 255, alpha: 1)
    }

    private func pan(excluding footer: CGRect) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureTouching) { _, value, _ in value = true }
            .onChanged { value in
                guard !reduced, !footer.contains(value.startLocation) else { return }
                if !touching {
                    let current = index(at: elapsed(at: .now))
                    grabbed = Self.rounded(current * 100) / 100
                    dragged = current
                    lastStep = Int(Self.rounded(current))
                    touching = true
                    manual = true
                    activated = false
                    touchID += 1
                }
                // RN begins on touch-down, and updates only after minDistance = 4.
                if hypot(value.translation.width, value.translation.height) >= 4 { activated = true }
                guard activated else { return }
                dragged = grabbed - value.translation.width / Self.perIcon
                let step = Int(Self.rounded(dragged))
                if step != lastStep {
                    lastStep = step
                    haptic.selectionChanged()
                }
            }
            .onEnded { _ in endDrag() }
    }

    private func endDrag() {
        guard touching else { return }
        releaseFrom = dragged
        snapTo = Self.rounded(dragged)
        released = elapsed(at: .now)
        touching = false
        activated = false
        touchID += 1
    }

    private func setClockRunning(_ active: Bool) {
        guard active != running else { return }
        if active {
            anchor = .now
            running = true
        } else {
            endDrag()
            accumulated = elapsed(at: .now)
            running = false
        }
    }

    private func settle() async {
        guard manual, !touching, running else { return }
        let restart = released + Self.snap + Self.hold
        try? await Task.sleep(for: .seconds(max(0, restart - elapsed(at: .now))))
        guard !Task.isCancelled, !touching, running else { return }
        origin = restart - Self.wrap(snapTo) * Self.stepDuration
        manual = false
    }

    // The static radii are ray-cast and smoothed exactly once, never during a frame.
    private static let profiles: [[Double]] = {
        let drop = polygonProfile(sampleCurve { t in
            CGPoint(x: 0.82 * sin(t) * sin(t / 2), y: -0.98 * cos(t) + 0.05)
        }, blur: 1)

        let c = 0.634
        let radius = 1.184
        let half = atan2(1.0, c)
        var leafPoints: [CGPoint] = []
        for i in 0...60 {
            let a = -half + 2 * half * Double(i) / 60
            leafPoints.append(CGPoint(x: -c + radius * cos(a), y: -radius * sin(a)))
        }
        for i in 0...60 {
            let a = .pi - half + 2 * half * Double(i) / 60
            leafPoints.append(CGPoint(x: c + radius * cos(a), y: -radius * sin(a)))
        }
        let k = sqrt(0.5)
        let leaf = polygonProfile(leafPoints.map { p in
            CGPoint(x: (p.x * k - p.y * k) * 0.95, y: (p.x * k + p.y * k) * 0.95)
        }, blur: 1)

        let sun = profile(blur: 0) { a in 0.7 + 0.2 * pow(max(0, cos(8 * a)), 2) }
        let cloud = profile(blur: 1) { a in
            let dx = cos(a)
            let dy = sin(a)
            let puffs = [(-0.5, 0.12, 0.42), (0.02, -0.16, 0.58),
                         (0.52, 0.14, 0.4), (0.0, 0.2, 0.42)]
            var best = 0.0
            for (x, y, r) in puffs {
                let b = dx * x + dy * y
                let disc = b * b - (x * x + y * y - r * r)
                if disc >= 0 { best = max(best, b + sqrt(disc)) }
            }
            if dy > 0 { best = min(best, 0.54 / dy) }
            return best
        }
        let heart = polygonProfile(sampleCurve { t in
            let x = 16 * pow(sin(t), 3)
            let y = 13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)
            return CGPoint(x: x / 17, y: -y / 17 - 0.1)
        }, blur: 1)
        let focus = polygonProfile([CGPoint(x: 0, y: -0.95), CGPoint(x: 0.95, y: 0.68),
                                    CGPoint(x: -0.95, y: 0.68)], blur: 4)
        return [drop, leaf, sun, cloud, heart, focus]
    }()

    private static func sampleCurve(_ curve: (Double) -> CGPoint) -> [CGPoint] {
        (0..<240).map { curve(Double($0) / 240 * .pi * 2) }
    }

    private static func castPolygon(_ points: [CGPoint], angle: Double) -> Double {
        let dx = cos(angle)
        let dy = sin(angle)
        var best = 0.0
        for i in points.indices {
            let p = points[i]
            let q = points[(i + 1) % points.count]
            let ex = q.x - p.x
            let ey = q.y - p.y
            let den = dx * ey - dy * ex
            if abs(den) < 1e-9 { continue }
            let t = (p.x * ey - p.y * ex) / den
            let s = (p.x * dy - p.y * dx) / den
            if t > 0, s >= 0, s <= 1 { best = max(best, t) }
        }
        return best
    }

    private static func profile(blur: Int, radius: (Double) -> Double) -> [Double] {
        let raw = (0..<rays).map { radius(Double($0) / Double(rays) * .pi * 2 - .pi / 2) }
        guard blur > 0 else { return raw }
        return (0..<rays).map { i in
            var sum = 0.0
            for k in -blur...blur { sum += raw[(i + k + rays) % rays] }
            return sum / Double(2 * blur + 1)
        }
    }

    private static func polygonProfile(_ points: [CGPoint], blur: Int) -> [Double] {
        profile(blur: blur) { castPolygon(points, angle: $0) }
    }
}

#Preview {
    SpinMorphIcons(autoPlay: true)
}
