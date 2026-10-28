package com.ngocdevv.nativecook.blocks.buttons.saucesqueezecart

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The product page on the whole screen, squeezing the bottle into the cart by itself. */
@Composable
fun SauceSqueezeCartDemo() {
    SauceSqueezeCart(autoPlay = true)
}

@Preview
@Composable
private fun SauceSqueezeCartDemoPreview() {
    SauceSqueezeCartDemo()
}
