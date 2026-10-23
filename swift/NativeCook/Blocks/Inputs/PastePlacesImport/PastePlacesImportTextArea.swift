import SwiftUI
import UIKit

/// The text area itself: a real multiline text input over the card, in the note's font, size, colour and left edge.
/// It takes input only while the screen is at rest (`live`); the sample flow paints its note instead.
struct PastePlacesImportTextArea: UIViewRepresentable {
    @Binding var text: String
    @Binding var focused: Bool
    let live: Bool
    /// Bumped to dismiss the keyboard (a tap elsewhere, start import).
    let resignToken: Int
    let fontSize: Double
    let lineHeight: Double
    /// Raises the glyphs so the baseline sits where the painted note's does (half-leading above and below).
    let baselineOffset: Double

    private var attributes: [NSAttributedString.Key: Any] {
        let style = NSMutableParagraphStyle()
        style.minimumLineHeight = lineHeight
        style.maximumLineHeight = lineHeight
        return [
            .font: PastePlacesImportAssets.font(PastePlacesImportTheme.textFontName, size: fontSize),
            .foregroundColor: UIColor(PastePlacesImportTheme.noteInk),
            .paragraphStyle: style,
            .baselineOffset: baselineOffset,
        ]
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> UITextView {
        let view = UITextView()
        view.delegate = context.coordinator
        view.backgroundColor = .clear
        view.textContainerInset = .zero
        view.textContainer.lineFragmentPadding = 0
        view.isScrollEnabled = true
        view.showsVerticalScrollIndicator = false
        view.autocorrectionType = .no
        view.tintColor = UIColor(PastePlacesImportTheme.detect)
        view.accessibilityLabel = PastePlacesImportTheme.Input.label
        view.accessibilityHint = "Type a note, or use paste"
        view.typingAttributes = attributes
        context.coordinator.token = resignToken
        return view
    }

    func updateUIView(_ view: UITextView, context: Context) {
        context.coordinator.parent = self
        view.typingAttributes = attributes
        if view.text != text {
            view.attributedText = NSAttributedString(string: text, attributes: attributes)
        }
        view.alpha = live ? 1 : 0
        view.isUserInteractionEnabled = live
        view.isAccessibilityElement = live
        if (!live || context.coordinator.token != resignToken), view.isFirstResponder {
            view.resignFirstResponder()
        }
        context.coordinator.token = resignToken
    }

    final class Coordinator: NSObject, UITextViewDelegate {
        var parent: PastePlacesImportTextArea
        var token = 0

        init(_ parent: PastePlacesImportTextArea) { self.parent = parent }

        func textViewDidChange(_ textView: UITextView) {
            let value = textView.text ?? ""
            DispatchQueue.main.async { self.parent.text = value }
        }

        func textViewDidBeginEditing(_ textView: UITextView) {
            DispatchQueue.main.async { self.parent.focused = true }
        }

        func textViewDidEndEditing(_ textView: UITextView) {
            DispatchQueue.main.async { self.parent.focused = false }
        }

        func textView(_ textView: UITextView, shouldChangeTextIn range: NSRange, replacementText replacement: String) -> Bool {
            let current = textView.text as NSString? ?? ""
            let next = current.replacingCharacters(in: range, with: replacement)
            return next.count <= PastePlacesImportTheme.Input.max
        }
    }
}
