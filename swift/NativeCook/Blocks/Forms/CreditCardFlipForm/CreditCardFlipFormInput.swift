import SwiftUI
import UIKit

/// One real text field of the form. It formats as it goes: every edit is handed to `onChange`, and
/// the field then shows what `format` makes of the result (grouped digits, MM/YY, capitals), so a
/// refused character never appears, even for a moment.
struct CreditCardFlipFormInput: UIViewRepresentable {
    let field: CreditCardFlipFormField
    let text: String
    let placeholder: String
    let accent: UIColor
    /// Asks a field to take the keyboard, or every field to give it back (nil); `id` makes each ask new.
    let request: (field: CreditCardFlipFormField?, id: Int)
    let onChange: (String) -> Void
    let format: () -> String
    let onFocus: () -> Void
    let onBlur: () -> Void
    /// The return key: moves on to another field when given, else puts the keyboard away.
    var onReturn: (() -> Void)? = nil

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> UITextField {
        let input = UITextField()
        input.delegate = context.coordinator
        input.font = UIFont.monospacedDigitSystemFont(ofSize: 17, weight: .medium)
        input.textColor = UIColor(red: 0xf4 / 255, green: 0xf5 / 255, blue: 0xf8 / 255, alpha: 1)
        input.keyboardType = field == .name ? .default : .numberPad
        input.autocapitalizationType = field == .name ? .allCharacters : .none
        input.autocorrectionType = .no
        input.spellCheckingType = .no
        input.smartDashesType = .no
        input.smartQuotesType = .no
        input.keyboardAppearance = .dark
        input.returnKeyType = onReturn == nil ? .done : .next
        input.attributedPlaceholder = NSAttributedString(
            string: placeholder,
            attributes: [.foregroundColor: UIColor(red: 0x4b / 255, green: 0x4e / 255, blue: 0x5a / 255, alpha: 1)]
        )
        input.setContentHuggingPriority(.defaultLow, for: .horizontal)
        input.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        context.coordinator.seen = request.id
        return input
    }

    func updateUIView(_ input: UITextField, context: Context) {
        context.coordinator.parent = self
        if input.text != text { input.text = text }
        input.tintColor = accent
        if context.coordinator.seen != request.id {
            context.coordinator.seen = request.id
            if request.field == field, !input.isFirstResponder {
                DispatchQueue.main.async { input.becomeFirstResponder() }
            } else if request.field == nil, input.isFirstResponder {
                DispatchQueue.main.async { input.resignFirstResponder() }
            }
        }
    }

    final class Coordinator: NSObject, UITextFieldDelegate {
        var parent: CreditCardFlipFormInput
        var seen = 0

        init(_ parent: CreditCardFlipFormInput) {
            self.parent = parent
        }

        func textField(_ textField: UITextField, shouldChangeCharactersIn range: NSRange, replacementString string: String) -> Bool {
            let current = (textField.text ?? "") as NSString
            let proposed = current.replacingCharacters(in: range, with: string)
            parent.onChange(proposed)
            let shown = parent.format()
            if textField.text != shown { textField.text = shown }
            return false
        }

        func textFieldDidBeginEditing(_ textField: UITextField) {
            parent.onFocus()
        }

        func textFieldDidEndEditing(_ textField: UITextField) {
            parent.onBlur()
        }

        func textFieldShouldReturn(_ textField: UITextField) -> Bool {
            if let next = parent.onReturn { next() } else { textField.resignFirstResponder() }
            return true
        }
    }
}
