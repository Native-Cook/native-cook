import SwiftUI
import UIKit

struct HealthAgeDialLabel: UIViewRepresentable, Equatable {
    let text: String
    let style: HealthAgeDialTheme.TextStyle
    var color: Color? = nil
    var alignment: NSTextAlignment = .left
    var lines = 1

    func makeUIView(context: Context) -> UILabel {
        let label = UILabel()
        label.backgroundColor = .clear
        label.adjustsFontForContentSizeCategory = false
        label.isAccessibilityElement = false
        label.clipsToBounds = true
        return label
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView label: UILabel, context: Context) -> CGSize? {
        guard let width = proposal.width, let height = proposal.height else { return nil }
        label.preferredMaxLayoutWidth = width
        return CGSize(width: width, height: height)
    }

    func updateUIView(_ label: UILabel, context: Context) {
        let font = UIFont.systemFont(ofSize: style.size, weight: style.weight)
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = alignment
        paragraph.lineBreakMode = .byWordWrapping
        if let line = style.line {
            paragraph.minimumLineHeight = line
            paragraph.maximumLineHeight = line
        }
        label.numberOfLines = lines
        label.preferredMaxLayoutWidth = label.bounds.width
        label.attributedText = NSAttributedString(
            string: text,
            attributes: [
                .font: font,
                .foregroundColor: UIColor(color ?? style.color),
                .kern: style.tracking,
                .paragraphStyle: paragraph,
            ]
        )
    }
}
