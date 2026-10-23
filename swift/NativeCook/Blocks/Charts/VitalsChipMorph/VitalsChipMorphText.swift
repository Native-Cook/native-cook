import CoreText
import SwiftUI
import UIKit

/// A one-line Core Text label whose `capTop` is in the 1206 px reference space.
///
/// React Native positions every label from the top of an SF Pro capital using a
/// measured 0.247 em offset.  SwiftUI's `Text` does not expose that cap metric,
/// so this wrapper asks the actual `UIFont` / Core Text line for it at runtime.
/// This keeps labels aligned when the rendered system font changes subtly across
/// iOS releases.
struct VitalsChipMorphText: View {
    enum Alignment: Equatable {
        case leading
        case center
    }

    let value: String
    let spec: VitalsChipMorphTheme.TextSpec
    let x: Double
    let capTop: Double
    let k: Double
    var color: Color?
    var width: Double?
    var alignment: Alignment = .leading
    /// RN `Line` opts into tabular figures; chip labels can opt out explicitly.
    var tabularNumbers = true

    /// The RN reference's SF Pro cap-top approximation, retained for callers
    /// that need its literal geometry. Drawing itself uses `UIFont.capHeight`.
    static let CAP_OFFSET = 0.247
    /// SVG text in the source uses this baseline factor for its softened value.
    static let BASELINE = 0.953

    private var scale: Double { 3 * k }
    private var font: UIFont {
        let size = CGFloat(spec.size * scale)
        let base: UIFont
        if spec.rounded {
            let descriptor = UIFont.systemFont(ofSize: size, weight: uiWeight(spec.fontWeight))
                .fontDescriptor.withDesign(.rounded)
            base = descriptor.map { UIFont(descriptor: $0, size: size) }
                ?? UIFont.systemFont(ofSize: size, weight: uiWeight(spec.fontWeight))
        } else {
            base = UIFont.systemFont(ofSize: size, weight: uiWeight(spec.fontWeight))
        }
        // RN applies `fontVariant: ['tabular-nums']` to every `Line`.  Ask SF
        // Pro for the same OpenType number-spacing selector before Core Text
        // resolves the glyphs, instead of using a proportional Text modifier.
        guard tabularNumbers else { return base }
        let descriptor = base.fontDescriptor.addingAttributes([
            .featureSettings: [[
                UIFontDescriptor.FeatureKey.type: kNumberSpacingType,
                UIFontDescriptor.FeatureKey.selector: kMonospacedNumbersSelector,
            ]],
        ])
        return UIFont(descriptor: descriptor, size: size)
    }

    var body: some View {
        let typeface = font
        let measured = VitalsChipMorphTextRenderer.measure(value, font: typeface, tracking: CGFloat(spec.ls * scale))
        let displayWidth = CGFloat((width ?? Double(measured.width) / k) * k)
        let height = measured.height
        VitalsChipMorphTextRenderer(
            value: value,
            font: typeface,
            tracking: CGFloat(spec.ls * scale),
            color: UIColor(color ?? spec.color),
            alignment: alignment
        )
        .frame(width: displayWidth, height: height)
        // `ascent - capHeight` leaves enough room for ascenders while putting
        // the true Core Text cap top at the reference coordinate.
        .offset(
            x: x * k,
            y: capTop * k - Double(typeface.ascender - typeface.capHeight)
        )
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func uiWeight(_ weight: Font.Weight) -> UIFont.Weight {
        switch weight {
        case .ultraLight: return .ultraLight
        case .thin: return .thin
        case .light: return .light
        case .medium: return .medium
        case .semibold: return .semibold
        case .bold: return .bold
        case .heavy: return .heavy
        case .black: return .black
        default: return .regular
        }
    }

    static func measuredWidth(_ value: String, spec: VitalsChipMorphTheme.TextSpec, k: Double) -> Double {
        let probe = VitalsChipMorphText(value: value, spec: spec, x: 0, capTop: 0, k: k)
        return Double(VitalsChipMorphTextRenderer.measure(value, font: probe.font, tracking: CGFloat(spec.ls * 3 * k)).width)
    }

    static func lineHeight(spec: VitalsChipMorphTheme.TextSpec, k: Double) -> Double {
        let probe = VitalsChipMorphText(value: "", spec: spec, x: 0, capTop: 0, k: k)
        return Double(probe.font.lineHeight)
    }
}

private struct VitalsChipMorphTextRenderer: UIViewRepresentable {
    let value: String
    let font: UIFont
    let tracking: CGFloat
    let color: UIColor
    let alignment: VitalsChipMorphText.Alignment

    func makeUIView(context: Context) -> VitalsChipMorphTextCanvas {
        let view = VitalsChipMorphTextCanvas()
        view.isOpaque = false
        view.backgroundColor = .clear
        return view
    }

    func updateUIView(_ view: VitalsChipMorphTextCanvas, context: Context) {
        guard view.value != value
            || !view.font.isEqual(font)
            || view.tracking != tracking
            || !view.color.isEqual(color)
            || view.alignment != alignment
        else { return }
        view.value = value
        view.font = font
        view.tracking = tracking
        view.color = color
        view.alignment = alignment
        view.setNeedsDisplay()
    }

    static func measure(_ value: String, font: UIFont, tracking: CGFloat) -> CGSize {
        let line = CTLineCreateWithAttributedString(attributed(value, font: font, tracking: tracking, color: .black))
        var ascent: CGFloat = 0
        var descent: CGFloat = 0
        let width = CGFloat(CTLineGetTypographicBounds(line, &ascent, &descent, nil))
        return CGSize(width: ceil(width), height: ceil(font.ascender - font.descender))
    }

    fileprivate static func attributed(_ value: String, font: UIFont, tracking: CGFloat, color: UIColor) -> NSAttributedString {
        NSAttributedString(
            string: value,
            attributes: [
                .font: font,
                .kern: tracking,
                .foregroundColor: color.cgColor,
            ]
        )
    }
}

private final class VitalsChipMorphTextCanvas: UIView {
    var value = ""
    var font = UIFont.systemFont(ofSize: 12)
    var tracking: CGFloat = 0
    var color = UIColor.black
    var alignment: VitalsChipMorphText.Alignment = .leading

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        let line = CTLineCreateWithAttributedString(
            VitalsChipMorphTextRenderer.attributed(value, font: font, tracking: tracking, color: color)
        )
        let width = CGFloat(CTLineGetTypographicBounds(line, nil, nil, nil))
        let x = alignment == .center ? (bounds.width - width) / 2 : 0
        context.saveGState()
        context.textMatrix = .identity
        context.translateBy(x: x, y: font.ascender)
        context.scaleBy(x: 1, y: -1)
        // Baseline lives exactly one ascender below this view's top edge.
        CTLineDraw(line, context)
        context.restoreGState()
        // `x` is deliberately applied as a graphics transform so the Core Text
        // line remains measured from the same leading origin as React Native.
    }
}
