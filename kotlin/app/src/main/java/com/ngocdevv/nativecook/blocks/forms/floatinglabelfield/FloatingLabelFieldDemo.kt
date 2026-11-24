package com.ngocdevv.nativecook.blocks.forms.floatinglabelfield

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 Floating Label Field: a sign-up form whose placeholders float up into a notch in the outline,
 with a drawn check, a shake and helper line on errors, and a password strength bar.
 Component: FloatingLabelField.kt (screen), FloatingField.kt (the reusable field).
 */
@Composable
fun FloatingLabelFieldDemo() {
    FloatingLabelField(autoPlay = true)
}

@Preview
@Composable
private fun FloatingLabelFieldDemoPreview() {
    FloatingLabelFieldDemo()
}
