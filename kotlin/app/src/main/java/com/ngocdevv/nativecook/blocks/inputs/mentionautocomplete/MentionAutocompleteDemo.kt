package com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 Mention Autocomplete: a team chat composer. Typing "@" opens a people popover that grows from the
 caret and filters as you type; picking someone turns the typed letters into a token chip, and
 Backspace on a chip selects it, then breaks it back into text.
 Component: MentionAutocomplete.kt (composer screen), MentionAutocompletePopover.kt (the popover).
 */
@Composable
fun MentionAutocompleteDemo() {
    MentionAutocomplete(autoPlay = true)
}

@Preview
@Composable
private fun MentionAutocompleteDemoPreview() {
    MentionAutocompleteDemo()
}
