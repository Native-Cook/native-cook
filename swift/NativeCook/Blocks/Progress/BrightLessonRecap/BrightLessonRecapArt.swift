import CoreText
import SwiftUI
import UIKit

/// The block's hex colours as sRGB `Color`s.
enum BrightLessonRecapPalette {
    static func color(_ hex: UInt32, _ opacity: Double = 1) -> Color {
        Color(
            .sRGB,
            red: Double((hex >> 16) & 0xff) / 255,
            green: Double((hex >> 8) & 0xff) / 255,
            blue: Double(hex & 0xff) / 255,
            opacity: opacity
        )
    }
}

// MARK: - Fonts

/// The two bundled Nunito faces (SIL OFL, `BrightLessonRecap-Nunito-OFL.txt`) and the system face
/// that RN uses for "Take a bow!" and the success line.
enum BrightLessonRecapFonts {
    enum Face {
        case black
        case extraBold
        case system
    }

    /// Registers the two files once. RN registers them under the aliases `BrightNunito-Black` and
    /// `BrightNunito-ExtraBold`; here the faces keep their PostScript names.
    static let registered: Bool = {
        for name in ["BrightLessonRecap-Nunito-Black", "BrightLessonRecap-Nunito-ExtraBold"] {
            if let url = Bundle.main.url(forResource: name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
        return true
    }()

    private static let cacheLock = NSLock()
    private static var cache: [String: UIFont] = [:]
    private static var widths: [String: CGFloat] = [:]

    /// The width of one line of `text` in a face at a final size.
    static func width(of text: String, face: Face, size: CGFloat) -> CGFloat {
        let key = "\(face)-\(size)-\(text)"
        cacheLock.lock()
        if let cached = widths[key] {
            cacheLock.unlock()
            return cached
        }
        cacheLock.unlock()
        let measured = (text as NSString).size(withAttributes: [.font: font(face, size)]).width
        cacheLock.lock()
        widths[key] = measured
        cacheLock.unlock()
        return measured
    }

    /// A face at a size in points. The size is final: Dynamic Type is applied by `BrightLessonRecapMetrics`.
    /// Fonts are cached, because the labels ask for them on every frame.
    static func font(_ face: Face, _ size: CGFloat) -> UIFont {
        _ = registered
        let key = "\(face)-\(size)"
        cacheLock.lock()
        defer { cacheLock.unlock() }
        if let cached = cache[key] { return cached }
        let made: UIFont
        switch face {
        case .black:
            made = UIFont(name: "Nunito-Black", size: size) ?? .systemFont(ofSize: size, weight: .black)
        case .extraBold:
            made = UIFont(name: "Nunito-ExtraBold", size: size) ?? .systemFont(ofSize: size, weight: .heavy)
        case .system:
            made = .systemFont(ofSize: size, weight: .regular)
        }
        cache[key] = made
        return made
    }
}

// MARK: - Metrics

/// The window's safe-area insets. The block lays out edge to edge (`.ignoresSafeArea()`), so a
/// GeometryReader inside it reports zero insets; they are read from the key window instead.
enum BrightLessonRecapSafeArea {
    @MainActor static var insets: EdgeInsets {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }
        let i = window?.safeAreaInsets ?? .zero
        return EdgeInsets(top: i.top, leading: i.left, bottom: i.bottom, trailing: i.right)
    }
}

/// Everything RN derives from `useWindowDimensions()`, `fontScale` and `useSafeAreaInsets()`.
struct BrightLessonRecapMetrics {
    let width: CGFloat
    let height: CGFloat
    let insets: EdgeInsets
    /// RN `fontScale` (the iOS content-size multiplier: 1 at the default size).
    let fontScale: CGFloat

    /// RN `flowLayout`: large text or a narrow window stacks the recap instead of laying it out on the reference grid.
    var flow: Bool { fontScale > 1.3 || width < 350 }
    var contentInset: CGFloat { flow ? 24 : width * 0.04 }
    var contentWidth: CGFloat { width - contentInset * 2 }
    /// The scene starts 18 pt under the top inset; positions are fractions of the reference screen.
    var sectionTop: CGFloat { insets.top + 18 }
    var mascotSize: CGFloat { flow ? 220 : width * 0.8 }
    var compactHeight: CGFloat { width * 55 / 272 }
    var heroHeight: CGFloat { flow ? 250 : max(350, height * 426 / 586 - sectionTop) }
    var actionGap: CGFloat {
        flow ? 28 : max(12, height * 525 / 586 - (sectionTop + heroHeight + compactHeight))
    }
    var statsWidth: CGFloat { width * 218 / 272 }
    var statGap: CGFloat { width * 12.5 / 272 }
    var pillWidth: CGFloat { (statsWidth - statGap * 2) / 3 }
    var bandsTop: CGFloat { flow ? 70 : height * 0.275 - sectionTop }
    var bandsHeight: CGFloat { flow ? 160 : height * 0.385 }
    var sparkleCenterY: CGFloat { flow ? 120 : height * 0.376 - sectionTop }
    var padTop: CGFloat { insets.top + 18 }
    var padBottom: CGFloat { insets.bottom + 18 }

    /// A label at the RN font size times the content-size multiplier, which `cap` (RN
    /// `maxFontSizeMultiplier`) limits for display type. Letter spacing is not scaled, as in RN.
    func label(
        _ text: String,
        face: BrightLessonRecapFonts.Face,
        size: CGFloat,
        lineHeight: CGFloat? = nil,
        kern: CGFloat = 0,
        color: UInt32,
        cap: CGFloat? = nil,
        tabular: Bool = false
    ) -> BrightLessonRecapLabel {
        let multiplier = cap.map { min(fontScale, $0) } ?? fontScale
        return BrightLessonRecapLabel(
            text: text,
            font: BrightLessonRecapFonts.font(face, size * multiplier),
            lineHeight: lineHeight.map { $0 * multiplier },
            kern: kern,
            color: BrightLessonRecapPalette.color(color),
            tabular: tabular
        )
    }

    /// The width of `text` in a face at a size, as RN's hidden measuring `Text` reports it.
    func measure(_ text: String, face: BrightLessonRecapFonts.Face, size: CGFloat) -> CGFloat {
        BrightLessonRecapFonts.width(of: text, face: face, size: size * fontScale)
    }
}

extension DynamicTypeSize {
    /// RN's iOS `fontScale` for each content-size category.
    var brightLessonRecapScale: CGFloat {
        switch self {
        case .xSmall: return 0.823
        case .small: return 0.882
        case .medium: return 0.941
        case .large: return 1
        case .xLarge: return 1.118
        case .xxLarge: return 1.235
        case .xxxLarge: return 1.353
        case .accessibility1: return 1.786
        case .accessibility2: return 2.143
        case .accessibility3: return 2.643
        case .accessibility4: return 3.143
        case .accessibility5: return 3.571
        @unknown default: return 1
        }
    }
}

// MARK: - Text

/// A line of text with RN's line-height model: every line box is `lineHeight` tall and the glyphs
/// sit in the middle of it. The size is final, so system text scaling never touches it.
struct BrightLessonRecapLabel: View {
    let text: String
    let font: UIFont
    var lineHeight: CGFloat?
    var kern: CGFloat = 0
    let color: Color
    var tabular = false
    var alignment: TextAlignment = .center

    var body: some View {
        let natural = font.lineHeight
        let extra = (lineHeight ?? natural) - natural
        styled
            .foregroundStyle(color)
            .multilineTextAlignment(alignment)
            .lineSpacing(extra)
            .padding(.vertical, extra / 2)
    }

    private var styled: Text {
        let base = Text(verbatim: text).font(Font(font as CTFont)).kerning(kern)
        return tabular ? base.monospacedDigit() : base
    }

    func aligned(_ alignment: TextAlignment) -> BrightLessonRecapLabel {
        var copy = self
        copy.alignment = alignment
        return copy
    }
}

// MARK: - Shapes

/// The four-point sparkle (RN `Sparkle`, viewBox 20).
struct BrightLessonRecapSparkleShape: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 20
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: rect.minX + x * s, y: rect.minY + y * s)
        }
        var path = Path()
        path.move(to: p(10, 0))
        path.addCurve(to: p(20, 10), control1: p(10.9, 6.4), control2: p(13.6, 9.1))
        path.addCurve(to: p(10, 20), control1: p(13.6, 10.9), control2: p(10.9, 13.6))
        path.addCurve(to: p(0, 10), control1: p(9.1, 13.6), control2: p(6.4, 10.9))
        path.addCurve(to: p(10, 0), control1: p(6.4, 9.1), control2: p(9.1, 6.4))
        path.closeSubpath()
        return path
    }
}

/// The glyph of a result: a filled bolt, a sealed check or a clock face (RN `StatIcon`, viewBox 24).
struct BrightLessonRecapStatIcon: View {
    let kind: BrightLessonRecapStat.Kind
    let color: Color
    var size: CGFloat = 23

    var body: some View {
        Canvas { context, canvas in
            let s = canvas.width / 24
            func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * s, y: y * s) }
            let round = { (width: CGFloat) in
                StrokeStyle(lineWidth: width * s, lineCap: .round, lineJoin: .round)
            }
            switch kind {
            case .points:
                var bolt = Path()
                bolt.move(to: p(13.4, 2.2))
                bolt.addLine(to: p(4.8, 13.4))
                bolt.addLine(to: p(10.9, 13.4))
                bolt.addLine(to: p(9.8, 21.8))
                bolt.addLine(to: p(18.7, 10.2))
                bolt.addLine(to: p(12.5, 10.2))
                bolt.addLine(to: p(13.4, 2.2))
                bolt.closeSubpath()
                context.fill(bolt, with: .color(color))
                context.stroke(bolt, with: .color(color), style: round(1.2))
            case .accuracy:
                context.fill(Path(ellipseIn: CGRect(x: 2.4 * s, y: 2.4 * s, width: 19.2 * s, height: 19.2 * s)), with: .color(color))
                var tick = Path()
                tick.move(to: p(7.8, 12.2))
                tick.addLine(to: p(10.7, 15.1))
                tick.addLine(to: p(16.3, 8.9))
                context.stroke(tick, with: .color(.white), style: round(2.3))
            case .time:
                context.fill(Path(ellipseIn: CGRect(x: 2.4 * s, y: 2.4 * s, width: 19.2 * s, height: 19.2 * s)), with: .color(color))
                var hands = Path()
                hands.move(to: p(12, 6.8))
                hands.addLine(to: p(12, 12))
                hands.addLine(to: p(15.4, 14.1))
                context.stroke(hands, with: .color(.white), style: round(2.2))
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// The share glyph of the compact action row (RN `Svg` 22 × 22, viewBox 24, stroke #268DDD 2.4).
struct BrightLessonRecapShareGlyph: View {
    var body: some View {
        Canvas { context, canvas in
            let s = canvas.width / 24
            func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * s, y: y * s) }
            let k: CGFloat = 0.5523 * 2
            var path = Path()
            // M12 14.5V3.5
            path.move(to: p(12, 14.5))
            path.addLine(to: p(12, 3.5))
            // M8 7.2 12 3.3l4 3.9
            path.move(to: p(8, 7.2))
            path.addLine(to: p(12, 3.3))
            path.addLine(to: p(16, 7.2))
            // M8.5 10.5H7a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6a2 2 0 0 0-2-2h-1.5
            path.move(to: p(8.5, 10.5))
            path.addLine(to: p(7, 10.5))
            path.addCurve(to: p(5, 12.5), control1: p(7 - k, 10.5), control2: p(5, 12.5 - k))
            path.addLine(to: p(5, 18.5))
            path.addCurve(to: p(7, 20.5), control1: p(5, 18.5 + k), control2: p(7 - k, 20.5))
            path.addLine(to: p(17, 20.5))
            path.addCurve(to: p(19, 18.5), control1: p(17 + k, 20.5), control2: p(19, 18.5 + k))
            path.addLine(to: p(19, 12.5))
            path.addCurve(to: p(17, 10.5), control1: p(19, 12.5 - k), control2: p(17 + k, 10.5))
            path.addLine(to: p(15.5, 10.5))
            context.stroke(
                path,
                with: .color(BrightLessonRecapPalette.color(0x268DDD)),
                style: StrokeStyle(lineWidth: 2.4 * s, lineCap: .round, lineJoin: .round)
            )
        }
        .frame(width: 22, height: 22)
        .accessibilityHidden(true)
    }
}

// MARK: - Assets

/// The mascot, decoded once.
enum BrightLessonRecapAssets {
    static let mascot: UIImage = {
        guard let path = Bundle.main.path(forResource: "BrightLessonRecap-pip-celebrates", ofType: "png"),
              let image = UIImage(contentsOfFile: path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }()
}

// MARK: - Sharing

/// RN `Share.share({ message })`: the system share sheet with the result as text.
enum BrightLessonRecapShare {
    @MainActor static func present(_ message: String) {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }
        guard var top = window?.rootViewController else { return }
        while let presented = top.presentedViewController { top = presented }
        let sheet = UIActivityViewController(activityItems: [message], applicationActivities: nil)
        sheet.popoverPresentationController?.sourceView = top.view
        sheet.popoverPresentationController?.sourceRect = CGRect(x: top.view.bounds.midX, y: top.view.bounds.midY, width: 1, height: 1)
        top.present(sheet, animated: true)
    }
}
