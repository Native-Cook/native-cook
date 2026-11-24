import SwiftUI
import UIKit

/*
 The composer's real text field. Its text is drawn as runs: plain text, and each chip's name in
 semibold accent (white while selected, invisible while the chip is being born, since the chip
 layer draws it then). Every edit is intercepted and handed to the model as the next plain text,
 which decides what it means (typing, picking, Backspace into a chip) and writes the runs back.
*/
struct MentionAutocompleteComposerField: UIViewRepresentable {
    struct Look: Equatable {
        var doc: MentionAutocompleteDoc
        /// Chip whose letters the chip layer draws (morph), or -1.
        var hidden: Int
        var caret: Int?
    }

    let look: Look
    let channel: String
    var onText: (String, Int) -> Void
    var onFocus: (Bool) -> Void
    var onReturn: () -> Void

    static let font = UIFont.systemFont(ofSize: 16)
    static let chipFont = UIFont.systemFont(ofSize: 16, weight: .semibold)
    static let ink = UIColor(red: 0x1E / 255, green: 0x1D / 255, blue: 0x1A / 255, alpha: 1)
    static let chipInk = UIColor(red: 0x24 / 255, green: 0x52 / 255, blue: 0xB9 / 255, alpha: 1)
    static let accent = UIColor(red: 0x2F / 255, green: 0x63 / 255, blue: 0xD6 / 255, alpha: 1)

    static func attributed(_ doc: MentionAutocompleteDoc, hidden: Int) -> NSAttributedString {
        let out = NSMutableAttributedString()
        for r in doc.runs {
            if r.chip < 0 {
                out.append(NSAttributedString(string: r.text, attributes: [.font: font, .foregroundColor: ink]))
            } else {
                let color: UIColor = r.chip == hidden ? .clear : (doc.selected == r.chip ? UIColor(red: 1, green: 0.992, blue: 0.973, alpha: 1) : chipInk)
                out.append(NSAttributedString(string: r.text, attributes: [.font: chipFont, .foregroundColor: color]))
            }
        }
        return out
    }

    /// Width of a doc's runs, as the field lays them out.
    static func width(_ doc: MentionAutocompleteDoc) -> CGFloat {
        attributed(doc, hidden: -1).size().width
    }

    static func width(_ s: String, semibold: Bool = false) -> CGFloat {
        (s as NSString).size(withAttributes: [.font: semibold ? chipFont : font]).width
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> UITextField {
        let f = UITextField()
        f.delegate = context.coordinator
        f.font = Self.font
        f.textColor = Self.ink
        f.tintColor = Self.accent
        f.borderStyle = .none
        f.backgroundColor = .clear
        f.autocorrectionType = .no
        f.spellCheckingType = .no
        f.autocapitalizationType = .sentences
        f.returnKeyType = .send
        f.accessibilityLabel = "Message \(channel)"
        f.setContentHuggingPriority(.defaultLow, for: .horizontal)
        f.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        f.addTarget(context.coordinator, action: #selector(Coordinator.began), for: .editingDidBegin)
        f.addTarget(context.coordinator, action: #selector(Coordinator.ended), for: .editingDidEnd)
        return f
    }

    func updateUIView(_ f: UITextField, context: Context) {
        context.coordinator.parent = self
        guard context.coordinator.applied != look else { return }
        context.coordinator.applied = look
        f.attributedText = Self.attributed(look.doc, hidden: look.hidden)
        f.typingAttributes = [.font: Self.font, .foregroundColor: Self.ink]
        if let caret = look.caret, f.isFirstResponder,
           let pos = f.position(from: f.beginningOfDocument, offset: min(caret, look.doc.text.utf16.count)) {
            f.selectedTextRange = f.textRange(from: pos, to: pos)
        }
    }

    final class Coordinator: NSObject, UITextFieldDelegate {
        var parent: MentionAutocompleteComposerField
        var applied: Look?

        init(_ parent: MentionAutocompleteComposerField) {
            self.parent = parent
        }

        @objc func began() { parent.onFocus(true) }
        @objc func ended() { parent.onFocus(false) }

        func textField(_ f: UITextField, shouldChangeCharactersIn range: NSRange, replacementString string: String) -> Bool {
            let current = (f.text ?? "") as NSString
            let next = current.replacingCharacters(in: range, with: string)
            parent.onText(next, range.location + (string as NSString).length)
            return false
        }

        func textFieldShouldReturn(_ f: UITextField) -> Bool {
            parent.onReturn()
            return false
        }
    }
}
