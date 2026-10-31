import SwiftUI
import UIKit

/// Ember's launch, translated from the React Native reference.
/// 0–1: orange rest; 1–1.8: logarithmic shrink; 1.8–3: flicker and gaze.
/// 3–3.6: back-eased rise and staggered welcome; 3.6–6.5: welcome hold.
/// 6.5–7: sink and fade; 7–7.8: logarithmic swell; 7.8–8: orange rest.
/// A tap adds the reference's half-second hop while the launch clock continues.
struct FlameMascotSplash: View {
    var autoPlay = false
    static let loop: Double = 8

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var origin = Date()
    @State private var accumulated = 0.0
    @State private var hopAt = -10.0
    @State private var hopping = false
    @State private var taps = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    private typealias M = FlameMascotSplashMotion
    private typealias Art = FlameMascotSplashArt

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(width: geometry.size.width + safe.leading + safe.trailing,
                              height: geometry.size.height + safe.top + safe.bottom)
            let copy = FlameMascotSplashCopy.layout(size: size, safe: safe)
            TimelineView(.animation(paused: scenePhase != .active || reduceMotion || (!autoPlay && !hopping))) { timeline in
                let elapsed = elapsed(at: timeline.date)
                let t = autoPlay && !reduceMotion ? M.time(elapsed) : 4.5
                let age = elapsed - hopAt
                let hop = !reduceMotion && age > 0 && age < 0.5 ? 26 * sin(.pi * age / 0.5) : 0
                let stage = M.stage(at: t)
                Canvas(opaque: true, colorMode: .nonLinear) { context, canvasSize in
                    context.fill(Path(CGRect(origin: .zero, size: canvasSize)), with: .color(Art.background))
                    Art.draw(context, t: t, stage: stage, hop: hop, size: canvasSize)
                    copy.draw(context, page: stage.page)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .contentShape(Rectangle())
                .onTapGesture { sendHop() }
                .accessibilityLabel("Ember, a little flame. Welcome to Ember. Your streaks, kept warm. One small habit a day is enough to keep the fire going. I agree to the terms and the privacy notice. Send me a gentle nudge at 8 pm. Continue.")
                .accessibilityHint("Tap to make the flame hop.")
                .accessibilityAddTraits(.isButton)
                .accessibilityAction { sendHop() }
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .background(Art.background.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear {
            origin = .now
            accumulated = 0
            haptic.prepare()
        }
        .onChange(of: scenePhase) { oldPhase, newPhase in
            if oldPhase == .active && !reduceMotion {
                accumulated += max(0, Date().timeIntervalSince(origin))
            }
            if newPhase == .active { origin = .now }
        }
        .onChange(of: reduceMotion) { _, reduced in
            guard scenePhase == .active else { return }
            if reduced { accumulated += max(0, Date().timeIntervalSince(origin)) }
            origin = .now
        }
        .task(id: HopTaskID(taps: taps, phase: scenePhase, reduced: reduceMotion)) {
            guard taps > 0, hopping, scenePhase == .active, !reduceMotion else { return }
            // Resume cleanup with the same active-time remainder after a scene pause.
            let remaining = max(0, 0.5 - (elapsed(at: .now) - hopAt))
            if remaining > 0 {
                try? await Task.sleep(for: .seconds(remaining))
            }
            guard !Task.isCancelled else { return }
            hopping = false
        }
    }

    private struct HopTaskID: Equatable {
        let taps: Int
        let phase: ScenePhase
        let reduced: Bool
    }

    private func elapsed(at date: Date) -> Double {
        accumulated + (scenePhase == .active && !reduceMotion ? max(0, date.timeIntervalSince(origin)) : 0)
    }

    private func sendHop() {
        guard !reduceMotion else { return }
        hopAt = elapsed(at: .now)
        hopping = true
        taps += 1
        haptic.impactOccurred()
        haptic.prepare()
    }
}

private enum FlameMascotSplashMotion {
    struct Stage {
        var size = 3000.0
        var y = 0.5
        var page = 0.0
        var blink = 0.0
        var gaze = 0.0
    }

    static func time(_ elapsed: Double) -> Double {
        max(0, elapsed).truncatingRemainder(dividingBy: FlameMascotSplash.loop)
    }
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func easeOutBack(_ x: Double) -> Double {
        1 + 2.5 * pow(x - 1, 3) + 1.5 * pow(x - 1, 2)
    }
    static func logLerp(_ a: Double, _ b: Double, _ u: Double) -> Double {
        exp(log(a) + (log(b) - log(a)) * u)
    }

    static func stage(at t: Double) -> Stage {
        var st = Stage()
        if t >= 1 && t < 3 {
            st.size = logLerp(3000, 150, easeInOut(seg(t, 1, 1.8)))
        } else if t >= 3 && t < 6.5 {
            let u = easeOutBack(seg(t, 3, 3.6))
            st.size = 150 + 40 * u
            st.y = 0.5 - 0.16 * u
        } else if t >= 6.5 && t < 7 {
            let u = easeInOut(seg(t, 6.5, 7))
            st.size = 190 - 40 * u
            st.y = 0.34 + 0.16 * u
        } else if t >= 7 {
            st.size = logLerp(150, 3000, pow(seg(t, 7, 7.8), 3))
        }
        st.page = seg(t, 3, 3.6) * (1 - seg(t, 6.5, 6.8))
        for blinkAt in [0.55, 2.4, 4.8] {
            let x = t - blinkAt
            if x > 0 && x < 0.16 { st.blink = sin(.pi * x / 0.16) }
        }
        st.gaze = sin(.pi * 2 * seg(t, 1.9, 3)) * 0.6 + sin(.pi * 2 * seg(t, 4, 5.6)) * 0.4
        return st
    }
}

private enum FlameMascotSplashArt {
    static func color(_ hex: UInt32, alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static let background = color(0xF3F4F8)
    static let ink = color(0x1A0E08)

    private struct AuroraBlob {
        let x: Double
        let y: Double
        let radius: Double
        let gradient: Gradient
    }

    // A blurred disk is radially symmetric. Cache its Gaussian alpha profile once
    // rather than relying on the different Canvas and Skia blur kernels.
    private static let aurora: [AuroraBlob] = [
        auroraBlob(x: 0.15, y: 0.05, radius: 190, hex: 0xFFBE8C),
        auroraBlob(x: 0.75, y: 0.02, radius: 210, hex: 0xD696FF),
        auroraBlob(x: 0.95, y: 0.3, radius: 170, hex: 0xA0CDFF),
        auroraBlob(x: 0.1, y: 0.35, radius: 140, hex: 0xFFDCB4)
    ]

    private static func auroraBlob(x: Double, y: Double, radius: Double, hex: UInt32) -> AuroraBlob {
        // RN's canvas scales to physical pixels. Skia caps the transformed sigma
        // at 128 px, so a requested 70 pt becomes 128 / 3 pt on the 3x reference.
        let sigma = min(70, 128 / Double(UIScreen.main.scale))
        let extent = radius + 3 * sigma
        let samples = 256
        let dx = 2 * radius / Double(samples)
        let nodes = (0...samples).map { index -> (x: Double, weight: Double) in
            let px = -radius + Double(index) * dx
            let halfHeight = sqrt(max(0, radius * radius - px * px))
            let simpson = index == 0 || index == samples ? 1.0 : (index.isMultiple(of: 2) ? 2.0 : 4.0)
            return (px, simpson * erf(halfHeight / (sqrt(2) * sigma)))
        }
        let stops = (0...128).map { index -> Gradient.Stop in
            let u = Double(index) / 128
            let distance = extent * u
            let integral = nodes.reduce(0.0) { sum, node in
                let q = (node.x - distance) / sigma
                return sum + node.weight * exp(-0.5 * q * q)
            }
            let alpha = index == 128 ? 0 : integral * dx / (3 * sqrt(2 * .pi) * sigma)
            return .init(color: color(hex, alpha: alpha), location: u)
        }
        return AuroraBlob(x: x, y: y, radius: extent, gradient: Gradient(stops: stops))
    }

    static func flamePath(at t: Double) -> Path {
        let w = .pi * 2 / FlameMascotSplash.loop
        let a = 0.05 * sin(5 * w * t)
        let b = 0.06 * sin(7 * w * t + 1.3)
        let c = 0.05 * sin(6 * w * t + 2.1)
        let sway = 0.04 * sin(4 * w * t)
        var p = Path()
        p.move(to: CGPoint(x: 0, y: 0.66))
        p.addCurve(to: CGPoint(x: -0.5, y: 0.1), control1: CGPoint(x: -0.36, y: 0.66), control2: CGPoint(x: -0.53, y: 0.4))
        p.addCurve(to: CGPoint(x: -0.34 + sway, y: -0.4 + a), control1: CGPoint(x: -0.48, y: -0.1), control2: CGPoint(x: -0.42, y: -0.24))
        p.addCurve(to: CGPoint(x: -0.13, y: -0.24), control1: CGPoint(x: -0.26, y: -0.28), control2: CGPoint(x: -0.18, y: -0.22))
        p.addCurve(to: CGPoint(x: 0.08 + sway, y: -0.82 + b), control1: CGPoint(x: -0.12, y: -0.46), control2: CGPoint(x: -0.02, y: -0.64))
        p.addCurve(to: CGPoint(x: 0.31 + sway, y: -0.5 + c), control1: CGPoint(x: 0.12, y: -0.62), control2: CGPoint(x: 0.2, y: -0.5))
        p.addCurve(to: CGPoint(x: 0.5, y: 0.1), control1: CGPoint(x: 0.44, y: -0.34), control2: CGPoint(x: 0.53, y: -0.12))
        p.addCurve(to: CGPoint(x: 0, y: 0.66), control1: CGPoint(x: 0.53, y: 0.4), control2: CGPoint(x: 0.36, y: 0.66))
        p.closeSubpath()
        return p
    }

    static func circle(x: Double, y: Double, radius: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
    }

    static func draw(_ context: GraphicsContext, t: Double, stage st: FlameMascotSplashMotion.Stage, hop: Double, size: CGSize) {
        let width = Double(size.width)
        let height = Double(size.height)
        let cx = width / 2
        let fy = height * st.y - hop
        if st.page > 0 {
            for blob in aurora {
                var layer = context
                // Skia setAlphaf replaces the alpha supplied with each blob's color.
                layer.opacity = st.page * 0.8
                let center = CGPoint(x: width * blob.x, y: height * blob.y)
                layer.fill(circle(x: center.x, y: center.y, radius: blob.radius),
                           with: .radialGradient(blob.gradient, center: center, startRadius: 0, endRadius: blob.radius))
            }
        }

        var flame = context
        flame.translateBy(x: cx, y: fy)
        flame.scaleBy(x: st.size, y: st.size)
        flame.translateBy(x: 0, y: -0.12)
        let path = flamePath(at: t)
        flame.fill(path, with: .linearGradient(Gradient(stops: [
            .init(color: color(0xFF9B22), location: 0),
            .init(color: color(0xFF7A12), location: 0.55),
            .init(color: color(0xFF4E12), location: 1)
        ]), startPoint: CGPoint(x: 0, y: 0.66), endPoint: CGPoint(x: 0, y: -0.82)))
        flame.clip(to: path)
        flame.fill(circle(x: 0, y: 0.32, radius: 0.42), with: .radialGradient(
            Gradient(colors: [color(0xFFD65A, alpha: 0.75), color(0xFFD65A, alpha: 0)]),
            center: CGPoint(x: 0, y: 0.32), startRadius: 0, endRadius: 0.42
        ))

        let k = min(1, st.size / 150) * (st.page > 0 ? 1 + 0.2 * st.page : 1)
        let eyeR = 9 * k
        let gx = st.gaze * 3 * k
        for side in [-1.0, 1.0] {
            let ex = cx + side * 22 * k
            let ey = fy - 3 * k
            let h = eyeR * (1 - 0.9 * st.blink)
            context.fill(Path(ellipseIn: CGRect(x: ex - eyeR, y: ey - h, width: eyeR * 2, height: h * 2)), with: .color(.white))
            if st.blink < 0.6 {
                context.fill(circle(x: ex + gx + side * 1.2 * k, y: ey + 0.5 * k,
                                    radius: 5.2 * k * (1 - st.blink)), with: .color(ink))
            }
        }
        var mouth = Path()
        mouth.addArc(center: .zero, radius: 1, startAngle: .degrees(25), endAngle: .degrees(155), clockwise: false)
        mouth = mouth.applying(CGAffineTransform(a: 9 * k, b: 0, c: 0, d: 7 * k, tx: cx, ty: fy + 5 * k))
        context.stroke(mouth, with: .color(ink), style: StrokeStyle(lineWidth: 3.2 * k, lineCap: .round))
    }
}

/// Immutable text is rasterized once using iOS TextKit, as in the RN text renderer.
private struct FlameMascotSplashCopy {
    private struct Label {
        let image: Image
        let size: CGSize
    }
    private let size: CGSize
    private let safe: EdgeInsets
    private let heading: Label
    private let sub: Label
    private let terms: Label
    private let nudge: Label
    private let tick: Label
    private let action: Label
    private static var cache: [String: FlameMascotSplashCopy] = [:]

    static func layout(size: CGSize, safe: EdgeInsets) -> FlameMascotSplashCopy {
        let key = "\(size.width)|\(size.height)|\(safe.bottom)"
        if let copy = cache[key] { return copy }
        let copy = FlameMascotSplashCopy(size: size, safe: safe)
        cache[key] = copy
        return copy
    }

    private init(size: CGSize, safe: EdgeInsets) {
        self.size = size
        self.safe = safe
        let ink = UIColor(red: 22 / 255, green: 20 / 255, blue: 26 / 255, alpha: 1)
        heading = Self.label("Welcome to Ember", size: 30, weight: .black, color: ink,
                             tracking: -0.8, width: size.width - 64, centered: true)
        sub = Self.label("Your streaks, kept warm. One small habit a day is enough to keep the fire going.",
                         size: 16, color: ink.withAlphaComponent(0.6), lineHeight: 22,
                         width: size.width - 64, centered: true)
        terms = Self.label("I agree to the terms and the privacy notice", size: 15,
                           color: ink.withAlphaComponent(0.75), width: size.width - 84)
        nudge = Self.label("Send me a gentle nudge at 8 pm", size: 15,
                           color: ink.withAlphaComponent(0.75), width: size.width - 84)
        tick = Self.label("✓", size: 14, weight: .black, color: .white, width: 24, centered: true)
        action = Self.label("Continue", size: 17, weight: .bold, color: .white,
                            width: size.width - 48, centered: true)
    }

    private static func label(_ text: String, size: CGFloat, weight: UIFont.Weight = .regular,
                              color: UIColor, tracking: CGFloat? = nil, lineHeight: CGFloat? = nil,
                              width: CGFloat, centered: Bool = false) -> Label {
        let font = UIFont.systemFont(ofSize: size, weight: weight)
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = centered ? .center : .left
        var attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        if let tracking { attributes[.kern] = tracking }
        if let lineHeight {
            paragraph.minimumLineHeight = lineHeight
            paragraph.maximumLineHeight = lineHeight
            if lineHeight >= font.lineHeight { attributes[.baselineOffset] = (lineHeight - font.lineHeight) / 2 }
        }
        attributes[.paragraphStyle] = paragraph
        let storage = NSTextStorage(string: text, attributes: attributes)
        let manager = NSLayoutManager()
        manager.usesFontLeading = false
        let container = NSTextContainer(size: CGSize(width: width, height: .greatestFiniteMagnitude))
        container.lineFragmentPadding = 0
        container.lineBreakMode = .byClipping
        manager.addTextContainer(container)
        storage.addLayoutManager(manager)
        manager.ensureLayout(for: container)
        let scale = UIScreen.main.scale
        let height = ceil(manager.usedRect(for: container).height * scale) / scale
        let measured = CGSize(width: width, height: max(1, height))
        let bitmap = UIGraphicsImageRenderer(size: measured).image { _ in
            let range = manager.glyphRange(for: container)
            manager.drawBackground(forGlyphRange: range, at: .zero)
            manager.drawGlyphs(forGlyphRange: range, at: .zero)
        }
        return Label(image: Image(uiImage: bitmap), size: measured)
    }

    func draw(_ context: GraphicsContext, page: Double) {
        let headerAmount = amount(page, delay: 0)
        if headerAmount > 0 {
            var header = context
            header.opacity = headerAmount
            let top = size.height * 0.54 + 18 * (1 - headerAmount)
            header.draw(heading.image, at: CGPoint(x: 32, y: top), anchor: .topLeading)
            header.draw(sub.image, at: CGPoint(x: 32, y: top + heading.size.height + 10), anchor: .topLeading)
        }

        let row1Height = max(24, terms.size.height)
        let row2Height = max(24, nudge.size.height)
        let buttonTop = size.height - safe.bottom - 14 - 58
        let row2Top = buttonTop - 8 - 12 - row2Height
        let row1Top = row2Top - 12 - row1Height
        row(context, label: terms, top: row1Top, height: row1Height, amount: amount(page, delay: 0.13))
        row(context, label: nudge, top: row2Top, height: row2Height, amount: amount(page, delay: 0.26))
        let buttonAmount = amount(page, delay: 0.39)
        if buttonAmount > 0 {
            var button = context
            button.opacity = buttonAmount
            let top = buttonTop + 18 * (1 - buttonAmount)
            button.fill(Path(roundedRect: CGRect(x: 24, y: top, width: size.width - 48, height: 58), cornerRadius: 29),
                        with: .color(FlameMascotSplashArt.color(0x16141A)))
            button.draw(action.image, at: CGPoint(x: 24, y: pixelFloor(top + (58 - action.size.height) / 2)), anchor: .topLeading)
        }
    }

    private func row(_ context: GraphicsContext, label: Label, top: CGFloat, height: CGFloat, amount: Double) {
        guard amount > 0 else { return }
        var layer = context
        layer.opacity = amount
        let y = top + 18 * (1 - amount)
        let boxTop = y + (height - 24) / 2
        layer.fill(Path(roundedRect: CGRect(x: 24, y: boxTop, width: 24, height: 24), cornerRadius: 7),
                   with: .color(FlameMascotSplashArt.color(0xFF7A12)))
        layer.draw(tick.image, at: CGPoint(x: 24, y: pixelFloor(boxTop + (24 - tick.size.height) / 2)), anchor: .topLeading)
        layer.draw(label.image, at: CGPoint(x: 60, y: pixelFloor(y + (height - label.size.height) / 2)), anchor: .topLeading)
    }

    private func amount(_ page: Double, delay: Double) -> Double {
        FlameMascotSplashMotion.clamp((page - delay) / (1 - delay))
    }
    private func pixelFloor(_ value: CGFloat) -> CGFloat {
        let scale = UIScreen.main.scale
        return floor(value * scale) / scale
    }
}

#Preview {
    FlameMascotSplash(autoPlay: true)
}
