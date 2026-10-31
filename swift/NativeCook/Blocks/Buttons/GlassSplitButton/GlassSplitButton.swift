import SwiftUI
import UIKit

/// Drift's capture disc gathers into a pill, springs into three actions, then folds back.
/// The 7 s timeline, dimensions, drawing order and touch phases match the React Native block.
struct GlassSplitButton: View {
    var brand = "Drift"
    var title = "Capture\nanything"
    var autoPlay = false

    static let loop: Double = 7

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var accumulated = 0.0
    @State private var running = true
    @State private var loopOrigin = 0.0
    @State private var tapped: Double?
    @State private var opening = true

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + safe.leading + safe.trailing,
                              height: geo.size.height + safe.top + safe.bottom)
            ZStack(alignment: .topLeading) {
                TimelineView(.animation(paused: reduceMotion || !running)) { timeline in
                    let phase = phase(at: elapsed(at: timeline.date))
                    let glass = Self.glass(at: phase.shown)
                    ZStack(alignment: .topLeading) {
                        Canvas { context, canvasSize in
                            Self.draw(context, size: canvasSize, clock: phase.clock, glass: glass)
                        }
                        .frame(width: size.width, height: size.height)

                        HStack(spacing: 18) {
                            ForEach(["Photo", "Note", "Voice"], id: \.self) { label in
                                Text(label)
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundStyle(Self.ink)
                                    .frame(width: 64, height: 17)
                            }
                        }
                        .frame(width: 272, height: 17)
                        .opacity(glass.icons)
                        .position(x: size.width / 2,
                                  y: size.height / 2 + 68 + 8 * (1 - glass.icons) + 8.5)
                        .allowsHitTesting(false)
                    }
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .allowsHitTesting(false)

                GlassSplitButtonCopy(brand: brand, title: title, safe: safe)
                    .frame(width: size.width, height: size.height)
                    .allowsHitTesting(false)

                Color.clear
                    .frame(width: 272, height: 132)
                    .contentShape(Rectangle())
                    .position(x: size.width / 2, y: size.height / 2)
                    .onTapGesture { tap() }
                    .accessibilityLabel("New capture")
                    .accessibilityAddTraits(.isButton)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear {
            epoch = .now
            accumulated = 0
            running = scenePhase == .active && !reduceMotion
        }
        .onChange(of: scenePhase) { _, newPhase in
            let now = Date()
            accumulated = elapsed(at: now)
            epoch = now
            running = newPhase == .active && !reduceMotion
        }
        .onChange(of: reduceMotion) { _, reduced in
            let now = Date()
            accumulated = elapsed(at: now)
            epoch = now
            running = scenePhase == .active && !reduced
        }
    }

    private func elapsed(at date: Date) -> Double {
        accumulated + (running ? max(0, date.timeIntervalSince(epoch)) : 0)
    }

    private func phase(at now: Double) -> (shown: Double, clock: Double) {
        guard !reduceMotion else { return (3, 0) }
        var origin = loopOrigin
        if let tapped {
            let since = now - tapped
            let duration = opening ? 4.0 : 3.4
            if since <= duration {
                let shown = opening ? min(3, 1 + since) : min(6.2, 4.8 + since)
                return (shown, Self.modulo(now - origin))
            }
            // RN resumes at 3 after opening (2 s animation + 2 s hold), or at 6.2
            // after closing (1.4 s animation + 2 s hold). No per-frame state writes.
            origin = tapped + duration - (opening ? 3 : 6.2)
        }
        let clock = Self.modulo(now - origin)
        return (autoPlay ? clock : 0, clock)
    }

    private func tap() {
        guard !reduceMotion else { return }
        let now = elapsed(at: .now)
        let current = phase(at: now)
        loopOrigin = now - current.clock
        opening = Self.glass(at: current.shown).width <= 152
        tapped = now
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    private struct Glass {
        var width = 132.0
        var height = 132.0
        var icons = 0.0
        var press = 1.0
    }

    private static let ink = Color(red: 21 / 255, green: 23 / 255, blue: 28 / 255)

    private static func modulo(_ t: Double) -> Double {
        let value = t.truncatingRemainder(dividingBy: loop)
        return value < 0 ? value + loop : value
    }

    private static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
        min(1, max(0, (t - a) / (b - a)))
    }

    private static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    private static func easeOutBack(_ x: Double) -> Double {
        1 + 2.5 * pow(x - 1, 3) + 1.5 * pow(x - 1, 2)
    }

    private static func glass(at t: Double) -> Glass {
        var g = Glass()
        if t < 1.4 {
            let a = easeInOut(seg(t, 1, 1.4))
            g.width += (104 - 132) * a
            g.height += (52 - 132) * a
        } else if t < 5 {
            let b = easeOutBack(seg(t, 1.4, 2.1))
            g.width = 104 + (272 - 104) * b
            g.height = 52 + (92 - 52) * b
        } else if t < 5.5 {
            let c = easeInOut(seg(t, 5, 5.45))
            g.width = 272 + (104 - 272) * c
            g.height = 92 + (52 - 92) * c
        } else {
            let d = easeOutBack(seg(t, 5.5, 6.2))
            g.width = 104 + (132 - 104) * d
            g.height = 52 + (132 - 52) * d
        }
        g.icons = (1 - pow(1 - seg(t, 1.7, 2.3), 3)) * (1 - pow(seg(t, 4.8, 5.15), 3))
        g.press = 1 - 0.05 * sin(.pi * seg(t, 3.3, 3.7))
        return g
    }

    private static func rgb(_ hex: UInt32, opacity: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: opacity)
    }

    private static func background(_ context: GraphicsContext, size: CGSize,
                                   clock: Double, extraBlur: Double = 0) {
        let cx = size.width / 2
        let cy = size.height / 2
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .linearGradient(
            Gradient(colors: [rgb(0xE9ECF1), rgb(0xB7BFCC), rgb(0x7D879A)]),
            startPoint: .zero, endPoint: CGPoint(x: size.width * 0.4, y: size.height)))
        let phi = clock / loop * 2 * Double.pi
        let blobs: [(Double, Double, Double, UInt32)] = [
            (cx - 90 + 40 * sin(phi), cy - 40, 84, 0xFFA97A),
            (cx + 100 + 36 * cos(phi), cy + 10, 78, 0x9D8CFF),
            (cx, cy - 110 + 30 * sin(2 * phi), 62, 0x6FD8C8),
        ]
        var blurred = context
        blurred.opacity = 0.9
        blurred.addFilter(.blur(radius: sqrt(34 * 34 + extraBlur * extraBlur)))
        for (x, y, r, color) in blobs {
            blurred.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)),
                         with: .color(rgb(color)))
        }
    }

    private static func shadow(_ context: GraphicsContext, path: Path, extraBlur: Double = 0) {
        var shadow = context
        shadow.addFilter(.blur(radius: sqrt(24 * 24 + extraBlur * extraBlur)))
        // Skia's shadow-only filter also multiplies by its source fill's 0.01 alpha.
        shadow.fill(path.offsetBy(dx: 0, dy: 18), with: .color(rgb(0x283046, opacity: 0.28 * 0.01)))
    }

    private static func draw(_ context: GraphicsContext, size: CGSize, clock: Double, glass g: Glass) {
        let cx = size.width / 2
        let cy = size.height / 2
        background(context, size: size, clock: clock)

        let w = g.width * g.press
        let h = g.height * g.press
        let rect = CGRect(x: cx - w / 2, y: cy - h / 2, width: w, height: h)
        let shape = Path(roundedRect: rect, cornerRadius: h / 2)
        shadow(context, path: shape)

        // Re-render this self-contained backdrop through the same 18 pt Gaussian glass blur.
        // Gaussian variances add, so the coloured circles use sqrt(34² + 18²) inside.
        var inside = context
        inside.clip(to: shape)
        background(inside, size: size, clock: clock, extraBlur: 18)
        shadow(inside, path: shape, extraBlur: 18)
        inside.fill(shape, with: .color(rgb(0xECF0F6, opacity: 0.42)))

        context.stroke(shape, with: .linearGradient(
            Gradient(colors: [.white.opacity(0.95), .white.opacity(0.25), rgb(0xFFD2AA, opacity: 0.9)]),
            startPoint: CGPoint(x: 0, y: cy - 66), endPoint: CGPoint(x: 0, y: cy + 66)),
                       lineWidth: 1.5)

        let stroke = StrokeStyle(lineWidth: 3.5, lineCap: .round)
        if g.icons > 0 {
            for sign in [-1.0, 1.0] {
                let ring = Path(ellipseIn: CGRect(x: cx + sign * g.width * 0.31 - 16,
                                                 y: cy - 16, width: 32, height: 32))
                context.stroke(ring.trimmedPath(from: 0, to: g.icons), with: .color(ink), style: stroke)
            }
        }
        var plus = Path()
        plus.move(to: CGPoint(x: cx, y: cy - 15))
        plus.addLine(to: CGPoint(x: cx, y: cy + 15))
        plus.move(to: CGPoint(x: cx - 15, y: cy))
        plus.addLine(to: CGPoint(x: cx + 15, y: cy))
        context.stroke(plus, with: .color(ink.opacity(0.28 + 0.72 * g.icons)), style: stroke)
    }
}

/// TextKit uses the same system faces and explicit line heights as React Native's iOS text.
private struct GlassSplitButtonCopy: UIViewRepresentable {
    var brand: String
    var title: String
    var safe: EdgeInsets

    func makeUIView(context: Context) -> CopyView {
        let view = CopyView()
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = false
        view.isAccessibilityElement = true
        return view
    }

    func updateUIView(_ view: CopyView, context: Context) {
        view.brand = brand
        view.title = title
        view.safe = safe
        view.accessibilityLabel = "\(brand). 12 notes today. \(title). A photo, a thought or a voice memo — one tap away. Last capture, 9:12, Pick up film rolls."
        view.setNeedsDisplay()
    }

    final class CopyView: UIView {
        var brand = ""
        var title = ""
        var safe = EdgeInsets()
        private let ink = UIColor(red: 21 / 255, green: 23 / 255, blue: 28 / 255, alpha: 1)

        override func draw(_ rect: CGRect) {
            let width = bounds.width
            let head = safe.top + 8
            drawText(brand, font: .systemFont(ofSize: 18, weight: .heavy), color: ink,
                     rect: CGRect(x: 24, y: head, width: width - 48, height: 34), centeredVertically: true)
            drawText("12 notes today", font: .systemFont(ofSize: 14, weight: .semibold),
                     color: ink.withAlphaComponent(0.55),
                     rect: CGRect(x: 24, y: head, width: width - 48, height: 34),
                     alignment: .right, centeredVertically: true)
            drawText(title, font: .systemFont(ofSize: 48, weight: .black), color: ink,
                     rect: CGRect(x: 24, y: head + 50, width: width - 48, height: 100),
                     lineHeight: 50, tracking: -1.8)
            drawText("A photo, a thought or a voice memo — one tap away.",
                     font: .systemFont(ofSize: 17), color: ink.withAlphaComponent(0.65),
                     rect: CGRect(x: 24, y: head + 162, width: min(290, width - 48), height: 46),
                     lineHeight: 23)
            let footerFont = UIFont.systemFont(ofSize: 14, weight: .semibold)
            let footerHeight = ceil(footerFont.lineHeight * contentScaleFactor) / contentScaleFactor
            drawText("Last capture · 9:12 · “Pick up film rolls”", font: footerFont,
                     color: .white.withAlphaComponent(0.85),
                     rect: CGRect(x: 24, y: bounds.height - safe.bottom - 18 - footerHeight,
                                  width: width - 48, height: footerHeight), alignment: .center)
        }

        private func drawText(_ text: String, font: UIFont, color: UIColor, rect: CGRect,
                              lineHeight: CGFloat? = nil, tracking: CGFloat = 0,
                              alignment: NSTextAlignment = .left, centeredVertically: Bool = false) {
            let paragraph = NSMutableParagraphStyle()
            paragraph.alignment = alignment
            paragraph.lineBreakMode = .byWordWrapping
            var attributes: [NSAttributedString.Key: Any] = [
                .font: font, .foregroundColor: color, .paragraphStyle: paragraph, .kern: tracking,
            ]
            if let lineHeight {
                paragraph.minimumLineHeight = lineHeight
                paragraph.maximumLineHeight = lineHeight
                if lineHeight >= font.lineHeight { attributes[.baselineOffset] = (lineHeight - font.lineHeight) / 2 }
            }
            let storage = NSTextStorage(string: text, attributes: attributes)
            let layout = NSLayoutManager()
            layout.usesFontLeading = false
            let container = NSTextContainer(size: CGSize(width: rect.width, height: .greatestFiniteMagnitude))
            container.lineFragmentPadding = 0
            layout.addTextContainer(container)
            storage.addLayoutManager(layout)
            let range = layout.glyphRange(for: container)
            let used = layout.usedRect(for: container)
            let offset = centeredVertically ? (rect.height - ceil(used.height * contentScaleFactor) / contentScaleFactor) / 2 : 0
            layout.drawGlyphs(forGlyphRange: range, at: CGPoint(x: rect.minX, y: rect.minY + offset))
        }
    }
}

#Preview {
    GlassSplitButton(autoPlay: true)
}
