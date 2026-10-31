import SwiftUI
import UIKit

/// The source uses native TextKit metrics rather than SwiftUI's extra text leading.
struct GlassIconOrbitCopy: UIViewRepresentable {
    let brand: String
    let action: String
    let safe: EdgeInsets

    func makeUIView(context: Context) -> GlassIconOrbitCopyView {
        let view = GlassIconOrbitCopyView()
        view.configure(brand: brand, action: action, safe: safe)
        return view
    }

    func updateUIView(_ view: GlassIconOrbitCopyView, context: Context) {
        view.configure(brand: brand, action: action, safe: safe)
    }
}

final class GlassIconOrbitCopyView: UIView {
    private let brandLabel = Text("halo", size: 20, weight: .heavy, tracking: -0.5)
    private let skipLabel = Text("Skip", size: 15, weight: .semibold, alpha: 0.55)
    private let headingLabel = Text("One assistant for all of it", size: 28, weight: .heavy,
                                    tracking: -0.8, lineHeight: 32)
    private let subtitleLabel = Text("Photos, notes, songs and chats — ask Halo and it finds, writes or plans.",
                                     size: 16, alpha: 0.6, lineHeight: 22)
    private let actionLabel = Text("Get started", size: 17, weight: .bold, color: .black)
    private let button = UIView()
    private var safe = EdgeInsets()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false
        isUserInteractionEnabled = false
        for label in [brandLabel, skipLabel, headingLabel, subtitleLabel] {
            addSubview(label)
        }
        headingLabel.accessibilityTraits = .header
        button.backgroundColor = .white
        button.layer.cornerRadius = 29
        button.isAccessibilityElement = true
        button.accessibilityTraits = .button
        addSubview(button)
        actionLabel.isAccessibilityElement = false
        button.addSubview(actionLabel)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func configure(brand: String, action: String, safe: EdgeInsets) {
        let brandChanged = brandLabel.setText(brand)
        let actionChanged = actionLabel.setText(action)
        let safeChanged = self.safe.top != safe.top || self.safe.bottom != safe.bottom
            || self.safe.leading != safe.leading || self.safe.trailing != safe.trailing
        self.safe = safe
        button.accessibilityLabel = action
        if brandChanged || actionChanged || safeChanged { setNeedsLayout() }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let width = max(1, bounds.width - 48)
        let top = safe.top + 8
        let brandSize = brandLabel.measure(width: width)
        let skipSize = skipLabel.measure(width: width)
        brandLabel.frame = CGRect(x: 24, y: top + (34 - brandSize.height) / 2,
                                 width: brandSize.width, height: brandSize.height)
        skipLabel.frame = CGRect(x: bounds.width - 24 - skipSize.width,
                                y: top + (34 - skipSize.height) / 2,
                                width: skipSize.width, height: skipSize.height)

        let headingSize = headingLabel.measure(width: width)
        let subtitleSize = subtitleLabel.measure(width: width)
        let buttonTop = bounds.height - safe.bottom - 14 - 58
        let subtitleTop = buttonTop - 20 - subtitleSize.height
        let headingTop = subtitleTop - 8 - headingSize.height
        headingLabel.frame = CGRect(x: 24, y: headingTop, width: width, height: headingSize.height)
        subtitleLabel.frame = CGRect(x: 24, y: subtitleTop, width: width, height: subtitleSize.height)
        button.frame = CGRect(x: 24, y: buttonTop, width: width, height: 58)
        let actionSize = actionLabel.measure(width: width)
        actionLabel.frame = CGRect(x: (width - actionSize.width) / 2,
                                  y: (58 - actionSize.height) / 2,
                                  width: actionSize.width, height: actionSize.height)
    }

    /// Match RCTTextLayoutManager: no font leading or text-container padding, and its
    /// baseline correction when a supplied line height exceeds the native font height.
    private final class Text: UIView {
        private let storage = NSTextStorage()
        private let manager = NSLayoutManager()
        private let container = NSTextContainer(size: .zero)
        private let attributes: [NSAttributedString.Key: Any]
        private var string = ""

        init(_ string: String, size: CGFloat, weight: UIFont.Weight = .regular,
             color: UIColor = .white, alpha: CGFloat = 1, tracking: CGFloat = 0,
             lineHeight: CGFloat? = nil) {
            let font = UIFont.systemFont(ofSize: size, weight: weight)
            var attributes: [NSAttributedString.Key: Any] = [
                .font: font, .foregroundColor: color.withAlphaComponent(alpha)
            ]
            if tracking != 0 { attributes[.kern] = tracking }
            if let lineHeight {
                let paragraph = NSMutableParagraphStyle()
                paragraph.minimumLineHeight = lineHeight
                paragraph.maximumLineHeight = lineHeight
                attributes[.paragraphStyle] = paragraph
                if lineHeight >= font.lineHeight {
                    attributes[.baselineOffset] = (lineHeight - font.lineHeight) / 2
                }
            }
            self.attributes = attributes
            super.init(frame: .zero)
            backgroundColor = .clear
            isOpaque = false
            contentMode = .redraw
            isUserInteractionEnabled = false
            isAccessibilityElement = true
            manager.usesFontLeading = false
            container.lineFragmentPadding = 0
            container.lineBreakMode = .byClipping
            container.maximumNumberOfLines = 0
            manager.addTextContainer(container)
            storage.addLayoutManager(manager)
            _ = setText(string)
        }

        required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

        @discardableResult
        func setText(_ value: String) -> Bool {
            guard string != value else { return false }
            string = value
            storage.setAttributedString(NSAttributedString(string: value, attributes: attributes))
            accessibilityLabel = value
            setNeedsDisplay()
            return true
        }

        func measure(width: CGFloat) -> CGSize {
            container.size = CGSize(width: max(1, width), height: 10000)
            manager.ensureLayout(for: container)
            let measured = manager.usedRect(for: container).size
            let scale = window?.screen.scale ?? UIScreen.main.scale
            return CGSize(width: ceil(measured.width * scale) / scale,
                          height: ceil(measured.height * scale) / scale)
        }

        override func draw(_ rect: CGRect) {
            super.draw(rect)
            let range = manager.glyphRange(for: container)
            manager.drawBackground(forGlyphRange: range, at: .zero)
            manager.drawGlyphs(forGlyphRange: range, at: .zero)
        }
    }
}
