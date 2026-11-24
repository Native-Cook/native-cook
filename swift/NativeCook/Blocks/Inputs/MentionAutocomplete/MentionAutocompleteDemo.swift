import SwiftUI

/// Mention Autocomplete: a team chat composer. "@" opens a popover of people that grows from the
/// caret and filters as you type; a pick turns the typed letters into a chip, and Backspace on a
/// chip selects it, then breaks it back into text.
struct MentionAutocompleteDemo: View {
    var body: some View {
        MentionAutocomplete(autoPlay: true)
    }
}

#Preview {
    MentionAutocompleteDemo()
}
