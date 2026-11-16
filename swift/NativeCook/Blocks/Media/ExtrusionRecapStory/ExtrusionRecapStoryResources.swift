import CoreText
import SwiftUI
import UIKit

enum ExtrusionRecapStoryResources {
    @MainActor private static var fonts: [String: UIFont] = [:]
    static let register: Void = {
        for weight in ["Regular", "ExtraBold"] {
            if let url = Bundle.main.url(forResource: "ExtrusionRecapStory-Onest-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()

    @MainActor static func font(_ bold: Bool, _ size: Double, tabular: Bool = false) -> UIFont {
        _ = register
        let weight = bold ? "ExtraBold" : "Regular"
        let key = "\(weight)-\(size)-\(tabular)"
        if let font = fonts[key] { return font }
        guard let url = Bundle.main.url(forResource: "ExtrusionRecapStory-Onest-\(weight)", withExtension: "ttf"),
              let provider = CGDataProvider(url: url as CFURL), let cgFont = CGFont(provider) else {
            return .systemFont(ofSize: size, weight: bold ? .heavy : .regular)
        }
        let ct = CTFontCreateWithGraphicsFont(cgFont, size, nil, nil)
        let base = UIFont(name: CTFontCopyPostScriptName(ct) as String, size: size) ?? .systemFont(ofSize: size)
        if !tabular { fonts[key] = base; return base }
        let descriptor = base.fontDescriptor.addingAttributes([
            .featureSettings: [[UIFontDescriptor.FeatureKey.type: kNumberSpacingType,
                                UIFontDescriptor.FeatureKey.selector: kMonospacedNumbersSelector]]
        ])
        let result = UIFont(descriptor: descriptor, size: size)
        fonts[key] = result
        return result
    }

    // The exact RN distance algorithm ran at port time with a Skia/CanvasKit mask adapter.
    // Opaque grey tiles preserve both bytes under sRGB -> P3, as in ers-atlas.ts.
    static let atlas: Image = {
        let url = Bundle.main.url(forResource: "ExtrusionRecapStory-atlas", withExtension: "rgba")!
        let bytes = try! Data(contentsOf: url)
        let provider = CGDataProvider(data: bytes as CFData)!
        let image = CGImage(width: 768, height: 512, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: 768 * 4,
                            space: CGColorSpace(name: CGColorSpace.sRGB)!,
                            bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                            provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)!
        return Image(decorative: image, scale: 1)
    }()

    struct ExtrusionRecapStoryPage {
        let fieldA: UInt32
        let fieldB: UInt32
        let face: UInt32
        let direction: [Double]
        let headline: String
        let caption: String
        var stat: Int? = nil
        var peekTitle: String { (stat.map { "\($0) " } ?? "") + headline.replacingOccurrences(of: "\n", with: " ") }
        var accessibilityLabel: String { [stat.map { String($0) }, headline.replacingOccurrences(of: "\n", with: " "), caption].compactMap { $0 }.joined(separator: ". ") }
    }
    static let pages: [ExtrusionRecapStoryPage] = [
        .init(fieldA: 0x080205, fieldB: 0xF26AB4, face: 0xD8004A, direction: [0.43, 0.9],
              headline: "That very\nmusic", caption: "Tracks that captured your year like nothing else"),
        .init(fieldA: 0xD51B39, fieldB: 0xF34455, face: 0xD8183B, direction: [-0.16, 0.98],
              headline: "new discoveries", caption: "Each time your heart said yes, a track found its way in.", stat: 264),
        .init(fieldA: 0x020B05, fieldB: 0x3DF03C, face: 0x32DF31, direction: [0.52, 0.85],
              headline: "Remember\nthat moment?", caption: "You found new music. And a little more of yourself.")
    ]
    static func rgb(_ hex: UInt32) -> [Double] {
        [Double(hex >> 16 & 255) / 255, Double(hex >> 8 & 255) / 255, Double(hex & 255) / 255]
    }
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        let c = rgb(hex)
        return Color(.sRGB, red: c[0], green: c[1], blue: c[2], opacity: alpha)
    }

    struct ExtrusionRecapStoryLayout {
        let width: Double
        let height: Double
        let top: Double
        let bottom: Double
        var scale: Double { width / 402 }
        var cardBottom: Double { height - max(bottom, 34 * scale) - 48 * scale }
        var titleY: Double { top + 74 * scale }
        var heroY: Double { min(height * (495.0 / 874), cardBottom - 145 * scale) }
        var peekRow: Double { max(height - max(bottom, 34 * scale) - cardBottom - 8 * scale, 32 * scale) }
    }
}

/// RN Text uses native attributed text too. Keep explicit line heights, fractional tracking and the bundled font.
struct ExtrusionRecapStoryText: UIViewRepresentable {
    @Environment(\.displayScale) private var displayScale
    var text: String
    var size: Double
    var lineHeight: Double? = nil
    var bold = false
    var tracking = 0.0
    var hex: UInt32 = 0xF4F83C
    var tabular = false
    var alignment: NSTextAlignment = .center
    var singleLine = false

    func makeUIView(context: Context) -> ExtrusionRecapStoryTextView {
        let view = ExtrusionRecapStoryTextView()
        view.backgroundColor = .clear
        view.isAccessibilityElement = false
        view.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return view
    }
    func updateUIView(_ view: ExtrusionRecapStoryTextView, context: Context) {
        let font = ExtrusionRecapStoryResources.font(bold, size, tabular: tabular)
        let c = ExtrusionRecapStoryResources.rgb(hex)
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = alignment
        paragraph.lineBreakMode = singleLine ? .byTruncatingTail : .byWordWrapping
        if let height = lineHeight { paragraph.minimumLineHeight = height; paragraph.maximumLineHeight = height }
        var attributes: [NSAttributedString.Key: Any] = [
            .font: font, .kern: tracking, .paragraphStyle: paragraph,
            .foregroundColor: UIColor(red: c[0], green: c[1], blue: c[2], alpha: 1)
        ]
        if let height = lineHeight, height > font.lineHeight { attributes[.baselineOffset] = (height - font.lineHeight) / 2 }
        let attributed = NSAttributedString(string: text, attributes: attributes)
        view.setText(attributed, singleLine: singleLine)
    }
    func sizeThatFits(_ proposal: ProposedViewSize, uiView: ExtrusionRecapStoryTextView, context: Context) -> CGSize? {
        let size = uiView.sizeThatFits(CGSize(width: proposal.width ?? uiView.naturalWidth, height: .greatestFiniteMagnitude))
        return CGSize(width: proposal.width ?? ceil(size.width * displayScale) / displayScale,
                      height: ceil(size.height * displayScale) / displayScale)
    }
}

/// TextKit 1 with the same container flags as RN's RCTTextLayoutManager.
/// UILabel vertically centres a compressed line box, which changes the 54/51 headline baseline.
final class ExtrusionRecapStoryTextView: UIView {
    private let storage = NSTextStorage()
    private let manager = NSLayoutManager()
    private let container = NSTextContainer(size: .zero)
    var naturalWidth: CGFloat { ceil(storage.size().width * contentScaleFactor) / contentScaleFactor }

    init() {
        super.init(frame: .zero)
        isOpaque = false
        container.lineFragmentPadding = 0
        manager.usesFontLeading = false
        manager.addTextContainer(container)
        storage.addLayoutManager(manager)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func setText(_ text: NSAttributedString, singleLine: Bool) {
        guard !storage.isEqual(to: text) || container.maximumNumberOfLines != (singleLine ? 1 : 0) else { return }
        storage.setAttributedString(text)
        container.maximumNumberOfLines = singleLine ? 1 : 0
        container.lineBreakMode = singleLine ? .byTruncatingTail : .byClipping
        invalidateIntrinsicContentSize()
        setNeedsLayout()
        setNeedsDisplay()
    }
    override func sizeThatFits(_ size: CGSize) -> CGSize {
        container.size = CGSize(width: size.width, height: .greatestFiniteMagnitude)
        manager.ensureLayout(for: container)
        return manager.usedRect(for: container).size
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        container.size = CGSize(width: bounds.width, height: .greatestFiniteMagnitude)
        setNeedsDisplay()
    }
    override func draw(_ rect: CGRect) {
        let range = manager.glyphRange(for: container)
        manager.drawBackground(forGlyphRange: range, at: .zero)
        manager.drawGlyphs(forGlyphRange: range, at: .zero)
    }
}
